package com.golink.busiscoming.data.repository

import com.golink.busiscoming.data.model.P2pRouteRecoveryContext
import java.net.URL
import java.util.UUID

data class CitybusHttpResponse(
    val body: String,
    val setCookieHeaders: List<String> = emptyList()
) {
    fun phpSessionIdFor(responseUrl: URL): String? {
        if (responseUrl.protocol != CITYBUS_PROTOCOL ||
            responseUrl.host != CITYBUS_HOST ||
            responseUrl.port !in setOf(-1, CITYBUS_HTTPS_PORT) ||
            !responseUrl.path.startsWith(CITYBUS_PATH_PREFIX)
        ) {
            return null
        }

        val values = setCookieHeaders.mapNotNull { header ->
            val firstPart = header.substringBefore(';').trim()
            val name = firstPart.substringBefore('=', missingDelimiterValue = "").trim()
            val value = firstPart.substringAfter('=', missingDelimiterValue = "").trim()
            value.takeIf { name == PHP_SESSION_COOKIE && it.matches(PHP_SESSION_VALUE_PATTERN) }
        }
        return values.singleOrNull()
    }

    private companion object {
        const val CITYBUS_PROTOCOL = "https"
        const val CITYBUS_HOST = "mobile.citybus.com.hk"
        const val CITYBUS_HTTPS_PORT = 443
        const val CITYBUS_PATH_PREFIX = "/nwp3/"
        const val PHP_SESSION_COOKIE = "PHPSESSID"
        val PHP_SESSION_VALUE_PATTERN = Regex("[A-Za-z0-9,_-]{1,256}")
    }
}

data class CitybusSession(
    val phpSessionId: String,
    val language: String,
    val recoveryContext: P2pRouteRecoveryContext,
    val ownerScope: String?,
    val expiresAtMillis: Long
)

class CitybusSessionRegistry(
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val ttlMillis: Long = DEFAULT_TTL_MILLIS,
    private val referenceFactory: (String) -> String = { UUID.randomUUID().toString() }
) {
    private val sessions = mutableMapOf<String, CitybusSession>()

    fun register(
        phpSessionId: String,
        language: String,
        recoveryContext: P2pRouteRecoveryContext,
        ownerScope: String? = null
    ): String {
        require(phpSessionId.isNotBlank())
        val reference = referenceFactory(phpSessionId)
        synchronized(sessions) {
            removeExpiredLocked(clock())
            sessions[reference] = CitybusSession(
                phpSessionId = phpSessionId,
                language = language,
                recoveryContext = recoveryContext,
                ownerScope = ownerScope,
                expiresAtMillis = clock() + ttlMillis
            )
        }
        return reference
    }

    fun resolve(reference: String?): CitybusSession? {
        if (reference.isNullOrBlank()) return null
        synchronized(sessions) {
            val now = clock()
            removeExpiredLocked(now)
            return sessions[reference]?.takeIf { it.expiresAtMillis > now }
        }
    }

    fun invalidate(reference: String?) {
        if (reference.isNullOrBlank()) return
        synchronized(sessions) {
            sessions.remove(reference)
        }
    }

    fun invalidateScope(ownerScope: String) {
        synchronized(sessions) {
            sessions.entries.removeAll { it.value.ownerScope == ownerScope }
        }
    }

    fun size(): Int = synchronized(sessions) {
        removeExpiredLocked(clock())
        sessions.size
    }

    private fun removeExpiredLocked(now: Long) {
        sessions.entries.removeAll { it.value.expiresAtMillis <= now }
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 30 * 60 * 1_000L
    }
}

object CitybusSessionRuntime {
    val registry = CitybusSessionRegistry()
}
