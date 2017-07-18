package com.cedarsoftware.ncube.util

import groovy.transform.CompileStatic

@CompileStatic
class CellMap<V> implements Map<Set<Long>, V>
{
    final private Map<Set<Long>, V> cells = new HashMap<>(100)

    CellMap()
    {
    }

    CellMap(Map<Set<Long>, V> m)
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