package io.github.versi.kvips.image

import io.github.versi.kvips.image.buffer.KVipsImageOperationResult
import io.github.versi.kvips.internal.KVipsImageGifOperationsNative
import io.github.versi.kvips.internal.KVipsImageStaticOperationsNative

class KVipsImageOperationsNative : KVipsImageOperations {

    private val imageOperations: KVipsImageOperations

    constructor(inputFormat: KVipsImageFormat, byteArray: ByteArray) {
        imageOperations = if (inputFormat == KVipsImageGIF) {
            KVipsImageGifOperationsNative(byteArray)
        } else {
            KVipsImageStaticOperationsNative(byteArray)
        }
    }

    constructor(inputFormat: KVipsImageFormat, byteArray: ByteArray, cropParams: KVipsImageCropOperationParams) {
        if (inputFormat != KVipsImageGIF) {
            throw RuntimeException("Crop on the load is supported only for GIF format.")
        }
        imageOperations = KVipsImageGifOperationsNative(byteArray, cropParams)
    }

    constructor(
        inputFormat: KVipsImageFormat,
        byteArray: ByteArray,
        thumbnailParams: KVipsImageThumbnailOperationParams
    ) {
        if (inputFormat == KVipsImageGIF) {
            throw RuntimeException("Thumbnail on the load is not supported for GIF format.")
        }
        imageOperations = KVipsImageStaticOperationsNative(byteArray, thumbnailParams)
    }

    override fun thumbnail(params: KVipsImageThumbnailOperationParams): KVipsImageOperations {
        return imageOperations.thumbnail(params)
    }

    override fun scale(params: KVipsImageScaleOperationParams): KVipsImageOperations {
        return imageOperations.scale(params)
    }

    override fun crop(params: KVipsImageCropOperationParams): KVipsImageOperations {
        return imageOperations.crop(params)
    }

    override fun compose(params: KVipsImageComposeOperationParams): KVipsImageOperations {
        return imageOperations.compose(params)
    }

    override fun text(params: KVipsImageTextOperationParams): KVipsImageOperations {
        return imageOperations.text(params)
    }

    override fun finalize(params: KVipsImageOutputParams): KVipsImageOperationResult {
        return imageOperations.finalize(params)
    }

    override fun dispose() {
        imageOperations.dispose()
    }
}