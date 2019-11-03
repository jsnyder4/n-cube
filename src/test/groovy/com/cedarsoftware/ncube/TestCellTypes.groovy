package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.proximity.LatLon
import com.cedarsoftware.ncube.proximity.Point2D
import com.cedarsoftware.ncube.proximity.Point3D
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.junit.Test

import java.util.regex.Matcher

import static com.cedarsoftware.util.Converter.*
import static com.cedarsoftware.util.StringUtilities.decode
import static org.junit.Assert.*

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
class TestCellTypes
{
    @Test
    void testInvalidCellType()
    {
        try
        {
            CellInfo.getType(new StringBuilder(), 'cells')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertEquals 'Unsupported type java.lang.StringBuilder found in cells', e.message
        }
    }

    @Test
    void testGetTypeOnClassLoader()
    {
        try {
            assertEquals("exp", CellInfo.getType(TestCellTypes.classLoader, 'cells'))
            fail()
        } catch (IllegalArgumentException e) {
            assertTrue(e.message.toLowerCase().contains("unsupported type"))
        }
    }

    @Test
    void testGetType()
    {
        assert 'boolean' == CellInfo.getType(Boolean.TRUE, 'cells')
        assert 'int' == CellInfo.getType(Integer.MAX_VALUE, 'cells')
        assert 'long' == CellInfo.getType(Long.MAX_VALUE, 'cells')
        assert 'double' == CellInfo.getType(Double.MAX_VALUE, 'cells')
        assert 'float' == CellInfo.getType(Float.MAX_VALUE, 'cells')
        assert 'string' == CellInfo.getType(null, 'cells')
    }

    @Test
    void testRecreate()
    {
        assertNull new CellInfo(null).recreate()

        performRecreateAssertion new StringUrlCmd('http://www.google.com', true)
        performRecreateAssertion new Double(4.56d)
        performRecreateAssertion new Float(4.56f)
        performRecreateAssertion new Short((short) 4)
        performRecreateAssertion new Long(4)
        performRecreateAssertion new Integer(4)
        performRecreateAssertion new Byte((byte) 4)
        performRecreateAssertion new BigDecimal('4.56')
        performRecreateAssertion new BigInteger('900')
        performRecreateAssertion Boolean.TRUE
        performRecreateAssertion new GroovyExpression('0', null, false)
        performRecreateAssertion new GroovyMethod('0', null, false)
        performRecreateAssertion new GroovyTemplate(null, 'http://www.google.com', false)
        performRecreateAssertion new BinaryUrlCmd('http://www.google.com', false)
        performRecreateAssertion 'foo'

        performRecreateAssertion new LatLon(5.5, 5.8)
        performRecreateAssertion new Point2D(5.5, 5.8)
        performRecreateAssertion new Point3D(5.5, 5.8, 5.9)

        //  Have to special create this because milliseconds are not saved
        Calendar c = Calendar.instance
        c.set Calendar.MILLISECOND, 0
        performRecreateAssertion c.time
    }

