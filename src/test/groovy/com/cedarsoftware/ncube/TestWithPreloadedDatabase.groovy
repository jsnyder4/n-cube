package com.cedarsoftware.ncube
import com.cedarsoftware.ncube.exception.BranchMergeException
import com.cedarsoftware.ncube.util.CdnClassLoader
import com.cedarsoftware.util.Converter
import org.junit.After
import org.junit.Before
import org.junit.Test

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
class TestWithPreloadedDatabase
{
    public static String USER_ID = TestNCubeManager.USER_ID

    public static ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, "preloaded", ApplicationID.DEFAULT_VERSION, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
    private static final ApplicationID head = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", ApplicationID.HEAD)
    private static final ApplicationID branch1 = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", "FOO")
    private static final ApplicationID branch2 = new ApplicationID('NONE', "test", "1.28.0", "SNAPSHOT", "BAR")
    private static final ApplicationID branch3 = new ApplicationID('NONE', "test", "1.29.0", "SNAPSHOT", "FOO")
    private static final ApplicationID boot = new ApplicationID('NONE', "test", "0.0.0", "SNAPSHOT", ApplicationID.HEAD)

    ApplicationID[] branches = [head, branch1, branch2, branch3, appId, boot] as ApplicationID[];

    private TestingDatabaseManager manager;

    @Before
    public void setup()
    {
        manager = testingDatabaseManager
        manager.setUp()

        NCubeManager.NCubePersister = getNCubePersister()
    }

    @After
    public void tearDown()
    {
        manager.removeBranches(branches)
        manager.tearDown()
        manager = null;

        NCubeManager.clearCache()
    }

    TestingDatabaseManager getTestingDatabaseManager()
    {
        return TestingDatabaseHelper.testingDatabaseManager
    }

    NCubePersister getNCubePersister()
    {
        return TestingDatabaseHelper.persister
    }

    private preloadCubes(ApplicationID id, String ...names)
    {
        manager.addCubes(id, USER_ID, TestingDatabaseHelper.getCubesFromDisk(names))
    }

    @Test
    void testToMakeSureOldStyleSysClasspathThrowsException() {
        preloadCubes(appId, "sys.classpath.old.style.json")

        // nothing in cache until we try and get the classloader or load a cube.
        assertEquals(0, NCubeManager.getCacheForApp(appId).size())

        //  url classloader has 1 item
        try {
            Map input = [:]
            URLClassLoader loader = NCubeManager.getUrlClassLoader(appId, input)
        } catch (IllegalStateException e) {
            assertTrue(e.message.contains('sys.classpath cube'))
            assertTrue(e.message.contains('exists'))
            assertTrue(e.message.toLowerCase().contains('urlclassloader'))
        }
    }

    @Test
    void testUrlClassLoader() {
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
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains("fail"))
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

        assertEquals(2, NCubeManager.copyBranch(head, branch1))
        assertEquals(2, NCubeManager.copyBranch(head, branch2))
        // version doesn't match one in head, nothing created.
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

