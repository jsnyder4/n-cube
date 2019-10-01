package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner

import static com.cedarsoftware.ncube.NCubeAppContext.getNcubeRuntime

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = [NCubeApplication.class, NCubeAppContext.class], initializers = ConfigFileApplicationContextInitializer.class)
@ActiveProfiles(profiles = ['combined-server','test-database'])
@SpringBootTest(properties= ["ncube.allow.mutable.methods=false"] )
@CompileStatic
class TestReadonlyNoBindings
{
    @Test
    void testOneRuleSetCallsAnotherRuleSet()
    {
        assert ncubeRuntime.readonly

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
