package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static org.junit.Assert.assertEquals
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
class TestGroovyMethodIntegration extends NCubeCleanupBaseTest
{
    @Test
    void testGroovyMethodClearCache()
    {
        ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'GroovyMethodCP', ApplicationID.DEFAULT_VERSION, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)

        NCube cpCube = createRuntimeCubeFromResource(appId, 'sys.classpath.cp1.json')
        mutableClient.createCube(cpCube)

        NCube cube = ncubeRuntime.getNCubeFromResource(appId, 'GroovyMethodClassPath1.json')
        mutableClient.createCube(cube)

        ncubeRuntime.clearCache(appId)
        cube = mutableClient.getCube(appId, 'GroovyMethodClassPath1')

        Object x = cube.getCell([method:'foo'])
        assertEquals 'foo', x

        x = cube.getCell([method:'foo2'])
        assertEquals 'foo2', x

        x = cube.getCell([method:'bar'])
        assertEquals 'Bar', x

        cpCube = createRuntimeCubeFromResource(appId, 'sys.classpath.cp2.json')
        mutableClient.updateCube(cpCube)

        ncubeRuntime.clearCache(appId)
        cube = mutableClient.getCube(appId, 'GroovyMethodClassPath1')

        x = cube.getCell([method:'foo'])
        assertEquals 'boo', x

        x = cube.getCell([method:'foo2'])
        assertEquals 'boo2', x

        x = cube.getCell([method:'bar'])
        assertEquals 'far', x
    }

    @Test
    void testGroovyMethodClearCacheExplicitly()
    {
        ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'GroovyMethodCP', ApplicationID.DEFAULT_VERSION, ReleaseStatus.SNAPSHOT.name(), ApplicationID.TEST_BRANCH)

        NCube cpCube = createRuntimeCubeFromResource(appId, 'sys.classpath.cp1.json')
        mutableClient.createCube(cpCube)

        NCube cube = ncubeRuntime.getNCubeFromResource(appId, 'GroovyMethodClassPath1.json')
        mutableClient.createCube(cube)

        ncubeRuntime.clearCache(appId)
        cube = mutableClient.getCube(appId, 'GroovyMethodClassPath1')

        Object x = cube.getCell([method:'foo'])
        assertEquals 'foo', x

        x = cube.getCell([method:'foo2'])
        assertEquals('foo2', x)

        x = cube.getCell([method:'bar'])
        assertEquals('Bar', x)

        cpCube = createRuntimeCubeFromResource(appId, 'sys.classpath.cp2.json')
        mutableClient.updateCube(cpCube)

        ncubeRuntime.clearCache(appId)
        cube = mutableClient.getCube(appId, 'GroovyMethodClassPath1')

        x = cube.getCell([method:'foo'])
        assertEquals 'boo', x

        x = cube.getCell([method:'foo2'])
        assertEquals 'boo2', x

        x = cube.getCell([method:'bar'])
        assertEquals('far', x)
    }
}