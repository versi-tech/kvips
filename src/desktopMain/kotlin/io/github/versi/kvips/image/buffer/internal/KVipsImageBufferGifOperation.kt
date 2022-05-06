package io.github.versi.kvips.image.buffer.internal

import io.github.versi.kvips.image.*
import io.github.versi.kvips.image.internal.*
import io.github.versi.kvips.libvips.*
import io.github.versi.kvips.image.buffer.KVipsImageBufferOperation
import io.github.versi.kvips.image.buffer.KVipsImageBufferOperationParams
import io.github.versi.kvips.image.buffer.KVipsImageOperationError
import io.github.versi.kvips.image.buffer.KVipsImageOperationResult
import io.github.versi.kvips.image.internal.asVipsImage
import io.github.versi.kvips.image.internal.resize
import io.github.versi.kvips.image.internal.thumbnail
import io.github.versi.kvips.image.internal.writeToBufferOperation
import kotlinx.cinterop.*
import platform.posix.NULL

typealias KVipsImageBufferGifRunnable = (CPointer<VipsObject>?, CPointer<CPointerVar<VipsImage>>, Int, KVipsImageOperationParams) -> CPointer<CPointerVar<VipsImage>>?

private val gifCropRunnable: KVipsImageBufferGifRunnable = { _, sourceFrames, _, _ ->
    // no need to perform additional action for crop - crop is already done during extraction
    sourceFrames
}

@OptIn(ExperimentalUnsignedTypes::class)
private val gifScaleResizeRunnable: KVipsImageBufferGifRunnable = { context, sourceFrames, numberOfPages, params ->
    val scaleParams = params as KVipsImageScaleOperationParams
    // performing some operations on each frame - before reassembling GIF back - scale as example
    // TODO: IMPORTANT - check if we can perform it on-the-fly on the same images, without allocation resizedImages locally
    val resizedImages = vips_object_local_array(context, numberOfPages)?.reinterpret<CPointerVar<VipsImage>>()
    resizedImages?.let {
        for (i in 0 until numberOfPages) {
            val sourceImage = sourceFrames.plus(i)
            val outputImage = resizedImages.plus(i)
            outputImage?.pointed?.let { output ->
                sourceImage?.pointed?.value?.resize(scaleParams, output)
            }
        }
    }
    resizedImages
}

@OptIn(ExperimentalUnsignedTypes::class)
private val gifThumbnailResizeRunnable: KVipsImageBufferGifRunnable = { context, sourceFrames, numberOfPages, params ->
    val thumbnailParams = params as KVipsImageThumbnailOperationParams
    val resizedImages = vips_object_local_array(context, numberOfPages)?.reinterpret<CPointerVar<VipsImage>>()
    resizedImages?.let {
        for (i in 0 until numberOfPages) {
            val sourceImage = sourceFrames.plus(i)
            val outputImage = resizedImages.plus(i)
            outputImage?.pointed?.let { output ->
                sourceImage?.pointed?.value?.thumbnail(thumbnailParams, output)
            }
        }
    }
    resizedImages
}

/**
 * Based on: https://github.com/libvips/libvips/issues/1167
 * GIF: great topic: https://github.com/libvips/pyvips/issues/129
 * https://stackoverflow.com/questions/69973023/getting-error-while-try-to-write-gif-image-to-buffer
 */
