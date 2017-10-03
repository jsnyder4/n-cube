package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Ignore

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
@Ignore
class NCubeCleanupBaseTest extends NCubeBaseTest
{
    @Before
    void setup()
    {
        NCube cp = createCubeFromResource(TestNCubeManager.defaultSnapshotApp, 'sys.classpath.tests.json')
        cp = createCubeFromResource(ApplicationID.testAppId, 'sys.classpath.tests.json')
    }

    @After
    void teardown()
    {
        testClient.clearTestDatabase()
        testClient.clearSysParams()
        testClient.clearPermCache()
        super.teardown()
    }

    /**
     * Loads ncube into the mutableClient, replacing references to ${baseRemoteUrl}, if found in the json
     */
    NCube createCubeFromResource(ApplicationID appId = ApplicationID.testAppId, String fileName)
    {
        String json = NCubeRuntime.getResourceAsString(fileName).replaceAll('\\$\\{baseRemoteUrl\\}',baseRemoteUrl)
        NCube ncube = NCube.fromSimpleJson(json)
        ncube.applicationID = appId
        mutableClient.createCube(ncube)
        return ncube
    }

    void preloadCubes(ApplicationID id, String ...names)
    {
        for (String name : names)
        {
            createCubeFromResource(id, name)
        }
        ncubeRuntime.clearCache(id)
    }

    NCube loadCube(ApplicationID appId, String cubeName, Map options = null)
    {
        NCubeInfoDto record = mutableClient.loadCubeRecord(appId, cubeName, options)
        NCube ncube = NCube.createCubeFromRecord(record)
        return ncube
    }
}