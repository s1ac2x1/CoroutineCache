package com.kishlaly.tools.coroutinecache

/** Supported eviction policies. */
enum class EvictionPolicy {
    /** No size eviction—only TTL expiration. */
    NONE,

    /** Least-recently-used when size > maxSize. */
    LRU,

    /** First-in-first-out when size > maxSize. */
    FIFO
}