package com.cedarsoftware.ncube
import com.cedarsoftware.ncube.exception.BranchMergeException
import com.cedarsoftware.ncube.exception.CommandCellException
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.ncube.exception.InvalidCoordinateException
import com.cedarsoftware.ncube.util.CdnClassLoader
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeManager.NCUBE_ACCEPTED_DOMAINS
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_APP
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_AXIS_NAME
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_BRANCH
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_CUBE_NAME
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_STATUS
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_TENANT
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_VERSION
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotEquals
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNotSame
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertSame
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail
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
class TestWithPreloadedDatabase
{
    public static String USER_ID = TestNCubeManager.USER_ID

    public static ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, "preloaded", ApplicationID.DEFAULT_VERSION, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
    private static final ApplicationID HEAD = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", ApplicationID.HEAD)
    private static final ApplicationID BRANCH1 = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", "FOO")
    private static final ApplicationID BRANCH2 = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", "BAR")
    private static final ApplicationID BRANCH3 = new ApplicationID('NONE', "test", "1.29.0", "SNAPSHOT", "FOO")
    private static final ApplicationID BOOT = new ApplicationID('NONE', "test", "0.0.0", "SNAPSHOT", ApplicationID.HEAD)

    ApplicationID[] branches = [HEAD, BRANCH1, BRANCH2, BRANCH3, appId, BOOT] as ApplicationID[]

    private TestingDatabaseManager manager

    @Before
    void setup()
    {
        manager = testingDatabaseManager
        manager.setUp()

        NCubeManager.NCubePersister = getNCubePersister()
    }

    @After
    void tearDown()
    {
        manager.removeBranches(branches)
        manager.tearDown()
        manager = null

        NCubeManager.clearCache()
    }

    static TestingDatabaseManager getTestingDatabaseManager()
    {
        return TestingDatabaseHelper.testingDatabaseManager
    }

    static NCubePersister getNCubePersister()
    {
        return TestingDatabaseHelper.persister
    }

    private preloadCubes(ApplicationID id, String ...names)
    {
        manager.addCubes(id, USER_ID, TestingDatabaseHelper.getCubesFromDisk(names))
    }

