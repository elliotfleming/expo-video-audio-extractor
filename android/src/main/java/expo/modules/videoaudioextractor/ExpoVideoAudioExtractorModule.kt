// android/src/main/java/expo/modules/videoaudioextractor/ExpoVideoAudioExtractorModule.kt

package expo.modules.videoaudioextractor

import android.media.*
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import kotlin.math.min

class ExpoVideoAudioExtractorModule : Module() {
  /** Keep coroutine jobs so JS can cancel by taskId. */
  private val jobs = mutableMapOf<String, Job>()

  override fun definition() = ModuleDefinition {
    Name("ExpoVideoAudioExtractor")
    Events("progress", "error")

    AsyncFunction("extractAudio") { opts: Map<String, Any>, promise: Promise ->
      val taskId = UUID.randomUUID().toString()
      val job = CoroutineScope(Dispatchers.IO).launch {
        try {
          val p = Params(opts)
          when (p.format) {
            "m4a" -> extractM4a(p, taskId)
            "wav" -> extractWav(p, taskId)
            else -> throw IllegalArgumentException("Unsupported format: ${p.format}")
          }
          promise.resolve(p.output)
        } catch (t: Throwable) {
          sendEvent("error", mapOf("taskId" to taskId, "message" to t.message))
          promise.reject("EXTRACT_ERROR", t)
        } finally {
          jobs.remove(taskId)
        }
      }
      jobs[taskId] = job
    }

    AsyncFunction("cancel") { taskId: String, promise: Promise ->
      jobs.remove(taskId)?.cancel()
      promise.resolve(null)
    }
  }

  // ---------------------------------------------------------------------------
  // Params
  // ---------------------------------------------------------------------------

  private data class Params(
    val video: String,
    val output: String,
    val format: String,
    val startUs: Long,
    val durUs: Long,          // 0 → through end
    val volume: Float,
    val sampleRate: Int?,     // wav-only overrides
    val channels: Int?        // wav-only overrides
  ) {
    constructor(m: Map<String, Any>) : this(
      video  = m["video"]  as String,
      output = m["output"] as String,
      format = (m["format"] as? String ?: "m4a").lowercase(),
      startUs = (((m["start"] ?: 0) as Number).toDouble() * 1_000_000L).toLong(),
      durUs   = (((m["duration"] ?: 0) as Number).toDouble() * 1_000_000L).toLong(),
      volume  = (m["volume"] as? Number ?: 1).toFloat(),
      sampleRate = (m["sampleRate"] as? Number)?.toInt(),
      channels   = (m["channels"]   as? Number)?.toInt()
    )
  }

  // ---------------------------------------------------------------------------
  // M4A (passthrough) path: MediaExtractor → MediaMuxer
  // ---------------------------------------------------------------------------

