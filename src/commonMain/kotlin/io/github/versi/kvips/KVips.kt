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
    val loggingEnabled: Boolean = true,
    val minStackSizeInMb: Int = 2
) {
    init {
        require(minStackSizeInMb > 0) {
            "Minimum stack size [MB] should be greater than 0"
        }
    }
}