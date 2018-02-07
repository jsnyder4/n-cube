package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import ncube.grv.exp.NCubeGroovyExpression
import org.junit.Assert
import org.junit.Test

import java.lang.reflect.Constructor
import java.lang.reflect.Modifier

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static org.junit.Assert.fail
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
class TestGroovyExpression extends NCubeBaseTest
{
    @Test
    void testDefaultConstructorIsPrivateForSerialization()
    {
        Class c = GroovyExpression.class
        Constructor<GroovyExpression> con = c.getDeclaredConstructor()
        Assert.assertEquals Modifier.PRIVATE, con.modifiers & Modifier.PRIVATE
        con.accessible = true
        Assert.assertNotNull con.newInstance()
    }

    @Test
    void testCompilerErrorOutput()
    {
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'GroovyExpCompileError.json')
        Map coord = [state: 'OH'] as Map
        Object x = ncube.getCell(coord)
        assert 'Hello, Ohio' == x
        coord.state = 'TX'
        try
        {
            ncube.getCell(coord)
            fail();
        }
        catch (RuntimeException e)
        {
            String msg = e.cause.message
            assert msg.toLowerCase().contains('no such property')
            assert msg.toLowerCase().contains('hi8')
        }
    }

    @Test
    void testExpThatCallsGetAxis()
    {
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'test.CellGetAxis.json')
        Map coord = [name: 'OH'] as Map
        Axis x = ncube.getCell(coord)
        assert x instanceof Axis

        coord = [name: 'IN'] as Map
        x = ncube.getCell(coord)
        assert x instanceof Axis
    }

    @Test
    void testRegexSubstitutions()
    {
        NCube ncube = new NCube('test')
        ncube.applicationID = ApplicationID.testAppId
        ncubeRuntime.addCube(ncube)
        Axis axis = new Axis('day', AxisType.DISCRETE, AxisValueType.STRING, false)
        axis.addColumn('mon')
        axis.addColumn('tue')
        axis.addColumn('wed')
        axis.addColumn('thu')
        axis.addColumn('fri')
        axis.addColumn('sat')
        axis.addColumn('sun')

        ncube.addAxis(axis)
        ncube.applicationID = ApplicationID.testAppId

        ncube.setCell(1, [day: 'mon'] as Map)
        ncube.setCell(2, [day: 'tue'] as Map)
        ncube.setCell(3, [day: 'wed'] as Map)
        ncube.setCell(4, [day: 'thu'] as Map)
        ncube.setCell(5, [day: 'fri'] as Map)
        ncube.setCell(6, [day: 'sat'] as Map)
        ncube.setCell(7, [day: 'sun'] as Map)

        parse(ncube, '''
int ret = @[day:'mon']
return ret
''', 1)

        parse(ncube, '''
int ret = @[day:'mon'];
return ret
''', 1)

        parse(ncube, '''
int ret = $[day:'tue']
return ret
''', 2)

        parse(ncube, '''
int ret = $[day:'tue'];
return ret
''', 2)

        parse(ncube, '''
int ret = @test[day:'wed']
return ret
''', 3)

        parse(ncube, '''
int ret = @test[day:'wed'];
return ret
''', 3)

        parse(ncube, '''
int ret = $test[day:'thu']
return ret
''', 4)

        parse(ncube, '''
int ret = $test[day:'thu'];
return ret
''', 4)

        // Map variable passed in as coordinate
        parse(ncube, '''
Map inp = [day:'mon']
int ret = @(inp)
return ret
''', 1)

        parse(ncube, '''
Map inp = [day:'mon']
int ret = @(inp);
return ret
''', 1)

        parse(ncube, '''
Map inp = [day:'tue']
int ret = $(inp)
return ret
''', 2)

        parse(ncube, '''
Map inp = [day:'tue']
int ret = $(inp);
return ret
''', 2)

        parse(ncube, '''
Map inp = [day:'wed']
int ret = @test(inp)
return ret
''', 3)

        parse(ncube, '''
Map inp = [day:'wed']
int ret = @test(inp);
return ret
''', 3)

        parse(ncube, '''
Map inp = [day:'thu']
int ret = $test(inp)
return ret
''', 4)

        parse(ncube, '''
Map inp = [day:'thu']
int ret = $test(inp);
return ret
''', 4)
    }

    private void parse(NCube ncube, String cmd, int val)
    {
        GroovyExpression exp = new GroovyExpression(cmd, null, false)
        Map ctx = [input: [:], output: [:], ncube: ncube]
        assert exp.execute(ctx) == val
    }

    @Test
    void testCachedExpressionClassIsGarbageCollected()
    {
        NCube ncube = new NCube('test')
        ncube.applicationID = ApplicationID.testAppId
        ncubeRuntime.addCube(ncube)
        Axis axis = new Axis('day', AxisType.DISCRETE, AxisValueType.STRING, false)
        axis.addColumn('mon')
        axis.addColumn('tue')

        ncube.addAxis(axis)
        ncube.applicationID = ApplicationID.testAppId

        ncube.setCell(new GroovyExpression("return 'hello'", null, false), [day:'mon'] as Map)
        assert 'hello' == ncube.getCell([day:'mon'] as Map)
        ncube.setCell(new GroovyExpression("return 'world'", null, true), [day:'tue'] as Map)
        assert 'world' == ncube.getCell([day:'tue'] as Map)

        assert 'hello' == ncube.getCell([day:'mon'] as Map)
        assert 'world' == ncube.getCell([day:'tue'] as Map)

        GroovyExpression exp = (GroovyExpression) ncube.getCellByIdNoExecute(ncube.getCoordinateKey([day:'mon'] as Map))
        assert "return 'hello'" == exp.cmd
        assert !exp.cacheable
        assert exp.runnableCode != null
        assert NCubeGroovyExpression.class.isAssignableFrom(exp.runnableCode)

        GroovyExpression exp1 = (GroovyExpression) ncube.getCellNoExecute([day:'mon'] as Map)
        assert exp1.equals(exp)

        exp = (GroovyExpression) ncube.getCellByIdNoExecute(ncube.getCoordinateKey([day:'tue'] as Map))
        assert exp.cmd == "return 'world'"
        assert exp.cacheable

        exp1 = (GroovyExpression) ncube.getCellNoExecute([day:'tue'] as Map)
        assert exp1.equals(exp)
    }

    @Test
    void testSysProtoErrorHandling()
    {
        assert null == GroovyExpression.getPrototypeExpClass(null, null)
    }

    @Test
    void testSysProtoErrorHandling3()
    {
        GroovyExpression.addPrototypeExpImports(null,  null)
    }

    @Test
    void testGetScopeKeys()
    {
        GroovyExpression exp = new GroovyExpression('return input.age', null, false)
        Set<String> scopeKeys = new LinkedHashSet<>()
        exp.getScopeKeys(scopeKeys)
        assert scopeKeys.contains('age')
        assert !scopeKeys.contains('AGE')
    }
}