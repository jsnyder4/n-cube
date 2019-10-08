package com.cedarsoftware.ncube.util

import gnu.trove.THashMap
import groovy.transform.CompileStatic

@CompileStatic
class CellMap<V> extends THashMap<Set<Long>, V>
{
    CellMap() { }

    CellMap(Map<Set<Long>, V> m)
    {
        putAll(m)
    }

    V put(Set<Long> key, V value)
    {
        if (key instanceof LongHashSet)
        {
            return super.put(key, value)
        }
        return super.put(new LongHashSet(key), value)
    }

    void putAll(Map<? extends Set<Long>, ? extends V> map)
    {
        for (entry in map.entrySet())
        {
            put(entry.key, entry.value)
        }
    }
}