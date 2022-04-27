package io.github.versi.kvips

import kotlin.math.roundToInt

interface KVips {

    fun init(params: KVipsInitParams)

    fun shutdown()

    fun getMemoryReport(): KVipsMemoryReport
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

data class KVipsMemoryReport(
    val currentOperations: Int,
    val maxOperations: Int,
    val currentMemory: ULong,
    val maxMemory: ULong,
    val currentFiles: Int,
    val maxFiles: Int
) {
    override fun toString(): String {
        val operationsUsage = currentOperations.asPercentOf(maxOperations)
        val memoryUsage = currentMemory.toLong().asPercentOf(maxMemory.toLong())
        val filesUsage = currentFiles.asPercentOf(maxFiles)
        return "KVipsMemoryReport: operations - $currentOperations/$maxOperations [$operationsUsage%] " +
                "memory - $currentMemory/$maxMemory [$memoryUsage%] files - $currentFiles/$maxFiles [$filesUsage%]"
    }
    private fun Number.asPercentOf(value: Number): Int {
        return ((this.toFloat() / value.toFloat()) * 100).roundToInt()
    }
}