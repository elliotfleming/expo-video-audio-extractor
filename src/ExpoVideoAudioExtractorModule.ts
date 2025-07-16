// src/ExpoVideoAudioExtractorModule.ts

import { NativeModule, requireNativeModule } from 'expo'
import { ExtractOptions, ExpoVideoAudioExtractorModuleEvents } from './ExpoVideoAudioExtractor.types'

declare class ExpoVideoAudioExtractorModule extends NativeModule<ExpoVideoAudioExtractorModuleEvents> {
  extractAudio(options: ExtractOptions): Promise<string>
  cancel(taskId: string): Promise<void>
}

export default requireNativeModule<ExpoVideoAudioExtractorModule>('ExpoVideoAudioExtractor')
