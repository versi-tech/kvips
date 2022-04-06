package io.github.versi.kvips.image

sealed class KVipsImageFormat(val extension: String) {

    val name: String = extension.lowercase().substring(1)

    companion object {

        fun fromName(name: String): KVipsImageFormat {
            return when (name.lowercase()) {
                KVipsImageJPG.name,
                KVipsImageJPG.altName -> KVipsImageJPG
                KVipsImagePNG.name -> KVipsImagePNG
                KVipsImageGIF.name -> KVipsImageGIF
                else -> throw KVipsImageFormatNotSupportedException(name)
            }
        }
    }

    override fun toString(): String {
        return name
    }
}

object KVipsImageJPG : KVipsImageFormat(".jpg") {
    const val altName = "jpeg"
}

object KVipsImagePNG : KVipsImageFormat(".png")
object KVipsImageGIF : KVipsImageFormat(".gif")

class KVipsImageFormatNotSupportedException(formatName: String) :
    Exception("Image format $formatName is not supported.")