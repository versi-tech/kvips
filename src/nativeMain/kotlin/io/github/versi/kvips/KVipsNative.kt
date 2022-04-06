package io.github.versi.kvips

import io.github.versi.kvips.libvips.*
import platform.posix.setenv

object KVipsNative : KVips {

    override fun init(params: KVipsInitParams) {
        // needs to be called before vips_init which checks env var VIPS_WARNING internally
        if (!params.loggingEnabled) {
            disableLogging()
        }
        if (vips_init(params.applicationName) < 0) {
            throw KVipsInitException("Failed to init KVips.")
        }

        val enableLeaksReporting = if (params.leaksReportingEnabled) 1 else 0
        vips_leak_set(enableLeaksReporting)
        vips_concurrency_set(params.concurrency)
    }

    /**
     * based on: https://github.com/libvips/libvips/issues/1498
     */
    private fun disableLogging() {
        setenv("VIPS_WARNING", "0", 1)
    }
}