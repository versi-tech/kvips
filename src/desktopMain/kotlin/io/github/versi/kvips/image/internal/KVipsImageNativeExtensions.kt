package io.github.versi.kvips.image.internal

import io.github.versi.kvips.image.*
import io.github.versi.kvips.libvips.*
import io.github.versi.kvips.image.buffer.KVipsImageOperationError
import io.github.versi.kvips.image.buffer.KVipsImageOperationResult
import io.github.versi.kvips.image.buffer.KVipsImageOperationSuccess
import kotlinx.cinterop.*
import platform.posix.NULL
import platform.posix.size_tVar

//great source: https://github.com/libvips/libvips/issues/1449 of knowledge!
@OptIn(ExperimentalUnsignedTypes::class)
internal fun Pinned<UByteArray>.asVipsImage(dataSize: Int, loadAllFrames: Boolean = false): CPointer<VipsImage>? {
    val inputImageData = this.addressOf(0)
    return if (loadAllFrames) {
        vips_image_new_from_buffer(
            inputImageData,
            dataSize.convert(),
            "n=-1",
            NULL
        )
    } else {
        vips_image_new_from_buffer(
            inputImageData,
            dataSize.convert(),
            null,
            NULL
        )
    }
}

// https://github.com/libvips/libvips/wiki/HOWTO----Image-shrinking
// important improvements to introduce:
// Use best image formats encoders/decoders, for JPEG: libjpeg-turbo and for gif: cgif
// checking if libjpeg-turbo is being used: https://github.com/libvips/pyvips/issues/229
// source: https://github.com/libvips/libvips/issues/1449, https://www.libvips.org/install.html
// https://stackoverflow.com/questions/61396796/how-to-compile-libvips-with-libjpeg-turbo8-instead-of-libjpeg
@OptIn(ExperimentalUnsignedTypes::class)
internal fun Pinned<UByteArray>.thumbnailBuffer(
    params: KVipsImageThumbnailOperationParams,
    memScope: MemScope
): CPointerVar<VipsImage> {
    val inputImageData = this.addressOf(0)
    val image = memScope.allocPointerTo<VipsImage>()
    val (width, height) = params.resolveDimensions()
    if (vips_thumbnail_buffer(
            inputImageData,
            this.get().size.convert(),
            image.ptr,
            width,
            "height", height, "crop", params.crop.asCropType(),
            "size", params.force.forceAsVipsSize(),
            NULL
        ) != 0
    ) {
        throw KVipsNativeImageOperationException("Failed to resize image")
    }
    return image
}

/**
 * resize multi-page/frames image format like: GIF, PDF
 */
internal fun CPointer<UByteVar>.thumbnailBufferFrames(
    params: KVipsImageThumbnailOperationParams,
    imageSize: Int,
    outputImage: CPointerVar<VipsImage>
) {
    val (width, height) = params.resolveDimensions()
    if (vips_thumbnail_buffer(
            this,
            imageSize.convert(),
            outputImage.ptr,
            width,
            "n", -1, "height", height, "crop", params.crop.asCropType(),
            "size", params.force.forceAsVipsSize(),
            NULL
        ) != 0
    ) {
        throw KVipsNativeImageOperationException("Failed to resize image")
    }
}

internal fun CPointer<VipsImage>.thumbnail(
    params: KVipsImageThumbnailOperationParams,
    outputImage: CPointerVar<VipsImage>
) {
    val (width, height) = params.resolveDimensions()
    if (vips_thumbnail_image(
            this,
            outputImage.ptr,
            width,
            "height", height, "crop", params.crop.asCropType(),
            "size", params.force.forceAsVipsSize(),
            NULL
        ) != 0
    ) {
        throw KVipsNativeImageOperationException("Failed to resize image")
    }
}

internal fun CPointer<VipsImage>.gaussBlur(
    sigma: Double,
    outputImage: CPointerVar<VipsImage>
) {
    if (vips_gaussblur(
            this,
            outputImage.ptr,
            sigma,
            NULL
        ) != 0
    ) {
        throw KVipsNativeImageOperationException("Failed to perform gauss blur on image")
    }
}

internal fun CPointerVar<VipsImage>.asText(
    text: String,
    width: Int,
    height: Int?
) {
    if (height != null) {
        val textHeight: Int = height
        if (vips_text(
                this.ptr,
                text,
                "width", width, "height", textHeight,
                "rgba", true,
                NULL
            ) != 0
        ) {
            throw KVipsNativeImageOperationException("Failed to generate image with text: $text")
        }
    } else {
        if (vips_text(
                this.ptr,
                text,
                "width", width,
                "rgba", true,
                NULL
            ) != 0
        ) {
            throw KVipsNativeImageOperationException("Failed to generate image with text: $text")
        }
    }
}