    @Test
    void testToMakeSureOldStyleSysClasspathThrowsException()
	{
        preloadCubes(appId, "sys.classpath.old.style.json")

        // nothing in cache until we try and get the classloader or load a cube.
        assertEquals(0, NCubeManager.getCacheForApp(appId).size())

        //  url classloader has 1 item
        try
		{
            NCubeManager.getUrlClassLoader(appId, [:])
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.contains('sys.classpath cube'))
            assertTrue(e.message.contains('exists'))
            assertTrue(e.message.toLowerCase().contains('urlclassloader'))
        }
    }

    @Test
    void testUrlClassLoader()
	{
        preloadCubes(appId, "sys.classpath.cp1.json")

        // nothing in cache until we try and get the classloader or load a cube.
        assertEquals(0, NCubeManager.getCacheForApp(appId).size())

        //  url classloader has 1 item
        Map input = [:]
        URLClassLoader loader = NCubeManager.getUrlClassLoader(appId, input)
        assertEquals(1, loader.URLs.length)
        assertEquals(2, NCubeManager.getCacheForApp(appId).size())
        assertEquals(new URL("http://files.cedarsoftware.com/tests/ncube/cp1/"), loader.URLs[0])

        Map<String, Object> cache = NCubeManager.getCacheForApp(appId)
        assertEquals(2, cache.size())

        assertNotNull(NCubeManager.getUrlClassLoader(appId, input))
        assertEquals(2, NCubeManager.getCacheForApp(appId).size())

        NCubeManager.clearCache()
        assertEquals(0, NCubeManager.getCacheForApp(appId).size())

        cache = NCubeManager.getCacheForApp(appId)
        assertEquals(1, NCubeManager.getUrlClassLoader(appId, input).URLs.length)
        assertEquals(2, cache.size())
    }

    @Test
    void testCoordinateNotFoundExceptionThrown()
    {
        preloadCubes(appId, "test.coordinate.not.found.exception.json")

        NCube cube = NCubeManager.getCube(appId, "test.coordinate.not.found.exception")

        try
        {
            cube.getCell([:])
            fail("should throw an exception")
        }
        catch (CoordinateNotFoundException e)
        {
            assertTrue(e.message.contains("fail\nerror occurred in cube: test.coordinate.not.found.exception\n-> cell:test.coordinate.not.found.exception:[]"))
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

        NCube cube = NCubeManager.getCube(appId, "test.coordinate.not.found.exception.additional.info")

        try
        {
            cube.getCell([:])
            fail("should throw an exception")
        }
        catch (CoordinateNotFoundException e)
        {
            assertTrue(e.message.contains("fail with additional info"))
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

        NCube cube = NCubeManager.getCube(appId, "test.invalid.coordinate.exception")

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
        ApplicationID app1 = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", ApplicationID.HEAD)
        ApplicationID app2 = new ApplicationID('NONE', "foo", "1.29.0", "SNAPSHOT", ApplicationID.HEAD)
        ApplicationID app3 = new ApplicationID('NONE', "bar", "1.29.0", "SNAPSHOT", ApplicationID.HEAD)
        preloadCubes(app1, "test.branch.1.json", "test.branch.age.1.json")
        preloadCubes(app2, "test.branch.1.json", "test.branch.age.1.json")
        preloadCubes(app3, "test.branch.1.json", "test.branch.age.1.json")

        ApplicationID branch1 = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", 'kenny')
        ApplicationID branch2 = new ApplicationID('NONE', 'foo', '1.29.0', 'SNAPSHOT', 'kenny')
        ApplicationID branch3 = new ApplicationID('NONE', 'test', '1.29.0', 'SNAPSHOT', 'someoneelse')
        ApplicationID branch4 = new ApplicationID('NONE', 'test', '1.28.0', 'SNAPSHOT', 'someoneelse')

        assertEquals(2, NCubeManager.copyBranch(HEAD, branch1))
        assertEquals(2, NCubeManager.copyBranch(HEAD, branch2))
        // version doesn't match one in HEAD, nothing created.
        assertEquals(0, NCubeManager.copyBranch(branch3.asHead(), branch3))
        assertEquals(2, NCubeManager.copyBranch(branch4.asHead(), branch4))

        // showing we only rely on tenant and branch to get app names.
        assertEquals(3, NCubeManager.getAppNames('NONE').size())

        manager.removeBranches([app1, app2, app3, branch1, branch2, branch3, branch4] as ApplicationID[])
    }

    @Test
    void testCommitBranchOnCubeCreatedInBranch()
    {
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")

        NCubeManager.updateCube(BRANCH1, cube, true)

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assert dtos.size() == 1

        // verify no HEAD changes for branch
        List<NCubeInfoDto> dtos2 = VersionControl.getHeadChangesForBranch(BRANCH1)
        assert dtos2.size() == 0
        // end verify

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        // ensure that there are no more branch changes after create
        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assert dtos.size() == 0

        ApplicationID headId = HEAD
        assert 1 == NCubeManager.search(headId, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size()
    }

    @Test
    void testMergeWithNoHeadCube()
    {
        NCube cube1 = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCube cube2 = NCubeManager.getNCubeFromResource("test.branch.age.2.json")

        NCubeManager.updateCube(BRANCH1, cube1, true)
        NCubeManager.updateCube(BRANCH2, cube2, true)

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        dtos = VersionControl.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)
    }

    @Test
    void updateWithNoChangeClearsChangedFlag()
    {
        NCube cube1 = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCubeManager.updateCube(BRANCH1, cube1)
        VersionControl.commitBranch(BRANCH1)
        List<NCubeInfoDto> cubes0 = NCubeManager.search(BRANCH1, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assert cubes0.size() == 1
        NCubeInfoDto info0 = cubes0[0]
        assert info0.revision == "0"
        assert !info0.changed

        cube1.setCell("XYZ", [code: 15])
        NCubeManager.updateCube(BRANCH1, cube1)
        List<NCubeInfoDto> cubes1 = NCubeManager.search(BRANCH1, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assert cubes1.size() == 1
        NCubeInfoDto info1 = cubes1[0]
        assert info1.id != info0.id
        assert info1.revision == "1"
        assert info1.sha1 != info0.sha1
        assert info1.changed

        cube1.removeCell([code: 15])
        NCubeManager.updateCube(BRANCH1, cube1)
        List<NCubeInfoDto> cubes2 = NCubeManager.search(BRANCH1, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
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
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")

        NCubeManager.updateCube(BRANCH1, cube, true)

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        assertTrue(NCubeManager.deleteBranch(BRANCH1))

        // ensure that there are no more branch changes after delete
        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testDeleteBranchRemovesTripZeroWhenNoOtherVersionsOfBranchExist()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        List<NCubeInfoDto> dtos = NCubeManager.search(BRANCH1, '*', null, null)
        assertEquals(1, dtos.size())

        dtos = NCubeManager.search(BRANCH1.asVersion('0.0.0'), '*', null, null)
        assertEquals(4, dtos.size())

        assertTrue(NCubeManager.deleteBranch(BRANCH1))

        dtos = NCubeManager.search(BRANCH1, '*', null, null)
        assertEquals(0, dtos.size())

        dtos = NCubeManager.search(BRANCH1.asVersion('0.0.0'), '*', null, null)
        assertEquals(0, dtos.size())
    }

    @Test
    void testDeleteBranchDoesNotRemoveTripZeroWhenOtherVersionsOfBranchExist()
    {
        ApplicationID patch = BRANCH1.asVersion('1.28.1')
        ApplicationID tripZero = BRANCH1.asVersion('0.0.0')
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)
        NCubeManager.releaseCubes(HEAD, '1.29.0')
        NCubeManager.copyBranch(BRANCH3, patch)

        List<NCubeInfoDto> dtos = NCubeManager.search(patch, '*', null, null)
        assertEquals(1, dtos.size())

        dtos = NCubeManager.search(BRANCH3, '*', null, null)
        assertEquals(1, dtos.size())

        dtos = NCubeManager.search(tripZero, '*', null, null)
        assertEquals(4, dtos.size())

        assertTrue(NCubeManager.deleteBranch(patch))

        dtos = NCubeManager.search(patch, '*', null, null)
        assertEquals(0, dtos.size())

        dtos = NCubeManager.search(BRANCH3, '*', null, null)
        assertEquals(1, dtos.size())

        dtos = NCubeManager.search(tripZero, '*', null, null)
        assertEquals(4, dtos.size())
    }

    @Test
    void testUpdateBranchOnCubeCreatedInBranch()
    {
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        //  update didn't affect item added locally
        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)
    }

    @Test
    void testRollbackBranchWithPendingAdd()
    {
        preloadCubes(HEAD, "test.branch.1.json")

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())
        Object[] names = [dtos.first().name]
        VersionControl.rollbackCubes(BRANCH1, names)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testRollbackBranchWithDeletedCube()
    {
        preloadCubes(BRANCH1, "test.branch.1.json")
        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(1, NCubeManager.search(HEAD, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(1, NCubeManager.search(BRANCH1, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        Object[] names = [dtos.first().name]
        assertEquals(1, dtos.size())

        List<NCubeInfoDto> dtos2 = VersionControl.getHeadChangesForBranch(BRANCH1)
        assert dtos2.size() == 0

        // undo delete
        VersionControl.rollbackCubes(BRANCH1, names)

        result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testRollbackBranchWithRestoredCube()
    {
        preloadCubes(BRANCH1, "test.branch.1.json")
        String cubeName = 'TestBranch'
        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(1, NCubeManager.search(HEAD, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(1, NCubeManager.search(BRANCH1, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())

        NCubeManager.deleteCubes(BRANCH1, cubeName)
        assertNull(NCubeManager.getCube(BRANCH1, cubeName))
        result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        NCubeManager.restoreCubes(BRANCH1, cubeName)
        assertNotNull(NCubeManager.getCube(BRANCH1, cubeName))

        // undo restore
        VersionControl.rollbackCubes(BRANCH1, cubeName)
        assertNull(NCubeManager.getCube(BRANCH1, cubeName))

        result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testRollbackAfterRelease()
    {
        preloadCubes(BRANCH1, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH1)

        String cubeName = 'TestBranch'
        Map coord = [Code: 15]
        String[] changes = ['AAA', 'BBB', 'CCC']

        NCube cube = NCubeManager.getCube(BRANCH1, cubeName)
        cube.setCell(changes[0], coord)
        NCubeManager.updateCube(BRANCH1, cube)
        VersionControl.commitBranch(BRANCH1)

        cube.setCell(changes[1], coord)
        NCubeManager.updateCube(BRANCH1, cube)
        VersionControl.commitBranch(BRANCH1)

        cube.setCell(changes[2], coord)
        NCubeManager.updateCube(BRANCH1, cube)

        String nextVersion = '1.29.0'
        NCubeManager.releaseCubes(BRANCH1, nextVersion)
        ApplicationID nextBranch1 = BRANCH1.asVersion(nextVersion)
        VersionControl.rollbackCubes(nextBranch1, cubeName)

        cube = NCubeManager.loadCube(nextBranch1, cubeName)
        assertEquals(changes[1], cube.getCell(coord))
    }

    @Test
    void testCreateBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        testValuesOnBranch(HEAD)

        def cube1Sha1 = NCubeManager.getCube(HEAD, "TestBranch").sha1()
        def cube2Sha1 = NCubeManager.getCube(HEAD, "TestAge").sha1()

        List<NCubeInfoDto> objects = NCubeManager.search(HEAD, "*", null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        objects.each { NCubeInfoDto dto ->
            assertNull(dto.headSha1)
        }

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        assertEquals(cube1Sha1, NCubeManager.getCube(BRANCH1, "TestBranch").sha1())
        assertEquals(cube2Sha1, NCubeManager.getCube(BRANCH1, "TestAge").sha1())

        objects = NCubeManager.search(BRANCH1, "*", null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
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
        preloadCubes(HEAD, "test.branch.1.json")

        NCube cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        assertNull(NCubeManager.getCube(HEAD, "TestAge"))

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertNull(NCubeManager.getCube(HEAD, "TestAge"))

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        assertNull(NCubeManager.getCube(HEAD, "TestAge"))

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertNull(NCubeManager.getCube(HEAD, "TestAge"))

        cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        assertNotNull(cube)
        NCubeManager.updateCube(BRANCH1, cube, true)


        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertNull(NCubeManager.getCube(HEAD, "TestAge"))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
    }

    @Test
    void testUpdateBranchWithUpdateOnBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(HEAD, "TestAge"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertNull(NCubeManager.getCube(BRANCH2, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH2, "TestAge"))

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH2))

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH2, "TestBranch").size())

        NCube cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(BRANCH1, cube, true))

        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.age.1.json")
        NCubeManager.updateCube(BRANCH1, cubes[0], true)

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)
        VersionControl.commitBranch(BRANCH1, dtos)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH2)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH2, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH2, "TestAge").size())

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(BRANCH2, "TestBranch")
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

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        NCube cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(BRANCH1, cube, true))

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        // commit the branch
        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // check HEAD hasn't changed.
        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        // both should be updated now.
        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))
        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testCommitBranchOnUpdateWithOldInvalidSha1()
    {
        // load cube with same name, but different structure in TEST branch
        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.1.json")

        manager.insertCubeWithNoSha1(HEAD, USER_ID, cubes[0])

        //assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").length)
        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2")

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())

        // No changes have happened yet, even though sha1 is incorrect,
        // we just copy the sha1 when we create the branch so the headsha1 won't
        // differ until we make a change.
        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testCommitBranchWithUpdateAndWrongRevisionNumber()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        NCube cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(BRANCH1, cube, true))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)
        ((NCubeInfoDto)dtos[0]).revision = Long.toString(100)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testRollback()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(HEAD)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        NCube cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(BRANCH1, cube, true))
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        cube.setCell("FOO", [Code : 10.0])
        assertEquals(3, cube.cellMap.size())
        assertEquals("FOO", cube.getCell([Code : 10.0]))

        assertTrue(NCubeManager.updateCube(BRANCH1, cube, true))
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        assertTrue(NCubeManager.updateCube(BRANCH1, cube, true))
        assertEquals(4, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        cube.setCell("FOO", [Code : 10.0])
        assertEquals(3, cube.cellMap.size())
        assertEquals("FOO", cube.getCell([Code : 10.0]))

        assertTrue(NCubeManager.updateCube(BRANCH1, cube, true))
        assertEquals(5, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())
        assertEquals(1, VersionControl.rollbackCubes(BRANCH1, "TestBranch"))

        assertEquals(6, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.size())
    }

    @Test
    void testCommitBranchOnDelete()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(HEAD)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        NCube cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.deleteCubes(BRANCH1, 'TestBranch'))

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())

        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        // cube is deleted
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        // check HEAD hasn't changed.
        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  loads in both TestAge and TestBranch though only TestBranch has changed.
        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        // both should be updated now.
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(HEAD, "TestBranch"))
    }

    @Test
    void testSearch()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json", "TestCubeLevelDefault.json", "basicJumpStart.json")
        testValuesOnBranch(HEAD)

        //  delete and re-add these cubes.
        assertEquals(4, NCubeManager.copyBranch(HEAD, BRANCH1))
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        NCubeManager.deleteCubes(BRANCH1, 'TestAge')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 2
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(BRANCH1, cube, true)
        result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        // test with default options
        assertEquals(4, NCubeManager.search(HEAD, null, null, null).size())
        assertEquals(4, NCubeManager.search(HEAD, "", "", null).size())
        assertEquals(2, NCubeManager.search(HEAD, "Test*", null, null).size())
        assertEquals(1, NCubeManager.search(HEAD, "Test*", "zzz", null).size())
        assertEquals(0, NCubeManager.search(HEAD, "*Codes*", "ZZZ", null).size())
        assertEquals(1, NCubeManager.search(HEAD, "*Codes*", "OH", null).size())
        assertEquals(1, NCubeManager.search(HEAD, null, "ZZZ", null).size())

        Map map = new HashMap()

        // test with default options and valid map
        assertEquals(4, NCubeManager.search(HEAD, null, null, map).size())
        assertEquals(4, NCubeManager.search(HEAD, "", "", map).size())
        assertEquals(2, NCubeManager.search(HEAD, "Test*", null, map).size())
        assertEquals(1, NCubeManager.search(HEAD, "Test*", "zzz", map).size())
        assertEquals(0, NCubeManager.search(HEAD, "*Codes*", "ZZZ", map).size())
        assertEquals(1, NCubeManager.search(HEAD, "*Codes*", "OH", map).size())
        assertEquals(1, NCubeManager.search(HEAD, null, "ZZZ", map).size())

        map = new HashMap()
        map.put(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY, true)

        assertEquals(3, NCubeManager.search(HEAD, null, null, map).size())
        assertEquals(3, NCubeManager.search(HEAD, "", "", map).size())
        assertEquals(1, NCubeManager.search(HEAD, "Test*", null, map).size())
        assertEquals(0, NCubeManager.search(HEAD, "Test*", "zzz", map).size())
        assertEquals(0, NCubeManager.search(HEAD, "*Codes*", "ZZZ", map).size())
        assertEquals(1, NCubeManager.search(HEAD, "*Codes*", "OH", map).size())
        assertEquals(0, NCubeManager.search(HEAD, null, "ZZZ", map).size())

        map.put(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY, false)
        map.put(NCubeManager.SEARCH_DELETED_RECORDS_ONLY, true)

        assertEquals(1, NCubeManager.search(HEAD, null, null, map).size())
        assertEquals(1, NCubeManager.search(HEAD, "", "", map).size())
        assertEquals(1, NCubeManager.search(HEAD, "Test*", null, map).size())
        assertEquals(1, NCubeManager.search(HEAD, "Test*", "zzz", map).size())
        assertEquals(0, NCubeManager.search(HEAD, "*Codes*", "ZZZ", map).size())
        assertEquals(0, NCubeManager.search(HEAD, "*Codes*", "OH", map).size())
        assertEquals(1, NCubeManager.search(HEAD, null, "ZZZ", map).size())

        map.put(NCubeManager.SEARCH_DELETED_RECORDS_ONLY, false)
        map.put(NCubeManager.SEARCH_CHANGED_RECORDS_ONLY, true)
    }

    @Test
    void testSearchAdvanced()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json", "basicJumpStart.json", "expressionAxis.json")
        testValuesOnBranch(HEAD)

        Map map = new HashMap()
        map.put(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY, true)

        assertEquals(2, NCubeManager.search(HEAD, "Test*", null, map).size())
        assertEquals(1, NCubeManager.search(HEAD, "TestBranch", "ZZZ", map).size())
        assertEquals(1, NCubeManager.search(HEAD, "*basic*", "input", map).size())
        assertEquals(0, NCubeManager.search(HEAD, "*Test*", "input", map).size())
        assertEquals(1, NCubeManager.search(HEAD, "*Branch", "ZZZ", map).size())
        assertEquals(2, NCubeManager.search(HEAD, null, "ZZZ", map).size())
        assertEquals(2, NCubeManager.search(HEAD, "", "ZZZ", map).size())
        assertEquals(2, NCubeManager.search(HEAD, "*", "output", map).size())
        assertEquals(0, NCubeManager.search(HEAD, "*axis", "input", map).size())
    }

    @Test
    void testSearchWildCardAndBrackets()
    {
        String cubeName = 'bracketsInString'
        preloadCubes(HEAD, cubeName + '.json')

        Map map = [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY): true]

        NCube cube = NCubeManager.getCube(HEAD, cubeName)
        String value = cube.getCell([axis1: 'column1', axis2: 'column2'])
        assertEquals('testValue[A]', value)

        //Test search with content value containing brackets, with or without wildcard
        assertEquals(1, NCubeManager.search(HEAD, cubeName, 'testValue[A]', map).size())
        assertEquals(1, NCubeManager.search(HEAD, cubeName, 'testValue', map).size())
        assertEquals(1, NCubeManager.search(HEAD, cubeName, 'Value[A]', map).size())
        assertEquals(1, NCubeManager.search(HEAD, cubeName, '*Value*', map).size())
        assertEquals(1, NCubeManager.search(HEAD, cubeName, '*', map).size())
        assertEquals(1, NCubeManager.search(HEAD, cubeName, null, map).size())
        assertEquals(0, NCubeManager.search(HEAD, cubeName, 'somethingElse', map).size())

        //Test search with cube name pattern, with or without wildcard, not exact match
        assertEquals(1, NCubeManager.search(HEAD, '*racketsIn*', null, map).size())
        assertEquals(1, NCubeManager.search(HEAD, 'racketsIn', null, map).size())

        //Test search with cube name pattern, with or without wildcard, exact match
        map[NCubeManager.SEARCH_EXACT_MATCH_NAME] = true
        assertEquals(1, NCubeManager.search(HEAD, cubeName, null, map).size())
        assertEquals(0, NCubeManager.search(HEAD, '*racketsIn*', null, map).size())
        assertEquals(0, NCubeManager.search(HEAD, 'racketsIn', null, map).size())
    }

    @Test
    void testUpdateBranchAfterDelete()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(HEAD)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        NCube cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.deleteCubes(BRANCH1, 'TestBranch'))

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())

        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        // cube is deleted
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        // check HEAD hasn't changed.
        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  loads in both TestAge and TestBranch though only TestBranch has changed.
        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestBranch"))
    }

    @Test
    void testCreateBranchThatAlreadyExists()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        //1) should work
        NCubeManager.copyBranch(HEAD, BRANCH1)

        try
		{
            //2) should already be created.
            NCubeManager.copyBranch(HEAD, BRANCH1)
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.contains("already exists"))
        }
    }

    @Test
    void testReleaseCubes()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        assertNotNull(cube)
        NCubeManager.updateCube(BRANCH1, cube, true)

        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        testValuesOnBranch(BRANCH1, "FOO")

        int rows = NCubeManager.releaseCubes(HEAD, "1.29.0")
        assertEquals(2, rows)

        Map<String, List<String>> versions = NCubeManager.getVersions(HEAD.tenant, HEAD.app)
        assert versions.size() == 2
        assert versions.SNAPSHOT.size() == 2
        assert versions.SNAPSHOT.contains('1.29.0')
        assert versions.RELEASE.size() == 1
        assert versions.RELEASE.contains('1.28.0')

        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(HEAD, "TestAge"))
        assertNull(NCubeManager.getCube(HEAD, "TestBranch"))

