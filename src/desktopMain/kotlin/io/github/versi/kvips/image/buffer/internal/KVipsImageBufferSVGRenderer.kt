package io.github.versi.kvips.image.buffer.internal

import io.github.versi.kvips.image.KVipsImageOutputParams
import io.github.versi.kvips.image.buffer.KVipsImageOperationResult
import io.github.versi.kvips.image.internal.asSvg
import io.github.versi.kvips.image.internal.writeToBufferOperation
import kotlinx.cinterop.*

/**
 * https://angrytools.com/gradient/ - tool for outputting SVG
 */
class KVipsImageBufferSVGRenderer {

    fun render(svgText: String, outputImageOutputParams: KVipsImageOutputParams): KVipsImageOperationResult {
        memScoped {
            val svgImage = svgText.asSvg(this)
            return svgImage.writeToBufferOperation(
                params = outputImageOutputParams,
                inputData = null,
                memScope = this
            )
        }
    }
}
