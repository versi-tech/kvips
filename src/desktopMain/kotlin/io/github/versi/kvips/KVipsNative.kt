package io.github.versi.kvips

import io.github.versi.kvips.libvips.*
import platform.posix.setenv

object KVipsNative : KVips {

    override fun init(params: KVipsInitParams) {
        // needs to be called before vips_init which checks env var VIPS_WARNING internally
        if (!params.loggingEnabled) {
            disableLogging()
        }
        // needs to be called before vips_init which checks env var VIPS_MIN_STACK_SIZE internally
        setMinStackSize(params.minStackSizeInMb)

        if (vips_init(params.applicationName) < 0) {
            throw KVipsInitException("Failed to init KVips.")
        }

        val enableLeaksReporting = if (params.leaksReportingEnabled) 1 else 0
        vips_leak_set(enableLeaksReporting)
        vips_concurrency_set(params.concurrency)
    }

    override fun shutdown() {
        vips_shutdown()
    }

    override fun getMemoryReport(): KVipsMemoryReport {
        val maxOperations = vips_cache_get_max()
        val currentOperations = vips_cache_get_size()
        val maxMemory = vips_cache_get_max_mem()
        val currentMemory = vips_tracked_get_mem()
        val maxFiles = vips_cache_get_max_files()
        val currentFiles = vips_tracked_get_files()
        return KVipsMemoryReport(
            currentOperations = currentOperations,
            maxOperations = maxOperations,
            currentMemory = currentMemory,
            maxMemory = maxMemory,
            currentFiles = currentFiles,
            maxFiles = maxFiles
        )
    }

    /**
     * based on: https://github.com/libvips/libvips/issues/1498
     */
    private fun disableLogging() {
        setenv("VIPS_WARNING", "0", 1)
    }

    /**
     * based on: https://github.com/libvips/libvips/issues/2761
     */
    private fun setMinStackSize(minStackSizeInMb: Int) {
        setenv("VIPS_MIN_STACK_SIZE", "${minStackSizeInMb}m", 1)
    }
}