// src/ExpoVideoAudioExtractor.types.ts

// Native events emitted by long-running tasks.
export interface ExpoVideoAudioExtractorModuleEvents {
  // `{ taskId, progress /* 0‒1 */ }` roughly every 250 ms.
  progress: { taskId: string; progress: number }
  // Fires once per task if an error bubbles up from native.
  error: { taskId: string; message: string }
  // Satisfy EventsMap type
  [eventName: string]: any
}

// Options for the `extractAudio` operation.
export interface ExtractOptions {
  // Absolute/local URI of the source video.
  video: string

  // Absolute/local URI where the audio file will be written (overwritten if present).
  output: string

  // Output container/codec.
  // - `m4a` (default) → AAC in M4A (universally supported)
  // - `wav`           → Linear PCM
  format?: 'm4a' | 'wav'

  // Seconds from the start of the video to begin extraction. Defaults to `0`.
  start?: number

  // Seconds of audio to export. Omit to export through the end of the video.
  duration?: number

  // Linear gain (0 – 1). Defaults to `1`.
  volume?: number

  // Channel layout. Defaults to platform/export preset (usually `2`).
  channels?: 1 | 2

  // Sample rate in Hz; native default if omitted.
  sampleRate?: number
}
