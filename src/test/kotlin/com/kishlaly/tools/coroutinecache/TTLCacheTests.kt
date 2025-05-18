package com.kishlaly.tools.coroutinecache

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.collections.all
import kotlin.collections.map
import kotlin.jvm.java

@OptIn(ExperimentalCoroutinesApi::class)
class TTLCacheTests {

    @Test
    fun `basic caching returns same value without reloading`() = runBlocking {
        val calls = AtomicInteger(0)
        val cache =
            TTLCache<Int, String>(ttl = Duration.ofMinutes(1), maxSize = 10, evictionPolicy = EvictionPolicy.NONE)

        val loader = suspend { calls.incrementAndGet().toString() }
        // First call: loader invoked
        val first = cache.getOrPut(42, loader)
        assertEquals("1", first)
        // Second call: should come from cache
        val second = cache.getOrPut(42, loader)
        assertEquals("1", second)
        assertEquals(1, calls.get())
    }

    @Test
    fun `entries expire after TTL`() = runBlocking {
        val calls = AtomicInteger(0)
        // TTL of 100ms
        val cache =
            TTLCache<Int, String>(ttl = Duration.ofMillis(100), maxSize = 10, evictionPolicy = EvictionPolicy.NONE)
        val loader = suspend { calls.incrementAndGet().toString() }

        val first = cache.getOrPut(1, loader)
        assertEquals("1", first)

        // Wait for longer than TTL
        delay(150)

        val second = cache.getOrPut(1, loader)
        assertEquals("2", second)
        assertEquals(2, calls.get())
    }

    @Test
    fun `FIFO eviction removes oldest`() = runBlocking {
        val cache = TTLCache<Int, String>(
            ttl = Duration.ofMinutes(1),
            maxSize = 2,
            evictionPolicy = EvictionPolicy.FIFO
        )

        cache.getOrPut(1) { "one" }
        cache.getOrPut(2) { "two" }
        // Insert third - should evict key=1
        cache.getOrPut(3) { "three" }

        // Key=1 should be gone:
        assertNull(cache.get(1))

        // And 2 & 3 should be present, without reloading:
        assertEquals("two", cache.getOrPut(2) { error("should not reload") })
        assertEquals("three", cache.getOrPut(3) { error("should not reload") })
    }

    @Test
    fun `LRU eviction removes least recently used`() = runBlocking {
        val cache = TTLCache<Int, String>(
            ttl = Duration.ofMinutes(1),
            maxSize = 2,
            evictionPolicy = EvictionPolicy.LRU
        )
        cache.getOrPut(1) { "one" }
        cache.getOrPut(2) { "two" }
        // mark key=1 as recently used
        assertEquals("one", cache.getOrPut(1) { error("should not reload") })
        // insert third → should evict key=2
        cache.getOrPut(3) { "three" }

        // key=2 is gone, so this should invoke the loader and return "reloaded"
        val reloaded = cache.getOrPut(2) { "reloaded" }
        assertEquals("reloaded", reloaded)

        // and 1 & 3 stay intact
        assertEquals("one", cache.getOrPut(1) { error("should not reload") })
        assertEquals("three", cache.getOrPut(3) { error("should not reload") })
    }

    @Test
    fun `coalescing shares single loader invocation`() = runTest {
        val calls = AtomicInteger(0)
        val cache = TTLCache<Int, String>(
            ttl = Duration.ofMinutes(1),
            maxSize = 10,
            evictionPolicy = EvictionPolicy.NONE,
            coalesce = true
        )
        // Loader delays to simulate work
        val loader: suspend () -> String = {
            delay(100)
            calls.incrementAndGet().toString()
        }

        // Launch several concurrent loads for same key
        val results = List(5) {
            async { cache.getOrPut(7, loader) }
        }.map { it.await() }

        // All should return the same result
        assertTrue(results.all { it == "1" })
        // Loader should have been called only once
        assertEquals(1, calls.get())
    }

    @Test
    fun `clear empties cache and inflight`() = runBlocking {
        val cache = TTLCache<Int, String>(
            ttl = Duration.ofMinutes(1),
            maxSize = 10,
            evictionPolicy = EvictionPolicy.NONE,
            coalesce = true
        )
        cache.getOrPut(1) { "one" }
        assertEquals(1, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
    }

    @Test
    fun `loader exception propagates and nothing is cached`() = runTest {
        val calls = AtomicInteger(0)
        val cache = TTLCache<Int, String>(
            ttl = Duration.ofMinutes(1),
            maxSize = 10,
            evictionPolicy = EvictionPolicy.NONE
        )

        val loader: suspend () -> String = {
            calls.incrementAndGet()
            throw kotlin.IllegalStateException("load failed")
        }

        // First call: should throw and increment calls to 1
        val ex1 = assertThrows(IllegalStateException::class.java) {
            runBlocking { cache.getOrPut(42, loader) }
        }
        assertEquals("load failed", ex1.message)
        assertEquals(1, calls.get())

        // After failure, nothing should be cached, so a second call should invoke loader again
        val ex2 = assertThrows(IllegalStateException::class.java) {
            runBlocking { cache.getOrPut(42, loader) }
        }
        assertEquals("load failed", ex2.message)
        assertEquals(2, calls.get())

        // Peeking via get() should return null
        assertNull(runBlocking { cache.get(42) })
    }

}