//        ApplicationID newSnapshot = HEAD.createNewSnapshotId("1.29.0")
        ApplicationID newBranchSnapshot = BRANCH1.createNewSnapshotId("1.29.0")

        ApplicationID release = HEAD.asRelease()

        testValuesOnBranch(release)
        testValuesOnBranch(newBranchSnapshot, "FOO")

        manager.removeBranches([release, newBranchSnapshot] as ApplicationID[])
    }

    @Test
    void testDuplicateCubeChanges()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        NCubeManager.duplicate(HEAD, BRANCH2, "TestBranch", "TestBranch2")
        NCubeManager.duplicate(HEAD, BRANCH2, "TestAge", "TestAge")

        // assert HEAD and branch are still there
        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        //  Test with new name.
        NCube cube = NCubeManager.getCube(BRANCH2, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(BRANCH2, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
    }

    @Test
    void testDuplicateCubeGoingToDifferentApp()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertNull(NCubeManager.getCube(appId, "TestBranch"))
        assertNull(NCubeManager.getCube(appId, "TestAge"))

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)
        assertNull(NCubeManager.getCube(appId, "TestBranch"))
        assertNull(NCubeManager.getCube(appId, "TestAge"))

        NCubeManager.duplicate(BRANCH1, appId, "TestBranch", "TestBranch")
        NCubeManager.duplicate(HEAD, appId, "TestAge", "TestAge")

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

        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertTrue(NCubeManager.deleteCubes(BRANCH1, ['TestBranch']))

        try
        {
            NCubeManager.duplicate(BRANCH1, appId, "TestBranch", "TestBranch")
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains("Unable to duplicate"))
            assertTrue(e.message.contains("deleted"))
        }
    }

    @Test
    void testRenameCubeOnDeletedCube()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertTrue(NCubeManager.deleteCubes(BRANCH1, ['TestBranch']))

        try
        {
            NCubeManager.renameCube(BRANCH1, "TestBranch", "Foo")
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains("cannot be rename"))
            assertTrue(e.message.contains("Deleted cubes"))
        }
    }

    @Test
    void testDuplicateWhenCubeWithNameAlreadyExists()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        try
        {
            NCubeManager.duplicate(BRANCH1, BRANCH1, "TestBranch", "TestAge")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains("Unable to duplicate"))
            assertTrue(e.message.contains("already exists"))
        }
    }

    @Test
    void testRenameCubeWhenNewNameAlreadyExists()
    {
        ApplicationID head = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", ApplicationID.HEAD)

        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, BRANCH1))

        testValuesOnBranch(head)
        testValuesOnBranch(BRANCH1)

        assert NCubeManager.renameCube(BRANCH1, "TestBranch", "TestAge")
    }

    @Test
    void testRenameCubeWithHeadHavingCubeAAndCubeBDeleted()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(HEAD, "*").size())

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, "*").size())

        assertTrue(NCubeManager.deleteCubes(BRANCH1, 'TestBranch'))
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertTrue(NCubeManager.deleteCubes(BRANCH1, 'TestAge'))
        assertEquals(2, getDeletedCubesFromDatabase(BRANCH1, "*").size())

        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 2
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertNull(NCubeManager.getCube(HEAD, "TestBranch"))
        assertNull(NCubeManager.getCube(HEAD, "TestAge"))

        assertEquals(2, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, getDeletedCubesFromDatabase(HEAD, "*").size())

        NCubeManager.restoreCubes(BRANCH1, "TestBranch")
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestAge"))
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertNull(NCubeManager.getCube(HEAD, "TestBranch"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestAge"))

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestAge", "TestBranch"))
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testRenameCubeWithBothCubesCreatedOnBranch()
    {
        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.1.json", "test.branch.age.1.json")
        NCubeManager.updateCube(BRANCH1, cubes[0], true)
        NCubeManager.updateCube(BRANCH1, cubes[1], true)

        assertNull(NCubeManager.getCube(HEAD, "TestBranch"))
        assertNull(NCubeManager.getCube(HEAD, "TestAge"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 2
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertNull(NCubeManager.getCube(HEAD, "TestBranch"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestAge"))

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch2").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(HEAD, "*").size())

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch2", "TestBranch"))

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)
        result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(HEAD, "TestBranch2").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(HEAD, "*").size())
    }

    @Test
    void testRenameCubeWhenNewNameAlreadyExistsButIsInactive()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        NCubeManager.deleteCubes(BRANCH1, 'TestAge')

        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())

        //  cube is deleted so won't throw exception
        NCubeManager.renameCube(BRANCH1, "TestBranch", "TestAge")

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
    }

    @Test
    void testDuplicateCubeWhenNewNameAlreadyExistsButIsInactive()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        NCubeManager.deleteCubes(BRANCH1, 'TestAge')

        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())

        //  cube is deleted so won't throw exception
        NCubeManager.duplicate(BRANCH1, BRANCH1, "TestBranch", "TestAge")

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, "*").size())
    }

    @Test
    void testRenameAndThenRenameAgainThenRollback()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertEquals(2, VersionControl.getBranchChangesForHead(BRANCH1).size())

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch2", "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
        assertEquals(0, VersionControl.rollbackCubes(BRANCH1, dtos))

        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
    }

    @Test
    void testRenameAndThenRollback()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertEquals(2, VersionControl.getBranchChangesForHead(BRANCH1).size())

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.size())

        assertEquals(2, VersionControl.rollbackCubes(BRANCH1, ["TestBranch", "TestBranch2"] as Object[]))

        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
    }

    @Test
    void testRenameAndThenCommitAndThenRenameAgainWithCommit()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertNull(NCubeManager.getCube(HEAD, "TestBranch"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestAge"))

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch2", "TestBranch"))

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        dtos = VersionControl.getBranchChangesForHead(BRANCH1)

        assertEquals(2, dtos.length)
        result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertNull(NCubeManager.getCube(HEAD, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestBranch"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestAge"))
    }

    @Test
    void testRenameAndThenRenameAgainThenCommit()
    {
        ApplicationID head = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", ApplicationID.HEAD)
        ApplicationID branch = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", "FOO")

        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch))

        testValuesOnBranch(head)
        testValuesOnBranch(branch)

        assertTrue(NCubeManager.renameCube(branch, "TestBranch", "TestBranch2"))

        assertNull(NCubeManager.getCube(branch, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(branch, "TestAge"))

        assertEquals(2, NCubeManager.getRevisionHistory(branch, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(branch, "*").size())
        Object[] dtos = VersionControl.getBranchChangesForHead(branch)
        assertEquals(2, dtos.length)

        assertTrue(NCubeManager.renameCube(branch, "TestBranch2", "TestBranch"))

        assertNull(NCubeManager.getCube(branch, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(branch, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch, "TestAge"))

        assertEquals(2, NCubeManager.getRevisionHistory(branch, "TestBranch2").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch, "TestBranch").size())
        dtos = VersionControl.getBranchChangesForHead(branch)
        assertEquals(0, dtos.length)

        //  techniacally don't have to do this since there aren't any changes,
        //  but we should verify we work with 0 dtos passed in, too.  :)
        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertNotNull(NCubeManager.getCube(branch, "TestBranch"))
        assertNull(NCubeManager.getCube(branch, "TestBranch2"))
    }

    @Test
    void testRenameAndThenRenameAgainThenCommitWhenNotCreatedFromBranch()
    {
        // load cube with same name, but different structure in TEST branch
        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.1.json", "test.branch.age.1.json")

        NCubeManager.updateCube(BRANCH1, cubes[0], true)
        NCubeManager.updateCube(BRANCH1, cubes[1], true)

        testValuesOnBranch(BRANCH1)

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, "*").size())
        assertEquals(2, VersionControl.getBranchChangesForHead(BRANCH1).size())

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch2", "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 2
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertNotNull(NCubeManager.getCube(HEAD, "TestBranch"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestBranch"))
        assertNull(NCubeManager.getCube(HEAD, "TestBranch2"))
    }

    @Test
    void testRenameCubeBasicCase()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch2"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        testValuesOnBranch(HEAD)

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertNotNull(NCubeManager.getCube(HEAD, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(HEAD, "TestAge"))

        //  Test with new name.
        NCube cube = NCubeManager.getCube(BRANCH1, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(BRANCH1, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
    }

    @Test
    void testRenameCubeBasicCaseWithNoHead()
	{
        // load cube with same name, but different structure in TEST branch
        NCube cube1 = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCube cube2 = NCubeManager.getNCubeFromResource("test.branch.age.1.json")

        NCubeManager.updateCube(BRANCH1, cube1, true)
        NCubeManager.updateCube(BRANCH1, cube2, true)
        testValuesOnBranch(BRANCH1)

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.size())

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.size())

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos as Object[])
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 2
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        //  Test with new name.
        NCube cube = NCubeManager.getCube(BRANCH1, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(BRANCH1, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        cube = NCubeManager.getCube(HEAD, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(HEAD, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
        assertNull(NCubeManager.getCube(HEAD, "TestBranch"))
    }

    @Test
    void testRenameCubeFunctionality()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        try
        {
            NCubeManager.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException ignore) { }

        try
        {
            NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException ignore) { }

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, null).size())


        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())

        try
        {
            NCubeManager.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException ignore) { }


        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(2, dtos.size())

        assertEquals(2, VersionControl.rollbackCubes(BRANCH1, [dtos.get(0).name, dtos.get(1).name] as Object[]))

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, null).size())

        assertEquals(0, VersionControl.getBranchChangesForHead(BRANCH1).size())

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        try
        {
            NCubeManager.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException ignore) { }

        assert 2 == NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size()

        assertTrue(NCubeManager.renameCube(BRANCH1, "TestBranch", "TestBranch2"))

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, null).size())

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(4, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())

        try
        {
            NCubeManager.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException ignore) { }

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch2").size())

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(4, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2").size())

        assertEquals(1, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(BRANCH1, null).size())
    }

    @Test
    void testDuplicateCubeFunctionality()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(HEAD)

        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        testValuesOnBranch(HEAD)
        testValuesOnBranch(BRANCH1)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        try
		{
            NCubeManager.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException ignore) { }

        try
		{
            NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException ignore) { }

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH2, null).size())

        NCubeManager.duplicate(BRANCH1, BRANCH2, "TestBranch", "TestBranch2")
        NCubeManager.duplicate(BRANCH1, BRANCH2, "TestAge", "TestAge")

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH2, null).size())


        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH2, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH2, "TestBranch2").size())

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        try
		{
            NCubeManager.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        try
		{
            NCubeManager.getRevisionHistory(BRANCH2, "TestBranch")
            fail()
        } catch (IllegalArgumentException ignore) {
        }


        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.size())

        assertEquals(1, VersionControl.rollbackCubes(BRANCH2, dtos.first().name))

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())

        assertEquals(0, VersionControl.getBranchChangesForHead(BRANCH1).size())

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        try
		{
            NCubeManager.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        try
		{
            NCubeManager.getRevisionHistory(BRANCH1, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }


        NCubeManager.duplicate(BRANCH1, BRANCH2, "TestBranch", "TestBranch2")

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH2, null).size())

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH2, "TestAge").size())
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH2, "TestBranch2").size())

        try
		{
            NCubeManager.getRevisionHistory(HEAD, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        Map<String, Object> result = VersionControl.commitBranch(BRANCH2)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch2").size())

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH2, "TestAge").size())
        assertEquals(3, NCubeManager.getRevisionHistory(BRANCH2, "TestBranch2").size())

        try
        {
            NCubeManager.getRevisionHistory(BRANCH2, "TestBranch")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        assertEquals(0, getDeletedCubesFromDatabase(HEAD, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(BRANCH2, null).size())
    }

    @Test
    void testDuplicateCubeWithNonExistentSource()
    {
        try
        {
            NCubeManager.duplicate(HEAD, BRANCH1, "foo", "bar")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains("not duplicate cube because"))
            assertTrue(e.message.contains("does not exist"))
        }
    }

    @Test
    void testDuplicateCubeWhenTargetExists()
    {
        preloadCubes(HEAD, "test.branch.1.json")
        NCubeManager.copyBranch(HEAD, BRANCH1)

        try
        {
            NCubeManager.duplicate(HEAD, BRANCH1, "TestBranch", "TestBranch")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains("Unable to duplicate"))
            assertTrue(e.message.contains("already exists"))
        }
    }

    @Test
    void testOverwriteHeadWhenHeadDoesntExist()
    {
        preloadCubes(HEAD, "test.branch.1.json")
        NCubeManager.copyBranch(HEAD, BRANCH1)
        NCubeManager.deleteCubes(BRANCH1, ['TestBranch'])

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        try
        {
            NCubeManager.duplicate(HEAD, BRANCH1, "TestBranch", "TestBranch")
            assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
            assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
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
        NCubeManager.copyBranch(HEAD, BRANCH1)
        NCubeManager.deleteCubes(BRANCH1, ['TestBranch'])

        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        try
        {
            NCubeManager.duplicate(HEAD, BRANCH1, "TestBranch", "TestBranch")
            assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
            assertEquals(3, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains("Unable to duplicate"))
            assertTrue(e.message.contains("already exists"))
        }
    }

    @Test
    void testDeleteCubeAndThenDeleteCubeAgain()
    {
        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.1.json")

        NCubeManager.updateCube(BRANCH1, cubes[0], true)
        assertNotNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        assertTrue(NCubeManager.deleteCubes(BRANCH1, 'TestBranch'))
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))

        try
        {
            NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        }
        catch (IllegalArgumentException e)
        {
            e.message.contains('does not exist')
        }
    }

    private static void testValuesOnBranch(ApplicationID appId, String code1 = "ABC", String code2 = "youth") {
        NCube cube = NCubeManager.getCube(appId, "TestBranch")
        assertEquals(code1, cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(appId, "TestAge")
        assertEquals(code2, cube.getCell(["Code": 10]))
    }

    @Test
    void testCommitBranchWithItemCreatedLocallyAndOnHead()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertEquals(1,  NCubeManager.copyBranch(HEAD, BRANCH2))

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.2.json")
        NCubeManager.updateCube(BRANCH2, cube, true)

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)
        VersionControl.commitBranch(BRANCH2, dtos)

        // Commit to branch 2 causes 1 pending update for BRANCH1
        List<NCubeInfoDto> dtos2 = VersionControl.getHeadChangesForBranch(BRANCH1)
        assert dtos2.size() == 1
        assert dtos2[0].name == 'TestAge'

        cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        try
        {
            VersionControl.commitBranch(BRANCH1, dtos)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testCommitWithCubeChangedButMatchesHead()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        NCube cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        assertEquals(1,  NCubeManager.copyBranch(HEAD, BRANCH2))

        cube = NCubeManager.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(BRANCH2, cube, true)

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)

        VersionControl.commitBranch(BRANCH2, dtos)

        cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        List dtos2 = VersionControl.getHeadChangesForBranch(BRANCH2)
        assert dtos2.size() == 0    // Nothing for BRANCH2 because cube matched HEAD already
    }

    @Test
    void testCommitFailsWithoutPermissions()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        // cube is updated by someone with access
        String cubeName = 'TestBranch'
        NCube testBranchCube = NCubeManager.getCube(BRANCH1, cubeName)
        testBranchCube.setCell('AAA', [Code: 15])
        NCubeManager.updateCube(BRANCH1, testBranchCube, true)

        // set permission on cube to deny commit for normal user
        ApplicationID branchBoot = BRANCH1.asVersion('0.0.0')
        NCube sysPermissions = NCubeManager.getCube(branchBoot, NCubeManager.SYS_PERMISSIONS)
        sysPermissions.addColumn(NCubeManager.AXIS_RESOURCE, cubeName)
        sysPermissions.setCell(true, [(NCubeManager.AXIS_RESOURCE): cubeName, (NCubeManager.AXIS_ROLE): NCubeManager.ROLE_USER, (NCubeManager.AXIS_ACTION): Action.READ.lower()])
        NCubeManager.updateCube(branchBoot, sysPermissions)
        VersionControl.commitBranch(branchBoot)

        // set testUser to have user role on branch
        NCube sysBranchPermissions = NCubeManager.getCube(branchBoot, NCubeManager.SYS_BRANCH_PERMISSIONS)
        String testUser = 'testUser'
        sysBranchPermissions.addColumn(NCubeManager.AXIS_USER, testUser)
        sysBranchPermissions.setCell(true, [(NCubeManager.AXIS_USER): testUser])
        NCubeManager.updateCube(branchBoot, sysBranchPermissions)

        // impersonate testUser, who shouldn't be able to commit the changed cube
        NCubeManager.userId = testUser

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    /***** tests for commit and update from our cube matrix *****/

    @Test
    void testCommitConsumerNoCubeHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerNoCubeHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerNoCubeHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerNoCubeHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerNoCubeHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerNoCubeHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerAddHeadNoChange()
	{
        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerAddHeadNoChange()
	{
        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerAddHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerAddHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerAddHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerAddHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerAddHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerAddHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerAddHeadUpdateMergeable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerAddHeadUpdateMergeable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerAddHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerAddHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerAddHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerAddHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerNoChangeHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerNoChangeHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerNoChangeHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerNoChangeHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerNoChangeHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerNoChangeHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerNoChangeHeadUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerNoChangeHeadUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerUpdateHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerUpdateHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerUpdateHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerUpdateHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerUpdateHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerUpdateHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerUpdateHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerUpdateHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerUpdateHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerUpdateHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerUpdateHeadUpdateMergeable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerUpdateHeadUpdateMergeable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerUpdateHeadUpdateTwice()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        //consumer change
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        //producer change and commit
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        //consumer update
        VersionControl.updateBranch(BRANCH1)

        //producer change and commit
        producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('CCC', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        //consumer commit
        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerUpdateHeadUpdateTwice()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        //consumer change
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        //producer change and commit
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        //consumer update
        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        //producer change and commit
        producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('CCC', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerUpdateHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerUpdateHeadUpdateSame()
	{
        //Includes additional checks to verify changed flag on update, commit and updateBranch
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        NCubeInfoDto producerDto = NCubeManager.search(BRANCH2, 'TestBranch', null, null)[0]
        assert producerDto.changed
        VersionControl.commitBranch(BRANCH2)
        producerDto = NCubeManager.search(BRANCH2, 'TestBranch', null, null)[0]
        assert !producerDto.changed

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeInfoDto consumerDto = NCubeManager.search(BRANCH1, 'TestBranch', null, null)[0]
        assert consumerDto.changed

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
        consumerDto = NCubeManager.search(BRANCH1, 'TestBranch', null, null)[0]
        assert !consumerDto.changed
    }

    @Test
    void testCommitConsumerUpdateHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerDeleteHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerDeleteHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerDeleteHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerDeleteHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerDeleteHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerDeleteHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerDeleteHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerDeleteHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerDeleteHeadUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerDeleteHeadUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerDeleteHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerDeleteHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerUpdateDeleteHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateDeleteHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerUpdateDeleteHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateDeleteHeadUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerUpdateDeleteHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateDeleteHeadUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerUpdateDeleteHeadUpdateMergable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerUpdateDeleteHeadUpdateMergable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerRestoreHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerRestoreHeadNoChange()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerRestoreHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerRestoreHeadAdd()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerRestoreHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerRestoreHeadDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerRestoreHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerRestoreHeadRestore()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerRestoreHeadRestoreUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerRestoreHeadRestoreUpdate()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerRestoreHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerRestoreHeadAddDelete()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerRestoreUpdateHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerRestoreUpdateHeadAddConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')
        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerRestoreUpdateHeadRestoreUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerRestoreUpdateHeadRestoreUpdateSame()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitConsumerRestoreUpdateHeadRestoreUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateConsumerRestoreUpdateHeadRestoreUpdateConflict()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testCommitConsumerRestoreUpdateHeadRestoreUpdateMergable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testUpdateConsumerRestoreUpdateHeadRestoreUpdateMergable()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        NCubeManager.restoreCubes(BRANCH1, 'TestBranch')
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('BBB', [Code : 15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testCommitWithReject()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        consumerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())

        consumerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH1, consumerCube)

        try
        {
            VersionControl.commitBranch(BRANCH1, dtos)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testMergeBetweenBranchesAddEmptyBranchCase()
    {
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        VersionControl.commitBranch(BRANCH2)

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 1
        NCubeInfoDto dto = dtos[0]
        assert dto.name == 'TestAge'
    }

    @Test
    void testMergeBetweenBranchesAddToEstablishedBranch()
    {
        preloadCubes(BRANCH2, "test.branch.age.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1, true)
        preloadCubes(BRANCH2, "test.branch.2.json")

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 1
        NCubeInfoDto dto = dtos[0]
        assert dto.name == 'TestBranch'
    }

    @Test
    void testMergeBetweenBranchesUpdate()
    {
        preloadCubes(BRANCH2, "test.branch.1.json")
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        preloadCubes(BRANCH1, "test.branch.1.json")
        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 1
        NCubeInfoDto dto = dtos[0]
        assert dto.notes.contains('updated')
    }

    @Test
    void testMergeBetweenBranchesDelete()
    {
        preloadCubes(BRANCH2, "test.branch.1.json", 'test.branch.age.1.json')
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1, true)
        NCubeManager.deleteCubes(BRANCH2, ['TestBranch'])

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForMyBranch(BRANCH1, 'BAR')
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
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1, true)

        // 'TestBranch' deleted from both branches
        NCubeManager.deleteCubes(BRANCH2, 'TestBranch')
        VersionControl.commitBranch(BRANCH2)
        VersionControl.updateBranch(BRANCH1)

        // 'TestBranch' restored in BRANCH2
        NCubeManager.restoreCubes(BRANCH2, 'TestBranch')

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 1
        NCubeInfoDto dto = dtos[0]
        assert dto.name == 'TestBranch'
        assert dto.notes.contains('restored')
    }

    @Test
    void testMergeBetweenBranchesNoCubesInYoBranch()
    {
        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForMyBranch(BRANCH1, 'BAR')
        assert dtos.size() == 0
    }

    @Test
    void testUpdateWithReject()
	{
        preloadCubes(BRANCH2, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        List<NCubeInfoDto> dtos = VersionControl.getHeadChangesForBranch(BRANCH1)
        assertEquals(1, dtos.size())

        producerCube.setCell('BBB', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1

        dtos = VersionControl.getHeadChangesForBranch(BRANCH1)
        assertEquals(1, dtos.size())

        result = VersionControl.updateBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0
    }

    /***** End tests for commit and update from cube test matrix *****/

    @Test
    void testAddDifferentColumnsWithDefault()
    {
        preloadCubes(BRANCH2, "testCube6.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        //consumer add column
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestCube')
        consumerCube.addColumn('Gender', 'Dog')
        NCubeManager.updateCube(BRANCH1, consumerCube)

        //producer add column and commit
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestCube')
        producerCube.addColumn('Gender', 'Cat')
        producerCube.setCell('calico', [Gender: 'Cat'])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        //consumer update
        VersionControl.getHeadChangesForBranch(BRANCH1)
        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        consumerCube = NCubeManager.loadCube(BRANCH1, 'TestCube')
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
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestCube')
        producerCube.deleteColumn('Gender', null)
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        //consumer add column
        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestCube')
        consumerCube.addColumn('Gender', 'Dog')
        NCubeManager.updateCube(BRANCH1, consumerCube)

        //producer add column and commit
        producerCube = NCubeManager.loadCube(BRANCH2, 'TestCube')
        producerCube.addColumn('Gender', 'Cat')
        producerCube.setCell('calico', [Gender: 'Cat'])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        //consumer update
        VersionControl.getHeadChangesForBranch(BRANCH1)
        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        consumerCube = NCubeManager.loadCube(BRANCH1, 'TestCube')
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
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        //producer remove default column and commit
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestCube')
        producerCube.deleteColumn('Gender', null)
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        //consumer update
        VersionControl.getHeadChangesForBranch(BRANCH1)
        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'TestCube')
        Axis genderAxis = consumerCube.getAxis('Gender')
        assert genderAxis.findColumn('Male')
        assert genderAxis.findColumn('Female')
        assert !genderAxis.hasDefaultColumn()
        assert genderAxis.size() == 2

        //producer add default column and cell and commit
        producerCube.addColumn('Gender', null)
        producerCube.setCell('it', [Gender: null])
        NCubeManager.updateCube(BRANCH2, producerCube)
        VersionControl.commitBranch(BRANCH2)

        //consumer update
        VersionControl.getHeadChangesForBranch(BRANCH1)
        result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        consumerCube = NCubeManager.loadCube(BRANCH1, 'TestCube')
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
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        //producer change cube
        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'TestBranch')
        producerCube.setCell('AAA', [Code : -15])
        NCubeManager.updateCube(BRANCH2, producerCube)

        //consumer delete cube
        NCubeManager.deleteCubes(BRANCH1, 'TestBranch')

        //consumer update from producer
        VersionControl.mergeAcceptTheirs(BRANCH1, ['TestBranch'] as Object[], BRANCH2.branch)

        //consumer open commit modal
        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assert dtos.length == 1
        NCubeInfoDto dto = dtos[0] as NCubeInfoDto
        assert dto.changed
    }

    @Test
    void testConflictOverwriteBranch()
	{
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(BRANCH2, cube, true)
        assertEquals("BE7891140C2404A14A6C093C26B1740C749E815B", cube.sha1())

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH2)
        VersionControl.commitBranch(BRANCH2, dtos)

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals("BE7891140C2404A14A6C093C26B1740C749E815B", cube.sha1())

        cube = NCubeManager.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals("B4020BFB1B47942D8661640E560881E34993B608", cube.sha1())
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        List<NCubeInfoDto> dtos2 = VersionControl.getHeadChangesForBranch(BRANCH1)
        assert dtos2[0].name == 'TestBranch'
        assert dtos2[0].changeType == ChangeType.CONFLICT.code
        assert dtos2[0].sha1 != cube.sha1()

        try
        {
            VersionControl.commitBranch(BRANCH1, dtos)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }

        assertEquals(1, VersionControl.mergeAcceptTheirs(BRANCH1, 'TestBranch'))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testConflictOverwriteBranchWithPreexistingCube()
    {
        preloadCubes(HEAD, "test.branch.3.json")

        NCubeManager.copyBranch(HEAD, BRANCH1)
        NCubeManager.copyBranch(HEAD, BRANCH2)

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(BRANCH2, cube, true)
        assertEquals("BE7891140C2404A14A6C093C26B1740C749E815B", cube.sha1())

        VersionControl.commitBranch(BRANCH2)

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals("BE7891140C2404A14A6C093C26B1740C749E815B", cube.sha1())

        cube = NCubeManager.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals("B4020BFB1B47942D8661640E560881E34993B608", cube.sha1())
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }

        assertEquals(1, VersionControl.mergeAcceptTheirs(BRANCH1, 'TestBranch'))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testConflictAcceptMine()
    {
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(BRANCH2, cube, true)
        VersionControl.commitBranch(BRANCH2)

        cube = NCubeManager.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }

        List<NCubeInfoDto> infos = NCubeManager.search(BRANCH1, 'TestBranch', null, null)
        NCubeInfoDto infoDto = infos[0]
        assert infoDto.headSha1 == null

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(HEAD, 'TestBranch', null, null)
        infoDto = infos[0]
        String saveHeadSha1 = infoDto.sha1
        assert saveHeadSha1 != null

        assertEquals(1, VersionControl.mergeAcceptMine(BRANCH1, "TestBranch"))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(BRANCH1, 'TestBranch', null, null)
        infoDto = infos[0]
        assert saveHeadSha1 == infoDto.headSha1

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(HEAD, 'TestBranch', null, null)
        infoDto = infos[0]
        assert saveHeadSha1 == infoDto.sha1
        assert infoDto.headSha1 == null // HEAD always has a null headSha1
    }

    @Test
    void testConflictAcceptMineWithPreexistingCube()
    {
        preloadCubes(HEAD, "test.branch.3.json")

        NCubeManager.copyBranch(HEAD, BRANCH1)
        NCubeManager.copyBranch(HEAD, BRANCH2)

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(BRANCH2, cube, true)
        VersionControl.commitBranch(BRANCH2)

        cube = NCubeManager.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals("B4020BFB1B47942D8661640E560881E34993B608", cube.sha1())
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }

        List<NCubeInfoDto> infos = NCubeManager.search(BRANCH1, 'TestBranch', null, null)
        NCubeInfoDto infoDto = infos[0]
        assert infoDto.headSha1 != null
        String saveOldHeadSha1 = infoDto.headSha1

        infos = NCubeManager.search(HEAD, 'TestBranch', null, null)
        infoDto = infos[0]
        assert infoDto.headSha1 == null
        String saveHeadSha1 = infoDto.sha1
        assert saveHeadSha1 != null

        assertEquals(1, VersionControl.mergeAcceptMine(BRANCH1, "TestBranch"))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(BRANCH1, 'TestBranch', null, null)
        infoDto = infos[0]
        assert infoDto.headSha1 == saveHeadSha1
        assert infoDto.headSha1 != saveOldHeadSha1

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(HEAD, 'TestBranch', null, null)
        infoDto = infos[0]
        assert infoDto.headSha1 == null
        assert infoDto.sha1 == saveHeadSha1
    }

    @Test
    void testMergeAcceptMineWhenBranchDoesNotExist()
    {
        try
        {
            VersionControl.mergeAcceptMine(appId, "TestBranch")
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.toLowerCase().contains("failed to update branch cube because head cube does not exist"))
        }
    }

    @Test
    void testMergeAcceptMineWhenHEADdoesNotExist()
    {
        try
        {
            preloadCubes(BRANCH1, "test.branch.1.json")
            VersionControl.mergeAcceptMine(appId, "TestBranch")
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.toLowerCase().contains('failed to update branch cube because head cube does not exist'))
        }
    }

    @Test
    void testOverwriteBranchCubeWhenBranchDoesNotExist()
    {
        try
		{
            VersionControl.mergeAcceptTheirs(appId, "TestBranch")
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.toLowerCase().contains("failed to overwrite"))
            assertTrue(e.message.toLowerCase().contains("does not exist"))
        }
    }

    @Test
    void testOverwriteBranchCubeWhenHEADDoesNotExist()
    {
        try
		{
            preloadCubes(BRANCH1, "test.branch.1.json")
            VersionControl.mergeAcceptTheirs(appId, "TestBranch")
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.toLowerCase().contains("failed to overwrite"))
            assertTrue(e.message.toLowerCase().contains("does not exist"))
        }
    }

    @Test
    void testCommitBranchWithExtendedMerge()
    {
        preloadCubes(HEAD, "merge2.json")

        NCube headCube = NCubeManager.getCube(HEAD, 'merge2')

        Map coord = [row:1, column:'A']
        assert "1" == headCube.getCell(coord)

        coord = [row:2, column:'B']
        assert 2 == headCube.getCell(coord)

        coord = [row:3, column:'C']
        assert 3.14159 == headCube.getCell(coord)

        coord = [row:4, column:'D']
        assert 6.28 == headCube.getCell(coord)

        coord = [row:5, column:'E']
        assert headCube.containsCell(coord)

        assert headCube.numCells == 5

        NCubeManager.copyBranch(HEAD, BRANCH1)
        NCubeManager.copyBranch(HEAD, BRANCH2)

        NCube cube1 = NCubeManager.getNCubeFromResource("merge1.json")
        cube1.name = 'merge2'
        NCubeManager.updateCube(BRANCH1, cube1, true)
        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        NCube cube2 = NCubeManager.getNCubeFromResource("merge3.json")
        cube2.name = 'merge2'
        NCubeManager.updateCube(BRANCH2, cube2, true)

        try
        {
            VersionControl.commitBranch(BRANCH2)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testCommitBranchWithExtendedMerge2()
    {
        preloadCubes(HEAD, "merge1.json")

        NCube headCube = NCubeManager.getCube(HEAD, 'merge1')

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


        NCubeManager.copyBranch(HEAD, BRANCH1)
        NCubeManager.copyBranch(HEAD, BRANCH2)

        NCube cube1 = NCubeManager.getCube(BRANCH1, "merge1")
        cube1.setCell(3.14159, [row:3, column:'C'])
        NCubeManager.updateCube(BRANCH1, cube1, true)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        headCube = NCubeManager.getCube(HEAD, "merge1")
        coord = [row:3, column:'C']
        assert 3.14159 == headCube.getCell(coord)

        NCube cube2 = NCubeManager.getCube(BRANCH2, "merge1")
        cube2.setCell('foo', [row:4, column:'D'])
        cube2.removeCell([row:5, column:'E'])
        NCubeManager.updateCube(BRANCH2, cube2, true)

        result = VersionControl.commitBranch(BRANCH2)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        headCube = NCubeManager.getCube(HEAD, "merge1")

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
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH2))

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.2.json")
        NCubeManager.updateCube(BRANCH2, cube, true)

        List<NCubeInfoDto> dtos = VersionControl.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.size())
        VersionControl.commitBranch(BRANCH2, dtos as Object[])

        cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.size())
        String newSha1 = dtos[0].sha1

        try
        {
            VersionControl.commitBranch(BRANCH1, dtos as Object[])
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }

        dtos = NCubeManager.search(HEAD, "TestAge", null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        String sha1 = dtos[0].sha1
        assertNotEquals(sha1, newSha1)

        VersionControl.mergeAcceptMine(BRANCH1, "TestAge")

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        String branchHeadSha1 = dtos[0].headSha1
        assertEquals(1, dtos.size())

        dtos = NCubeManager.search(HEAD, "TestAge", null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(branchHeadSha1, dtos[0].sha1)
    }

    @Test
    void testMergeDeltas()
    {
        preloadCubes(BRANCH1, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH1)
        NCubeManager.deleteBranch(BRANCH1)
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH2))

        NCube headCube = NCubeManager.loadCube(HEAD, 'TestBranch')
        NCube cube = NCubeManager.loadCube(BRANCH1, 'TestBranch')
        NCube cube2 = NCubeManager.loadCube(BRANCH2, 'TestBranch')

        // get original values from the cube
        List<Column> columns = cube.getAxis('Code').columns
        Object addedCell = cube.getCell([Code : 15])
        Object deletedCell = cube.getCell([Code : -10])
        Object defaultCellValue = cube.defaultCellValue
        Map cubeMetaProps = cube.metaProperties
        Map axisMetaProps = cube.getAxis('Code').metaProperties
        Map colMetaProps = cube.getAxis('Code').findColumn(0).metaProperties
        Comparable colVal = cube.getAxis('Code').findColumn(10).value

        // make changes
        cube.addColumn('Code', 20)
        cube.deleteColumn('Code', -15)
        cube.setCell('JKL', [Code : 15])
        cube.removeCell([Code : -10])
        cube.defaultCellValue = 'AAA'
        cube.addMetaProperties([key : 'value' as Object])
        cube.getAxis('Code').addMetaProperties([key : 'value' as Object])
        cube.getAxis('Code').findColumn(0).addMetaProperties([key : 'value' as Object])
        cube.getAxis('Code').findColumn(10).value = 9

        // save changes
        NCubeManager.updateCube(BRANCH1, cube)

        // get our delta list, which should include all the changes we made
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(cube, cube2)
        assertEquals(9, deltas.size())

        // merge deltas into BRANCH2
        cube2.mergeDeltas(deltas)
        NCubeManager.updateCube(BRANCH2, cube2)

        VersionControl.commitBranch(BRANCH2)
        headCube = NCubeManager.loadCube(HEAD, headCube.name)

        // verify cube2 is the same as cube
        assertEquals(columns.size(), cube2.getAxis('Code').columns.size())
        assertEquals(addedCell, cube2.getCell([Code : 15]))
        assertEquals(deletedCell, cube2.getCell([Code : -10]))
        assertEquals(defaultCellValue, cube2.defaultCellValue)
        assertEquals(cubeMetaProps.size(), cube2.metaProperties.size())
        assertEquals(axisMetaProps.size(), cube2.getAxis('Code').metaProperties.size())
        assertEquals(colMetaProps.size(), cube2.getAxis('Code').findColumn(0).metaProperties.size())
        assertEquals(colVal, cube2.getAxis('Code').findColumn(10).value)
    }

    @Test
    void testAddAxis()
    {
        preloadCubes(BRANCH1, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH1)
        NCube headCube = NCubeManager.loadCube(HEAD, 'TestBranch')
        NCube cube = NCubeManager.loadCube(BRANCH1, 'TestBranch')

        // test for add axis
        cube.addAxis(new Axis('Axis', AxisType.DISCRETE, AxisValueType.STRING, false, Axis.SORTED, 2))
        NCubeManager.updateCube(BRANCH1, cube)
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(cube, headCube)
        assertEquals(1, deltas.size())
        cube = NCubeManager.mergeDeltas(BRANCH1, 'TestBranch', deltas)
        assert cube.getAxis('Axis') != null // Verify axis added

        // test for delete axis
        NCubeManager.updateCube(BRANCH1, cube)
        VersionControl.commitBranch(BRANCH1)
        headCube = NCubeManager.loadCube(HEAD, 'TestBranch')
        cube.deleteAxis('Axis')
        NCubeManager.updateCube(BRANCH1, cube)
        deltas = DeltaProcessor.getDeltaDescription(cube, headCube)
        assertEquals(1, deltas.size())
        cube = NCubeManager.mergeDeltas(BRANCH1, 'TestBranch', deltas)
        assert null == cube.getAxis('Axis')
    }

    @Test
    void testMergeOverwriteBranchWithItemsCreatedInBothPlaces()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(BRANCH1, "test.branch.1.json")
        VersionControl.commitBranch(BRANCH1)
        NCubeManager.deleteBranch(BRANCH1)

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertEquals(1,  NCubeManager.copyBranch(HEAD, BRANCH2))

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.2.json")
        NCubeManager.updateCube(BRANCH2, cube, true)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH2)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 1
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(BRANCH1, cube, true)

        result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1

        VersionControl.mergeAcceptTheirs(BRANCH1, "TestAge")
        assertEquals(0, VersionControl.getBranchChangesForHead(BRANCH1).size())
    }

    @Test
    void testCommitBranchWithItemThatWasChangedOnHeadAndInBranchButHasNonconflictingRemovals()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertEquals(1,  NCubeManager.copyBranch(HEAD, BRANCH2))

        NCube cube = NCubeManager.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())
        NCubeManager.updateCube(BRANCH2, cube, true)

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)

        VersionControl.commitBranch(BRANCH2, dtos)

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : -10.0])
        assertEquals(2, cube.cellMap.size())
        NCubeManager.updateCube(BRANCH1, cube, true)

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        VersionControl.commitBranch(BRANCH1, dtos)

        cube = NCubeManager.getCube(HEAD, "TestBranch")
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
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertEquals(1,  NCubeManager.copyBranch(HEAD, BRANCH2))

        NCube cube = NCubeManager.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.setCell(18L, [Code : -10.0])
        assertEquals(3, cube.cellMap.size())
        NCubeManager.updateCube(BRANCH2, cube, true)
        VersionControl.commitBranch(BRANCH2)

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.setCell(19L, [Code : -10.0])
        assertEquals(3, cube.cellMap.size())
        NCubeManager.updateCube(BRANCH1, cube, true)

        try
        {
            VersionControl.commitBranch(BRANCH1)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[VersionControl.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[VersionControl.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testUpdateBranchWithItemThatWasChangedOnHeadAndInBranchWithNonConflictingChanges()
    {
        preloadCubes(HEAD, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertEquals(1,  NCubeManager.copyBranch(HEAD, BRANCH2))

        NCube cube = NCubeManager.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code: 10.0])
        assertEquals(2, cube.cellMap.size())
        cube.setCell(18L, [Code: 15])
        NCubeManager.updateCube(BRANCH2, cube, true)
        VersionControl.commitBranch(BRANCH2)

        cube = NCubeManager.getCube(HEAD, "TestBranch")

        assertEquals('ZZZ', cube.getCell([Code: -15]))
        assertEquals('ABC', cube.getCell([Code: -10]))
        assertEquals(18L, cube.getCell([Code: 15]))
        assertEquals('ZZZ', cube.getCell([Code: 10]))
        assertEquals(3, cube.cellMap.size())


        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code: -10])
        assertEquals(2, cube.cellMap.size())
        cube.setCell(-19L, [Code: -15])
        assertEquals(3, cube.cellMap.size())
        NCubeManager.updateCube(BRANCH1, cube, true)

        VersionControl.updateBranch(BRANCH1)
        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
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
        assertEquals(1, NCubeManager.copyBranch(HEAD, BRANCH1))
        assertEquals(1,  NCubeManager.copyBranch(HEAD, BRANCH2))

        NCube cube = NCubeManager.getCube(BRANCH2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : 10])
        assertEquals(2, cube.cellMap.size())
        NCubeManager.updateCube(BRANCH2, cube, true)

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH2)
        assertEquals(1, dtos.length)

        VersionControl.commitBranch(BRANCH2, dtos)

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.setCell('AAA', [Code : 10])
        assertEquals(3, cube.cellMap.size())
        NCubeManager.updateCube(BRANCH1, cube, true)

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = VersionControl.updateBranch(BRANCH1)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testGetBranchChanges()
	{
        // load cube with same name, but different structure in TEST branch
        preloadCubes(HEAD, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(HEAD)

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(BRANCH1, "TestBranch"))
        assertNull(NCubeManager.getCube(BRANCH1, "TestAge"))

        NCube cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(HEAD, BRANCH1))

        Object[] dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)

        //  test values on branch
        testValuesOnBranch(BRANCH1)

        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10]))

        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10]))

        // edit branch cube
        cube.removeCell([Code : 10])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(BRANCH1, cube, true))

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        // commit the branch
        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10]))

        // check HEAD hasn't changed.
        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10]))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = VersionControl.commitBranch(BRANCH1, dtos)
        assert (result[VersionControl.BRANCH_ADDS] as Map).size() == 0
        assert (result[VersionControl.BRANCH_DELETES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_UPDATES] as Map).size() == 1
        assert (result[VersionControl.BRANCH_RESTORES] as Map).size() == 0
        assert (result[VersionControl.BRANCH_REJECTS] as Map).size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(HEAD, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(HEAD, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(BRANCH1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(BRANCH1, "TestAge").size())

        // both should be updated now.
        cube = NCubeManager.getCube(BRANCH1, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10]))
        cube = NCubeManager.getCube(HEAD, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10]))

        dtos = VersionControl.getBranchChangesForHead(BRANCH1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testBootstrapWithOverrides()
	{
        ApplicationID id = ApplicationID.getBootVersion('none', 'example')
        assertEquals(new ApplicationID('NONE', 'EXAMPLE', '0.0.0', ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD), id)

        preloadCubes(id, "sys.bootstrap.user.overloaded.json")

        NCube cube = NCubeManager.getCube(id, 'sys.bootstrap')

        // force reload of system params, you wouldn't usually do this because it wouldn't be thread safe this way.
        NCubeManager.clearSysParams()

        // ensure properties are cleared (if empty, this would load the environment version of NCUBE_PARAMS)
        System.setProperty('NCUBE_PARAMS', '{"foo":"bar"}')

        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.28.0', 'SNAPSHOT', 'HEAD'), cube.getCell([env:'DEV']))
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.25.0', 'RELEASE', 'HEAD'), cube.getCell([env:'PROD']))
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.29.0', 'SNAPSHOT', 'baz'), cube.getCell([env:'SAND']))

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.clearSysParams()
        System.setProperty("NCUBE_PARAMS", '{"status":"RELEASE", "app":"UD", "tenant":"foo", "branch":"bar"}')
        assertEquals(new ApplicationID('foo', 'UD', '1.28.0', 'RELEASE', 'bar'), cube.getCell([env:'DEV']))
        assertEquals(new ApplicationID('foo', 'UD', '1.25.0', 'RELEASE', 'bar'), cube.getCell([env:'PROD']))
        assertEquals(new ApplicationID('foo', 'UD', '1.29.0', 'RELEASE', 'bar'), cube.getCell([env:'SAND']))

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.clearSysParams()
        System.setProperty("NCUBE_PARAMS", '{"branch":"bar"}')
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.28.0', 'SNAPSHOT', 'bar'), cube.getCell([env:'DEV']))
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.25.0', 'RELEASE', 'bar'), cube.getCell([env:'PROD']))
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.29.0', 'SNAPSHOT', 'bar'), cube.getCell([env:'SAND']))
    }

    @Test
    void testUserOverloadedClassPath()
	{
        preloadCubes(appId, "sys.classpath.user.overloaded.json", "sys.versions.json")

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.clearSysParams()
        // Check DEV
        NCube cube = NCubeManager.getCube(appId, "sys.classpath")
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
        NCubeManager.clearSysParams()
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
        NCubeManager.clearCache(appId)
        cube = NCubeManager.getCube(appId, "sys.classpath")
        devLoaderAgain = cube.getCell([env:"DEV"])

        assertEquals('file://C:/Development/public/', devLoaderAgain.URLs[0].toString())
        assertEquals('file://C:/Development/private/', devLoaderAgain.URLs[1].toString())
        assertEquals('file://C:/Development/private/groovy/', devLoaderAgain.URLs[2].toString())

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.clearSysParams()
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

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.clearSysParams()

        // Check DEV
        NCube cube = NCubeManager.getCube(appId, "sys.classpath")
        // ensure properties are cleared.
        System.setProperty('NCUBE_PARAMS', '{"foo", "bar"}')

        CdnClassLoader devLoader = cube.getCell([env:"DEV"])
        assertEquals('http://files.cedarsoftware.com/foo/1.31.0-SNAPSHOT/public/', devLoader.URLs[0].toString())
        assertEquals('http://files.cedarsoftware.com/foo/1.31.0-SNAPSHOT/private/', devLoader.URLs[1].toString())
        assertEquals('http://files.cedarsoftware.com/foo/1.31.0-SNAPSHOT/private/groovy/', devLoader.URLs[2].toString())

        // Check INT
        CdnClassLoader intLoader = cube.getCell([env:"INT"])
        assertEquals('http://files.cedarsoftware.com/foo/1.31.0-SNAPSHOT/public/', intLoader.URLs[0].toString())
        assertEquals('http://files.cedarsoftware.com/foo/1.31.0-SNAPSHOT/private/', intLoader.URLs[1].toString())
        assertEquals('http://files.cedarsoftware.com/foo/1.31.0-SNAPSHOT/private/groovy/', intLoader.URLs[2].toString())

        // Check with overload
        cube = NCubeManager.getCube(appId, "sys.classpath")
        System.setProperty("NCUBE_PARAMS", '{"cpBase":"file://C:/Development/"}')

        // int loader is not marked as cached so we recreate this one each time.
        NCubeManager.clearSysParams()
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
        NCubeManager.clearCache(appId)
        cube = NCubeManager.getCube(appId, "sys.classpath")
        devLoaderAgain = cube.getCell([env:"DEV"])

        assertEquals('file://C:/Development/public/', devLoaderAgain.URLs[0].toString())
        assertEquals('file://C:/Development/private/', devLoaderAgain.URLs[1].toString())
        assertEquals('file://C:/Development/private/groovy/', devLoaderAgain.URLs[2].toString())

        // Check version overload only
        NCubeManager.clearCache(appId)
        NCubeManager.clearSysParams()
        System.setProperty("NCUBE_PARAMS", '{"version":"1.28.0"}')
        // SAND hasn't been loaded yet so it should give us updated values based on the system params.
        URLClassLoader loader = cube.getCell([env:"SAND"])
        assertEquals('http://files.cedarsoftware.com/foo/1.28.0/public/', loader.URLs[0].toString())
        assertEquals('http://files.cedarsoftware.com/foo/1.28.0/private/', loader.URLs[1].toString())
        assertEquals('http://files.cedarsoftware.com/foo/1.28.0/private/groovy/', loader.URLs[2].toString())
    }

    @Test
    void testClearCacheWithClassLoaderLoadedByCubeRequest()
	{

        preloadCubes(appId, "sys.classpath.cp1.json", "GroovyMethodClassPath1.json")

        assertEquals(0, NCubeManager.getCacheForApp(appId).size())
        NCube cube = NCubeManager.getCube(appId, "GroovyMethodClassPath1")
        assertEquals(1, NCubeManager.getCacheForApp(appId).size())

        Map input = new HashMap()
        input.put("method", "foo")
        Object x = cube.getCell(input)
        assertEquals("foo", x)

        assertEquals(3, NCubeManager.getCacheForApp(appId).size())

        input.put("method", "foo2")
        x = cube.getCell(input)
        assertEquals("foo2", x)

        input.put("method", "bar")
        x = cube.getCell(input)
        assertEquals("Bar", x)

        // change classpath in database only
        NCube[] cp2 = TestingDatabaseHelper.getCubesFromDisk("sys.classpath.cp2.json")
        manager.updateCube(appId, USER_ID, cp2[0])
        assertEquals(3, NCubeManager.getCacheForApp(appId).size())

        // reload hasn't happened in cache so we get same answers as above
        input = new HashMap()
        input.put("method", "foo")
        x = cube.getCell(input)
        assertEquals("foo", x)

        input.put("method", "foo2")
        x = cube.getCell(input)
        assertEquals("foo2", x)

        input.put("method", "bar")
        x = cube.getCell(input)
        assertEquals("Bar", x)

        //  clear cache so we get different answers this time.  classpath 2 has already been loaded in database.
        NCubeManager.clearCache(appId)

        assertEquals(0, NCubeManager.getCacheForApp(appId).size())

        cube = NCubeManager.getCube(appId, "GroovyMethodClassPath1")
        assertEquals(1, NCubeManager.getCacheForApp(appId).size())

        input = new HashMap()
        input.put("method", "foo")
        x = cube.getCell(input)
        assertEquals("boo", x)

        assertEquals(3, NCubeManager.getCacheForApp(appId).size())

        input.put("method", "foo2")
        x = cube.getCell(input)
        assertEquals("boo2", x)

        input.put("method", "bar")
        x = cube.getCell(input)
        assertEquals("far", x)
    }

    @Test
    void testMultiCubeClassPath()
	{
        preloadCubes(appId, "sys.classpath.base.json", "sys.classpath.json", "sys.status.json", "sys.versions.json", "sys.version.json", "GroovyMethodClassPath1.json")

        assertEquals(0, NCubeManager.getCacheForApp(appId).size())
        NCube cube = NCubeManager.getCube(appId, "GroovyMethodClassPath1")

        // classpath isn't loaded at this point.
        assertEquals(1, NCubeManager.getCacheForApp(appId).size())

        def input = [:]
        input.env = "DEV"
        input.put("method", "foo")
        Object x = cube.getCell(input)
        assertEquals("foo", x)

        assertEquals(5, NCubeManager.getCacheForApp(appId).size())

        // cache hasn't been cleared yet.
        input.put("method", "foo2")
        x = cube.getCell(input)
        assertEquals("foo2", x)

        input.put("method", "bar")
        x = cube.getCell(input)
        assertEquals("Bar", x)

        NCubeManager.clearCache(appId)

        // Had to reget cube so I had a new classpath
        cube = NCubeManager.getCube(appId, "GroovyMethodClassPath1")

        input.env = 'UAT'
        input.put("method", "foo")
        x = cube.getCell(input)

        assertEquals("boo", x)

        assertEquals(5, NCubeManager.getCacheForApp(appId).size())

        input.put("method", "foo2")
        x = cube.getCell(input)
        assertEquals("boo2", x)

        input.put("method", "bar")
        x = cube.getCell(input)
        assertEquals("far", x)

        //  clear cache so we get different answers this time.  classpath 2 has already been loaded in database.
        NCubeManager.clearCache(appId)
        assertEquals(0, NCubeManager.getCacheForApp(appId).size())
    }

    @Test
    void testTwoClasspathsSameAppId()
    {
        preloadCubes(appId, "sys.classpath.2per.app.json", "GroovyExpCp1.json")

        assertEquals(0, NCubeManager.getCacheForApp(appId).size())
        NCube cube = NCubeManager.getCube(appId, "GroovyExpCp1")

        // classpath isn't loaded at this point.
        assertEquals(1, NCubeManager.getCacheForApp(appId).size())

        def input = [:]
        input.env = "a"
        input.state = "OH"
        def x = cube.getCell(input)
        assert 'Hello, world.' == x

        // GroovyExpCp1, sys.classpath, sys.prototype are now both loaded.
        assertEquals(3, NCubeManager.getCacheForApp(appId).size())

        input.env = "b"
        input.state = "TX"
        def y = cube.getCell(input)
        assert 'Goodbye, world.' == y

        // Test JsonFormatter - that it properly handles the URLClassLoader in the sys.classpath cube
        NCube cp1 = NCubeManager.getCube(appId, "sys.classpath")
        String json = cp1.toFormattedJson()

        NCube cp2 = NCube.fromSimpleJson(json)
        cp1.clearSha1()
        cp2.clearSha1()
        String json1 = cp1.toFormattedJson()
        String json2 = cp2.toFormattedJson()
        assertEquals(json1, json2)

        // Test HtmlFormatter - that it properly handles the URLClassLoader in the sys.classpath cube
        String html = cp1.toHtml()
        assert html.contains('http://files.cedarsoftware.com')
    }

    @Test
    void testMathControllerUsingExpressions()
    {
        preloadCubes(appId, "sys.classpath.2per.app.json", "math.controller.json")

        assertEquals(0, NCubeManager.getCacheForApp(appId).size())
        NCube cube = NCubeManager.getCube(appId, "MathController")

        // classpath isn't loaded at this point.
        assertEquals(1, NCubeManager.getCacheForApp(appId).size())
        def input = [:]
        input.env = "a"
        input.x = 5
        input.method = 'square'

        assertEquals(1, NCubeManager.getCacheForApp(appId).size())
        assertEquals(25, cube.getCell(input))
        assertEquals(3, NCubeManager.getCacheForApp(appId).size())

        input.method = 'factorial'
        assertEquals(120, cube.getCell(input))

        // same number of cubes, different cells
        assertEquals(3, NCubeManager.getCacheForApp(appId).size())

        // test that shows you can add an axis to a controller to selectively choose a new classpath
        input.env = "b"
        input.method = 'square'
        assertEquals(5, cube.getCell(input))
        assertEquals(3, NCubeManager.getCacheForApp(appId).size())

        input.method = 'factorial'
        assertEquals(5, cube.getCell(input))
        assertEquals(3, NCubeManager.getCacheForApp(appId).size())
    }

    @Test
    void testClearCache()
    {
        preloadCubes(appId, "sys.classpath.cedar.json", "cedar.hello.json")

        Map input = new HashMap()
        NCube cube = NCubeManager.getCube(appId, 'hello')
        Object out = cube.getCell(input)
        assertEquals('Hello, world.', out)
        NCubeManager.clearCache(appId)

        cube = NCubeManager.getCube(appId, 'hello')
        out = cube.getCell(input)
        assertEquals('Hello, world.', out)
    }

    @Test
    void testMultiTenantApplicationIdBootstrap()
    {
        preloadCubes(appId, "sys.bootstrap.multi.api.json", "sys.bootstrap.version.json")

        def input = [:]
        input.env = "SAND"

        NCube cube = NCubeManager.getCube(appId, 'sys.bootstrap')
        Map<String, ApplicationID> map = cube.getCell(input) as Map
        assertEquals(new ApplicationID("NONE", "APP", "1.15.0", "SNAPSHOT", ApplicationID.TEST_BRANCH), map.get("A"))
        assertEquals(new ApplicationID("NONE", "APP", "1.19.0", "SNAPSHOT", ApplicationID.TEST_BRANCH), map.get("B"))
        assertEquals(new ApplicationID("NONE", "APP", "1.28.0", "SNAPSHOT", ApplicationID.TEST_BRANCH), map.get("C"))

        input.env = "INT"
        map = cube.getCell(input) as Map

        assertEquals(new ApplicationID("NONE", "APP", "1.25.0", "RELEASE", ApplicationID.TEST_BRANCH), map.get("A"))
        assertEquals(new ApplicationID("NONE", "APP", "1.26.0", "RELEASE", ApplicationID.TEST_BRANCH), map.get("B"))
        assertEquals(new ApplicationID("NONE", "APP", "1.27.0", "RELEASE", ApplicationID.TEST_BRANCH), map.get("C"))
    }

    @Test
    void testBootstrapWihMismatchedTenantAndAppForcesWarning()
    {
        ApplicationID zero = new ApplicationID('FOO', 'TEST', '0.0.0', 'SNAPSHOT', 'HEAD')

        preloadCubes(zero, "sys.bootstrap.test.1.json")

        ApplicationID appId = NCubeManager.getApplicationID('FOO', 'TEST', null)
        // ensure cube on disk tenant and app are not loaded (saved as NONE and ncube.test
        assertEquals('FOO', appId.tenant)
        assertEquals('TEST', appId.app)
        assertEquals('1.28.0', appId.version)
        assertEquals('RELEASE', appId.status)
        assertEquals('HEAD', appId.branch)

        manager.removeBranches([zero, HEAD] as ApplicationID[])
    }

    @Test
    void testGetReferenceAxes()
    {
        NCube one = NCubeBuilder.discrete1DAlt
        NCubeManager.updateCube(ApplicationID.testAppId, one, true)
        assert one.getAxis('state').size() == 2
        NCubeManager.addCube(ApplicationID.testAppId, one)

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
        two.addAxis(axis)

        two.setCell('a', [stateSource:'OH'] as Map)
        two.setCell('b', [stateSource:'TX'] as Map)

        String json = two.toFormattedJson()
        NCube reload = NCube.fromSimpleJson(json)
        assert reload.numCells == 2
        assert 'a' == reload.getCell([stateSource:'OH'] as Map)
        assert 'b' == reload.getCell([stateSource:'TX'] as Map)
        assert reload.getAxis('stateSource').reference
        NCubeManager.updateCube(ApplicationID.testAppId, two, true)

        List<AxisRef> axisRefs = NCubeManager.getReferenceAxes(ApplicationID.testAppId)
        assert axisRefs.size() == 1
        AxisRef axisRef = axisRefs[0]

        // Will fail because cube is not RELEASE / HEAD
        try
        {
            NCubeManager.updateReferenceAxes([axisRef] as List) // Update
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('cannot point')
            assert e.message.toLowerCase().contains('reference axis')
            assert e.message.toLowerCase().contains('non-existing cube')
        }
    }

    @Test
    void testMultipleInstanceOfSameReferenceAxis()
    {
        NCube one = NCubeBuilder.discrete1DAlt
        Axis state = one.getAxis('state')
        state.setMetaProperty('nose', 'smell')
        state.setMetaProperty('ear', 'sound')
        state.findColumn('OH').setMetaProperty('foo', 'bar')
        state.findColumn('TX').setMetaProperty('baz', 'qux')
        NCubeManager.updateCube(ApplicationID.testAppId, one, true)
        assert one.getAxis('state').size() == 2
        NCubeManager.addCube(ApplicationID.testAppId, one)

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
        NCubeManager.updateCube(ApplicationID.testAppId, two, true)

        List<AxisRef> axisRefs = NCubeManager.getReferenceAxes(ApplicationID.testAppId)
        assert axisRefs.size() == 2
    }

    @Test
    void testDynamicallyLoadedCode()
    {
        String save = NCubeManager.systemParams[NCUBE_ACCEPTED_DOMAINS]
        NCubeManager.systemParams[NCUBE_ACCEPTED_DOMAINS] = 'org.apache.'
        NCube ncube = NCubeBuilder.discrete1DEmpty
        GroovyExpression exp = new GroovyExpression('''\
import org.apache.commons.collections.primitives.*
@Grab(group='commons-primitives', module='commons-primitives', version='1.0')

Object ints = new ArrayIntList()
ints.add(42)
assert ints.size() == 1
assert ints.get(0) == 42
return ints''', null, false)
        ncube.setCell(exp, [state: 'OH'])
        def x = ncube.getCell([state: 'OH'])
        assert 'org.apache.commons.collections.primitives.ArrayIntList' == x.class.name

        if (save)
        {
            NCubeManager.systemParams[NCUBE_ACCEPTED_DOMAINS] = save
        }
        else
        {
            NCubeManager.systemParams.remove(NCUBE_ACCEPTED_DOMAINS)
        }
    }

    @Test
    void testSearchIncludeFilter()
    {
        preloadCubes(appId, "testCube1.json", "testCube3.json", "test.branch.1.json", "delta.json", "deltaRule.json", "basicJump.json", "basicJumpStart.json")

        // Mark TestCube as red
        NCube testCube = NCubeManager.getCube(appId, 'TestCube')
        testCube.setMetaProperty("cube_tags", "red")
        NCubeManager.updateCube(appId, testCube)

        // Mark TestBranch as red & white
        NCube testBranch = NCubeManager.getCube(appId, 'TestBranch')
        testBranch.addMetaProperties([(NCubeManager.CUBE_TAGS): new CellInfo('string', 'rEd , whiTe', false, false)] as Map)
        NCubeManager.updateCube(appId, testBranch)

        List<NCubeInfoDto> list = NCubeManager.search(appId, null, null, [(NCubeManager.SEARCH_FILTER_INCLUDE):['red', 'white']])
        assert list.size() == 2
        assert 'TestCube' == list[0].name || 'TestBranch' == list[0].name
        assert 'TestCube' == list[1].name || 'TestBranch' == list[1].name

        list = NCubeManager.search(appId, null, null, [(NCubeManager.SEARCH_FILTER_INCLUDE):['red', 'white'], (NCubeManager.SEARCH_FILTER_EXCLUDE):['white', 'blue']])
        assert list.size() == 1
        assert 'TestCube' == list[0].name
    }

    @Test
    void testSearchExcludeFilter()
    {
        preloadCubes(appId, "testCube1.json", "testCube3.json", "test.branch.1.json", "delta.json", "deltaRule.json", "basicJump.json", "basicJumpStart.json")

        // Mark TestCube as red
        NCube testCube = NCubeManager.getCube(appId, 'TestCube')
        testCube.setMetaProperty("cube_tags", "red")
        NCubeManager.updateCube(appId, testCube)

        // Mark TestBranch as red & white
        NCube testBranch = NCubeManager.getCube(appId, 'TestBranch')
        testBranch.setMetaProperty("cube_tags", "red , WHIte")
        NCubeManager.updateCube(appId, testBranch)

        List<NCubeInfoDto> list = NCubeManager.search(appId, null, null, [(NCubeManager.SEARCH_FILTER_EXCLUDE):['red', 'white']])
        assert list.size() == 5
    }

    @Test
    void testMergedAddDefaultColumn()
    {
        preloadCubes(BRANCH2, "mergeDefaultColumn.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'merge')
        producerCube.addColumn('Column', null)

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'merge')
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producerCube, consumerCube)
        NCube merged = NCubeManager.mergeDeltas(BRANCH1, 'merge', deltas)
        Axis axis = merged.getAxis('Column')
        assert axis.hasDefaultColumn()
        assert axis.size() == 4
    }

    @Test
    void testMergedAddRegularColumn()
    {
        preloadCubes(BRANCH2, "mergeDefaultColumn.json")
        VersionControl.commitBranch(BRANCH2)
        NCubeManager.copyBranch(HEAD, BRANCH1)

        NCube producerCube = NCubeManager.loadCube(BRANCH2, 'merge')
        producerCube.addColumn('Column', 'D')

        NCube consumerCube = NCubeManager.loadCube(BRANCH1, 'merge')
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producerCube, consumerCube)
        NCube merged = NCubeManager.mergeDeltas(BRANCH1, 'merge', deltas)
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
        options.put(NCubeManager.SEARCH_DELETED_RECORDS_ONLY, true)

        return NCubeManager.search(appId, pattern, null, options)
    }
}
