package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.BranchMergeException
import com.cedarsoftware.ncube.exception.CommandCellException
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.ncube.exception.InvalidCoordinateException
import com.cedarsoftware.ncube.util.CdnClassLoader
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.junit4.SpringRunner

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static com.cedarsoftware.ncube.ReferenceAxisLoader.*
import static org.junit.Assert.*

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
@RunWith(SpringRunner.class)
@TestPropertySource(properties=["ncube.accepted.domains=org.apache."])
class TestWithPreloadedDatabase extends NCubeCleanupBaseTest
{
    public static ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'preloaded', ApplicationID.DEFAULT_VERSION, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
    public static ApplicationID sysAppId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'sys.app', ApplicationID.SYS_BOOT_VERSION, ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
    private static final ApplicationID HEAD = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
    private static final ApplicationID BRANCH1 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'FOO')
    private static final ApplicationID BRANCH2 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'BAR')
    private static final ApplicationID BRANCH3 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.29.0', ReleaseStatus.SNAPSHOT.name(), 'FOO')

    @Before
    void setup() {}

    @Test
    void testCoordinateNotFoundExceptionThrown()
    {
        preloadCubes(appId, "test.coordinate.not.found.exception.json")

        NCube cube = mutableClient.getCube(appId, "test.coordinate.not.found.exception")

        try
        {
            cube.getCell([:])
            fail("should throw an exception")
        }
        catch (CoordinateNotFoundException e)
        {
            assertContainsIgnoreCase(e.message, 'error', 'in cube', cube.name, '[')
            assertNull(e.cubeName)
            assertNull(e.coordinate)
            assertNull(e.axisName)
            assertNull(e.value)
        }
        catch (Exception ignored)
        {
            fail("should throw CoordinateNotFoundException")
        }
    }

    @Test
    void testCoordinateNotFoundExceptionThrownWithAdditionalInfo()
    {
        preloadCubes(appId, "test.coordinate.not.found.exception.additional.info.json")

        NCube cube = mutableClient.getCube(appId, "test.coordinate.not.found.exception.additional.info")

        try
        {
            cube.getCell([:])
            fail("should throw an exception")
        }
        catch (CoordinateNotFoundException e)
        {
            assertContainsIgnoreCase(e.message, 'fail with additional info')
            assertEquals(cube.name, e.cubeName)
            assertEquals([condition:'true'], e.coordinate)
            assertEquals("condition", e.axisName)
            assertEquals("value", e.value)

        }
        catch (Exception ignored)
        {
            fail("should throw CoordinateNotFoundException")
        }
    }

    @Test
    void testInvalidCoordinateExceptionThrown()
    {
        preloadCubes(appId, "test.invalid.coordinate.exception.json")

        NCube cube = mutableClient.getCube(appId, 'test.invalid.coordinate.exception')

        try
        {
            cube.getCell([:])
            fail()
        }
        catch (CommandCellException e)
        {
            assertTrue(e.message.contains("Error occurred in cube"))
            assertTrue((e.cause instanceof InvalidCoordinateException))
            assertTrue((e.cause instanceof IllegalArgumentException))
            InvalidCoordinateException invalidException = e.cause as InvalidCoordinateException
            assertTrue(invalidException.message.contains("fail with additional info"))
            assertEquals(cube.name, invalidException.cubeName)
            assertEquals(['coord1','coord2'] as  Set,invalidException.coordinateKeys)
            assertEquals(['req1','req2'] as Set, invalidException.requiredKeys)
       }
        catch (Exception ignored)
        {
            fail("should throw InvalidCoordinateException")
        }
    }

    @Test
    void testGetAppNames()
    {
        ApplicationID app1 = new ApplicationID(ApplicationID.DEFAULT_TENANT, "test", "1.28.0", ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
        ApplicationID app2 = new ApplicationID(ApplicationID.DEFAULT_TENANT, "foo", "1.29.0", ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
        ApplicationID app3 = new ApplicationID(ApplicationID.DEFAULT_TENANT, "bar", "1.29.0", ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
        preloadCubes(app1, "test.branch.1.json", "test.branch.age.1.json")
        preloadCubes(app2, "test.branch.1.json", "test.branch.age.1.json")
        preloadCubes(app3, "test.branch.1.json", "test.branch.age.1.json")

        ApplicationID branch1 = new ApplicationID(ApplicationID.DEFAULT_TENANT, "test", "1.28.0", ReleaseStatus.SNAPSHOT.name(), 'kenny')
        ApplicationID branch2 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'foo', '1.29.0', ReleaseStatus.SNAPSHOT.name(), 'kenny')
        ApplicationID branch3 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.29.0', ReleaseStatus.SNAPSHOT.name(), 'someoneelse')
        ApplicationID branch4 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'someoneelse')

        assertEquals(3, mutableClient.copyBranch(HEAD, branch1))
        assertEquals(3, mutableClient.copyBranch(HEAD, branch2))
        // version doesn't match one in HEAD, nothing created.
        assertEquals(0, mutableClient.copyBranch(branch3.asHead(), branch3))
        assertEquals(3, mutableClient.copyBranch(branch4.asHead(), branch4))

        assertTrue(mutableClient.appNames.contains('test'))
        assertTrue(mutableClient.appNames.contains('foo'))
        assertTrue(mutableClient.appNames.contains('bar'))
    }

    @Test
    void testCommitBranchOnCubeCreatedInBranch()
    {
        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assert dtos.size() == 1

        // verify no HEAD changes for branch
        List<NCubeInfoDto> dtos2 = mutableClient.getHeadChangesForBranch(BRANCH1)
        assert dtos2.empty
        // end verify

        String notesText = 'i am a note'
        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos.toArray(), notesText)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        // ensure that there are no more branch changes after create
        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assert dtos.empty

        ApplicationID headId = HEAD
        dtos = mutableClient.search(headId, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assert 1 == dtos.size()
        assertContainsIgnoreCase(dtos[0].notes, notesText)
    }

    @Test
    void testMergeWithNoHeadCube()
    {
        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')
        createCubeFromResource(BRANCH2, 'test.branch.age.2.json')

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        dtos = mutableClient.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)
    }

    @Test
    void updateWithNoChangeClearsChangedFlag()
    {
        NCube cube1 = createCubeFromResource(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        List<NCubeInfoDto> cubes0 = mutableClient.search(BRANCH1, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assert cubes0.size() == 1
        NCubeInfoDto info0 = cubes0[0]
        assert info0.revision == "0"
        assert !info0.changed

        cube1.setCell('XYZ', [code: 15])
        mutableClient.updateCube(cube1)
        List<NCubeInfoDto> cubes1 = mutableClient.search(BRANCH1, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assert cubes1.size() == 1
        NCubeInfoDto info1 = cubes1[0]
        assert info1.id != info0.id
        assert info1.revision == "1"
        assert info1.sha1 != info0.sha1
        assert info1.changed

        cube1.removeCell([code: 15])
        mutableClient.updateCube(cube1)
        List<NCubeInfoDto> cubes2 = mutableClient.search(BRANCH1, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assert cubes2.size() == 1
        NCubeInfoDto info2 = cubes2[0]
        assert info2.id != info1.id
        assert info2.id != info0.id
        assert info2.revision == "2"
        assert info2.sha1 == info0.sha1
        assert !info2.changed
    }

    @Test
    void testGetBranchChangesOnceBranchIsDeleted()
    {
        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        assertTrue(mutableClient.deleteBranch(BRANCH1))

        // ensure that there are no more branch changes after delete
        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testDeleteBranchRemovesTripZeroWhenNoOtherVersionsOfBranchExist()
    {
        preloadCubes(BRANCH2, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        List<NCubeInfoDto> dtos = mutableClient.search(BRANCH1, '*', null, null)
        assertEquals(1, dtos.size())

        dtos = mutableClient.search(BRANCH1.asVersion('0.0.0'), '*', null, null)
        assertEquals(4, dtos.size())

        assertTrue(mutableClient.deleteBranch(BRANCH1))

        dtos = mutableClient.search(BRANCH1, '*', null, null)
        assertEquals(0, dtos.size())

        dtos = mutableClient.search(BRANCH1.asVersion('0.0.0'), '*', null, null)
        assertEquals(0, dtos.size())
    }

    @Test
    void testDeleteBranchDoesNotRemoveTripZeroWhenOtherVersionsOfBranchExist()
    {
        ApplicationID patch = BRANCH1.asVersion('1.28.1')
        ApplicationID tripZero = BRANCH1.asVersion('0.0.0')
        preloadCubes(BRANCH2, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)
        mutableClient.releaseCubes(HEAD, '1.29.0')
        mutableClient.copyBranch(BRANCH3, patch)

        List<NCubeInfoDto> dtos = mutableClient.search(patch, '*', null, null)
        assertEquals(1, dtos.size())

        dtos = mutableClient.search(BRANCH3, '*', null, null)
        assertEquals(1, dtos.size())

        dtos = mutableClient.search(tripZero, '*', null, null)
        assertEquals(4, dtos.size())

        assertTrue(mutableClient.deleteBranch(patch))

        dtos = mutableClient.search(patch, '*', null, null)
        assertEquals(0, dtos.size())

        dtos = mutableClient.search(BRANCH3, '*', null, null)
        assertEquals(1, dtos.size())

        dtos = mutableClient.search(tripZero, '*', null, null)
        assertEquals(4, dtos.size())
    }

    @Test
    void testDeleteAppPass()
    {
        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')
        mutableClient.commitBranch(BRANCH1)

        assert 1 == mutableClient.search(BRANCH1, null, null, null).size()
        assert 1 == mutableClient.search(HEAD, null, null, null).size()
        assert 3 == mutableClient.search(HEAD.asBootVersion(), null, null, null).size()

        mutableClient.deleteApp(BRANCH1)
        assert !mutableClient.getVersions(BRANCH1.app).size()
    }

    @Test
    void testDeleteReleasedAppFails()
    {
        String newSnapVer = '1.1.1'
        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.releaseCubes(HEAD, newSnapVer)

        assert 1 == mutableClient.search(HEAD.asRelease(), null, null, null).size()

        try
        {
            mutableClient.deleteApp(BRANCH1)
            fail()
        }
        catch(IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'Only applications without a released version can be deleted')
        }
    }

    @Test
    void testDeleteAppNotSysAdminFails()
    {
        ApplicationID sysAppBranch = sysAppId.asBranch('test')
        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(sysAppId, sysAppBranch)
        NCube sysPermsCube = mutableClient.getCube(sysAppBranch, SYS_USERGROUPS)
        sysPermsCube.setCell(false, [(AXIS_USER):mutableClient.userId, (AXIS_ROLE):ROLE_ADMIN])
        mutableClient.updateCube(sysPermsCube)
        mutableClient.commitBranch(sysAppBranch)
        testClient.clearPermCache()

        try
        {
            mutableClient.deleteApp(BRANCH1)
            fail()
        }
        catch(IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'system administrator')
        }
    }

    @Test
    void testUpdateBranchOnCubeCreatedInBranch()
    {
        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        //  update didn't affect item added locally
        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)
    }

    @Test
    void testRollbackBranchWithPendingAdd()
    {
        preloadCubes(HEAD, "test.branch.1.json")
        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())
        Object[] names = [dtos.first().name]
        mutableClient.rollbackBranch(BRANCH1, names)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testRollbackBranchWithDeletedCube()
    {
        preloadCubes(BRANCH1, "test.branch.1.json")
        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(1, mutableClient.search(HEAD, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(1, mutableClient.search(BRANCH1, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        Object[] names = [dtos.first().name]
        assertEquals(1, dtos.size())

        List<NCubeInfoDto> dtos2 = mutableClient.getHeadChangesForBranch(BRANCH1)
        assert dtos2.empty

        // undo delete
        mutableClient.rollbackBranch(BRANCH1, names)
        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testRollbackBranchWithRestoredCube()
    {
        preloadCubes(BRANCH1, 'test.branch.1.json')
        String cubeName = 'TestBranch'
        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(1, mutableClient.search(HEAD, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(1, mutableClient.search(BRANCH1, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())

        mutableClient.deleteCubes(BRANCH1, cubeName)
        assertNull(mutableClient.getCube(BRANCH1, cubeName))
        result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        mutableClient.restoreCubes(BRANCH1, cubeName)
        assertNotNull(mutableClient.getCube(BRANCH1, cubeName))

        // undo restore
        mutableClient.rollbackBranch(BRANCH1, cubeName)
        assertNull(mutableClient.getCube(BRANCH1, cubeName))

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testRollbackAfterRelease()
    {
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)

        String cubeName = 'TestBranch'
        Map coord = [Code: 15]
        String[] changes = ['AAA', 'BBB', 'CCC']

        NCube cube = mutableClient.getCube(BRANCH1, cubeName)
        cube.setCell(changes[0], coord)
        mutableClient.updateCube(cube)
        mutableClient.commitBranch(BRANCH1)

        cube.setCell(changes[1], coord)
        mutableClient.updateCube(cube)
        mutableClient.commitBranch(BRANCH1)

        cube.setCell(changes[2], coord)
        mutableClient.updateCube(cube)

        String nextVersion = '1.29.0'
        mutableClient.releaseCubes(BRANCH1, nextVersion)
        ApplicationID nextBranch1 = BRANCH1.asVersion(nextVersion)
        mutableClient.rollbackBranch(nextBranch1, cubeName)

        cube = mutableClient.getCube(nextBranch1, cubeName)
        assertEquals(changes[1], cube.getCell(coord))
    }

    @Test
    void testCreateBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // pre-branch, cubes don't exist
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))

        testValuesOnBranch(HEAD)

        def cube1Sha1 = mutableClient.getCube(HEAD, "TestBranch").sha1()
        def cube2Sha1 = mutableClient.getCube(HEAD, "TestAge").sha1()

        List<NCubeInfoDto> objects = mutableClient.search(HEAD, "*", null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        objects.each { NCubeInfoDto dto ->
            assertNull(dto.headSha1)
        }

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        assertEquals(cube1Sha1, mutableClient.getCube(BRANCH1, "TestBranch").sha1())
        assertEquals(cube2Sha1, mutableClient.getCube(BRANCH1, "TestAge").sha1())

        objects = mutableClient.search(BRANCH1, "*", null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        objects.each { NCubeInfoDto dto ->
            assertNotNull(dto.headSha1)
        }

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)
    }

    @Test
    void testCommitBranchWithItemCreatedInBranchOnly()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, 'test.branch.1.json')

        NCube cube = mutableClient.getCube(HEAD, 'TestBranch')
        assertEquals('ABC', cube.getCell(['Code': -10]))
        assertNull(mutableClient.getCube(HEAD, 'TestAge'))

        // pre-branch, cubes don't exist
        assertNull(mutableClient.getCube(BRANCH1, 'TestBranch'))
        assertNull(mutableClient.getCube(BRANCH1, 'TestAge'))
        assertNull(mutableClient.getCube(HEAD, 'TestAge'))

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, 'TestBranch').size())
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))

        cube = mutableClient.getCube(HEAD, 'TestBranch')
        assertEquals('ABC', cube.getCell(['Code': -10]))
        assertNull(mutableClient.getCube(HEAD, 'TestAge'))

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, 'TestBranch').size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, 'TestBranch').size())
        assertNull(mutableClient.getCube(BRANCH1, 'TestAge'))
        assertNull(mutableClient.getCube(HEAD, 'TestAge'))

        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, 'TestBranch').size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, 'TestBranch').size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, 'TestAge').size())
        assertNull(mutableClient.getCube(HEAD, 'TestAge'))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos.toArray())
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, 'TestBranch').size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, 'TestAge').size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, 'TestBranch').size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, 'TestAge').size())
    }

    @Test
    void testUpdateBranchWithUpdateOnBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        // pre-branch, cubes don't exist
        assertNull(mutableClient.getCube(HEAD, "TestAge"))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))
        assertNull(mutableClient.getCube(BRANCH2, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH2, "TestAge"))

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH2))

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH2, "TestBranch").size())

        NCube cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(mutableClient.updateCube(cube))

        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)
        mutableClient.commitBranch(BRANCH1, dtos)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(2, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH2, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH2, "TestAge").size())

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testCommitBranchOnUpdate()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(HEAD)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))

        NCube cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(mutableClient.updateCube(cube))

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        // commit the branch
        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // check HEAD hasn't changed.
        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos.toArray())
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(2, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        // both should be updated now.
        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))
        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testCommitBranchOnUpdateWithOldInvalidSha1()
    {
        createCubeFromResource(HEAD, 'test.branch.1.json')

        //assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").length)
        // pre-branch, cubes don't exist
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))

        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2")

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch2"))

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())

        // No changes have happened yet, even though sha1 is incorrect,
        // we just copy the sha1 when we create the branch so the headsha1 won't
        // differ until we make a change.
        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testCommitBranchWithUpdateAndWrongRevisionNumber()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        NCube cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(mutableClient.updateCube(cube))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)
        ((NCubeInfoDto)dtos[0]).revision = Long.toString(100)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testRollback()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(HEAD)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))

        NCube cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(mutableClient.updateCube(cube))
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        cube.setCell("FOO", [Code : 10.0])
        assertEquals(3, cube.cellMap.size())
        assertEquals("FOO", cube.getCell([Code : 10.0]))

        assertTrue(mutableClient.updateCube(cube))
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        assertTrue(mutableClient.updateCube(cube))
        assertEquals(4, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        cube.setCell("FOO", [Code : 10.0])
        assertEquals(3, cube.cellMap.size())
        assertEquals("FOO", cube.getCell([Code : 10.0]))

        assertTrue(mutableClient.updateCube(cube))
        assertEquals(5, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())
        assertEquals(1, mutableClient.rollbackBranch(BRANCH1, "TestBranch"))

        assertEquals(6, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.size())
    }

    @Test
    void testCommitBranchOnDelete()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(HEAD)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))

        NCube cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(mutableClient.deleteCubes(BRANCH1, 'TestBranch'))

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())

        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        // cube is deleted
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        // check HEAD hasn't changed.
        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  loads in both TestAge and TestBranch though only TestBranch has changed.
        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(2, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        // both should be updated now.
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(HEAD, "TestBranch"))
    }

    @Test
    void testSearch()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json", "TestCubeLevelDefault.json", "basicJumpStart.json")
        testValuesOnBranch(HEAD)

        //  delete and re-add these cubes.
        assertEquals(5, mutableClient.copyBranch(HEAD, BRANCH1))
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        mutableClient.deleteCubes(BRANCH1, 'TestAge')

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 2
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        NCube cube = ncubeRuntime.getNCubeFromResource(BRANCH1, "test.branch.2.json")
        mutableClient.updateCube(cube)
        result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        // test with default options
        assertEquals(4, mutableClient.search(HEAD, null, null, null).size())
        assertEquals(4, mutableClient.search(HEAD, "", "", null).size())
        assertEquals(2, mutableClient.search(HEAD, "Test*", null, null).size())
        assertEquals(1, mutableClient.search(HEAD, "Test*", "zzz", null).size())
        assertEquals(0, mutableClient.search(HEAD, "*Codes*", "ZZZ", null).size())
        assertEquals(1, mutableClient.search(HEAD, "*Codes*", "OH", null).size())
        assertEquals(1, mutableClient.search(HEAD, null, "ZZZ", null).size())

        Map map = new HashMap()

        // test with default options and valid map
        assertEquals(4, mutableClient.search(HEAD, null, null, map).size())
        assertEquals(4, mutableClient.search(HEAD, "", "", map).size())
        assertEquals(2, mutableClient.search(HEAD, "Test*", null, map).size())
        assertEquals(1, mutableClient.search(HEAD, "Test*", "zzz", map).size())
        assertEquals(0, mutableClient.search(HEAD, "*Codes*", "ZZZ", map).size())
        assertEquals(1, mutableClient.search(HEAD, "*Codes*", "OH", map).size())
        assertEquals(1, mutableClient.search(HEAD, null, "ZZZ", map).size())

        map = new HashMap()
        map.put(SEARCH_ACTIVE_RECORDS_ONLY, true)

        assertEquals(3, mutableClient.search(HEAD, null, null, map).size())
        assertEquals(3, mutableClient.search(HEAD, "", "", map).size())
        assertEquals(1, mutableClient.search(HEAD, "Test*", null, map).size())
        assertEquals(0, mutableClient.search(HEAD, "Test*", "zzz", map).size())
        assertEquals(0, mutableClient.search(HEAD, "*Codes*", "ZZZ", map).size())
        assertEquals(1, mutableClient.search(HEAD, "*Codes*", "OH", map).size())
        assertEquals(0, mutableClient.search(HEAD, null, "ZZZ", map).size())
        assertEquals(0, mutableClient.search(HEAD, null, "TestCubeLevelDefault", map).size())

        map.put(SEARCH_ACTIVE_RECORDS_ONLY, false)
        map.put(SEARCH_DELETED_RECORDS_ONLY, true)

        assertEquals(1, mutableClient.search(HEAD, null, null, map).size())
        assertEquals(1, mutableClient.search(HEAD, "", "", map).size())
        assertEquals(1, mutableClient.search(HEAD, "Test*", null, map).size())
        assertEquals(1, mutableClient.search(HEAD, "Test*", "zzz", map).size())
        assertEquals(0, mutableClient.search(HEAD, "*Codes*", "ZZZ", map).size())
        assertEquals(0, mutableClient.search(HEAD, "*Codes*", "OH", map).size())
        assertEquals(1, mutableClient.search(HEAD, null, "ZZZ", map).size())

        map.put(SEARCH_DELETED_RECORDS_ONLY, false)
        map.put(SEARCH_CHANGED_RECORDS_ONLY, true)
    }

    @Test
    void testSearchWithCheckSha1()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json", "TestCubeLevelDefault.json", "basicJumpStart.json")
        testValuesOnBranch(HEAD)

        NCube testBranchCube = mutableClient.getCube(HEAD, 'TestBranch')

        Map<String,Object> options = [(SEARCH_INCLUDE_CUBE_DATA):true, (SEARCH_EXACT_MATCH_NAME):true] as Map<String,Object>
        List<NCubeInfoDto> records = mutableClient.search(HEAD, 'TestBranch', null, options)
        assertEquals(1, records.size())
        NCubeInfoDto testBranchInfo = records.first()
        assertEquals(true,testBranchInfo.hasCubeData())
        assertEquals(testBranchCube.sha1(),testBranchInfo.sha1)

        // add bogus sha1 and ensure bytes are still returned
        options[SEARCH_CHECK_SHA1] = 'xx'
        records = mutableClient.search(HEAD, 'TestBranch', null, options)
        assertEquals(1, records.size())
        testBranchInfo = records.first()
        assertEquals(true,testBranchInfo.hasCubeData())
        assertEquals(testBranchCube.sha1(),testBranchInfo.sha1)

        // add matching sha1 and ensure bytes are not returned
        options[SEARCH_CHECK_SHA1] = testBranchCube.sha1()
        records = mutableClient.search(HEAD, 'TestBranch', null, options)
        assertEquals(1, records.size())
        testBranchInfo = records.first()
        assertEquals(false,testBranchInfo.hasCubeData())
    }

    @Test
    void testSearchAdvanced()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json", "basicJumpStart.json", "expressionAxis.json")
        testValuesOnBranch(HEAD)

        Map map = new HashMap()
        map.put(SEARCH_ACTIVE_RECORDS_ONLY, true)

        assertEquals(2, mutableClient.search(HEAD, "Test*", null, map).size())
        assertEquals(1, mutableClient.search(HEAD, "TestBranch", "ZZZ", map).size())
        assertEquals(1, mutableClient.search(HEAD, "*basic*", "input", map).size())
        assertEquals(0, mutableClient.search(HEAD, "*Test*", "input", map).size())
        assertEquals(1, mutableClient.search(HEAD, "*Branch", "ZZZ", map).size())
        assertEquals(2, mutableClient.search(HEAD, null, "ZZZ", map).size())
        assertEquals(2, mutableClient.search(HEAD, "", "ZZZ", map).size())
        assertEquals(2, mutableClient.search(HEAD, "*", "output", map).size())
        assertEquals(0, mutableClient.search(HEAD, "*axis", "input", map).size())
    }

    @Test
    void testSearchWildCardAndBrackets()
    {
        String cubeName = 'bracketsInString'
        preloadCubes(HEAD, cubeName + '.json')

        Map map = [(SEARCH_ACTIVE_RECORDS_ONLY): true]

        NCube cube = mutableClient.getCube(HEAD, cubeName)
        String value = cube.getCell([axis1: 'column1', axis2: 'column2'])
        assertEquals('testValue[A]', value)

        //Test search with content value containing brackets, with or without wildcard
        assertEquals(1, mutableClient.search(HEAD, cubeName, 'testValue[A]', map).size())
        assertEquals(1, mutableClient.search(HEAD, cubeName, 'testValue', map).size())
        assertEquals(1, mutableClient.search(HEAD, cubeName, 'Value[A]', map).size())
        assertEquals(1, mutableClient.search(HEAD, cubeName, '*Value*', map).size())
        assertEquals(1, mutableClient.search(HEAD, cubeName, '*', map).size())
        assertEquals(1, mutableClient.search(HEAD, cubeName, null, map).size())
        assertEquals(0, mutableClient.search(HEAD, cubeName, 'somethingElse', map).size())

        //Test search with cube name pattern, with or without wildcard, not exact match
        assertEquals(1, mutableClient.search(HEAD, '*racketsIn*', null, map).size())
        assertEquals(1, mutableClient.search(HEAD, 'racketsIn', null, map).size())

        //Test search with cube name pattern, with or without wildcard, exact match
        map[SEARCH_EXACT_MATCH_NAME] = true
        assertEquals(1, mutableClient.search(HEAD, cubeName, null, map).size())
        assertEquals(0, mutableClient.search(HEAD, '*racketsIn*', null, map).size())
        assertEquals(0, mutableClient.search(HEAD, 'racketsIn', null, map).size())
    }

    @Test
    void testSearchReferenceAxesContent()
    {
        NCube one = NCubeBuilder.discrete1DAlt
        one.applicationID = ApplicationID.testAppId
        mutableClient.createCube(one)
        assert one.getAxis('state').size() == 2

        Map<String, Object> args = [:]

        ApplicationID appId = ApplicationID.testAppId
        args[REF_TENANT] = appId.tenant
        args[REF_APP] = appId.app
        args[REF_VERSION] = appId.version
        args[REF_STATUS] = appId.status
        args[REF_BRANCH] = appId.branch
        args[REF_CUBE_NAME] = 'SimpleDiscrete'
        args[REF_AXIS_NAME] = 'state'

        // stateSource instead of 'state' to prove the axis on the referring cube does not have to have the same name
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader('Mongo', 'stateSource', args)
        Axis axis = new Axis('stateSource', 1, false, refAxisLoader)
        NCube two = new NCube('Mongo')
        two.applicationID = ApplicationID.testAppId
        two.addAxis(axis)

        two.setCell('a', [stateSource:'OH'] as Map)
        two.setCell('b', [stateSource:'TX'] as Map)

        String json = two.toFormattedJson()
        NCube reload = NCube.fromSimpleJson(json)
        assert reload.numCells == 2
        assert 'a' == reload.getCell([stateSource:'OH'] as Map)
        assert 'b' == reload.getCell([stateSource:'TX'] as Map)
        assert reload.getAxis('stateSource').reference
        mutableClient.createCube(two)

        List<NCubeInfoDto> result = mutableClient.search(ApplicationID.testAppId, 'Mongo', 'OH', [(SEARCH_ACTIVE_RECORDS_ONLY): true])
        assert 1 == result.size()
    }

    @Test
    void testUpdateBranchAfterDelete()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(HEAD)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))

        NCube cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(mutableClient.deleteCubes(BRANCH1, 'TestBranch'))

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())

        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        // cube is deleted
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        // check HEAD hasn't changed.
        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  loads in both TestAge and TestBranch though only TestBranch has changed.
        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNotNull(mutableClient.getCube(HEAD, "TestBranch"))
    }

    @Test
    void testCreateBranchThatAlreadyExists()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        //1) should work
        mutableClient.copyBranch(HEAD, BRANCH1)

        try
		{
            //2) should already be created.
            mutableClient.copyBranch(HEAD, BRANCH1)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'already exists')
        }
    }

    @Test
    void testReleaseCubes()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        NCube cube = ncubeRuntime.getNCubeFromResource(BRANCH1, 'test.branch.2.json')
        assertNotNull(cube)
        mutableClient.updateCube(cube)

        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        testValuesOnBranch(BRANCH1, "FOO")

        int rows = mutableClient.releaseCubes(HEAD, "1.29.0")
        assertEquals(3, rows) // 2 + sys.info = 3

        Object[] versions = ncubeRuntime.getVersions(HEAD.app)
        assert versions.length == 3
        assert versions.contains('0.0.0-SNAPSHOT')
        assert versions.contains('1.29.0-SNAPSHOT')
        assert versions.contains('1.28.0-RELEASE')

        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(HEAD, "TestAge"))
        assertNull(mutableClient.getCube(HEAD, "TestBranch"))

        ApplicationID newBranchSnapshot = BRANCH1.createNewSnapshotId("1.29.0")

        ApplicationID release = HEAD.asRelease()

        testValuesOnBranch(release)
        testValuesOnBranch(newBranchSnapshot, "FOO")

        // test release with no new version
        mutableClient.releaseCubes(HEAD.asVersion('1.29.0'))
        versions = ncubeRuntime.getVersions(HEAD.app)
        assert 3 == versions.length
        assert versions.contains('0.0.0-SNAPSHOT')
        assert versions.contains('1.28.0-RELEASE')
        assert versions.contains('1.29.0-RELEASE')
    }

    @Test
    void testIndividualCallsCanReplaceReleaseCubes()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))
        testValuesOnBranch(HEAD)
        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))
        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        NCube cube = ncubeRuntime.getNCubeFromResource(BRANCH1, 'test.branch.2.json')
        assertNotNull(cube)
        mutableClient.updateCube(cube)

        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        testValuesOnBranch(BRANCH1, "FOO")

        // lock app
        mutableClient.lockApp(HEAD, true)

        // change versions on existing branches
        Object[] branches = mutableClient.getBranches(HEAD)
        for (Object branchObj : branches)
        {
            String branch = branchObj as String
            if (!ApplicationID.HEAD.equalsIgnoreCase(branch))
            {
                ApplicationID branchAppId = HEAD.asBranch(branch)
                mutableClient.moveBranch(branchAppId, '1.29.0')
            }
        }

        // release version
        mutableClient.releaseVersion(HEAD, '1.29.0')

        // copy head forward
        mutableClient.copyBranch(HEAD, HEAD.asVersion('1.29.0'))

        // unlock app
        mutableClient.lockApp(HEAD, false)

        Object[] versions = ncubeRuntime.getVersions(HEAD.app)
        assert versions.length == 3
        assert versions.contains('0.0.0-SNAPSHOT')
        assert versions.contains('1.29.0-SNAPSHOT')
        assert versions.contains('1.28.0-RELEASE')

        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(HEAD, "TestAge"))
        assertNull(mutableClient.getCube(HEAD, "TestBranch"))

        ApplicationID newBranchSnapshot = BRANCH1.createNewSnapshotId("1.29.0")

        ApplicationID release = HEAD.asRelease()

        testValuesOnBranch(release)
        testValuesOnBranch(newBranchSnapshot, "FOO")
    }

    @Test
    void testReleaseCubesWithOpenPullRequests()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, 'test.branch.1.json', 'test.branch.age.1.json')
        assert 3 == mutableClient.copyBranch(HEAD, BRANCH1)

        NCube cube = ncubeRuntime.getNCubeFromResource(BRANCH1, 'test.branch.2.json')
        assertNotNull(cube)
        mutableClient.updateCube(cube)

        // create pull request with version 1.28.0
        String prId = mutableClient.generatePullRequestHash(BRANCH1, mutableClient.getBranchChangesForHead(BRANCH1).toArray())
        Object[] prInfos = mutableClient.pullRequests
        assert 1 == prInfos.size()
        ApplicationID prAppId = (prInfos[0] as Map)[PR_APP] as ApplicationID
        assert HEAD.equalsNotIncludingBranch(prAppId)

        // release 1.28.0
        assert 3 == mutableClient.releaseCubes(HEAD, '1.29.0') // 2 + sys.info = 3

        // test 1.29.0 snapshot still has same values
        ApplicationID newBranchSnapshot = BRANCH1.asVersion('1.29.0')
        ApplicationID newBranchHead = newBranchSnapshot.asHead()
        testValuesOnBranch(newBranchHead)
        testValuesOnBranch(newBranchSnapshot, 'FOO')

        // test PR version updated to version 1.29.0
        prInfos = mutableClient.pullRequests
        assert 1 == prInfos.size()
        prAppId = (prInfos[0] as Map)[PR_APP] as ApplicationID
        assert newBranchSnapshot.equalsNotIncludingBranch(prAppId)

        // test PR merged with version 1.29.0
        mutableClient.mergePullRequest(prId)
        testValuesOnBranch(newBranchHead, 'FOO')
        testValuesOnBranch(newBranchSnapshot, 'FOO')
    }

    @Test
    void testReleaseCubesWithOpenPullRequestsNoNewSnapshot()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, 'test.branch.1.json', 'test.branch.age.1.json')
        assert 3 == mutableClient.copyBranch(HEAD, BRANCH1)

        NCube cube = ncubeRuntime.getNCubeFromResource(BRANCH1, 'test.branch.2.json')
        assertNotNull(cube)
        mutableClient.updateCube(cube)

        // create pull request with version 1.28.0
        mutableClient.generatePullRequestHash(BRANCH1, mutableClient.getBranchChangesForHead(BRANCH1).toArray())
        Object[] prInfos = mutableClient.pullRequests
        assert 1 == prInfos.size()
        ApplicationID prAppId = (prInfos[0] as Map)[PR_APP] as ApplicationID
        assert HEAD.equalsNotIncludingBranch(prAppId)

        // release 1.28.0 with no new snapshot version
        assert 3 == mutableClient.releaseCubes(HEAD) // 2 + sys.info = 3

        // test PR obsoleted
        prInfos = mutableClient.pullRequests
        assert 1 == prInfos.size()
        String prStatus = (prInfos[0] as Map)[PR_STATUS] as String
        assert PR_OBSOLETE == prStatus
    }

    @Test
    void testDuplicateCubeChanges()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        mutableClient.duplicate(HEAD, BRANCH2, "TestBranch", "TestBranch2")
        mutableClient.duplicate(HEAD, BRANCH2, "TestAge", "TestAge")

        // assert HEAD and branch are still there
        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        //  Test with new name.
        NCube cube = mutableClient.getCube(BRANCH2, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = mutableClient.getCube(BRANCH2, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
    }

    @Test
    void testDuplicateCubeGoingToDifferentApp()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))
        assertNull(mutableClient.getCube(appId, "TestBranch"))
        assertNull(mutableClient.getCube(appId, "TestAge"))

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)
        assertNull(mutableClient.getCube(appId, "TestBranch"))
        assertNull(mutableClient.getCube(appId, "TestAge"))

        mutableClient.duplicate(BRANCH1, appId, "TestBranch", "TestBranch")
        mutableClient.duplicate(HEAD, appId, "TestAge", "TestAge")

        // assert HEAD and branch are still there
        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)
        testValuesOnBranch(appId)
    }

    @Test
    void testDuplicateCubeOnDeletedCube()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertTrue(mutableClient.deleteCubes(BRANCH1, ['TestBranch']))

        try
        {
            mutableClient.duplicate(BRANCH1, appId, "TestBranch", "TestBranch")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'Unable to duplicate', 'deleted')
        }
    }

    @Test
    void testRenameCubeOnDeletedCube()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertTrue(mutableClient.deleteCubes(BRANCH1, ['TestBranch']))

        try
        {
            mutableClient.renameCube(BRANCH1, "TestBranch", "Foo")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'Deleted cubes', 'cannot be rename')
        }
    }

    @Test
    void testDuplicateWhenCubeWithNameAlreadyExists()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        try
        {
            mutableClient.duplicate(BRANCH1, BRANCH1, "TestBranch", "TestAge")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'Unable to duplicate', 'already exists')
        }
    }

    @Test
    void testRenameCubeWhenNewNameAlreadyExists()
    {
        ApplicationID head = new ApplicationID(ApplicationID.DEFAULT_TENANT, "test", "1.28.0", ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)

        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(3, mutableClient.copyBranch(head, BRANCH1))

        testValuesOnBranch(head)
        testValuesOnBranch(BRANCH1)

        assert mutableClient.renameCube(BRANCH1, "TestBranch", "TestAge")
    }

    @Test
    void testRenameCubeWithHeadHavingCubeAAndCubeBDeleted()
	{
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(HEAD, "*").size())

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, "*").size())

        assertTrue(mutableClient.deleteCubes(BRANCH1, 'TestBranch'))
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertTrue(mutableClient.deleteCubes(BRANCH1, 'TestAge'))
        assertEquals(2, getDeletedCubesFromDatabase(BRANCH1, "*").size())

        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 2
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertNull(mutableClient.getCube(HEAD, "TestBranch"))
        assertNull(mutableClient.getCube(HEAD, "TestAge"))

        assertEquals(2, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(2, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, getDeletedCubesFromDatabase(HEAD, "*").size())

        mutableClient.restoreCubes(BRANCH1, "TestBranch")
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestAge"))
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestAge"))

        result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertNull(mutableClient.getCube(HEAD, "TestBranch"))
        assertNotNull(mutableClient.getCube(HEAD, "TestAge"))

        assertTrue(mutableClient.renameCube(BRANCH1, "TestAge", "TestBranch"))
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testRenameCubeWithBothCubesCreatedOnBranch()
    {
        preloadCubes(BRANCH1, "test.branch.1.json", "test.branch.age.1.json")

        assertNull(mutableClient.getCube(HEAD, "TestBranch"))
        assertNull(mutableClient.getCube(HEAD, "TestAge"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestAge"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestAge"))

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 2
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertNull(mutableClient.getCube(HEAD, "TestBranch"))
        assertNotNull(mutableClient.getCube(HEAD, "TestBranch2"))
        assertNotNull(mutableClient.getCube(HEAD, "TestAge"))

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch2").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(HEAD, "*").size())

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch2", "TestBranch"))

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestAge"))

        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)
        result = mutableClient.commitBranch(BRANCH1, dtos)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(2, mutableClient.getRevisionHistory(HEAD, "TestBranch2").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(HEAD, "*").size())
    }

    @Test
    void testRenameCubeWhenNewNameAlreadyExistsButIsInactive()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        mutableClient.deleteCubes(BRANCH1, 'TestAge')

        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())

        //  cube is deleted so won't throw exception
        mutableClient.renameCube(BRANCH1, "TestBranch", "TestAge")

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
    }

    @Test
    void testDuplicateCubeWhenNewNameAlreadyExistsButIsInactive()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        mutableClient.deleteCubes(BRANCH1, 'TestAge')

        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())

        //  cube is deleted so won't throw exception
        mutableClient.duplicate(BRANCH1, BRANCH1, "TestBranch", "TestAge")

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, "*").size())
    }

    @Test
    void testRenameAndThenRenameAgainThenRollback()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertEquals(2, mutableClient.getBranchChangesForHead(BRANCH1).size())

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch2", "TestBranch"))
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
        assertEquals(0, mutableClient.rollbackBranch(BRANCH1, dtos))

        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
    }

    @Test
    void testRenameAndThenRollback()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertEquals(2, mutableClient.getBranchChangesForHead(BRANCH1).size())

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.size())

        assertEquals(2, mutableClient.rollbackBranch(BRANCH1, ["TestBranch", "TestBranch2"] as Object[]))

        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
    }

    @Test
    void testRenameAndThenCommitAndThenRenameAgainWithCommit()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestAge"))

        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.size())

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos.toArray())
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertNull(mutableClient.getCube(HEAD, "TestBranch"))
        assertNotNull(mutableClient.getCube(HEAD, "TestBranch2"))
        assertNotNull(mutableClient.getCube(HEAD, "TestAge"))

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch2", "TestBranch"))

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestAge"))

        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        dtos = mutableClient.getBranchChangesForHead(BRANCH1)

        assertEquals(2, dtos.size())
        result = mutableClient.commitBranch(BRANCH1, dtos.toArray())
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertNull(mutableClient.getCube(HEAD, "TestBranch2"))
        assertNotNull(mutableClient.getCube(HEAD, "TestBranch"))
        assertNotNull(mutableClient.getCube(HEAD, "TestAge"))
    }

    @Test
    void testRenameAndThenRenameAgainThenCommit()
    {
        ApplicationID head = new ApplicationID(ApplicationID.DEFAULT_TENANT, "test", "1.28.0", ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
        ApplicationID branch = new ApplicationID(ApplicationID.DEFAULT_TENANT, "test", "1.28.0", ReleaseStatus.SNAPSHOT.name(), "FOO")

        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")
        testValuesOnBranch(head)

        assertEquals(3, mutableClient.copyBranch(head, branch))

        testValuesOnBranch(head)
        testValuesOnBranch(branch)

        assertTrue(mutableClient.renameCube(branch, "TestBranch", "TestBranch2"))

        assertNull(mutableClient.getCube(branch, "TestBranch"))
        assertNotNull(mutableClient.getCube(branch, "TestBranch2"))
        assertNotNull(mutableClient.getCube(branch, "TestAge"))

        assertEquals(2, mutableClient.getRevisionHistory(branch, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(branch, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(branch, "*").size())
        Object[] dtos = mutableClient.getBranchChangesForHead(branch)
        assertEquals(2, dtos.length)

        assertTrue(mutableClient.renameCube(branch, "TestBranch2", "TestBranch"))

        assertNull(mutableClient.getCube(branch, "TestBranch2"))
        assertNotNull(mutableClient.getCube(branch, "TestBranch"))
        assertNotNull(mutableClient.getCube(branch, "TestAge"))

        assertEquals(2, mutableClient.getRevisionHistory(branch, "TestBranch2").size())
        assertEquals(3, mutableClient.getRevisionHistory(branch, "TestBranch").size())
        dtos = mutableClient.getBranchChangesForHead(branch)
        assertEquals(0, dtos.length)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty

        assertNotNull(mutableClient.getCube(branch, "TestBranch"))
        assertNull(mutableClient.getCube(branch, "TestBranch2"))
    }

    @Test
    void testRenameAndThenRenameAgainThenCommitWhenNotCreatedFromBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(BRANCH1, "test.branch.1.json", "test.branch.age.1.json")
        testValuesOnBranch(BRANCH1)

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertEquals(2, mutableClient.getBranchChangesForHead(BRANCH1).size())

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch2", "TestBranch"))
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch2"))

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 2
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertNotNull(mutableClient.getCube(HEAD, "TestBranch"))
        assertNotNull(mutableClient.getCube(HEAD, "TestBranch"))
        assertNull(mutableClient.getCube(HEAD, "TestBranch2"))
    }

    @Test
    void testRenameCubeBasicCase()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")
        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch2"))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        testValuesOnBranch(HEAD)

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertNotNull(mutableClient.getCube(HEAD, "TestBranch2"))
        assertNotNull(mutableClient.getCube(HEAD, "TestAge"))

        //  Test with new name.
        NCube cube = mutableClient.getCube(BRANCH1, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = mutableClient.getCube(BRANCH1, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
    }

    @Test
    void testRenameCubeBasicCaseWithNoHead()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(BRANCH1, "test.branch.1.json", "test.branch.age.1.json")
        testValuesOnBranch(BRANCH1)

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.size())

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.size())

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos as Object[])
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 2
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        //  Test with new name.
        NCube cube = mutableClient.getCube(BRANCH1, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = mutableClient.getCube(BRANCH1, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        cube = mutableClient.getCube(HEAD, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = mutableClient.getCube(HEAD, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
        assertNull(mutableClient.getCube(HEAD, "TestBranch"))
    }

    @Test
    void testRenameCubeFunctionality()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        try
        {
            mutableClient.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        try
        {
            mutableClient.getRevisionHistory(BRANCH1, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, null).size())


        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())

        try
        {
            mutableClient.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.size())

        assertEquals(2, mutableClient.rollbackBranch(BRANCH1, [dtos.get(0).name, dtos.get(1).name] as Object[]))

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, null).size())

        assertEquals(0, mutableClient.getBranchChangesForHead(BRANCH1).size())

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        try
        {
            mutableClient.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        assert 2 == mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size()

        assertTrue(mutableClient.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, null).size())

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(4, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())

        try
        {
            mutableClient.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch2").size())

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(4, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestBranch2").size())

        assertEquals(1, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, null).size())
    }

    @Test
    void testDuplicateCubeFunctionality()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        try
		{
            mutableClient.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        try
		{
            mutableClient.getRevisionHistory(BRANCH1, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH2, null).size())

        mutableClient.duplicate(BRANCH1, BRANCH2, "TestBranch", "TestBranch2")
        mutableClient.duplicate(BRANCH1, BRANCH2, "TestAge", "TestAge")

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH2, null).size())


        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH2, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH2, "TestBranch2").size())

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        try
		{
            mutableClient.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        try
		{
            mutableClient.getRevisionHistory(BRANCH2, "TestBranch")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.size())

        assertEquals(1, mutableClient.rollbackBranch(BRANCH2, dtos.first().name))

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())

        assertEquals(0, mutableClient.getBranchChangesForHead(BRANCH1).size())

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        try
		{
            mutableClient.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        try
		{
            mutableClient.getRevisionHistory(BRANCH1, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        mutableClient.duplicate(BRANCH1, BRANCH2, "TestBranch", "TestBranch2")

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH2, null).size())

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH2, "TestAge").size())
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH2, "TestBranch2").size())

        try
		{
            mutableClient.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        Map<String, Object> result = mutableClient.commitBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch2").size())

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())

        assertEquals(1, mutableClient.getRevisionHistory(BRANCH2, "TestAge").size())
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH2, "TestBranch2").size())

        try
        {
            mutableClient.getRevisionHistory(BRANCH2, "TestBranch")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'revision history', 'not exist')
        }

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH2, null).size())

        ApplicationID newAppAppId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test2', '1.0.0', ReleaseStatus.SNAPSHOT.toString(), 'foo')
        mutableClient.duplicate(BRANCH1, newAppAppId, 'TestBranch', 'TestBranch')
        assert 1 == mutableClient.getBranchChangesForHead(newAppAppId).size()
    }

    @Test
    void testDuplicateCubeWithNonExistentSource()
    {
        try
        {
            mutableClient.duplicate(HEAD, BRANCH1, "foo", "bar")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'not duplicate cube because', 'does not exist')
        }
    }

    @Test
    void testDuplicateCubeWhenTargetExists()
    {
        preloadCubes(HEAD, "test.branch.1.json")
        mutableClient.copyBranch(HEAD, BRANCH1)

        try
        {
            mutableClient.duplicate(HEAD, BRANCH1, "TestBranch", "TestBranch")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'Unable to duplicate', 'already exists')
        }
    }

    @Test
    void testOverwriteHeadWhenHeadDoesntExist()
    {
        preloadCubes(HEAD, "test.branch.1.json")
        mutableClient.copyBranch(HEAD, BRANCH1)
        mutableClient.deleteCubes(BRANCH1, ['TestBranch'])

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        try
        {
            mutableClient.duplicate(HEAD, BRANCH1, "TestBranch", "TestBranch")
            assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))
            assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains("Unable to duplicate"))
            assertTrue(e.message.contains("already exists"))
        }
    }

    @Test
    void testDuplicateCubeWhenSourceCubeIsADeletedCube()
    {
        preloadCubes(HEAD, "test.branch.1.json")
        mutableClient.copyBranch(HEAD, BRANCH1)
        mutableClient.deleteCubes(BRANCH1, ['TestBranch'])

        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        mutableClient.duplicate(HEAD, BRANCH1, "TestBranch", "TestBranch")
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertEquals(3, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
    }

    @Test
    void testDeleteCubeAndThenDeleteCubeAgain()
    {
        preloadCubes(BRANCH1, "test.branch.1.json")
        assertNotNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        assertTrue(mutableClient.deleteCubes(BRANCH1, 'TestBranch'))
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))

        try
        {
            mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'does not exist')
        }
    }

    private static void testValuesOnBranch(ApplicationID appId, String code1 = "ABC", String code2 = "youth") {
        NCube cube = mutableClient.getCube(appId, "TestBranch")
        assertEquals(code1, cube.getCell(["Code": -10]))
        cube = mutableClient.getCube(appId, "TestAge")
        assertEquals(code2, cube.getCell(["Code": 10]))
    }

    @Test
    void testCommitBranchWithItemCreatedLocallyAndOnHead()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2,  mutableClient.copyBranch(HEAD, BRANCH2))

        createCubeFromResource(BRANCH2, "test.branch.age.2.json")

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)
        mutableClient.commitBranch(BRANCH2, dtos)

        // Commit to branch 2 causes 1 pending update for BRANCH1
        List<NCubeInfoDto> dtos2 = mutableClient.getHeadChangesForBranch(BRANCH1)
        assert dtos2.size() == 1
        assert dtos2[0].name == 'TestAge'

        createCubeFromResource(BRANCH1, "test.branch.age.1.json")

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        try
        {
            mutableClient.commitBranch(BRANCH1, dtos)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testCommitWithCubeChangedButMatchesHead()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        NCube cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        assertEquals(2,  mutableClient.copyBranch(HEAD, BRANCH2))

        cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = ncubeRuntime.getNCubeFromResource(BRANCH2, "test.branch.2.json")
        mutableClient.updateCube(cube)

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)

        mutableClient.commitBranch(BRANCH2, dtos)

        cube = ncubeRuntime.getNCubeFromResource(BRANCH1, "test.branch.2.json")
        mutableClient.updateCube(cube)

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        List dtos2 = mutableClient.getHeadChangesForBranch(BRANCH2)
        assert dtos2.empty    // Nothing for BRANCH2 because cube matched HEAD already
    }

    @Test
    void testCommitFailsWithoutPermissions()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        String origUser = mutableClient.userId
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        // cube is updated by someone with access
        String cubeName = 'TestBranch'
        NCube testBranchCube = mutableClient.getCube(BRANCH1, cubeName)
        testBranchCube.setCell('AAA', [Code: 15])
        mutableClient.updateCube(testBranchCube)

        // set permission on cube to deny commit for normal user
        ApplicationID branchBoot = BRANCH1.asVersion('0.0.0')
        NCube sysPermissions = mutableClient.getCube(branchBoot, SYS_PERMISSIONS)
        sysPermissions.addColumn(AXIS_RESOURCE, cubeName)
        sysPermissions.setCell(true, [(AXIS_RESOURCE): cubeName, (AXIS_ROLE): ROLE_USER, (AXIS_ACTION): Action.READ.lower()])
        mutableClient.updateCube(sysPermissions)
        List<NCubeInfoDto> dtos = ncubeRuntime.search(branchBoot, SYS_PERMISSIONS, null, null)
        assert dtos.size() == 1
        NCubeInfoDto permissionDto = dtos[0]
        mutableClient.commitBranch(branchBoot, permissionDto)

        // set testUser to have user role on branch
        NCube sysBranchPermissions = mutableClient.getCube(branchBoot, SYS_BRANCH_PERMISSIONS)
        String testUser = 'testUser'
        sysBranchPermissions.addColumn(AXIS_USER, testUser)
        sysBranchPermissions.setCell(true, [(AXIS_USER): testUser])
        mutableClient.updateCube(sysBranchPermissions)

        // impersonate testUser, who shouldn't be able to commit the changed cube
        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        manager.userId = testUser
        String prId = mutableClient.generatePullRequestHash(BRANCH1) // should still be able to make PR

        try
        {
            mutableClient.mergePullRequest(prId)
            fail()
        }
        catch (BranchMergeException e)
        {
            Map data = e.errors
            assert (data[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (data[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (data[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (data[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (data[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
        finally
        {
            manager.userId = origUser
        }
    }

    /***** tests for commit and update from our cube matrix *****/

    @Test
    void testCommitConsumerNoCubeHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerNoCubeHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerNoCubeHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerNoCubeHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerNoCubeHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerNoCubeHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerAddHeadNoChange()
	{
        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerAddHeadNoChange()
	{
        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerAddHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerAddHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerAddHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerAddHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerAddHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerAddHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerAddHeadUpdateMergeable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerAddHeadUpdateMergeable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerAddHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerAddHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerAddHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerAddHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerNoChangeHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)
        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerNoChangeHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerNoChangeHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerNoChangeHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerNoChangeHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerNoChangeHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerNoChangeHeadUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerNoChangeHeadUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerUpdateHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerUpdateHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerUpdateHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerUpdateHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerUpdateHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerUpdateHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testConsumerUpdateCubeDeleteCubePullFromHead()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerUpdateHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerUpdateHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerUpdateHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerUpdateHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerUpdateHeadUpdateMergeable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerUpdateHeadUpdateMergeable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerUpdateHeadUpdateTwice()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        //consumer change
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        mutableClient.updateCube(consumerCube)

        //producer change and commit
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        //consumer update
        mutableClient.updateBranch(BRANCH1)

        //producer change and commit
        producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('CCC', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        //consumer commit
        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerUpdateHeadUpdateTwice()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        //consumer change
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        mutableClient.updateCube(consumerCube)

        //producer change and commit
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        //consumer update
        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        //producer change and commit
        producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('CCC', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerUpdateHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerUpdateHeadUpdateSame()
	{
        //Includes additional checks to verify changed flag on update, commit and updateBranch
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        NCubeInfoDto producerDto = mutableClient.search(BRANCH2, 'TestBranch', null, null)[0]
        assert producerDto.changed
        mutableClient.commitBranch(BRANCH2)
        producerDto = mutableClient.search(BRANCH2, 'TestBranch', null, null)[0]
        assert !producerDto.changed

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)
        NCubeInfoDto consumerDto = mutableClient.search(BRANCH1, 'TestBranch', null, null)[0]
        assert consumerDto.changed

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
        consumerDto = mutableClient.search(BRANCH1, 'TestBranch', null, null)[0]
        assert !consumerDto.changed
    }

    @Test
    void testCommitConsumerUpdateHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerDeleteHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerDeleteHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerDeleteHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerDeleteHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerDeleteHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerDeleteHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerDeleteHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerDeleteHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerDeleteHeadUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerDeleteHeadUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerDeleteHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerDeleteHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerUpdateDeleteHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateDeleteHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerUpdateDeleteHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateDeleteHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerUpdateDeleteHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateDeleteHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerUpdateDeleteHeadUpdateMergable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : 15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateDeleteHeadUpdateMergable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : 15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerRestoreHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerRestoreHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerRestoreHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerRestoreHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerRestoreHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerRestoreHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerRestoreHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerRestoreHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerRestoreHeadRestoreUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerRestoreHeadRestoreUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerRestoreHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerRestoreHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerRestoreUpdateHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerRestoreUpdateHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')
        mutableClient.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerRestoreUpdateHeadRestoreUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        assert mutableClient.getBranchChangesForHead(BRANCH1).empty
    }

    @Test
    void testUpdateConsumerRestoreUpdateHeadRestoreUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testCommitConsumerRestoreUpdateHeadRestoreUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateConsumerRestoreUpdateHeadRestoreUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testCommitConsumerRestoreUpdateHeadRestoreUpdateMergable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testUpdateConsumerRestoreUpdateHeadRestoreUpdateMergable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        mutableClient.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        mutableClient.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        mutableClient.updateCube(consumerCube)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    @Test
    void testMergedCubesIfPossibleResultsInNewCubeMatchingHead() {
        ApplicationID teamBranch = BRANCH1.asBranch('TEAM')
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)
        mutableClient.copyBranch(HEAD, teamBranch)
        // all 3 branches on HEAD

        // branch1 makes change
        NCube branch1Cube = mutableClient.getCube(BRANCH1, 'TestBranch')
        branch1Cube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(branch1Cube)

        // branch2 makes different change
        NCube branch2Cube = mutableClient.getCube(BRANCH2, 'TestBranch')
        branch2Cube.setCell('DDD', [Code : 0])
        mutableClient.updateCube(branch2Cube)

        // team branch takes branch2 change and commits
        NCube teamCube = mutableClient.getCube(teamBranch, 'TestBranch')
        teamCube.setCell('DDD', [Code : 0])
        mutableClient.updateCube(teamCube)
        mutableClient.commitBranch(teamBranch)

        // branch1 updates then commits change
        mutableClient.updateBranch(BRANCH1)
        mutableClient.commitBranch(BRANCH1)

        NCubeInfoDto head = mutableClient.search(HEAD, 'TestBranch', null, null).first()

        // branch2 updates, should have all changes and match HEAD
        Map result = mutableClient.updateBranch(BRANCH2)
        NCubeInfoDto dto = (result[mutableClient.BRANCH_UPDATES] as List).first() as NCubeInfoDto
        assert dto.headSha1 == head.sha1
        assert dto.sha1 == dto.headSha1
        assert !dto.changed // should no longer be changed after matching HEAD
    }

    @Test
    void testCommitWithReject()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(consumerCube)

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())

        consumerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(consumerCube)

        try
        {
            mutableClient.commitBranch(BRANCH1, dtos.toArray())
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testMergeBetweenBranchesAddEmptyBranchCase()
    {
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        mutableClient.commitBranch(BRANCH2)

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 1
        NCubeInfoDto dto = dtos[0]
        assert dto.name == 'TestAge'
    }

    @Test
    void testMergeBetweenBranchesAddToEstablishedBranch()
    {
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1, true)
        preloadCubes(BRANCH2, "test.branch.2.json")

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 1
        NCubeInfoDto dto = dtos[0]
        assert dto.name == 'TestBranch'
    }

    @Test
    void testMergeBetweenBranchesUpdate()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 1
        NCubeInfoDto dto = dtos[0]
        assert dto.notes.contains('updated')
    }

    @Test
    void testMergeBetweenBranchesDelete()
    {
        preloadCubes(BRANCH2, "test.branch.1.json", 'test.branch.age.1.json')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1, true)
        mutableClient.deleteCubes(BRANCH2, ['TestBranch'])

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 1
        NCubeInfoDto dto = dtos[0]
        assert dto.name == 'TestBranch'
        assert dto.notes.contains('deleted')
    }

    @Test
    void testMergeBetweenBranchesRestore()
    {
        // Both branches have two n-cubes
        preloadCubes(BRANCH2, "test.branch.1.json", 'test.branch.age.1.json')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1, true)

        // 'TestBranch' deleted from both branches
        mutableClient.deleteCubes(BRANCH2, 'TestBranch')
        mutableClient.commitBranch(BRANCH2)
        mutableClient.updateBranch(BRANCH1)

        // 'TestBranch' restored in BRANCH2
        mutableClient.restoreCubes(BRANCH2, 'TestBranch')

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 1
        NCubeInfoDto dto = dtos[0]
        assert dto.name == 'TestBranch'
        assert dto.notes.contains('restored')
    }

    @Test
    void testMergeBetweenBranchesNoCubesInYoBranch()
    {
        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.empty
    }

    @Test
    void testUpdateWithReject()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        List<NCubeInfoDto> dtos = mutableClient.getHeadChangesForBranch(BRANCH1)
        assertEquals(1, dtos.size())

        producerCube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1, dtos)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1

        dtos = mutableClient.getHeadChangesForBranch(BRANCH1)
        assertEquals(1, dtos.size())

        result = mutableClient.updateBranch(BRANCH1, dtos)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty
    }

    /***** End tests for commit and update from cube test matrix *****/

    @Test
    void testAddDifferentColumnsWithDefault()
    {
        preloadCubes(BRANCH2, "testCube6.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        //consumer add column
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestCube')
        consumerCube.addColumn('Gender', 'Dog')
        mutableClient.updateCube(consumerCube)

        //producer add column and commit
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestCube')
        producerCube.addColumn('Gender', 'Cat')
        producerCube.setCell('calico', [Gender: 'Cat'])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        //consumer update
        mutableClient.getHeadChangesForBranch(BRANCH1)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        consumerCube = mutableClient.getCube(BRANCH1, 'TestCube')
        Axis genderAxis = consumerCube.getAxis('Gender')
        assert genderAxis.findColumn('Male')
        assert genderAxis.findColumn('Female')
        assert genderAxis.findColumn('Dog')
        assert genderAxis.findColumn('Cat')
        assert genderAxis.hasDefaultColumn()
        assert genderAxis.size() == 5
        assert consumerCube.getCell([Gender: 'Cat']) == 'calico'
    }

    @Test
    void testAddDifferentColumns()
    {
        preloadCubes(BRANCH2, "testCube6.json")
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestCube')
        producerCube.deleteColumn('Gender', null)
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        //consumer add column
        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestCube')
        consumerCube.addColumn('Gender', 'Dog')
        mutableClient.updateCube(consumerCube)

        //producer add column and commit
        producerCube = mutableClient.getCube(BRANCH2, 'TestCube')
        producerCube.addColumn('Gender', 'Cat')
        producerCube.setCell('calico', [Gender: 'Cat'])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        //consumer update
        mutableClient.getHeadChangesForBranch(BRANCH1)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        consumerCube = mutableClient.getCube(BRANCH1, 'TestCube')
        Axis genderAxis = consumerCube.getAxis('Gender')
        assert genderAxis.findColumn('Male')
        assert genderAxis.findColumn('Female')
        assert genderAxis.findColumn('Dog')
        assert genderAxis.findColumn('Cat')
        assert !genderAxis.hasDefaultColumn()
        assert genderAxis.size() == 4
        assert consumerCube.getCell([Gender: 'Cat']) == 'calico'
    }

    @Test
    void testRemoveAndAddDefaultColumn()
    {
        preloadCubes(BRANCH2, "testCube6.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        //producer remove default column and commit
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestCube')
        producerCube.deleteColumn('Gender', null)
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        //consumer update
        mutableClient.getHeadChangesForBranch(BRANCH1)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'TestCube')
        Axis genderAxis = consumerCube.getAxis('Gender')
        assert genderAxis.findColumn('Male')
        assert genderAxis.findColumn('Female')
        assert !genderAxis.hasDefaultColumn()
        assert genderAxis.size() == 2

        //producer add default column and cell and commit
        producerCube.addColumn('Gender', null)
        producerCube.setCell('it', [Gender: null])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(BRANCH2)

        //consumer update
        mutableClient.getHeadChangesForBranch(BRANCH1)
        result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        consumerCube = mutableClient.getCube(BRANCH1, 'TestCube')
        genderAxis = consumerCube.getAxis('Gender')
        assert genderAxis.findColumn('Male')
        assert genderAxis.findColumn('Female')
        assert genderAxis.hasDefaultColumn()
        assert genderAxis.size() == 3
        assert consumerCube.getCell([Gender: null]) == 'it'
    }

    @Test
    void testRestoreFromChangedCubeInOtherBranch()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        //producer change cube
        NCube producerCube = mutableClient.getCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(producerCube)

        //consumer delete cube
        mutableClient.deleteCubes(BRANCH1, 'TestBranch')

        //consumer update from producer
        mutableClient.acceptTheirs(BRANCH1, ['TestBranch'] as Object[], BRANCH2.branch)

        //consumer open commit modal
        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assert dtos.length == 1
        NCubeInfoDto dto = dtos[0] as NCubeInfoDto
        assert dto.changed
    }

    @Test
    void testConflictOverwriteBranch()
	{
        NCube cube = createCubeFromResource(BRANCH2, "test.branch.2.json")
        assertEquals("EF05D590F7F2DA94B880EA9F4730EB6D53031B54", cube.sha1())

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH2)
        mutableClient.commitBranch(BRANCH2, dtos)

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals("EF05D590F7F2DA94B880EA9F4730EB6D53031B54", cube.sha1())

        cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        createCubeFromResource(BRANCH1, "test.branch.1.json")

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals("D71891F6BD1CE8644F6BF5E2E553E2ECA652E785", cube.sha1())
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        List<NCubeInfoDto> dtos2 = mutableClient.getHeadChangesForBranch(BRANCH1)
        assert dtos2[0].name == 'TestBranch'
        assert dtos2[0].changeType == ChangeType.CONFLICT.code
        assert dtos2[0].sha1 != cube.sha1()

        try
        {
            mutableClient.commitBranch(BRANCH1, dtos)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }

        assertEquals(1, mutableClient.acceptTheirs(BRANCH1, 'TestBranch'))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testConflictOverwriteBranchWithPreexistingCube()
    {
        preloadCubes(HEAD, "test.branch.3.json")

        mutableClient.copyBranch(HEAD, BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)

        NCube cube = ncubeRuntime.getNCubeFromResource(BRANCH2, "test.branch.2.json")
        mutableClient.updateCube(cube)
        assertEquals("EF05D590F7F2DA94B880EA9F4730EB6D53031B54", cube.sha1())

        mutableClient.commitBranch(BRANCH2)

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals("EF05D590F7F2DA94B880EA9F4730EB6D53031B54", cube.sha1())

        cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = ncubeRuntime.getNCubeFromResource(BRANCH1, "test.branch.1.json")
        mutableClient.updateCube(cube)

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals("D71891F6BD1CE8644F6BF5E2E553E2ECA652E785", cube.sha1())
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }

        assertEquals(1, mutableClient.acceptTheirs(BRANCH1, 'TestBranch'))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testMergeAcceptTheirsTheirSha1IsHeadsSha1OursIsBehindHeadsSha1AndNotChanged()
    {
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)

        NCube branch1Cube = mutableClient.getCube(BRANCH1, 'TestBranch')
        branch1Cube.setCell('DDD', [Code:0])
        mutableClient.updateCube(branch1Cube)
        mutableClient.commitBranch(BRANCH1)
        // HEAD SHA1 = A
        // BRANCH1 SHA1 = A, HEAD_SHA1 = A
        // BRANCH2 SHA1 = B, HEAD_SHA1 = B

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert !branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        NCubeInfoDto headDto = mutableClient.search(HEAD, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        // HEAD SHA1 = A
        // BRANCH1 SHA1 = A, HEAD_SHA1 = A
        // BRANCH2 SHA1 = A, HEAD_SHA1 = A
        assert branch1Dto.headSha1 == branch1Dto.sha1
        assert branch1Dto.headSha1 == headDto.sha1
        assert branch2Dto.sha1 == headDto.sha1
        assert !branch2Dto.changed
    }

    @Test
    void testMergeAcceptTheirsTheirSha1IsHeadsSha1OursIsBehindHeadsSha1AndChanged()
    {
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)

        NCube branch1Cube = mutableClient.getCube(BRANCH1, 'TestBranch')
        branch1Cube.setCell('DDD', [Code:0])
        mutableClient.updateCube(branch1Cube)
        mutableClient.commitBranch(BRANCH1)
        // HEAD SHA1 = A
        // BRANCH1 SHA1 = A, HEAD_SHA1 = A

        NCube branch2Cube = mutableClient.getCube(BRANCH2, 'TestBranch')
        branch2Cube.setCell('AAA', [Code:-15])
        mutableClient.updateCube(branch2Cube)
        // BRANCH2 SHA1 = X, HEAD_SHA1 = B

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        NCubeInfoDto headDto = mutableClient.search(HEAD, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        // HEAD SHA1 = A
        // BRANCH1 SHA1 = A, HEAD_SHA1 = A
        // BRANCH2 SHA1 = A, HEAD_SHA1 = A
        assert branch1Dto.headSha1 == branch1Dto.sha1
        assert branch1Dto.headSha1 == headDto.sha1
        assert branch2Dto.sha1 == headDto.sha1
        assert !branch2Dto.changed
    }

    @Test
    void testMergeAcceptTheirsTheirSha1MatchesHeadSha1ButBehindHeadsSha1OursIsNotChanged()
    {
        ApplicationID producer = BRANCH1.asBranch('producer')
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)
        mutableClient.copyBranch(HEAD, producer)

        NCube producerCube = mutableClient.getCube(producer, 'TestBranch')
        producerCube.setCell('DDD', [Code:0])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(producer)
        // HEAD SHA1 = B

        mutableClient.updateBranch(BRANCH1)
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B

        producerCube.setCell('GGG', [Code:-10])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(producer)
        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B
        // BRANCH2 SHA1 = C, HEAD_SHA1 = C

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert !branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        NCubeInfoDto headDto = mutableClient.search(HEAD, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B
        // BRANCH2 SHA1 = B, HEAD_SHA1 = C
        assert branch1Dto.headSha1 == branch1Dto.sha1
        assert branch1Dto.headSha1 != headDto.sha1
        assert branch2Dto.sha1 == branch1Dto.sha1
        assert branch2Dto.headSha1 != branch2Dto.sha1
        assert branch2Dto.changed
    }

    @Test
    void testMergeAcceptTheirsTheirSha1MatchesHeadSha1ButBehindHeadsSha1OursIsChanged()
    {
        ApplicationID producer = BRANCH1.asBranch('producer')
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)
        mutableClient.copyBranch(HEAD, producer)

        NCube producerCube = mutableClient.getCube(producer, 'TestBranch')
        producerCube.setCell('DDD', [Code:0])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(producer)
        // HEAD SHA1 = B

        mutableClient.updateBranch(BRANCH1)
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B

        producerCube.setCell('GGG', [Code:-10])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(producer)
        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B

        NCube branch2Cube = mutableClient.getCube(BRANCH2, 'TestBranch')
        branch2Cube.setCell('AAA', [Code:-15])
        mutableClient.updateCube(branch2Cube)
        // BRANCH2 SHA1 = X, HEAD_SHA1 = C

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        NCubeInfoDto headDto = mutableClient.search(HEAD, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B
        // BRANCH2 SHA1 = B, HEAD_SHA1 = C
        assert branch1Dto.headSha1 == branch1Dto.sha1
        assert branch1Dto.headSha1 != headDto.sha1
        assert branch2Dto.sha1 == branch1Dto.sha1
        assert branch2Dto.headSha1 != branch2Dto.sha1
        assert branch2Dto.changed
    }

    @Test
    void testMergeAcceptTheirsTheirHeadSha1BehindHeadsSha1OurHeadSha1MatchesHeadsSha1AndIsNotChanged()
    {
        ApplicationID producer = BRANCH1.asBranch('producer')
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)
        mutableClient.copyBranch(HEAD, producer)

        NCube producerCube = mutableClient.getCube(producer, 'TestBranch')
        producerCube.setCell('DDD', [Code:0])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(producer)
        // HEAD SHA1 = B

        mutableClient.updateBranch(BRANCH1)
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B

        producerCube.setCell('GGG', [Code:-10])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(producer)
        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B

        mutableClient.updateBranch(BRANCH2)
        // BRANCH2 SHA1 = A, HEAD_SHA1 = A

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert !branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        NCubeInfoDto headDto = mutableClient.search(HEAD, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B
        // BRANCH2 SHA1 = B, HEAD_SHA1 = A
        assert branch1Dto.headSha1 == branch1Dto.sha1
        assert branch1Dto.headSha1 != headDto.sha1
        assert branch2Dto.sha1 == branch1Dto.sha1
        assert branch2Dto.headSha1 != branch2Dto.sha1
        assert branch2Dto.headSha1 == headDto.sha1
        assert branch2Dto.changed
    }

    @Test
    void testMergeAcceptTheirsTheirHeadSha1BehindHeadsSha1OurHeadSha1MatchesHeadsSha1AndIsChanged()
    {
        ApplicationID producer = BRANCH1.asBranch('producer')
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)
        mutableClient.copyBranch(HEAD, producer)

        NCube producerCube = mutableClient.getCube(producer, 'TestBranch')
        producerCube.setCell('DDD', [Code:0])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(producer)
        // HEAD SHA1 = B

        mutableClient.updateBranch(BRANCH1)
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B

        producerCube.setCell('GGG', [Code:-10])
        mutableClient.updateCube(producerCube)
        mutableClient.commitBranch(producer)
        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B

        mutableClient.updateBranch(BRANCH2)
        // BRANCH2 SHA1 = A, HEAD_SHA1 = A

        NCube branch2Cube = mutableClient.getCube(BRANCH2, 'TestBranch')
        branch2Cube.setCell('AAA', [Code:-15])
        mutableClient.updateCube(branch2Cube)
        // BRANCH2 SHA1 = X, HEAD_SHA1 = A

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        NCubeInfoDto headDto = mutableClient.search(HEAD, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = B
        // BRANCH2 SHA1 = B, HEAD_SHA1 = A
        assert branch1Dto.headSha1 == branch1Dto.sha1
        assert branch1Dto.headSha1 != headDto.sha1
        assert branch2Dto.sha1 == branch1Dto.sha1
        assert branch2Dto.headSha1 != branch2Dto.sha1
        assert branch2Dto.headSha1 == headDto.sha1
        assert branch2Dto.changed
    }

    @Test
    void testMergeAcceptTheirsTheirsIsChangedAndOursIsNotChanged()
    {
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)

        NCube branch1Cube = mutableClient.getCube(BRANCH1, 'TestBranch')
        branch1Cube.setCell('DDD', [Code:0])
        mutableClient.updateCube(branch1Cube)
        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = A
        // BRANCH2 SHA1 = A, HEAD_SHA1 = A

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert !branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        NCubeInfoDto headDto = mutableClient.search(HEAD, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = A
        // BRANCH2 SHA1 = B, HEAD_SHA1 = A
        assert branch1Dto.headSha1 != branch1Dto.sha1
        assert branch1Dto.headSha1 == headDto.sha1
        assert branch2Dto.headSha1 == headDto.sha1
        assert branch2Dto.sha1 == branch1Dto.sha1
        assert branch2Dto.changed
    }

    @Test
    void testMergeAcceptTheirsTheirsIsChangedAndOursIsChanged()
    {
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)

        NCube branch1Cube = mutableClient.getCube(BRANCH1, 'TestBranch')
        branch1Cube.setCell('DDD', [Code:0])
        mutableClient.updateCube(branch1Cube)
        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = A

        NCube branch2Cube = mutableClient.getCube(BRANCH2, 'TestBranch')
        branch2Cube.setCell('AAA', [Code:-15])
        mutableClient.updateCube(branch2Cube)
        // BRANCH2 SHA1 = X, HEAD_SHA1 = A

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        NCubeInfoDto headDto = mutableClient.search(HEAD, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        // HEAD SHA1 = A
        // BRANCH1 SHA1 = B, HEAD_SHA1 = A
        // BRANCH2 SHA1 = B, HEAD_SHA1 = A
        assert branch1Dto.headSha1 != branch1Dto.sha1
        assert branch1Dto.headSha1 == headDto.sha1
        assert branch2Dto.headSha1 == headDto.sha1
        assert branch2Dto.sha1 == branch1Dto.sha1
        assert branch2Dto.changed
    }

    @Test
    void testMergeAcceptTheirsTheirsIsDeleted()
    {
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)

        mutableClient.deleteCubes(BRANCH1, ['TestBranch'] as Object[])
        mutableClient.commitBranch(BRANCH1)

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert !branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        assert branch2Dto.sha1 == branch1Dto.sha1
        assert branch2Dto.changed
        assert Integer.parseInt(branch2Dto.revision) < 0
    }

    @Test
    void testMergeAcceptTheirsTheirsIsRestored()
    {
        preloadCubes(BRANCH1, 'test.branch.1.json')
        mutableClient.commitBranch(BRANCH1)
        mutableClient.deleteCubes(BRANCH1, ['TestBranch'] as Object[])
        mutableClient.commitBranch(BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)

        mutableClient.restoreCubes(BRANCH1, ['TestBranch'] as Object[])
        mutableClient.commitBranch(BRANCH1)

        NCubeInfoDto branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()
        assert !branch2Dto.changed

        assert 1 == mutableClient.acceptTheirs(BRANCH2, ['TestBranch'] as Object[], BRANCH1.branch)

        NCubeInfoDto branch1Dto = mutableClient.search(BRANCH1, 'TestBranch', null, null).first()
        branch2Dto = mutableClient.search(BRANCH2, 'TestBranch', null, null).first()

        assert branch2Dto.sha1 == branch1Dto.sha1
        assert branch2Dto.changed
        assert Integer.parseInt(branch2Dto.revision) > 0
    }

    @Test
    void testConflictAcceptMine()
    {
        createCubeFromResource(BRANCH2, "test.branch.2.json")
        mutableClient.commitBranch(BRANCH2)

        NCube cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        createCubeFromResource(BRANCH1, "test.branch.1.json")

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }

        List<NCubeInfoDto> infos = mutableClient.search(BRANCH1, 'TestBranch', null, null)
        NCubeInfoDto infoDto = infos[0]
        assert infoDto.headSha1 == null

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
        infos = mutableClient.search(HEAD, 'TestBranch', null, null)
        infoDto = infos[0]
        String saveHeadSha1 = infoDto.sha1
        assert saveHeadSha1 != null

        assertEquals(1, mutableClient.acceptMine(BRANCH1, "TestBranch"))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))
        infos = mutableClient.search(BRANCH1, 'TestBranch', null, null)
        infoDto = infos[0]
        assert saveHeadSha1 == infoDto.headSha1

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
        infos = mutableClient.search(HEAD, 'TestBranch', null, null)
        infoDto = infos[0]
        assert saveHeadSha1 == infoDto.sha1
        assert infoDto.headSha1 == null // HEAD always has a null headSha1
    }

    @Test
    void testConflictAcceptMineWithPreexistingCube()
    {
        preloadCubes(HEAD, "test.branch.3.json")

        mutableClient.copyBranch(HEAD, BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)

        NCube cube = ncubeRuntime.getNCubeFromResource(BRANCH2, "test.branch.2.json")
        mutableClient.updateCube(cube)
        mutableClient.commitBranch(BRANCH2)

        cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = ncubeRuntime.getNCubeFromResource(BRANCH1, "test.branch.1.json")
        mutableClient.updateCube(cube)

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals("D71891F6BD1CE8644F6BF5E2E553E2ECA652E785", cube.sha1())
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }

        List<NCubeInfoDto> infos = mutableClient.search(BRANCH1, 'TestBranch', null, null)
        NCubeInfoDto infoDto = infos[0]
        assert infoDto.headSha1 != null
        String saveOldHeadSha1 = infoDto.headSha1

        infos = mutableClient.search(HEAD, 'TestBranch', null, null)
        infoDto = infos[0]
        assert infoDto.headSha1 == null
        String saveHeadSha1 = infoDto.sha1
        assert saveHeadSha1 != null

        assertEquals(1, mutableClient.acceptMine(BRANCH1, "TestBranch"))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))
        infos = mutableClient.search(BRANCH1, 'TestBranch', null, null)
        infoDto = infos[0]
        assert infoDto.headSha1 == saveHeadSha1
        assert infoDto.headSha1 != saveOldHeadSha1

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
        infos = mutableClient.search(HEAD, 'TestBranch', null, null)
        infoDto = infos[0]
        assert infoDto.headSha1 == null
        assert infoDto.sha1 == saveHeadSha1
    }

    @Test
    void testMergeAcceptMineWhenBranchDoesNotExist()
    {
        try
        {
            mutableClient.acceptMine(appId, "TestBranch")
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'failed', 'update', 'branch cube', 'not exist')
        }
    }

    @Test
    void testMergeAcceptMineWhenHEADdoesNotExist()
    {
        try
        {
            preloadCubes(BRANCH1, "test.branch.1.json")
            mutableClient.acceptMine(appId, "TestBranch")
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'failed', 'update', 'branch cube', 'not exist')
        }
    }

    @Test
    void testOverwriteBranchCubeWhenBranchDoesNotExist()
    {
        try
		{
            mutableClient.acceptTheirs(appId, "TestBranch")
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'failed to overwrite', 'does not exist')
        }
    }

    @Test
    void testOverwriteBranchCubeWhenHEADDoesNotExist()
    {
        try
		{
            preloadCubes(BRANCH1, "test.branch.1.json")
            mutableClient.acceptTheirs(appId, "TestBranch")
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'failed to overwrite', 'does not exist')
        }
    }

    @Test
    void testCommitBranchWithExtendedMerge2()
    {
        preloadCubes(HEAD, "merge1.json")

        NCube headCube = mutableClient.getCube(HEAD, 'merge1')

        Map coord = [row:1, column:'A']
        assert "1" == headCube.getCell(coord)

        coord = [row:2, column:'B']
        assert 2 == headCube.getCell(coord)

        coord = [row:3, column:'C']
        assert 3.14 == headCube.getCell(coord)

        coord = [row:4, column:'D']
        assert 6.28 == headCube.getCell(coord)

        coord = [row:5, column:'E']
        assert headCube.containsCell(coord)

        assert headCube.numCells == 5


        mutableClient.copyBranch(HEAD, BRANCH1)
        mutableClient.copyBranch(HEAD, BRANCH2)

        NCube cube1 = mutableClient.getCube(BRANCH1, "merge1")
        cube1.setCell(3.14159, [row:3, column:'C'])
        mutableClient.updateCube(cube1)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        headCube = mutableClient.getCube(HEAD, "merge1")
        coord = [row:3, column:'C']
        assert 3.14159 == headCube.getCell(coord)

        NCube cube2 = mutableClient.getCube(BRANCH2, "merge1")
        cube2.setCell('foo', [row:4, column:'D'])
        cube2.removeCell([row:5, column:'E'])
        mutableClient.updateCube(cube2)

        result = mutableClient.commitBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        headCube = mutableClient.getCube(HEAD, "merge1")

        coord = [row:1, column:'A']
        assert "1" == headCube.getCell(coord)

        coord = [row:2, column:'B']
        assert 2 == headCube.getCell(coord)

        coord = [row:3, column:'C']
        assert 3.14159 == headCube.getCell(coord)

        coord = [row:4, column:'D']
        assert 'foo' == headCube.getCell(coord)

        coord = [row:5, column:'E']
        assert !headCube.containsCell(coord)

        assert headCube.numCells == 4
    }

    @Test
    void testMergeAcceptMineException()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH2))

        createCubeFromResource(BRANCH2, "test.branch.age.2.json")

        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.size())
        mutableClient.commitBranch(BRANCH2, dtos as Object[])

        createCubeFromResource(BRANCH1, "test.branch.age.1.json")

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())
        String newSha1 = dtos[0].sha1

        try
        {
            mutableClient.commitBranch(BRANCH1, dtos as Object[])
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }

        dtos = mutableClient.search(HEAD, "TestAge", null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        String sha1 = dtos[0].sha1
        assertNotEquals(sha1, newSha1)

        mutableClient.acceptMine(BRANCH1, "TestAge")

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        String branchHeadSha1 = dtos[0].headSha1
        assertEquals(1, dtos.size())

        dtos = mutableClient.search(HEAD, "TestAge", null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(branchHeadSha1, dtos[0].sha1)
    }

    @Test
    void testMergeDeltas()
    {
        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH1)
        mutableClient.deleteBranch(BRANCH1)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH2))

        NCube cube = mutableClient.getCube(BRANCH1, 'TestBranch')
        NCube cube2 = mutableClient.getCube(BRANCH2, 'TestBranch')

        // make changes
        cube.addColumn('Code', 20)
        cube.deleteColumn('Code', -15)
        cube.setCell('JKL', [Code : 15])
        cube.removeCell([Code : -10])
        cube.defaultCellValue = 'AAA'
        cube.addMetaProperties([key : 'value' as Object])
        cube.getAxis('Code').addMetaProperties([key : 'value' as Object])
        cube.getAxis('Code').findColumn(0).addMetaProperties([key : 'value' as Object])
        Column column = cube.getAxis('Code').findColumn(10)
        cube.updateColumn(column.id, 9)

        // save changes
        mutableClient.updateCube(cube)

        // get our delta list, which should include all the changes we made
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(cube, cube2)
        assertEquals(9, deltas.size())

        // merge deltas into BRANCH2
        cube2.mergeDeltas(deltas)
        mutableClient.updateCube(cube2)

        mutableClient.commitBranch(BRANCH2)

        // verify cube2 is the same as cube
        assertEquals(5, cube2.getAxis('Code').columns.size())
        assertEquals('JKL', cube2.getCell([Code : 15]))     // Newly set value
        assertEquals('AAA', cube2.getCell([Code : -10]))    // default cell value
        assertEquals('AAA', cube2.defaultCellValue)         // default cell value
        assertEquals(1, cube2.metaProperties.size())
        assertEquals(1, cube2.getAxis('Code').metaProperties.size())
        assertEquals(1, cube2.getAxis('Code').findColumn(0).metaProperties.size())
        assert cube2.getAxis('Code').findColumn(9)
        assert !cube2.getAxis('Code').findColumn(10)
    }

    @Test
    void testMergeNewColumnWithMetaPropertyOnBranch()
    {
        String cubeName = 'TestBranch'
        String axisName = 'Code'
        Integer colVal = 20
        String cubeMetaKey = 'cubekey'
        String cubeMetaVal = 'cubeval'
        String colMetaKey = 'key'
        String colMetaVal = 'val'

        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH1)
        mutableClient.deleteBranch(BRANCH1)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH2))

        NCube cube = mutableClient.getCube(BRANCH1, cubeName)
        NCube cube2 = mutableClient.getCube(BRANCH2, cubeName)

        // make changes
        cube.addColumn(axisName, colVal)
        Column column = cube.getAxis(axisName).findColumn(colVal)
        column.addMetaProperties([(colMetaKey): colMetaVal as Object])
        mutableClient.updateCube(cube)

        // make change in other cube / head
        cube2.addMetaProperties([(cubeMetaKey): cubeMetaVal as Object])
        mutableClient.updateCube(cube2)
        mutableClient.commitBranch(cube2.applicationID)

        // update cube1 from head
        mutableClient.updateBranch(cube.applicationID)
        cube = mutableClient.getCube(cube.applicationID, cube.name)

        // verify metaproperty on new column stays (bug fix)
        Column newColumn = cube.getAxis(axisName).findColumn(colVal)
        assert cubeMetaVal == cube.metaProperties[cubeMetaKey]
        assert newColumn
        assert colMetaVal == newColumn.metaProperties[colMetaKey]
    }

    @Test
    void testAddAxis()
    {
        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH1)
        NCube headCube = mutableClient.getCube(HEAD, 'TestBranch')
        NCube cube = mutableClient.getCube(BRANCH1, 'TestBranch')

        // test for add axis
        cube.addAxis(new Axis('Axis', AxisType.DISCRETE, AxisValueType.STRING, false, Axis.SORTED, 2))
        mutableClient.updateCube(cube)
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(cube, headCube)
        assertEquals(1, deltas.size())
        cube = mutableClient.mergeDeltas(BRANCH1, 'TestBranch', deltas)
        assert cube.getAxis('Axis') != null // Verify axis added

        // test for delete axis
        mutableClient.updateCube(cube)
        mutableClient.commitBranch(BRANCH1)
        headCube = mutableClient.getCube(HEAD, 'TestBranch')
        cube.deleteAxis('Axis')
        mutableClient.updateCube(cube)
        deltas = DeltaProcessor.getDeltaDescription(cube, headCube)
        assertEquals(1, deltas.size())
        cube = mutableClient.mergeDeltas(BRANCH1, 'TestBranch', deltas)
        assert null == cube.getAxis('Axis')
    }

    @Test
    void testMergeOverwriteBranchWithItemsCreatedInBothPlaces()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(BRANCH1, "test.branch.1.json")
        mutableClient.commitBranch(BRANCH1)
        mutableClient.deleteBranch(BRANCH1)

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2,  mutableClient.copyBranch(HEAD, BRANCH2))

        createCubeFromResource(BRANCH2, "test.branch.age.2.json")

        Map<String, Object> result = mutableClient.commitBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        createCubeFromResource(BRANCH1, "test.branch.age.1.json")

        result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1

        mutableClient.acceptTheirs(BRANCH1, "TestAge")
        assertEquals(0, mutableClient.getBranchChangesForHead(BRANCH1).size())
    }

    @Test
    void testCommitBranchWithItemThatWasChangedOnHeadAndInBranchButHasNonconflictingRemovals()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2,  mutableClient.copyBranch(HEAD, BRANCH2))

        NCube cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())
        mutableClient.updateCube(cube)

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)

        mutableClient.commitBranch(BRANCH2, dtos)

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : -10.0])
        assertEquals(2, cube.cellMap.size())
        mutableClient.updateCube(cube)

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        mutableClient.commitBranch(BRANCH1, dtos)

        cube = mutableClient.getCube(HEAD, "TestBranch")
        // cube has default of 'zzz' for non-existing cells
        assertEquals('ZZZ', cube.getCell([Code : -10.0]))
        assertEquals('ZZZ', cube.getCell([Code : 10.0]))
    }

    @Test
    void testCommitBranchWithItemThatWasChangedOnHeadAndInBranchAndConflict()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2,  mutableClient.copyBranch(HEAD, BRANCH2))

        NCube cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.setCell(18L, [Code : -10.0])
        assertEquals(3, cube.cellMap.size())
        mutableClient.updateCube(cube)
        mutableClient.commitBranch(BRANCH2)

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.setCell(19L, [Code : -10.0])
        assertEquals(3, cube.cellMap.size())
        mutableClient.updateCube(cube)

        try
        {
            mutableClient.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_DELETES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Collection).empty
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
        }
    }

    @Test
    void testUpdateBranchWithItemThatWasChangedOnHeadAndInBranchWithNonConflictingChanges()
    {
        preloadCubes(HEAD, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2,  mutableClient.copyBranch(HEAD, BRANCH2))

        NCube cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code: 10.0])
        assertEquals(2, cube.cellMap.size())
        cube.setCell(18L, [Code: 15])
        mutableClient.updateCube(cube)
        mutableClient.commitBranch(BRANCH2)

        cube = mutableClient.getCube(HEAD, "TestBranch")

        assertEquals('ZZZ', cube.getCell([Code: -15]))
        assertEquals('ABC', cube.getCell([Code: -10]))
        assertEquals(18L, cube.getCell([Code: 15]))
        assertEquals('ZZZ', cube.getCell([Code: 10]))
        assertEquals(3, cube.cellMap.size())


        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code: -10])
        assertEquals(2, cube.cellMap.size())
        cube.setCell(-19L, [Code: -15])
        assertEquals(3, cube.cellMap.size())
        mutableClient.updateCube(cube)

        mutableClient.updateBranch(BRANCH1)
        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(-19L, cube.getCell([Code: -15]))
        assertEquals('ZZZ', cube.getCell([Code: -10]))
        assertEquals(18L, cube.getCell([Code: 15]))
        assertEquals('ZZZ', cube.getCell([Code: 10]))
        assertEquals(3, cube.cellMap.size())
    }

    @Test
    void testUpdateBranchWithItemThatWasChangedOnHeadAndInBranchWithConflict()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, mutableClient.copyBranch(HEAD, BRANCH1))
        assertEquals(2,  mutableClient.copyBranch(HEAD, BRANCH2))

        NCube cube = mutableClient.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : 10])
        assertEquals(2, cube.cellMap.size())
        mutableClient.updateCube(cube)

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)

        mutableClient.commitBranch(BRANCH2, dtos)

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.setCell('AAA', [Code : 10])
        assertEquals(3, cube.cellMap.size())
        mutableClient.updateCube(cube)

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).empty
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).size() == 1
    }

    @Test
    void testGetBranchChanges()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(HEAD)

        // pre-branch, cubes don't exist
        assertNull(mutableClient.getCube(BRANCH1, "TestBranch"))
        assertNull(mutableClient.getCube(BRANCH1, "TestAge"))

        NCube cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(3, mutableClient.copyBranch(HEAD, BRANCH1))

        Object[] dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10]))

        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10]))

        // edit branch cube
        cube.removeCell([Code : 10])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10]))

        // update the new edited cube.
        assertTrue(mutableClient.updateCube(cube))

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        // commit the branch
        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10]))

        // check HEAD hasn't changed.
        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10]))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = mutableClient.commitBranch(BRANCH1, dtos)
        assert (result[mutableClient.BRANCH_ADDS] as Collection).empty
        assert (result[mutableClient.BRANCH_DELETES] as Collection).empty
        assert (result[mutableClient.BRANCH_UPDATES] as Collection).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Collection).empty
        assert (result[mutableClient.BRANCH_REJECTS] as Collection).empty

        assertEquals(2, mutableClient.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, mutableClient.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, mutableClient.getRevisionHistory(BRANCH1, "TestAge").size())

        // both should be updated now.
        cube = mutableClient.getCube(BRANCH1, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10]))
        cube = mutableClient.getCube(HEAD, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10]))

        dtos = mutableClient.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testBootstrapWithOverrides()
	{
        ApplicationID id = ncubeRuntime.getBootVersion(ApplicationID.DEFAULT_TENANT, 'example')
        assert id.tenant == ApplicationID.DEFAULT_TENANT
        assert id.app == 'example'
        assert id.version == '0.0.0'
        assert id.status == ReleaseStatus.SNAPSHOT.name()

        preloadCubes(id, "sys.bootstrap.user.overloaded.json")

        NCube cube = mutableClient.getCube(id, 'sys.bootstrap')

        // force reload of system params, you wouldn't usually do this because it wouldn't be thread safe this way.
        testClient.clearSysParams()

        // ensure properties are cleared (if empty, this would load the environment version of NCUBE_PARAMS)
        System.setProperty('NCUBE_PARAMS', '{"foo":"bar"}')

        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, 'UD.REF.APP', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'HEAD'), cube.getCell([env:'DEV']))
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, 'UD.REF.APP', '1.25.0', ReleaseStatus.RELEASE.name(), 'HEAD'), cube.getCell([env:'PROD']))
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, 'UD.REF.APP', '1.29.0', ReleaseStatus.SNAPSHOT.name(), 'baz'), cube.getCell([env:'SAND']))

        // force reload of system params, you wouldn't usually do this because it wouldn't be thread safe this way.
        testClient.clearSysParams()
        System.setProperty("NCUBE_PARAMS", '{"status":"RELEASE", "app":"UD", "tenant":"foo", "branch":"bar"}')
        assertEquals(new ApplicationID('foo', 'UD', '1.28.0', ReleaseStatus.RELEASE.name(), 'bar'), cube.getCell([env:'DEV']))
        assertEquals(new ApplicationID('foo', 'UD', '1.25.0', ReleaseStatus.RELEASE.name(), 'bar'), cube.getCell([env:'PROD']))
        assertEquals(new ApplicationID('foo', 'UD', '1.29.0', ReleaseStatus.RELEASE.name(), 'bar'), cube.getCell([env:'SAND']))

        // force reload of system params, you wouldn't usually do this because it wouldn't be thread safe this way.
        testClient.clearSysParams()
        System.setProperty("NCUBE_PARAMS", '{"branch":"bar"}')
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, 'UD.REF.APP', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'bar'), cube.getCell([env:'DEV']))
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, 'UD.REF.APP', '1.25.0', ReleaseStatus.RELEASE.name(), 'bar'), cube.getCell([env:'PROD']))
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, 'UD.REF.APP', '1.29.0', ReleaseStatus.SNAPSHOT.name(), 'bar'), cube.getCell([env:'SAND']))
    }

    @Test
    void testUserOverloadedClassPath()
	{
        preloadCubes(appId, "sys.classpath.user.overloaded.json", "sys.versions.json")

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        testClient.clearSysParams()
        // Check DEV
        NCube cube = mutableClient.getCube(appId, "sys.classpath")
        // ensure properties are cleared.
        System.setProperty('NCUBE_PARAMS', '{"foo", "bar"}')

        CdnClassLoader devLoader = cube.getCell([env:"DEV"])
        assertEquals('https://www.foo.com/tests/ncube/cp1/public/', devLoader.URLs[0].toString())
        assertEquals('https://www.foo.com/tests/ncube/cp1/private/', devLoader.URLs[1].toString())
        assertEquals('https://www.foo.com/tests/ncube/cp1/private/groovy/', devLoader.URLs[2].toString())

        // Check INT
        CdnClassLoader intLoader = cube.getCell([env:"INT"])
        assertEquals('https://www.foo.com/tests/ncube/cp2/public/', intLoader.URLs[0].toString())
        assertEquals('https://www.foo.com/tests/ncube/cp2/private/', intLoader.URLs[1].toString())
        assertEquals('https://www.foo.com/tests/ncube/cp2/private/groovy/', intLoader.URLs[2].toString())

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        testClient.clearSysParams()
        // Check with overload
        System.setProperty("NCUBE_PARAMS", '{"cpBase":"file://C:/Development/"}')

        // This test does not actually use this file://C:/Dev... path.  I run these tests on my Mac all the time - JTD.
        // int loader is not marked as cached so we recreate this one each time.
        CdnClassLoader differentIntLoader = cube.getCell([env:"INT"])
        assertNotSame(intLoader, differentIntLoader)
        assertEquals('file://C:/Development/public/', differentIntLoader.URLs[0].toString())
        assertEquals('file://C:/Development/private/', differentIntLoader.URLs[1].toString())
        assertEquals('file://C:/Development/private/groovy/', differentIntLoader.URLs[2].toString())

        // devLoader is marked as cached so we would get the same one until we clear the cache.
        URLClassLoader devLoaderAgain = cube.getCell([env:"DEV"])
        assertSame(devLoader, devLoaderAgain)

        assertNotEquals('file://C:/Development/public/', devLoaderAgain.URLs[0].toString())
        assertNotEquals('file://C:/Development/private/', devLoaderAgain.URLs[1].toString())
        assertNotEquals('file://C:/Development/private/groovy/', devLoaderAgain.URLs[2].toString())

        //  force cube clear so it will auto next time we get cube
        ncubeRuntime.clearCache(appId)
        cube = mutableClient.getCube(appId, "sys.classpath")
        devLoaderAgain = cube.getCell([env:"DEV"])

        assertEquals('file://C:/Development/public/', devLoaderAgain.URLs[0].toString())
        assertEquals('file://C:/Development/private/', devLoaderAgain.URLs[1].toString())
        assertEquals('file://C:/Development/private/groovy/', devLoaderAgain.URLs[2].toString())

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        testClient.clearSysParams()
        // Check version overload only
        System.setProperty("NCUBE_PARAMS", '{"version":"1.28.0"}')
        // SAND hasn't been loaded yet so it should give us updated values based on the system params.
        URLClassLoader loader = cube.getCell([env:"SAND"])
        assertEquals('https://www.foo.com/1.28.0/public/', loader.URLs[0].toString())
        assertEquals('https://www.foo.com/1.28.0/private/', loader.URLs[1].toString())
        assertEquals('https://www.foo.com/1.28.0/private/groovy/', loader.URLs[2].toString())
    }

    @Test
    void testSystemParamsOverloads()
	{
        preloadCubes(appId, "sys.classpath.system.params.user.overloaded.json", "sys.versions.2.json", "sys.resources.base.url.json")

        // force reload of system params, you wouldn't usually do this because it wouldn't be thread safe this way.
        testClient.clearSysParams()

        // Check DEV
        NCube cube = mutableClient.getCube(appId, "sys.classpath")
        // ensure properties are cleared.
        System.setProperty('NCUBE_PARAMS', '{"foo", "bar"}')

        CdnClassLoader devLoader = cube.getCell([env:"DEV"])
        assertEquals("${baseRemoteUrl}/foo/1.31.0-SNAPSHOT/public/".toString(), devLoader.URLs[0].toString())
        assertEquals("${baseRemoteUrl}/foo/1.31.0-SNAPSHOT/private/".toString(), devLoader.URLs[1].toString())
        assertEquals("${baseRemoteUrl}/foo/1.31.0-SNAPSHOT/private/groovy/".toString(), devLoader.URLs[2].toString())

        // Check INT
        CdnClassLoader intLoader = cube.getCell([env:"INT"])
        assertEquals("${baseRemoteUrl}/foo/1.31.0-SNAPSHOT/public/".toString(), intLoader.URLs[0].toString())
        assertEquals("${baseRemoteUrl}/foo/1.31.0-SNAPSHOT/private/".toString(), intLoader.URLs[1].toString())
        assertEquals("${baseRemoteUrl}/foo/1.31.0-SNAPSHOT/private/groovy/".toString(), intLoader.URLs[2].toString())

        // Check with overload
        cube = mutableClient.getCube(appId, "sys.classpath")
        System.setProperty("NCUBE_PARAMS", '{"cpBase":"file://C:/Development/"}')

        // int loader is not marked as cached so we recreate this one each time.
        testClient.clearSysParams()
        CdnClassLoader differentIntLoader = cube.getCell([env:"INT"])

        assertNotSame(intLoader, differentIntLoader)
        assertEquals('file://C:/Development/public/', differentIntLoader.URLs[0].toString())
        assertEquals('file://C:/Development/private/', differentIntLoader.URLs[1].toString())
        assertEquals('file://C:/Development/private/groovy/', differentIntLoader.URLs[2].toString())

        // devLoader is marked as cached so we would get the same one until we clear the cache.
        URLClassLoader devLoaderAgain = cube.getCell([env:"DEV"])
        assertSame(devLoader, devLoaderAgain)

        assertNotEquals('file://C:/Development/public/', devLoaderAgain.URLs[0].toString())
        assertNotEquals('file://C:/Development/private/', devLoaderAgain.URLs[1].toString())
        assertNotEquals('file://C:/Development/private/groovy/', devLoaderAgain.URLs[2].toString())

        //  force cube clear so it will auto next time we get cube
        ncubeRuntime.clearCache(appId)
        cube = mutableClient.getCube(appId, "sys.classpath")
        devLoaderAgain = cube.getCell([env:"DEV"])

        assertEquals('file://C:/Development/public/', devLoaderAgain.URLs[0].toString())
        assertEquals('file://C:/Development/private/', devLoaderAgain.URLs[1].toString())
        assertEquals('file://C:/Development/private/groovy/', devLoaderAgain.URLs[2].toString())

        // Check version overload only
        ncubeRuntime.clearCache(appId)
        testClient.clearSysParams()
        System.setProperty("NCUBE_PARAMS", '{"version":"1.28.0"}')
        // SAND hasn't been loaded yet so it should give us updated values based on the system params.
        URLClassLoader loader = cube.getCell([env:"SAND"])
        assertEquals("${baseRemoteUrl}/foo/1.28.0/public/".toString(), loader.URLs[0].toString())
        assertEquals("${baseRemoteUrl}/foo/1.28.0/private/".toString(), loader.URLs[1].toString())
        assertEquals("${baseRemoteUrl}/foo/1.28.0/private/groovy/".toString(), loader.URLs[2].toString())
    }

    @Test
    void testClearCache()
    {
        preloadCubes(appId, "sys.classpath.cedar.json", "cedar.hello.json")

        Map input = new HashMap()
        NCube cube = mutableClient.getCube(appId, 'hello')
        Object out = cube.getCell(input)
        assertEquals('Hello, world.', out)
        ncubeRuntime.clearCache(appId)

        cube = mutableClient.getCube(appId, 'hello')
        out = cube.getCell(input)
        assertEquals('Hello, world.', out)
    }

    @Test
    void testMultiTenantApplicationIdBootstrap()
    {
        preloadCubes(appId, "sys.bootstrap.multi.api.json", "sys.bootstrap.version.json")

        def input = [:]
        input.env = "SAND"

        NCube cube = mutableClient.getCube(appId, 'sys.bootstrap')
        Map<String, ApplicationID> map = cube.getCell(input) as Map
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, "APP", "1.15.0", ReleaseStatus.SNAPSHOT.name(), ApplicationID.TEST_BRANCH), map.get("A"))
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, "APP", "1.19.0", ReleaseStatus.SNAPSHOT.name(), ApplicationID.TEST_BRANCH), map.get("B"))
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, "APP", "1.28.0", ReleaseStatus.SNAPSHOT.name(), ApplicationID.TEST_BRANCH), map.get("C"))

        input.env = "INT"
        map = cube.getCell(input) as Map

        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, "APP", "1.25.0", "RELEASE", ApplicationID.TEST_BRANCH), map.get("A"))
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, "APP", "1.26.0", "RELEASE", ApplicationID.TEST_BRANCH), map.get("B"))
        assertEquals(new ApplicationID(ApplicationID.DEFAULT_TENANT, "APP", "1.27.0", "RELEASE", ApplicationID.TEST_BRANCH), map.get("C"))
    }

    @Test
    void testGetAppIdFromBootstrapCube()
    {
        ApplicationID zero = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'TEST', '0.0.0', ReleaseStatus.SNAPSHOT.name(), ncubeRuntime.systemParams[NCUBE_PARAMS_BRANCH] as String)
        preloadCubes(zero, "sys.bootstrap.test.1.json")
        ApplicationID appId = ncubeRuntime.getApplicationID(ApplicationID.DEFAULT_TENANT, 'TEST', null)

        assertEquals(ApplicationID.DEFAULT_TENANT, appId.tenant)
        assertEquals('TEST', appId.app)
        assertEquals('1.28.0', appId.version)
        assertEquals(ReleaseStatus.RELEASE.name(), appId.status)
        assertEquals('HEAD', appId.branch)
    }

    @Test
    void testGetBootstrapVersion()
    {
        System.setProperty("NCUBE_PARAMS", '{}')
        testClient.clearSysParams()

        ApplicationID id = ncubeRuntime.getBootVersion('foo', 'bar')
        assertEquals 'foo', id.tenant
        assertEquals 'bar', id.app
        assertEquals '0.0.0', id.version
        assertEquals 'SNAPSHOT', id.status
        assertEquals 'HEAD', id.branch

        System.setProperty("NCUBE_PARAMS", '{"branch":"qux"}')
        testClient.clearSysParams()

        id = ncubeRuntime.getBootVersion('foo', 'bar')
        assertEquals 'foo', id.tenant
        assertEquals 'bar', id.app
        assertEquals '0.0.0', id.version
        assertEquals 'SNAPSHOT', id.status
        assertEquals 'qux', id.branch
    }

    @Test
    void testGetReferenceAxes()
    {
        ApplicationID appId = ApplicationID.testAppId
        NCube one = NCubeBuilder.discrete1DAlt
        one.applicationID = appId
        mutableClient.createCube(one)
        assert one.getAxis('state').size() == 2

        Map<String, Object> args = [:]
        args[REF_TENANT] = appId.tenant
        args[REF_APP] = appId.app
        args[REF_VERSION] = appId.version
        args[REF_STATUS] = appId.status
        args[REF_BRANCH] = appId.branch
        args[REF_CUBE_NAME] = 'SimpleDiscrete'
        args[REF_AXIS_NAME] = 'state'

        // stateSource instead of 'state' to prove the axis on the referring cube does not have to have the same name
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader('Mongo', 'stateSource', args)
        Axis axis = new Axis('stateSource', 1, false, refAxisLoader)
        NCube two = new NCube('Mongo')
        two.applicationID = appId
        two.addAxis(axis)

        two.setCell('a', [stateSource:'OH'] as Map)
        two.setCell('b', [stateSource:'TX'] as Map)

        String json = two.toFormattedJson()
        NCube reload = NCube.fromSimpleJson(json)
        assert reload.numCells == 2
        assert 'a' == reload.getCell([stateSource:'OH'] as Map)
        assert 'b' == reload.getCell([stateSource:'TX'] as Map)
        assert reload.getAxis('stateSource').reference
        mutableClient.createCube(two)

        List<AxisRef> axisRefs = mutableClient.getReferenceAxes(appId)
        assert axisRefs.size() == 1
        AxisRef axisRef = axisRefs[0]
        assert 'stateSource' == axisRef.srcAxisName
        assert appId.app == axisRef.destApp
        assert appId.version == axisRef.destVersion
        assert appId.status == axisRef.destStatus
        assert appId.branch == axisRef.destBranch
        assert 'SimpleDiscrete' == axisRef.destCubeName
        assert 'state' == axisRef.destAxisName

        try
        {
            mutableClient.commitBranch(appId)
            fail()
        }
        catch(IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'not performed', 'snapshot')
        }
    }

    private static void setUpReferenceCubeForAxisReferenceTests()
    {
        ApplicationID firstReleaseAppId = ApplicationID.testAppId.asVersion('1.0.0')
        ApplicationID secondReleaseAppId = ApplicationID.testAppId.asVersion('2.0.0')
        ApplicationID snapshotAppId = ApplicationID.testAppId.asVersion('3.0.0')
        NCube one = NCubeBuilder.discrete1DAlt
        one.applicationID = firstReleaseAppId
        mutableClient.createCube(one)
        assert one.getAxis('state').size() == 2
        mutableClient.commitBranch(firstReleaseAppId)
        mutableClient.releaseCubes(firstReleaseAppId, secondReleaseAppId.version)
        mutableClient.releaseCubes(secondReleaseAppId, snapshotAppId.version)
    }

    @Test
    void testMultipleReferenceAxisVersionPerCommit()
    {
        setUpReferenceCubeForAxisReferenceTests()
        ApplicationID firstReleaseAppId = ApplicationID.testAppId.asVersion('1.0.0')
        ApplicationID secondReleaseAppId = ApplicationID.testAppId.asVersion('2.0.0')
        ApplicationID snapshotAppId = ApplicationID.testAppId.asVersion('3.0.0')

        Map<String, Object> args = [:]
        args[REF_TENANT] = firstReleaseAppId.tenant
        args[REF_APP] = firstReleaseAppId.app
        args[REF_VERSION] = firstReleaseAppId.version
        args[REF_STATUS] = ReleaseStatus.RELEASE.name()
        args[REF_BRANCH] = ApplicationID.HEAD
        args[REF_CUBE_NAME] = 'SimpleDiscrete'
        args[REF_AXIS_NAME] = 'state'

        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader('Mongo', 'stateSource1', args)
        Axis axis = new Axis('stateSource1', 1, false, refAxisLoader)
        NCube two = new NCube('Mongo')
        two.applicationID = snapshotAppId
        two.addAxis(axis)
        args[REF_VERSION] = secondReleaseAppId.version
        refAxisLoader = new ReferenceAxisLoader('Mongo', 'stateSource2', args)
        axis = new Axis('stateSource2', 2, false, refAxisLoader)
        axis.findColumn('TX').setMetaProperty('baz', 'quux')
        axis.setMetaProperty('ear', 'hear')
        two.addAxis(axis)
        mutableClient.createCube(two)

        List<AxisRef> axisRefs = mutableClient.getReferenceAxes(snapshotAppId)
        assert axisRefs.size() == 2
        assert axisRefs[0].destVersion != axisRefs[1].destVersion

        try
        {
            mutableClient.commitBranch(snapshotAppId)
            fail()
        }
        catch(IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'not performed', 'versions')
        }

        try
        { // make sure going around commitBranch doesn't work (bugfix)
            String prId = mutableClient.generatePullRequestHash(snapshotAppId, mutableClient.getBranchChangesForHead(snapshotAppId).toArray())
            mutableClient.mergePullRequest(prId)
            fail()
        }
        catch(IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'not performed', 'versions')
        }
    }

    @Test
    void testMultipleReferenceAxisVersionPerApp()
    {
        setUpReferenceCubeForAxisReferenceTests()
        ApplicationID firstReleaseAppId = ApplicationID.testAppId.asVersion('1.0.0')
        ApplicationID secondReleaseAppId = ApplicationID.testAppId.asVersion('2.0.0')
        ApplicationID snapshotAppId = ApplicationID.testAppId.asVersion('3.0.0')

        Map<String, Object> args = [:]
        args[REF_TENANT] = firstReleaseAppId.tenant
        args[REF_APP] = firstReleaseAppId.app
        args[REF_VERSION] = firstReleaseAppId.version
        args[REF_STATUS] = ReleaseStatus.RELEASE.name()
        args[REF_BRANCH] = ApplicationID.HEAD
        args[REF_CUBE_NAME] = 'SimpleDiscrete'
        args[REF_AXIS_NAME] = 'state'

        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader('Mongo', 'stateSource1', args)
        Axis axis = new Axis('stateSource1', 1, false, refAxisLoader)
        NCube two = new NCube('Mongo')
        two.applicationID = snapshotAppId
        two.addAxis(axis)
        mutableClient.createCube(two)
        mutableClient.commitBranch(snapshotAppId)

        NCube three = new NCube('Mongo2')
        three.applicationID = snapshotAppId
        args[REF_VERSION] = secondReleaseAppId.version
        refAxisLoader = new ReferenceAxisLoader(three.name, 'stateSource2', args)
        axis = new Axis('stateSource2', 1, false, refAxisLoader)
        three.addAxis(axis)
        mutableClient.createCube(three)

        List<AxisRef> axisRefs = mutableClient.getReferenceAxes(snapshotAppId)
        assert 2 == axisRefs.size()
        assert axisRefs[0].destVersion != axisRefs[1].destVersion

        try
        {
            mutableClient.commitBranch(snapshotAppId)
            fail()
        }
        catch(IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'not performed', 'versions')
        }

        try
        {
            List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(snapshotAppId)
            assert 1 == dtos.size()
            String prId = mutableClient.generatePullRequestHash(snapshotAppId, dtos.toArray())
            mutableClient.mergePullRequest(prId)
            fail()
        }
        catch(IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'not performed', 'versions')
        }
    }

    @Test
    void testMergePRWithNewCubeWithDifferentReferenceVersion()
    {
        setUpReferenceCubeForAxisReferenceTests()
        ApplicationID firstReleaseAppId = ApplicationID.testAppId.asVersion('1.0.0')
        ApplicationID secondReleaseAppId = ApplicationID.testAppId.asVersion('2.0.0')
        ApplicationID snapshotAppId = ApplicationID.testAppId.asVersion('3.0.0')
        ApplicationID otherAppId = snapshotAppId.asBranch('other')

        Map<String, Object> args = [:]
        args[REF_TENANT] = firstReleaseAppId.tenant
        args[REF_APP] = firstReleaseAppId.app
        args[REF_VERSION] = firstReleaseAppId.version
        args[REF_STATUS] = ReleaseStatus.RELEASE.name()
        args[REF_BRANCH] = ApplicationID.HEAD
        args[REF_CUBE_NAME] = 'SimpleDiscrete'
        args[REF_AXIS_NAME] = 'state'

        mutableClient.copyBranch(otherAppId.asHead(), otherAppId)
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader('Mongo', 'stateSource1', args)
        Axis axis = new Axis('stateSource1', 1, false, refAxisLoader)
        NCube two = new NCube('Mongo')
        two.applicationID = otherAppId
        two.addAxis(axis)
        mutableClient.createCube(two)
        mutableClient.commitBranch(otherAppId)

        mutableClient.updateBranch(snapshotAppId)
        NCube newCube = new NCube('MongoTest')
        newCube.applicationID = snapshotAppId
        newCube.addAxis(axis)
        mutableClient.createCube(newCube)
        String prId = mutableClient.generatePullRequestHash(snapshotAppId, mutableClient.getBranchChangesForHead(snapshotAppId).toArray())

        args[REF_VERSION] = secondReleaseAppId.version
        mutableClient.updateAxisMetaProperties(otherAppId, two.name, axis.name, args)
        mutableClient.commitBranch(otherAppId)

        try
        {
            mutableClient.mergePullRequest(prId)
            fail()
        }
        catch(IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'not performed', 'versions')
        }
    }

    @Test
    void testMultipleInstanceOfSameReferenceAxis()
    {
        NCube one = NCubeBuilder.discrete1DAlt
        one.applicationID = ApplicationID.testAppId
        Axis state = one.getAxis('state')
        state.setMetaProperty('nose', 'smell')
        state.setMetaProperty('ear', 'sound')
        state.findColumn('OH').setMetaProperty('foo', 'bar')
        state.findColumn('TX').setMetaProperty('baz', 'qux')
        mutableClient.createCube(one)
        assert one.getAxis('state').size() == 2

        Map<String, Object> args = [:]

        ApplicationID appId = ApplicationID.testAppId
        args[REF_TENANT] = appId.tenant
        args[REF_APP] = appId.app
        args[REF_VERSION] = appId.version
        args[REF_STATUS] = appId.status
        args[REF_BRANCH] = appId.branch
        args[REF_CUBE_NAME] = 'SimpleDiscrete'
        args[REF_AXIS_NAME] = 'state'

        // stateSource instead of 'state' to prove the axis on the referring cube does not have to have the same name
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader('Mongo', 'stateSource1', args)
        Axis axis = new Axis('stateSource1', 1, false, refAxisLoader)
        axis.setMetaProperty('nose', 'sniff')
        axis.findColumn('OH').setMetaProperty('foo', 'bart')    // over-ride meta-property on referenced axis
        NCube two = new NCube('Mongo')
        two.applicationID = ApplicationID.testAppId
        two.addAxis(axis)

        refAxisLoader = new ReferenceAxisLoader('Mongo', 'stateSource2', args)
        axis = new Axis('stateSource2', 2, false, refAxisLoader)
        axis.findColumn('TX').setMetaProperty('baz', 'quux')
        axis.setMetaProperty('ear', 'hear')
        two.addAxis(axis)

        two.setCell('a', [stateSource1:'OH', stateSource2:'OH'] as Map)
        two.setCell('b', [stateSource1:'TX', stateSource2:'OH'] as Map)

        String json = two.toFormattedJson()
        NCube reload = NCube.fromSimpleJson(json)
        assert reload.numCells == 2
        assert 'a' == reload.getCell([stateSource1:'OH', stateSource2:'OH'] as Map)
        assert 'b' == reload.getCell([stateSource1:'TX', stateSource2:'OH'] as Map)
        Axis refAxis1 = reload.getAxis('stateSource1')
        Axis refAxis2 = reload.getAxis('stateSource2')
        assert refAxis1.reference
        assert refAxis2.reference

        // Ensure Axis meta-properties are brought over (and appropriately overridden) from referenced axis
        assert 'sniff' == refAxis1.getMetaProperty('nose')
        assert 'sound' == refAxis1.getMetaProperty('ear')
        assert 'smell' == refAxis2.getMetaProperty('nose')
        assert 'hear' == refAxis2.getMetaProperty('ear')

        // Ensure Column meta-properties are brought over (and appropriately overridden) from referenced axis
        assert 'bart' == refAxis1.findColumn('OH').getMetaProperty('foo')
        assert 'qux' == refAxis1.findColumn('TX').getMetaProperty('baz')
        assert 'bar' == refAxis2.findColumn('OH').getMetaProperty('foo')
        assert 'quux' == refAxis2.findColumn('TX').getMetaProperty('baz')
        mutableClient.createCube(two)

        List<AxisRef> axisRefs = mutableClient.getReferenceAxes(ApplicationID.testAppId)
        assert axisRefs.size() == 2
    }

    @Test
    void testDynamicallyLoadedCode()
    {
        NCube ncube = NCubeBuilder.discrete1DEmpty
        GroovyExpression exp = new GroovyExpression('''\
import org.apache.commons.collections.primitives.*
import static com.cedarsoftware.ncube.NCubeConstants.*
@Grab(group='commons-primitives', module='commons-primitives', version='1.0')

Object ints = new ArrayIntList()
ints.add(42)
assert ints.size() == 1
assert ints.get(0) == 42
return ints''', null, false)
        ncube.setCell(exp, [state: 'OH'])
        def x = ncube.getCell([state: 'OH'])
        assert 'org.apache.commons.collections.primitives.ArrayIntList' == x.class.name
    }

    @Test
    void testSearchIncludeFilter()
    {
        preloadCubes(appId, "testCube1.json", "testCube3.json", "test.branch.1.json", "delta.json", "deltaRule.json", "basicJump.json", "basicJumpStart.json")

        // Mark TestCube as red
        NCube testCube = mutableClient.getCube(appId, 'TestCube')
        testCube.setMetaProperty(CUBE_TAGS, "red")
        mutableClient.updateCube(testCube)

        // Mark TestBranch as red & white
        NCube testBranch = mutableClient.getCube(appId, 'TestBranch')
        testBranch.addMetaProperties([(CUBE_TAGS): new CellInfo('string', 'rEd , whiTe', false, false)] as Map)
        mutableClient.updateCube(testBranch)

        List<NCubeInfoDto> list = mutableClient.search(appId, null, null, [(SEARCH_FILTER_INCLUDE):['red', 'white']])
        assert list.size() == 2
        assert list.name.contains('TestCube')
        assert list.name.contains('TestBranch')

        list = mutableClient.search(appId, null, null, [(SEARCH_FILTER_INCLUDE):['red', 'white'], (SEARCH_FILTER_EXCLUDE):['white', 'blue']])
        assert list.size() == 1
        assert 'TestCube' == list[0].name
    }

    @Test
    void testSearchExcludeFilter()
    {
        preloadCubes(appId, "testCube1.json", "testCube3.json", "test.branch.1.json", "delta.json", "deltaRule.json", "basicJump.json", "basicJumpStart.json")

        // Mark TestCube as red
        NCube testCube = mutableClient.getCube(appId, 'TestCube')
        testCube.setMetaProperty(CUBE_TAGS, "red")
        mutableClient.updateCube(testCube)

        // Mark TestBranch as red & white
        NCube testBranch = mutableClient.getCube(appId, 'TestBranch')
        testBranch.setMetaProperty(CUBE_TAGS, "red , WHIte")
        mutableClient.updateCube(testBranch)

        List<NCubeInfoDto> list = mutableClient.search(appId, null, null, [(SEARCH_FILTER_EXCLUDE):['red', 'white']])
        assert list.size() == 5
    }

    @Test
    void testMergedAddDefaultColumn()
    {
        preloadCubes(BRANCH2, "mergeDefaultColumn.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'merge')
        producerCube.addColumn('Column', null)

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'merge')
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producerCube, consumerCube)
        NCube merged = mutableClient.mergeDeltas(BRANCH1, 'merge', deltas)
        Axis axis = merged.getAxis('Column')
        assert axis.hasDefaultColumn()
        assert axis.size() == 4
    }

    @Test
    void testMergedAddRegularColumn()
    {
        preloadCubes(BRANCH2, "mergeDefaultColumn.json")
        mutableClient.commitBranch(BRANCH2)
        mutableClient.copyBranch(HEAD, BRANCH1)

        NCube producerCube = mutableClient.getCube(BRANCH2, 'merge')
        producerCube.addColumn('Column', 'D')

        NCube consumerCube = mutableClient.getCube(BRANCH1, 'merge')
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producerCube, consumerCube)
        NCube merged = mutableClient.mergeDeltas(BRANCH1, 'merge', deltas)
        Axis axis = merged.getAxis('Column')
        assert axis.size() == 4
        assert axis.findColumn('D') instanceof Column
    }
    
    /**
     * Get List<NCubeInfoDto> for the given ApplicationID, filtered by the pattern.  If using
     * JDBC, it will be used with a LIKE clause.  For Mongo...TBD.
     * For any cube record loaded, for which there is no entry in the app's cube cache, an entry
     * is added mapping the cube name to the cube record (NCubeInfoDto).  This will be replaced
     * by an NCube if more than the name is required.
     */
    static List<NCubeInfoDto> getDeletedCubesFromDatabase(ApplicationID appId, String pattern)
    {
        Map options = new HashMap()
        options.put(SEARCH_DELETED_RECORDS_ONLY, true)

        return mutableClient.search(appId, pattern, null, options)
    }
}
