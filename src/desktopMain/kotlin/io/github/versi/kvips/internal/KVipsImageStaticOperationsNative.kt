package io.github.versi.kvips.internal

import io.github.versi.kvips.image.*
import io.github.versi.kvips.image.buffer.KVipsImageOperationResult
import io.github.versi.kvips.image.internal.asVipsImage
import io.github.versi.kvips.libvips.*
import io.github.versi.kvips.image.internal.thumbnailBuffer
import io.github.versi.kvips.image.internal.unref
import io.github.versi.kvips.image.internal.writeToBufferOperation
import kotlinx.cinterop.*

@OptIn(ExperimentalUnsignedTypes::class)
internal class KVipsImageStaticOperationsNative : KVipsImageOperations {

    private val memScope = MemScope()
    private var image: CPointer<VipsImage>
    private val data: Pinned<UByteArray>

    constructor(inputData: ByteArray) {
        data = inputData.toUByteArray().pin()
        val baseImage = data.asVipsImage(data.get().size)
        if (baseImage == null) {
            data.unpin()
            throw KVipsImageOperationException("Failed to load input image.")
        }

        image = baseImage
    }

    constructor(
        inputData: ByteArray,
        params: KVipsImageThumbnailOperationParams
    ) {
        data = inputData.toUByteArray().pin()
        val baseImage = data.thumbnailBuffer(params, memScope).value
        if (baseImage == null) {
            data.unpin()
            throw KVipsImageOperationException("Failed to load input image.")
        }

        image = baseImage
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

        return image.writeToBufferOperation(params, memScope)
    }

    override fun dispose() {
        // this might cause: g_object_unref: assertion 'G_IS_OBJECT (object)' failed due to duplicate
        // unref after line 77 in case user caught and consumed internal exception with dispose call
        // this won't affect the app and is safer that lack of deallocating memory in case there's exception
        // on the user side without finalize/dispose call and freeing memory
        image.unref()
        data.unpin()
    }

    private fun performOperation(
        operation: KVipsImageOperation,
        params: KVipsImageOperationParams
    ): KVipsImageOperations {
        val outputImage = memScope.allocPointerTo<VipsImage>()
        try {
            operation.invoke(params, image, outputImage, memScope)
            outputImage.value?.let {
                image.unref()
                image = it
            }
            return this
        } catch (exception: Exception) {
            image.unref()
            throw exception
        }
    }
}