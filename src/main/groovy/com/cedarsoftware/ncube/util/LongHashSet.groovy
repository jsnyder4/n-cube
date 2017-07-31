package com.cedarsoftware.ncube.util

import groovy.transform.CompileStatic

/**
 * Special Set instance that hashes the Set<Long> column IDs with excellent dispersion,
 * while at the same time, using only a single primitive long (8 bytes) per entry.
 * This set is backed by a long[], so adding and removing items is O(n).
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
class LongHashSet implements Set<Long>
{
    private long[] elems = (long[]) null
    private Integer hash = null

    LongHashSet()
    { }

    LongHashSet(Set<Long> col)
    {
        elems = new long[col.size()]
        int i = 0
        for (o in col)
        {
            elems[i++] = o
        }
    }

    int size()
    {
        return elems == null ? 0 : elems.length
    }

    boolean isEmpty()
    {
        return elems == null || elems.length == 0
    }

    boolean contains(Object item)
    {
        if (empty || item == null)
        {
            return false
        }

        long[] local = elems
        int len = local.length

        for (int i=0; i < len; i++)
        {
            if (item == local[i])
            {
                return true
            }
        }
        return false
    }

    Iterator iterator()
    {
        Iterator it = new Iterator() {
            private int currentIndex = 0

            boolean hasNext()
            {
                if (elems == null)
                {
                    return false
                }
                return currentIndex < elems.length
            }

            Long next()
            {
                return elems[currentIndex++]
            }

            void remove()
            {
                throw new UnsupportedOperationException()
            }
        }
        return it
    }

    Object[] toArray()
    {
        if (empty)
        {
            return [] as Object[]
        }

        long[] local = elems
        int len = local.length
        Object[] array = new Object[len]

        for (int i=0; i < len; i++)
        {
            array[i] = local[i]
        }
        return array
    }

    boolean add(int x)
    {
        add((long) x)
    }

    boolean add(Long o)
    {
        hash = null
        if (elems == null)
        {
            elems = new long[1]
            elems[0] = o
            return true
        }
        else
        {
            if (contains(o))
            {   // Don't allow duplicates - this is a Set
                return false
            }
            int origSize = size()
            long[] newElems = new long[origSize + 1]
            System.arraycopy(elems, 0, newElems, 0, origSize)
            newElems[origSize] = o
            elems = newElems
            return size() != origSize
        }
    }

    boolean remove(Object o)
    {
        hash = null
        if (empty || o == null)
        {
            return false
        }

        long[] local = elems
        int len = local.length

        for (int i=0; i < len; i++)
        {
            if (o == local[i])
            {
                long[] newElems = new long[len - 1]
                System.arraycopy(local, i + 1, local, i, len - i - 1)
                System.arraycopy(local, 0, newElems, 0, len - 1)
                elems = newElems
                return true
            }
        }
        return false
    }

    boolean addAll(Collection<? extends Long> col)
    {
        int origSize = size()
        for (o in col)
        {
            add(o)
        }
        return size() != origSize
    }

    void clear()
    {
        hash = null
        elems = (long[])null
    }

    boolean removeAll(Collection col)
    {
        int origSize = size()
        for (o in col)
        {
            remove(o)
        }
        return size() != origSize
    }

    boolean retainAll(Collection col)
    {
        hash = null
        int origSize = size()
        Set<Long> keep = new LinkedHashSet<Long>()
        for (item in col)
        {
            if (contains(item))
            {
                keep.add((Long) item)
            }
        }
        elems = new long[keep.size()]
        long[] local = elems
        int idx = 0
        for (item in keep)
        {
            local[idx++] = item
        }
        return size() != origSize
    }

    boolean containsAll(Collection col)
    {
        for (item in col)
        {
            if (!contains(item))
            {
                return false
            }
        }
        return true
    }

    Object[] toArray(Object[] a)
    {
        return toArray()
    }

    boolean equals(Object other)
    {
        Set<Long> that = other as Set<Long>
        if (that.size() != size())
        {
            return false
        }

        return that.containsAll(this)
    }

    int hashCode()
    {
        if (hash != null)
        {
            return hash
        }

        // This must be an order insensitive hash
        int h = 0

        for (i in elems)
        {
            h += i.hashCode()
        }
        return hash = h
    }
}