internal fun String.asSvg(
    memScope: MemScope
): CPointerVar<VipsImage> {
    val svgImage = memScope.allocPointerTo<VipsImage>()
    if (vips_svgload_string(
            this,
            svgImage.ptr,
            NULL
        ) != 0
    ) {
        throw KVipsNativeImageOperationException("Failed to generate gradient image from text: $this")
    }
    return svgImage
}

internal fun String.asVipsImage(): CPointer<VipsImage>? {
    return vips_image_new_from_file(
        this,
        "access", VipsAccess.VIPS_ACCESS_SEQUENTIAL,
        NULL
    )
}

internal fun String.thumbnail(
    params: KVipsImageThumbnailOperationParams,
    memScope: MemScope
): CPointerVar<VipsImage> {
    val image = memScope.allocPointerTo<VipsImage>()
    val (width, height) = params.resolveDimensions()
    if (vips_thumbnail(
            this,
            image.ptr,
            width,
            "height", height, "crop", params.crop.asCropType(),
            "size", params.force.forceAsVipsSize(),
            NULL
        ) != 0
    ) {
        throw KVipsNativeImageOperationException("Failed to resize image from file")
    }
    return image
}

internal fun CPointer<VipsImage>.resize(params: KVipsImageScaleOperationParams, outputImage: CPointerVar<VipsImage>) {
    if (vips_resize(
            this,
            outputImage.ptr,
            params.hScale, "vscale", params.vScale, "kernel", 1, NULL
        ) != 0
    ) {
        throw KVipsNativeImageOperationException("Failed to resize image")
    }
}

internal fun CPointer<VipsImage>.crop(cropParams: KVipsImageCropOperationParams, outputImage: CPointerVar<VipsImage>) {
    if (vips_extract_area(
            this,
            outputImage.ptr,
            left = cropParams.left,
            top = cropParams.top,
            width = cropParams.width,
            height = cropParams.height,
            NULL
        ) != 0
    ) {
        throw KVipsNativeImageOperationException("Failed to crop image")
    }
}

internal fun CPointer<VipsImage>.compose(
    overlayImage: CPointer<VipsImage>,
    outputImage: CPointerVar<VipsImage>,
    overlayX: Int? = null,
    overlayY: Int? = null,
    blendMode: VipsBlendMode = VipsBlendMode.VIPS_BLEND_MODE_OVER
) {
    if (overlayX != null && overlayY != null) {
        val xPos: Int = overlayX
        val yPos: Int = overlayY
        if (vips_composite2(
                this,
                overlayImage,
                outputImage.ptr,
                blendMode,
                "x", xPos, "y", yPos,
                NULL
            ) != 0
        ) {
            throw KVipsNativeImageOperationException("Failed to compose images")
        }
    } else {
        if (vips_composite2(
                this,
                overlayImage,
                outputImage.ptr,
                blendMode,
                NULL
            ) != 0
        ) {
            throw KVipsNativeImageOperationException("Failed to compose images")
        }
    }
}

internal fun CPointer<VipsImage>.embed(
    outputImage: CPointerVar<VipsImage>,
    x: Int,
    y: Int,
    width: Int,
    height: Int
) {
    if (vips_embed(
            this,
            outputImage.ptr,
            x,
            y,
            width,
            height,
            NULL
        ) != 0
    ) {
        throw KVipsNativeImageOperationException("Failed to embed image")
    }
}

internal fun CPointerVar<VipsImage>.writeToBufferOperation(
    params: KVipsImageOutputParams,
    inputData: Pinned<UByteArray>?,
    memScope: MemScope
): KVipsImageOperationResult {
    return this.value!!.writeToBufferOperation(params, inputData, memScope)
}

internal fun CPointer<VipsImage>.writeToBufferOperation(
    params: KVipsImageOutputParams,
    inputData: Pinned<UByteArray>?,
    memScope: MemScope
): KVipsImageOperationResult {
    val imageBuffer = memScope.alloc<COpaquePointerVar>()
    val imageSize = memScope.alloc<size_tVar>()
    try {
        this.writeToBuffer(
            imageBuffer,
            imageSize,
            params,
            memScope
        )
        imageBuffer.value?.let { bytes ->
            return KVipsImageOperationSuccess(bytes.readBytes(imageSize.value.toInt()))
        }

        return KVipsImageOperationError(
            KVipsNativeImageOperationException(
                "Failed during conversion of bytes data in buffer scaling"
            )
        )
    } catch (exception: KVipsImageOperationException) {
        return KVipsImageOperationError(exception)
    } finally {
        inputData?.unpin()
        this.unref()
        g_free(imageBuffer.value)
    }
}

