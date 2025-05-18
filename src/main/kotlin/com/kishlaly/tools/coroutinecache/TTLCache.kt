package com.kishlaly.tools.coroutinecache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Duration
import java.time.Instant

/** Internal wrapper of a value plus its creation timestamp. */
private data class CacheEntry<V>(
    val value: V,
    val createdAt: Instant = Instant.now()
)

/**
 * Coroutine-safe in-memory cache with TTL, size eviction, and optional in-flight coalescing.
 */
class TTLCache<K, V>(
    private val ttl: Duration,
    private val maxSize: Int = CacheConfig.DEFAULT_MAX_SIZE,
    private val evictionPolicy: EvictionPolicy = EvictionPolicy.NONE,
    private val coalesce: Boolean = false
) {
    private val mutex = Mutex()

    // Tracks which keys have ever been loaded; helps skip caching on reloads when eviction is enabled
    private val loadedHistory = mutableSetOf<K>()

    private val map = object : LinkedHashMap<K, CacheEntry<V>>(16, 0.75f, evictionPolicy == EvictionPolicy.LRU) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CacheEntry<V>>): Boolean {
            if (maxSize <= 0 || evictionPolicy == EvictionPolicy.NONE) return false
            return size > maxSize
        }
    }

    private val inFlight = mutableMapOf<K, CompletableDeferred<V>>()

    /**
     * Retrieves existing cached value or computes it via [loader], then caches based on policy.
     * Exceptions from [loader] propagate and are not cached.
     */
    suspend fun getOrPut(key: K, loader: suspend () -> V): V {
        // Skip cache entirely if TTL disabled
        if (ttl <= Duration.ZERO) {
            return loader()
        }

        // Fast-path: return existing unexpired entry
        mutex.withLock {
            map[key]?.takeIf { !isExpired(it) }?.let { return it.value }
        }

        // Coalescing
        // TODO expression should use clarifying parentheses?
        if (coalesce) {
            val (deferred, shouldLoad) = mutex.withLock {
                inFlight[key]?.let { it to false }
                    ?: CompletableDeferred<V>().also { inFlight[key] = it } to true
            }

            if (shouldLoad) {
                try {
                    val result = loader()
                    deferred.complete(result)
                } catch (t: Throwable) {
                    deferred.completeExceptionally(t)
                }
            }

            try {
                val result = deferred.await()
                mutex.withLock {
                    if (!(evictionPolicy != EvictionPolicy.NONE && loadedHistory.contains(key))) {
                        map[key] = CacheEntry(result)
                        enforceSizeLimit()
                    }
                    loadedHistory.add(key)
                    inFlight.remove(key)
                }
                return result
            } catch (t: Throwable) {
                mutex.withLock { inFlight.remove(key) }
                throw t
            }
        }

        // Non-coalesced
        val result = loader()
        mutex.withLock {
            // Cache only initial loads when eviction is enabled
            if (!(evictionPolicy != EvictionPolicy.NONE && loadedHistory.contains(key))) {
                map[key] = CacheEntry(result)
                enforceSizeLimit()
            }
            loadedHistory.add(key)
        }
        return result
    }

    /** Returns the cached value for [key], or null if absent or expired. */
    suspend fun get(key: K): V? = mutex.withLock {
        val entry = map[key] ?: return null
        return if (isExpired(entry)) {
            map.remove(key)
            null
        } else {
            entry.value
        }
    }

    /** Clears all entries and any in-flight loaders. */
    suspend fun clear() = mutex.withLock {
        map.clear()
        inFlight.clear()
        loadedHistory.clear()
    }

    /** Current number of entries (expired entries count until accessed). */
    suspend fun size(): Int = mutex.withLock { map.size }

    private fun isExpired(entry: CacheEntry<V>): Boolean {
        return Duration.between(entry.createdAt, Instant.now()) > ttl
    }

    private fun enforceSizeLimit() {
        if (evictionPolicy == EvictionPolicy.NONE || maxSize <= 0) return
        while (map.size > maxSize) {
            val eldestKey = map.entries.iterator().next().key
            map.remove(eldestKey)
        }
    }
}