package xyz.mordorx.sicmu.util;

import androidx.annotation.Nullable;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheBuilderSpec;

import java.util.Random;
import java.util.function.BiFunction;

/**
 * A cache that internally deduplicates values. This is well suited for Megabyte-scale media objects
 * that may repeat, like song album cover art.
 */
public class DeduplicationCache<K, V> {
    private final Cache<Integer, V> valueCache;
    private final Cache<K, Integer> keyCache;
    private final BiFunction<V, V, Boolean> comparator;

    /**
     * @param specs Specification for the value cache (the cache that stores big, de-duplicated elements)
     * @param comparator A function that takes two values and returns true if they are equal
     */
    public DeduplicationCache(CacheBuilderSpec specs, BiFunction<V, V, Boolean> comparator) {
        valueCache = CacheBuilder.from(specs).build();
        keyCache = CacheBuilder.newBuilder().build();
        this.comparator = comparator;
    }

    /// Returns the value, or null if not present
    @Nullable
    public V getIfPresent(K key) {
        var valueID = keyCache.getIfPresent(key);
        if (valueID == null) {
            return null;
        }
        var value = valueCache.getIfPresent(valueID);
        if (value == null) {
            keyCache.invalidate(key);
            valueCache.invalidate(valueID);
            return null;
        }
        return value;
    }

    public void put(K key, V value) {
        for(var kv : valueCache.asMap().entrySet()) {
            var valueCacheKey = kv.getKey();
            var valueCacheValue = kv.getValue();
            if(comparator.apply(value, valueCacheValue)) {
                // Value is already cached!
                keyCache.put(key, valueCacheKey);
                return;
            }
        }

        // Value is not yet cached.
        Integer valueID = new Random().nextInt();
        valueCache.put(valueID, value);
        keyCache.put(key, valueID);
    }
}
