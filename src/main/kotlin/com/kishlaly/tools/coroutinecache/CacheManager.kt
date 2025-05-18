package com.kishlaly.tools.coroutinecache

import java.util.concurrent.ConcurrentHashMap

/**
 * A central registry of named cache instances
 * Subsequent calls with the same name reuse the first config
 */
object CacheManager {
    private val caches = ConcurrentHashMap<String, TTLCache<Any, Any>>()

    @Suppress("UNCHECKED_CAST")
    fun <K, V> getCache(name: String, config: CacheConfig = CacheConfig.DEFAULT): TTLCache<K, V> {
        return caches.computeIfAbsent(name) {
            TTLCache<Any, Any>(
                ttl = config.ttl,
                maxSize = config.maxSize,
                evictionPolicy = if (config.useLRU) EvictionPolicy.LRU else EvictionPolicy.NONE,
                coalesce = config.coalesce
            )
        } as TTLCache<K, V>
    }

    /** Remove all caches (for testing or shutdown) */
    fun clearAll() {
        caches.clear()
    }
}