package io.github.versi.kvips.internal

import kotlin.system.getTimeMillis

inline fun <T> measureTimeMillis(
    loggingFunction: (Long) -> Unit,
    function: () -> T
): T {

    val startTime = getTimeMillis()
    val result: T = function.invoke()
    loggingFunction.invoke(getTimeMillis() - startTime)

    return result
}