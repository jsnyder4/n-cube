package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.proximity.LatLon
import com.cedarsoftware.ncube.proximity.Point2D
import com.cedarsoftware.ncube.proximity.Point3D
import com.cedarsoftware.util.Converter
import groovy.transform.CompileStatic
import org.junit.Test

import java.lang.reflect.Constructor
import java.lang.reflect.Modifier

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

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
class TestProximity
{
    @Test(expected = IllegalArgumentException.class)
    void testInvalidArgument()
    {
        Proximity.distance new BigInteger('9'), new BigInteger('11')
    }

    @Test
    void testProximityBigDecimal()
    {
        BigDecimal a = 1.0g
        BigDecimal b = 101.0g
        double d = Proximity.distance(b, a)
        assertTrue(d == 100.0)
    }

    @Test
    void testConstructorIsPrivate()
    {
        Class c = Proximity.class;
        assertEquals Modifier.FINAL, c.modifiers & Modifier.FINAL

        Constructor<Proximity> con = c.getDeclaredConstructor();
        assertEquals Modifier.PRIVATE, con.modifiers & Modifier.PRIVATE
        con.accessible = true;

        assertNotNull con.newInstance()
    }

    @Test
    void testProximity()
    {
        try
        {
            Proximity.distance(null, "hey")
            fail("should not make it here")
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('neither source nor target can be null')
            assert e.message.toLowerCase().contains('nearest')
        }

        try
        {
            Proximity.distance("yo", null)
            fail("should not make it here")
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('neither source nor target can be null')
            assert e.message.toLowerCase().contains('nearest')
        }

        try
        {
            Proximity.distance("yo", 16)
            fail("should not make it here")
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('source and target data types')
            assert e.message.toLowerCase().contains('must be the same')
            assert e.message.toLowerCase().contains('nearest')
        }
    }

    @Test
    void testPoint2DhashCode()
    {
        Point2D pt1 = new Point2D(0.0d, 0.0d)
        Point2D pt2 = new Point2D(0.000001d, 0.0d)
        Map map = [:]
        map[pt1.hashCode()] = pt1
        map[pt2.hashCode()] = pt2

        Point2D pt3 = new Point2D(-1.0d, 0.0d)
        Point2D pt4 = new Point2D(0.0d, -1.0d)
        map[pt3.hashCode()] = pt3
        map[pt4.hashCode()] = pt4

        Point2D pt5 = new Point2D(1.0d, 0.0d)
        Point2D pt6 = new Point2D(-1.0d, -1.0d)
        map[pt5.hashCode()] = pt5
        map[pt6.hashCode()] = pt6

        Point2D pt7 = new Point2D(0.0d, 1.0d)
        Point2D pt8 = new Point2D(1.0d, 1.0d)
        map[pt7.hashCode()] = pt7
        map[pt8.hashCode()] = pt8

        assert map.size() == 8
    }

    @Test
    void testPoint2DhashCodeStat()
    {
        Map map = [:]
        for (int i=0; i < 10; i++)
        {
            for (int j = 0; j < 10; j++)
            {
                Point2D pt = new Point2D(i, j)
                map[pt.hashCode()] = pt
            }
        }
        assert map.size() > 95
    }

    @Test
    void testLatLonhashCodeStat()
    {
        Map map = [:]
        for (int i=0; i < 10; i++)
        {
            for (int j = 0; j < 10; j++)
            {
                LatLon pt = new LatLon(i, j)
                map[pt.hashCode()] = pt
            }
        }
        assert map.size() > 95
    }

    @Test
    void testLatLonHashCode()
    {
        LatLon pt1 = new LatLon(0.0d, 0.0d)
        LatLon pt2 = new LatLon(0.000001d, 0.0d)
        Map map = [:]
        map[pt1.hashCode()] = pt1
        map[pt2.hashCode()] = pt2

        LatLon pt3 = new LatLon(-1.0d, 0.0d)
        LatLon pt4 = new LatLon(0.0d, -1.0d)
        map[pt3.hashCode()] = pt3
        map[pt4.hashCode()] = pt4

        LatLon pt5 = new LatLon(1.0d, 0.0d)
        LatLon pt6 = new LatLon(-1.0d, -1.0d)
        map[pt5.hashCode()] = pt5
        map[pt6.hashCode()] = pt6

        LatLon pt7 = new LatLon(0.0d, 1.0d)
        LatLon pt8 = new LatLon(1.0d, 1.0d)
        map[pt7.hashCode()] = pt7
        map[pt8.hashCode()] = pt8

        assert map.size() == 8
    }


    @Test
    void testPoint3DhashCode()
    {
        Point3D pt1 = new Point3D(0.0d, 0.0d, 0.0d)
        Point3D pt2 = new Point3D(0.000001d, 0.0d, 0.0d)
        Map map = [:]
        map[pt1.hashCode()] = pt1
        map[pt2.hashCode()] = pt2

        Point3D pt3 = new Point3D(-1.0d, 0.0d, 0.0d)
        Point3D pt4 = new Point3D(0.0d, -1.0d, 0.0d)
        map[pt3.hashCode()] = pt3
        map[pt4.hashCode()] = pt4

        Point3D pt5 = new Point3D(1.0d, 0.0d, 0.0d)
        Point3D pt6 = new Point3D(-1.0d, -1.0d, 0.0d)
        map[pt5.hashCode()] = pt5
        map[pt6.hashCode()] = pt6

        Point3D pt7 = new Point3D(0.0d, 1.0d, 0.0d)
        Point3D pt8 = new Point3D(1.0d, 1.0d, 0.0d)
        map[pt7.hashCode()] = pt7
        map[pt8.hashCode()] = pt8

        assert map.size() == 8
    }

    @Test
    void testPoint3DHashCodeStat()
    {
        Map map = [:]
        for (int i=0; i < 10; i++)
        {
            for (int j = 0; j < 10; j++)
            {
                for (int k = 0; k < 10; k++)
                {
                    Point3D pt = new Point3D(i, j, k)
                    map[pt.hashCode()] = pt
                }
            }
        }
        assert map.size() > 95
    }

    @Test
    void testProximityDistance()
    {
        assert 4L == Proximity.distance(1L, 5L)
        assert 4.0d == Proximity.distance(1.0d, 5.0d)
    }

    @Test
    void testProximityDistanceDays()
    {
        Calendar c1 = Calendar.instance
        Calendar c2 = Calendar.instance
        c1.clear()
        c2.clear()
        c1.set(2015, 0, 1)
        c2.set(2016, 0, 1)
        long millis = Converter.convertToLong(Proximity.distance(c1.time, c2.time))
        long seconds = (long)(millis / 1000)
        long minutes = (long)(seconds / 60)
        long hours = (long) (minutes / 60)
        long days = (long) (hours / 24)
        assert 365L == days
    }
}
