A lightweight Kotlin library for caching suspend functions with TTL (Time-To-Live), optional size-based eviction (LRU/FIFO), and in-flight coalescing. Ideal for reducing unnecessary I/O, database calls, or expensive computations without pulling in a heavy cache dependency.

# Installation (TBD)
Add to your Gradle project:
```gradle
plugins {
  kotlin("jvm") version "1.9.10"
  // If you want annotation processing
  id("com.google.devtools.ksp") version "1.9.10-1.0.13"
}

repositories {
  mavenCentral()
}

dependencies {
  implementation("com.kishlaly.tools:coroutinecache:<latest-version>")
  // For annotation-based DSL (KSP):
  ksp("com.kishlaly.tools:coroutinecache-processor:<latest-version>")
}
```

# Quickstart

## Wrapper-Style DSL
Use the `cacheable` function to wrap any suspend lambda. Configure TTL, max size, eviction policy, and key selector.
```kotlin
import com.kishlaly.tools.coroutinecache.cacheable
import com.kishlaly.tools.coroutinecache.EvictionPolicy
import java.time.Duration

// Original suspend function (e.g., network fetch)
suspend fun fetchUserFromApi(id: Int): User { /* ... */ }

// Create a cached version:
val fetchUserCached: suspend (Int) -> User = cacheable(
    ttl = Duration.ofMinutes(5),
    maxSize = 100,
    evictionPolicy = EvictionPolicy.LRU,
    coalesce = true
) { args ->
    args[0] as Int
}.invoke(::fetchUserFromApi)

// Usage:
suspend fun getUser(id: Int): User {
    return fetchUserCached(id)
}
```

* `ttl`: how long an entry stays fresh
* `maxSize`: maximum entries (0 = unlimited)
* `evictionPolicy`: `NONE` (TTL-only), `LRU`, or `FIFO`
* `coalesce`: share one in-flight load among concurrent callers
* `keySelector`: derive cache key from function arguments (default: `listOf(args)`)

## Annotation-Style DSL

Annotate suspend functions with `@Cacheable` and KSP generates `*Cached` wrappers at compile time.
```kotlin
import com.kishlaly.tools.coroutinecache.Cacheable
import java.time.Duration

class UserService {
  @Cacheable(
    ttlSeconds = 300,
    maxSize     = 200,
    useLRU      = true,
    coalesce    = true
  )
  suspend fun fetchUser(id: Int): User {
    return httpClient.get("/users/$id")
  }
}

// KSP generates:
// suspend fun UserService.fetchUserCached(id: Int): User { ... }

// Usage in code:
suspend fun display(id: Int) {
  val service = UserService()
  val user = service.fetchUserCached(id)
  println(user.name)
}
```

Generated wrappers live next to your code and delegate through a central `CacheManager`.

## Configuration & Globals

Customize default cache parameters or access caches directly:
```kotlin
import com.kishlaly.tools.coroutinecache.CacheConfig
import com.kishlaly.tools.coroutinecache.CacheManager
import java.time.Duration

// Retrieve or create a named cache:
val cache = CacheManager.getCache<Int, String>(
    name   = "myCache",
    config = CacheConfig(
      ttl            = Duration.ofSeconds(60),
      maxSize        = 50,
      useLRU         = false,
      coalesce       = true
    )
)

// Use getOrPut directly:
suspend fun getValue(key: Int): String =
  cache.getOrPut(key) { expensiveComputation(key) }
```

# Planned Improvements

* **Custom key serializers**: allow pluggable serialization for complex argument types.
* **Metrics & instrumentation**: customizable hooks or Micrometer integration for hit/miss rates and eviction counts.
* **Loader timeouts & retries**: configure per-key timeouts and retry policies for loader functions.
* **Additional eviction strategies**: support LFU (least-frequently-used) and size-based (memory/bytes) eviction.
* **Distributed cache backends**: optional adapters for Redis, Caffeine, or other clustered caches.
* **Kotlin Multiplatform support**: extend to JS and Native targets for multiplatform projects.
* **DI framework integrations**: seamless bindings for Koin, Dagger/Hilt, Spring, or Micronaut.
* **Annotation enhancements**: support class-level defaults and annotation inheritance.
* **Reactive Flow caching**: intercept Flow emissions with TTL-based caching.
* what else? 🤔