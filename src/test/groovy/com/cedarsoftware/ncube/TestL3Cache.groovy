package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.util.CdnClassLoader
import groovy.transform.CompileStatic
import org.codehaus.groovy.control.CompilationFailedException
import org.junit.*
import org.springframework.test.context.TestPropertySource

import java.lang.reflect.Field

import static com.cedarsoftware.ncube.NCubeAppContext.getNcubeRuntime
import static org.junit.Assert.*

/**
 * @author Greg Morefield (morefigs@hotmail.com)
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
@TestPropertySource(properties="ncube.accepted.domains=org.apache.")
class TestL3Cache extends NCubeCleanupBaseTest
{
    private NCube cp
    private NCube proto

    private NCube testCube
    private File sourcesDir
    private File classesDir
    private File singleDir

    private static File targetDir
    private static String savedSourcesDir
    private static String savedClassesDir
    
    @BeforeClass
    static void init()
    {
        targetDir = new File ('target')
        assertTrue(targetDir.exists() && targetDir.directory)
        savedSourcesDir = GroovyBase.generatedSourcesDirectory
        savedClassesDir = CdnClassLoader.generatedClassesDirectory
    }

    @Before
    void setUp()
    {
        sourcesDir = new File("${targetDir.path}/TestL3Cache-sources")
        classesDir = new File("${targetDir.path}/TestL3Cache-classes")
        singleDir = new File ("${targetDir.path}/single-l3cache")

        reloadCubes()

        clearDirectory(sourcesDir)
        clearDirectory(classesDir)
        clearDirectory(singleDir)

        configureDirectories(sourcesDir.path,classesDir.path)
    }

    private void clearDirectory(File dir) {
        if (dir.exists()) {
            assertTrue('directory should be purged', dir.deleteDir())
        }
        assertFalse('directory should not already exist', dir.exists())
    }

    @After
    void tearDown()
    {
        GroovyBase.generatedSourcesDirectory = savedSourcesDir
        CdnClassLoader.generatedClassesDirectory = savedClassesDir
        super.teardown()
    }

    /**
     * Test verifies that the sources and classes directory will be created
     * with the appropriate *.groovy and *.class files
     */
    @Test
    void testCreateCache()
    {
        clearDirectory(sourcesDir)
        clearDirectory(classesDir)

        assertFalse(sourcesDir.exists())
        assertFalse(classesDir.exists())
        assertTrue(loadedClasses.empty)

        Map output = [:]
        testCube.getCell([name:'simple'],output)

        Class expClass = output.simple
        assertTrue(expClass.simpleName.startsWith('N_'))
        assertTrue(sourcesDir.exists())
        assertTrue(classesDir.exists())
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
    }

    /**
     * Test verifies that classes generated/loaded for cell expressions are cleared with the cache
     * and then are re-loaded from the L3 cache location
     */
    @Test
    void testClearCache()
    {
        // load the expression initially
        Map output = [:]
        testCube.getCell([name:'simple'],output)
        Class expClass = output.simple

        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        // clear the cache, but make sure l3 cache still exists
        reloadCubes()
        assertTrue(loadedClasses.empty)
        verifyClassFileExistence(expClass)

        // clear the source file in order to detect if a recompile occurs or class loaded from l3 cache
        clearDirectory(sourcesDir)

        // re-execute the cube and ensure class was reloaded
        output.clear()
        testCube.getCell([name:'simple'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        assertFalse(sourcesDir.exists())
    }

    /**
     * Tests that NCube compile() generates sources and classes for all expressions
     * that are defined as cells or meta properties
     */
    @Test
    void testCompile()
    {
        reloadCubes('sys.classpath.L3cache.json')

        CompileInfo compileInfo = testCube.compile()
        List<Map> exceptions = compileInfo.getExceptions()
        assertEquals(exceptions.toString(),1,exceptions.size())
        Map cellException = exceptions.first()
        assertEquals(cellException.toString(),'test.L3CacheTest',compileInfo.getCubeName())
        assertEquals(cellException.toString(),'cellException',cellException[CompileInfo.EXCEPTION_COORDINATES]['name'])
        Exception compileException = cellException[CompileInfo.EXCEPTION_INSTANCE] as Exception
        assertTrue(cellException.toString(),compileException instanceof CompilationFailedException)

        // exercise ncube in a variety of ways to invoke cells and meta properties
        Map output = [:]
        testCube.getCell([name:'simple'],output)
        testCube.getCell([useRule:true],output)
        testCube.extractMetaPropertyValue(testCube.getMetaProperty('metaTest'),[:],output)
        Axis nameAxis = testCube.getAxis('name')
        testCube.extractMetaPropertyValue(nameAxis.getMetaProperty('metaTest'),[:],output)
        testCube.extractMetaPropertyValue(nameAxis.findColumn('simple').getMetaProperty('metaTest'),[:],output)

        // validate sources/classes have been created for the expressions
        verifySourceAndClassFilesExistence(output.metaCube as Class)
        verifySourceAndClassFilesExistence(output.metaAxis as Class)
        verifySourceAndClassFilesExistence(output.metaColumn as Class)
        verifySourceAndClassFilesExistence(output.metaRule as Class)
        verifySourceAndClassFilesExistence(output.simple as Class)
    }

    /**
     * Test verifies that inner classes are supported
     */
    @Test
    void testExpressionWithInnerClass()
    {
        Map output = [:]
        testCube.getCell([name:'innerClass'],output)

        Class expClass = output.innerClass
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        String innerClassName = "${expClass.name}\$1"
        verifyFileExistence(sourcesDir,innerClassName,'groovy',false)
        verifyFileExistence(classesDir,innerClassName,'class',true)

        reloadCubes()
        assertTrue(loadedClasses.empty)
        verifyClassFileExistence(expClass)
        verifyFileExistence(classesDir,innerClassName,'class',true)

        output.clear()
        testCube.getCell([name:'innerClass'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceFileExistence(expClass,false)
    }


    /**
     * Test verifies that classes will be recompiled if cache files are invalid
     */
    @Test
    void testInvalidClassFile()
    {
        // invoke cell initially to determine name of class
        Map output = [:]
        testCube.getCell([name:'simple'],output)

        Class expClass = output.simple
        String simpleClassName = expClass.name
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        // reload and clear class file to force compilation attempt
        reloadCubes()
        File classFile = new File ("${classesDir.path}/${simpleClassName.replace('.',File.separator)}.class")
        classFile.delete()
        verifyFileExistence(classesDir,simpleClassName,'class',false)
        classFile.newWriter().withWriter { w -> w << 'bogus class file' }
        verifyFileExistence(classesDir,simpleClassName,'class',true)

        // re-access cell and verify compilation took place
        output.clear()
        testCube.getCell([name:'simple'],output)
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
    }

    /**
     * Test verifies that classes will be recompiled if cache files are invalid
     */
    @Test
    @Ignore('Need to determine if and how this should be handled as LinkageError is thrown at runtime (invoking run)')
    void testMissingInnerClass()
    {
        Map output = [:]
        testCube.getCell([name:'innerClass'],output)

        Class expClass = output.innerClass
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        String innerClassName = "${expClass.name}\$1"
        verifyFileExistence(sourcesDir,innerClassName,'groovy',false)
        verifyFileExistence(classesDir,innerClassName,'class',true)

        // remove the inner class to cause a class load failure
        File innerClassFile = new File ("${classesDir.path}/${innerClassName.replace('.',File.separator)}.class")
        innerClassFile.delete()

        reloadCubes()
        assertTrue(loadedClasses.empty)
        verifyClassFileExistence(expClass)
        verifyFileExistence(classesDir,innerClassName,'class',false)

        output.clear()
        testCube.getCell([name:'innerClass'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceFileExistence(expClass,true)
        verifyFileExistence(classesDir,innerClassName,'class',true)
    }

    /**
     * Test verifies that custom classes are supported
     */
    @Test
    void testExpressionWithCustomClass()
    {
        Map output = [:]
        testCube.getCell([name:'customClass'],output)

        Class expClass = output.customClass
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        reloadCubes()
        assertTrue(loadedClasses.empty)
        verifyClassFileExistence(expClass)

        output.clear()
        testCube.getCell([name:'customClass'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceFileExistence(expClass,true)
    }

    /**
     * Test verifies that custom classes with matching names will load based on first one accessed and cached
     */
    @Test
    void testExpressionWithMatchingCustomClass()
    {
        // load customClass before customMatchingClass
        Map output = [:]
        testCube.getCell([name:'customClass'],output)

        Class expClass = output.customClass
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        // verify customMatchingClass matches customClass
        output.clear()
        testCube.getCell([name:'customMatchingClass'],output)
        assertFalse(output.containsKey('customMatchingClass'))  // won't find second implementation
        assertTrue(output.containsKey('customClass')) // still find first implementation

        reloadCubes()
        assertTrue(loadedClasses.empty)
        verifyClassFileExistence(expClass)

        // load customMatchingClass before customClass will NOT load cached customClass
        output.clear()
        testCube.getCell([name:'customMatchingClass'],output)
        assertTrue(output.containsKey('customMatchingClass')) // second still not found
        assertFalse(output.containsKey('customClass')) // first still returns
    }

    /**
     * Test verifies that custom classes with custom package are supported
     */
    @Test
    void testExpressionWithCustomClassAndPackage()
    {
        Map output = [:]
        testCube.getCell([name:'customPackage'],output)

        Class expClass = output.customPackage
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        reloadCubes()
        assertTrue(loadedClasses.empty)
        verifyClassFileExistence(expClass)

        output.clear()
        testCube.getCell([name:'customPackage'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceFileExistence(expClass,true)
    }

    /**
     * Test verifies that GroovyMethods are supported using custom classes
     */
    @Test
    void testMethod()
    {
        Map output = [:]
        Map input = [method: 'method']
        testCube.getCell(input,output)

        Class methodClass = output.methodClass
        assertEquals(input.method,output.methodName)
        verifySourceAndClassFilesExistence(methodClass)
        assertEquals(methodClass.name,findLoadedClass(methodClass).name)

        reloadCubes()
        assertTrue(loadedClasses.empty)
        verifyClassFileExistence(methodClass)

        output.clear()
        testCube.getCell(input,output)
        assertEquals(methodClass.name,findLoadedClass(methodClass).name)
        verifySourceFileExistence(methodClass,true)
    }

    /**
     * Test verifies that cells using Groovy's grab are handled
     */
    @Test
    void testExpressionWithGrab()
    {
        Map output = [:]
        testCube.getCell([name:'grab'],output)

        Class expClass = output.grab
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        reloadCubes()
        assertTrue(loadedClasses.empty)
        verifyClassFileExistence(expClass)

        output.clear()
        testCube.getCell([name:'grab'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceFileExistence(expClass,true)    // verify recompile occurred
    }

    /**
     * Test verifies that GroovyMethods are supported using custom package definitions
     */
    @Test
    void testMethodWithCustomPackage()
    {
        Map output = [:]
        Map input = [method: 'packageMethod']
        testCube.getCell(input,output)

        Class methodClass = output.packageMethodClass
        assertEquals(input.method,output.packageMethodName)
        verifySourceAndClassFilesExistence(methodClass)
        assertEquals(methodClass.name,findLoadedClass(methodClass).name)

        reloadCubes()
        assertTrue(loadedClasses.empty)
        verifyClassFileExistence(methodClass)

        output.clear()
        testCube.getCell(input,output)
        assertEquals(methodClass.name,findLoadedClass(methodClass).name)
        verifySourceFileExistence(methodClass,true)
    }

    /**
     * Test verifies that URLs to classes available from the classpath are not cached, but still supported
     */
    @Test
    void testUrlToClass()
    {
        Map output = [:]
        testCube.getCell([name:'urlToClass'],output)

        Class expClass = output.urlToClass
        verifySourceAndClassFilesExistence(expClass,false)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        reloadCubes()
        assertTrue(loadedClasses.empty)

        output.clear()
        testCube.getCell([name:'urlToClass'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceAndClassFilesExistence(expClass,false)
    }

    /**
     * Test verifies that URLs to statement blocks available from the classpath are not cached, but still supported
     */
    @Test
    void testUrlToStatementBlock()
    {
        Map output = [:]
        testCube.getCell([name:'urlToBlock'],output)

        Class expClass = output.urlToBlock
        verifySourceAndClassFilesExistence(expClass,false)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        reloadCubes()
        assertTrue(loadedClasses.empty)

        output.clear()
        testCube.getCell([name:'urlToBlock'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceAndClassFilesExistence(expClass,false)
    }

    /**
     * Test verifies that URLs to external classes are not cached, but still supported
     */
    @Test
    void testUrlToRemoteClass()
    {
        reloadCubes('sys.classpath.L3cache.json')
        Map output = [:]
        testCube.getCell([name:'urlToRemoteClass'],output)

        Class expClass = output.urlToRemoteClass
        verifySourceAndClassFilesExistence(expClass,false)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        reloadCubes('sys.classpath.L3cache.json')
        assertTrue(loadedClasses.empty)

        output.clear()
        testCube.getCell([name:'urlToRemoteClass'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceAndClassFilesExistence(expClass,false)
    }

    /**
     * Test verifies that URLs to external statement blocks are not cached, but still supported
     */
    @Test
    void testUrlToRemoteStatementBlock()
    {
        reloadCubes('sys.classpath.L3cache.json')
        Map output = [:]
        testCube.getCell([name:'urlToRemoteBlock'],output)

        Class expClass = output.urlToRemoteBlock
        verifySourceAndClassFilesExistence(expClass,false)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        reloadCubes('sys.classpath.L3cache.json')
        assertTrue(loadedClasses.empty)

        output.clear()
        testCube.getCell([name:'urlToRemoteBlock'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceAndClassFilesExistence(expClass,false)
    }

    /**
     * Test verifies that URLs to external classes, with shortcuts, are not cached, but still supported
     */
    @Test
    void testUrlToRemoteClassWithShortcuts()
    {
        reloadCubes('sys.classpath.L3cache.json')
        Map output = [:]
        testCube.getCell([name:'urlToShortcutClass'],output)

        Class expClass = output.urlToShortcutClass
        verifySourceAndClassFilesExistence(expClass,false)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        reloadCubes('sys.classpath.L3cache.json')
        assertTrue(loadedClasses.empty)

        output.clear()
        testCube.getCell([name:'urlToShortcutClass'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceAndClassFilesExistence(expClass,false)
    }

    /**
     * Test verifies that URLs to external statement blocks, with shortcuts, are not cached, but still supported
     */
    @Test
    void testUrlToRemoteStatementBlockWithShortcuts()
    {
        reloadCubes('sys.classpath.L3cache.json')
        Map output = [:]
        testCube.getCell([name:'urlToShortcutBlock'],output)

        Class expClass = output.urlToShortcutBlock
        verifySourceAndClassFilesExistence(expClass,false)
        assertEquals(expClass.name,findLoadedClass(expClass).name)

        reloadCubes('sys.classpath.L3cache.json')
        assertTrue(loadedClasses.empty)

        output.clear()
        testCube.getCell([name:'urlToShortcutBlock'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceAndClassFilesExistence(expClass,false)
    }

    /**
     * Test verifies that sources and classes can be pointed to the same directory
     */
    @Test
    void testUsingSameDirectory()
    {
        sourcesDir = classesDir = singleDir
        configureDirectories(sourcesDir.path,classesDir.path)

        assertEquals(sourcesDir.path,GroovyBase.generatedSourcesDirectory)
        assertEquals(GroovyBase.generatedSourcesDirectory,CdnClassLoader.generatedClassesDirectory)
        assertTrue(loadedClasses.empty)

        Map output = [:]
        testCube.getCell([name:'simple'],output)

        // validate class loaded, but no cache directories created
        Class expClass = output.simple
        verifySourceAndClassFilesExistence(expClass)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
    }

    /**
     * Test verifies that invalid sources and classes directories are ignored
     */
    @Test
    void testInvalidDirectoryParameters()
    {
        File sourceFile = new File ("${targetDir}/sources.txt")
        sourceFile.write('source parameter that is not a directory')
        File classesFile = new File ("${targetDir}/classes.txt")
        classesFile.write('class parameter that is not a directory')

        GroovyBase.generatedSourcesDirectory = sourceFile.path
        CdnClassLoader.generatedClassesDirectory = classesFile.path

        assertTrue(sourceFile.exists())
        assertTrue(classesFile.exists())

        // invoke a cell to force compilation
        Map output = [:]
        testCube.getCell([name: 'simple'],output)

        // validate class loaded and ensure source/class directories not configured
        // but that the class was still cached
        Class expClass = output.simple
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        assertEquals('',GroovyBase.generatedSourcesDirectory)
        assertEquals('',CdnClassLoader.generatedClassesDirectory)
    }

    /**
     * Test verifies that caching is not enabled if no directories are specified
     */
    @Test
    void testNoParameters()
    {
        GroovyBase.generatedSourcesDirectory = null
        CdnClassLoader.generatedClassesDirectory = null

        Map output = [:]
        testCube.getCell([name:'simple'],output)

        // validate class loaded, but no cache directories created
        Class expClass = output.simple
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        assertEquals('',GroovyBase.generatedSourcesDirectory)
        assertEquals('',CdnClassLoader.generatedClassesDirectory)
    }

    @Test
    void testSysPrototypeChange()
    {
        Map output = [:]
        testCube.getCell([name:'simple'],output)

        Class origClass = output.simple
        assertTrue(sourcesDir.exists())
        assertTrue(classesDir.exists())
        verifySourceAndClassFilesExistence(origClass)
        assertEquals(origClass.name,findLoadedClass(origClass).name)

        reloadCubes()
        loadTestCube(sysPrototypeDef.bytes)

        output.clear()
        testCube.getCell([name:'simple'],output)

        Class newClass = output.simple
        assertNotEquals(origClass.name,newClass.name)
    }

    @Test
    void testSwapCustomClass()
    {
        NCube ncube = new NCube('simple')
        ncube.applicationID = ApplicationID.testAppId
        Axis axis = new Axis('state', AxisType.DISCRETE, AxisValueType.CISTRING, true)
        ncube.addAxis(axis)
        ncubeRuntime.addCube(ncube)

        String class1 = "class Foo extends ncube.grv.exp.NCubeGroovyExpression { def run() { output.hello = true } }"
        String class2 = "class Foo extends ncube.grv.exp.NCubeGroovyExpression { def run() { output.goodbye = true } }"

        GroovyExpression exp1 = new GroovyExpression(class1, null,false)
        GroovyExpression exp2 = new GroovyExpression(class2, null,false)

        Map output = [:]
        ncube.setCell(exp1, [:])
        ncube.getCell([:], output)
        assert output.hello == true

        output.clear()
        ncubeRuntime.clearCache(ApplicationID.testAppId, ['simple'])
        ncubeRuntime.addCube(ncube)
        ncube.setCell(exp2, [:])
        ncube.getCell([:], output)
        assert output.hello == true

        ncubeRuntime.clearCache(ApplicationID.testAppId)
        ncubeRuntime.addCube(ncube)
        ncube.getCell([:], output)
        assert output.goodbye == true
    }

    @Test
    void testSwapCustomClass2()
    {
        NCube ncube = new NCube('simple')
        ncube.applicationID = ApplicationID.testAppId
        Axis axis = new Axis('state', AxisType.DISCRETE, AxisValueType.CISTRING, true)
        ncube.addAxis(axis)
        ncubeRuntime.addCube(ncube)

        String class1 = "println 'hello' ; output.hello = true"
        String class2 = "println 'goodbye' ; output.goodbye = true"

        GroovyExpression exp1 = new GroovyExpression(class1, null,false)
        GroovyExpression exp2 = new GroovyExpression(class2, null,false)

        Map output = [:]
        ncube.setCell(exp1, [:])
        ncube.getCell([:], output)
        assert output.hello == true

        output.clear()
        ncubeRuntime.clearCache(ApplicationID.testAppId, ['simple'])
        ncube.setCell(exp2, [:])
        ncube.getCell([:], output)
        assert output.goodbye == true
    }

    private void configureDirectories(srcDirPath, clsDirPath)
    {
        GroovyBase.generatedSourcesDirectory = srcDirPath
        CdnClassLoader.generatedClassesDirectory = clsDirPath

        assertEquals(srcDirPath,GroovyBase.generatedSourcesDirectory)
        assertEquals(clsDirPath,CdnClassLoader.generatedClassesDirectory)
    }

    private void reloadCubes(String sysClassPath = 'sys.classpath.tests.json') {
        ncubeRuntime.clearCache(ApplicationID.testAppId)

        cp = createRuntimeCubeFromResource(ApplicationID.testAppId,sysClassPath)

        proto = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId,'sys.prototype.json')
        ncubeRuntime.addCube(proto)

        testCube = loadTestCube(L3CacheCubeDef.bytes)

        clearDirectory(sourcesDir)
    }

    private NCube loadTestCube(byte [] bytes)
    {
        NCube testCube = NCube.createCubeFromStream(new ByteArrayInputStream(bytes))
        assertNotNull(testCube)
        ncubeRuntime.addCube(testCube)

        return testCube
    }

    private void verifySourceAndClassFilesExistence(Class clazz, boolean exists=true) {
        verifySourceFileExistence(clazz,exists)
        verifyClassFileExistence(clazz,exists)
    }

    private void verifySourceFileExistence(Class clazz, boolean exists=true) {
        verifyFileExistence(sourcesDir,clazz.name,'groovy',exists)
    }

    private void verifyClassFileExistence(Class clazz, boolean exists=true) {
        verifyFileExistence(classesDir,clazz.name,'class',exists)
    }

    private static void verifyFileExistence(File dir, String className, String extension, boolean exists=true) {
        String fileName = className.contains('.') ? "${className.replace('.',File.separator)}" : className

        File classFile = new File ("${dir.path}/${fileName}.${extension}")
        assertEquals("file=${classFile.path} should ${exists?'':'not '}exist",exists,classFile.exists())
    }

    private Class findLoadedClass(Class clazz)
    {
        return loadedClasses.find { it.name == clazz.name}
    }

    private List<Class> getLoadedClasses()
    {
        ClassLoader classLoader = cp.getCell([:]) as GroovyClassLoader
        Field classesField = ClassLoader.class.getDeclaredField('classes')
        classesField.accessible = true

        List<Class> classes = classesField.get(classLoader) as List
        while (classLoader != null)
        {
            if (!classLoader.toString().contains('Launcher'))
            {
                if (classLoader instanceof GroovyClassLoader)
                {
                    classes.addAll((classLoader as GroovyClassLoader).loadedClasses)
                }
                classes.addAll(classesField.get(classLoader) as List)
            }
            classLoader=classLoader.parent
        }

        return classes
    }


    static String L3CacheCubeDef='''{
  "ncube":"test.L3CacheTest",
  "metaTest":{
    "type":"exp",
    "value":"output.metaCube = this.class"
  },
  "axes":[
    {
      "id":1,
      "name":"name",
      "hasDefault":true,
      "metaTest":{
        "type":"exp",
        "value":"output.metaAxis = this.class"
      },
      "type":"DISCRETE",
      "valueType":"STRING",
      "preferredOrder":0,
      "fireAll":true,
      "columns":[
        {
          "id":1000329189157,
          "type":"string",
          "metaTest":{
            "type":"exp",
            "value":"output.metaColumn = this.class"
          },
          "value":"simple"
        },
        {
          "id":1001649720956,
          "type":"string",
          "value":"simple-clone"
        },
        {
          "id":"innerClass"
        },
        {
          "id":"customClass"
        },
        {
          "id":"customMatchingClass"
        },
        {
          "id":"customPackage"
        },
        {
          "id":"cellException"
        },
        {
          "id":"grab"
        },
        {
          "id":"urlToClass"
        },
        {
          "id":"urlToBlock"
        },
        {
          "id":"urlToRemoteClass"
        },
        {
          "id":"urlToRemoteBlock"
        },
        {
          "id":"urlToShortcutClass"
        },
        {
          "id":"urlToShortcutBlock"
        }
      ]
    },
    {
      "id":2,
      "name":"type",
      "hasDefault":true,
      "type":"RULE",
      "valueType":"EXPRESSION",
      "preferredOrder":1,
      "fireAll":true,
      "columns":[
        {
          "id":2000714454923,
          "type":"exp",
          "name":"useRule",
          "value":"if (input.useRule) {output.metaRule = this.class}\nreturn input.useRule"
        }
      ]
    },
    {
      "id":3,
      "name":"method",
      "hasDefault":true,
      "type":"DISCRETE",
      "valueType":"STRING",
      "preferredOrder":1,
      "fireAll":true,
      "columns":[
        {
          "id":"method",
          "id":"packageMethod"
        }
      ]
    }
  ],
  "cells":[
    {
      "id":[
        1000329189157
      ],
      "type":"exp",
      "value":"output.simple = this.class"
    },
    {
      "id":[
        1001649720956
      ],
      "type":"exp",
      "value":"output.simple = this.class"
    },
    {
      "id":[
        "innerClass"
      ],
      "type":"exp",
      "value":"Comparator c = new Comparator() { int compare(Object o1, Object o2) { return 0 } }
output.innerClass = this.class"
    },
    {
      "id":[
        "customClass"
      ],
      "type":"exp",
      "value":"package ncube.grv.exp
class CustomClass extends NCubeGroovyExpression {
 def run() {
  output.customClass = this.class
 }
}"
    },
    {
      "id":[
        "customMatchingClass"
      ],
      "type":"exp",
      "value":"package ncube.grv.exp
class CustomClass extends NCubeGroovyExpression {
 def run() {
  output.customMatchingClass = this.class
 }
}"
    },
    {
      "id":[
        "customPackage"
      ],
      "type":"exp",
      "value":"package ncube.test
import ncube.grv.exp.*
class CustomPackage extends NCubeGroovyExpression
{
def run() {
output.customPackage = this.class
}
}"
    },
    {
      "id":[
        "method"
      ],
      "type":"method",
      "value":"package ncube.grv.exp
import ncube.grv.method.NCubeGroovyController
class MethodController extends NCubeGroovyController {
 def run() {
  output.methodClass = this.class
  output.methodName = 'run'
 }
 def method() {
  output.methodClass = this.class
  output.methodName = 'method'
 }
}"
    },
    {
      "id":[
        "packageMethod"
      ],
      "type":"method",
      "value":"package ncube.test
import ncube.grv.method.NCubeGroovyController
class PackageMethodController extends NCubeGroovyController {
 def run() {
  output.packageMethodClass = this.class
  output.packageMethodName = 'run'
 }
 def packageMethod() {
  output.packageMethodClass = this.class
  output.packageMethodName = 'packageMethod'
 }
}"
    },
    {
      "id":[
        "cellException"
      ],
      "type":"exp",
      "value":"package test"
    },
    {
      "id":[
        "grab"
      ],
      "type":"exp",
      "value":"import org.apache.commons.collections.primitives.*
@Grab(group='commons-primitives', module='commons-primitives', version='1.0')

Object ints = new ArrayIntList()
ints.add(42)
output.grab = this.class"
    },
    {
      "id":["urlToClass"],
      "type":"exp",
      "url":"files/ncube/UrlToClass.groovy"
    },
    {
      "id":["urlToBlock"],
      "type":"exp",
      "url":"files/ncube/UrlToBlock.groovy"
    },
    {
      "id":["urlToRemoteClass"],
      "type":"exp",
      "url":"files/ncube/UrlToRemoteClass.groovy"
    },
    {
      "id":["urlToRemoteBlock"],
      "type":"exp",
      "url":"files/ncube/UrlToRemoteBlock.groovy"
    },
    {
      "id":["urlToShortcutClass"],
      "type":"exp",
      "url":"files/ncube/UrlToShortcutClass.groovy"
    },
    {
      "id":["urlToShortcutBlock"],
      "type":"exp",
      "url":"files/ncube/UrlToShortcutBlock.groovy"
    }
  ]
}'''

    static String sysPrototypeDef = '''{
  "ncube": "sys.prototype",
  "axes": [
    {
      "name": "sys.property",
      "hasDefault": false,
      "type": "DISCRETE",
      "valueType": "STRING",
      "preferredOrder": 1,
      "fireAll": true,
      "columns": [
        {
          "id": "imports",
          "type": "string",
          "value": "exp.imports"
        },
        {
          "id": "class",
          "type": "string",
          "value": "exp.class"
        }
      ]
    }
  ],
  "cells": [
    {
      "id":["imports"],
      "type":"string",
      "value":"javax.net.ssl.HostnameVerifier"
    }
  ]
}'''

}
