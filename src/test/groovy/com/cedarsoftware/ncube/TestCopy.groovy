package com.cedarsoftware.ncube

import groovy.sql.Sql
import groovy.transform.CompileStatic
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
@CompileStatic
class TestCopy
{
    /*
    @Test
    void testCopy()
    {
        Connection oracle = getConnection('oracle')
        List<Long> ids = fetchIds(oracle)
        oracle.close()
        println ids.size()
        oracle = getConnection('oracle')
        copyToMySql(oracle, ids)
        oracle.close()
    }

    private void copyToMySql(Connection oracle, ids)
    {
//        Connection mysql = getConnection('mysql')
        Iterator i = ids.iterator()
        Sql load = new Sql(oracle)
        load.withStatement { Statement statement -> statement.fetchSize = 10 }
        Map<String, Object> bind = [:] as Map
        int count = 0

        while (i.hasNext() && count++ < 10)
        {
            bind.id = i.next()
            load.eachRow("""\
SELECT n_cube_nm, create_dt, create_hid 
FROM n_cube 
WHERE n_cube_id = :id
""", bind, 0, 1, { ResultSet row ->
                String name = row.getString('n_cube_nm')
                Date createDate = new Date(row.getTimestamp('create_dt').time)
                String hid = row.getString('create_hid')
                println "${name}, ${createDate}, ${hid}"
            })
        }
//        mysql.close()
    }

    private List<Long> fetchIds(Connection oracleConn)
    {
        Sql sql = new Sql(oracleConn)
        sql.withStatement { Statement stmt -> stmt.fetchSize = 1000 }
        List<Long> ids = []
        sql.eachRow("select n_cube_id from n_cube", { ResultSet row ->
            ids.add(row.getLong('n_cube_id'))
            if (ids.size() % 10000 == 0)
            {
                println ids.size()
            }
        })
        sql.close()
        return ids
    }

    Connection getConnection(String dbms) throws SQLException
    {
        Connection conn = null

        if (dbms.equals("oracle"))
        {
            Properties connectionProps = new Properties()
            connectionProps.put("user", 'nce')
            connectionProps.put("password", 'quality')

            OracleDriver driver = new OracleDriver()
            conn = driver.connect('jdbc:oracle:thin:@dm01np-scan.td.afg:1521/app_ncubed.dev.gai.com', connectionProps)
        }
        else if (dbms.equals("mysql"))
        {
            Properties connectionProps = new Properties()
            connectionProps.put("user", 'nce')
            connectionProps.put("password", 'quality')

            Class.forName("com.mysql.jdbc.Driver");
            conn=DriverManager.getConnection("jdbc:mysql://localhost:3306/ncube","ncube","ncube");
        }
        return conn
    }
    */
}
