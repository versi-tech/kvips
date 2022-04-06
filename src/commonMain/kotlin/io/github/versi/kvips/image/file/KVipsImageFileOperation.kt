package io.github.versi.kvips.image.file

import io.github.versi.kvips.image.KVipsImageOperationException
import io.github.versi.kvips.image.KVipsImageOutputParams

interface KVipsImageFileOperation {

    fun run(params: KVipsImageFileOperationParams): KVipsImageFileOperationResult
}

data class KVipsImageFileOperationParams(
    val inputImagePath: String,
    val outputImagePath: String,
    val outputImageParams: KVipsImageOutputParams
)

sealed class KVipsImageFileOperationResult
data class KVipsImageFileOperationError(val exception: KVipsImageOperationException) :
    KVipsImageFileOperationResult()

object KVipsImageFileOperationSuccess : KVipsImageFileOperationResult()

class KVipsImageFileOperationException(message: String) : Exception(message)