        NCubeManager.updateCube(branch1, cube, true)

        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch1)
        assert dtos.size() == 1

        // verify no HEAD changes for branch
        List<NCubeInfoDto> dtos2 = NCubeManager.getHeadChangesForBranch(branch1)
        assert dtos2.size() == 0
        // end verify

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        // ensure that there are no more branch changes after create
        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assert dtos.size() == 0

        ApplicationID headId = head
        assert 1 == NCubeManager.search(headId, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size()
    }

    @Test
    void testMergeWithNoHeadCube()
    {
        NCube cube1 = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCube cube2 = NCubeManager.getNCubeFromResource("test.branch.age.2.json")

        NCubeManager.updateCube(branch1, cube1, true)
        NCubeManager.updateCube(branch2, cube2, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)
    }

    @Test
    void testGetBranchChangesOnceBranchIsDeleted()
    {
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")

        NCubeManager.updateCube(branch1, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        assertTrue(NCubeManager.deleteBranch(branch1))

        // ensure that there are no more branch changes after delete
        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testUpdateBranchOnCubeCreatedInBranch()
    {
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.updateBranch(branch1)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_FASTFORWARDS].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        //  update didn't affect item added locally
        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)
    }

    @Test
    void testRollbackBranchWithPendingAdd()
    {
        preloadCubes(head, "test.branch.1.json")

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)
        Object[] names = [dtos[0].name]
        NCubeManager.rollbackCubes(branch1, names)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0
    }

    @Test
    void testRollbackBranchWithDeletedCube()
    {
        preloadCubes(branch1, "test.branch.1.json")
        Map<String, Object> result = NCubeManager.commitBranch(branch1)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(1, NCubeManager.search(head, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(1, NCubeManager.search(branch1, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())

        NCubeManager.deleteCubes(branch1, ['TestBranch'].toArray())

        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch1)
        Object[] names = [dtos.first().name]
        assertEquals(1, dtos.size())

        List<NCubeInfoDto> dtos2 = NCubeManager.getHeadChangesForBranch(branch1)
        assert dtos2.size() == 0

        // undo delete
        NCubeManager.rollbackCubes(branch1, names)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.size())

        result = NCubeManager.commitBranch(branch1, dtos.toArray())
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0
    }

    @Test
    void testCommitBranchOnCreateThenDeleted() {
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")

        NCubeManager.updateCube(branch1, cube, true)
        NCubeManager.deleteCubes(branch1, ['TestAge'].toArray())

        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.size())

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos.toArray())
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        ApplicationID headId = head
        assertEquals(0, NCubeManager.search(headId, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):false]).size())
    }

    @Test
    void testUpdateBranchOnCreateThenDeleted()
    {
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(branch1, cube, true)
        NCubeManager.deleteCubes(branch1, ['TestAge'],)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)

        Map<String, Object> result = NCubeManager.updateBranch(branch1)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_FASTFORWARDS].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        ApplicationID headId = head
        assertEquals(0, NCubeManager.search(headId, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):false]).size())
    }

    @Test
    void testUpdateBranchWhenSingleCubeWasDeletedinDifferentBranchAndNotChangedInOurBranch()
    {
        preloadCubes(branch1, "test.branch.1.json")
        NCubeManager.commitBranch(branch1, NCubeManager.search(branch1, null, null, null) as Object[])

        NCubeManager.copyBranch(head, branch2)
        NCubeManager.deleteCubes(branch2, ['TestBranch'])

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch2, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        List dtos2 = NCubeManager.getHeadChangesForBranch(branch1)
        assert dtos2.size() == 1
        assert dtos2[0].name == 'TestBranch'
        assert Converter.convert(dtos2[0].revision, long.class) == -1

        result = NCubeManager.updateBranch(branch1)

        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        List deletes = result[NCubeManager.BRANCH_DELETES]
        assert deletes.size() == 1
        NCubeInfoDto dto = deletes[0]
        assert dto.name == 'TestBranch'
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_FASTFORWARDS].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0
    }

    @Test
    void testUpdateBranchWhenCubeWasDeletedInDifferentBranchAndDeletedInOurBranch()
    {
        preloadCubes(branch1, "test.branch.1.json")
        NCubeManager.commitBranch(branch1, null)

        NCubeManager.copyBranch(head, branch2)
        NCubeManager.deleteCubes(branch2, ['TestBranch'])
        NCubeManager.deleteCubes(branch1, ['TestBranch'])

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)
        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1

        result = NCubeManager.updateBranch(branch1)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_FASTFORWARDS].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0
    }

    @Test
    void testCreateBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))

        testValuesOnBranch(head)

        def cube1Sha1 = NCubeManager.getCube(head, "TestBranch").sha1()
        def cube2Sha1 = NCubeManager.getCube(head, "TestAge").sha1()

        Object[] objects = NCubeManager.search(head, "*", null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        for (NCubeInfoDto dto : objects)
        {
            assertNull(dto.headSha1)
        }

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        assertEquals(cube1Sha1, NCubeManager.getCube(branch1, "TestBranch").sha1())
        assertEquals(cube2Sha1, NCubeManager.getCube(branch1, "TestAge").sha1())

        objects = NCubeManager.search(branch1, "*", null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        for (NCubeInfoDto dto : objects)
        {
            assertNotNull(dto.headSha1)
        }

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)
    }

    @Test
    void testCommitBranchWithItemCreatedInBranchOnly()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json")

        NCube cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        assertNull(NCubeManager.getCube(head, "TestAge"))

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))
        assertNull(NCubeManager.getCube(head, "TestAge"))

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.copyBranch(head, branch1))

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        assertNull(NCubeManager.getCube(head, "TestAge"))

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertNull(NCubeManager.getCube(branch1, "TestAge"))
        assertNull(NCubeManager.getCube(head, "TestAge"))

        cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        assertNotNull(cube)
        NCubeManager.updateCube(branch1, cube, true)


        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertNull(NCubeManager.getCube(head, "TestAge"))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.size())

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
    }

    @Test
    void testUpdateBranchWithUpdateOnBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json")

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(head, "TestAge"))
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))
        assertNull(NCubeManager.getCube(branch2, "TestBranch"))
        assertNull(NCubeManager.getCube(branch2, "TestAge"))

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertEquals(1, NCubeManager.copyBranch(head, branch2))

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch2, "TestBranch").size())

        NCube cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(branch1, cube, true))

        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.age.1.json")
        NCubeManager.updateCube(branch1, cubes[0], true)

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)
        NCubeManager.commitBranch(branch1, dtos)

        Map<String, Object> result = NCubeManager.updateBranch(branch2)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        List<NCubeInfoDto> adds = result[NCubeManager.BRANCH_ADDS]
        assert adds[0].name == 'TestAge'

        assert result[NCubeManager.BRANCH_UPDATES].size() == 1
        List<NCubeInfoDto> updates = result[NCubeManager.BRANCH_UPDATES]
        assert updates[0].name == 'TestBranch'

        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_FASTFORWARDS].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch2, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch2, "TestAge").size())

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testCommitBranchOnUpdate() {

        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(head)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))

        NCube cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        //  test values on branch
        testValuesOnBranch(branch1)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(branch1, cube, true))

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        // commit the branch
        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // check head hasn't changed.
        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.size)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 1
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        // both should be updated now.
        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))
        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testGetCubeNamesWithoutBeingAddedToDatabase()
    {
        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.1.json", "test.branch.age.1.json")
        NCubeManager.addCube(branch1, cubes[0])
        NCubeManager.addCube(branch1, cubes[1])
        Set<String> set = NCubeManager.getCubeNames(branch1)
        assertEquals(2, set.size())
        assertTrue(set.contains("TestBranch"))
        assertTrue(set.contains("TestAge"))
    }

    @Test
    void testCommitBranchOnUpdateWithOldInvalidSha1()
    {
        // load cube with same name, but different structure in TEST branch
        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.1.json")

        manager.insertCubeWithNoSha1(head, USER_ID, cubes[0])

        //assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").length)
        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(branch1, "TestAge"))

        assertEquals(1, NCubeManager.copyBranch(head, branch1))

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)

        NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2")

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch2"))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())

        // No changes have happened yet, even though sha1 is incorrect,
        // we just copy the sha1 when we create the branch so the headsha1 won't
        // differ until we make a change.
        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)

        result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testCommitBranchWithUpdateAndWrongRevisionNumber() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        NCube cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(branch1, cube, true))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)
        ((NCubeInfoDto)dtos[0]).revision = Long.toString(100)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 1
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0
    }



    @Test
    void testRollback()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(head)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))

        NCube cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        //  test values on branch
        testValuesOnBranch(branch1)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // edit branch cube
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(branch1, cube, true))
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())

        cube.setCell("FOO", [Code : 10.0])
        assertEquals(3, cube.cellMap.size())
        assertEquals("FOO", cube.getCell([Code : 10.0]))

        assertTrue(NCubeManager.updateCube(branch1, cube, true))
        assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())

        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10.0]))

        assertTrue(NCubeManager.updateCube(branch1, cube, true))
        assertEquals(4, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())

        cube.setCell("FOO", [Code : 10.0])
        assertEquals(3, cube.cellMap.size())
        assertEquals("FOO", cube.getCell([Code : 10.0]))

        assertTrue(NCubeManager.updateCube(branch1, cube, true))
        assertEquals(5, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)
        dtos[0] = dtos[0].name
        assertEquals(1, NCubeManager.rollbackCubes(branch1, dtos))

        assertEquals(6, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testCommitBranchOnDelete() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(head)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))

        NCube cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        //  test values on branch
        testValuesOnBranch(branch1)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.deleteCubes(branch1, ['TestBranch'].toArray()))

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())

        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        // cube is deleted
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))

        // check head hasn't changed.
        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  loads in both TestAge and TestBranch though only TestBranch has changed.
        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.size())

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos.toArray())
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        // both should be updated now.
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(head, "TestBranch"))
    }

    @Test
    void testSearch() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json", "TestCubeLevelDefault.json", "basicJumpStart.json")
        testValuesOnBranch(head)

        //  delete and re-add these cubes.
        assertEquals(4, NCubeManager.copyBranch(head, branch1))
        NCubeManager.deleteCubes(branch1, ['TestBranch'].toArray())
        NCubeManager.deleteCubes(branch1, ['TestAge'].toArray())

        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.size())
        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos.toArray())
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 2
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(branch1, cube, true)
        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.size())
        result = NCubeManager.commitBranch(branch1, dtos.toArray())
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 1
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        // test with default options
        assertEquals(4, NCubeManager.search(head, null, null, null).size())
        assertEquals(4, NCubeManager.search(head, "", "", null).size())
        assertEquals(2, NCubeManager.search(head, "Test*", null, null).size())
        assertEquals(1, NCubeManager.search(head, "Test*", "zzz", null).size())
        assertEquals(0, NCubeManager.search(head, "*Codes*", "ZZZ", null).size())
        assertEquals(1, NCubeManager.search(head, "*Codes*", "OH", null).size())
        assertEquals(1, NCubeManager.search(head, null, "ZZZ", null).size())

        Map map = new HashMap()

        // test with default options and valid map
        assertEquals(4, NCubeManager.search(head, null, null, map).size())
        assertEquals(4, NCubeManager.search(head, "", "", map).size())
        assertEquals(2, NCubeManager.search(head, "Test*", null, map).size())
        assertEquals(1, NCubeManager.search(head, "Test*", "zzz", map).size())
        assertEquals(0, NCubeManager.search(head, "*Codes*", "ZZZ", map).size())
        assertEquals(1, NCubeManager.search(head, "*Codes*", "OH", map).size())
        assertEquals(1, NCubeManager.search(head, null, "ZZZ", map).size())

        map = new HashMap()
        map.put(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY, true)

        assertEquals(3, NCubeManager.search(head, null, null, map).size())
        assertEquals(3, NCubeManager.search(head, "", "", map).size())
        assertEquals(1, NCubeManager.search(head, "Test*", null, map).size())
        assertEquals(0, NCubeManager.search(head, "Test*", "zzz", map).size())
        assertEquals(0, NCubeManager.search(head, "*Codes*", "ZZZ", map).size())
        assertEquals(1, NCubeManager.search(head, "*Codes*", "OH", map).size())
        assertEquals(0, NCubeManager.search(head, null, "ZZZ", map).size())

        map.put(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY, false)
        map.put(NCubeManager.SEARCH_DELETED_RECORDS_ONLY, true)

        assertEquals(1, NCubeManager.search(head, null, null, map).size())
        assertEquals(1, NCubeManager.search(head, "", "", map).size())
        assertEquals(1, NCubeManager.search(head, "Test*", null, map).size())
        assertEquals(1, NCubeManager.search(head, "Test*", "zzz", map).size())
        assertEquals(0, NCubeManager.search(head, "*Codes*", "ZZZ", map).size())
        assertEquals(0, NCubeManager.search(head, "*Codes*", "OH", map).size())
        assertEquals(1, NCubeManager.search(head, null, "ZZZ", map).size())

        map.put(NCubeManager.SEARCH_DELETED_RECORDS_ONLY, false)
        map.put(NCubeManager.SEARCH_CHANGED_RECORDS_ONLY, true)

