package io.github.versi.kvips

interface KVips {

    fun init(params: KVipsInitParams)

    fun shutdown()
}

class KVipsInitException(message: String) : RuntimeException(message)

data class KVipsInitParams(
    val applicationName: String,
    val leaksReportingEnabled: Boolean,
    val concurrency: Int,
    val loggingEnabled: Boolean = true
)