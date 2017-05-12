package com.cedarsoftware.ncube.formatters

import com.cedarsoftware.ncube.*
import com.cedarsoftware.util.CaseInsensitiveMap
import groovy.transform.CompileStatic
import org.junit.Test

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
class TestTestResultsFormatter extends NCubeBaseTest
{
    @Test
    void testResultsFromNCube()
    {
        NCube<String> ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'idNoValue.json')
        def coord = [age:18, state:'OH']
        def output = [:]
        ncube.getCell(coord, output)
        String s = new TestResultsFormatter(output).format()
        assert s.contains('18 OH')
        assert s.contains('No assertion failures')
        assert s.contains('No output')
    }

    @Test
    void testResultsWithOutputAndError()
    {
        NCube<String> ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'idNoValue.json')
        def coord = [age:18, state:'OH']
        def output = ['foo.age':'56', 'foo.name':'John']
        ncube.getCell coord, output

        Set<String> assertionFailures = new HashSet<>()
        assertionFailures.add '[some assertion happened]'

        RuleInfo ruleInfo = output.get(NCube.RULE_EXEC_INFO) as RuleInfo
        ruleInfo.setAssertionFailures(assertionFailures)

        String s = new TestResultsFormatter(output).format()
        assert s.contains('18 OH')
        assert s.contains('[some assertion happened]')
        assert s.contains('foo.name = John')
        assert s.contains('foo.age = 56')
        assert s.contains('return = 18 OH')
    }

    @Test
    void testOutput()
    {
        Map<String, CellInfo> coord = new CaseInsensitiveMap<>()
        CellInfo[] expected = new CellInfo[3]
        expected[0] = new CellInfo(3.0d)
        expected[1] = new CellInfo(3.0f)
        expected[2] = new CellInfo(new GroovyExpression('help me', null, false))
        NCubeTest test = new NCubeTest('testName', coord, expected)
        assert test.name == 'testName'
    }
}