//        assertEquals(2, NCubeManager.search(head, null, null, map).size())
//        assertEquals(2, NCubeManager.search(head, "", "", map).size())
//        assertEquals(2, NCubeManager.search(head, "Test*", null, map).size())
//        assertEquals(2, NCubeManager.search(head, "Test*", "zzz", map).size())
//        assertEquals(2, NCubeManager.search(head, "*Codes*", "ZZZ", map).size())
//        assertEquals(2, NCubeManager.search(head, "*Codes*", "OH", map).size())
//        assertEquals(2, NCubeManager.search(head, null, "ZZZ", map).size())
    }

    @Test
    void testSearchAdvanced() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json", "basicJumpStart.json", "expressionAxis.json")
        testValuesOnBranch(head)

        Map map = new HashMap()
        map.put(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY, true)

        assertEquals(2, NCubeManager.search(head, "Test*", null, map).size())
        assertEquals(1, NCubeManager.search(head, "TestBranch", "ZZZ", map).size())
        assertEquals(1, NCubeManager.search(head, "*basic*", "input", map).size())
        assertEquals(0, NCubeManager.search(head, "*Test*", "input", map).size())
        assertEquals(1, NCubeManager.search(head, "*Branch", "ZZZ", map).size())
        assertEquals(2, NCubeManager.search(head, null, "ZZZ", map).size())
        assertEquals(2, NCubeManager.search(head, "", "ZZZ", map).size())
        assertEquals(2, NCubeManager.search(head, "*", "output", map).size())
        assertEquals(0, NCubeManager.search(head, "*axis", "input", map).size())
    }

    @Test
    void testSearchWildCardAndBrackets()
    {
        String cubeName = 'bracketsInString'
        preloadCubes(head, cubeName + '.json')

        Map map = [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY): true]

        NCube cube = NCubeManager.getCube(head, cubeName)
        String value = cube.getCell([axis1: 'column1', axis2: 'column2'])
        assertEquals('testValue[A]', value)

        //Test search with content value containing brackets, with or without wildcard
        assertEquals(1, NCubeManager.search(head, cubeName, 'testValue[A]', map).size())
        assertEquals(1, NCubeManager.search(head, cubeName, 'testValue', map).size())
        assertEquals(1, NCubeManager.search(head, cubeName, 'Value[A]', map).size())
        assertEquals(1, NCubeManager.search(head, cubeName, '*Value*', map).size())
        assertEquals(1, NCubeManager.search(head, cubeName, '*', map).size())
        assertEquals(1, NCubeManager.search(head, cubeName, null, map).size())
        assertEquals(0, NCubeManager.search(head, cubeName, 'somethingElse', map).size())

        //Test search with cube name pattern, with or without wildcard, not exact match
        assertEquals(1, NCubeManager.search(head, '*racketsIn*', null, map).size())
        assertEquals(1, NCubeManager.search(head, 'racketsIn', null, map).size())

        //Test search with cube name pattern, with or without wildcard, exact match
        map[NCubeManager.SEARCH_EXACT_MATCH_NAME] = true
        assertEquals(1, NCubeManager.search(head, cubeName, null, map).size())
        assertEquals(0, NCubeManager.search(head, '*racketsIn*', null, map).size())
        assertEquals(0, NCubeManager.search(head, 'racketsIn', null, map).size())
    }


    @Test
    void testUpdateBranchAfterDelete()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(head)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))

        NCube cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        //  test values on branch
        testValuesOnBranch(branch1)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        // update the new edited cube.
        assertTrue(NCubeManager.deleteCubes(branch1, ['TestBranch'].toArray()))

        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())

        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        // cube is deleted
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))

        // check head hasn't changed.
        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  loads in both TestAge and TestBranch though only TestBranch has changed.
        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        Map<String, Object> result = NCubeManager.updateBranch(branch1)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_FASTFORWARDS].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(head, "TestBranch"))
    }

    @Test
    void testCreateBranchThatAlreadyExists() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        //1) should work
        NCubeManager.copyBranch(head, branch1)

        try {
            //2) should already be created.
            NCubeManager.copyBranch(head, branch1)
            fail()
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("already exists"))
        }
    }

    @Test
    void testReleaseCubes()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        assertNotNull(cube)
        NCubeManager.updateCube(branch1, cube, true)

        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        testValuesOnBranch(branch1, "FOO")

        int rows = NCubeManager.releaseCubes(head, "1.29.0")
        assertEquals(2, rows)

        Map<String, List<String>> versions = NCubeManager.getVersions(head.tenant, head.app)
        assert versions.size() == 2
        assert versions.SNAPSHOT.size() == 2
        assert versions.SNAPSHOT.contains('1.29.0')
        assert versions.RELEASE.size() == 1
        assert versions.RELEASE.contains('1.28.0')

        assertNull(NCubeManager.getCube(branch1, "TestAge"))
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(head, "TestAge"))
        assertNull(NCubeManager.getCube(head, "TestBranch"))

