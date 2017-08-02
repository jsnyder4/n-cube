package com.cedarsoftware.util

import com.cedarsoftware.ncube.JdbcConnectionProvider
import com.cedarsoftware.ncube.NCube
import groovy.transform.CompileStatic

import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

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
class HsqlSchemaCreator
{
    HsqlSchemaCreator(String driverClassName, String databaseUrl, String username, String password, String schema)
    {
        NCube.DEFAULT_CELL_VALUE // force NCube class to load for custom json reader and writer
        TestingConnectionProvider provider = new TestingConnectionProvider(driverClassName, databaseUrl, username, password)

        URL url = getClass().getResource(schema)
        String fileContents = url.text

        Connection c = provider.connection
        try
        {
            Statement s = c.createStatement()
            // Normally this should NOT be used, however, for the first time creation of your MySQL
            // schema, you will want to run this one time.  You will also need to change
            // TestingDatabaseHelper.test_db = MYSQL instead of HSQL
            s.execute(fileContents)
            s.close()
        }
        finally
        {
            provider.releaseConnection(c)
        }
    }

    static class TestingConnectionProvider implements JdbcConnectionProvider
    {
        private String databaseUrl
        private String user
        private String password

        TestingConnectionProvider(String driverClassName, String databaseUrl, String user, String password)
        {
            if (driverClassName != null)
            {
                try
                {
                    Class.forName(driverClassName)
                }
                catch (Exception e)
                {
                    throw new IllegalArgumentException("Could not load: ${driverClassName}", e)
                }
            }

            this.databaseUrl = databaseUrl
            this.user = user
            this.password = password
        }

        Connection getConnection()
        {
            try
            {
                return DriverManager.getConnection(databaseUrl, user, password)
            }
            catch (Exception e)
            {
                throw new IllegalStateException("Could not create connection: ${databaseUrl}", e)
            }
        }

        void releaseConnection(Connection c)
        {
            try
            {
                c.close()
            }
            catch (Exception ignore)
            { }
        }
    }
}