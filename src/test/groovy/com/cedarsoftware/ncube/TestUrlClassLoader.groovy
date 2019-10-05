package com.cedarsoftware.ncube

import org.junit.Before
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static com.cedarsoftware.ncube.TestWithPreloadedDatabase.appId
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull
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
class TestUrlClassLoader extends NCubeCleanupBaseTest
{
    @Before
    void setup()
    {
    }

    static int getCacheSize(ApplicationID applicationID)
    {
        return testClient.getCacheForApp(applicationID).cache.size()
    }

    @Test
    void testToMakeSureOldStyleSysClasspathThrowsException()
    {
        preloadCubes(appId, 'sys.classpath.old.style.json')

        // nothing in cache until we try and get the classloader or load a cube.
        assertEquals(0, getCacheSize(appId))

        //  url classloader has 1 item
        try
        {
            ncubeRuntime.getUrlClassLoader(appId, [:])
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'sys.classpath cube', 'exists', 'urlclassloader')
        }
    }

    @Test
    void testUrlClassLoader()
    {
        preloadCubes(appId, 'sys.classpath.cp1.json')

        // nothing in cache until we try and get the classloader or load a cube.
        assertEquals(0, getCacheSize(appId))

        //  url classloader has 1 item
        Map input = [:]
        URLClassLoader loader = ncubeRuntime.getUrlClassLoader(appId, input)
        assertEquals(1, loader.URLs.length)
        assertEquals(3, getCacheSize(appId))
        assertEquals(new URL("${baseRemoteUrl}/tests/ncube/cp1/"), loader.URLs[0])

        assertEquals(3, getCacheSize(appId))

        assertNotNull(ncubeRuntime.getUrlClassLoader(appId, input))
        assertEquals(3, getCacheSize(appId))

        ncubeRuntime.clearCache(appId)
        assertEquals(0, getCacheSize(appId))

        assertEquals(1, ncubeRuntime.getUrlClassLoader(appId, input).URLs.length)
        assertEquals(3, getCacheSize(appId))
    }

    @Test
    void testClearCacheWithClassLoaderLoadedByCubeRequest()
    {
        preloadCubes(appId, 'sys.classpath.cp1.json', 'GroovyMethodClassPath1.json')

        assertEquals(0, getCacheSize(appId))
        NCube cube = ncubeRuntime.getCube(appId, 'GroovyMethodClassPath1')
        assertEquals(2, getCacheSize(appId))

        Map input = [method: 'foo']
        Object x = cube.getCell(input)
        assertEquals('foo', x)

        assertEquals(4, getCacheSize(appId))

        input.method = 'foo2'
        x = cube.getCell(input)
        assertEquals('foo2', x)

        input.method = 'bar'
        x = cube.getCell(input)
        assertEquals('Bar', x)

        NCube sysClassPath2 = createRuntimeCubeFromResource(appId, 'sys.classpath.cp2.json')
        mutableClient.updateCube(sysClassPath2)

        //  clear cache so we get different answers this time.  classpath 2 has already been loaded in database.
        ncubeRuntime.clearCache(appId)

        assertEquals(0, getCacheSize(appId))

        cube = ncubeRuntime.getCube(appId, 'GroovyMethodClassPath1')
        assertEquals(2, getCacheSize(appId))

        input = [method: 'foo']
        x = cube.getCell(input)
        assertEquals('boo', x)

        assertEquals(4, getCacheSize(appId))

        input.method = 'foo2'
        x = cube.getCell(input)
        assertEquals('boo2', x)

        input.method = 'bar'
        x = cube.getCell(input)
        assertEquals('far', x)
    }

    @Test
    void testMultiCubeClassPath()
    {
        preloadCubes(appId, 'sys.classpath.base.json', 'sys.classpath.json', 'sys.status.json', 'sys.versions.json', 'sys.version.json', 'GroovyMethodClassPath1.json')

        assertEquals(0, getCacheSize(appId))
        NCube cube = ncubeRuntime.getCube(appId, 'GroovyMethodClassPath1')

        // classpath isn't loaded at this point.
        assertEquals(2, getCacheSize(appId))

        def input = [:]
        input.env = 'DEV'
        input.put('method', 'foo')
        Object x = cube.getCell(input)
        assertEquals('foo', x)

        assertEquals(6, getCacheSize(appId))

        // cache hasn't been cleared yet.
        input.put('method', 'foo2')
        x = cube.getCell(input)
        assertEquals('foo2', x)

        input.put('method', 'bar')
        x = cube.getCell(input)
        assertEquals('Bar', x)

        ncubeRuntime.clearCache(appId)

        // Had to reget cube so I had a new classpath
        cube = ncubeRuntime.getCube(appId, 'GroovyMethodClassPath1')

        input.env = 'UAT'
        input.put('method', 'foo')
        x = cube.getCell(input)

        assertEquals('boo', x)

        assertEquals(6, getCacheSize(appId))

        input.put('method', 'foo2')
        x = cube.getCell(input)
        assertEquals('boo2', x)

        input.put('method', 'bar')
        x = cube.getCell(input)
        assertEquals('far', x)

        //  clear cache so we get different answers this time.  classpath 2 has already been loaded in database.
        ncubeRuntime.clearCache(appId)
        assertEquals(0, getCacheSize(appId))
    }

    @Test
    void testTwoClasspathsSameAppId()
    {
        preloadCubes(appId, 'sys.classpath.2per.app.json', 'GroovyExpCp1.json')

        assertEquals(0, getCacheSize(appId))
        NCube cube = ncubeRuntime.getCube(appId, 'GroovyExpCp1')

        // classpath isn't loaded at this point.
        assertEquals(2, getCacheSize(appId))

        def input = [:]
        input.env = 'a'
        input.state = 'OH'
        def x = cube.getCell(input)
        assert 'Hello, world.' == x

        // GroovyExpCp1, sys.classpath, sys.prototype are now both loaded.
        assertEquals(4, getCacheSize(appId))

        input.env = 'b'
        input.state = 'TX'
        def y = cube.getCell(input)
        assert 'Goodbye, world.' == y

        // Test JsonFormatter - that it properly handles the URLClassLoader in the sys.classpath cube
        NCube cp1 = ncubeRuntime.getCube(appId, 'sys.classpath')
        String json = cp1.toFormattedJson()

        NCube cp2 = NCube.fromSimpleJson(json)
        cp1.clearSha1()
        cp2.clearSha1()
        String json1 = cp1.toFormattedJson()
        String json2 = cp2.toFormattedJson()
        assertEquals(json1, json2)

        // Test HtmlFormatter - that it properly handles the URLClassLoader in the sys.classpath cube
        String html = cp1.toHtml()
        assert html.contains("${baseRemoteUrl}")
    }

    @Test
    void testMathControllerUsingExpressions()
    {
        preloadCubes(appId, 'sys.classpath.2per.app.json', 'math.controller.json')

        assertEquals(0, getCacheSize(appId))
        NCube cube = ncubeRuntime.getCube(appId, 'MathController')

        // classpath isn't loaded at this point.
        assertEquals(2, getCacheSize(appId))
        def input = [:]
        input.env = 'a'
        input.x = 5
        input.method = 'square'

        assertEquals(2, getCacheSize(appId))
        assertEquals(25, cube.getCell(input))
        assertEquals(4, getCacheSize(appId))

        input.method = 'factorial'
        assertEquals(120, cube.getCell(input))

        // same number of cubes, different cells
        assertEquals(4, getCacheSize(appId))

        // test that shows you can add an axis to a controller to selectively choose a new classpath
        input.env = 'b'
        input.method = 'square'
        assertEquals(5, cube.getCell(input))
        assertEquals(4, getCacheSize(appId))

        input.method = 'factorial'
        assertEquals(5, cube.getCell(input))
        assertEquals(4, getCacheSize(appId))
    }
}