package com.cedarsoftware.ncube

import com.cedarsoftware.util.IOUtilities

import java.sql.SQLException

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
class TestingDatabaseHelper
{
    public static int MYSQL = 1
    public static int HSQL = 2
    public static int test_db = HSQL

    static NCube[] getCubesFromDisk(String ...names) throws IOException
    {
        List<NCube> list = new ArrayList<NCube>(names.length)

        for (String name : names)
        {
            URL url = NCubeManager.class.getResource("/" + name)
            File jsonFile = new File(url.file)

            InputStream input = null
            try
            {
                input = new FileInputStream(jsonFile)
                byte[] data = new byte[(int) jsonFile.length()]
                input.read(data)

                String str = new String(data, "UTF-8")
                list.add(NCube.fromSimpleJson(str))
            }
            finally
            {
                IOUtilities.close(input)
            }
        }

        return list.toArray(new NCube[list.size()])
    }

    static NCubePersister getPersister()
    {
        return new NCubeJdbcPersisterAdapter(createJdbcConnectionProvider())
    }

    static JdbcConnectionProvider createJdbcConnectionProvider()
    {
        if (test_db == HSQL)
        {
            return new TestingConnectionProvider(null, 'jdbc:hsqldb:mem:testdb', 'sa', '')
        }

        if (test_db == MYSQL)
        {
            return new TestingConnectionProvider(null, 'jdbc:mysql://127.0.0.1:3306/ncube?autoCommit=true', 'ncube', 'ncube')
        }

        throw new IllegalArgumentException('Unknown Database:  ' + test_db)
    }

    static TestingDatabaseManager getTestingDatabaseManager()
    {
        if (test_db == HSQL)
        {
            return new JdbcTestingDatabaseManager(createJdbcConnectionProvider(), '/ddl/hsqldb-schema.sql')
        }

        if (test_db == MYSQL)
        {
            return new JdbcTestingDatabaseManager(createJdbcConnectionProvider(), '/ddl/mysql-schema.sql')
        }

        //  Don't manage tables for other databases
        return new EmptyTestDatabaseManager()
    }

    private static class EmptyTestDatabaseManager implements TestingDatabaseManager
    {
        void setUp() throws SQLException
        {
        }

        void tearDown() throws SQLException
        {
        }

        void insertCubeWithNoSha1(ApplicationID appId, String username, NCube cube) {

        }

        void addCubes(ApplicationID appId, String username, NCube[] cubes)
        {

        }

        void removeBranches(ApplicationID[] appId)
        {
        }

        void updateCube(ApplicationID appId, String username, NCube cube)
        {

        }
    }

    static void setupDatabase()
    {
        testingDatabaseManager.setUp()
        NCubeManager.NCubePersister = persister
        setupTestClassPaths()
    }

    static void setupTestClassPaths()
    {
        NCube cp = NCubeManager.getNCubeFromResource(TestNCubeManager.defaultSnapshotApp, 'sys.classpath.tests.json')
        NCubeManager.updateCube(TestNCubeManager.defaultSnapshotApp, cp, true)
        cp = NCubeManager.getNCubeFromResource(ApplicationID.testAppId, 'sys.classpath.tests.json')
        NCubeManager.updateCube(ApplicationID.testAppId, cp, true)
    }

    static void tearDownDatabase()
    {
        try
        {
            NCubeManager.deleteCubes TestNCubeManager.defaultSnapshotApp, 'sys.classpath'
        }
        catch (Exception ignored)
        { }

        try
        {
            NCubeManager.deleteCubes ApplicationID.testAppId, 'sys.classpath'
        }
        catch (Exception ignored)
        { }

        testingDatabaseManager.tearDown()
        NCubeManager.clearCache()
    }
}
