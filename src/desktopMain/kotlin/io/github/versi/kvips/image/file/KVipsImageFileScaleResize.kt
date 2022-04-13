package io.github.versi.kvips.image.file

import io.github.versi.kvips.image.KVipsImageOperationException
import io.github.versi.kvips.image.KVipsImageScaleOperationParams
import io.github.versi.kvips.image.internal.asVipsImage
import io.github.versi.kvips.image.internal.resize
import io.github.versi.kvips.image.internal.unref
import io.github.versi.kvips.image.internal.writeToFile
import io.github.versi.kvips.libvips.*
import kotlinx.cinterop.allocPointerTo
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.value

class KVipsImageFileScaleResize(private val scaleParams: KVipsImageScaleOperationParams) : KVipsImageFileOperation {

    override fun run(params: KVipsImageFileOperationParams): KVipsImageFileOperationResult {
        memScoped {
            defer {
                vips_thread_shutdown()
            }
            val sourceImage = params.inputImagePath.asVipsImage()
            val resizedImage = allocPointerTo<VipsImage>()
            try {
                sourceImage?.resize(scaleParams, resizedImage)
            } catch (exception: KVipsImageOperationException) {
                return KVipsImageFileOperationError(exception)
            } finally {
                sourceImage.unref()
            }

            try {
                resizedImage.writeToFile(params.outputImagePath)
            } catch (exception: KVipsImageOperationException) {
                return KVipsImageFileOperationError(exception)
            } finally {
                resizedImage.value.unref()
            }

            return KVipsImageFileOperationSuccess
        }
    }
}