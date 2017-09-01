package com.cedarsoftware.ncube.formatters

import com.cedarsoftware.ncube.*
import com.cedarsoftware.util.IOUtilities
import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.JsonWriter
import groovy.transform.CompileStatic
import org.junit.Test
import org.springframework.util.FastByteArrayOutputStream

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static org.junit.Assert.assertEquals
import static org.junit.Assert.fail
import static org.mockito.Matchers.anyInt
import static org.mockito.Matchers.anyObject
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when

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
class TestJsonFormatter extends NCubeBaseTest
{
    public static ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, "clearCacheTest", ApplicationID.DEFAULT_VERSION, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)

    @Test
    void testJsonFormatter()
    {
        // when running a single test.
        //List<String> s = new ArrayList<String>()
        //s.add('urlContent.json')
        List<String> s = allTestFiles
        runAllTests(s)
    }

    @Test
    void testConvertArray()
    {
        createRuntimeCubeFromResource(ApplicationID.testAppId, 'sys.classpath.tests.json')
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'arrays.json')
        
        def coord = [Code:'longs']
        assertEquals 9223372036854775807L, ((Object[]) ncube.getCell(coord))[2]

        coord.Code = 'ints'
        assertEquals 2147483647, ((Object[]) ncube.getCell(coord))[2]

        coord.Code = 'bytes'
        assertEquals 127 as byte, ((Object[]) ncube.getCell(coord))[2]

        coord.Code = 'shorts'
        assertEquals 32767 as short, ((Object[]) ncube.getCell(coord))[2]

        coord.Code = 'booleans'
        assertEquals Boolean.TRUE, ((Object[]) ncube.getCell(coord))[2]
        assertEquals Boolean.FALSE, ((Object[]) ncube.getCell(coord))[3]

        coord.Code = 'floats'
        assertEquals(3.8d, ((Object[]) ncube.getCell(coord))[2] as double, 0.00001d)

        coord.Code = 'doubles'
        assertEquals(10.1d, ((Object[]) ncube.getCell(coord))[2] as double, 0.00001d)

        coord.Code = 'bigints'
        assertEquals 0g, ((Object[]) ncube.getCell(coord))[0]
        assertEquals 9223372036854775807g, ((Object[]) ncube.getCell(coord))[2]
        assertEquals 147573952589676410000g, ((Object[]) ncube.getCell(coord))[3]

        String s = ncube.toFormattedJson()
        ncube = NCube.fromSimpleJson(s)

        coord.Code = 'longs'
        assertEquals 9223372036854775807L, ((Object[]) ncube.getCell(coord))[2]

        coord.Code = 'ints'
        assertEquals 2147483647, ((Object[]) ncube.getCell(coord))[2]

        coord.Code = 'bytes'
        assertEquals 127 as byte, ((Object[]) ncube.getCell(coord))[2]

        coord.Code = 'shorts'
        assertEquals 32767 as short, ((Object[]) ncube.getCell(coord))[2]

        coord.Code = 'booleans'
        assertEquals Boolean.TRUE, ((Object[]) ncube.getCell(coord))[2]
        assertEquals Boolean.FALSE, ((Object[]) ncube.getCell(coord))[3]

        coord.Code = 'floats'
        assertEquals(3.8f, ((Object[]) ncube.getCell(coord))[2] as double, 0.00001d)

        coord.Code= 'doubles'
        assertEquals(10.1d, ((Object[]) ncube.getCell(coord))[2] as double, 0.00001d)

        coord.Code = 'bigints'
        assertEquals new BigInteger('0'), ((Object[]) ncube.getCell(coord))[0]
        assertEquals new BigInteger('9223372036854775807'), ((Object[]) ncube.getCell(coord))[2]
        assertEquals new BigInteger('147573952589676410000'), ((Object[]) ncube.getCell(coord))[3]
    }

    @Test
    void testInvalidNCube()
    {
        NCube ncube = new NCube(null)
        JsonFormatter formatter = new JsonFormatter()
        String json = formatter.format(ncube)
        assertEquals('{"ncube":null,"axes":[],"cells":[]}', json)
    }

    @Test
    void testNullValueGoingToAppend()
    {
        OutputStream stream = mock(OutputStream.class)
        when(stream.write(anyObject() as byte[], anyInt(), anyInt()) as Object).thenThrow(new IOException("foo error"))

        BufferedInputStream input = null

        try
        {
            JsonFormatter formatter = new JsonFormatter(stream)
            formatter.append((String)null)
            fail()
        }
        catch (NullPointerException ignored)
        {
        }
        finally
        {
            IOUtilities.close((Closeable)input)
        }
    }

    @Test
    void testNullCube()
    {
        try
        {
            new JsonFormatter().format(null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot be null')
        }
    }

    @Test
    void testTryingToUseFormatToWriteToStream()
    {
        FastByteArrayOutputStream stream = new FastByteArrayOutputStream()
        JsonFormatter formatter = new JsonFormatter(stream)
        try
        {
            formatter.format(null)
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'builder is not a stringwriter')
        }
    }

    @Test
    void testCubeWithInvalidDefaultCell()
    {
        try
        {
            NCube cube = new NCube('foo')
            cube.defaultCellValue = new NCube('bar')
            new JsonFormatter().format(cube)
            fail()
        }
        catch (IllegalStateException e)
        {
            assertEquals IllegalArgumentException.class, e.cause.class
            assertContainsIgnoreCase(e.message, 'unable to format')
        }
    }

    @Test
    void testCubeWithInvalidDefaultCellArrayType()
    {
        try
        {
            NCube cube = new NCube('foo')
            cube.defaultCellValue = [] as Object[]
            new JsonFormatter().format cube
            fail 'should not make it here'
        }
        catch (IllegalStateException e)
        {
            assertEquals(IllegalArgumentException.class, e.cause.class)
            assertContainsIgnoreCase(e.message, 'unable to format')
            assertContainsIgnoreCase(e.cause.message, 'cell cannot be an array')
        }
    }

    @Test
    void testAlternateJsonFormat()
    {
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, "idBasedCube.json")
        String json = ncube.toFormattedJson([indexFormat: true])
        assert json.contains('"cells":{"1')
        assert json.contains('":{"type":"string","value":"1 10"}')
        assert json.contains('"axes":{"age":{"id":1,"name":"Age"')
        assert json.contains('"columns":{"2')
        assert json.contains('":{"type":"string","value":"CA"}')

        json = ncube.toFormattedJson([indexFormat: false])
        assert json.contains('"cells":[{"id":[')
        assert json.contains('],"type":"string","value":"1 10"}')
        assert json.contains('"axes":[{"id":1,"name":"Age"')
        assert json.contains('"columns":[{"id":2')
        assert json.contains(',"type":"string","value":"CA"}')
    }

    @Test
    void testNCubeUsesCustomReaderWriterWithJsonIo()
    {
        NCube ncube = NCubeBuilder.get5DTestCube()
        ApplicationID appId = new ApplicationID('foo', 'bar', '1.0.4', 'SNAPSHOT', 'baz')
        ncube.applicationID = appId
        String json = JsonWriter.objectToJson(ncube)
        NCube cube = JsonReader.jsonToJava(json) as NCube
        assert cube.name == 'testMerge'
        assert cube.numDimensions == 5
        assert cube.getAxis('age').size() == 2
        assert cube.getAxis('salary').size() == 2
        assert cube.getAxis('log').size() == 2
        assert cube.getAxis('rule').size() == 2
        assert cube.getAxis('state').size() == 3
        assert cube.applicationID == appId
        assert cube.metaProperties.size() == 0
    }

    private static class TestFilenameFilter implements FilenameFilter
    {
        boolean accept(File dir, String name)
        {
            return name != null && name.endsWith('.json') &&
                    !(name.endsWith('idBasedCubeError.json') ||
                            name.endsWith('badJsonNoNcubeRoot.json') ||
                            name.endsWith('idBasedCubeError2.json') ||
                            name.endsWith('error.json') ||
                            name.endsWith('2D1Ref.json') ||
                            name.endsWith('arrays.json') ||  /** won't have equivalency **/
                            name.endsWith('testCubeList.json'))   /** list of cubes **/
        }
    }

    List<String> getAllTestFiles()
    {
        URL u = getClass().classLoader.getResource('')
        File dir = new File(u.file)
        File[] files = dir.listFiles(new TestFilenameFilter())
        List<String> names = new ArrayList<>(files.length)

        for (File f : files)
        {
            names.add f.name
        }
        return names
    }

    void runAllTests(List<String> strings)
    {
        for (String f : strings)
        {
            String original = NCubeRuntime.getResourceAsString(f)
            NCube ncube = NCube.fromSimpleJson(original)

            //long start = System.nanoTime()
            String s = ncube.toFormattedJson()
//            System.out.println(s)
            NCube res = NCube.fromSimpleJson((String)s)
            //long end = System.nanoTime()
            assertEquals(res, ncube)
            //long time = (end-start)/1000000;
            //if (time > 250) {
                //System.out.println(f + " " + time)
            //}
        }
    }
}