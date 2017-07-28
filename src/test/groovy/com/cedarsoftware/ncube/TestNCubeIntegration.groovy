package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static org.junit.Assert.*

/**
 * NCube tests with database.
 *
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
class TestNCubeIntegration extends NCubeCleanupBaseTest
{
    @Test
    void testTemplateFromUrl()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'urlPieces.json')
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'urlWithNcubeRefs.json')

        def coord = [:]
        coord.put("env_level", "local")
        coord.put("protocol", "http")
        coord.put("content", "ai")
        String html = (String) ncube.getCell(coord)
        assertNotNull(html)

        coord.put("protocol", "https")
        coord.put("content", "ai")
        String html1 = (String) ncube.getCell(coord)
        assertEquals(html, html1)

        coord.put("protocol", "http")
        coord.put("content", "vend")
        String html2 = (String) ncube.getCell(coord)
        assertNotEquals(html, html2)
    }

    @Test
    void testClassLoader()
    {
        NCube ncube = createRuntimeCubeFromResource(ApplicationID.testAppId, 'ncube-class-loader-test.json')
        def coord = [:]

        coord.put("code", "local")
        assertEquals("Successful test run of local url classloader.", ncube.getCell(coord))

        coord.put("code", "remote")
        assertEquals("Successful test run of remote url classloader.", ncube.getCell(coord))
    }

    @Test
    void testExpressionFromUrl()
    {
        createRuntimeCubeFromResource(ApplicationID.testAppId, 'urlPieces.json')
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'urlWithNcubeRefs.json')

        def coord = [:]
        coord["env_level"] = "local"
        coord["protocol"] = "http"
        coord["content"] = "hello"
        String html = (String) ncube.getCell(coord)
        assertNotNull(html)
        assertEquals("Hello, world.", html)

        coord["protocol"] = "https"
        coord["content"] = "hello"
        String html1 = (String) ncube.getCell(coord)
        assertEquals(html, html1)

        coord["protocol"] = "http"
        coord["content"] = "hello2"
        def x = ncube.getCell(coord)
        assert x == "Hello, world."  // Not 2, because the same class name was used for same classpath.

        coord["protocol"] = "http"
        coord["content"] = "95"
        Integer num = (Integer) ncube.getCell(coord)
        assertEquals(95, num.intValue())
    }

    @Test
    void testExpandableUrlRef()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'urlPieces.json')
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'urlWithNcubeRefs.json')

        def coord = [:]
        coord.put("env_level", "local")
        coord.put("protocol", "http")
        coord.put("content", "ai")
        String html = (String) ncube.getCell(coord)
        assertNotNull(html)

        coord.put("protocol", "https")
        coord.put("content", "ai")
        String html1 = (String) ncube.getCell(coord)
        assertEquals(html, html1)

        coord.put("protocol", "http")
        coord.put("content", "lwt")
        String html2 = (String) ncube.getCell(coord)
        assertNotEquals(html, html2)
    }

    @Test
    void testReloadGroovyClass()
    {
        String base = System.getProperty("java.io.tmpdir")

        ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, "reloadGroovyTest", ApplicationID.DEFAULT_VERSION, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        NCube cpCube = createTempDirClassPathCube()
        cpCube.applicationID = appId

        // manually set classpath cube
        mutableClient.createCube(cpCube)

        FileOutputStream fo = new FileOutputStream(base + File.separator + "Abc.groovy")
        String code = "import ncube.grv.exp.NCubeGroovyExpression; class Abc extends NCubeGroovyExpression { def run() { return 10 } }";
        fo.write(code.bytes)
        fo.close()

        ncubeRuntime.getNCubeFromResource(appId, 'testReloadGroovyClass.json')
        NCube ncube = ncubeRuntime.getCube(appId, 'testReloadGroovy')

        def coord = [:]
        coord.put("state", "OH")
        Map output = new LinkedHashMap()
        Object out = ncube.getCell(coord, output)
        assertEquals(10, out)

        ncubeRuntime.clearCache(appId)

        fo = new FileOutputStream(base + File.separator + "Abc.groovy")
        code = "import ncube.grv.exp.NCubeGroovyExpression; class Abc extends NCubeGroovyExpression { def run() { return 20 } }"
        fo.write(code.bytes)
        fo.close()
        fo.flush()

        ncube = ncubeRuntime.getNCubeFromResource(appId, 'testReloadGroovyClass.json')
        out = ncube.getCell(coord, output)
        assertEquals(20, out)

        coord.put("state", "IN")
        String gcode = (String) ncube.getCell(coord, output)
        assertEquals(code, gcode)
    }

    @Test
    void testGroovyTwoMethods()
    {
        NCube ncube = new NCube("GroovyCube")
        Axis axis = new Axis("method", AxisType.DISCRETE, AxisValueType.STRING, false)
        axis.addColumn("doIt")
        axis.addColumn("bar")
        axis.addColumn("baz")
        ncube.addAxis(axis)

        def coord = [:]
        coord.put("method", "doIt")
        coord.put("age", 25)
        ncube.setCell(new GroovyMethod(
                "package ncube.grv.method; class JunkTwo extends NCubeGroovyController " +
                        "{\n" +
                        "def doIt() {\n" +
                        " int x = input.age * 10;" +
                        " jump(x)" +
                        "}\n" +
                        "int jump(int x) { x * 2; }" +
                        "}", null, false), coord)

        def output = [:]
        coord.put("method", "doIt")
        coord.put("age", 25)
        long start = System.currentTimeMillis()
        Object o = null
        for (int i = 0; i < 100000; i++)
        {
            o = ncube.getCell(coord, output)
        }
        long stop = System.currentTimeMillis()
        println("execute GroovyMethod 100,000 times = " + (stop - start))
        assertEquals(o, 500)
    }

    @Test
    void testGroovyTwoMethodsAndClass()
    {
        NCube ncube = new NCube("GroovyCube")
        Axis axis = new Axis("age", AxisType.DISCRETE, AxisValueType.LONG, false)
        ncube.addAxis(axis)
        axis.addColumn(25)
        axis.addColumn(35)
        axis.addColumn(45)

        def coord = [:]
        coord.put("age", 25)
        coord.put("method", "doIt")
        ncube.setCell(new GroovyMethod(
                "package ncube.grv.method; class JunkTwoClass extends NCubeGroovyController {" +
                        "def doIt()" +
                        "{" +
                        " int x = input['age'] * 10;" +
                        " return Fargo.freeze(jump(x))" +
                        "}\n" +
                        "int jump(int x) { x * 2; }\n" +
                        "\n" +
                        "static class Fargo {" +
                        "static int freeze(int d) {" +
                        "  -d" +
                        "}}}", null, false), coord)

        def output = [:]
        coord.put("age", 25)
        coord.put("method", "doIt")
        long start = System.currentTimeMillis()
        Object o = null
        for (int i = 0; i < 100000; i++)
        {
            o = ncube.getCell(coord, output)
            assertEquals(o, -500)
        }
        long stop = System.currentTimeMillis()
        println("execute GroovyMethod 100,000 times = " + (stop - start))
        assertEquals(o, -500)
    }

    private static NCube createTempDirClassPathCube()
    {
        NCube cpCube = new NCube<>("sys.classpath")

        Axis axis = new Axis("environment", AxisType.DISCRETE, AxisValueType.STRING, true)
        cpCube.addAxis(axis)

        String base = System.getProperty("java.io.tmpdir")
        cpCube.setCell(new GroovyExpression("new com.cedarsoftware.ncube.util.CdnClassLoader(['" + new File(base).toURI().toURL().toString() + "','${baseRemoteUrl}'])", null, false), new HashMap())
        return cpCube
    }
}