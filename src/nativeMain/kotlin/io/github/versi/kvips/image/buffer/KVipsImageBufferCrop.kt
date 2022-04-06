package io.github.versi.kvips.image.buffer

import io.github.versi.kvips.image.KVipsImageCropOperationParams
import io.github.versi.kvips.image.KVipsImageGIF
import io.github.versi.kvips.image.buffer.internal.KVipsImageBufferGifCrop
import io.github.versi.kvips.image.buffer.internal.KVipsImageBufferStaticCrop

class KVipsImageBufferCrop(private val cropParams: KVipsImageCropOperationParams) : KVipsImageBufferOperation {

    override fun run(params: KVipsImageBufferOperationParams): KVipsImageOperationResult {
        return when (params.outputImageParams.format) {
            KVipsImageGIF -> KVipsImageBufferGifCrop(cropParams).run(params)
            else -> KVipsImageBufferStaticCrop(cropParams).run(params)
        }
    }
}