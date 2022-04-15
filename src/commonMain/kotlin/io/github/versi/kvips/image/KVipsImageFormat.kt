package io.github.versi.kvips.image

sealed class KVipsImageFormat(val extension: String) {

    abstract val loaderSuffix: String

    val name: String = extension.lowercase().substring(1)

    companion object {

        fun fromName(name: String): KVipsImageFormat {
            return when (name.lowercase()) {
                KVipsImageJPG.name,
                KVipsImageJPG.altName -> KVipsImageJPG
                KVipsImagePNG.name -> KVipsImagePNG
                KVipsImageGIF.name -> KVipsImageGIF
                KVipsImageTIFF.name -> KVipsImageTIFF
                KVipsImageWEBP.name -> KVipsImageWEBP
                KVipsImagePDF.name -> KVipsImagePDF
                KVipsImageSVG.name -> KVipsImageSVG
                else -> throw KVipsImageFormatNotSupportedException(name)
            }
        }

        fun fromLoader(loader: String): KVipsImageFormat {
            // there are 2 types of loaders:
            // from file, e.g: "jpegload"
            // from buffer, e.g: "jpegload_buffer"
            // source: https://github.com/libvips/libvips/blob/356edc3779c254fe9515fff38443da9827cf0f82/test/test-suite/test_foreign.py
            return when (loader.lowercase().split("_")[0]) {
                KVipsImageJPG.loaderSuffix -> KVipsImageJPG
                KVipsImagePNG.loaderSuffix -> KVipsImagePNG
                KVipsImageGIF.loaderSuffix -> KVipsImageGIF
                KVipsImageTIFF.loaderSuffix -> KVipsImageTIFF
                KVipsImageWEBP.loaderSuffix -> KVipsImageWEBP
                KVipsImagePDF.loaderSuffix -> KVipsImagePDF
                KVipsImageSVG.loaderSuffix -> KVipsImageSVG
                else -> throw KVipsImageFormatNotSupportedException(loader)
            }
        }
    }

    override fun toString(): String {
        return name
    }
}

object KVipsImageJPG : KVipsImageFormat(".jpg") {
    const val altName = "jpeg"
    override val loaderSuffix = "jpegload"
}

object KVipsImagePNG : KVipsImageFormat(".png") {
    override val loaderSuffix = "pngload"
}

object KVipsImageGIF : KVipsImageFormat(".gif") {
    override val loaderSuffix = "gifload"
}

object KVipsImageTIFF : KVipsImageFormat(".tiff") {
    override val loaderSuffix = "tiffload"
}

object KVipsImageWEBP : KVipsImageFormat(".webp") {
    override val loaderSuffix = "webpload"
}

object KVipsImagePDF : KVipsImageFormat(".pdf") {
    override val loaderSuffix = "pdfload"
}

object KVipsImageSVG : KVipsImageFormat(".svg") {
    override val loaderSuffix = "svgload"
}

class KVipsImageFormatNotSupportedException(formatName: String) :
    Exception("Image format $formatName is not supported.")

class KVipsImageFormatReadException : Exception("Can't read image format from image.")