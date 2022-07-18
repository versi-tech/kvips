package io.github.versi.kvips.image.internal

import io.github.versi.kvips.image.KVipsImageOperationException
import io.github.versi.kvips.libvips.vips_error_buffer
import kotlinx.cinterop.toKStringFromUtf8

class KVipsNativeImageOperationException(message: String) :
    KVipsImageOperationException("$message error: ${getVipsError()}")

internal fun getVipsError() = vips_error_buffer()?.toKStringFromUtf8()