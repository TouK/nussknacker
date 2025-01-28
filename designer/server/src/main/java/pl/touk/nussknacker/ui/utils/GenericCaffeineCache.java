package pl.touk.nussknacker.ui.utils;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

// In Scala 2.12 it is impossible to create Caffeine cache with generic types for key and/or value using purely Scala code.
// The type inference does not work as expected. This util written in Java is needed to bypass that problem.
public class GenericCaffeineCache<K, V> {
    private final Cache<K, V> cache;

    public GenericCaffeineCache(Duration ttl) {
        this.cache = Caffeine.newBuilder().expireAfterWrite(ttl).build();
    }

    public Cache<K, V> getCache() {
        return this.cache;
    }
}
