package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestInputKeysUsed extends NCubeBaseTest
{
    @Test
    void testKeyTracking()
    {
        NCube ncube = NCubeBuilder.trackingTestCube
        Map input = [column: 'A', Age: 7]
        Map output = [:]
        def x = ncube.getCell(input, output)
        assert 'a1' == x
        RuleInfo ruleInfo = ncube.getRuleInfo(output)
        assert ruleInfo.getInputKeysUsed().size() == 2
        assert ruleInfo.getInputKeysUsed().contains('Column')
        assert ruleInfo.getInputKeysUsed().contains('Row')
    }

    @Test
    void testKeyTrackingWithContainsKeyAccess()
    {
        NCube ncube = NCubeBuilder.trackingTestCube
        Map input = [column: 'B']
        Map output = [:]
        ncube.getCell(input, output)
        RuleInfo ruleInfo = ncube.getRuleInfo(output)
        assert ruleInfo.getInputKeysUsed().size() == 3
        assert ruleInfo.getInputKeysUsed().contains('Row')
        assert ruleInfo.getInputKeysUsed().contains('Column')
        assert ruleInfo.getInputKeysUsed().contains('smokes')

        input = [column: 'B', Row: 99]
        output = [:]
        ncube.getCell(input, output)
        ruleInfo = ncube.getRuleInfo(output)
        assert ruleInfo.getInputKeysUsed().size() == 3
        assert ruleInfo.getInputKeysUsed().contains('Column')
        assert ruleInfo.getInputKeysUsed().contains('Row')
        assert ruleInfo.getInputKeysUsed().contains('smokes')

        input = [column: 'B', Row: 99, SMOKES: null]
        output = [rate: 0]
        ncube.getCell(input, output)
        ruleInfo = ncube.getRuleInfo(output)
        assert ruleInfo.getInputKeysUsed().size() == 3
        assert ruleInfo.getInputKeysUsed().contains('Column')
        assert ruleInfo.getInputKeysUsed().contains('Row')
        assert ruleInfo.getInputKeysUsed().contains('smokes')
    }

    @Test
    void testKeyTrackingInputAccessedInCode()
    {
        NCube ncube = NCubeBuilder.trackingTestCube
        Map input = [column: 'A', Row: 1, Age: 7]
        Map output = [:]
        ncube.getCell(input, output)
        RuleInfo ruleInfo = ncube.getRuleInfo(output)
        assert ruleInfo.getInputKeysUsed().size() == 4
        assert ruleInfo.getInputKeysUsed().contains('Column')
        assert ruleInfo.getInputKeysUsed().contains('Row')
        assert ruleInfo.getInputKeysUsed().contains('age')
        assert ruleInfo.getInputKeysUsed().contains('Weight')
    }

    @Test
    void testKeyTrackingAllInputProvided()
    {
        NCube ncube = NCubeBuilder.trackingTestCube
        Map input = [column: 'A', Row: 1, Age: 7, weight: 210]
        Map output = [:]
        ncube.getCell(input, output)
        RuleInfo ruleInfo = ncube.getRuleInfo(output)
        assert ruleInfo.getInputKeysUsed().size() == 4
        assert ruleInfo.getInputKeysUsed().contains('Column')
        assert ruleInfo.getInputKeysUsed().contains('Row')
        assert ruleInfo.getInputKeysUsed().contains('age')
        assert ruleInfo.getInputKeysUsed().contains('Weight')
    }

    @Test
    void testKeyTrackingNested()
    {
        NCube ncube = NCubeBuilder.trackingTestCube
        NCube ncube2 = NCubeBuilder.trackingTestCubeSecondary
        ncube.applicationID = ApplicationID.testAppId
        ncube2.applicationID = ApplicationID.testAppId
        ncubeRuntime.addCube(ncube)
        ncubeRuntime.addCube(ncube2)

        Map input = [Column: 'B', Row:1, state:'OH']
        Map output = [:]
        def x = ncube.getCell(input, output)
        assert 9 == x
        RuleInfo ruleInfo = ncube.getRuleInfo(output)

        assert ruleInfo.getInputKeysUsed().size() == 5
        assert ruleInfo.getInputKeysUsed().contains('Column')
        assert ruleInfo.getInputKeysUsed().contains('Row')
        assert ruleInfo.getInputKeysUsed().contains('state')
        assert ruleInfo.getInputKeysUsed().contains('age')
        assert ruleInfo.getInputKeysUsed().contains('weight')
    }

    @Test
    void testKeyTrackingInRuleConditions()
    {
        NCube ncube = NCubeBuilder.simpleAutoRule
        Map output = [:]
        ncube.getCell([rate:0], output)
        Set keys = NCube.getRuleInfo(output).getInputKeysUsed()
        assert keys.size() == 4
        assert keys.contains('conditions')
        assert keys.contains('AGe')
        assert keys.contains('coLOR')
        assert keys.contains('creditSCORE')

        output = [:]
        output.rate = 0.0
        ncube.getCell([age:50, creditScore:701], output)
        keys = NCube.getRuleInfo(output).getInputKeysUsed()
        assert keys.size() == 3
        assert keys.contains('conditions')
        assert keys.contains('AGe')
        assert keys.contains('creditSCORE')
    }

    @Test
    void testKeyTrackingMetaPropertyWithExp()
    {
        NCube ncube = NCubeBuilder.metaPropWithFormula
        Map output = [:]
        def formula = ncube.getMetaProperty("formula")
        ncube.extractMetaPropertyValue(formula, [:], output)
        Set keys = NCube.getRuleInfo(output).getInputKeysUsed()
        assert keys.size() == 1
        assert keys.contains("Revenue")

        formula = ncube.getMetaProperty("formula")
        def profit = ncube.extractMetaPropertyValue(formula, [revenue:100, cost:40, tax:(1 - 0.2)], output)
        assert profit == 48.0
        keys = NCube.getRuleInfo(output).getInputKeysUsed()
        assert keys.size() == 3
        assert keys.contains("Revenue")
        assert keys.contains("Cost")
        assert keys.contains("Tax")
    }
}