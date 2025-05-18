package com.kishlaly.tools.coroutinecache

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.FUNCTION)
annotation class Cacheable(
    /** TTL in seconds */
    val ttlSeconds: Long = 300,
    /** Max entries */
    val maxSize: Int = 100,
    /** LRU? */
    val useLRU: Boolean = false,
    /** Coalesce in-flight? */
    val coalesce: Boolean = false
)