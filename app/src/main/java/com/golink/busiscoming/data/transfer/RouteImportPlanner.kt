package com.golink.busiscoming.data.transfer

import com.golink.busiscoming.data.model.RouteConfig

object RouteImportPlanner {
    fun plan(
        incoming: List<TransferRoute>,
        inFileDuplicateCount: Int,
        existing: List<RouteConfig>
    ): RouteImportPlan {
        val existingIdentities = existing.mapTo(HashSet()) {
            RouteIdentity(it.name.trim(), it.origin, it.destination)
        }
        val mergeSkipCount = incoming.count { it.identity() in existingIdentities }
        return RouteImportPlan(
            uniqueRouteCount = incoming.size,
            inFileDuplicateCount = inFileDuplicateCount,
            mergeAddCount = incoming.size - mergeSkipCount,
            mergeSkipCount = mergeSkipCount,
            replaceDeleteCount = existing.size,
            replaceImportCount = incoming.size
        )
    }
}
