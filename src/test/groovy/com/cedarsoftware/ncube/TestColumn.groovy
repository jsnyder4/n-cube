package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.formatters.JsonFormatter
import com.cedarsoftware.util.io.JsonReader
import groovy.transform.CompileStatic
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNull

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the 'License')
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an 'AS IS' BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestColumn extends NCubeBaseTest
{
    @Test
    void testSetValue()
    {
        Column c = new Column(0)
        assertEquals(0, c.value)
        c.value = 5
        assertEquals(5, c.value)
    }

    @Test
    void testMetaProperties()
    {
        Column c = new Column(true, 5)

        assertNull c.removeMetaProperty('foo')
        assertNull c.metaProperties.get('foo')

        c.clearMetaProperties()
        c.setMetaProperty 'foo', 'bar'
        assertEquals 'bar', c.metaProperties.get('foo')
        assertEquals 'bar', c.getMetaProperty('foo')

        c.clearMetaProperties()
        assertNull c.metaProperties.get('foo')
        assertNull c.getMetaProperty('foo')
        c.clearMetaProperties()
        Map map = new HashMap()
        map.put 'BaZ', 'qux'

        c.addMetaProperties(map)
        assertEquals 'qux', c.metaProperties.get('baz')
        assertEquals 'qux', c.getMetaProperty('baz')

        assertEquals 'qux', c.removeMetaProperty('baz')
        assertEquals null, c.removeMetaProperty('baz')
    }

    @Test
    void testHashCode()
    {
        Column c1 = new Column("alpha", 1)
        Column c2 = new Column("beta", 2)
        assert c1.hashCode() != c2.hashCode()
    }

    @Test
    void testDeleteRuleColumnBadInput()
    {
        NCube ncube = NCubeBuilder.getRule1D()
        Axis rule = (Axis) ncube.getAxis('rule')
        assert rule.size() == 2
        assert !ncube.deleteColumn('rule', new Date())
        assert rule.size() == 2
    }

    @Test
    void testCompareTo()
    {
        Column col1 = new Column(null, 1)
        Column col2 = new Column('hi', 2)
        assert -1 == col1.compareTo(col2)

        col1 = new Column('hi', 1)
        col2 = new Column(null, 2)
        assert 1 == col1.compareTo(col2)

        col1 = new Column('a', 1)
        col2 = new Column('z', 2)
        assert -1 == col1.compareTo(col2)

        col1 = new Column('z', 1)
        col2 = new Column('a', 2)
        assert 1 == col1.compareTo(col2)

        col1 = new Column('a', 1)
        col2 = new Column('a', 2)
        assert 0 == col1.compareTo(col2)
    }

    @Test
    void testMetaPropertiesOnDefaultColumn()
    {
        NCube ncube = NCubeBuilder.getDiscrete1DEmptyWithDefault()
        Axis state = ncube.getAxis('state')
        state.defaultColumn.setMetaProperty("default_value", 2017i)
        assert 2017i == ncube.getCell([state: 'FL'])

        String json = ncube.toFormattedJson()
        NCube ncube2 = NCube.fromSimpleJson(json)
        assert 2017i == ncube2.getCell([state:'FL'])
        state = ncube2.getAxis('state')
        state.defaultColumn.setMetaProperty('foo', 'bar')
        state.defaultColumn.setMetaProperty('baz', 'qux')

        json = ncube2.toFormattedJson()
        NCube ncube3 = NCube.fromSimpleJson(json)
        assert 2017i == ncube3.getCell([state:'FL'])
        state = ncube2.getAxis('state')
        assert 'bar' == state.defaultColumn.getMetaProperty('foo')
        assert 'qux' == state.defaultColumn.getMetaProperty('baz')
        assert 2017i == state.defaultColumn.getMetaProperty('default_value')

        json = ncube2.toFormattedJson([indexFormat:true])
        Map cube4 = JsonReader.jsonToMaps(json)
        Map axes = cube4.axes as Map
        Map<String, Object> state2 = axes.state as Map
        Map<String, Object> defColMetaProps = state2.findAll { String key, Object value -> key.startsWith(JsonFormatter.DEFAULT_COLUMN_PREFIX) }

        assert defColMetaProps.size() == 3
        assert defColMetaProps[JsonFormatter.DEFAULT_COLUMN_PREFIX + 'default_value'] instanceof Map    // because it was read as a Map, not with .hydrate
        assert "bar" == defColMetaProps[JsonFormatter.DEFAULT_COLUMN_PREFIX + 'foo']
        assert "qux" == defColMetaProps[JsonFormatter.DEFAULT_COLUMN_PREFIX + 'baz']
    }
}
