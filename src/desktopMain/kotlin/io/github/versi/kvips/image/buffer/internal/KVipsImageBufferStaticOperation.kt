package io.github.versi.kvips.image.buffer.internal

import io.github.versi.kvips.image.*
import io.github.versi.kvips.image.internal.*
import io.github.versi.kvips.libvips.VipsImage
import io.github.versi.kvips.libvips.vips_thread_shutdown
import io.github.versi.kvips.image.buffer.KVipsImageBufferOperation
import io.github.versi.kvips.image.buffer.KVipsImageBufferOperationParams
import io.github.versi.kvips.image.buffer.KVipsImageOperationError
import io.github.versi.kvips.image.buffer.KVipsImageOperationResult
import io.github.versi.kvips.image.internal.asVipsImage
import io.github.versi.kvips.image.internal.thumbnailBuffer
import io.github.versi.kvips.image.internal.writeToBufferOperation
import kotlinx.cinterop.*

typealias KVipsImageBufferStaticRunnable = (Pinned<UByteArray>, KVipsImageOperationParams, MemScope) -> CPointerVar<VipsImage>?

@OptIn(ExperimentalUnsignedTypes::class)
private val staticCropRunnable: KVipsImageBufferStaticRunnable = { pinnedImageData, params, memScope ->
    val cropParams = params as KVipsImageCropOperationParams
    val sourceImage = pinnedImageData.asVipsImage(pinnedImageData.get().size)
    sourceImage?.let {
        val imageWidth = sourceImage.pointed.Xsize
        val imageHeight = sourceImage.pointed.Ysize
        if (!cropParams.fitInImage(imageWidth, imageHeight)) {
            throw KVipsImageOperationException("Crop params must fit in source image.")
        }

        val cropAction = KVipsNativeImageCropAction(memScope)
        cropAction.perform(sourceImage, cropParams)
    }
}

private val staticScaleResizeRunnable: KVipsImageBufferStaticRunnable = { pinnedImageData, params, memScope ->
    val scaleParams = params as KVipsImageScaleOperationParams
    val sourceImage = pinnedImageData.asVipsImage(pinnedImageData.get().size)
    sourceImage?.let {
        val scaleAction = KVipsNativeImageScaleAction(memScope)
        scaleAction.perform(sourceImage, scaleParams)
    }
}

private val staticThumbnailResizeRunnable: KVipsImageBufferStaticRunnable = { pinnedImageData, params, memScope ->
    val thumbnailParams = params as KVipsImageThumbnailOperationParams
    val resizedImage = pinnedImageData.thumbnailBuffer(thumbnailParams, memScope)
    resizedImage
}

internal sealed class KVipsImageBufferStaticOperation private constructor(
    private val operationName: String,
    private val runnable: KVipsImageBufferStaticRunnable,
    private val operationParams: KVipsImageOperationParams
) : KVipsImageBufferOperation {

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun run(params: KVipsImageBufferOperationParams): KVipsImageOperationResult {
        params.inputImage.toUByteArray().usePinned { pinnedImageData ->
            memScoped {
                defer {
                    vips_thread_shutdown()
                }
                try {
                    val outputImage = runnable.invoke(pinnedImageData, operationParams, this)
                    outputImage?.let {
                        return outputImage.writeToBufferOperation(params.outputImageParams, null, this)
                    }
                } catch (exception: KVipsImageOperationException) {
                    KVipsImageOperationError(exception)
                }
            }
            return KVipsImageOperationError(KVipsImageOperationException("Failed to read source image for $operationName operation."))
        }
    }
}

internal class KVipsImageBufferStaticCrop(cropParams: KVipsImageCropOperationParams) :
    KVipsImageBufferStaticOperation("Crop", staticCropRunnable, cropParams)

internal class KVipsImageBufferStaticScaleResize(scaleParams: KVipsImageScaleOperationParams) :
    KVipsImageBufferStaticOperation("Scale-resize", staticScaleResizeRunnable, scaleParams)

internal class KVipsImageBufferStaticThumbnailResize(thumbnailParams: KVipsImageThumbnailOperationParams) :
    KVipsImageBufferStaticOperation("Thumbnail-resize", staticThumbnailResizeRunnable, thumbnailParams)