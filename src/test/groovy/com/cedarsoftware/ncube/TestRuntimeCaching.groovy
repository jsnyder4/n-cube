package com.cedarsoftware.ncube

import com.cedarsoftware.util.ReflectionUtils
import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner

import java.lang.reflect.Method

/**
 * @author Josh Snyder (joshsnyder@gmail.com)
 *         John DeRegnaucourt (jdereg@gmail.com)
 *
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
@RunWith(SpringRunner.class)
@TestPropertySource(properties = ['ncube.allow.mutable.methods=false'])
@ContextConfiguration(classes = NCubeApplication.class, initializers = ConfigFileApplicationContextInitializer.class)
@ActiveProfiles(profiles = ['ncube-client'])
@CompileStatic
@Ignore // Undo to run this test by itself.  This test messes up the Spring App Context for the rests of the tests.
class TestRuntimeCaching
{
    @Test
    void testBar()
    {
        NCubeRuntimeClient runtime = NCubeAppContext.ncubeRuntime
        NCube cube1 = runtime.getNCubeFromResource(ApplicationID.testAppId, 'testCube1.json')
        NCube cube2 = runtime.getNCubeFromResource(ApplicationID.testAppId, 'testCube5.json')
        assert cube1 != cube2
        
        runtime.clearCache(ApplicationID.testAppId)
        runtime.addCube(cube1)
        cube1 = runtime.getCube(ApplicationID.testAppId, 'TestCube')    // cube 1

        Method method = NCubeRuntime.class.getDeclaredMethod('cacheCube', NCube.class, boolean.class)
        method.accessible = true
        NCube rogue = (NCube) method.invoke(runtime, cube2, false) // calling cacheCube(cube2, false) - but it should ignore this

        assert cube1 == rogue
    }
}
