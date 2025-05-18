package com.kishlaly.tools.coroutinecache

import java.time.Duration

/** Configuration for a cache instance. */
data class CacheConfig(
    val ttl: Duration,
    val maxSize: Int = DEFAULT_MAX_SIZE,
    val useLRU: Boolean = false,
    val coalesce: Boolean = false
) {
    companion object {
        const val DEFAULT_MAX_SIZE = 100

        /** 5 min TTL, 100 entries, no LRU, no coalescing */
        val DEFAULT = CacheConfig(
            ttl = Duration.ofMinutes(5),
            maxSize = DEFAULT_MAX_SIZE,
            useLRU = false,
            coalesce = false
        )
    }
}