package io.github.versi.kvips.image.internal

import io.github.versi.kvips.libvips.VipsImage
import io.github.versi.kvips.libvips.VipsObject
import io.github.versi.kvips.libvips.vips_image_new
import io.github.versi.kvips.libvips.vips_object_local_array
import kotlinx.cinterop.*

internal interface KVipsImagesPool {

    fun getImage(): CPointerVar<VipsImage>

    fun dispose()
}

class KVipsImagesPoolException(message: String) : Exception(message)

internal class DefaultKVipsImagesPool(private val size: Int) : KVipsImagesPool {

    private val vipsContext = vips_image_new()?.reinterpret<VipsObject>()
    private val imagesPool = vips_object_local_array(vipsContext, size)?.reinterpret<CPointerVar<VipsImage>>()
    private var index = -1

    override fun getImage(): CPointerVar<VipsImage> {
        index++
        if (index == size) {
            throw KVipsImagesPoolException("Pool with size: $size has no more images")
        }
        return imagesPool.plus(index)?.pointed
            ?: throw KVipsImagesPoolException("Failed to retrieve image index: $index from images pool")
    }

    override fun dispose() {
        vipsContext.unref()
    }
}