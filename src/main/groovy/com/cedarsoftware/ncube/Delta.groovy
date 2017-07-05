package com.cedarsoftware.ncube

import com.cedarsoftware.util.StringUtilities
import groovy.transform.CompileStatic

/**
 * This class records information about the delta (difference) between
 * two n-cubes.  It allows a level of determinism regarding the difference
 * that could not be obtain with purely textual differences.
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
class Delta implements Comparable
{
    private final String desc
    private final Location loc
    private final Type type
    private final Object locId
    private final Object sourceVal
    private final Object destVal
    private final Object[] sourceList
    private final Object[] destList

    enum Location
    {
        NCUBE,
        NCUBE_META,
        AXIS,
        AXIS_META,
        COLUMN,
        COLUMN_META,
        CELL,
        CELL_META,
        TEST,
        TEST_COORD,
        TEST_ASSERT
    }

    enum Type
    {
        ADD,
        DELETE,
        UPDATE,
        ORDER
    }

    Delta(Location location, Type type, String description, Object locId, Object sourceVal, Object destVal, Object[] sourceList, Object[] destList)
    {
        desc = description
        loc = location
        this.type = type
        this.locId = locId
        if (sourceVal instanceof Column)
        {
            Column column = sourceVal as Column
            sourceVal = new Column(column)
        }
        this.sourceVal = sourceVal
        if (destVal instanceof Column)
        {
            Column column = destVal as Column
            destVal = new Column(column)
        }
        this.destVal = destVal
        this.sourceList = sourceList
        this.destList = destList
    }

    String getDescription()
    {
        return desc
    }

    Location getLocation()
    {
        return loc
    }

    Type getType()
    {
        return type
    }

    Object getLocId()
    {
        return locId
    }

    Object getSourceVal()
    {
        return sourceVal
    }

    Object getDestVal()
    {
        return destVal
    }

    Object[] getSourceList()
    {
        return sourceList
    }

    Object[] getDestList()
    {
        return destList
    }

    String toString()
    {
        return desc
    }

    int hashCode()
    {
        if (StringUtilities.isEmpty(desc))
        {
            return 0
        }
        return desc.hashCode()
    }

    boolean equals(Object other)
    {
        if (this.is(other))
        {
            return true
        }
        if (!(other instanceof Delta))
        {
            return false
        }
        Delta that = (Delta) other
        return desc == that.desc && loc == that.loc && type == that.type
    }

    int compareTo(Object other)
    {
        if (other == null || !(other instanceof Delta))
        {
            return 1
        }

        Delta that = (Delta) other

        if (loc < that.loc)
        {
            return -1
        }
        if (loc > that.loc)
        {
            return 1
        }

        // Location is the same, now look at type
        if (type < that.type)
        {
            return -1
        }
        if (type > that.type)
        {
            return 1
        }

        // Location and Type are the same, order by description
        if (StringUtilities.isEmpty(desc))
        {
            if (StringUtilities.isEmpty(that.desc))
            {
                return 0
            }
            return -1
        }
        return desc.compareToIgnoreCase(that.desc)
    }
}
