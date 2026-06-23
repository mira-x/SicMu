package xyz.mordorx.sicmu.collections

import com.google.common.cache.Cache
import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheBuilderSpec
import java.util.Random
import java.util.function.BiFunction

/**
 * A cache that internally deduplicates values. This is well suited for Megabyte-scale media objects
 * that may repeat, like song album cover art.
 *
 * @param specs Specification for the value cache (the cache that stores big, de-duplicated elements)
 * @param comparator A function that takes two values and returns true if they are equal
 */
class DeduplicationCache<K, V>(
    specs: CacheBuilderSpec,
    private val comparator: BiFunction<V, V, Boolean>
) {
    private val valueCache: Cache<Int, V>
    private val keyCache: Cache<K, Int>

    init {
        valueCache = CacheBuilder.from(specs).build()
        keyCache = CacheBuilder.newBuilder().maximumSize(1000).build()
    }

    /** Returns the value, or null if not present */
    fun getIfPresent(key: K): V? {
        val valueID = keyCache.getIfPresent(key) ?: return null
        val value = valueCache.getIfPresent(valueID)
        if (value == null) {
            keyCache.invalidate(key)
            valueCache.invalidate(valueID)
            return null
        }
        return value
    }

    fun put(key: K, value: V) {
        for (kv in valueCache.asMap().entries) {
            val valueCacheKey = kv.key
            val valueCacheValue = kv.value
            if (comparator.apply(value, valueCacheValue) ?: false) {
                // Value is already cached!
                keyCache.put(key, valueCacheKey)
                return
            }
        }

        // Value is not yet cached.
        val valueID = Random().nextInt()
        valueCache.put(valueID, value)
        keyCache.put(key, valueID)
    }
}
