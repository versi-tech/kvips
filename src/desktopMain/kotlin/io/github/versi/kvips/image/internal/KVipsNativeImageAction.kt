package io.github.versi.kvips.image.internal

import io.github.versi.kvips.image.KVipsImageCropOperationParams
import io.github.versi.kvips.image.KVipsImageOperationParams
import io.github.versi.kvips.image.KVipsImageScaleOperationParams
import io.github.versi.kvips.image.KVipsImageThumbnailOperationParams
import io.github.versi.kvips.libvips.VipsImage
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.allocPointerTo

typealias KVipsNativeImageRunnable = (CPointer<VipsImage>, CPointerVar<VipsImage>, KVipsImageOperationParams) -> Unit

private val cropRunnable: KVipsNativeImageRunnable = { inputImage, outputImage, params ->
    inputImage.crop(
        params as KVipsImageCropOperationParams,
        outputImage
    )
}

private val scaleRunnable: KVipsNativeImageRunnable = { inputImage, outputImage, params ->
    inputImage.resize(
        params as KVipsImageScaleOperationParams,
        outputImage
    )
}

private val thumbnailRunnable: KVipsNativeImageRunnable = { inputImage, outputImage, params ->
    inputImage.thumbnail(
        params as KVipsImageThumbnailOperationParams,
        outputImage
    )
}

sealed class KVipsNativeImageAction(private val runnable: KVipsNativeImageRunnable, private val memScope: MemScope) {

    fun perform(
        inputImage: CPointer<VipsImage>,
        params: KVipsImageOperationParams
    ): CPointerVar<VipsImage> {
        val outputImage = memScope.allocPointerTo<VipsImage>()
        try {
            runnable.invoke(inputImage, outputImage, params)
            return outputImage
        } finally {
            memScope.defer {
                inputImage.unref()
            }
        }
    }
}

class KVipsNativeImageCropAction(memScope: MemScope) : KVipsNativeImageAction(cropRunnable, memScope)
class KVipsNativeImageScaleAction(memScope: MemScope) : KVipsNativeImageAction(scaleRunnable, memScope)
class KVipsNativeImageThumbnailAction(memScope: MemScope) : KVipsNativeImageAction(thumbnailRunnable, memScope)