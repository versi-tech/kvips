package io.github.versi.kvips.internal

import io.github.versi.kvips.image.*
import io.github.versi.kvips.image.buffer.KVipsImageOperationResult
import io.github.versi.kvips.image.internal.asVipsImage
import io.github.versi.kvips.image.internal.unref
import io.github.versi.kvips.image.internal.writeToBufferOperation
import io.github.versi.kvips.libvips.*
import kotlinx.cinterop.*
import platform.posix.NULL

@OptIn(ExperimentalUnsignedTypes::class)
internal class KVipsImageGifOperationsNative : KVipsImageOperations {

    private val memScope = MemScope()
    private var imagesContext: CPointer<VipsObject>? = null
    private var imageFrames: CPointer<CPointerVar<VipsImage>>? = null
    private var numberOfPages: Int = 0
    private val data: Pinned<UByteArray>

    constructor(inputData: ByteArray) {
        data = inputData.toUByteArray().pin()
        val baseAnimation = data.asVipsImage(data.get().size, loadAllFrames = true)
        if (baseAnimation == null) {
            data.unpin()
            throw KVipsImageOperationException("Failed to load input image.")
        }

        numberOfPages = vips_image_get_n_pages(baseAnimation)
        loadAnimationFrames(baseAnimation)
    }

    constructor(
        inputData: ByteArray,
        params: KVipsImageCropOperationParams
    ) {
        data = inputData.toUByteArray().pin()
        val baseAnimation = data.asVipsImage(data.get().size, loadAllFrames = true)
        if (baseAnimation == null) {
            data.unpin()
            throw KVipsImageOperationException("Failed to load input image.")
        }

        numberOfPages = vips_image_get_n_pages(baseAnimation)
        loadAnimationFrames(baseAnimation, params)
    }

    override fun thumbnail(params: KVipsImageThumbnailOperationParams): KVipsImageOperations {
        return performOperation(thumbnailOperation, params)
    }

    override fun scale(params: KVipsImageScaleOperationParams): KVipsImageOperations {
        return performOperation(scaleOperation, params)
    }

    override fun crop(params: KVipsImageCropOperationParams): KVipsImageOperations {
        return performOperation(cropOperation, params)
    }

    override fun compose(params: KVipsImageComposeOperationParams): KVipsImageOperations {
        return performOperation(composeOperation, params)
    }

    override fun text(params: KVipsImageTextOperationParams): KVipsImageOperations {
        return performOperation(textOperation, params)
    }

    override fun finalize(params: KVipsImageOutputParams): KVipsImageOperationResult {
        memScope.defer {
            vips_thread_shutdown()
            data.unpin()
        }
        val context = vips_image_new()?.reinterpret<VipsObject>()
        try {
            val copyPages = vips_object_local_array(context, 1)?.reinterpret<CPointerVar<VipsImage>>()
            val outputAnimation = memScope.allocPointerTo<VipsImage>()
            if (imageFrames == null || copyPages == null) {
                throw KVipsImageOperationException("Failed to finalize animation.")
            }
            assembleAnimation(imageFrames!!, copyPages, outputAnimation, numberOfPages)
            return outputAnimation.writeToBufferOperation(params, memScope)
        } finally {
            imagesContext.unref()
            context.unref()
        }
    }

    // this might cause: g_object_unref: assertion 'G_IS_OBJECT (object)' failed due to duplicate
    // unref after line 193 in case user caught and consumed internal exception with dispose call
    // this won't affect the app and is safer that lack of deallocating memory in case there's exception
    // on the user side without finalize/dispose call and freeing memory
    override fun dispose() {
        imagesContext.unref()
        data.unpin()
    }

    private fun loadAnimationFrames(
        inputAnimation: CPointer<VipsImage>,
        extractionParams: KVipsImageCropOperationParams? = null
    ) {
        try {
            val pageHeight = vips_image_get_page_height(inputAnimation)
            val numberOfPages = vips_image_get_n_pages(inputAnimation)
            val animationWidth = inputAnimation.pointed.Xsize
            imagesContext = vips_image_new()?.reinterpret()
            imageFrames = vips_object_local_array(imagesContext, numberOfPages)?.reinterpret()
            if (imageFrames == null) {
                throw KVipsImageOperationException("Failed to extract frames from animation.")
            }

            val paramsForExtraction = resolveExtractionParams(extractionParams, animationWidth, pageHeight)
            extractAnimationFrames(
                inputAnimation,
                imageFrames!!,
                numberOfPages,
                pageHeight,
                paramsForExtraction
            )
        } catch (exception: Exception) {
            // intercepting exception to make sure we dealloc imageFrames
            imagesContext.unref()
            throw exception
        } finally {
            inputAnimation.unref()
        }
    }

    private fun resolveExtractionParams(
        extractionParams: KVipsImageCropOperationParams?,
        animationWidth: Int,
        pageHeight: Int
    ) = extractionParams?.let {
        if (!extractionParams.fitInImage(animationWidth, pageHeight)) {
            throw KVipsImageOperationException("Crop params must fit in source animation.")
        }
        extractionParams
    } ?: KVipsImageCropOperationParams(
        left = 0,
        top = 0,
        width = animationWidth,
        height = pageHeight
    )

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
        val (resultWidth, resultHeight) = inputImages[0]!!.pointed.run {
            Pair(this.Xsize, this.Ysize)
        }
        validateRequestedGifSize(width = resultWidth, height = resultHeight)
        vips_arrayjoin(inputImages, joinedCopy.pointed.ptr, numberOfPages, "across", 1, NULL)
        vips_copy(joinedCopy.pointed.value, outputAnimation.ptr, NULL)
        vips_image_set_int(outputAnimation.value, "page-height", resultHeight)
    }

    private fun performOperation(
        operation: KVipsImageOperation,
        params: KVipsImageOperationParams
    ): KVipsImageOperations {
        val updatedContext = vips_image_new()?.reinterpret<VipsObject>()
        try {
            val processedFrames =
                vips_object_local_array(updatedContext, numberOfPages)?.reinterpret<CPointerVar<VipsImage>>()

            if (imageFrames == null || processedFrames == null) {
                throw KVipsImageOperationException("Failed to perform animation operation.")
            }
            for (i in 0 until numberOfPages) {
                val sourceImage = imageFrames.plus(i)
                val outputImage = processedFrames.plus(i)
                outputImage?.pointed?.let { output ->
                    sourceImage?.pointed?.value?.let { input ->
                        operation.invoke(params, input, output, memScope)
                    }
                }
            }
            // processed images are now reference, input animation
            imageFrames = processedFrames
            imagesContext.unref()
            imagesContext = updatedContext
            return this
        } catch (exception: Exception) {
            imagesContext.unref()
            // intercepting exception to dealloc updatedContext in case image operation error -
            // this deallocates processedFrames: vips_object_local_array has lifecycle of context
            updatedContext.unref()
            throw exception
        }
    }

    /**
     * libvips (8.12) uses cgif internally for GIF files, there's a frame size limit:
     * https://github.com/libvips/libvips/blob/cfa7f03278c3aa10bc9232ac14378817046c16c0/libvips/foreign/cgifsave.c#L437
     * method checks if request is valid in terms of that limit, without it library silently avoids processing image
     * and prints error on process stop: gifsave_target: frame too large
     */
    private fun validateRequestedGifSize(width: Int, height: Int) {
        if (width * height > 2000 * 2000) {
            throw KVipsImageOperationException("Requested GIF animation frame to large. Area of 2000 x 2000 pixels is the supported maximum.")
        }
    }
}