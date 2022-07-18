package io.github.versi.kvips.image

import io.github.versi.kvips.image.buffer.KVipsImageOperationResult
import kotlin.math.roundToInt

private const val RGB_WHITE = "#FFFFFF"

open class KVipsImageOperationException(message: String) : Exception(message)

data class KVipsImageQuality(val value: Int = 75) {
    init {
        require(value in 0..100) {
            "Failed to initialize. KVipsImageQuality must be in <0, 100> range."
        }
    }
}

data class KVipsImageOutputParams(
    val format: KVipsImageFormat?,
    val quality: KVipsImageQuality = KVipsImageQuality(),
    val stripMetadata: Boolean = true
)

sealed class KVipsImageOperationParams
data class KVipsImageThumbnailOperationParams(
    val width: Int,
    val height: Int = 0,
    val crop: Boolean = false,
    val force: Boolean = false
) : KVipsImageOperationParams()

data class KVipsImageScaleOperationParams(
    val hScale: Double,
    val vScale: Double
) : KVipsImageOperationParams()

data class KVipsImageCropOperationParams(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int
) : KVipsImageOperationParams() {

    fun fitInImage(imageWidth: Int, imageHeight: Int): Boolean {
        if (width > imageWidth || (left + width) > imageWidth) {
            return false
        }
        if (height > imageHeight || (top + height) > imageHeight) {
            return false
        }
        return true
    }
}

sealed class KVipsImageOverlaySize

data class KVipsPercentImageOverlaySize(val widthPercent: Float, val heightPercent: Float? = null) :
    KVipsImageOverlaySize() {
    init {
        require(widthPercent > 0f && widthPercent <= 1f) {
            "Failed to initialize. WidthPercent must be greater than 0 and less or equal 1."
        }
        heightPercent?.let {
            require(it > 0f && it <= 1f) {
                "Failed to initialize. HeightPercent if set - must be greater than 0 and less or equal 1."
            }
        }
    }
}

data class KVipsAbsoluteImageOverlaySize(val width: Float, val height: Float? = null) :
    KVipsImageOverlaySize()

sealed class KVipsImageOverlayMargin

data class KVipsPercentImageOverlayMargin(
    val verticalMarginPercent: Float = 0f,
    val horizontalMarginPercent: Float = 0f
) : KVipsImageOverlayMargin()

data class KVipsAbsoluteImageOverlayMargin(
    val verticalMargin: Float = 0f,
    val horizontalMargin: Float = 0f
) : KVipsImageOverlayMargin()

data class KVipsImageOverlaySizeAndMargin(
    val size: KVipsImageOverlaySize,
    val margin: KVipsImageOverlayMargin
)

sealed class KVipsImageOverlayOperationParams(
    open val overlaySizeAndMargin: KVipsImageOverlaySizeAndMargin,
    open val verticalAlign: VerticalAlign = VerticalAlign.TOP,
    open val horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT
) : KVipsImageOperationParams() {

    enum class VerticalAlign {
        TOP, CENTER, BOTTOM
    }

    enum class HorizontalAlign {
        LEFT, CENTER, RIGHT
    }

    fun getWidth(sourceImageWidth: Int): Int {
        return with(overlaySizeAndMargin.size) {
            when (this) {
                is KVipsPercentImageOverlaySize -> (sourceImageWidth * this.widthPercent).roundToInt()
                is KVipsAbsoluteImageOverlaySize -> {
                    val targetWidth = this.width.roundToInt()
                    require(targetWidth < sourceImageWidth) {
                        "Target width: $targetWidth must not be bigger than source image width: $sourceImageWidth"
                    }
                    targetWidth
                }
            }
        }
    }

    fun getHeight(sourceImageHeight: Int): Int? {
        return with(overlaySizeAndMargin.size) {
            when (this) {
                is KVipsPercentImageOverlaySize -> this.heightPercent?.let {
                    (sourceImageHeight * it).roundToInt()
                }
                is KVipsAbsoluteImageOverlaySize -> {
                    val targetHeight = this.height?.roundToInt()
                    targetHeight?.let {
                        require(targetHeight < sourceImageHeight) {
                            "Target height: $targetHeight must not be bigger than source image height: $sourceImageHeight"
                        }
                    }
                    targetHeight
                }
            }
        }
    }

    fun getOverlayX(overlayWidth: Int, sourceImageWidth: Int): Int {
        val horizontalMargin = getHorizontalMargin(sourceImageWidth)
        val overlayX = when (horizontalAlign) {
            HorizontalAlign.LEFT -> horizontalMargin
            HorizontalAlign.CENTER -> sourceImageWidth / 2 - overlayWidth / 2
            HorizontalAlign.RIGHT -> sourceImageWidth - overlayWidth - horizontalMargin
        }
        return overlayX
    }

    fun getOverlayY(overlayHeight: Int, sourceImageHeight: Int): Int {
        val verticalMargin = getVerticalMargin(sourceImageHeight)
        return when (verticalAlign) {
            VerticalAlign.TOP -> verticalMargin
            VerticalAlign.CENTER -> sourceImageHeight / 2 - overlayHeight / 2
            VerticalAlign.BOTTOM -> sourceImageHeight - overlayHeight - verticalMargin
        }
    }

    fun getThumbnailParams(sourceImageWidth: Int, sourceImageHeight: Int): KVipsImageThumbnailOperationParams {
        return KVipsImageThumbnailOperationParams(
            width = getWidth(sourceImageWidth),
            // if height is not set then we set height to 0 to resolve it in a way to keep aspect ratio
            height = getHeight(sourceImageHeight) ?: 0,
            // if widthPercent == 1.0f we enable canvas-overlay mode with crop
            crop = with(overlaySizeAndMargin.size) {
                this is KVipsPercentImageOverlaySize && this.widthPercent == 1.0f
            })
    }

    private fun getHorizontalMargin(sourceImageWidth: Int): Int {
        return with(overlaySizeAndMargin.margin) {
            when (this) {
                is KVipsPercentImageOverlayMargin -> (sourceImageWidth * this.horizontalMarginPercent).roundToInt()
                is KVipsAbsoluteImageOverlayMargin -> {
                    val margin = this.horizontalMargin.roundToInt()
                    require(margin < sourceImageWidth) {
                        "Horizontal margin: $margin must be smaller then source image width: $sourceImageWidth"
                    }
                    margin
                }
            }
        }
    }

    private fun getVerticalMargin(sourceImageHeight: Int): Int {
        return with(overlaySizeAndMargin.margin) {
            when (this) {
                is KVipsPercentImageOverlayMargin -> (sourceImageHeight * this.verticalMarginPercent).roundToInt()
                is KVipsAbsoluteImageOverlayMargin -> {
                    val margin = this.verticalMargin.roundToInt()
                    require(margin < sourceImageHeight) {
                        "Vertical margin: $margin must be smaller then source image height: $sourceImageHeight"
                    }
                    margin
                }
            }
        }
    }
}

