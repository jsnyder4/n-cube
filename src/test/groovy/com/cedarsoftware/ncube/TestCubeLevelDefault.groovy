package com.cedarsoftware.ncube

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
class TestCubeLevelDefault extends NCubeBaseTest
{
    @Test
    void testDefaultExpression()
    {
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'TestCubeLevelDefault.json')
        assert 1 == ncube.getCell([age:10, 'state':'OH'] as Map)
        assert 2 == ncube.getCell([age:10, 'state':'NJ'] as Map)
        assert 3 == ncube.getCell([age:10, 'state':'TX'] as Map)
        assert 20 == ncube.getCell([age:10, 'state':'AK'] as Map)
        assert 40 == ncube.getCell([age:20, 'state':'ME'] as Map)
    }

    @Test
    void testDefaultExpressionWithCaching()
    {
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'TestCubeLevelDefaultCache.json')
        assert 1 == ncube.getCell([age:10, 'state':'OH'] as Map)
        assert 2 == ncube.getCell([age:10, 'state':'NJ'] as Map)
        assert 3 == ncube.getCell([age:10, 'state':'TX'] as Map)
        assert 20 == ncube.getCell([age:10, 'state':'AK'] as Map)
        assert 20 == ncube.getCell([age:20, 'state':'ME'] as Map)
    }

    @Test
    void testDefaultExpressionSha1()
    {
        assertSha1Calculation('TestCubeLevelDefaultCache.json')
        assertSha1Calculation('TestCubeLevelDefault.json')
    }

    @Test
    void testGetCellDefault()
    {
        NCube cube = NCubeBuilder.discrete1DEmpty
        assert null == cube.getCell([state: 'OH'] as Map)
        assert null == cube.getCell([state: 'TX'] as Map)

        String m = 'money'
        cube.defaultCellValue = m
        assert m == cube.getCell([state: 'OH'] as Map)
        assert m == cube.getCell([state: 'TX'] as Map)
        assert m.is(cube.getCell([state: 'OH'] as Map))
        assert m.is(cube.getCell([state: 'TX'] as Map))

        String c = 'cash'
        assert m == cube.getCell([state: 'OH'] as Map, [:], c)
        assert m == cube.getCell([state: 'TX'] as Map, [:], c)
        assert m.is(cube.getCell([state: 'OH'] as Map, [:], c))
        assert m.is(cube.getCell([state: 'TX'] as Map, [:], c))

        cube.defaultCellValue = null
        assert c == cube.getCell([state: 'OH'] as Map, [:], c)
        assert c == cube.getCell([state: 'TX'] as Map, [:], c)
        assert c.is(cube.getCell([state: 'OH'] as Map, [:], c))
        assert c.is(cube.getCell([state: 'TX'] as Map, [:], c))
    }

    private void assertSha1Calculation(String jsonFile)
    {
        String json = NCubeRuntime.getResourceAsString(jsonFile)

        NCube x = NCube.fromSimpleJson(json)
        String json2 = x.toFormattedJson()
        NCube y = NCube.fromSimpleJson((String)json2)
        assert x.sha1() == y.sha1()
    }
}
