package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.proximity.Distance
import com.cedarsoftware.util.CaseInsensitiveMap
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.io.MetaUtils
import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Holds the value of a 'column' on an axis.
 * This class exists in order to allow additional
 * columns to be inserted onto an axis, without
 * having to "move" the existing cells.
 *
 * Furthermore, for some axis types (String), it is
 * often better for display purposes to use the
 * display order, as opposed to it's sort order
 * (e.g., months-of-year, days-of-week) for display.
 *
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
class Column implements Comparable<Comparable>
{
    public static final String NAME = 'name'
    public static final String DEFAULT_VALUE = 'default_value'
    protected long id
    private int displayOrder
    private Comparable value
    protected Map<String, Object> metaProps = null
    private static ConcurrentMap primitives = new ConcurrentHashMap()

    Column(Comparable value, long id = 0L, Map metaProps = null, int order = -1)
    {
        this.value = internValue(value)
        this.id = id
        if (value == null)
        {
            displayOrder = Integer.MAX_VALUE
        }

        if (order != -1)
        {
            displayOrder = order
        }

        if (metaProps)
        {
            addMetaProperties(metaProps)
        }
    }

    Column(Column source)
    {
        value = internValue(source.value)
        id = source.id
        displayOrder = source.displayOrder
        if (source.metaProps)
        {
            addMetaProperties(source.metaProps)
        }
    }

    /**
     * @return Map (case insensitive keys) containing meta (additional) properties for the n-cube.
     */
    Map<String, Object> getMetaProperties()
    {
        Map ret = metaProps == null ? new CaseInsensitiveMap() : metaProps
        return Collections.unmodifiableMap(ret)
    }

    /**
     * Fetch the value associated to the passed in Key from the MetaProperties (if any exist).  If
     * none exist, null is returned.
     */
    Object getMetaProperty(String key)
    {
        if (metaProps == null)
        {
            return null
        }
        return metaProps[key]
    }

    /**
     * Set (add / overwrite) a Meta Property associated to this Column.
     * @param key String key name of meta property
     * @param metaPropValue Object value to associate to key
     * @return prior value associated to key or null if none was associated prior
     */
    Object setMetaProperty(String key, Object metaPropValue)
    {
        if (metaProps == null)
        {
            metaProps = new CaseInsensitiveMap<>()
        }
        return metaProps[key] = metaPropValue
    }

    /**
     * Remove a meta-property entry
     */
    Object removeMetaProperty(String key)
    {
        if (metaProps == null)
        {
            return null
        }
        return metaProps.remove(key)
    }

    /**
     * Add a Map of meta properties all at once.
     * @param allAtOnce Map of meta properties to add
     */
    void addMetaProperties(Map<String, Object> allAtOnce)
    {
        if (metaProps == null)
        {
            metaProps = new CaseInsensitiveMap<>()
        }
        metaProps.putAll(allAtOnce)
    }

    /**
     * Remove all meta properties associated to this Column.
     */
    void clearMetaProperties()
    {
        if (metaProps != null)
        {
            metaProps.clear()
            metaProps = null
        }
    }

    /**
     * @return long ID of this column.  Note that the ID of a column is guaranteed to by unique within
     * a given n-cube, but not across n-cubes.
     */
    long getId()
    {
        return id
    }

    protected void setId(long id)
    {
        this.id = id
    }

    /**
     * Get the optional name of a column.  Since names are optional they are stored
     * within meta-properties.  Any column used on any type of axis can have a name.
     *
     * @return String name of Column if one is set, otherwise return null.
     */
    String getColumnName()
    {
        Object name = getMetaProperty('name')
        if (name instanceof String && StringUtilities.hasContent((String)name))
        {
            return (String) name
        }
        return null
    }

    /**
     * Set the optional name of a column.  Since names are optional they are stored within
     * meta-properties.  Any column used on any type of axis can have a name.
     *
     * @param name String name for column.
     */
    void setColumnName(String name)
    {
        setMetaProperty(NAME, name)
    }

    int hashCode()
    {
        long x = id
        x ^= x >> 23
        x *= 0x2127599bf4325c37L
        x ^= x >> 47
        return (int)(x)
    }

    boolean equals(Object that)
    {
        return that instanceof Column && id == ((Column) that).id
    }

    /**
     * @return Comparable value of the column.
     */
    Comparable getValue()
    {
        return value
    }

    /**
     * Set the value of a column.
     * @param v Comparable instance to store as the column value.
     */
    protected void setValue(Comparable v)
    {
        value = internValue(v)
    }

    /**
     * @return boolean true if this is the Default column, false otherwise.  The Default Column's value is always
     * null.
     */
    boolean isDefault()
    {
        return value == null
    }

    /**
     * @return a value that will match this column.  This returns column value
     * if it is a DISCRETE axis column.  If it is a Range axis column, the 'low'
     * value will be returned (low is inclusive, high is exclusive).  If it is a
     * RangeSet axis column, then the first value will be returned.  If it is a Range,
     * then the low value of that Range will be returned.  In all cases, the returned
     * value can be used to match against an axis including this column and the returned
     * value will match this column.
     */
    Comparable getValueThatMatches()
    {
        if (value instanceof Range)
        {
            return ((Range)value).low
        }
        else if (value instanceof RangeSet)
        {
            RangeSet set = (RangeSet) value
            Comparable v = set.get(0)
            return v instanceof Range ? ((Range) v).low : v
        }

        return value
    }

    protected void setDisplayOrder(int order)
    {
        displayOrder = order
    }

    int getDisplayOrder()
    {
        return displayOrder
    }

    int compareTo(Comparable that)
    {
        if (that instanceof Column)
        {
            that = ((Column)that).value
        }

        return value <=> that
    }

    String toString()
    {
        if (value instanceof Range || value instanceof RangeSet || value instanceof Distance)
        {
            return value.toString()
        }
        return CellInfo.formatForDisplay(value)
    }

    /**
     * Intern the passed in value.  Collapses (folds) equivalent instances into same instance.
     * @param value Object to intern (if possible)
     * @return interned instance (if internable) otherwise passed-in instance is returned.
     */
    private static Comparable internValue(Comparable value)
    {
        if (value == null)
        {
            return null
        }

        if (!MetaUtils.isLogicalPrimitive(value.class))
        {   // don't attempt to intern null (NPE) or non-primitive instances
            return value
        }

        if (primitives.containsKey(value))
        {   // intern it (re-use instance)
            return primitives[value]
        }

        Comparable singletonInstance = primitives.putIfAbsent(value, value)
        if (singletonInstance != null)
        {
            return singletonInstance
        }
        return value
    }
}
