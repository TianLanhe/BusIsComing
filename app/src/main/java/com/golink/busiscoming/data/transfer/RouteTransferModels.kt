package com.golink.busiscoming.data.transfer

import com.golink.busiscoming.data.model.Place

data class TransferRoute(
    val name: String,
    val origin: Place,
    val destination: Place
)

data class DecodedRouteTransfer(
    val exportedAtUtc: String,
    val routes: List<TransferRoute>,
    val duplicateCount: Int
)

data class RouteImportPlan(
    val uniqueRouteCount: Int,
    val inFileDuplicateCount: Int,
    val mergeAddCount: Int,
    val mergeSkipCount: Int,
    val replaceDeleteCount: Int,
    val replaceImportCount: Int
)

enum class RouteTransferError {
    FILE_TOO_LARGE,
    INVALID_FILE_EXTENSION,
    MALFORMED_JSON,
    INVALID_SCHEMA,
    INVALID_FORMAT,
    UNSUPPORTED_VERSION,
    EMPTY_ROUTES,
    TOO_MANY_ROUTES,
    INVALID_ROUTE
}

class RouteTransferException(
    val error: RouteTransferError,
    cause: Throwable? = null
) : IllegalArgumentException(error.name, cause)
