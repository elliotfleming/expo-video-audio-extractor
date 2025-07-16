// ios/ExpoVideoAudioExtractorModule.swift

import AVFoundation
import ExpoModulesCore

public class ExpoVideoAudioExtractorModule: Module {
  // Keep references so JS can call `cancel`
  private static var exports: [String: AVAssetExportSession] = [:]

  public func definition() -> ModuleDefinition {
    Name("ExpoVideoAudioExtractor")
    Events("progress", "error")

    AsyncFunction("extractAudio") { (options: [String: Any]) async throws -> String in
      let p = try ExtractParams(dict: options)
      let taskId = UUID().uuidString

      return try await withCheckedThrowingContinuation { cont in
        Task(priority: .userInitiated) {
          do {
            try await Self.runExtraction(params: p, module: self, taskId: taskId)
            cont.resume(returning: p.output)
          } catch {
            self.sendEvent("error", ["taskId": taskId, "message": error.localizedDescription])
            cont.resume(throwing: error)
          }
        }
      }
    }

    AsyncFunction("cancel") { (taskId: String) in
      Self.exports[taskId]?.cancelExport()
      Self.exports.removeValue(forKey: taskId)
    }
  }

  // MARK: - Core work

  private static func runExtraction(
    params p: ExtractParams,
    module: ExpoVideoAudioExtractorModule,
    taskId: String
  ) async throws {
    try? FileManager.default.removeItem(atPath: p.output)

    let asset = AVURLAsset(url: URL(fileURLWithPath: p.video))
    guard let srcTrack = asset.tracks(withMediaType: .audio).first else {
      throw err("No audio track", code: 1)
    }

    let comp = AVMutableComposition()
    guard
      let dstTrack = comp.addMutableTrack(
        withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)
    else {
      throw err("Unable to create composition track", code: 2)
    }

    let start = CMTime(seconds: p.start, preferredTimescale: srcTrack.naturalTimeScale)
    let dur =
      p.duration > 0
      ? CMTime(seconds: p.duration, preferredTimescale: srcTrack.naturalTimeScale)
      : CMTimeSubtract(asset.duration, start)

    try dstTrack.insertTimeRange(
      CMTimeRange(start: start, duration: dur),
      of: srcTrack,
      at: .zero
    )

    // Preset / file-type
    let preset: String
    let fileType: AVFileType
    switch p.format {
    case "wav":
      preset = AVAssetExportPresetPassthrough
      fileType = .wav
    default:
      preset = AVAssetExportPresetAppleM4A
      fileType = .m4a
    }

    guard let exporter = AVAssetExportSession(asset: comp, presetName: preset) else {
      throw err("Failed to create exporter", code: 3)
    }
    guard exporter.supportedFileTypes.contains(fileType) else {
      throw err("Format not supported on this device", code: 4)
    }

    exporter.outputFileType = fileType
    exporter.outputURL = URL(fileURLWithPath: p.output)
    exporter.timeRange = CMTimeRange(start: .zero, duration: dur)

    if p.volume != 1 {
      let ip = AVMutableAudioMixInputParameters(track: dstTrack)
      ip.setVolume(p.volume, at: .zero)
      let mix = AVMutableAudioMix()
      mix.inputParameters = [ip]
      exporter.audioMix = mix
    }

    exports[taskId] = exporter

    let poll = DispatchSource.makeTimerSource(queue: .global(qos: .utility))
    poll.schedule(deadline: .now(), repeating: .milliseconds(250))
    poll.setEventHandler {
      module.sendEvent("progress", ["taskId": taskId, "progress": exporter.progress])
    }
    poll.resume()

    try await withCheckedThrowingContinuation { cont in
      exporter.exportAsynchronously {
        poll.cancel()
        exports.removeValue(forKey: taskId)

        switch exporter.status {
        case .completed:
          cont.resume(returning: ())
        case .cancelled:
          cont.resume(throwing: err("Export cancelled", code: 5))
        default:
          cont.resume(
            throwing: exporter.error
              ?? err("Unknown export error", code: 6))
        }
      }
    }
  }

  // MARK: - Params

  private struct ExtractParams {
    let video: String
    let output: String
    let format: String
    let start: Double
    let duration: Double
    let volume: Float

    init(dict: [String: Any]) throws {
      guard
        let v = dict["video"] as? String,
        let o = dict["output"] as? String
      else { throw err("Missing required options", code: 0) }

      video = Self.clean(v)
      output = Self.clean(o)
      format = (dict["format"] as? String ?? "m4a").lowercased()
      start = (dict["start"] as? NSNumber)?.doubleValue ?? 0
      duration = (dict["duration"] as? NSNumber)?.doubleValue ?? 0
      volume = (dict["volume"] as? NSNumber)?.floatValue ?? 1
    }

    private static func clean(_ path: String) -> String {
      if let url = URL(string: path), url.scheme == "file" { return url.path }
      return path
    }
  }

  // MARK: - Helpers

  private static func err(_ msg: String, code: Int) -> NSError {
    NSError(
      domain: "ExpoVideoAudioExtractor", code: code,
      userInfo: [NSLocalizedDescriptionKey: msg])
  }
}
