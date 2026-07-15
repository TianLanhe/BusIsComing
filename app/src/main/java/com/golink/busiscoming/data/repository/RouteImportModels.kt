package com.golink.busiscoming.data.repository

enum class RouteImportMode {
    MERGE,
    REPLACE
}

enum class RouteImportFailureStage {
    AFTER_DELETE,
    BEFORE_INSERT
}

data class RouteImportResult(
    val addedCount: Int,
    val skippedCount: Int,
    val deletedCount: Int
)

fun interface RouteImportFailureInjector {
    fun invoke(stage: RouteImportFailureStage, index: Int)

    companion object {
        val NONE = RouteImportFailureInjector { _, _ -> }
    }
}
