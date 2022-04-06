package io.github.versi.kvips.internal

import io.github.versi.kvips.image.*
import io.github.versi.kvips.image.internal.*
import io.github.versi.kvips.libvips.VipsBlendMode
import io.github.versi.kvips.libvips.VipsImage
import io.github.versi.kvips.image.internal.crop
import io.github.versi.kvips.image.internal.resize
import io.github.versi.kvips.image.internal.thumbnail
import kotlinx.cinterop.*

typealias KVipsImageOperation = (KVipsImageOperationParams, CPointer<VipsImage>, CPointerVar<VipsImage>, MemScope) -> Unit

// based on: https://github.com/libvips/libvips/discussions/2385
internal val textOperation: KVipsImageOperation = { params, image, outputImage, _ ->
    val imagesPool = DefaultKVipsImagesPool(5)
    try {
        val textParams = params as KVipsImageTextOperationParams
        val overlayImageWidth = image.pointed.Xsize
        val overlayImageHeight = image.pointed.Ysize
        val requestTextWidth = textParams.getWidth(overlayImageWidth)
        val requestTextHeight = textParams.getHeight(overlayImageHeight)
        val textImage = imagesPool.getImage()
        textImage.asText(textParams.text, requestTextWidth, requestTextHeight)
        val outputText = textImage.addTextShadow(textParams, requestTextWidth, requestTextHeight, imagesPool)
        val resultTextWidth = outputText.pointed?.Xsize
            ?: throw KVipsImageOperationException("Text: failed to retrieve text image width.")
        val resultTextHeight = outputText.pointed?.Ysize
            ?: throw KVipsImageOperationException("Text: failed to retrieve text image height.")
        val overlayX = params.getOverlayX(resultTextWidth, overlayImageWidth)
        val overlayY = params.getOverlayY(resultTextHeight, overlayImageHeight)
        outputText.value?.let {
            image.compose(
                it,
                outputImage,
                overlayX,
                overlayY
            )
        } ?: throw KVipsImageOperationException("Text: failed to generate text image.")
    } finally {
        imagesPool.dispose()
    }
}

@OptIn(ExperimentalUnsignedTypes::class)
internal val composeOperation: KVipsImageOperation = { params, image, outputImage, memScope ->
    var overlayImage: CPointer<VipsImage>? = null
    try {
        val composeParams = params as KVipsImageComposeOperationParams
        val sourceImageWidth = image.pointed.Xsize
        val sourceImageHeight = image.pointed.Ysize
        val thumbnailParams = composeParams.getThumbnailParams(
            sourceImageWidth = sourceImageWidth,
            sourceImageHeight = sourceImageHeight
        )
        overlayImage = when (composeParams) {
            is KVipsImageComposeByteArrayOperationParams -> {
                composeParams.overlayData.toUByteArray().usePinned { pinnedImageData ->
                    pinnedImageData.thumbnailBuffer(thumbnailParams, memScope).value
                }
            }
            is KVipsImageComposeFileOperationParams -> {
                composeParams.filePath.thumbnail(thumbnailParams, memScope).value
            }
            is KVipsImageComposeGradientOperationParams -> {
                composeParams.toSvg(thumbnailParams.width, thumbnailParams.height).asSvg(memScope).value
            }
        }
        overlayImage?.let {
            val overlayImageWidth = overlayImage.pointed.Xsize
            val overlayImageHeight = overlayImage.pointed.Ysize
            val overlayX = params.getOverlayX(overlayImageWidth, sourceImageWidth)
            val overlayY = params.getOverlayY(overlayImageHeight, sourceImageHeight)
            image.compose(it, outputImage, overlayX, overlayY)
        } ?: throw KVipsImageOperationException("Compose: failed to load overlay image.")
    } finally {
        overlayImage.unref()
    }
}

internal val cropOperation: KVipsImageOperation = { params, image, outputImage, _ ->
    val cropParams = params as KVipsImageCropOperationParams
    val imageWidth = image.pointed.Xsize
    val imageHeight = image.pointed.Ysize
    if (!cropParams.fitInImage(imageWidth, imageHeight)) {
        throw KVipsImageOperationException("Crop params must fit in source animation.")
    }
    image.crop(cropParams, outputImage)
}

internal val thumbnailOperation: KVipsImageOperation = { params, image, outputImage, _ ->
    image.thumbnail(params as KVipsImageThumbnailOperationParams, outputImage)
}

internal val scaleOperation: KVipsImageOperation = { params, image, outputImage, _ ->
    image.resize(params as KVipsImageScaleOperationParams, outputImage)
}

private fun CPointerVar<VipsImage>.addTextShadow(
    textParams: KVipsImageTextOperationParams,
    requestTextWidth: Int,
    requestTextHeight: Int?,
    imagesPool: KVipsImagesPool,
): CPointerVar<VipsImage> {
    if (textParams.shadowText.isNullOrBlank()) {
        return this
    } else {
        val shadowImage = imagesPool.getImage()
        shadowImage.asText(textParams.shadowText, requestTextWidth, requestTextHeight)
        val enlargedImage = imagesPool.getImage()
        val shadowRadius = 4.0
        val shadowImageWidth = shadowImage.pointed?.Xsize ?: 0
        val shadowImageHeight = shadowImage.pointed?.Ysize ?: 0
        val shadowedWidth = (shadowImageWidth + 2 * shadowRadius).toInt()
        val shadowedHeight = (shadowImageHeight + 2 * shadowRadius).toInt()
        val textPos = shadowRadius.toInt()
        shadowImage.value?.embed(
            enlargedImage,
            textPos,
            textPos,
            shadowedWidth,
            shadowedHeight
        )
        val blurShadowImage = imagesPool.getImage()
        val outputText = imagesPool.getImage()
        enlargedImage.value?.gaussBlur(shadowRadius, blurShadowImage)
        this.value?.let { text ->
            blurShadowImage.value?.compose(
                text,
                outputText,
                overlayX = textPos,
                overlayY = textPos,
                blendMode = VipsBlendMode.VIPS_BLEND_MODE_OVER
            )
        } ?: throw KVipsImageOperationException("Text: failed to add shadow to text.")
        return outputText
    }
}