package io.github.versi.kvips.image.buffer

import io.github.versi.kvips.image.internal.unref
import io.github.versi.kvips.libvips.*
import kotlinx.cinterop.*
import platform.posix.size_tVar

class KVipsImageBufferBlackImage {

    fun run(width: Int, height: Int): ByteArray {
        memScoped {
            val blackImage = allocPointerTo<VipsImage>()
            vips_black(blackImage.ptr, width, height)

            val imageBuffer = alloc<COpaquePointerVar>()
            val imageSize = alloc<size_tVar>()
            vips_image_write_to_buffer(blackImage.value, ".jpg", imageBuffer.ptr, imageSize.ptr)
            blackImage.value.unref()

            imageBuffer.value?.let { bytes ->
                val outputBytes = bytes.readBytes(imageSize.value.toInt())
                g_free(imageBuffer.value)
                return outputBytes
            }
            return ByteArray(0)
        }
    }
}