data class KVipsImageTextOperationParams(
    private val textContent: String,
    private val textColor: String = RGB_WHITE,
    private val shadowColor: String? = null,
    override val overlaySizeAndMargin: KVipsImageOverlaySizeAndMargin,
    override val verticalAlign: VerticalAlign = VerticalAlign.TOP,
    override val horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT
) : KVipsImageOverlayOperationParams(
    overlaySizeAndMargin,
    verticalAlign,
    horizontalAlign
) {

    val text: String = "<span fgcolor='$textColor' bgalpha='100%'>$textContent</span>"
    val shadowText = if (shadowColor.isNullOrBlank()) null else {
        "<span fgcolor='$shadowColor' bgalpha='100%'>$textContent</span>"
    }
}

sealed class KVipsImageComposeOperationParams(
    override val overlaySizeAndMargin: KVipsImageOverlaySizeAndMargin,
    override val verticalAlign: VerticalAlign = VerticalAlign.TOP,
    override val horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT
) : KVipsImageOverlayOperationParams(
    overlaySizeAndMargin,
    verticalAlign,
    horizontalAlign
)

class KVipsImageComposeFileOperationParams(
    val filePath: String, override val overlaySizeAndMargin: KVipsImageOverlaySizeAndMargin,
    override val verticalAlign: VerticalAlign = VerticalAlign.TOP,
    override val horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT
) :
    KVipsImageComposeOperationParams(
        overlaySizeAndMargin,
        verticalAlign,
        horizontalAlign
    )

class KVipsImageComposeByteArrayOperationParams(
    val overlayData: ByteArray, override val overlaySizeAndMargin: KVipsImageOverlaySizeAndMargin,
    override val verticalAlign: VerticalAlign = VerticalAlign.TOP,
    override val horizontalAlign: HorizontalAlign = HorizontalAlign.LEFT
) :
    KVipsImageComposeOperationParams(
        overlaySizeAndMargin,
        verticalAlign,
        horizontalAlign
    )

