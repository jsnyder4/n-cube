package com.cedarsoftware.ncube

import com.cedarsoftware.util.StringUtilities
import groovy.transform.CompileStatic

import java.sql.Connection
import java.sql.SQLException
import java.sql.Statement

/**
 * @author Ken Partlow (kpartlow@gmail.com)
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
class JdbcTestingDatabaseManager implements TestingDatabaseManager
{
    JdbcConnectionProvider provider
    NCubeJdbcPersister persister = new NCubeJdbcPersister()
    private final String schemaName

    JdbcTestingDatabaseManager(JdbcConnectionProvider p, String schemaName)
    {
        provider = p
        this.schemaName = schemaName
    }

    String fetchSchemaDDL()
    {
        URL url = getClass().getResource(schemaName)
        String fileContents = new File(url.getFile()).text
        return fileContents
    }

    void setUp() throws SQLException
    {
        Connection c = provider.connection
        try
        {
            Statement s = c.createStatement()
            // Normally this should NOT be used, however, for the first time creation of your MySQL
            // schema, you will want to run this one time.  You will also need to change
            // TestingDatabaseHelper.test_db = MYSQL instead of HSQL
            s.execute(fetchSchemaDDL())
            s.close()
        }
        finally
        {
            provider.releaseConnection(c)
        }
    }

    void tearDown() throws SQLException
    {
        Connection c = provider.connection
        try
        {
            Statement s = c.createStatement()
            s.execute("DROP TABLE n_cube;")
            s.close()
        }
        finally
        {
            provider.releaseConnection(c)
        }
    }


    void insertCubeWithNoSha1(ApplicationID appId, String username, NCube cube)
    {
        Connection c = provider.connection;
        try
        {
            byte[] cubeData = StringUtilities.getUTF8Bytes(cube.toFormattedJson())
            persister.insertCube(c, appId, cube.name, 0L, cubeData, (byte[]) null, "Inserted without sha1-1", (Boolean) false, (String) null, (String) null, username, 'insertCubeWithNoSha1')
        }
        finally
        {
            provider.releaseConnection(c)
        }
    }

    void addCubes(ApplicationID appId, String username, NCube[] cubes)
    {
        Connection c = provider.connection;
        try
        {
            for (NCube ncube : cubes)
            {
                persister.updateCube(c, appId, ncube, username)
            }
        }
        finally
        {
            provider.releaseConnection(c)
        }
    }

    void removeBranches(ApplicationID[] appIds)
    {
        Connection c = provider.connection;
        try
        {
            for (ApplicationID appId : appIds)
            {
                persister.deleteBranch(c, appId)
            }
        }
        finally
        {
            provider.releaseConnection(c)
        }
    }

    void updateCube(ApplicationID appId, String username, NCube ncube)
    {
        Connection c = provider.connection;
        try
        {
            persister.updateCube(c, appId, ncube, username)
        }
        finally
        {
            provider.releaseConnection(c)
        }
    }
}
