package com.golink.busiscoming.data.repository

import android.util.Log
import com.golink.busiscoming.BuildConfig

data class RouteDetailDiagnosticEvent(
    val category: String,
    val action: String,
    val safeKeyHash: String? = null,
    val generation: Int? = null,
    val reason: String? = null
) {
    override fun toString(): String = buildList {
        add("category=$category")
        add("action=$action")
        safeKeyHash?.let { add("key=$it") }
        generation?.let { add("generation=$it") }
        reason?.let { add("reason=$it") }
    }.joinToString(" ")
}

object RouteDetailDiagnostics {
    @Volatile var observer: (RouteDetailDiagnosticEvent) -> Unit = {}

    fun record(event: RouteDetailDiagnosticEvent) {
        runCatching { observer(event) }
        if (BuildConfig.DEBUG) runCatching { Log.d("RouteDetail", event.toString()) }
    }

    fun safeHash(value: Any): String = value.hashCode().toUInt().toString(16)
}
