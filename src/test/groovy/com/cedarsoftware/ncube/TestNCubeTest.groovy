package com.cedarsoftware.ncube

import com.cedarsoftware.util.CaseInsensitiveMap
import org.junit.Test

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
class TestNCubeTest extends NCubeBaseTest
{
    @Test
    void testNCubeTest()
    {
        String name = 'test-1'
        Map<String, CellInfo> params = new CaseInsensitiveMap<>()
        params['int'] = new CellInfo('int', '5', false, false)
        params['double'] = new CellInfo('double', '5.75', false, false)
        params['key1'] = new CellInfo('string', 'foo', false, false)

        CellInfo[] info = new CellInfo[1]
        info[0] = new CellInfo('exp', "'bar'", false, false)

        NCubeTest test = new NCubeTest(name, params, info)

        CellInfo[] assertions = test.assertions
        assert 1 == assertions.length

        Map<String, CellInfo> coord = test.coord
        assert 3 == coord.size()

        def coord1 = test.coordWithValues
        assert 5 == coord1.int
        assertEquals(5.75, coord1.double as double, 0.00001d)
        assert 'foo' == coord1.key1

        final List<GroovyExpression> testAssertions = test.createAssertions()

        // unfortunately you have to pass in ncube to the execute.
        def map = [ncube:new NCube('hello'), input:[:],output:[:]]
        assert 'bar' == testAssertions[0].execute(map)
    }
}
