package com.cedarsoftware.ncube

import com.cedarsoftware.util.Converter
import com.google.common.base.Splitter
import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner

import static com.cedarsoftware.ncube.NCubeConstants.*
import static org.junit.Assert.fail

/**
 * @author John DeRegnaucourt (jdereg@gmail.com), Josh Snyder (joshsnyder@gmail.com)
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
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = NCubeApplication.class, initializers = ConfigFileApplicationContextInitializer.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles(profiles = ['combined-server-ml, test-marklogic'])
//@Ignore
class TestMarkLogicPersister
{

    @Test
    void testSearch()
    {
        List<NCubeInfoDto> dtos = mutableClient.search(ApplicationID.testAppId, null, null, null)
        String test = 'test'
    }

    @Test
    void testCreateCube()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        json = NCubeRuntime.getResourceAsString('test.branch.age.1.json')
        ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        try
        {
            mutableClient.createCube(ncube)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            println e.message
//            assertContainsIgnoreCase(e.message, 'unable to create', 'already exists')
        }
    }

    @Test
    void testUpdateCube()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        ncube.setCell('XYZ', [Code: -15])
        mutableClient.updateCube(ncube)
        ncube = mutableClient.getCube(ApplicationID.testAppId, 'TestBranch')
        assert 'XYZ' == ncube.getCell([Code: -15])
    }

    @Test
    void testDeleteCubes()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        mutableClient.deleteCubes(ApplicationID.testAppId, ['TestBranch'].toArray())
        List<NCubeInfoDto> dtos = mutableClient.getRevisionHistory(ApplicationID.testAppId, 'TestBranch')
        assert '0' == dtos[0].revision
        assert '-1' == dtos[1].revision
    }

    @Test
    void testGetAppNames()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        ncube.applicationID = new ApplicationID('NONE', 'app1', '1.0.0', ReleaseStatus.SNAPSHOT.name(), 'branch1')
        mutableClient.createCube(ncube)
        ncube.applicationID = new ApplicationID('NONE', 'app2', '1.0.0', ReleaseStatus.SNAPSHOT.name(), 'branch1')
        mutableClient.createCube(ncube)

        Object[] appNames = mutableClient.appNames
        assert 3 == appNames.length
        assert appNames.contains('sys.app')
        assert appNames.contains('app1')
        assert appNames.contains('app2')
        for (Object appName : appNames)
        {
            String app = (String)appName
            println "${app}"
            Object[] versions = mutableClient.getVersions(app)
            for (Object ver : versions)
            {
                String versionStatus = (String)ver
                println "-${versionStatus}"
                Iterable<String> i = Splitter.on('-').split(versionStatus)
                String version = i[0]
                String status = i[1]
                ApplicationID appId = new ApplicationID('NONE', app, version, status, 'NoBranch')
                Object [] branches = mutableClient.getBranches(appId)
                for (Object branchName : branches)
                {
                    String branch = (String)branchName
                    println "--${branch}"
                    ApplicationID ncubeApp = appId.asBranch(branch)
                }
            }
        }
        ApplicationID ncubeApp = new ApplicationID('NONE', 'DEFAULT_APP', '999.99.9', 'SNAPSHOT', 'TEST')
    }

    @Test
    void testLoadCubeRecordById()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        NCubeInfoDto idDto = mutableClient.search(ApplicationID.testAppId, 'TestBranch', null, null)[0]
        NCubeInfoDto dto = mutableClient.loadCubeRecordById(Converter.convertToLong(idDto.id), null)
        assert idDto.id == dto.id
        assert idDto.tenant == dto.tenant
        assert idDto.app == dto.app
        assert idDto.version == dto.version
        assert idDto.name == dto.name
    }

    @Test
    void testCopyBranch()
    {
        ApplicationID appId = setupHistory(false)
        List<NCubeInfoDto> dtos = mutableClient.search(appId, 'TestBranch', null, null)
        assert 1 == dtos.size()
        List<NCubeInfoDto> revisions = mutableClient.getRevisionHistory(appId, 'TestBranch')
        assert 1 == revisions.size()
    }

    @Test
    void testCopyBranchWithHistory()
    {
        ApplicationID appId = setupHistory(true)
        List<NCubeInfoDto> dtos = mutableClient.search(appId, 'TestBranch', null, null)
        assert 1 == dtos.size()
        List<NCubeInfoDto> revisions = mutableClient.getRevisionHistory(appId, 'TestBranch')
        assert 4 == revisions.size()
    }

    private ApplicationID setupHistory(boolean withHistory)
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        ncube.setCell('XYZ', [Code: -15])
        mutableClient.updateCube(ncube)
        ncube.setCell('LMN', [Code: -15])
        mutableClient.updateCube(ncube)
        ncube.setCell('RST', [Code: -15])
        mutableClient.updateCube(ncube)

        ApplicationID anotherBranch = ncube.applicationID.asBranch('anotherBranch')
        mutableClient.copyBranch(ncube.applicationID, anotherBranch, withHistory)
        return anotherBranch
    }

    @Test
    void testGetTestData()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        NCubeTest testFoo = new NCubeTest('testFoo', [:], [] as CellInfo[])
        NCubeTest testBar = new NCubeTest('testBar', [:], [] as CellInfo[])
        ncube.testData = [testFoo, testBar] as Object[]
        mutableClient.createCube(ncube)

        NCubeManager manager = NCubeAppContext.getBean('ncubeManager') as NCubeManager
        Object[] testData = manager.getTests(ApplicationID.testAppId, 'TestBranch')
        assert 2 == testData.length
        assert 'testFoo' == ((NCubeTest)testData[0]).name
        assert 'testBar' == ((NCubeTest)testData[1]).name
    }

    @Test
    void testGetAppTestData()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)

        json = NCubeRuntime.getResourceAsString('test.branch.age.1.json')
        ncube = NCube.fromSimpleJson(json)
        NCubeTest testFoo = new NCubeTest('testFoo', [:], [] as CellInfo[])
        NCubeTest testBar = new NCubeTest('testBar', [:], [] as CellInfo[])
        ncube.testData = [testFoo, testBar] as Object[]
        mutableClient.createCube(ncube)

        json = NCubeRuntime.getResourceAsString('testCube1.json')
        ncube = NCube.fromSimpleJson(json)
        ncube.testData = [testFoo] as Object[]
        mutableClient.createCube(ncube)

        Map tests = mutableClient.getAppTests(ApplicationID.testAppId)
        List branchTests = tests['TestBranch']
        assert 0 == branchTests.size()
        List ageTests = tests['TestAge']
        assert 2 == ageTests.size()
        List cubeTests = tests['TestCube']
        assert 1 == cubeTests.size()
    }

    @Test
    void testGetRevisions()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        ncube.applicationID = ApplicationID.testAppId.asVersion('1.6.1')
        mutableClient.createCube(ncube)
        ncube.setCell('XYZ', [Code: -15])
        mutableClient.updateCube(ncube)

        List<NCubeInfoDto> revs = mutableClient.getRevisionHistory(ApplicationID.testAppId, 'TestBranch', true)
        assert '1' == revs[0].revision
        assert '0' == revs[1].revision
    }

    @Test
    void testUpdateNotes()
    {
        String notes = 'new notes'
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        mutableClient.updateNotes(ApplicationID.testAppId, 'TestBranch', notes)
        NCubeInfoDto dto = mutableClient.search(ApplicationID.testAppId, 'TestBranch', null, [(SEARCH_INCLUDE_NOTES): true])[0]
        assert notes == dto.notes
    }

    @Test
    void testDuplicate()
    {
        ApplicationID branch1 = new ApplicationID('NONE', 'app', '1.0.0', 'SNAPSHOT', 'branch1')
        ApplicationID branch2 = new ApplicationID('NONE', 'app', '1.0.0', 'SNAPSHOT', 'branch2')
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        ncube.applicationID = branch1
        mutableClient.createCube(ncube)
        mutableClient.duplicate(branch1, branch2, 'TestBranch', 'TestBranch')
        List<NCubeInfoDto> dtos = mutableClient.search(branch2, 'TestBranch', null, null)
        assert 1 == dtos.size()
    }

    @Test
    void testRenameCube()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        mutableClient.renameCube(ApplicationID.testAppId, 'TestBranch', 'TestBlah')
        mutableClient.renameCube(ApplicationID.testAppId, 'TestBlah', 'Crypto')
        List<NCubeInfoDto> dtos = mutableClient.search(ApplicationID.testAppId, 'TestBranch', null, null)
        assert 1 == dtos.size()
        assert 'TestBranch' == dtos[0].name
        assert '-1' == dtos[0].revision
        dtos = mutableClient.search(ApplicationID.testAppId, 'TestBlah', null, null)
        assert 1 == dtos.size()
        assert 'TestBlah' == dtos[0].name
        assert '-1' == dtos[0].revision
        dtos = mutableClient.search(ApplicationID.testAppId, 'Crypto', null, null)
        assert 1 == dtos.size()
        assert 'Crypto' == dtos[0].name
        assert '0' == dtos[0].revision
        dtos = mutableClient.search(ApplicationID.testAppId, 'Test', null, null)
        assert 2 == dtos.size()
    }

    @Test
    void testDeleteBranch()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        List branches = Arrays.asList(mutableClient.getBranches(ApplicationID.testAppId))
        assert branches.contains(ApplicationID.testAppId.branch)
        mutableClient.deleteBranch(ApplicationID.testAppId)
        branches = Arrays.asList(mutableClient.appNames)
        assert !branches.contains(ApplicationID.testAppId.branch)
    }

    @Test
    void testDeleteApp()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        List appNames = Arrays.asList(mutableClient.appNames)
        assert appNames.contains(ApplicationID.testAppId.app)
        mutableClient.deleteApp(ApplicationID.testAppId)
        appNames = Arrays.asList(mutableClient.appNames)
        assert !appNames.contains(ApplicationID.testAppId.app)
    }

    @Test
    void testCommitCubes()
    {
        ApplicationID appId = new ApplicationID('NONE', 'commitApp', '1.0.0', 'SNAPSHOT', 'commitBranch')
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        ncube.applicationID = appId
        mutableClient.createCube(ncube)
        mutableClient.commitBranch(appId)
        List<NCubeInfoDto> dtos = mutableClient.search(appId.asHead(), null, null, null)
        assert 1 == dtos.size()
    }

    @Test
    void testChangeVersionValue()
    {
        ApplicationID appId = new ApplicationID('NONE', 'changeApp', '1.0.0', 'SNAPSHOT', 'changeBranch')
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        ncube.applicationID = appId
        mutableClient.createCube(ncube)
        mutableClient.changeVersionValue(appId, '2.0.0')
        List<NCubeInfoDto> dtos1 = mutableClient.search(appId, null, null, null)
        assert 0 == dtos1.size()
        List<NCubeInfoDto> dtos2 = mutableClient.search(appId.asVersion('2.0.0'), null, null, null)
        assert 1 == dtos2.size()
    }

    @Test
    void rollbackCubes()
    {
        ApplicationID appId = new ApplicationID('NONE', 'rollBackApp', '1.0.0', 'SNAPSHOT', 'rollBackBranch')
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        ncube.applicationID = appId
        mutableClient.createCube(ncube)
        mutableClient.commitBranch(appId)
        mutableClient.deleteCubes(appId, ['TestBranch'].toArray())
        mutableClient.restoreCubes(appId, ['TestBranch'].toArray())
        ncube.setCell('XYZ', [Code: 15])
        mutableClient.updateCube(ncube)
        ncube.setCell('CBA', [Code: -10])
        mutableClient.updateCube(ncube)
        mutableClient.rollbackBranch(appId, ['TestBranch'].toArray())
        NCube rbCube = mutableClient.getCube(appId, 'TestBranch')
        assert 'ZZZ' == rbCube.getCell([Code: -15])
        assert 'ABC' == rbCube.getCell([Code: -10])
        assert 'DEF' == rbCube.getCell([Code: 0])
        assert 'GHI' == rbCube.getCell([Code: 10])
        assert 'ZZZ' == rbCube.getCell([Code: 15])
    }

    @Test
    void testClearTestDatabase()
    {
        NCubeTestClient testClient = NCubeAppContext.getBean(RUNTIME_BEAN) as NCubeTestClient
        testClient.clearTestDatabase()
    }

    @Test
    void testDoCubesExist()
    {
        ApplicationID appId = new ApplicationID('NONE', 'SomeApp', '0.0.0', 'SNAPSHOT', 'HEAD')
        MarkLogicPersisterAdapter adapter = (MarkLogicPersisterAdapter)NCubeAppContext.getBean('mlPersister')
        boolean exist = adapter.doCubesExist(appId, false, '', 'user')
        assert !exist

    }

    private NCubeMutableClient getMutableClient()
    {
        String beanName = NCubeAppContext.containsBean(RUNTIME_BEAN) ? RUNTIME_BEAN : MANAGER_BEAN
        return NCubeAppContext.getBean(beanName) as NCubeMutableClient
    }
}