class KVipsImageComposeGradientOperationParams(
    gradients: List<Gradient>
) :
    KVipsImageComposeOperationParams(
        KVipsImageOverlaySizeAndMargin(
            size = KVipsPercentImageOverlaySize(
                1.0f,
                heightPercent = 1.0f
            ),
            margin = KVipsPercentImageOverlayMargin(
                0f,
                0f
            )
        ),
        VerticalAlign.TOP,
        HorizontalAlign.LEFT
    ) {

    /**
     * Based on: https://angrytools.com/gradient/
     */
    private val gradientSvgSecondPart = """
          <defs>
            ${gradients.toSvg()}
          </defs>
          ${gradients.toRects()}
        </svg>
    """.trimIndent()

    sealed class Gradient(open val id: String, open val offsets: List<GradientOffset>) {

        protected fun List<GradientOffset>.toSvgString(): String {
            return this.sortedBy { it.offset }.joinToString(separator = "\n") {
                """<stop offset="${it.offset}%" style="stop-color:${it.color}" />"""
            }
        }
    }

    data class LinearGradient(
        override val id: String,
        override val offsets: List<GradientOffset>,
        val direction: Direction = Direction.Top
    ) : Gradient(id, offsets) {

        sealed class Direction(val x1: Int, val y1: Int, val x2: Int, val y2: Int) {
            object TopLeft : Direction(0, 0, 100, 100)
            object Top : Direction(50, 0, 50, 100)
            object TopRight : Direction(100, 0, 0, 100)
            object Left : Direction(0, 50, 100, 50)
            object Right : Direction(100, 50, 0, 50)
            object BottomLeft : Direction(0, 100, 100, 0)
            object Bottom : Direction(50, 100, 50, 0)
            object BottomRight : Direction(100, 100, 0, 0)
            class Custom(x1: Int, y1: Int, x2: Int, y2: Int) : Direction(x1, y1, x2, y2) {
                init {
                    checkCoordinate(x1, "x1")
                    checkCoordinate(y1, "y1")
                    checkCoordinate(x2, "x2")
                    checkCoordinate(y2, "y2")
                }

                private fun checkCoordinate(value: Int, name: String) {
                    require(value in 0..100) {
                        "Gradient coordinate percentage: $name must be in range <0, 100>"
                    }
                }
            }
        }

        override fun toString(): String {
            return """
            <linearGradient id="$id" x1="${direction.x1}%" y1="${direction.y1}%" x2="${direction.x2}%" y2="${direction.y2}%" >
                ${offsets.toSvgString()}
            </linearGradient>
            """.trimIndent()
        }
    }

    data class RadialGradient(
        override val id: String,
        override val offsets: List<GradientOffset>,
        private val params: Params
    ) : Gradient(id, offsets) {

        sealed class Point(
            open val x: Double,
            open val y: Double
        ) {
            init {
                require(x in 0.0..100.0)
                {
                    "RadialGradient Point is a percentage value: X $x must be in range <0, 100>"
                }
                require(y in 0.0..100.0)
                {
                    "RadialGradient Point is a percentage value: Y $y must be in range <0, 100>"
                }
            }
        }

        data class CenterPoint(override val x: Double, override val y: Double) : Point(x, y)
        data class FocalPoint(override val x: Double, override val y: Double) : Point(x, y)

        data class Params(val radius: Double, val centerPoint: CenterPoint, val focalPoint: FocalPoint) {
            init {
                require(radius in 0.0..100.0)
                {
                    "RadialGradient radius is a percentage value: $radius must be in range <0, 100>"
                }
            }
        }

        override fun toString(): String {
            return """
            <radialGradient id="$id" cx="${params.centerPoint.x}%" cy="${params.centerPoint.y}%" r="${params.radius}%">
                ${offsets.toSvgString()}
            </radialGradient>
            """.trimIndent()
        }
    }

    data class GradientOffset(val color: String, val offset: Int) {
        init {
            require(isRGB(color)) {
                "Gradient color should be defined in RGB/RGBA color format"
            }
            require(offset in 0..100) {
                "Gradient offset should be in <0, 100> range"
            }
        }

        companion object {
            private val rgbRegex = Regex("^#([a-fA-F0-9]{3}|[a-fA-F0-9]{6}|[a-fA-F0-9]{8})$")

            fun isRGB(color: String) = rgbRegex.matches(color)
        }
    }

    fun toSvg(width: Int, height: Int): String {
        return getSvgSizePart(width, height) + gradientSvgSecondPart
    }

    private fun List<Gradient>.toSvg(): String {
        return this.joinToString(separator = "\n") { it.toString() }
    }

    private fun List<Gradient>.toRects(): String {
        return this.joinToString(separator = "\n") {
            """
                <rect x="0" y="0" width="100%" height="100%" fill="url(#${it.id})"/>
            """.trimIndent()
        }
    }

    private fun getSvgSizePart(width: Int, height: Int): String {
        return """<svg xmlns="http://www.w3.org/2000/svg" width="${width}px" height="${height}px">"""
    }
}

interface KVipsImageOperations {

    fun thumbnail(params: KVipsImageThumbnailOperationParams): KVipsImageOperations

    fun scale(params: KVipsImageScaleOperationParams): KVipsImageOperations

    fun crop(params: KVipsImageCropOperationParams): KVipsImageOperations

    fun compose(params: KVipsImageComposeOperationParams): KVipsImageOperations

    fun text(params: KVipsImageTextOperationParams): KVipsImageOperations

    fun finalize(params: KVipsImageOutputParams): KVipsImageOperationResult

    /**
     * Deallocates all memory, safe to be called multiple times, should be called whenever finalize could not be called, e.x.
     * when exception breaks the operation chain
     */
    fun dispose()
}

inline fun <T : KVipsImageOperations?, R> T.use(block: (T) -> R): R {
    try {
        return block.invoke(this)
    } catch (exception: Exception) {
        this?.dispose()
        throw exception
    }
}