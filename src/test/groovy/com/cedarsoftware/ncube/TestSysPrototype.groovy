package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test

import java.awt.*

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime

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
class TestSysPrototype extends NCubeBaseTest
{
    @Test
    void testInheritedImports()
    {
        NCube prototype = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'sys.prototype.json')
        NCube protoTests = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'prototype.tests.json')
        Map coord = [state:'OH']
        assert protoTests.getCell(coord) == 2

        coord = [state:'ZZ']
        Point p = protoTests.getCell(coord)
        assert p.x == 10
        assert p.y == 20
    }

    @Test
    void testCustomBaseExpressionClass()
    {
        NCube prototype = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'sys.prototype.json')
        NCube protoTests = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'prototype.tests.json')
        Map coord = [state:'TX']
        assert protoTests.getCell(coord) == 70
    }
}
