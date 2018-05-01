package com.cedarsoftware.ncube.util

import com.cedarsoftware.ncube.NCube
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.RemovalCause
import com.github.benmanes.caffeine.cache.RemovalListener
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCache

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class CCacheManager implements CacheManager
{
    private static final Logger LOG = LoggerFactory.getLogger(CCacheManager.class)
    private final ConcurrentMap<String, Cache> caches = new ConcurrentHashMap<>()
    private final int maximumSize
    private final int evictionDuration
    private final String evictionTimeUnit
    private final String evictionType
    private final Closure removalClosure

    CCacheManager(Closure removalClosure = null, int maximumSize = 0, String evictionType = 'none', int evictionDuration = 0, String evictionTimeUnit = 'hours')
    {
        this.removalClosure = removalClosure
        this.maximumSize = maximumSize
        this.evictionType = evictionType
        this.evictionDuration = evictionDuration
        this.evictionTimeUnit = evictionTimeUnit
    }

    Cache getCache(String name)
    {
        Cache cache = caches[name]  // name = ApplicationID.toString()

        if (cache == null)
        {
            cache = createCache(name)
            Cache mapRef = caches.putIfAbsent(name, cache)
            if (mapRef != null)
            {
                cache = mapRef
            }
        }
        return cache
    }

    private CaffeineCache createCache(String name)
    {
        Caffeine builder = Caffeine.newBuilder()
        builder.removalListener(new NCubeRemovalListener(removalClosure))
        if (maximumSize > 0)
        {
            builder.maximumSize(maximumSize)
        }
        if (evictionType == 'expireAfterWrite')
        {
            builder.expireAfterWrite(evictionDuration, getTimeUnitFromString(evictionTimeUnit))
        }
        else if (evictionType == 'expireAfterAccess')
        {
            builder.expireAfterAccess(evictionDuration, getTimeUnitFromString(evictionTimeUnit))
        }
        CaffeineCache caffeineCache = new CaffeineCache(name, builder.build() as com.github.benmanes.caffeine.cache.Cache)
        return caffeineCache
    }

    private TimeUnit getTimeUnitFromString(String timeunit)
    {
        String lowerTimeunit = timeunit.toLowerCase()
        switch (lowerTimeunit)
        {
            case 'seconds':
                return TimeUnit.SECONDS
                break
            case 'minutes':
                return TimeUnit.MINUTES
                break
            case 'hours':
                return TimeUnit.HOURS
                break
            case 'days':
                return TimeUnit.DAYS
                break
            default:
                throw new IllegalArgumentException("Eviction time unit not understood: ${timeunit}. Please choose one of ['seconds', 'minutes', 'hours', 'days']")
        }
    }

    Collection<String> getCacheNames()
    {
        return caches.keySet()
    }

    /**
     * Apply the passed in Closure to all values in the cache.
     * @param name String name of the Cache
     * @param closure Closure to apply to each key-value pair
     */
    void applyToEntries(String name, Closure closure)
    {
        Cache cache = caches[name]
        if (cache == null)
        {
            return
        }
        CaffeineCache cCache = cache as CaffeineCache
        com.github.benmanes.caffeine.cache.Cache cafCache = cCache.nativeCache
        Iterator i = cafCache.asMap().entrySet().iterator()
        while (i.hasNext())
        {
            Map.Entry entry = i.next()
            String key = entry.key as String
            Object value = entry.value
            closure(key, value)
        }
    }

    /**
     * Cube cache eviction listener.  This class exists to clear the class loader cache entries
     * associated to cells that have generated classes dynamically.  By removing the references
     * from those caches, the dynamically generated classes can be garbage collected (Java 8+).
     */
    private static class NCubeRemovalListener implements RemovalListener<String, Object>
    {
        private final Closure closure
        NCubeRemovalListener(Closure closure)
        {
            this.closure = closure
        }

        void onRemoval(String key, Object value, RemovalCause removalCause)
        {
            if (closure != null)
            {
                if (value instanceof NCube)
                {
                    NCube ncube = (NCube) value
                    LOG.info("Cache eviction: n-cube: ${ncube.name}, app: ${ncube.applicationID}")
                }
                else
                {
                    LOG.info("Cache eviction: key=${key}, value=${value?.toString()}")
                }
                closure(value)
            }
        }
    }
}
