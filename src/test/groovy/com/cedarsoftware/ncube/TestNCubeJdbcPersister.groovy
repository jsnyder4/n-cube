package com.cedarsoftware.ncube

import com.cedarsoftware.util.EnvelopeException
import com.cedarsoftware.util.UniqueIdGenerator
import org.junit.Test

import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.SQLException

import static com.cedarsoftware.ncube.NCubeConstants.*
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail
import static org.mockito.Matchers.anyInt
import static org.mockito.Matchers.anyString
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when
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
class TestNCubeJdbcPersister extends NCubeCleanupBaseTest
{
    static final String APP_ID = TestNCubeManager.APP_ID
    static final String USER_ID = TestNCubeManager.USER_ID

    private ApplicationID defaultSnapshotApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, "1.0.0", ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)

    @Test
    void testDbApis()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCube ncube2 = NCubeBuilder.getTestNCube2D(true)
        ncube1.applicationID = defaultSnapshotApp
        ncube2.applicationID = defaultSnapshotApp

        mutableClient.createCube(ncube1)
        mutableClient.createCube(ncube2)

        Object[] cubeList = mutableClient.search(defaultSnapshotApp, "test.%", null, [(SEARCH_ACTIVE_RECORDS_ONLY) : true])

        assertTrue(cubeList != null)
        assertTrue(cubeList.length == 2)

        assertTrue(ncube1.numDimensions == 3)
        assertTrue(ncube2.numDimensions == 2)

        ncube1.deleteAxis("bu")
        ApplicationID next = defaultSnapshotApp.createNewSnapshotId("0.2.0")
        mutableClient.updateCube(ncube1)
        Integer numRelease = mutableClient.releaseCubes(defaultSnapshotApp, "0.2.0")
        assertEquals(0, numRelease)

        cubeList = mutableClient.search(next, 'test.*', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        // Two cubes at the new 1.2.3 SNAPSHOT version.
        assert cubeList.length == 2

        // Verify that you cannot delete a RELEASE ncube
        try
        {
            mutableClient.deleteCubes(defaultSnapshotApp, [ncube1.name].toArray())
            fail()
        }
        catch (EnvelopeException e)
        {
            e.message.contains('does not exist')
        }

        try
        {
            mutableClient.deleteCubes(defaultSnapshotApp, [ncube2.name].toArray())
        }
        catch (EnvelopeException e)
        {
            e.message.contains('does not exist')
        }

        // Delete new SNAPSHOT cubes
        assertTrue(mutableClient.deleteCubes(next, [ncube1.name].toArray()))
        assertTrue(mutableClient.deleteCubes(next, [ncube2.name].toArray()))

        // Ensure that all test ncubes are deleted
        cubeList = mutableClient.search(defaultSnapshotApp, "test.%", null, ['activeRecordsOnly' : true])
        assertTrue(cubeList.length == 0)
    }

    @Test
    void testGetAppNamesWithSQLException()
    {
        Connection c = getConnectionThatThrowsSQLException()
        try
        {
            new NCubeJdbcPersister().getAppNames(c, null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('cannot be null or empty')
        }
    }

    @Test
    void testGetAppVersionsWithSQLException()
    {
        Connection c = getConnectionThatThrowsSQLException()
        try
        {
            new NCubeJdbcPersister().getVersions(c, "DEFAULT", null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('cannot be null or empty')
        }
    }

    @Test
    void testUpdateBranchCubeHeadSha1BadArgs()
    {
        try
        {
            new NCubeJdbcPersister().updateBranchCubeHeadSha1(null, null, 'badSha1')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('id cannot be empty')
        }
        try
        {
            new NCubeJdbcPersister().updateBranchCubeHeadSha1(null, 75, '')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('sha-1 cannot be empty')
        }
    }

    @Test
    void testUpdateBranchThatIsNotFound()
    {
        Connection c = mock(Connection.class)
        PreparedStatement ps = mock(PreparedStatement.class)
        ResultSet rs = mock(ResultSet.class)
        when(c.prepareStatement(anyString())).thenReturn(ps).thenReturn(ps).thenReturn(ps)
        when(ps.executeQuery()).thenReturn(rs).thenReturn(rs)
        when(rs.next()).thenReturn(false)
        when(rs.getLong(1)).thenReturn(5L)
        when(rs.getDate(anyString())).thenReturn(new java.sql.Date(System.currentTimeMillis()))

        Object[] ids = [0] as Object[]
        assert new NCubeJdbcPersister().pullToBranch(c, defaultSnapshotApp, ids, USER_ID, UniqueIdGenerator.uniqueId).empty
    }

    @Test
    void testReleaseCubesWithRuntimeExceptionWhileCreatingNewSnapshot()
    {
        Connection c = mock(Connection.class)
        PreparedStatement ps = mock(PreparedStatement.class)
        ResultSet rs = mock(ResultSet.class)
        when(c.prepareStatement(anyString())).thenReturn(ps).thenReturn(ps).thenReturn(ps).thenThrow(NullPointerException.class)
        when(ps.executeQuery()).thenReturn(rs)
        when(rs.next()).thenReturn(false)

        try
        {
            new NCubeJdbcPersister().releaseCubes(c, defaultSnapshotApp, "1.2.3")
            fail()
        }
        catch (NullPointerException e)
        {
            assert e.message == null
        }
    }

    @Test
    void testCommitCubeWithInvalidRevision()
    {
        assert 0 == new NCubeJdbcPersister().commitCubes(null, defaultSnapshotApp, null, USER_ID, UniqueIdGenerator.uniqueId).size()
    }

    @Test
    void testCommitCubeThatDoesntExist()
    {
        createCubeFromResource(defaultSnapshotApp, '2DSimpleJson.json')
        List<NCubeInfoDto> dtos = mutableClient.search(defaultSnapshotApp, 'businessUnit', null, null)
        assert 1 == dtos.size()
        NCubeInfoDto dto = dtos[0]
        dto.name = 'notBusinessUnit'
        try
        {
            mutableClient.commitBranch(defaultSnapshotApp, dtos.toArray())
            fail()
        }
        catch (EnvelopeException e)
        {
            Map data = e.envelopeData as Map
            assert (data[mutableClient.BRANCH_ADDS] as Map).size() == 0
            assert (data[mutableClient.BRANCH_DELETES] as Map).size() == 0
            assert (data[mutableClient.BRANCH_UPDATES] as Map).size() == 0
            assert (data[mutableClient.BRANCH_RESTORES] as Map).size() == 0
            assert (data[mutableClient.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testCreateBranchWithNullPointerException()
    {
        Connection c = getConnectionThatThrowsExceptionAfterExistenceCheck(false, NullPointerException.class)

        try
        {
            new NCubeJdbcPersister().copyBranch(c, defaultSnapshotApp.asHead(), defaultSnapshotApp)
            fail()
        }
        catch (NullPointerException ignored)
        {
        }
    }

    private static getConnectionThatThrowsSQLException = { ->
        Connection c = mock(Connection.class)
        when(c.prepareStatement(anyString())).thenThrow(SQLException.class)
        when(c.createStatement()).thenThrow(SQLException.class)
        when(c.createStatement(anyInt(), anyInt())).thenThrow(SQLException.class)
        when(c.createStatement(anyInt(), anyInt(), anyInt())).thenThrow(SQLException.class)
        DatabaseMetaData metaData = mock(DatabaseMetaData.class)
        when(c.metaData).thenReturn(metaData)
        when(metaData.driverName).thenReturn("Oracle")
        return c
    }

    private static Connection getConnectionThatThrowsExceptionAfterExistenceCheck(boolean exists, Class exceptionClass = SQLException.class) throws SQLException
    {
        Connection c = mock(Connection.class)
        PreparedStatement ps = mock(PreparedStatement.class)
        ResultSet rs = mock(ResultSet.class)
        when(c.prepareStatement(anyString())).thenReturn(ps).thenThrow(exceptionClass)
        DatabaseMetaData metaData = mock(DatabaseMetaData.class)
        when(c.metaData).thenReturn(metaData)
        when(metaData.driverName).thenReturn("HSQL")
        when(ps.executeQuery()).thenReturn(rs)
        when(rs.next()).thenReturn(exists)
        return c
    }

    @Test
    void testUpdateBranchCubeWithNull()
    {
        List<NCubeInfoDto> list = new NCubeJdbcPersister().pullToBranch((Connection)null, (ApplicationID) null,(Object[]) null, null, UniqueIdGenerator.uniqueId)
        assert 0 == list.size()
    }
}