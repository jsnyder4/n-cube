package com.cedarsoftware.ncube

import groovy.sql.Sql
//import oracle.jdbc.driver.OracleDriver
import org.junit.Test

import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

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
class TestCopy
{
    @Test
    void testCopy()
    {
//        Connection c = getConnection('oracle')
//        Sql sql = new Sql(c)
//        sql.withStatement { Statement stmt -> stmt.fetchSize = 10000 }
//        int count = 0
//        sql.eachRow("select n_cube_id from n_cube where app_cd='UD.REF.APP'", { ResultSet row ->
//            count++
//            if (count % 1000 == 0)
//            {
//                println count
//            }
//        })
//        println count
//        sql.close()
    }

    Connection getConnection(String dbms) throws SQLException
    {
        Connection conn = null
//
//        if (dbms.equals("oracle"))
//        {
//            Properties connectionProps = new Properties()
//            connectionProps.put("user", 'nce')
//            connectionProps.put("password", 'quality')
//
//            OracleDriver driver = new OracleDriver()
//            conn = driver.connect('jdbc:oracle:thin:@dm01np-scan.td.afg:1521/app_ncubed.dev.gai.com', connectionProps)
//        }
//        else if (dbms.equals("mysql"))
//        {
//            conn = DriverManager.getConnection("""jdbc:oracle:thin:@dm01np-scan.td.afg:1521/app_ncubed.dev.gai.com""", connectionProps)
//        }
//        System.out.println("Connected to database")
        return conn
    }
}
