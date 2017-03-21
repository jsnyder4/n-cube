package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Test

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
@Ignore
class TestRuntimeAPIs //extends NCubeCleanupBaseTest
{
    private static final ApplicationID BRANCH1 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'FOO')
    private static final ApplicationID BRANCH2 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'BAR')

    @Test
    void testGetMenu()
    {
        Map menu = call('getMenu', [BRANCH1]) as Map
        assert menu.title != null
    }
    
    @Test
    void testExecute()
    {
//        createCubeFromResource(BRANCH1, 'test.execute.json')
//        def result = call('execute', [BRANCH1, 'test.execute', 'plus', [value: 2.0d, term: 3.0d]])
//        println result
    }

    private Object call(String method, List args)
    {
//        NCubeRuntime runtime = mutableClient as NCubeRuntime
//        runtime.bean.call('ncubeController', method, args)
        return null // remove this line
    }
}
