package com.cedarsoftware.ncube.rules

import com.cedarsoftware.ncube.NCubeAppContext
import com.cedarsoftware.ncube.NCubeBaseTest
import groovy.transform.CompileStatic
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired

import static com.cedarsoftware.ncube.ApplicationID.testAppId
import static com.cedarsoftware.ncube.NCubeAppContext.getNcubeRuntime
import static com.cedarsoftware.ncube.rules.TestApplication.createAndCacheNCube

@CompileStatic
class RulesConfigurationTest extends NCubeBaseTest
{
    @Autowired(required = false)
    RulesConfiguration config

    @Before
    void setup()
    {
        Assume.assumeTrue(NCubeAppContext.clientTest)
    }

    @Test
    void testGetRulesEngines()
    {
        Map<String, RulesEngine> engines = config.rulesEngines
        assert 3 == engines.size()
        RulesEngine fooEngine = engines['foo']
        assert 'foo' == fooEngine.name
        RulesEngine barEngine = engines['bar']
        assert 'bar' == barEngine.name
        RulesEngine bazEngine = engines['baz']
        assert 'baz' == bazEngine.name
    }

    @Test
    void testGetRulesEngine()
    {
        RulesEngine fooEngine = config.getRulesEngine('foo')
        assert 'foo' == fooEngine.name
        RulesEngine barEngine = config.getRulesEngine('bar')
        assert 'bar' == barEngine.name
    }

    @Test
    void testGetRulesEngineNames()
    {
        Set<String> engineNames = config.rulesEngineNames
        assert 3 == engineNames.size()
        assert engineNames.contains('foo')
        assert engineNames.contains('bar')
        assert engineNames.contains('baz')
    }

    @Test
    void testGetInfo()
    {
        createAndCacheNCube('rules/ncubes/app.rules.json')
        createAndCacheNCube('rules/ncubes/app.rules.categories.json')
        createAndCacheNCube(testAppId.asBranch('SomeBranch'), 'rules/ncubes/app.rules.json')
        createAndCacheNCube(testAppId.asRelease(), 'rules/ncubes/app.rules.json')

        Map info = config.info
        assert 3 == info.size()
        assert info.containsKey('foo')
        assert info.containsKey('bar')

        Map info2 = config.info
        assert info.is(info2)

        ncubeRuntime.clearCache(testAppId)
        ncubeRuntime.clearCache(testAppId.asBranch('SomeBranch'))
        ncubeRuntime.clearCache(testAppId.asRelease())
    }
}
