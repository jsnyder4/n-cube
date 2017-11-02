package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.BranchMergeException
import com.cedarsoftware.util.UniqueIdGenerator
import org.junit.Test

import java.sql.*

import static org.junit.Assert.*
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
    void testCopyBranchInitialRevisions()
    {
        String cubeNameBranch = 'TestBranch'
        String cubeNameAge = 'TestAge'
        ApplicationID branch1 = defaultSnapshotApp.asBranch('branch1')
        ApplicationID branch2 = defaultSnapshotApp.asBranch('branch2')
        ApplicationID branch3 = defaultSnapshotApp.asBranch('branch3')
        preloadCubes(branch1, 'test.branch.1.json', 'test.branch.age.1.json')
        mutableClient.commitBranch(branch1)
        mutableClient.updateBranch(branch2)

        NCube ncube1 = mutableClient.getCube(branch1, cubeNameBranch)
        ncube1.setCell('XYZ', [Code: -10])
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(branch1)

        NCube ncube2 = mutableClient.getCube(branch2, cubeNameBranch)
        ncube2.setCell('XYZ', [Code: 10])
        mutableClient.updateCube(ncube2)
        // make sure this works with multiple modified cubes in branch
        ncube2 = mutableClient.getCube(branch2, 'TestAge')
        ncube2.setCell('infant', [Code: 0])
        mutableClient.updateCube(ncube2)

        ncube1.setCell('ABC', [Code: -10])
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(branch1)

        mutableClient.copyBranch(branch2, branch3)

        NCube ncube3 = mutableClient.getCube(branch3, cubeNameBranch)
        assert 'ABC' == ncube3.getCell([Code: -10])
        assert 'XYZ' == ncube3.getCell([Code: 10])

        ncube3 = mutableClient.getCube(branch3, cubeNameAge)
        assert 'infant' == ncube3.getCell([Code: 0])

        mutableClient.rollbackBranch(branch3, [cubeNameBranch, cubeNameAge] as Object[])
        ncube3 = mutableClient.getCube(branch3, cubeNameBranch)
        assert 'ABC' == ncube3.getCell([Code: -10])
        assert 'GHI' == ncube3.getCell([Code: 10])

        ncube3 = mutableClient.getCube(branch3, cubeNameAge)
        assert 'baby' == ncube3.getCell([Code: 0])
    }

    @Test
    void testCopyBranchCreateHidCurrentUser()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        ApplicationID branch1 = defaultSnapshotApp.asBranch('branch1')
        ApplicationID branch2 = defaultSnapshotApp.asBranch('branch2')
        preloadCubes(branch1, 'test.branch.1.json', 'test.branch.age.1.json')

        String origUser = mutableClient.userId
        String otherUser = UniqueIdGenerator.uniqueId
        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        manager.userId = otherUser

        try
        {
            mutableClient.copyBranch(branch1, branch2)
            NCubeInfoDto branch1dto = mutableClient.getRevisionHistory(branch1, 'TestBranch').first()
            NCubeInfoDto branch2dto = mutableClient.getRevisionHistory(branch2, 'TestBranch').first()
            String branch1hid = branch1dto.createHid
            String branch2hid = branch2dto.createHid

            assert branch1hid != branch2hid
            assert branch1hid == origUser
            assert branch2hid == otherUser
        }
        finally
        { // reset userId no matter what
            manager.userId = origUser
        }
    }

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
        catch (IllegalArgumentException e)
        {
            e.message.contains('does not exist')
        }

        try
        {
            mutableClient.deleteCubes(defaultSnapshotApp, [ncube2.name].toArray())
        }
        catch (IllegalArgumentException e)
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
            new NCubeJdbcPersister().updateBranchCubeHeadSha1(null, null, 'branchSha1', 'badSha1')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('id cannot be empty')
        }
        try
        {
            new NCubeJdbcPersister().updateBranchCubeHeadSha1(null, 75, '', 'badSha1')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('sha-1 cannot be empty')
        }
        try
        {
            new NCubeJdbcPersister().updateBranchCubeHeadSha1(null, 75, 'branchSha1', '')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('head sha-1')
            assert e.message.toLowerCase().contains('cannot be empty')

        }
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
            new NCubeJdbcPersister().releaseCubes(c, defaultSnapshotApp)
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
        assert 0 == new NCubeJdbcPersister().commitCubes(null, defaultSnapshotApp, null, USER_ID, 'requestUser', 'testprid', 'notes').size()
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
        catch (BranchMergeException e)
        {
            Map data = e.errors
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
            new NCubeJdbcPersister().copyBranch(c, defaultSnapshotApp.asHead(), defaultSnapshotApp, USER_ID)
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