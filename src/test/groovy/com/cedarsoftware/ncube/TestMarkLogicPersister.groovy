package com.cedarsoftware.ncube

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
        NCube ncube = mutableClient.getCube(ApplicationID.testAppId, 'TestBranch')
        ncube.setCell('XYZ', [Code: -15])
        mutableClient.updateCube(ncube)
        String test = 'test'
    }

    @Test
    void testDeleteCubes()
    {
        mutableClient.deleteCubes(ApplicationID.testAppId, ['TestBranch'].toArray())
    }

    @Test
    void testGetAppNames()
    {
        Object[] appNames = mutableClient.appNames
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
        NCubeInfoDto dto = mutableClient.loadCubeRecordById(151215875482800199L, null)
    }

    @Test
    void testCopyBranch()
    {
        String json = NCubeRuntime.getResourceAsString('test.branch.1.json')
        NCube ncube = NCube.fromSimpleJson(json)
        mutableClient.createCube(ncube)
        ncube.setCell('XYZ', [Code: -15])
        mutableClient.updateCube(ncube)

        mutableClient.copyBranch(ncube.applicationID, ncube.applicationID.asBranch('anotherBranch'))
        String test = 'test'
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
