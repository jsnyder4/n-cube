package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Ignore

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
    private static final String TEST_DB_ERROR = "You're not connected to the HSQLDB test database. Please check jdbc settings in application.properties and spring.profiles.active (use test-database)."

    @Before
    void setup()
    {
        NCube cp = runtimeClient.getNCubeFromResource(TestNCubeManager.defaultSnapshotApp, 'sys.classpath.tests.json')
        mutableClient.createCube(cp)
        cp = runtimeClient.getNCubeFromResource(ApplicationID.testAppId, 'sys.classpath.tests.json')
        mutableClient.createCube(cp)
    }

    @After
    void teardown()
    {
        testClient.clearTestDatabase()
        testClient.clearCache()
        testClient.clearSysParams()
    }

    NCube createCubeFromResource(ApplicationID appId = ApplicationID.testAppId, String fileName)
    {
        String json = NCubeRuntime.getResourceAsString(fileName)
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
        runtimeClient.clearCache(id)
    }
}