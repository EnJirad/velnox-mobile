package com.velnox.app.core.network

/**
 * Central media URL resolver for Cloudflare R2 and legacy Express storage.
 *
 * Cloudflare Workers serves media through a dedicated R2 public bucket CDN
 * (`https://pub-75f6f26e59df431b854fe53caedb49b6.r2.dev/<key>`), whereas the legacy Express
 * backend streamed bytes via `/api/media/<key>`.
 *
 * This resolver guarantees:
 * - Absolute URLs (`http://`, `https://`, `data:`, `content://`, `file://`) are preserved untouched.
 * - Relative bare keys (`avatar/123.webp`, `cover.webp`, `chat/...`) are cleanly mapped
 *   to the appropriate CDN origin.
 */
object VelnoxMediaUrlResolver {

    fun resolve(
        rawUrl: String?,
        baseUrl: String = BackendConfig.PRIMARY_HOST,
        dialect: BackendDialect = BackendDialect.WORKERS_V1,
    ): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()

        // Absolute URLs or data/content schemes stay untouched
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true) ||
            trimmed.startsWith("content:", ignoreCase = true) ||
            trimmed.startsWith("file:", ignoreCase = true)
        ) {
            return trimmed
        }

        val cleanKey = trimmed.removePrefix("/").removePrefix("api/v1/media/").removePrefix("api/media/").removePrefix("media/")

        return when (dialect) {
            BackendDialect.WORKERS_V1 -> {
                "${BackendConfig.MEDIA_BASE_URL}/$cleanKey"
            }
            BackendDialect.EXPRESS_LEGACY -> {
                val origin = baseUrl.trimEnd('/')
                "$origin/api/media/$cleanKey"
            }
        }
    }
}