private fun CValuesRef<VipsImage>.writeToBuffer(
    buffer: COpaquePointerVar,
    imageSize: size_tVar,
    outputImageParams: KVipsImageOutputParams,
    memScope: MemScope
) {
    val strip = if (outputImageParams.stripMetadata) TRUE else FALSE
    val outputFormat = outputImageParams.format ?: this.getFormat(memScope)
    if (outputFormat == KVipsImageGIF || outputFormat == KVipsImageBMP) {
        // vips_gifsave_buffer used under the hood for GIF does not support Quality param currently:
        // https://www.libvips.org/API/current/VipsForeignSave.html#vips-gifsave-buffer
        // same case with BMP:  magicksave_bmp_buffer: no property named `Q'
        if (vips_image_write_to_buffer(
                this,
                outputFormat.extension,
                buffer.ptr,
                imageSize.ptr,
                "strip",
                strip,
                NULL
            ) != 0
        ) {
            throw KVipsNativeImageOperationException("Failed to save image to buffer with params: $outputImageParams")
        }
    } else {
        if (vips_image_write_to_buffer(
                this,
                outputFormat.extension,
                buffer.ptr,
                imageSize.ptr,
                "Q",
                outputImageParams.quality.value,
                "strip",
                strip,
                NULL
            ) != 0
        ) {
            throw KVipsNativeImageOperationException("Failed to save image to buffer with params: $outputImageParams")
        }
    }
}

internal fun CValuesRef<VipsImage>.writeToFile(
    outputImagePath: String,
    stripMetadata: Boolean = false
) {
    if (vips_image_write_to_file(
            this,
            outputImagePath,
            "strip",
            stripMetadata
        ) != 0
    ) {
        throw KVipsNativeImageOperationException(
            "Failed to save image to file: $outputImagePath"
        )
    }
}

internal fun CPointerVar<VipsImage>.writeToFile(
    outputImagePath: String,
    stripMetadata: Boolean = false
) {
    this.value?.writeToFile(outputImagePath, stripMetadata)
}

internal fun COpaquePointer.readUBytes(count: Int): ByteArray {
    val result = ByteArray(count)
    val sourceArray = this.reinterpret<guint8Var>()
    var index = 0
    while (index < count) {
        result[index] = sourceArray[index].toByte()
        ++index
    }
    return result
}

/**
 * Based on: https://www.libvips.org/API/current/libvips-header.html#VIPS-META-LOADER:CAPS
 */
internal fun CValuesRef<VipsImage>.getFormat(memScope: MemScope): KVipsImageFormat {
    if (vips_image_get_typeof(this, VIPS_META_LOADER).toUInt() != 0U) {
        val loaderString = memScope.allocPointerTo<ByteVar>()
        vips_image_get_string(this, VIPS_META_LOADER, loaderString.ptr)
        val loaderName = loaderString.value?.toKString()
        loaderName?.let {
            return KVipsImageFormat.fromLoader(it)
        }
        throw KVipsImageFormatReadException()
    }
    throw KVipsImageFormatReadException()
}

// source: https://www.libvips.org/API/current/libvips-conversion.html#VipsInteresting
private fun Boolean.asCropType() = if (this) {
    // using the default - center, as in bimg by default: https://github.com/h2non/bimg/blob/b29a573563382fe062a02c006e7dace89c611b0d/resizer.go#L548
    VipsInteresting.VIPS_INTERESTING_CENTRE
} else {
    VipsInteresting.VIPS_INTERESTING_NONE
}

// source: https://www.libvips.org/API/8.9/libvips-resample.html#VipsSize
private fun Boolean.forceAsVipsSize() = if (this) {
    // if true then force resizing image to passed width and height
    VipsSize.VIPS_SIZE_FORCE
} else {
    VipsSize.VIPS_SIZE_BOTH
}

inline fun <reified T : CPointed> CPointer<T>?.unref() {
    this?.let {
        g_object_unref(it.reinterpret())
    }
}

/**
 * if one dimension size is 0 and another not 0 then we set width VIPS_MAX_COORD of 0-set dimension to indicate we want exact
 * size of non-zero dimension and second one to keep the aspect ratio
 * source: https://github.com/libvips/libvips/pull/1639
 */
private fun KVipsImageThumbnailOperationParams.resolveDimensions(): Pair<Int, Int> {
    val targetWidth = if (height != 0 && width == 0) {
        VIPS_MAX_COORD
    } else {
        width
    }
    val targetHeight = if (width != 0 && height == 0) {
        VIPS_MAX_COORD
    } else {
        height
    }
    return Pair(targetWidth, targetHeight)
}

fun vipsBufferCleaner(image: CPointer<VipsImage>?, imageBuffer: COpaquePointer?) {
    g_free(imageBuffer)
}

/**
 * Based on: https://gist.github.com/kropp/9b8b9578b9421e932f932bb6aed9598a
 */
// Note that all callback parameters must be primitive types or nullable C pointers.
fun <F : CFunction<*>> gSignalConnect(
    obj: CPointer<*>, actionName: String,
    action: CPointer<F>, data: gpointer? = null, connectFlags: GConnectFlags = 0U
) {
    g_signal_connect_data(
        obj.reinterpret(), actionName, action.reinterpret(),
        data = data, destroy_data = null, connect_flags = connectFlags
    )
}
