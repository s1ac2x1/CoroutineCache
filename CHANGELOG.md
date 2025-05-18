## [1.0.0] – 2025-05-17

### Added

- **`TTLCache`**: coroutine-safe in-memory cache with:
    - Time-To-Live (TTL) expiration
    - Optional size limits and eviction policies (`NONE`, `LRU`, `FIFO`)
    - In-flight coalescing to dedupe concurrent loads
- **Wrapper DSL**: `cacheable { … }` function to decorate any `suspend` lambda
- **Annotation DSL**: `@Cacheable` annotation + KSP processor generates `*Cached` wrappers
- **`CacheManager`** & **`CacheConfig`**: central registry and configuration for named caches
- **Utility methods**: `getOrPut`, `get`, `clear`, `size` for manual cache control
- Comprehensive **unit tests** covering:
    - Basic cache hits & misses
    - TTL expiration
    - LRU & FIFO eviction
    - In-flight coalescing
    - Exception propagation
- **README** and documentation with quickstart examples

### Fixed

- N/A

### Changed

Initial release