  private fun extractM4a(p: Params, taskId: String) {
    File(p.output).delete()

    val ex = MediaExtractor().apply { setDataSource(p.video) }
    val srcIdx = selectTrack(ex, "audio/")
    val fmt = ex.getTrackFormat(srcIdx)
    val totalDur = fmt.getLong(MediaFormat.KEY_DURATION)
    val wantedDur = if (p.durUs > 0) p.durUs else totalDur - p.startUs

    val muxer = MediaMuxer(p.output, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
    val dstTrack = muxer.addTrack(fmt)
    muxer.start()

    // Seek to start
    ex.seekTo(p.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

    val buf = ByteBuffer.allocate(1 * 1024 * 1024)
    val info = MediaCodec.BufferInfo()
    var lastPts = 0L

    while (true) {
      val sz = ex.readSampleData(buf, 0)
      if (sz < 0) break
      info.apply {
        offset = 0
        size = sz
        flags = ex.sampleFlags
        presentationTimeUs = ex.sampleTime - p.startUs
      }
      if (info.presentationTimeUs >= wantedDur) break

      muxer.writeSampleData(dstTrack, buf, info)
      lastPts = info.presentationTimeUs
      sendProgress(taskId, lastPts, wantedDur)
      ex.advance()
    }

    muxer.stop(); muxer.release()
    ex.release()
    sendProgress(taskId, 1, 1) // 100 %
  }

  // ---------------------------------------------------------------------------
  // WAV path: decode AAC/whatever → PCM → .wav
  // ---------------------------------------------------------------------------

  private fun extractWav(p: Params, taskId: String) {
    File(p.output).delete()

    val ex = MediaExtractor().apply { setDataSource(p.video) }
    val srcIdx = selectTrack(ex, "audio/")
    val srcFmt = ex.getTrackFormat(srcIdx)
    val mime = srcFmt.getString(MediaFormat.KEY_MIME)!!
    val totalDur = srcFmt.getLong(MediaFormat.KEY_DURATION)
    val wantedDur = if (p.durUs > 0) p.durUs else totalDur - p.startUs

    val sampleRate =
      p.sampleRate ?: srcFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    val channels =
      p.channels ?: srcFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    val pcmFrameSize = 2 * channels // 16-bit

    val dec = MediaCodec.createDecoderByType(mime).apply {
      configure(srcFmt, null, null, 0)
      start()
    }

    val outFile = FileOutputStream(p.output)
    writeWavHeader(outFile, sampleRate, channels, 0) // placeholder

    ex.seekTo(p.startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

    val bufInfo = MediaCodec.BufferInfo()
    var done = false
    var totalPcmBytes = 0L
    val endPts = p.startUs + wantedDur

    while (!done) {
      // ---------- feed encoder ----------
      val inIdx = dec.dequeueInputBuffer(10_000)
      if (inIdx >= 0) {
        val inBuf = dec.getInputBuffer(inIdx)!!
        val sampleSize = ex.readSampleData(inBuf, 0)
        if (sampleSize < 0 || ex.sampleTime > endPts) {
          dec.queueInputBuffer(
            inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
          done = true
        } else {
          dec.queueInputBuffer(
            inIdx, 0, sampleSize, ex.sampleTime, 0)
          ex.advance()
        }
      }

      // ---------- drain decoder ----------
      var outIdx = dec.dequeueOutputBuffer(bufInfo, 10_000)
      while (outIdx >= 0) {
        val outBuf = dec.getOutputBuffer(outIdx)!!.order(ByteOrder.LITTLE_ENDIAN)
        if (bufInfo.size > 0) {
          val pcm = ByteArray(bufInfo.size)
          outBuf.get(pcm)

          // volume tweak
          if (p.volume != 1f) scalePcm16(pcm, p.volume)

          outFile.write(pcm)
          totalPcmBytes += pcm.size
          sendProgress(taskId, bufInfo.presentationTimeUs, wantedDur)
        }
        dec.releaseOutputBuffer(outIdx, false)
        outIdx = dec.dequeueOutputBuffer(bufInfo, 0)
      }
    }

    // finalize
    dec.stop()
    dec.release()
    outFile.close()

    // Patch header with real sizes
    patchWavHeader(File(p.output), sampleRate, channels, totalPcmBytes)
    sendProgress(taskId, 1, 1)
    ex.release()
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private fun selectTrack(ex: MediaExtractor, mimePrefix: String): Int {
    for (i in 0 until ex.trackCount) {
      val mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
      if (mime.startsWith(mimePrefix)) {
        ex.selectTrack(i)
        return i
      }
    }
    throw IllegalStateException("Track $mimePrefix not found")
  }

  private fun sendProgress(taskId: String, done: Long, total: Long) {
    val progress = if (total == 0L) 0f else min(done.toFloat() / total.toFloat(), 1f)
    sendEvent("progress", mapOf("taskId" to taskId, "progress" to progress))
  }

  /** Multiply 16-bit PCM samples by [vol]. */
  private fun scalePcm16(buf: ByteArray, vol: Float) {
    for (i in buf.indices step 2) {
      val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort()
      val scaled = (s * vol).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
      buf[i] = scaled.toByte()
      buf[i + 1] = (scaled shr 8).toByte()
    }
  }

  // ---------- WAV header helpers ----------

  private fun writeWavHeader(out: FileOutputStream, sampleRate: Int, channels: Int, dataLen: Long) {
    val byteRate = sampleRate * channels * 2
    val totalLen = 36 + dataLen
    val hdr = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    hdr.put("RIFF".toByteArray())
    hdr.putInt(totalLen.toInt())
    hdr.put("WAVEfmt ".toByteArray())
    hdr.putInt(16)                       // PCM header length
    hdr.putShort(1)                      // PCM format
    hdr.putShort(channels.toShort())
    hdr.putInt(sampleRate)
    hdr.putInt(byteRate)
    hdr.putShort((channels * 2).toShort()) // block align
    hdr.putShort(16)                      // bits per sample
    hdr.put("data".toByteArray())
    hdr.putInt(dataLen.toInt())
    out.write(hdr.array())
  }

  private fun patchWavHeader(file: File, sampleRate: Int, channels: Int, dataLen: Long) {
    val raf = file.randomAccessFile("rw")
    writeWavHeader(FileOutputStream(raf.fd), sampleRate, channels, dataLen)
    raf.close()
  }

  // RandomAccessFile extension for brevity
  private fun File.randomAccessFile(mode: String) = java.io.RandomAccessFile(this, mode)
}
