package com.velnox.app.core.network

import com.velnox.app.BuildConfig

/**
 * Backend dialect supported by the Velnox Marketplace platform.
 *
 * - [WORKERS_V1]: Target Cloudflare Workers + Hono architecture (api.velnox.live).
 *   Features JSON-native tokens, /api/v1 endpoints, decimal currency, R2 CDN media,
 *   and strict token rotation.
 *
 * - [EXPRESS_LEGACY]: Legacy/transitional Node.js Express architecture (velnox-api.onrender.com).
 *   Features /api/customer and /api/auth/mobile endpoints, cookie-native sessions,
 *   and streaming /api/media endpoints.
 */
enum class BackendDialect {
    WORKERS_V1,
    EXPRESS_LEGACY,
}

object BackendConfig {
    const val CONNECT_TIMEOUT_SEC = 15L
    const val READ_TIMEOUT_SEC = 30L
    const val WRITE_TIMEOUT_SEC = 60L
    const val HOST_COOLDOWN_MS = 60_000L

    val PRIMARY_HOST: String = BuildConfig.API_BASE_URL.trimEnd('/')

    val FALLBACK_HOSTS: List<String> = BuildConfig.API_FALLBACK_URLS
        .split(',')
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotEmpty() && it != PRIMARY_HOST }

    val MEDIA_BASE_URL: String = BuildConfig.MEDIA_BASE_URL.trimEnd('/')
}
