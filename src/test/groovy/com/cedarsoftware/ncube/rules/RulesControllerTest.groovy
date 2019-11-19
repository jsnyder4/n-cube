package com.cedarsoftware.ncube.rules

import com.cedarsoftware.ncube.NCubeAppContext
import com.cedarsoftware.ncube.NCubeBaseTest
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.ResponseEntity

import static TestApplication.createAndCacheNCube
import static com.cedarsoftware.ncube.ApplicationID.testAppId
import static com.cedarsoftware.ncube.NCubeAppContext.getNcubeRuntime
import static com.cedarsoftware.util.TestUtil.assertContainsIgnoreCase

@CompileStatic
class RulesControllerTest extends NCubeBaseTest
{
    @Autowired
    TestRestTemplate restTemplate

    @Before
    void setupNCubes()
    {
        Assume.assumeTrue(NCubeAppContext.clientTest)

        createAndCacheNCube('rules/ncubes/lookup.something.json')
        createAndCacheNCube('rules/ncubes/app.rules.json')
        createAndCacheNCube('rules/ncubes/app.rules.categories.json')
        createAndCacheNCube('rules/ncubes/rule.group1.type1.object1.json')
        createAndCacheNCube('rules/ncubes/rule.group1.type1.object2.json')
        createAndCacheNCube('rules/ncubes/rule.group2.type1.object1.json')
        createAndCacheNCube('rules/ncubes/rule.group1.type2.object1.json')
        createAndCacheNCube(testAppId.asBranch('SomeBranch'), 'rules/ncubes/app.rules.json')
        createAndCacheNCube(testAppId.asBranch('SomeBranch'), 'rules/ncubes/lookup.something.json')
        createAndCacheNCube(testAppId.asRelease(), 'rules/ncubes/app.rules.json')
    }

    @After
    void clearNCubes()
    {
        ncubeRuntime.clearCache(testAppId)
        ncubeRuntime.clearCache(testAppId.asBranch('SomeBranch'))
        ncubeRuntime.clearCache(testAppId.asRelease())
    }

    @Test
    void testGetInfo()
    {
        ResponseEntity<Map> entity = restTemplate.getForEntity('/ui/info', Map.class)
        assert 200 == entity.statusCodeValue
        Map body = entity.body
        assert body.containsKey('foo')
        assert body.containsKey('bar')

        Map foo = body['foo']
        assert foo.containsKey('categories')
        Map categories = foo['categories']
        assert 3 == categories.size()
        assert foo.containsKey('groups')
        List<String> groups = (List<String>) foo['groups']
        assert 3 == groups.size()
        assert groups.contains('group1')
        assert groups.contains('group2')
        assert groups.contains('group3')

        Map bar = body['bar']
        assert bar.containsKey('categories')
        categories = bar['categories']
        assert 0 == categories.size()
        assert bar.containsKey('groups')
        groups = (List<String>) bar['groups']
        assert 3 == groups.size()
    }

    @Test
    void testGetBusinessRules()
    {
        String query = '/ui/rules?engine={engine}&group={group}'
        ResponseEntity<Map> entity = restTemplate.getForEntity(query, Map.class, 'foo', 'group1')
        assert 200 == entity.statusCodeValue
        Map body = (Map<String, Object>)entity.body
        assert 1 == body.keySet().size()
        assert body.containsKey('group1')
    }

    @Test
    void testGetBusinessRules_NotDefined()
    {
        String query = '/ui/rules?engine={engine}&group={group}'
        ResponseEntity<Map> entity = restTemplate.getForEntity(query, Map.class, 'foo', 'foo')
        assert 200 == entity.statusCodeValue
        Map body = (Map<String, Object>)entity.body
        assert body.isEmpty()
    }

    @Test
    void testGetNcubeHtml()
    {
        String query = '/ui/ncube?name={name}&appIdString={appIdString}'
        ResponseEntity<Map> entity = restTemplate.getForEntity(query, Map.class, 'lookup.something', testAppId.toString())
        assert 200 == entity.statusCodeValue
        Map output = (Map<String, Object>)entity.body
        assert output.containsKey('html')
        assertContainsIgnoreCase(( String)output['html'], 'html', 'head', 'body')
    }

    @Test
    void testGetNcubeHtml_NotFound()
    {
        String query = '/ui/ncube?name={name}&appIdString={appIdString}'
        ResponseEntity<Map> entity = restTemplate.getForEntity(query, Map.class, 'foo', testAppId.toString())
        assert 200 == entity.statusCodeValue
        Map output = (Map<String, Object>)entity.body
        assert output.containsKey('html')
        assertContainsIgnoreCase((String)output['html'], 'not found')
    }

    @Test
    void testGetNcubeHtml_MissingName()
    {
        String query = '/ui/ncube?appIdString={appIdString}'
        ResponseEntity<Map> entity = restTemplate.getForEntity(query, Map.class, testAppId.toString())
        assert 400 == entity.statusCodeValue
    }

    @Test
    void testGetNcubeHtml_MissingAppId()
    {
        String query = '/ui/ncube?name={name}'
        ResponseEntity<Map> entity = restTemplate.getForEntity(query, Map.class, 'foo')
        assert 400 == entity.statusCodeValue
    }

    @Test
    void testGetRulesByCategory()
    {
        ResponseEntity<Map> entity = restTemplate.postForEntity('/ui/rulesByCategory', ['_engine': 'foo'], Map.class)
        assert 200 == entity.statusCodeValue
        Map body = (Map<String, Object>)entity.body
        assert 3 == body.size()
        assert body.containsKey('group1')
        assert body.containsKey('group2')
        assert body.containsKey('group3')
    }
}