//        ApplicationID newSnapshot = head.createNewSnapshotId("1.29.0")
        ApplicationID newBranchSnapshot = branch1.createNewSnapshotId("1.29.0")

        ApplicationID release = head.asRelease()

        testValuesOnBranch(release)
        testValuesOnBranch(newBranchSnapshot, "FOO")

        manager.removeBranches([release, newBranchSnapshot] as ApplicationID[])
    }


    @Test
    void testDuplicateCubeChanges() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        NCubeManager.duplicate(head, branch2, "TestBranch", "TestBranch2")
        NCubeManager.duplicate(head, branch2, "TestAge", "TestAge")

        // assert head and branch are still there
        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        //  Test with new name.
        NCube cube = NCubeManager.getCube(branch2, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(branch2, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
    }

    @Test
    void testDuplicateCubeGoingToDifferentApp() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))
        assertNull(NCubeManager.getCube(appId, "TestBranch"))
        assertNull(NCubeManager.getCube(appId, "TestAge"))

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)
        assertNull(NCubeManager.getCube(appId, "TestBranch"))
        assertNull(NCubeManager.getCube(appId, "TestAge"))

        NCubeManager.duplicate(branch1, appId, "TestBranch", "TestBranch")
        NCubeManager.duplicate(head, appId, "TestAge", "TestAge")

        // assert head and branch are still there
        testValuesOnBranch(head)
        testValuesOnBranch(branch1)
        testValuesOnBranch(appId)
    }

    @Test
    void testDuplicateCubeOnDeletedCube() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json")

        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertTrue(NCubeManager.deleteCubes(branch1, ['TestBranch']))

        try
        {
    NCubeManager.duplicate(branch1, appId, "TestBranch", "TestBranch")
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains("Unable to duplicate"))
            assertTrue(e.message.contains("deleted"))
        }
    }

    @Test
    void testRenameCubeOnDeletedCube() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json")

        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertTrue(NCubeManager.deleteCubes(branch1, ['TestBranch']))

        try
        {
    NCubeManager.renameCube(branch1, "TestBranch", "Foo")
            fail()
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains("cannot be rename"))
            assertTrue(e.message.contains("Deleted cubes"))
        }
    }

    @Test
    void testDuplicateWhenCubeWithNameAlreadyExists() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        try
        {
    NCubeManager.duplicate(branch1, branch1, "TestBranch", "TestAge")
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

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        assert NCubeManager.renameCube(branch1, "TestBranch", "TestAge")
    }

    @Test
    void testRenameCubeWithHeadHavingCubeAAndCubeBDeleted() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(head, "*").size())

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(branch1, "*").size())

        assertTrue(NCubeManager.deleteCubes(branch1, ['TestBranch'].toArray()))
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())
        assertTrue(NCubeManager.deleteCubes(branch1, ['TestAge'].toArray()))
        assertEquals(2, getDeletedCubesFromDatabase(branch1, "*").size())

        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 2
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertNull(NCubeManager.getCube(head, "TestBranch"))
        assertNull(NCubeManager.getCube(head, "TestAge"))

        assertEquals(2, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, getDeletedCubesFromDatabase(head, "*").size())

        NCubeManager.restoreCubes(branch1, "TestBranch")
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())
        assertNull(NCubeManager.getCube(branch1, "TestAge"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestAge"))
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch1, "TestAge"))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)
        assertEquals('TestAge', dtos[0].name)

        result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 1
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertNull(NCubeManager.getCube(head, "TestBranch"))
        assertNotNull(NCubeManager.getCube(head, "TestAge"))

        assertTrue(NCubeManager.renameCube(branch1, "TestAge", "TestBranch"))
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())
        assertNull(NCubeManager.getCube(branch1, "TestAge"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)

        result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 1
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0
    }

    @Test
    void testRenameCubeWithBothCubesCreatedOnBranch()
    {
        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.1.json", "test.branch.age.1.json")
        NCubeManager.updateCube(branch1, cubes[0], true)
        NCubeManager.updateCube(branch1, cubes[1], true)

        assertNull(NCubeManager.getCube(head, "TestBranch"))
        assertNull(NCubeManager.getCube(head, "TestAge"))
        assertNotNull(NCubeManager.getCube(branch1, "TestAge"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))

        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2"))

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(branch1, "TestAge"))


        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 2
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertNull(NCubeManager.getCube(head, "TestBranch"))
        assertNotNull(NCubeManager.getCube(head, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(head, "TestAge"))

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch2").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(head, "*").size())

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch2", "TestBranch"))

        assertNull(NCubeManager.getCube(branch1, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch1, "TestAge"))

        assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)
        result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(head, "TestBranch2").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(head, "*").size())
    }

    @Test
    void testRenameCubeWhenNewNameAlreadyExistsButIsInactive() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        NCubeManager.deleteCubes(branch1, ['TestAge'].toArray())

        assertNull(NCubeManager.getCube(branch1, "TestAge"))
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())

        //  cube is deleted so won't throw exception
        NCubeManager.renameCube(branch1, "TestBranch", "TestAge")

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())
    }

    @Test
    void testDuplicateCubeWhenNewNameAlreadyExistsButIsInactive() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        NCubeManager.deleteCubes(branch1, ['TestAge'].toArray())

        assertNull(NCubeManager.getCube(branch1, "TestAge"))
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())

        //  cube is deleted so won't throw exception
        NCubeManager.duplicate(branch1, branch1, "TestBranch", "TestAge")

        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(0, getDeletedCubesFromDatabase(branch1, "*").size())
    }

    @Test
    void testRenameAndThenRenameAgainThenRollback()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2"))

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())
        assertEquals(2, NCubeManager.getBranchChangesForHead(branch1).size())

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch2", "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)

        assertNull(NCubeManager.getCube(branch1, "TestBranch2"))
        assertEquals(0, NCubeManager.rollbackCubes(branch1, dtos))

        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestBranch2"))
    }

    @Test
    void testRenameAndThenRollback()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2"))

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch2"))
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())
        assertEquals(2, NCubeManager.getBranchChangesForHead(branch1).size())

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        dtos[0] = dtos[0].name
        dtos[1] = dtos[1].name
        assertEquals(2, dtos.length)

        assertEquals(2, NCubeManager.rollbackCubes(branch1, dtos))

        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestBranch2"))
    }

    @Test
    void testRenameAndThenCommitAndThenRenameAgainWithCommit()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2"))

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(branch1, "TestAge"))

        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())
        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertNull(NCubeManager.getCube(head, "TestBranch"))
        assertNotNull(NCubeManager.getCube(head, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(head, "TestAge"))

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch2", "TestBranch"))

        assertNull(NCubeManager.getCube(branch1, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch1, "TestAge"))

        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        dtos = NCubeManager.getBranchChangesForHead(branch1)

        assertEquals(2, dtos.length)
        result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 1
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertNull(NCubeManager.getCube(head, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(head, "TestBranch"))
        assertNotNull(NCubeManager.getCube(head, "TestAge"))
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
        Object[] dtos = NCubeManager.getBranchChangesForHead(branch)
        assertEquals(2, dtos.length)

        assertTrue(NCubeManager.renameCube(branch, "TestBranch2", "TestBranch"))

        assertNull(NCubeManager.getCube(branch, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(branch, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch, "TestAge"))

        assertEquals(2, NCubeManager.getRevisionHistory(branch, "TestBranch2").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch, "TestBranch").size())
        dtos = NCubeManager.getBranchChangesForHead(branch)
        assertEquals(0, dtos.length)

        //  techniacally don't have to do this since there aren't any changes,
        //  but we should verify we work with 0 dtos passed in, too.  :)
        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertNotNull(NCubeManager.getCube(branch, "TestBranch"))
        assertNull(NCubeManager.getCube(branch, "TestBranch2"))
    }

    @Test
    void testRenameAndThenRenameAgainThenCommitWhenNotCreatedFromBranch()
    {
        // load cube with same name, but different structure in TEST branch
        NCube[] cubes = TestingDatabaseHelper.getCubesFromDisk("test.branch.1.json", "test.branch.age.1.json")

        NCubeManager.updateCube(branch1, cubes[0], true)
        NCubeManager.updateCube(branch1, cubes[1], true)

        testValuesOnBranch(branch1)

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2"))

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, "*").size())
        assertEquals(2, NCubeManager.getBranchChangesForHead(branch1).size())

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch2", "TestBranch"))
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)

        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestBranch2"))

        assertNull(NCubeManager.getCube(branch1, "TestBranch2"))
        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 2
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertNotNull(NCubeManager.getCube(head, "TestBranch"))
        assertNotNull(NCubeManager.getCube(head, "TestBranch"))
        assertNull(NCubeManager.getCube(head, "TestBranch2"))
    }


    @Test
    void testRenameCubeBasicCase() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2"))

        assertNotNull(NCubeManager.getCube(branch1, "TestBranch2"))
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))

        testValuesOnBranch(head)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertNotNull(NCubeManager.getCube(head, "TestBranch2"))
        assertNotNull(NCubeManager.getCube(head, "TestAge"))

        //  Test with new name.
        NCube cube = NCubeManager.getCube(branch1, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(branch1, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
    }

    @Test
    void testRenameCubeBasicCaseWithNoHead() {
        // load cube with same name, but different structure in TEST branch
        NCube cube1 = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCube cube2 = NCubeManager.getNCubeFromResource("test.branch.age.1.json")

        NCubeManager.updateCube(branch1, cube1, true)
        NCubeManager.updateCube(branch1, cube2, true)
        testValuesOnBranch(branch1)

        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.size())

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2"))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.size())

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos.toArray())
        assert result[NCubeManager.BRANCH_ADDS].size() == 2
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        //  Test with new name.
        NCube cube = NCubeManager.getCube(branch1, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(branch1, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))

        cube = NCubeManager.getCube(head, "TestBranch2")
        assertEquals("ABC", cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(head, "TestAge")
        assertEquals("youth", cube.getCell(["Code": 10]))
        assertNull(NCubeManager.getCube(head, "TestBranch"))
    }

    @Test
    void testRenameCubeFunctionality() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        try
        {
            NCubeManager.getRevisionHistory(head, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException ignore) { }

        try
        {
            NCubeManager.getRevisionHistory(branch1, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) { }

        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch1, null).size())

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2"))

        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, null).size())


        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())

        try
        {
            NCubeManager.getRevisionHistory(head, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) { }


        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        dtos[0] = dtos[0].name
        dtos[1] = dtos[1].name
        assertEquals(2, dtos.length)

        assertEquals(2, NCubeManager.rollbackCubes(branch1, dtos))

        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, null).size())

        assertEquals(0, NCubeManager.getBranchChangesForHead(branch1).size())

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        try
        {
            NCubeManager.getRevisionHistory(head, "TestBranch2")
            fail()
        }
        catch (IllegalArgumentException ignore) { }

        assert 2 == NCubeManager.getRevisionHistory(branch1, "TestBranch2").size()

        assertTrue(NCubeManager.renameCube(branch1, "TestBranch", "TestBranch2"))

        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, null).size())

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(4, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())

        try
        {
            NCubeManager.getRevisionHistory(head, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) { }

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(2, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 1
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch2").size())

        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(4, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestBranch2").size())

        assertEquals(1, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(1, getDeletedCubesFromDatabase(branch1, null).size())
    }

    @Test
    void testDuplicateCubeFunctionality()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        testValuesOnBranch(head)

        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        testValuesOnBranch(head)
        testValuesOnBranch(branch1)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        try {
            NCubeManager.getRevisionHistory(head, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        try {
            NCubeManager.getRevisionHistory(branch1, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch2, null).size())

        NCubeManager.duplicate(branch1, branch2, "TestBranch", "TestBranch2")
        NCubeManager.duplicate(branch1, branch2, "TestAge", "TestAge")

        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch2, null).size())


        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch2, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch2, "TestBranch2").size())

        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())

        try {
            NCubeManager.getRevisionHistory(head, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        try {
            NCubeManager.getRevisionHistory(branch2, "TestBranch")
            fail()
        } catch (IllegalArgumentException ignore) {
        }


        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        dtos[0] = dtos[0].name
        assertEquals(1, dtos.length)

        assertEquals(1, NCubeManager.rollbackCubes(branch2, dtos))

        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch1, null).size())

        assertEquals(0, NCubeManager.getBranchChangesForHead(branch1).size())

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        try {
            NCubeManager.getRevisionHistory(head, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        try {
            NCubeManager.getRevisionHistory(branch1, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }


        NCubeManager.duplicate(branch1, branch2, "TestBranch", "TestBranch2")

        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch2, null).size())

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(branch2, "TestAge").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch2, "TestBranch2").size())

        try {
            NCubeManager.getRevisionHistory(head, "TestBranch2")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch2, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch2").size())

        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())

        assertEquals(1, NCubeManager.getRevisionHistory(branch2, "TestAge").size())
        assertEquals(3, NCubeManager.getRevisionHistory(branch2, "TestBranch2").size())

        try
        {
            NCubeManager.getRevisionHistory(branch2, "TestBranch")
            fail()
        } catch (IllegalArgumentException ignore) {
        }

        assertEquals(0, getDeletedCubesFromDatabase(head, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch1, null).size())
        assertEquals(0, getDeletedCubesFromDatabase(branch2, null).size())
    }

    @Test
    void testDuplicateCubeWithNonExistentSource()
    {
        try
        {
    NCubeManager.duplicate(head, branch1, "foo", "bar")
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
        preloadCubes(head, "test.branch.1.json")
        NCubeManager.copyBranch(head, branch1)

        try
        {
    NCubeManager.duplicate(head, branch1, "TestBranch", "TestBranch")
            fail()
        } catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains("Unable to duplicate"))
            assertTrue(e.message.contains("already exists"))
        }
    }

    @Test
    void testOverwriteHeadWhenHeadDoesntExist()
    {
        preloadCubes(head, "test.branch.1.json")
        NCubeManager.copyBranch(head, branch1)
        NCubeManager.deleteCubes(branch1, ['TestBranch'])

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))

        try
        {
    NCubeManager.duplicate(head, branch1, "TestBranch", "TestBranch")
            assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))
            assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        } catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains("Unable to duplicate"))
            assertTrue(e.message.contains("already exists"))
        }
    }

    @Test
    void testDuplicateCubeWhenSourceCubeIsADeletedCube()
    {
        preloadCubes(head, "test.branch.1.json")
        NCubeManager.copyBranch(head, branch1)
        NCubeManager.deleteCubes(branch1, ['TestBranch'])

        assertNull(NCubeManager.getCube(branch1, "TestBranch"))

        try
        {
            NCubeManager.duplicate(head, branch1, "TestBranch", "TestBranch")
            assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))
            assertEquals(3, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
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

        NCubeManager.updateCube(branch1, cubes[0], true)
        assertNotNull(NCubeManager.getCube(branch1, "TestBranch"))

        assertTrue(NCubeManager.deleteCubes(branch1, ['TestBranch'].toArray()))
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))

        try
        {
            NCubeManager.deleteCubes(branch1, ['TestBranch'].toArray())
        }
        catch (IllegalArgumentException e)
        {
            e.message.contains('does not exist')
        }
    }

    private void testValuesOnBranch(ApplicationID appId, String code1 = "ABC", String code2 = "youth") {
        NCube cube = NCubeManager.getCube(appId, "TestBranch")
        assertEquals(code1, cube.getCell(["Code": -10]))
        cube = NCubeManager.getCube(appId, "TestAge")
        assertEquals(code2, cube.getCell(["Code": 10]))
    }

    @Test
    void testCommitBranchWithItemCreatedLocallyAndOnHead() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertEquals(1,  NCubeManager.copyBranch(head, branch2))

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.2.json")
        NCubeManager.updateCube(branch2, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)
        NCubeManager.commitBranch(branch2, dtos)

        // Commit to branch 2 causes 1 pending update for branch1
        List dtos2 = NCubeManager.getHeadChangesForBranch(branch1)
        assert dtos2.size() == 1
        assert dtos2[0].name == 'TestAge'

        cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1
    }

    @Test
    void testCommitWithCubeChangedButMatchesHead() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json")

        NCube cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        assertEquals(1,  NCubeManager.copyBranch(head, branch2))

        cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(branch2, cube, true)

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)

        NCubeManager.commitBranch(branch2, dtos)

        cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(branch1, cube, true)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        List dtos2 = NCubeManager.getHeadChangesForBranch(branch2)
        assert dtos2.size() == 0    // Nothing for branch2 because cube matched HEAD already
    }

    @Test
    void testConflictOverwriteBranch() {
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(branch2, cube, true)
        assertEquals("BE7891140C2404A14A6C093C26B1740C749E815B", cube.sha1())

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        NCubeManager.commitBranch(branch2, dtos)

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals("BE7891140C2404A14A6C093C26B1740C749E815B", cube.sha1())

        cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals("B4020BFB1B47942D8661640E560881E34993B608", cube.sha1())
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        List dtos2 = NCubeManager.getHeadChangesForBranch(branch1)
        assert dtos2[0].name == 'TestBranch'
        assert dtos2[0].changeType == ChangeType.CONFLICT.code
        assert dtos2[0].sha1 != cube.sha1()

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1

        assertEquals(1, NCubeManager.mergeAcceptTheirs(branch1, ["TestBranch"].toArray(), [cube.sha1()].toArray()))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testConflictOverwriteBranchWithPreexistingCube()
    {
        preloadCubes(head, "test.branch.3.json")

        NCubeManager.copyBranch(head, branch1)
        NCubeManager.copyBranch(head, branch2)

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(branch2, cube, true)
        assertEquals("BE7891140C2404A14A6C093C26B1740C749E815B", cube.sha1())

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        NCubeManager.commitBranch(branch2, dtos)

        List dtos2 = NCubeManager.getHeadChangesForBranch(branch1)
        assert dtos2.size() == 1
        assert dtos2[0].name == 'TestBranch'
        assert dtos2[0].sha1 == dtos[0].sha1
        assert dtos2[0].branch != ApplicationID.HEAD
        assert dtos[0].branch == branch2.branch
        assert dtos[0].branch != ApplicationID.HEAD

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals("BE7891140C2404A14A6C093C26B1740C749E815B", cube.sha1())

        cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals("B4020BFB1B47942D8661640E560881E34993B608", cube.sha1())
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1

        assertEquals(1, NCubeManager.mergeAcceptTheirs(branch1, ["TestBranch"] as Object[], cube.sha1()))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
    }

    @Test
    void testWhereCubesWithSameNameMatchButBothWereAddedIndividually()
    {
        preloadCubes(head, "test.branch.2.json")

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(branch2, cube, true)

        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(0, dtos.size())

        Map<String, Object> result = NCubeManager.commitBranch(branch2, dtos.toArray())
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        List dtos2 = NCubeManager.getHeadChangesForBranch(branch1)
        assert dtos2.size() == 0
        dtos2 = NCubeManager.getHeadChangesForBranch(branch2)
        assert dtos2.size() == 1
        assert dtos2.first().changeType == ChangeType.FASTFORWARD.code
    }

    @Test
    void testConflictAcceptMine()
    {
        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(branch2, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        NCubeManager.commitBranch(branch2, dtos)

        cube = NCubeManager.getCube(head, "TestBranch")
        cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1

        cube = NCubeManager.getCube(branch1, "TestBranch")
        List<NCubeInfoDto> infos = NCubeManager.search(branch1, 'TestBranch', null, null)
        NCubeInfoDto infoDto = infos[0]
        assert infoDto.headSha1 == null

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(head, 'TestBranch', null, null)
        infoDto = infos[0]
        String saveHeadSha1 = infoDto.sha1
        assert saveHeadSha1 != null

        assertEquals(1, NCubeManager.mergeAcceptMine(branch1, "TestBranch"))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(branch1, 'TestBranch', null, null)
        infoDto = infos[0]
        assert saveHeadSha1 == infoDto.headSha1

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(head, 'TestBranch', null, null)
        infoDto = infos[0]
        assert saveHeadSha1 == infoDto.sha1
        assert infoDto.headSha1 == null // head always has a null headSha1
    }

    @Test
    void testConflictAcceptMineWithPreexistingCube()
    {
        preloadCubes(head, "test.branch.3.json")

        NCubeManager.copyBranch(head, branch1)
        NCubeManager.copyBranch(head, branch2)

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.2.json")
        NCubeManager.updateCube(branch2, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        NCubeManager.commitBranch(branch2, dtos)

        cube = NCubeManager.getCube(head, "TestBranch")

        cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))

        cube = NCubeManager.getNCubeFromResource("test.branch.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals("B4020BFB1B47942D8661640E560881E34993B608", cube.sha1())
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1

        List<NCubeInfoDto> infos = NCubeManager.search(branch1, 'TestBranch', null, null)
        NCubeInfoDto infoDto = infos[0]
        assert infoDto.headSha1 != null
        String saveOldHeadSha1 = infoDto.headSha1

        infos = NCubeManager.search(head, 'TestBranch', null, null)
        infoDto = infos[0]
        assert infoDto.headSha1 == null
        String saveHeadSha1 = infoDto.sha1
        assert saveHeadSha1 != null

        assertEquals(1, NCubeManager.mergeAcceptMine(branch1, "TestBranch"))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(branch1, 'TestBranch', null, null)
        infoDto = infos[0]
        assert infoDto.headSha1 == saveHeadSha1
        assert infoDto.headSha1 != saveOldHeadSha1

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("BAZ", cube.getCell([Code : 10.0]))
        infos = NCubeManager.search(head, 'TestBranch', null, null)
        infoDto = infos[0]
        assert infoDto.headSha1 == null
        assert infoDto.sha1 == saveHeadSha1
    }

    @Test
    void testMergeAcceptMineWhenBranchDoesNotExist()
    {
        try
        {
    NCubeManager.mergeAcceptMine(appId, "TestBranch")
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
            preloadCubes(branch1, "test.branch.1.json")
    NCubeManager.mergeAcceptMine(appId, "TestBranch")
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
        try {
    NCubeManager.mergeAcceptTheirs(appId, ["TestBranch"] as Object[], "foo")
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
        try {
            preloadCubes(branch1, "test.branch.1.json")
    NCubeManager.mergeAcceptTheirs(appId, ["TestBranch"] as Object[], "foo")
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
        preloadCubes(head, "merge2.json")

        NCube headCube = NCubeManager.getCube(head, 'merge2')

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

        NCubeManager.copyBranch(head, branch1)
        NCubeManager.copyBranch(head, branch2)

        NCube cube1 = NCubeManager.getNCubeFromResource("merge1.json")
        cube1.name = 'merge2'
        NCubeManager.updateCube(branch1, cube1, true)
        Map<String, Object> result = NCubeManager.commitBranch(branch1)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 1
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        NCube cube2 = NCubeManager.getNCubeFromResource("merge3.json")
        cube2.name = 'merge2'
        NCubeManager.updateCube(branch2, cube2, true)
        result = NCubeManager.commitBranch(branch2)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1
    }

    @Test
    void testCommitBranchWithExtendedMerge2()
    {
        preloadCubes(head, "merge1.json")

        NCube headCube = NCubeManager.getCube(head, 'merge1')

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


        NCubeManager.copyBranch(head, branch1)
        NCubeManager.copyBranch(head, branch2)

        NCube cube1 = NCubeManager.getCube(branch1, "merge1")
        cube1.setCell(3.14159, [row:3, column:'C'])
        NCubeManager.updateCube(branch1, cube1, true);

        List<NCubeInfoDto> changes = NCubeManager.getBranchChangesForHead(branch1)
        Map<String, Object> result = NCubeManager.commitBranch(branch1, changes)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 1
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        headCube = NCubeManager.getCube(head, "merge1")
        coord = [row:3, column:'C']
        assert 3.14159 == headCube.getCell(coord)

        NCube cube2 = NCubeManager.getCube(branch2, "merge1")
        cube2.setCell('foo', [row:4, column:'D'])
        cube2.removeCell([row:5, column:'E'])
        NCubeManager.updateCube(branch2, cube2, true)

        changes = NCubeManager.getBranchChangesForHead(branch2)
        result = NCubeManager.commitBranch(branch2, changes)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 1
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        headCube = NCubeManager.getCube(head, "merge1")

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
        preloadCubes(head, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertEquals(1, NCubeManager.copyBranch(head, branch2))

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.2.json")
        NCubeManager.updateCube(branch2, cube, true)

        List<NCubeInfoDto> dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.size)
        NCubeManager.commitBranch(branch2, dtos as Object[])

        cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.size)
        String newSha1 = dtos[0].sha1;

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1

        NCubeInfoDto[] dto = (NCubeInfoDto[])NCubeManager.search(head, "TestAge", null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        String sha1 = dto[0].sha1;
        assertNotEquals(sha1, newSha1)

        NCubeManager.mergeAcceptMine(branch1, "TestAge")

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        String branchHeadSha1 = dtos[0].headSha1
        assertEquals(1, dtos.size)

        dtos = NCubeManager.search(head, "TestAge", null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(branchHeadSha1, dtos[0].sha1)
    }

    @Test
    void testUpdateBranchWithItemCreatedLocallyAndOnHead()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertEquals(1, NCubeManager.copyBranch(head, branch2))

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.2.json")
        NCubeManager.updateCube(branch2, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)
        NCubeManager.commitBranch(branch2, dtos)

        cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)
        assertEquals(dtos[0].changeType, ChangeType.CONFLICT.code)

        Map<String, Object> result = NCubeManager.updateBranch(branch1)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_FASTFORWARDS].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1
    }

    @Test
    void testMergeOverwriteBranchWithItemsCreatedInBothPlaces()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(branch1, "test.branch.1.json")
        NCubeManager.commitBranch(branch1, NCubeManager.search(branch1, null, null, null) as Object[])
        NCubeManager.deleteBranch(branch1)

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertEquals(1,  NCubeManager.copyBranch(head, branch2))

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.age.2.json")
        NCubeManager.updateCube(branch2, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch2, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 1
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        cube = NCubeManager.getNCubeFromResource("test.branch.age.1.json")
        NCubeManager.updateCube(branch1, cube, true)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)
        assertEquals(ChangeType.CONFLICT.code, dtos[0].changeType)

        result = NCubeManager.updateBranch(branch1)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_FASTFORWARDS].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1

        dtos = NCubeManager.search(branch1, "TestAge", null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        String sha1 = dtos[0].sha1

        NCubeManager.mergeAcceptTheirs(branch1, ["TestAge"] as Object[], sha1)

        assertEquals(0, NCubeManager.getBranchChangesForHead(branch1).size())

    }

    @Test
    void testCommitBranchWithItemThatWasChangedOnHeadAndInBranchButHasNonconflictingRemovals()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertEquals(1,  NCubeManager.copyBranch(head, branch2))

        NCube cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : 10.0])
        assertEquals(2, cube.cellMap.size())
        NCubeManager.updateCube(branch2, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)

        NCubeManager.commitBranch(branch2, dtos)

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : -10.0])
        assertEquals(2, cube.cellMap.size())
        NCubeManager.updateCube(branch1, cube, true)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        NCubeManager.commitBranch(branch1, dtos)

        cube = NCubeManager.getCube(head, "TestBranch")
        // cube has default of 'zzz' for non-existing cells
        assertEquals('ZZZ', cube.getCell([Code : -10.0]))
        assertEquals('ZZZ', cube.getCell([Code : 10.0]))
    }

    @Test
    void testCommitBranchWithItemThatWasChangedOnHeadAndInBranchAndConflict()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertEquals(1,  NCubeManager.copyBranch(head, branch2))

        NCube cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.setCell(18L, [Code : -10.0])
        assertEquals(3, cube.cellMap.size())
        NCubeManager.updateCube(branch2, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)

        NCubeManager.commitBranch(branch2, dtos)

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.setCell(19L, [Code : -10.0])
        assertEquals(3, cube.cellMap.size())
        NCubeManager.updateCube(branch1, cube, true)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 0
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 1
    }

    @Test
    void testUpdateBranchWithItemThatWasChangedOnHeadAndInBranchWithNonConflictingChanges()
    {
        testUpdateBranchWithItemThatWasChanegdOnHeadAndInBranchWithNoConflicts({
    NCubeManager.updateBranch(branch1)
        })
    }

    private void testUpdateBranchWithItemThatWasChanegdOnHeadAndInBranchWithNoConflicts(Closure closure)
    {
        preloadCubes(head, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertEquals(1,  NCubeManager.copyBranch(head, branch2))

        NCube cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code: 10.0])
        assertEquals(2, cube.cellMap.size())
        cube.setCell(18L, [Code: 15])
        NCubeManager.updateCube(branch2, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)

        NCubeManager.commitBranch(branch2, dtos)

        cube = NCubeManager.getCube(head, "TestBranch")

        assertEquals('ZZZ', cube.getCell([Code: -15]))
        assertEquals('ABC', cube.getCell([Code: -10]))
        assertEquals(18L, cube.getCell([Code: 15]))
        assertEquals('ZZZ', cube.getCell([Code: 10]))
        assertEquals(3, cube.cellMap.size())


        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code: -10])
        assertEquals(2, cube.cellMap.size())
        cube.setCell(-19L, [Code: -15])
        assertEquals(3, cube.cellMap.size())
        NCubeManager.updateCube(branch1, cube, true)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        closure()
        cube = NCubeManager.getCube(branch1, "TestBranch")
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
        preloadCubes(head, "test.branch.1.json")

        //  create the branch (TestAge, TestBranch)
        assertEquals(1, NCubeManager.copyBranch(head, branch1))
        assertEquals(1,  NCubeManager.copyBranch(head, branch2))

        NCube cube = NCubeManager.getCube(branch2, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : 10])
        assertEquals(2, cube.cellMap.size())
        NCubeManager.updateCube(branch2, cube, true)

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch2)
        assertEquals(1, dtos.length)

        NCubeManager.commitBranch(branch2, dtos)

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        cube.removeCell([Code : -10])
        assertEquals(2, cube.cellMap.size())
        NCubeManager.updateCube(branch1, cube, true)

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        try
        {
    NCubeManager.updateBranch(branch1)
            // TODO: Need better assertion
//            fail()
        }
        catch (BranchMergeException e)
        {
            assert e.message.toLowerCase().contains("conflict(s) updating branch")
            assert e.errors.TestBranch.message.toLowerCase().contains('cube')
            assert e.errors.TestBranch.message.toLowerCase().contains('changed in head')
            assertEquals("5CA932980E050E97E09543F8B79BE08696E0A1A4", e.errors.TestBranch.sha1)
            assertEquals("75EE6BA78989BD3563B9091FFF458E620FEAFDE8", e.errors.TestBranch.headSha1)
        }
    }

    @Test
    void testGetBranchChanges() {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(head, "test.branch.1.json", "test.branch.age.1.json")

        // cubes were preloaded
        testValuesOnBranch(head)

        // pre-branch, cubes don't exist
        assertNull(NCubeManager.getCube(branch1, "TestBranch"))
        assertNull(NCubeManager.getCube(branch1, "TestAge"))

        NCube cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())

        //  create the branch (TestAge, TestBranch)
        assertEquals(2, NCubeManager.copyBranch(head, branch1))

        Object[] dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)

        //  test values on branch
        testValuesOnBranch(branch1)

        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10]))

        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10]))

        // edit branch cube
        cube.removeCell([Code : 10])
        assertEquals(2, cube.cellMap.size())

        // default now gets loaded
        assertEquals("ZZZ", cube.getCell([Code : 10]))

        // update the new edited cube.
        assertTrue(NCubeManager.updateCube(branch1, cube, true))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)


        // Only Branch "TestBranch" has been updated.
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        // commit the branch
        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals(2, cube.cellMap.size())
        assertEquals("ZZZ", cube.getCell([Code : 10]))

        // check head hasn't changed.
        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals(3, cube.cellMap.size())
        assertEquals("GHI", cube.getCell([Code : 10]))

        //  loads in both TestAge and TestBranch through only TestBranch has changed.
        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(1, dtos.length)

        Map<String, Object> result = NCubeManager.commitBranch(branch1, dtos)
        assert result[NCubeManager.BRANCH_ADDS].size() == 0
        assert result[NCubeManager.BRANCH_DELETES].size() == 0
        assert result[NCubeManager.BRANCH_UPDATES].size() == 1
        assert result[NCubeManager.BRANCH_RESTORES].size() == 0
        assert result[NCubeManager.BRANCH_REJECTS].size() == 0

        assertEquals(2, NCubeManager.getRevisionHistory(head, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(head, "TestAge").size())
        assertEquals(2, NCubeManager.getRevisionHistory(branch1, "TestBranch").size())
        assertEquals(1, NCubeManager.getRevisionHistory(branch1, "TestAge").size())

        // both should be updated now.
        cube = NCubeManager.getCube(branch1, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10]))
        cube = NCubeManager.getCube(head, "TestBranch")
        assertEquals("ZZZ", cube.getCell([Code : 10]))

        dtos = NCubeManager.getBranchChangesForHead(branch1)
        assertEquals(0, dtos.length)
    }

    @Test
    void testBootstrapWithOverrides() {
        ApplicationID id = ApplicationID.getBootVersion('none', 'example')
        assertEquals(new ApplicationID('NONE', 'EXAMPLE', '0.0.0', ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD), id)

        preloadCubes(id, "sys.bootstrap.user.overloaded.json")

        NCube cube = NCubeManager.getCube(id, 'sys.bootstrap')

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.systemParams = null;

        // ensure properties are cleared (if empty, this would load the environment version of NCUBE_PARAMS)
        System.setProperty('NCUBE_PARAMS', '{"foo":"bar"}')

        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.28.0', 'SNAPSHOT', 'HEAD'), cube.getCell([env:'DEV']))
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.25.0', 'RELEASE', 'HEAD'), cube.getCell([env:'PROD']))
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.29.0', 'SNAPSHOT', 'baz'), cube.getCell([env:'SAND']))

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.systemParams = null;
        System.setProperty("NCUBE_PARAMS", '{"status":"RELEASE", "app":"UD", "tenant":"foo", "branch":"bar"}')
        assertEquals(new ApplicationID('foo', 'UD', '1.28.0', 'RELEASE', 'bar'), cube.getCell([env:'DEV']))
        assertEquals(new ApplicationID('foo', 'UD', '1.25.0', 'RELEASE', 'bar'), cube.getCell([env:'PROD']))
        assertEquals(new ApplicationID('foo', 'UD', '1.29.0', 'RELEASE', 'bar'), cube.getCell([env:'SAND']))

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.systemParams = null;
        System.setProperty("NCUBE_PARAMS", '{"branch":"bar"}')
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.28.0', 'SNAPSHOT', 'bar'), cube.getCell([env:'DEV']))
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.25.0', 'RELEASE', 'bar'), cube.getCell([env:'PROD']))
        assertEquals(new ApplicationID('NONE', 'UD.REF.APP', '1.29.0', 'SNAPSHOT', 'bar'), cube.getCell([env:'SAND']))
    }

    @Test
    public void testUserOverloadedClassPath() {
        preloadCubes(appId, "sys.classpath.user.overloaded.json", "sys.versions.json")

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.systemParams = null;
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
        NCubeManager.systemParams = null;
        // Check with overload
        System.setProperty("NCUBE_PARAMS", '{"cpBase":"file://C:/Development/"}')

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
        NCubeManager.systemParams = null;
        // Check version overload only
        System.setProperty("NCUBE_PARAMS", '{"version":"1.28.0"}')
        // SAND hasn't been loaded yet so it should give us updated values based on the system params.
        URLClassLoader loader = cube.getCell([env:"SAND"])
        assertEquals('https://www.foo.com/1.28.0/public/', loader.URLs[0].toString())
        assertEquals('https://www.foo.com/1.28.0/private/', loader.URLs[1].toString())
        assertEquals('https://www.foo.com/1.28.0/private/groovy/', loader.URLs[2].toString())
    }

    @Test
    public void testSystemParamsOverloads() {
        preloadCubes(appId, "sys.classpath.system.params.user.overloaded.json", "sys.versions.2.json", "sys.resources.base.url.json")

        // force reload of system params, you wouln't usually do this because it wouldn't be thread safe this way.
        NCubeManager.systemParams = null;

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
        NCubeManager.systemParams = null;
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
        NCubeManager.systemParams = null;
        System.setProperty("NCUBE_PARAMS", '{"version":"1.28.0"}')
        // SAND hasn't been loaded yet so it should give us updated values based on the system params.
        URLClassLoader loader = cube.getCell([env:"SAND"])
        assertEquals('http://files.cedarsoftware.com/foo/1.28.0/public/', loader.URLs[0].toString())
        assertEquals('http://files.cedarsoftware.com/foo/1.28.0/private/', loader.URLs[1].toString())
        assertEquals('http://files.cedarsoftware.com/foo/1.28.0/private/groovy/', loader.URLs[2].toString())
    }

    @Test
    public void testClearCacheWithClassLoaderLoadedByCubeRequest() {

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
    void testMultiCubeClassPath() {

        preloadCubes(appId, "sys.classpath.base.json", "sys.classpath.json", "sys.status.json", "sys.versions.json", "sys.version.json", "GroovyMethodClassPath1.json")

        assertEquals(0, NCubeManager.getCacheForApp(appId).size())
        NCube cube = NCubeManager.getCube(appId, "GroovyMethodClassPath1")

        // classpath isn't loaded at this point.
        assertEquals(1, NCubeManager.getCacheForApp(appId).size())

        def input = [:]
        input.env = "DEV";
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

        input.env = 'UAT';
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

        def input = [:];
        input.env = "SAND";

        NCube cube = NCubeManager.getCube(appId, 'sys.bootstrap')
        Map<String, ApplicationID> map = cube.getCell(input)
        assertEquals(new ApplicationID("NONE", "APP", "1.15.0", "SNAPSHOT", ApplicationID.TEST_BRANCH), map.get("A"))
        assertEquals(new ApplicationID("NONE", "APP", "1.19.0", "SNAPSHOT", ApplicationID.TEST_BRANCH), map.get("B"))
        assertEquals(new ApplicationID("NONE", "APP", "1.28.0", "SNAPSHOT", ApplicationID.TEST_BRANCH), map.get("C"))

        input.env = "INT"
        map = cube.getCell(input)

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

        manager.removeBranches([zero, head] as ApplicationID[])
    }

    @Test
    void testMergeSuccessfullyFromOtherBranchNoHeadCube()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(branch1, "test.branch.3.json")
        preloadCubes(branch2, "test.branch.3.json")

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.3.json")
        cube.setCell('FRI', [code:15])
        NCubeManager.updateCube(branch2, cube, true)

        // TODO: Write when merge between branches is supported.
//        Map results = NCubeManager.updateBranchCube(branch1, cube.name, branch2.branch)
//        assert results[NCubeManager.BRANCH_UPDATES].size() == 0
//        assert results[NCubeManager.BRANCH_MERGES].size() == 1
//        assert results[NCubeManager.BRANCH_CONFLICTS].size() == 0
//        NCube merged = NCubeManager.getCube(branch1, cube.name)
//        assert 'FRI' == merged.getCell([code:15])
    }

    @Test
    void testMergeSuccessfullyFromOtherBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(branch1, "test.branch.3.json")
        preloadCubes(branch2, "test.branch.3.json")
        preloadCubes(head, "test.branch.3.json")

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.3.json")

        // TODO: Test needs to be written when merge between branches is supported.
