package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.getNcubeRuntime

@CompileStatic
class TestNoOutputBindings extends NCubeBaseTest
{
    @Before
    void setup()
    {
        (ncubeRuntime as NCubeRuntime).trackBindings = false
    }

    @After
    void tearDown()
    {
        // Restore original spring configured state.
        (ncubeRuntime as NCubeRuntime).trackBindings = null
    }

    @Test
    void testOneRuleSetCallsAnotherRuleSet()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'ruleSet2.json')
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'ruleSet1.json')
        Map input = [age: 10]
        Map output = [:]
        ncube.getCell input, output
        assert 1.0 == output.total

        input.age = 48
        ncube.getCell input, output
        assert 8.560 == output.total

        input.age = 84
        ncube.getCell input, output
        assert 5.150 == output.total

        RuleInfo ruleInfo = NCube.getRuleInfo(output)
        assert 0 == ruleInfo.getAxisBindings().size()       // Would have been 6, but 0 because ncube.allow.mutable.methods = false [means readOnly]
    }
}
