// src/index.ts

export { default } from './ExpoVideoAudioExtractorModule'
export * from './ExpoVideoAudioExtractor.types'

import ExpoVideoAudioExtractorModule from './ExpoVideoAudioExtractorModule'
import type { ExtractOptions } from './ExpoVideoAudioExtractor.types'

/** Strip (and optionally trim) the audio track from a local video.
 *  Resolves with an absolute path to the newly-created file. */
export function extractAudio(options: ExtractOptions): Promise<string> {
  return ExpoVideoAudioExtractorModule.extractAudio(options)
}
