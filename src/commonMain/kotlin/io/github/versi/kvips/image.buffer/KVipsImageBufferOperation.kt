package io.github.versi.kvips.image.buffer

import io.github.versi.kvips.image.KVipsImageOperationException
import io.github.versi.kvips.image.KVipsImageOutputParams

interface KVipsImageBufferOperation {

    fun run(params: KVipsImageBufferOperationParams): KVipsImageOperationResult
}

data class KVipsImageBufferOperationParams(
    val inputImage: ByteArray,
    val outputImageParams: KVipsImageOutputParams
)

sealed class KVipsImageOperationResult
data class KVipsImageOperationError(val exception: KVipsImageOperationException) :
    KVipsImageOperationResult()

data class KVipsImageOperationSuccess(val outputImage: ByteArray) : KVipsImageOperationResult()