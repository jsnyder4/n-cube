package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime

/**
 * NCube RuleEngine Tests
 *
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
class TestRuleEngineIntegration extends NCubeCleanupBaseTest
{
    @Test
    void testNCubeGroovyExpressionAPIs()
    {
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'expressionTests.json')
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'months.json')

        Map input = ['Age': 10]
        Map output = [:]
        ncube.getCell input, output
        assert output.isAxis
        assert output.isColumn
        assert output.isRange
        assert (output.colId as long) > 0
        assert output.containsKey(0)
        assert 'sys.classpath' == output[0]
    }
}
