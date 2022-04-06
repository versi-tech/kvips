package io.github.versi.kvips.image.buffer

import io.github.versi.kvips.image.KVipsImageScaleOperationParams
import io.github.versi.kvips.image.KVipsImageGIF
import io.github.versi.kvips.image.buffer.internal.KVipsImageBufferGifScaleResize
import io.github.versi.kvips.image.buffer.internal.KVipsImageBufferStaticScaleResize

class KVipsImageBufferScaleResize(private val scaleParams: KVipsImageScaleOperationParams) :
    KVipsImageBufferOperation {

    override fun run(params: KVipsImageBufferOperationParams): KVipsImageOperationResult {
        return when (params.outputImageParams.format) {
            KVipsImageGIF -> KVipsImageBufferGifScaleResize(scaleParams).run(params)
            else -> KVipsImageBufferStaticScaleResize(scaleParams).run(params)
        }
    }
}