//        Map results = NCubeManager.updateBranchCube(branch1, cube.name, head.branch)
//        assert results[NCubeManager.BRANCH_UPDATES].size() == 0
//        assert results[NCubeManager.BRANCH_MERGES].size() == 0
//        assert results[NCubeManager.BRANCH_CONFLICTS].size() == 0
//
//        cube.setCell('FRI', [code:15])
//        NCubeManager.updateCube(branch2, cube, true)
//
//        results = NCubeManager.updateBranchCube(branch1, cube.name, branch2.branch)
//        assert results[NCubeManager.BRANCH_UPDATES].size() == 0
//        assert results[NCubeManager.BRANCH_MERGES].size() == 1
//        assert results[NCubeManager.BRANCH_CONFLICTS].size() == 0
//        NCube merged = NCubeManager.getCube(branch1, cube.name)
//        assert 'FRI' == merged.getCell([code:15])
    }

    @Test
    void testMergeConflictFromOtherBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(branch1, "test.branch.3.json")
        preloadCubes(branch2, "test.branch.3.json")

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.3.json")
        cube.setCell('x', [code:0])
        NCubeManager.updateCube(branch2, cube, true)

        // TODO: Write when merge between branches is supported
//        Map results = NCubeManager.updateBranchCube(branch1, cube.name, branch2.branch)
//        assert results[NCubeManager.BRANCH_UPDATES].size() == 0
//        assert results[NCubeManager.BRANCH_MERGES].size() == 0
//        assert results[NCubeManager.BRANCH_CONFLICTS].size() == 1
    }

    @Test
    void testMergeNoCubeInOtherBranch()
    {
        // load cube with same name, but different structure in TEST branch
        preloadCubes(branch1, "test.branch.3.json")

        NCube cube = NCubeManager.getNCubeFromResource("test.branch.3.json")

        // TODO: Write when merge between branches is supported.
//        Map results = NCubeManager.updateBranchCube(branch1, cube.name, branch2.branch)
//        assert results[NCubeManager.BRANCH_UPDATES].size() == 0
//        assert results[NCubeManager.BRANCH_MERGES].size() == 0
//        assert results[NCubeManager.BRANCH_CONFLICTS].size() == 0
    }

    @Test
    void testGetReferenceAxes()
    {
        NCube one = NCubeBuilder.getDiscrete1DAlt()
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
        assert reload.getNumCells() == 2
        assert 'a' == reload.getCell([stateSource:'OH'] as Map)
        assert 'b' == reload.getCell([stateSource:'TX'] as Map)
        assert reload.getAxis('stateSource').isReference()
        NCubeManager.updateCube(ApplicationID.testAppId, two, true)

        List<AxisRef> axisRefs = NCubeManager.getReferenceAxes(ApplicationID.testAppId)
        assert axisRefs.size() == 1
        AxisRef axisRef = axisRefs[0]

        // Will fail because cube is not RELEASE / HEAD
        try
        {
            NCubeManager.updateReferenceAxes([axisRef] as List) // Update
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('cannot point')
            assert e.message.toLowerCase().contains('reference axis')
            assert e.message.toLowerCase().contains('non-existing cube')
        }

//        axisRef.destVersion = ApplicationID.testAppId.version
//        axisRef.destAxisName = 'BadAxe'
//        try
//        {
//    NCubeManager.updateReferenceAxes([axisRef] as List) // Update
//            fail();
//        }
//        catch (IllegalArgumentException e)
//        {
//            e.printStackTrace()
//            assert e.message.toLowerCase().contains('cannot point')
//            assert e.message.toLowerCase().contains('reference axis')
//            assert e.message.toLowerCase().contains('non-existing axis')
//        }
//        axisRef.destAxisName = 'state'
//
//        axisRef.transformApp = axisRef.destApp
//        axisRef.transformVersion = axisRef.destVersion
//        axisRef.transformCubeName = 'nonexist'
//        axisRef.transformMethodName = axisRef.destAxisName
//        try
//        {
//    NCubeManager.updateReferenceAxes([axisRef] as List) // Update
//            fail();
//        }
//        catch (IllegalArgumentException e)
//        {
//            assert e.message.toLowerCase().contains('cannot point')
//            assert e.message.toLowerCase().contains('reference axis')
//            assert e.message.toLowerCase().contains('non-existing cube')
//        }
//
//        axisRef.transformCubeName = axisRef.destCubeName
//        axisRef.transformMethodName = 'nonexist'
//        try
//        {
//    NCubeManager.updateReferenceAxes([axisRef] as List) // Update
//            fail();
//        }
//        catch (IllegalArgumentException e)
//        {
//            assert e.message.toLowerCase().contains('cannot point')
//            assert e.message.toLowerCase().contains('reference axis')
//            assert e.message.toLowerCase().contains('non-existing axis')
//        }
//
//        axisRef.transformApp = null
//        axisRef.transformVersion = null
//        axisRef.transformCubeName = null
//        axisRef.transformMethodName = null
//
//        NCubeManager.loadCube(ApplicationID.testAppId, 'Mongo')
//        axisRef.destVersion = ApplicationID.testAppId.version
//
//        NCubeManager.updateReferenceAxes([axisRef] as List) // Update
//        NCubeManager.loadCube(ApplicationID.testAppId, 'Mongo')     // Loadable after setting version on ref axis back
//        axisRefs = NCubeManager.getReferenceAxes(ApplicationID.testAppId)
//        assert axisRefs.size() == 1
//        axisRef = axisRefs[0]
//        assert axisRef.srcAppId == ApplicationID.testAppId
//        assert axisRef.srcCubeName == two.name
//        assert axisRef.srcAxisName == axis.name
//        assert axisRef.destApp == ApplicationID.testAppId.app
//        assert axisRef.destVersion == ApplicationID.testAppId.version
//        assert axisRef.transformApp == null
//        assert axisRef.transformVersion == null
//        assert axisRef.transformCubeName == null
//        assert axisRef.transformMethodName == null
//
//        // Break reference and verify broken (also verify it does not show up as reference axis anymore from the persister)
//        reload.breakAxisReference('stateSource')
//        assert reload.getNumCells() == 2
//        assert 'a' == reload.getCell([stateSource:'OH'] as Map)
//        assert 'b' == reload.getCell([stateSource:'TX'] as Map)
//        assert !reload.getAxis('stateSource').isReference()
//        NCubeManager.updateCube(ApplicationID.testAppId, reload)
//        axisRefs = NCubeManager.getReferenceAxes(ApplicationID.testAppId)
//        assert axisRefs.isEmpty()
    }

    @Test
    void testDynamicallyLoadedCode()
    {
        System.getProperties().setProperty(CdnClassLoader.NCUBE_ACCEPTED_DOMAINS, 'org.apache.')
        NCube ncube = NCubeBuilder.getDiscrete1DEmpty()
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
//        println x
        assert 'org.apache.commons.collections.primitives.ArrayIntList' == x.getClass().getName()
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
