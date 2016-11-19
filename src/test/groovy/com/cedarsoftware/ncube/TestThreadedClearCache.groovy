package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
class TestThreadedClearCache
{
    public static String USER_ID = TestNCubeManager.USER_ID
    public static ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, "clearCacheTest", ApplicationID.DEFAULT_VERSION, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
    public static ApplicationID usedId = new ApplicationID(ApplicationID.DEFAULT_TENANT, "usedInvalidId", ApplicationID.DEFAULT_VERSION, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)

    private TestingDatabaseManager manager;

    @Before
    public void setup()
    {
        manager = TestingDatabaseHelper.testingDatabaseManager
        manager.setUp()

        NCubeManager.NCubePersister = TestingDatabaseHelper.persister
    }

    @After
    public void tearDown()
    {
        manager.tearDown()
        manager = null

        NCubeManager.clearCache()
    }

    @Test
    void testCubesWithThreadedClearCacheWithAppId()
    {
        NCube[] ncubes = TestingDatabaseHelper.getCubesFromDisk("sys.classpath.2per.app.json", "math.controller.json")

        // add cubes for this test.
        manager.addCubes(usedId, USER_ID, ncubes)

        concurrencyTestWithAppId()

        // remove cubes
        manager.removeBranches([usedId] as ApplicationID[])
    }

    private void concurrencyTestWithAppId()
    {
        long time = 8000L
        int numThreads = 8
        def run =
        {
            long start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < time)
            {
                try
                {
                    NCube cube = NCubeManager.getCube(usedId, "MathController")

                    for (int i=0; i < 10; i++)
                    {
                        def input = [:]
                        input.env = "a"
                        input.x = 5
                        input.method = 'square'

                        assertEquals(25, cube.getCell(input))

                        input.method = 'factorial'
                        assertEquals(120, cube.getCell(input))

                        input.env = "b"
                        input.x = 6
                        input.method = 'square'
                        assertEquals(6, cube.getCell(input))
                        input.method = 'factorial'
                        assertEquals(6, cube.getCell(input))
                    }
                }
                catch (Exception e)
                {
                    Throwable t = getDeepestException(e)
                    if (!(t.message?.contains('cleared while cell was executing') || t instanceof LinkageError))
                    {
                        throw e
                    }
                }
            }
        }

        def clearCache = {
            long start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < time)
            {
                try
                {
                    NCubeManager.clearCache()
                    Thread.sleep(250)
                }
                catch (Exception e)
                {
                    Throwable t = getDeepestException(e)
                    if (!(t.message?.contains('cleared while cell was executing') || t instanceof LinkageError))
                    {
                        throw e
                    }
                }
            }
        }

        Thread[] threads = new Thread[numThreads]

        for (int i = 0; i < numThreads; i++)
        {
            threads[i] = new Thread(run)
            threads[i].name = 'NCubeConcurrencyTest' + i
            threads[i].daemon = true
        }

        Thread clear = new Thread(clearCache)
        clear.name = "ClearCache";
        clear.daemon = true;

        // Start all at the same time (more concurrent that starting them during construction)
        for (int i = 0; i < numThreads; i++)
        {
            threads[i].start()
        }
        clear.start()

        for (int i = 0; i < numThreads; i++)
        {
            try
            {
                threads[i].join()
            }
            catch (InterruptedException ignored)
            { }
        }
        clear.join()
    }

    /**
     * Get the deepest (original cause) of the exception chain.
     * @param e Throwable exception that occurred.
     * @return Throwable original (causal) exception
     */
    static Throwable getDeepestException(Throwable e)
    {
        while (e.cause != null)
        {
            e = e.cause
        }

        return e
    }
}