    @Test
    void testRecreateExceptions()
    {
        try
        {
            def result
            switch ('latlon')
            {
                case 'string':
                    result = false ? new StringUrlCmd('foo', false) : 'foo'
                    break

                case 'date':
                    result = convertToDate('foo')
                    break

                case 'boolean':
                    result = convertToBoolean('foo')
                    break

                case 'byte':
                    result = convertToByte('foo')
                    break

                case 'short':
                    result = convertToShort('foo')
                    break

                case 'int':
                    result = convertToInteger('foo')
                    break

                case 'long':
                    result = convertToLong('foo')
                    break

                case 'float':
                    result = convertToFloat('foo')
                    break

                case 'double':
                    result = convertToDouble('foo')
                    break

                case 'bigdec':
                    result = convertToBigDecimal('foo')
                    break

                case 'bigint':
                    result = convertToBigInteger('foo')
                    break

                case 'binary':
                    result = false ? new BinaryUrlCmd('foo', false) : decode('foo')
                    break

                case 'exp':
                    result = new GroovyExpression(false ? null : 'foo', false ? 'foo' : null, false)
                    break

                case 'method':
                    result = new GroovyMethod(false ? null : 'foo', false ? 'foo' : null, false)
                    break

                case 'template':
                    result = new GroovyTemplate(false ? null : 'foo', false ? 'foo' : null, false)
                    break

                case 'latlon':
                    Matcher m = Regexes.valid2Doubles.matcher('foo')
                    if (!m.matches())
                    {
                        throw new IllegalArgumentException(String.format("Invalid Lat/Long value (%s)", 'foo'))
                    }
                    result = new LatLon(convertToDouble(m.group(1)), convertToDouble(m.group(2)))
                    break

                case 'point2d':
                    Matcher m = Regexes.valid2Doubles.matcher('foo')
                    if (!m.matches())
                    {
                        throw new IllegalArgumentException(String.format("Invalid Point2D value (%s)", 'foo'))
                    }
                    result = new Point2D(convertToDouble(m.group(1)), convertToDouble(m.group(2)))
                    break

                case 'point3d':
                    Matcher m = Regexes.valid3Doubles.matcher('foo')
                    if (!m.matches())
                    {
                        throw new IllegalArgumentException(String.format("Invalid Point3D value (%s)", 'foo'))
                    }
                    result = new Point3D(convertToDouble(m.group(1)),
                            convertToDouble(m.group(2)),
                            convertToDouble(m.group(3)))
                    break

                case 'null':
                    result = null
                    break

                case null:
                    result = null
                    break

                default:
                    throw new IllegalArgumentException("Invalid Type:  " + 'latlon')
            }
            fail()
        }
        catch (Exception e)
        {
            assert e.message.contains('Invalid Lat/Long')
        }

        try
        {
            def result
            switch ('point2d')
            {
                case 'string':
                    result = false ? new StringUrlCmd('foo', false) : 'foo'
                    break

                case 'date':
                    result = convertToDate('foo')
                    break

                case 'boolean':
                    result = convertToBoolean('foo')
                    break

                case 'byte':
                    result = convertToByte('foo')
                    break

                case 'short':
                    result = convertToShort('foo')
                    break

                case 'int':
                    result = convertToInteger('foo')
                    break

                case 'long':
                    result = convertToLong('foo')
                    break

                case 'float':
                    result = convertToFloat('foo')
                    break

                case 'double':
                    result = convertToDouble('foo')
                    break

                case 'bigdec':
                    result = convertToBigDecimal('foo')
                    break

                case 'bigint':
                    result = convertToBigInteger('foo')
                    break

                case 'binary':
                    result = false ? new BinaryUrlCmd('foo', false) : decode('foo')
                    break

                case 'exp':
                    result = new GroovyExpression(false ? null : 'foo', false ? 'foo' : null, false)
                    break

                case 'method':
                    result = new GroovyMethod(false ? null : 'foo', false ? 'foo' : null, false)
                    break

                case 'template':
                    result = new GroovyTemplate(false ? null : 'foo', false ? 'foo' : null, false)
                    break

                case 'latlon':
                    Matcher m = Regexes.valid2Doubles.matcher('foo')
                    if (!m.matches())
                    {
                        throw new IllegalArgumentException(String.format("Invalid Lat/Long value (%s)", 'foo'))
                    }
                    result = new LatLon(convertToDouble(m.group(1)), convertToDouble(m.group(2)))
                    break

                case 'point2d':
                    Matcher m = Regexes.valid2Doubles.matcher('foo')
                    if (!m.matches())
                    {
                        throw new IllegalArgumentException(String.format("Invalid Point2D value (%s)", 'foo'))
                    }
                    result = new Point2D(convertToDouble(m.group(1)), convertToDouble(m.group(2)))
                    break

                case 'point3d':
                    Matcher m = Regexes.valid3Doubles.matcher('foo')
                    if (!m.matches())
                    {
                        throw new IllegalArgumentException(String.format("Invalid Point3D value (%s)", 'foo'))
                    }
                    result = new Point3D(convertToDouble(m.group(1)),
                            convertToDouble(m.group(2)),
                            convertToDouble(m.group(3)))
                    break

                case 'null':
                    result = null
                    break

                case null:
                    result = null
                    break
                
                default:
                    throw new IllegalArgumentException("Invalid Type:  " + 'point2d')
            }
            fail()
        }
        catch (Exception e)
        {
            assert e.message.contains('Invalid Point2D')
        }

        try
        {
            def result
            switch ('point3d')
            {
                case 'string':
                    result = false ? new StringUrlCmd('foo', false) : 'foo'
                    break

                case 'date':
                    result = convertToDate('foo')
                    break

                case 'boolean':
                    result = convertToBoolean('foo')
                    break

                case 'byte':
                    result = convertToByte('foo')
                    break

                case 'short':
                    result = convertToShort('foo')
                    break

                case 'int':
                    result = convertToInteger('foo')
                    break

                case 'long':
                    result = convertToLong('foo')
                    break

                case 'float':
                    result = convertToFloat('foo')
                    break

                case 'double':
                    result = convertToDouble('foo')
                    break

                case 'bigdec':
                    result = convertToBigDecimal('foo')
                    break

                case 'bigint':
                    result = convertToBigInteger('foo')
                    break

                case 'binary':
                    result = false ? new BinaryUrlCmd('foo', false) : decode('foo')
                    break

                case 'exp':
                    result = new GroovyExpression(false ? null : 'foo', false ? 'foo' : null, false)
                    break

                case 'method':
                    result = new GroovyMethod(false ? null : 'foo', false ? 'foo' : null, false)
                    break

                case 'template':
                    result = new GroovyTemplate(false ? null : 'foo', false ? 'foo' : null, false)
                    break

                case 'latlon':
                    Matcher m = Regexes.valid2Doubles.matcher('foo')
                    if (!m.matches())
                    {
                        throw new IllegalArgumentException(String.format("Invalid Lat/Long value (%s)", 'foo'))
                    }
                    result = new LatLon(convertToDouble(m.group(1)), convertToDouble(m.group(2)))
                    break

                case 'point2d':
                    Matcher m = Regexes.valid2Doubles.matcher('foo')
                    if (!m.matches())
                    {
                        throw new IllegalArgumentException(String.format("Invalid Point2D value (%s)", 'foo'))
                    }
                    result = new Point2D(convertToDouble(m.group(1)), convertToDouble(m.group(2)))
                    break

                case 'point3d':
                    Matcher m = Regexes.valid3Doubles.matcher('foo')
                    if (!m.matches())
                    {
                        throw new IllegalArgumentException(String.format("Invalid Point3D value (%s)", 'foo'))
                    }
                    result = new Point3D(convertToDouble(m.group(1)),
                            convertToDouble(m.group(2)),
                            convertToDouble(m.group(3)))
                    break

                case 'null':
                    result = null
                    break

                case null:
                    result = null
                    break
                
                default:
                    throw new IllegalArgumentException("Invalid Type:  " + 'point3d')
            }
            fail()
        }
        catch (Exception e)
        {
            assert e.message.contains('Invalid Point3D')
        }
    }

    @TypeChecked(TypeCheckingMode.SKIP)
    void performRecreateAssertion(Object o)
    {
        if (o instanceof Float || o instanceof Double)
        {
            assertEquals(o, new CellInfo(o).recreate(), 0.00001d)
        }
        else
        {
            assertEquals(o, new CellInfo(o).recreate())
        }
    }
}
