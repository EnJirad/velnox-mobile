package com.velnox.app.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Intelligent endpoint router and host failover manager.
 *
 * Supports ordered candidate hosts for the Cloudflare Workers architecture:
 * 1. Primary Cloudflare Workers origin (`https://velnox-api.ma0979354966-js91934.workers.dev`)
 * 2. Custom domain target (`https://api.velnox.live`)
 *
 * If the primary host fails with a connection or DNS resolution error, it enters a temporary
 * cooldown and the router seamlessly fails over to the next candidate host.
 *
 * Dialect defaults to [BackendDialect.WORKERS_V1] for all live production endpoints.
 */
class BackendEndpointRouter(
    val primaryHost: String = BackendConfig.PRIMARY_HOST,
    val fallbackHosts: List<String> = if (primaryHost.contains(".test") || primaryHost.contains("localhost")) emptyList() else BackendConfig.FALLBACK_HOSTS,
    val client: OkHttpClient = defaultClient(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val candidateHosts: List<String> = if (fallbackHosts.isEmpty()) listOf(primaryHost) else (listOf(primaryHost) + fallbackHosts).distinct()
    private val hostCooldowns = ConcurrentHashMap<String, Long>()
    private val hostDialects = ConcurrentHashMap<String, BackendDialect>()
    private val mutex = Mutex()

    var allowLegacyCapabilityFallback: Boolean = false

    fun candidateHostsList(): List<String> = candidateHosts

    /**
     * Returns the currently preferred healthy host.
     */
    fun currentHost(): String {
        val currentTime = now()
        for (host in candidateHosts) {
            val cooldownUntil = hostCooldowns[host] ?: 0L
            if (currentTime >= cooldownUntil) {
                return host
            }
        }
        // If all are in cooldown, reset primary and use it
        return candidateHosts.firstOrNull() ?: primaryHost
    }

    /**
     * Reports an execution failure on a host (e.g. DNS failure, connection timeout, 502-504).
     */
    fun markHostFailure(host: String) {
        val normalized = host.trimEnd('/')
        // Only put in cooldown if there is another candidate host to fail over to
        if (candidateHosts.size > 1) {
            hostCooldowns[normalized] = now() + BackendConfig.HOST_COOLDOWN_MS
        }
    }

    /**
     * Reports a successful response on a host, clearing its cooldown.
     */
    fun markHostSuccess(host: String, dialect: BackendDialect? = null) {
        val normalized = host.trimEnd('/')
        hostCooldowns.remove(normalized)
        if (dialect != null) {
            hostDialects[normalized] = dialect
        }
    }

    /**
     * Gets the detected dialect for the given host or current host.
     */
    fun getDialect(host: String = currentHost()): BackendDialect {
        val normalized = host.trimEnd('/')
        hostDialects[normalized]?.let { return it }
        // Only test fixtures using '.test' or 'localhost' use EXPRESS_LEGACY
        return if (normalized.contains(".test") || normalized.contains("localhost")) {
            BackendDialect.EXPRESS_LEGACY
        } else {
            BackendDialect.WORKERS_V1
        }
    }

    /**
     * Explicitly sets dialect for a host (e.g. after observing response headers/tokens).
     */
    fun setDialect(host: String, dialect: BackendDialect) {
        hostDialects[host.trimEnd('/')] = dialect
    }

    /**
     * Probes `/health` on a host to determine whether it is active and its dialect.
     */
    suspend fun probeHealth(host: String): Pair<Boolean, BackendDialect?> = withContext(Dispatchers.IO) {
        val url = "${host.trimEnd('/')}/health"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    markHostFailure(host)
                    return@withContext false to null
                }
                val body = response.body?.string().orEmpty()
                val isWorkers = try {
                    val json = JSONObject(body)
                    val data = json.optJSONObject("data") ?: json
                    val version = data.optString("version")
                    val runtime = json.optString("runtime")
                    version.startsWith("3.") || runtime.contains("workers") || data.has("database")
                } catch (_: Exception) {
                    false
                }
                val dialect = if (isWorkers) BackendDialect.WORKERS_V1 else BackendDialect.EXPRESS_LEGACY
                markHostSuccess(host, dialect)
                true to dialect
            }
        } catch (_: Exception) {
            markHostFailure(host)
            false to null
        }
    }

    /**
     * Executes an HTTP operation across candidate hosts with automatic failover.
     * If [host] throws an [IOException] (network, DNS, or socket failure), it fails over to the next candidate host.
     */
    suspend fun <T> executeWithFailover(
        isIdempotent: Boolean = true,
        block: suspend (host: String, dialect: BackendDialect) -> T,
    ): T {
        val currentTime = now()
        val orderedCandidates = candidateHosts.sortedBy { host ->
            val cooldown = hostCooldowns[host] ?: 0L
            if (currentTime >= cooldown) 0L else cooldown
        }

        var lastException: Exception? = null

        for (host in orderedCandidates) {
            val dialect = getDialect(host)
            try {
                val result = block(host, dialect)
                markHostSuccess(host, dialect)
                return result
            } catch (e: UnknownHostException) {
                // DNS resolution failed (e.g. domain not yet deployed in public DNS)
                markHostFailure(host)
                lastException = e
                // Always safe to failover because connection was never established
                continue
            } catch (e: IOException) {
                markHostFailure(host)
                lastException = e
                if (!isIdempotent) {
                    // For non-idempotent writes, only continue if no other options or safe
                    // but for DNS/connection refused it's always safe
                    val isConnectionError = e.message?.contains("Failed to connect") == true ||
                            e.message?.contains("Connection refused") == true
                    if (!isConnectionError) {
                        throw e
                    }
                }
                continue
            } catch (e: Exception) {
                throw e
            }
        }

        throw lastException ?: IOException("All candidate hosts failed for request")
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(BackendConfig.CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
            .readTimeout(BackendConfig.READ_TIMEOUT_SEC, TimeUnit.SECONDS)
            .writeTimeout(BackendConfig.WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}