internal sealed class KVipsImageBufferGifOperation(
    private val operationName: String,
    private val runnable: KVipsImageBufferGifRunnable,
    private val operationParams: KVipsImageOperationParams
) : KVipsImageBufferOperation {

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun run(params: KVipsImageBufferOperationParams): KVipsImageOperationResult {
        params.inputImage.toUByteArray().usePinned { pinnedImageData ->
            memScoped {
                defer {
                    vips_thread_shutdown()
                }

                val sourceAnimation = pinnedImageData.asVipsImage(pinnedImageData.get().size, loadAllFrames = true)
                sourceAnimation?.let {
                    val context = vips_image_new()?.reinterpret<VipsObject>()
                    val outputAnimation = allocPointerTo<VipsImage>()

                    val pageHeight = vips_image_get_page_height(sourceAnimation)
                    val numberOfPages = vips_image_get_n_pages(sourceAnimation)
                    val animationWidth = sourceAnimation.pointed.Xsize

                    val paramsForExtraction = resolveExtractionParams(operationParams, animationWidth, pageHeight)

                    val imagePages =
                        vips_object_local_array(context, numberOfPages)?.reinterpret<CPointerVar<VipsImage>>()
                    val copyPages = vips_object_local_array(context, 1)?.reinterpret<CPointerVar<VipsImage>>()

                    if (imagePages == null || copyPages == null) {
                        return KVipsImageOperationError(KVipsImageOperationException("Failed to extract frames from animation."))
                    }
                    extractAnimationFrames(
                        sourceAnimation,
                        imagePages,
                        numberOfPages,
                        pageHeight,
                        paramsForExtraction
                    )
                    val processedFrames = runnable.invoke(context, imagePages, numberOfPages, operationParams)
                    processedFrames?.let {
                        assembleAnimation(processedFrames, copyPages, outputAnimation, numberOfPages)
                        sourceAnimation.unref()
                        context.unref()
                        return outputAnimation.writeToBufferOperation(params.outputImageParams, null,this)
                    }
                    sourceAnimation.unref()
                    context.unref()
                }
                return KVipsImageOperationError(KVipsImageOperationException("Failed to read source animation for $operationName operation."))
            }
        }
    }

    private fun resolveExtractionParams(
        operationParams: KVipsImageOperationParams,
        animationWidth: Int,
        pageHeight: Int
    ) = if (operationParams is KVipsImageCropOperationParams) {
        if (!operationParams.fitInImage(animationWidth, pageHeight)) {
            throw KVipsImageOperationException("Crop params must fit in source animation.")
        }
        operationParams
    } else {
        KVipsImageCropOperationParams(
            left = 0,
            top = 0,
            width = animationWidth,
            height = pageHeight
        )
    }

    private fun extractAnimationFrames(
        sourceAnimation: CPointer<VipsImage>,
        outputFrames: CPointer<CPointerVar<VipsImage>>,
        numberOfPages: Int,
        pageHeight: Int,
        cropParams: KVipsImageCropOperationParams
    ) {
        for (i in 0 until numberOfPages) {
            val outputImage = outputFrames.plus(i)?.pointed?.ptr
            vips_crop(
                sourceAnimation,
                outputImage,
                cropParams.left,
                pageHeight * i + cropParams.top,
                cropParams.width,
                cropParams.height,
                NULL
            )
        }
    }

    private fun assembleAnimation(
        inputImages: CPointer<CPointerVar<VipsImage>>,
        joinedCopy: CPointer<CPointerVar<VipsImage>>,
        outputAnimation: CPointerVar<VipsImage>,
        numberOfPages: Int
    ) {
        vips_arrayjoin(inputImages, joinedCopy.pointed.ptr, numberOfPages, "across", 1, NULL)
        vips_copy(joinedCopy.pointed.value, outputAnimation.ptr, NULL)
        val resultHeight = inputImages[0]!!.pointed.Ysize
        vips_image_set_int(outputAnimation.value, "page-height", resultHeight)
    }
}

internal class KVipsImageBufferGifCrop(cropParams: KVipsImageCropOperationParams) :
    KVipsImageBufferGifOperation("Crop", gifCropRunnable, cropParams)

internal class KVipsImageBufferGifScaleResize(scaleParams: KVipsImageScaleOperationParams) :
    KVipsImageBufferGifOperation("Scale-resize", gifScaleResizeRunnable, scaleParams)

internal class KVipsImageBufferGifThumbnailResize(thumbnailParams: KVipsImageThumbnailOperationParams) :
    KVipsImageBufferGifOperation("Thumbnail-resize", gifThumbnailResizeRunnable, thumbnailParams)