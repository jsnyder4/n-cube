package com.cedarsoftware.ncube

import com.cedarsoftware.controller.NCubeController
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Ignore
import org.junit.runner.RunWith
import org.springframework.boot.test.context.ConfigFileApplicationContextInitializer
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringRunner

import static com.cedarsoftware.ncube.NCubeAppContext.getNcubeRuntime
import static org.junit.Assert.assertTrue

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
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = NCubeApplication.class, initializers = ConfigFileApplicationContextInitializer.class)
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
//@ActiveProfiles(profiles = [NCubeConstants.NCUBE_CLIENT_BEAN])  // requires server running
@ActiveProfiles(profiles = ['combined-server','test-database'])
@Ignore
class NCubeBaseTest implements NCubeConstants
{
    static String baseRemoteUrl

    @After
    void teardown()
    {
        testClient.clearCache()
    }
    
    static NCubeMutableClient getMutableClient()
    {
        String beanName = NCubeAppContext.containsBean(RUNTIME_BEAN) ? RUNTIME_BEAN : MANAGER_BEAN
        return NCubeAppContext.getBean(beanName) as NCubeMutableClient
    }

    static NCubeTestClient getTestClient()
    {
        return NCubeAppContext.getBean(RUNTIME_BEAN) as NCubeTestClient
    }

    static NCubeTestServer getTestServer()
    {
        return NCubeAppContext.getBean(MANAGER_BEAN) as NCubeTestServer
    }

    static NCubeController getNcubeController()
    {
        return NCubeAppContext.getBean(CONTROLLER_BEAN) as NCubeController
    }

    static void assertContainsIgnoreCase(String source, String... contains)
    {
        String lowerSource = source.toLowerCase()
        for (String contain : contains)
        {
            int idx = lowerSource.indexOf(contain.toLowerCase())
            assertTrue("'${contain}' not found in '${lowerSource}'", idx >= 0)
            lowerSource = lowerSource.substring(idx)
        }
    }

    static boolean checkContainsIgnoreCase(String source, String... contains)
    {
        String lowerSource = source.toLowerCase()
        for (String contain : contains)
        {
            int idx = lowerSource.indexOf(contain.toLowerCase())
            if (idx == -1)
            {
                return false
            }
            lowerSource = lowerSource.substring(idx)
        }
        return true
    }

    /**
     * Loads ncube into the runtimeClient, replacing references to ${baseRemoteUrl}, if found in the json
     */
    static NCube createRuntimeCubeFromResource(ApplicationID appId = ApplicationID.testAppId, String fileName)
    {
        String json = NCubeRuntime.getResourceAsString(fileName).replaceAll('\\$\\{baseRemoteUrl\\}',baseRemoteUrl)
        NCube ncube = NCube.fromSimpleJson(json)
        ncube.applicationID = appId
        ncubeRuntime.addCube(ncube)
        return ncube
    }
}