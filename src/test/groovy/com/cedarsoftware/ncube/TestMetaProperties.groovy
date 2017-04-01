package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.proximity.LatLon
import groovy.transform.CompileStatic
import org.junit.Test

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestMetaProperties extends NCubeBaseTest
{
    @Test
    void testComplexMetaProperties()
    {
        NCube small = small

        small.setMetaProperty('jersey-number', (byte)16)
        small.setMetaProperty('jersey-number2', new CellInfo((byte)16))
        small.setMetaProperty('age', 40L)
        small.setMetaProperty('age2', new CellInfo(40L))
        small.setMetaProperty('smoker', false)
        small.setMetaProperty('smoker2', new CellInfo(false))
        small.setMetaProperty('pi', Math.PI)
        small.setMetaProperty('pi2', new CellInfo(Math.PI))
        small.setMetaProperty('big.pi', new BigDecimal(Math.PI))
        small.setMetaProperty('big.pi2', new CellInfo(new BigDecimal(Math.PI)))
        small.setMetaProperty('code', new GroovyExpression('return "hello"', null, false))
        small.setMetaProperty('code2', new GroovyExpression('return "hello"', null, false))
        small.setMetaProperty('null', null)
        small.setMetaProperty('null2', new CellInfo(null))
        small.setMetaProperty('latlon', new LatLon(51.5034070,-0.1275920))
        small.setMetaProperty('latlon2', new CellInfo(new LatLon(51.5034070,-0.1275920)))
        small.setMetaProperty('command', new GroovyExpression(null, 'http://ncube.io', true))
        small.setMetaProperty('command2', new CellInfo(new GroovyExpression(null, 'http://ncube.io', true)))

        String json = small.toFormattedJson()
        NCube copy = NCube.fromSimpleJson(json)
        def x = copy.getMetaProperty('jersey-number')
        assert x instanceof Byte
        assert x == 16

        x = copy.getMetaProperty('age')
        assert x instanceof Long
        assert x == 40L
        x = copy.getMetaProperty('age2')
        assert x instanceof Long
        assert x == 40L

        x = copy.getMetaProperty('smoker')
        assert x instanceof Boolean
        assert !x
        x = copy.getMetaProperty('smoker2')
        assert x instanceof Boolean
        assert !x

        x = copy.getMetaProperty('pi')
        assert x instanceof Double
        assert x == Math.PI
        x = copy.getMetaProperty('pi2')
        assert x instanceof Double
        assert x == Math.PI

        x = copy.getMetaProperty('code')
        assert x instanceof GroovyExpression
        GroovyExpression exp = (GroovyExpression) x
        assert exp.cmd == 'return "hello"'
        x = copy.getMetaProperty('code2')
        assert x instanceof GroovyExpression
        assert exp.cmd == 'return "hello"'

        x = copy.getMetaProperty('null')
        assert x == null
        x = copy.getMetaProperty('null2')
        assert x == null

        x = copy.getMetaProperty('latlon')
        assert x instanceof LatLon
        LatLon latlon = (LatLon) x
        assert latlon.lat == 51.5034070d
        assert latlon.lon == -0.1275920d
        x = copy.getMetaProperty('latlon2')
        assert x instanceof LatLon
        latlon = (LatLon) x
        assert latlon.lat == 51.5034070d
        assert latlon.lon == -0.1275920d

        x = copy.getMetaProperty('command')
        assert x instanceof GroovyExpression
        exp = (GroovyExpression) x
        assert exp.url == 'http://ncube.io'
        assert exp.cacheable
        x = copy.getMetaProperty('command2')
        assert x instanceof GroovyExpression
        assert exp.url == 'http://ncube.io'
        assert exp.cacheable
    }

    @Test
    void testAxisMetaProps()
    {
        NCube small = small
        Axis axis = small.getAxis('state')

        // Only do simple and complex here (all other combinations already tested)
        axis.setMetaProperty('age', 40L)
        axis.setMetaProperty('age2', new CellInfo(40L))
        axis.setMetaProperty('latlon', new LatLon(51.5034070,-0.1275920))
        axis.setMetaProperty('latlon2', new CellInfo(new LatLon(51.5034070,-0.1275920)))

        String json = small.toFormattedJson()
        NCube copy = NCube.fromSimpleJson(json)
        Axis copyAxis = copy.getAxis('state')

        def x = copyAxis.getMetaProperty('age')
        assert x instanceof Long
        assert x == 40L
        x = copyAxis.getMetaProperty('age2')
        assert x instanceof Long
        assert x == 40L

        x = copyAxis.getMetaProperty('latlon')
        assert x instanceof LatLon
        LatLon latlon = (LatLon) x
        assert latlon.lat == 51.5034070d
        assert latlon.lon == -0.1275920d
        x = copyAxis.getMetaProperty('latlon2')
        assert x instanceof LatLon
        latlon = (LatLon) x
        assert latlon.lat == 51.5034070d
        assert latlon.lon == -0.1275920d
    }

    @Test
    void testColumnMetaProps()
    {
        NCube small = small
        Axis axis = small.getAxis('state')
        Column column = axis.findColumn('OH')

        // Only do simple and complex here (all other combinations already tested)
        column.setMetaProperty('age', 40L)
        column.setMetaProperty('age2', new CellInfo(40L))
        column.setMetaProperty('latlon', new LatLon(51.5034070,-0.1275920))
        column.setMetaProperty('latlon2', new CellInfo(new LatLon(51.5034070,-0.1275920)))

        String json = small.toFormattedJson()
        NCube copy = NCube.fromSimpleJson(json)
        Axis copyAxis = copy.getAxis('state')
        Column copyCol = copyAxis.findColumn('OH')

        def x = copyCol.getMetaProperty('age')
        assert x instanceof Long
        assert x == 40L
        x = copyCol.getMetaProperty('age2')
        assert x instanceof Long
        assert x == 40L

        x = copyCol.getMetaProperty('latlon')
        assert x instanceof LatLon
        LatLon latlon = (LatLon) x
        assert latlon.lat == 51.5034070d
        assert latlon.lon == -0.1275920d
        x = copyCol.getMetaProperty('latlon2')
        assert x instanceof LatLon
        latlon = (LatLon) x
        assert latlon.lat == 51.5034070d
        assert latlon.lon == -0.1275920d
    }

    private static NCube getSmall()
    {
        NCube small = new NCube('small')
        Axis axis = new Axis('state', AxisType.DISCRETE, AxisValueType.STRING, false)
        axis.addColumn('OH')
        small.addAxis(axis)
        return small
    }
}
