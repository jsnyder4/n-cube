package com.cedarsoftware.ncube.util

import gnu.trove.map.hash.TCustomHashMap
import gnu.trove.strategy.HashingStrategy
import groovy.transform.CompileStatic

@CompileStatic
class CellMap<V> implements Map<Set<Long>, V>
{
    private TCustomHashMap<Set<Long>, V> cells = new TCustomHashMap<>(new HashingStrategy<Set<Long>>() {
        int computeHashCode(Set<Long> set)
        {
            if (set instanceof LongHashSet)
            {
                return set.hashCode()
            }
            int h = 0
            for (i in set)
            {
                // do not change the formula below.  It is been hand crafted and tested for performance.
                // If this does not hash well, ncube breaks down in performance.  The BigCube tests are
                // greatly slowed down as proper hashing is vital or cells will be really slow to access
                // when there are a lot of them in the ncube.

                // Original hash function  (John)
//            h += (int)(x * 347 ^ (x >>> 32) * 7)

                // Better (from Stack overflow)
//            x = ((x >> 16) ^ x) * 0x45d9f3b
//            x = ((x >> 16) ^ x) * 0x45d9f3b
//            x = ((x >> 16) ^ x)
//            h += (int) x

                // Even better (from Google)
                long x = i
                x ^= x >> 23
                x *= 0x2127599bf4325c37L
                x ^= x >> 47
                h += (int) x
            }
            return h
        }

        boolean equals(Set<Long> a, Set<Long> b)
        {
            if (a == null || b == null)
            {
                return a == null && b == null
            }
            if (a.size() != b.size())
            {
                return false
            }

            return a.containsAll(b)
        }
    }, 127, 0.9f)

    CellMap()
    {
    }

    /**
     * Constructs a new <code>SmallMap</code> with the same mappings as the
     * specified <code>Map</code>.
     *
     * @param m
     *            the map whose mappings are to be placed in this map
     * @throws NullPointerException
     *             if the specified map is null
     */
    CellMap(Map m)
    {
        putAll(m)
    }


    int size()
    {
        return cells.size()
    }

    boolean isEmpty()
    {
        return cells.isEmpty()
    }

    boolean containsKey(Object key)
    {
        return cells.containsKey(key)
    }

    boolean containsValue(Object value)
    {
        return cells.containsValue(value)
    }

    V get(Object key)
    {
        return cells.get(key)
    }

    V put(Set<Long> key, V value)
    {
        if (key instanceof LongHashSet)
        {
            return cells[key] = value
        }
        return cells[new LongHashSet(key)] = value
    }

    V remove(Object key)
    {
        return cells.remove(key)
    }

    void putAll(Map<? extends Set<Long>, ? extends V> map)
    {
        for (entry in map.entrySet())
        {
            put(entry.key, entry.value)
        }
    }

    void clear()
    {
        cells.clear()
    }

    Set<Set<Long>> keySet()
    {
        return cells.keySet()
    }

    Collection<V> values()
    {
        return cells.values()
    }

    Set<Map.Entry<Set<Long>, V>> entrySet()
    {
        return cells.entrySet()
    }
}