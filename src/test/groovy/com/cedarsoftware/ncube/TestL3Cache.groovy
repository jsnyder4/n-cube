package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.util.CdnClassLoader
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Ignore
import org.junit.Test
import org.slf4j.Logger
import org.slf4j.LoggerFactory

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
class TestL3Cache extends NCubeCleanupBaseTest
{
    private NCube cp
    private NCube proto

    private NCube testCube
    private File sourcesDir
    private File classesDir
    private File singleDir

    private static File targetDir
    private static String savedNcubeParams

    private static final Logger LOG = LoggerFactory.getLogger(TestL3Cache.class)

    @BeforeClass
    static void init()
    {
        targetDir = new File ('target')
        assertTrue(targetDir.exists() && targetDir.isDirectory())
        savedNcubeParams = System.getProperty('NCUBE_PARAMS')
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

        configureSysParams(sourcesDir.path,classesDir.path)
        GroovyBase.setGeneratedSourcesDirectory(null)
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
        if (savedNcubeParams) {
            System.setProperty('NCUBE_PARAMS',savedNcubeParams)
        }
        else {
            System.clearProperty('NCUBE_PARAMS')
        }
        GroovyBase.setGeneratedSourcesDirectory(null)
        super.teardown()
    }

    /**
     * Test verifies that the sources and classes directory will be created
     * with the appropriate *.groovy and *.class files
     */
    @Test
    void testCreateCache()
    {
        assertFalse(sourcesDir.exists())
        assertFalse(classesDir.exists())
        assertTrue(getLoadedClasses().isEmpty())

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
        assertTrue(getLoadedClasses().isEmpty())
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
        testCube.compile()

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
        assertTrue(getLoadedClasses().isEmpty())
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
        assertTrue(getLoadedClasses().isEmpty())
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
        assertTrue(getLoadedClasses().isEmpty())
        verifyClassFileExistence(expClass)

        output.clear()
        testCube.getCell([name:'customClass'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceFileExistence(expClass,false)
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
        assertTrue(getLoadedClasses().isEmpty())
        verifyClassFileExistence(expClass)

        // load customMatchingClass before customClass will load cached customClass still
        output.clear()
        testCube.getCell([name:'customMatchingClass'],output)
        assertFalse(output.containsKey('customMatchingClass')) // second still not found
        assertTrue(output.containsKey('customClass')) // first still returns
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
        assertTrue(getLoadedClasses().isEmpty())
        verifyClassFileExistence(expClass)

        output.clear()
        testCube.getCell([name:'customPackage'],output)
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        verifySourceFileExistence(expClass,false)
    }

    /**
     * Test verifies that sources and classes can be pointed to the same directory
     */
    @Test
    void testUsingSameDirectory()
    {
        sourcesDir = classesDir = singleDir
        configureSysParams(sourcesDir.path,classesDir.path)

        assertFalse(sourcesDir.exists())
        assertEquals(sourcesDir,classesDir)
        assertTrue(getLoadedClasses().isEmpty())

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
    void testInvalidParameters()
    {
        File sourceFile = new File ("${targetDir}/sources.txt")
        sourceFile.write('source parameter that is not a directory')
        File classesFile = new File ("${targetDir}/classes.txt")
        classesFile.write('class parameter that is not a directory')

        configureSysParams(sourceFile.path,classesFile.path)

        assertTrue(sourceFile.exists())
        assertTrue(classesFile.exists())

        // invoke a cell to force compilation
        Map output = [:]
        testCube.getCell([name: 'simple'],output)

        // validate class loaded and ensure source/class directories not configured
        Class expClass = output.simple
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        assertEquals('',GroovyBase.getGeneratedSourcesDirectory())
        assertEquals('',getLoaderGeneratedClassesDir())
    }

    /**
     * Test verifies that caching is not enabled if no directories are specified
     */
    @Test
    void testNoParameters()
    {
        System.clearProperty('NCUBE_PARAMS')
        ncubeRuntime.clearSysParams()

        Map output = [:]
        testCube.getCell([name:'simple'],output)

        // validate class loaded, but no cache directories created
        Class expClass = output.simple
        assertEquals(expClass.name,findLoadedClass(expClass).name)
        assertEquals('',GroovyBase.getGeneratedSourcesDirectory())
        assertEquals('',getLoaderGeneratedClassesDir())
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

    private String getLoaderGeneratedClassesDir()
    {
        ClassLoader cdnClassLoader = cp.getCell([:]) as CdnClassLoader
        return cdnClassLoader['generatedClassesDir']
    }

    private static void configureSysParams(String srcDirPath, String clsDirPath)
    {
        ncubeRuntime.clearSysParams()

        System.setProperty("NCUBE_PARAMS", """{"${NCUBE_PARAMS_GENERATED_SOURCES_DIR}":"${srcDirPath.replace('\\', '\\\\')}","${NCUBE_PARAMS_GENERATED_CLASSES_DIR}":"${clsDirPath.replace('\\', '\\\\')}"}""")
        assertEquals(srcDirPath,ncubeRuntime.systemParams[NCUBE_PARAMS_GENERATED_SOURCES_DIR])
        assertEquals(clsDirPath,ncubeRuntime.systemParams[NCUBE_PARAMS_GENERATED_CLASSES_DIR])
    }

    private void reloadCubes() {
        ncubeRuntime.clearCache()

        cp = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId,'sys.classpath.threading.json')
        ncubeRuntime.addCube(cp)

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

    private boolean verifySourceAndClassFilesExistence(Class clazz, boolean exists=true) {
        verifySourceFileExistence(clazz,exists)
        verifyClassFileExistence(clazz,exists)
    }

    private boolean verifySourceFileExistence(Class clazz, boolean exists=true) {
        verifyFileExistence(sourcesDir,clazz.name,'groovy',exists)
    }

    private boolean verifyClassFileExistence(Class clazz, boolean exists=true) {
        verifyFileExistence(classesDir,clazz.name,'class',exists)
    }

    private boolean verifyFileExistence(File dir, String className, String extension, boolean exists=true) {
        String fileName = className.contains('.') ? "${className.replace('.',File.separator)}" : "ncube/grv/exp/${className}"

        File classFile = new File ("${dir.path}/${fileName}.${extension}")
        assertEquals("file=${classFile.path} should ${exists?'':'not '}exist",exists,classFile.exists())
    }

    private Class findLoadedClass(Class clazz)
    {
        return getLoadedClasses().find { it.name == clazz.name}
    }

    private List<Class> getLoadedClasses()
    {
        GroovyClassLoader gcl = cp.getCell([:]) as GroovyClassLoader
        Field classesField = ClassLoader.class.getDeclaredField('classes')
        classesField.setAccessible(true)

        if (ncubeRuntime.getSystemParams()[NCUBE_PARAMS_GENERATED_SOURCES_DIR])
            return classesField.get(gcl) + classesField.get(gcl.parent)
        else
            return classesField.get(gcl)
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
          "id":"innerClass",
          "type":"string",
          "value":"innerClass"
        },
        {
          "id":"customClass",
          "type":"string",
          "value":"customClass"
        },
        {
          "id":"customMatchingClass",
          "type":"string",
          "value":"customMatchingClass"
        },
        {
          "id":"customPackage",
          "type":"string",
          "value":"customPackage"
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
