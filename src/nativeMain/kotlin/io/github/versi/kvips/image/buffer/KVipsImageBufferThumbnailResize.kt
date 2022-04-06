package io.github.versi.kvips.image.buffer

import io.github.versi.kvips.image.KVipsImageGIF
import io.github.versi.kvips.image.KVipsImageThumbnailOperationParams
import io.github.versi.kvips.image.buffer.internal.KVipsImageBufferGifThumbnailResize
import io.github.versi.kvips.image.buffer.internal.KVipsImageBufferStaticThumbnailResize

class KVipsImageBufferThumbnailResize(private val thumbnailParams: KVipsImageThumbnailOperationParams) :
    KVipsImageBufferOperation {

    override fun run(params: KVipsImageBufferOperationParams): KVipsImageOperationResult {
        return when (params.outputImageParams.format) {
            KVipsImageGIF -> KVipsImageBufferGifThumbnailResize(thumbnailParams).run(params)
            else -> KVipsImageBufferStaticThumbnailResize(thumbnailParams).run(params)
        }
    }
}