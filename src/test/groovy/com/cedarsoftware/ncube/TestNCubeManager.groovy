package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.CommandCellException
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.ncube.formatters.HtmlFormatter
import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.DeepEquals
import com.cedarsoftware.util.UniqueIdGenerator
import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static com.cedarsoftware.ncube.ReferenceAxisLoader.*
import static com.cedarsoftware.ncube.TestUrlClassLoader.getCacheSize
import static org.junit.Assert.*

/**
 * NCubeManager Tests
 *
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
class TestNCubeManager extends NCubeCleanupBaseTest
{
    public static final String APP_ID = 'ncube.test'
    public static final String USER_ID = 'cubeUser'
    public static ApplicationID defaultSnapshotApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '1.0.0', ReleaseStatus.SNAPSHOT.name(), ApplicationID.TEST_BRANCH)
    public static ApplicationID defaultReleaseApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '1.0.0', ReleaseStatus.RELEASE.name(), ApplicationID.TEST_BRANCH)
    public static ApplicationID defaultBootApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '0.0.0', ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
    public static ApplicationID sysAppId = new ApplicationID(ApplicationID.DEFAULT_TENANT, NCubeConstants.SYS_APP, ApplicationID.SYS_BOOT_VERSION, ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)

    private static Object[] createTests()
    {
        CellInfo foo = new CellInfo('int', '5', false, false)
        CellInfo bar = new CellInfo('string', 'none', false, false)
        Map<String, CellInfo> pairs = ['foo': foo, 'bar': bar]
        CellInfo[] cellInfos = [foo, bar] as CellInfo[]

        return [new NCubeTest('foo', pairs, cellInfos)] as Object[]
    }

    private static NCube createCube()
    {
        NCube<Double> ncube = NCubeBuilder.getTestNCube2D(true)
        ncube.applicationID = defaultSnapshotApp

        Map<String, Object> coord = [gender:'male', age:47] as Map
        ncube.setCell(1.0d, coord)

        coord.gender = 'female'
        ncube.setCell(1.1d, coord)

        coord.age = 16
        ncube.setCell(1.5d, coord)

        coord.gender = 'male'
        ncube.setCell(1.8d, coord)

        ncube.testData = createTests()
        mutableClient.createCube(ncube)
        mutableClient.updateNotes(defaultSnapshotApp, ncube.name, 'notes follow')
        return ncube
    }

    @Test
    void testSearchIncludeData()
    {
        preloadCubes(defaultSnapshotApp, 'test.branch.1.json', 'test.branch.age.1.json')
        List<NCubeInfoDto> list = mutableClient.search(defaultSnapshotApp, null, 'adult', [:])
        assert list.size() == 1
        NCubeInfoDto dto = list.first()
        assert dto.bytes == null
        assert dto.testData == null

        list = mutableClient.search(defaultSnapshotApp, null, 'adult', [(SEARCH_INCLUDE_CUBE_DATA):true])
        assert list.size() == 1
        dto = list.first()
        assert dto.bytes != null
    }

    @Test
    void testMergeChangedCubeWithTestData()
    {
        ApplicationID branch1 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'FOO')
        ApplicationID branch2 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'BAR')
        NCube cube1 = NCubeBuilder.getTestNCube2D(true)
        cube1.applicationID = branch1

        Map<String, Object> coord = [gender:'male', age:47] as Map
        cube1.setCell(1.0d, coord)

        coord.gender = 'female'
        cube1.setCell(1.1d, coord)

        coord.age = 16
        cube1.setCell(1.5d, coord)

        coord.gender = 'male'
        cube1.setCell(1.8d, coord)

        String cubeName = cube1.name

        // put cube in both branches
        mutableClient.createCube(cube1)
        mutableClient.commitBranch(branch1)
        mutableClient.copyBranch(branch1.asHead(), branch2)
        assert !cube1.testData

        // change cube and add tests to branch1 and commit
        Map coord1 = [gender:'male', age:47]
        double val1 = 2.0d
        cube1 = loadCube(branch1, cubeName, [(SEARCH_INCLUDE_TEST_DATA):true])
        cube1.testData = createTests()
        cube1.setCell(val1, coord1)
        mutableClient.updateCube(cube1)
        mutableClient.commitBranch(branch1)

        // make change to cube branch2
        Map coord2 = [gender:'male', age:16]
        double val2 = 1.0d
        NCube cube2 = loadCube(branch2, cubeName, [(SEARCH_INCLUDE_TEST_DATA):true])
        cube2.setCell(val2, coord2)
        mutableClient.updateCube(cube2)
        assert !cube2.testData

        // updating cube2 from HEAD should copy tests and merge data
        mutableClient.updateBranch(branch2)
        cube1 = loadCube(branch1, cubeName, [(SEARCH_INCLUDE_TEST_DATA):true])
        cube2 = loadCube(branch2, cubeName, [(SEARCH_INCLUDE_TEST_DATA):true])

        assert cube1.testData[0].name == cube2.testData[0].name
        assert val1 == cube2.getCell(coord1)
        assert val2 == cube2.getCell(coord2)
    }
    
    @Test
    void testLoadCubes()
    {
        NCube ncube = NCubeBuilder.getTestNCube2D(true)

        Map coord = [gender:'male', age:47] as Map
        ncube.setCell(1.0d, coord)

        coord.gender = 'female'
        ncube.setCell(1.1d, coord)

        coord.age = 16
        ncube.setCell(1.5d, coord)

        coord.gender = 'male'
        ncube.setCell(1.8d, coord)

        String version = '0.1.0'
        String name1 = ncube.name

        ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, version, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        ncube.applicationID = appId
        mutableClient.createCube(ncube)
        ncube.testData = createTests()
        mutableClient.updateNotes(appId, ncube.name, 'notes follow')

        mutableClient.search(appId, name1, null, [(SEARCH_EXACT_MATCH_NAME) : true])

        ncube = NCubeBuilder.testNCube3D_Boolean
        ncube.applicationID = appId
        String name2 = ncube.name
        mutableClient.createCube(ncube)

        ncubeRuntime.clearCache(appId)
        mutableClient.search(appId, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])

        NCube ncube1 = mutableClient.getCube(appId, name1)
        NCube ncube2 = mutableClient.getCube(appId, name2)
        assertNotNull(ncube1)
        assertNotNull(ncube2)
        assertEquals(name1, ncube1.name)
        assertEquals(name2, ncube2.name)
        ncubeRuntime.clearCache(appId)

        mutableClient.deleteCubes(appId, [name1].toArray())
        mutableClient.deleteCubes(appId, [name2].toArray())

        Map options = [(SEARCH_ACTIVE_RECORDS_ONLY) : true, (SEARCH_EXACT_MATCH_NAME) : true]
        Object[] cubeInfo = mutableClient.search(appId, name1, null, options)
        assertEquals(0, cubeInfo.length)
        cubeInfo = mutableClient.search(appId, name2, null, options)
        assertEquals(0, cubeInfo.length)
    }

    @Test
    void testDuplicateWhereNameAndAppIdAreIdentical()
    {
        try
        {
            mutableClient.duplicate(defaultSnapshotApp, defaultSnapshotApp, 'test', 'test')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'could not duplicate')
        }
    }

    @Test
    void testUpdateSavesTestData()
    {
        NCube cube = createCube()
        assertNotNull(cube)

        List<NCubeTest> expectedTests = createTests().toList() as List<NCubeTest>

        // reading from cache.
        List<NCubeTest> data = loadCube(defaultSnapshotApp, cube.name, [(SEARCH_INCLUDE_TEST_DATA):true]).testData
        assert DeepEquals.deepEquals(expectedTests, data)

        // reload from db
        ncubeRuntime.clearCache(defaultSnapshotApp)
        data = loadCube(defaultSnapshotApp, cube.name, [(SEARCH_INCLUDE_TEST_DATA):true]).testData
        assert DeepEquals.deepEquals(expectedTests, data)

        //  update cube
        cube.testData = [new NCubeTest('different test', [:], [] as CellInfo[])] as Object[]
        mutableClient.updateCube(cube)
        cube = loadCube(defaultSnapshotApp, cube.name, [(SEARCH_INCLUDE_TEST_DATA):true])
        data = cube.testData
        assert !DeepEquals.deepEquals(expectedTests, data)
        assert cube.metaProperties.containsKey(NCube.METAPROPERTY_TEST_UPDATED)
        String testUpdated = cube.metaProperties[NCube.METAPROPERTY_TEST_UPDATED]

        //  make sure NOT changing tests will NOT update test metaproperty
        cube.setCell(1.1d, [gender:'male', age:47])
        mutableClient.updateCube(cube)
        cube = loadCube(defaultSnapshotApp, cube.name, [(SEARCH_INCLUDE_TEST_DATA):true])
        List<NCubeTest> newData = cube.testData
        String newTestUpdated = cube.metaProperties[NCube.METAPROPERTY_TEST_UPDATED]
        assert DeepEquals.deepEquals(data, newData)
        assert testUpdated == newTestUpdated
    }

    @Test
    void testDeleteAllTestsRemovesTestData()
    {
        NCube cube = createCube()
        mutableClient.updateCube(cube)
        cube.testData = (Object[])null
        mutableClient.updateCube(cube)

        cube = loadCube(defaultSnapshotApp, cube.name, [(SEARCH_INCLUDE_TEST_DATA):true])
        assert cube.metaProperties.containsKey(NCube.METAPROPERTY_TEST_UPDATED)
        assert !cube.testData
    }

    @Test
    void testGetReferencedCubesThatLoadsTwoCubes()
    {
        try
        {
            mutableClient.getReferencesFrom(defaultSnapshotApp, 'AnyCube')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'not', 'referenced', 'does not exist')
            assertNull(e.cause)
        }
    }

    @Test
    void testRenameWithMatchingNames()
    {
        try
        {
            mutableClient.renameCube(defaultSnapshotApp, 'foo', 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'could not rename')
        }
    }

    @Test
    void testBadCommandCellCommandWithJdbc()
    {
        NCube<Object> continentCounty = new NCube<>('test.ContinentCountries')
        continentCounty.applicationID = defaultSnapshotApp
        ncubeRuntime.addCube(continentCounty)
        continentCounty.addAxis(NCubeBuilder.continentAxis)
        Axis countries = new Axis('Country', AxisType.DISCRETE, AxisValueType.STRING, true)
        countries.addColumn('Canada')
        countries.addColumn('USA')
        countries.addColumn('Mexico')
        continentCounty.addAxis(countries)

        NCube<Object> canada = new NCube<>('test.Provinces')
        canada.applicationID = defaultSnapshotApp
        ncubeRuntime.addCube(canada)
        canada.addAxis(NCubeBuilder.provincesAxis)

        NCube<Object> usa = new NCube<>('test.States')
        usa.applicationID = defaultSnapshotApp
        ncubeRuntime.addCube(usa)
        usa.addAxis(NCubeBuilder.statesAxis)

        Map coord1 = new HashMap()
        coord1.put('Continent', 'North America')
        coord1.put('Country', 'USA')
        coord1.put('State', 'OH')

        Map coord2 = new HashMap()
        coord2.put('Continent', 'North America')
        coord2.put('Country', 'Canada')
        coord2.put('Province', 'Quebec')

        continentCounty.setCell(new GroovyExpression('@test.States([:])', null, false), coord1)
        continentCounty.setCell(new GroovyExpression('\$test.Provinces(crunch)', null, false), coord2)

        usa.setCell(1.0, coord1)
        canada.setCell(0.78, coord2)

        assertEquals((Double) continentCounty.getCell(coord1), 1.0d, 0.00001d)

        try
        {
            assertEquals((Double) continentCounty.getCell(coord2), 0.78d, 0.00001d)
            fail 'should throw exception'
        }
        catch (RuntimeException e)
        {
            assert e.message.toLowerCase().contains('error occurred')
        }

        mutableClient.createCube(continentCounty)
        mutableClient.createCube(usa)
        mutableClient.createCube(canada)

        assertEquals(4, mutableClient.search(defaultSnapshotApp, null, null, null).size())

        // make sure items aren't in cache for next load from db for next getCubeNames call
        // during create they got added to database.
        ncubeRuntime.clearCache(defaultSnapshotApp)

        assertEquals(4, mutableClient.search(defaultSnapshotApp, null, null, null).size())

        ncubeRuntime.clearCache(defaultSnapshotApp)

        mutableClient.search(defaultSnapshotApp, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        NCube test = mutableClient.getCube(defaultSnapshotApp, 'test.ContinentCountries')
        assertEquals((Double) test.getCell(coord1), 1.0d, 0.00001d)

        mutableClient.deleteCubes(defaultSnapshotApp, ['test.ContinentCountries'].toArray())
        mutableClient.deleteCubes(defaultSnapshotApp, ['test.States'].toArray())
        mutableClient.deleteCubes(defaultSnapshotApp, ['test.Provinces'].toArray())
        assertEquals(1, mutableClient.search(defaultSnapshotApp, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())

        HtmlFormatter formatter = new HtmlFormatter()
        String s = formatter.format(continentCounty)
        assertTrue(s.contains("column-code"))
    }

    @Test
    void testGetReferencedCubeNames()
    {
        NCube n1 = createCubeFromResource(defaultSnapshotApp, 'template1.json')
        NCube n2 = createCubeFromResource(defaultSnapshotApp, 'template2.json')

        Set refs = mutableClient.getReferencesFrom(defaultSnapshotApp, n1.name)

        assertEquals(2, refs.size())
        assertTrue(refs.contains('Template2Cube'))

        refs.clear()
        refs = mutableClient.getReferencesFrom(defaultSnapshotApp, n2.name)
        assertEquals(2, refs.size())
        assertTrue(refs.contains('Template1Cube'))

        assertTrue(mutableClient.deleteCubes(defaultSnapshotApp, [n1.name].toArray()))
        assertTrue(mutableClient.deleteCubes(defaultSnapshotApp, [n2.name].toArray()))

        try
        {
            mutableClient.getReferencesFrom(defaultSnapshotApp, n2.name)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'not get referenced', 'not exist')
        }
    }

    @Test
    void testGetReferencedCubeNamesSimple()
    {
        createCubeFromResource(defaultSnapshotApp, 'aa.json')
        createCubeFromResource(defaultSnapshotApp, 'bb.json')

        Set refs = mutableClient.getReferencesFrom(defaultSnapshotApp, 'aa')

        assertEquals(1, refs.size())
        assertTrue(refs.contains('bb'))

        refs.clear()
        refs = mutableClient.getReferencesFrom(defaultSnapshotApp, 'bb')
        assertEquals(0, refs.size())
    }

    @Test
    void testReferencedCubeCoordinateNotFound()
    {
        NCube n1 = ncubeRuntime.getNCubeFromResource(defaultSnapshotApp, 'aa.json')
        ncubeRuntime.getNCubeFromResource(defaultSnapshotApp, 'bb.json')

        try
        {
            Map input = new HashMap()
            input.put('state', 'OH')
            n1.getCell(input)
            fail()
        }
        catch (CoordinateNotFoundException e)
        {
            assertTrue(e.message.contains("not found"))
            assertEquals("bb", e.cubeName)
            assertEquals("KY", e.coordinate.state)
            assertEquals("state", e.axisName)
            assertEquals("KY", e.value)
        }
    }

    @Test
    void testDuplicateNCubeWithinSameApp()
    {
        NCube n1 = ncubeRuntime.getNCubeFromResource(defaultSnapshotApp, 'stringIds.json')
        mutableClient.createCube(n1)
        ApplicationID newId = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '1.1.2', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)

        mutableClient.duplicate(defaultSnapshotApp, newId, n1.name, n1.name)
        NCube n2 = mutableClient.getCube(defaultSnapshotApp, n1.name)

        assertTrue(mutableClient.deleteCubes(defaultSnapshotApp, [n1.name].toArray()))
        assertTrue(mutableClient.deleteCubes(newId, [n2.name].toArray()))
        assertTrue(n1 == n2)
    }

    @Test
    void testDuplicateNCubeToNewApp()
    {
        NCube n1 = ncubeRuntime.getNCubeFromResource(defaultSnapshotApp, 'stringIds.json')
        mutableClient.createCube(n1)
        ApplicationID newId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'some.new.app', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)

        mutableClient.duplicate(defaultSnapshotApp, newId, n1.name, n1.name)
        NCube n2 = mutableClient.getCube(newId, n1.name)
        assertNotNull(n2)

        //check for permissions cubes in new app
        ApplicationID newBootId = newId.asVersion('0.0.0')
        assertNotNull(mutableClient.getCube(newBootId, SYS_BRANCH_PERMISSIONS))
        assertNotNull(mutableClient.getCube(newBootId, SYS_PERMISSIONS))
        assertNotNull(mutableClient.getCube(newBootId, SYS_USERGROUPS))
        assertNotNull(mutableClient.getCube(newBootId, SYS_LOCK))
    }

    @Test
    void testGetAppNames()
    {
        NCube n1 = ncubeRuntime.getNCubeFromResource(defaultSnapshotApp, 'stringIds.json')
        mutableClient.createCube(n1)

        List<String> names = mutableClient.appNames as List<String>
        boolean foundName = false
        for (String name : names)
        {
            if ('ncube.test' == name)
            {
                foundName = true
                break
            }
        }

        Object[] vers = mutableClient.getVersions(APP_ID)
        boolean foundVer = false
        String version = '1.0.0'
        for (String ver : vers)
        {
            foundVer = checkContainsIgnoreCase(ver, version, 'SNAPSHOT')
            if (foundVer)
            {
                break
            }
        }

        assertTrue(foundName)
        assertTrue(foundVer)
    }

    @Test
    void testChangeVersionValue()
    {
        ApplicationID newId = defaultSnapshotApp.createNewSnapshotId('1.1.20')
        NCube n1 = createCubeFromResource(defaultSnapshotApp, 'stringIds.json')

        assertNotNull(mutableClient.getCube(defaultSnapshotApp, 'idTest'))
        assertNull(mutableClient.getCube(newId, 'idTest'))
        mutableClient.changeVersionValue(defaultSnapshotApp, '1.1.20')

        NCube n2 = mutableClient.getCube(newId, 'idTest')
        assertNotNull(n2)
        assertEquals(n1, n2)
    }

    @Test
    void testUpdateOnDeletedCube()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        ncube1.applicationID = defaultSnapshotApp

        mutableClient.createCube(ncube1)

        assertTrue(ncube1.numDimensions == 3)

        mutableClient.deleteCubes(defaultSnapshotApp, [ncube1.name].toArray())

        assertTrue(mutableClient.updateCube(ncube1))
    }

    @Test
    void testGetBranchChangesFromDatabaseWithInvalidAppIdOfHead()
    {
        try
        {
            mutableClient.getBranchChangesForHead(defaultSnapshotApp.asHead())
            fail 'should not make it here'
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, "branch cannot be 'head'")
        }
    }

    @Test
    void testUpdateNotesOnDeletedCube()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        ncube1.applicationID = defaultSnapshotApp
        mutableClient.createCube(ncube1)
        assertTrue(ncube1.numDimensions == 3)
        mutableClient.deleteCubes(defaultSnapshotApp, [ncube1.name].toArray())
        mutableClient.updateNotes(defaultSnapshotApp, ncube1.name, 'new notes')
        String notes = mutableClient.getNotes(defaultSnapshotApp, ncube1.name)
        assert 'new notes' == notes
    }

    @Test
    void testUpdateTestDataOnDeletedCube()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        ncube1.applicationID = defaultSnapshotApp
        mutableClient.createCube(ncube1)
        assertTrue(ncube1.numDimensions == 3)
        mutableClient.deleteCubes(defaultSnapshotApp, [ncube1.name].toArray())
        ncube1.testData = createTests()
        mutableClient.updateCube(ncube1)
        List<NCubeTest> testData = loadCube(defaultSnapshotApp, ncube1.name, [(SEARCH_INCLUDE_TEST_DATA):true]).testData
        List<NCubeTest> expectedTests = createTests().toList() as List<NCubeTest>
        assertTrue(DeepEquals.deepEquals(expectedTests, testData))
    }

    @Test
    void testReleaseCubesWhenNoCubesExist()
    {
        try
        {
            mutableClient.releaseCubes(new ApplicationID('foo', 'bar', '1.0.0', 'SNAPSHOT', 'john'), "1.0.1")
            // No exception should be thrown, just nothing to promote.
        }
        catch (Exception ignored)
        {
            fail()
        }
    }

    @Test
    void testChangeVersionWhenNoCubesExist()
    {
        try
        {
            mutableClient.changeVersionValue(new ApplicationID('foo', 'bar', '1.0.0', 'SNAPSHOT', 'john'), "1.0.1")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'no snapshot n-cubes found with version 1.0.0')
        }
    }

    @Test
    void testUpdateNotesNonExistingCube()
    {
        try
        {
            mutableClient.updateNotes(new ApplicationID('foo', 'bar', '1.0.0', 'SNAPSHOT', 'john'), 'a', "notes")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot update notes', 'cube: a does not exist')
        }
    }

    @Test
    void testGetNCubes()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCube ncube2 = NCubeBuilder.getTestNCube2D(true)
        ncube1.applicationID = defaultSnapshotApp
        ncube2.applicationID = defaultSnapshotApp

        mutableClient.createCube(ncube1)
        mutableClient.createCube(ncube2)

        Object[] cubeList = mutableClient.search(defaultSnapshotApp, 'test.%', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])

        assertTrue(cubeList != null)
        assertTrue(cubeList.length == 2)

        assertTrue(ncube1.numDimensions == 3)
        assertTrue(ncube2.numDimensions == 2)

        ncube1.deleteAxis('bu')
        mutableClient.updateCube(ncube1)
        NCube cube1 = mutableClient.getCube(defaultSnapshotApp, 'test.ValidTrailorConfigs')
        assertTrue(cube1.numDimensions == 2)    // used to be 3

        // 0 below, because there were no HEAD cubes, so release here, just MOVEs the existing cubes to the next snapshot version
        assertEquals(0, mutableClient.releaseCubes(defaultSnapshotApp, '1.2.3'))
        ApplicationID next = defaultSnapshotApp.createNewSnapshotId('1.2.3')
        cubeList = mutableClient.search(next, 'test.*', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        // Two cubes at the new 1.2.3 SNAPSHOT version.
        assert cubeList.length == 2

        String notes1 = mutableClient.getNotes(next, cube1.name)
        assertContainsIgnoreCase(notes1, 'updated')

        mutableClient.updateNotes(next, cube1.name, null)
        notes1 = mutableClient.getNotes(next, cube1.name)
        assertTrue('' == notes1)

        mutableClient.updateNotes(next, cube1.name, 'Trailer Config Notes')
        notes1 = mutableClient.getNotes(next, cube1.name)
        assertTrue('Trailer Config Notes' == notes1)

        List<NCubeTest> testData = loadCube(next, cube1.name, [(SEARCH_INCLUDE_TEST_DATA):true]).testData
        assert testData.size() == 0
    }

    @Test
    void testNotAllowedToDeleteReleaseCubesOrRerelease()
    {
        NCube cube = ncubeRuntime.getNCubeFromResource(defaultSnapshotApp, 'latlon.json')
        mutableClient.createCube(cube)
        Object[] cubeInfos = mutableClient.search(defaultSnapshotApp, '*', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertNotNull(cubeInfos)
        assertEquals(2, cubeInfos.length)
        mutableClient.commitBranch(defaultSnapshotApp, cubeInfos)

        try
        {
            mutableClient.releaseCubes(defaultSnapshotApp, '1.0.0')
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, '1.0.0 already exists')
        }

        assert 3 == mutableClient.releaseCubes(defaultSnapshotApp, '1.2.3') // 2 + sys.info = 3

        try
        {
            mutableClient.deleteCubes(defaultSnapshotApp, [cube.name].toArray())
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'does not exist')
        }

        try
        {
            mutableClient.releaseCubes(defaultSnapshotApp, '1.2.3')
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, '1.0.0 already exists')
        }
    }

    @Test
    void testRenameNCube()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        ncube1.applicationID = defaultSnapshotApp

        try
        {
            mutableClient.renameCube(defaultSnapshotApp, ncube1.name, 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'could not rename', 'does not exist')
        }

        mutableClient.createCube(ncube1)
        mutableClient.renameCube(defaultSnapshotApp, ncube1.name, 'test.Floppy')

        List<NCubeInfoDto> cubeList = mutableClient.search(defaultSnapshotApp, 'test.*', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])

        assert 1 == cubeList.size()

        NCubeInfoDto nc1 = cubeList[0]

        assert nc1.toString().startsWith('NONE/ncube.test/1.0.0/SNAPSHOT/TEST/test.Floppy')
        assert 'test.Floppy' == nc1.name

        // added to be sure CUBE_VALUE_BIN was not being deleted
        List<NCubeInfoDto> newRevisions = mutableClient.getRevisionHistory(defaultSnapshotApp, 'test.Floppy')
        assert mutableClient.loadCubeRecordById(newRevisions[0].id as long, null)
        List<NCubeInfoDto> oldRevisions = mutableClient.getRevisionHistory(defaultSnapshotApp, 'test.ValidTrailorConfigs')
        assert mutableClient.loadCubeRecordById(oldRevisions[0].id as long, null)
    }

    @Test
    void testNCubeManagerGetCubes()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCube ncube2 = NCubeBuilder.getTestNCube2D(true)
        ncube1.applicationID = defaultSnapshotApp
        ncube2.applicationID = defaultSnapshotApp

        mutableClient.createCube(ncube1)
        mutableClient.createCube(ncube2)

        // This proves that null is turned into '%' (no exception thrown)
        Object[] cubeList = mutableClient.search(defaultSnapshotApp, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])

        assertEquals(3, cubeList.length)
    }

    @Test
    void testUpdateCubeWithSysClassPath()
    {
        //  from setup, assert initial classloader condition (files.cedarsoftware.com)
        ApplicationID customId = new ApplicationID('NONE', 'updateCubeSys', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        assertNotNull(ncubeRuntime.getUrlClassLoader(customId, [:]))
        assertEquals(1, getCacheSize(customId))

        NCube testCube = createCubeFromResource(customId, 'sys.classpath.tests.json')

        assertEquals(1, ncubeRuntime.getUrlClassLoader(customId, [:]).URLs.length)
        assertEquals(3, getCacheSize(customId))

        mutableClient.updateCube(testCube) // reload to clear classLoader inside the cell

        assertEquals(0, getCacheSize(customId)) // 0 cubes in caches because updateCube() above clears cube from cache
        assertEquals(testCube, mutableClient.getCube(customId, 'sys.classpath'))

        assertTrue(mutableClient.updateCube(testCube))
        assertNotNull(ncubeRuntime.getUrlClassLoader(customId, [:]))
        assertEquals(3, getCacheSize(customId))

        testCube = mutableClient.getCube(customId, 'sys.classpath')
        assertEquals(3, getCacheSize(customId))
        assertEquals(1, ncubeRuntime.getUrlClassLoader(customId, [:]).URLs.length)

        //  validate item got added to cache.
        assertEquals(testCube, mutableClient.getCube(customId, 'sys.classpath'))
    }

    @Test
    void testRenameCubeWithSysClassPath()
    {
        //  from setup, assert initial classloader condition (files.cedarsoftware.com)
        ApplicationID customId = new ApplicationID('NONE', 'renameCubeSys', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        final URLClassLoader urlClassLoader1 = ncubeRuntime.getUrlClassLoader(customId, [:])
        assertNotNull(urlClassLoader1)
        assertEquals(1, getCacheSize(customId))

        createCubeFromResource(customId, 'sys.classpath.tests.json')

        final URLClassLoader urlClassLoader = ncubeRuntime.getUrlClassLoader(customId, [:])
        assertEquals(1, urlClassLoader.URLs.length)
        assertEquals(3, getCacheSize(customId))

        ncubeRuntime.clearCache(customId)
        NCube testCube = createRuntimeCubeFromResource(customId, 'sys.classpath.tests.json')        // reload so that it does not attempt to write classLoader cells (which will blow up)
        testCube.name = 'sys.mistake'
        mutableClient.createCube(testCube)

        assertEquals(3, getCacheSize(customId))     // both sys.mistake and sys.classpath are in the cache

        //  validate item got added to cache.
        assertEquals(testCube, mutableClient.getCube(customId, 'sys.mistake'))

        assertTrue(mutableClient.renameCube(customId, 'sys.mistake', 'sys.classpath'))
        assertNotNull(ncubeRuntime.getUrlClassLoader(customId, [:]))
        assertEquals(3, getCacheSize(customId))

        testCube = mutableClient.getCube(customId, 'sys.classpath')
        assertEquals(3, getCacheSize(customId))
        assertEquals(1, ncubeRuntime.getUrlClassLoader(customId, [:]).URLs.length)

        //  validate item got added to cache.
        assertEquals(testCube, mutableClient.getCube(customId, 'sys.classpath'))
    }

    @Test
    void testDuplicateCubeWithSysClassPath()
    {
        //  from setup, assert initial classloader condition (files.cedarsoftware.com)
        ApplicationID customId = new ApplicationID('NONE', 'renameCubeSys', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        final URLClassLoader urlClassLoader1 = ncubeRuntime.getUrlClassLoader(customId, [:])
        assertNotNull(urlClassLoader1)
        assertEquals(1, getCacheSize(customId))

        createRuntimeCubeFromResource(customId, 'sys.classpath.tests.json')

        final URLClassLoader urlClassLoader = ncubeRuntime.getUrlClassLoader(customId, [:])
        assertEquals(1, urlClassLoader.URLs.length)
        assertEquals(3, getCacheSize(customId))

        ncubeRuntime.clearCache(customId)
        NCube testCube = createRuntimeCubeFromResource(customId, 'sys.classpath.tests.json')        // reload so that it does not attempt to write classLoader cells (which will blow up)
        testCube.name = 'sys.mistake'
        mutableClient.createCube(testCube)

        assertEquals(3, getCacheSize(customId))     // both sys.mistake and sys.classpath are in the cache

        //  validate item got added to cache.
        assertEquals(testCube, mutableClient.getCube(customId, 'sys.mistake'))

        mutableClient.duplicate(customId, customId, 'sys.mistake', 'sys.classpath')
        assertNotNull(ncubeRuntime.getUrlClassLoader(customId, [:]))
        assertEquals(4, getCacheSize(customId))

        testCube = mutableClient.getCube(customId, 'sys.classpath')
        assertEquals(4, getCacheSize(customId))
        assertEquals(1, ncubeRuntime.getUrlClassLoader(customId, [:]).URLs.length)

        //  validate item got added to cache.
        assertEquals(testCube, mutableClient.getCube(customId, 'sys.classpath'))
    }

    @Test
    void testMissingBootstrapException()
    {
        try
        {
            ncubeRuntime.getApplicationID('foo', 'bar', new HashMap())
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'Missing', 'bootstrap', '0.0.0')
        }
    }

    @Test
    void testNCubeManagerUpdateCubeExceptions()
    {
        try
        {
            mutableClient.updateCube(null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'null')
        }
    }

    @Test
    void testNCubeManagerCreateCubes()
    {
        NCube ncube1 = createCube()
        List<NCubeInfoDto> dtos = mutableClient.getRevisionHistory(defaultSnapshotApp, ncube1.name)
        assertEquals(1, dtos.size())

        try
        {
            mutableClient.createCube(ncube1)
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to create', 'already exists')
        }
        dtos = mutableClient.getRevisionHistory(defaultSnapshotApp, ncube1.name)
        assertEquals(1, dtos.size())
    }

    @Test
    void testNCubeManagerDeleteNotExistingCube()
    {
        ApplicationID id = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'DASHBOARD', '0.1.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        try
        {
            assertFalse(mutableClient.deleteCubes(id, ['DashboardRoles'].toArray()))
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot delete cube')
        }
    }

    @Test
    void testNotes()
    {
        try
        {
            mutableClient.getNotes(defaultSnapshotApp, 'DashboardRoles')
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'Could not fetch', 'notes')
        }

        createCube()
        String notes = mutableClient.getNotes(defaultSnapshotApp, 'test.Age-Gender')
        assertNotNull(notes)
        assertTrue(notes.length() > 0)

        try
        {
            mutableClient.updateNotes(defaultSnapshotApp, 'test.funky', null)
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'not', 'update', 'does not exist')
        }

        try
        {
            ApplicationID newId = defaultSnapshotApp.createNewSnapshotId('0.1.1')
            mutableClient.getNotes(newId, 'test.Age-Gender')
            fail('Should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'Could not fetch', 'notes')
        }
    }

    @Test
    void testGetAppTests()
    {
        createCube()
        NCube noTestsCube = new NCube('noTests')
        noTestsCube.applicationID = defaultSnapshotApp
        noTestsCube.addAxis(new Axis('testAxis', AxisType.DISCRETE, AxisValueType.STRING, true))
        mutableClient.createCube(noTestsCube)

        Map testData = mutableClient.getAppTests(defaultSnapshotApp)
        assert (testData['test.Age-Gender'] as Object[]).size()
        assert !testData.containsKey('noTests')
    }

    @Test
    void testNCubeManagerTestData()
    {
        createCube()
        List<NCubeTest> testData = loadCube(defaultSnapshotApp, 'test.Age-Gender', [(SEARCH_INCLUDE_TEST_DATA):true]).testData
        assert !testData.empty
    }

    @Test
    void testSaveTestsUpdatesSha1()
    {
        NCube cube = createCube()
        List<NCubeTest> testData = loadCube(defaultSnapshotApp, cube.name, [(SEARCH_INCLUDE_TEST_DATA):true]).testData
        assert 1 == testData.size()
        mutableClient.commitBranch(defaultSnapshotApp)

        NCubeTest newTest = new NCubeTest('bar', testData[0].coord, testData[0].assertions)
        cube.testData = [newTest].toArray()
        mutableClient.updateCube(cube)
        List<NCubeInfoDto> dtos = mutableClient.getBranchChangesForHead(defaultSnapshotApp)
        assert 1 == dtos.size()
    }

    @Test
    void testEmptyNCubeMetaProps()
    {
        NCube ncube = createCube()
        String json = ncube.toFormattedJson()
        ncube = NCube.fromSimpleJson(json)
        assertTrue(ncube.metaProperties.size() == 0)

        List<Axis> axes = ncube.axes
        for (Axis axis : axes)
        {
            assertTrue(axis.metaProperties.size() == 0)

            for (Column column : axis.columns)
            {
                assertTrue(column.metaProperties.size() == 0)
            }
        }
    }

    @Test
    void testLoadCubesWithNullApplicationID()
    {
        try
        {
            // This API is now package friendly and only to be used by tests or NCubeManager implementation work.
            mutableClient.search(null, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'ApplicationID', 'null')
        }
    }

    @Test(expected = RuntimeException.class)
    void testGetNCubesFromResourceException()
    {
        ncubeRuntime.getNCubesFromResource(null, null)
    }

    @Test
    void testRestoreCubeWithEmptyArray()
    {
        try
        {
            mutableClient.restoreCubes(defaultSnapshotApp)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'empty array', 'to be restored')
        }
    }

    @Test
    void testRollbackBranchWhenCubeDoesntExist()
    {
        Object[] names = new Object[2]
        names[0] = "TestCube"

        int x = mutableClient.rollbackBranch(defaultSnapshotApp, names)
        assert 0 == x
    }

    @Test
    void testRollbackMultiple()
    {
        ApplicationID appId = new ApplicationID('NONE', 'none', '1.0.0', 'SNAPSHOT', 'jdereg')

        NCube cube1 = createCubeFromResource(appId, 'testCube1.json')
        String sha1 = cube1.sha1()

        NCube cube2 = createCubeFromResource(appId, 'template1.json')
        String sha2 = cube2.sha1()

        NCube cube3 = createCubeFromResource(appId, 'urlPieces.json')
        String sha3 = cube3.sha1()

        NCube cube4 = createCubeFromResource(appId, 'months.json')
        String sha4 = cube4.sha1()

        List<NCubeInfoDto> changes = mutableClient.getBranchChangesForHead(appId)
        assert 4 == changes.size()

        Object[] cubes = [cube1.name, cube2.name, cube3.name, cube4.name].toArray()
        assert 4 == mutableClient.rollbackBranch(appId, cubes)
        changes = mutableClient.getBranchChangesForHead(appId)
        assert 0 == changes.size()  // Looks like we've got no cubes (4 deleted ones but 0 active)

        List<NCubeInfoDto> deleted = mutableClient.search(appId, null, null, [(SEARCH_DELETED_RECORDS_ONLY): true])
        assert 4 == deleted.size()  // Rollback of a create is a DELETE

        mutableClient.restoreCubes(appId, cubes)
        assert 4 == mutableClient.search(appId, null, null, null).size()

        changes = mutableClient.getBranchChangesForHead(appId)
        assert 4 == changes.size()

        mutableClient.getCube(appId, cubes[0] as String).sha1() == sha1
        mutableClient.getCube(appId, cubes[1] as String).sha1() == sha2
        mutableClient.getCube(appId, cubes[2] as String).sha1() == sha3
        mutableClient.getCube(appId, cubes[3] as String).sha1() == sha4
    }

    @Test
    void testRestoreCubeWithNullArray()
    {
        try
        {
            mutableClient.restoreCubes(defaultSnapshotApp, null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot restore', 'not deleted')
        }
    }

    @Test
    void testRestoreExistingCube()
    {
        NCube cube = createCube()
        try
        {
            mutableClient.restoreCubes(defaultSnapshotApp, cube.name)
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot restore', 'not deleted in app')
        }
    }

    @Test
    void testRestoreDeletedCube()
    {
        NCube cube = createCube()
        Object[] records = mutableClient.search(defaultSnapshotApp, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(2, records.length) // 2 (because of sys.classpath)

        records = mutableClient.search(defaultSnapshotApp, cube.name, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(1, records.length)
        String sha1 = (records[0] as NCubeInfoDto).sha1

        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())

        mutableClient.deleteCubes(defaultSnapshotApp, [cube.name].toArray())

        assertEquals(1, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())

        records = mutableClient.search(defaultSnapshotApp, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(1, records.length)

        mutableClient.restoreCubes(defaultSnapshotApp, cube.name)
        records = mutableClient.search(defaultSnapshotApp, 'test*', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(1, records.length)

        NCube ncube = mutableClient.getCube(defaultSnapshotApp, cube.name)
        assertNotNull ncube
        assert ncube.sha1() == cube.sha1()
        assert ncube.sha1() == sha1
    }

    @Test
    void testLoadCubeWithNonExistingName()
    {
        NCube ncube = mutableClient.getCube(defaultSnapshotApp, 'sdflsdlfk')
        assertNull ncube
    }

    @Test
    void testRestoreCubeWithCubeThatDoesNotExist()
    {
        try
        {
            mutableClient.restoreCubes(defaultSnapshotApp, 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot restore', 'not deleted in app')
        }
    }

    @Test
    void testGetRevisionHistory()
    {
        try
        {
            mutableClient.getRevisionHistory(defaultSnapshotApp, 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot fetch', 'does not exist')
        }
    }

    @Test
    void testDeleteWithRevisions()
    {
        NCube cube = createCube()
        assertEquals(2, mutableClient.search(defaultSnapshotApp, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, null).size())
        assertEquals(1, mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name).size())

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        mutableClient.updateCube(cube)
        assertEquals(2, mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name).size())
        assertEquals(2, mutableClient.search(defaultSnapshotApp, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())

        Axis conAxis = NCubeBuilder.continentAxis
        cube.addAxis(conAxis)

        mutableClient.updateCube(cube)

        assertEquals(3, mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name).size())
        assertEquals(2, mutableClient.search(defaultSnapshotApp, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())

        mutableClient.deleteCubes(defaultSnapshotApp, [cube.name].toArray())

        assertEquals(1, mutableClient.search(defaultSnapshotApp, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(1, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())
        assertEquals(4, mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name).size())

        mutableClient.restoreCubes(defaultSnapshotApp, cube.name)
        NCube restored = mutableClient.getCube(defaultSnapshotApp, cube.name)
        assert cube.sha1() == restored.sha1()

        assertEquals(2, mutableClient.search(defaultSnapshotApp, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())
        assertEquals(5, mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name).size())

        mutableClient.deleteCubes(defaultSnapshotApp, [cube.name].toArray())

        assertEquals(1, mutableClient.search(defaultSnapshotApp, '', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(1, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())
        assertEquals(6, mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name).size())
    }

    @Test
    void testRevisionHistory()
    {
        NCube cube = createCube()
        Object[] his = mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name)
        NCubeInfoDto[] history = (NCubeInfoDto[]) his
        assertEquals(1, history.length)
        assert history[0].name == 'test.Age-Gender'
        assert history[0].revision == '0'
        assert history[0].notes == 'notes follow'
        assertNotNull history[0].toString()

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        mutableClient.updateCube(cube)
        his = mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name)
        history = (NCubeInfoDto[]) his
        assertEquals(2, history.length)
        assert history[1].name == 'test.Age-Gender'
        assert history[0].revision == '1'
        assert history[1].revision == '0'
        assert history[1].notes == 'notes follow'

        long rev0Id = Converter.convert(history[1].id, long.class) as long
        long rev1Id = Converter.convert(history[0].id, long.class) as long
        NCubeInfoDto record0 = mutableClient.loadCubeRecordById(rev0Id, null)
        NCubeInfoDto record1 = mutableClient.loadCubeRecordById(rev1Id, null)
        NCube rev0 = NCube.createCubeFromBytes(record0.bytes as byte[])
        NCube rev1 = NCube.createCubeFromBytes(record1.bytes as byte[])

        assert rev0.numDimensions == 2
        assert rev1.numDimensions == 3
    }

    @Test
    void testRevisionHistoryIgnoreVersion()
    {
        NCube cube = createCube()
        Object[] his = mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name)
        NCubeInfoDto[] history = (NCubeInfoDto[]) his
        assertEquals(1, history.length)
        assert history[0].name == 'test.Age-Gender'
        assert history[0].revision == '0'
        assert history[0].notes == 'notes follow'
        assertNotNull history[0].toString()

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        mutableClient.updateCube(cube)
        Map<String, Object> result = mutableClient.commitBranch(defaultSnapshotApp, mutableClient.search(defaultSnapshotApp, cube.name, null, null).toArray())
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 1
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0
        mutableClient.lockApp(defaultSnapshotApp, true)
        mutableClient.releaseVersion(defaultSnapshotApp, '2.0.0')
        mutableClient.lockApp(defaultSnapshotApp, false)
        List<NCubeInfoDto> fullHistory = mutableClient.getRevisionHistory(defaultSnapshotApp.asVersion('2.0.0').asHead(), cube.name, true)
        assert fullHistory.size() == 1
    }

    @Test
    void testCellAnnotate()
    {
        Map testCoord = [Gender:'Male', Age: 42]
        NCube cube = createCube()
        cube.setCell(7d, testCoord)
        mutableClient.updateCube(cube)
        cube.setCell(2d, [Gender:'Male', Age: 10])
        mutableClient.updateCube(cube)
        cube.setCell(5d, [Gender:'Male', Age: 42])
        mutableClient.updateCube(cube)

        Set<Long> ids = []
        ids << cube.getAxis('Gender').findColumn('Male').id
        ids << cube.getAxis('Age').findColumn(42).id

        List<NCubeInfoDto> revs = mutableClient.getCellAnnotation(defaultSnapshotApp, cube.name, ids)
        assert 3 == revs.size()
        assert '0' == revs[0].revision
        assert '1' == revs[1].revision
        assert '3' == revs[2].revision
    }

    @Test
    void testCellAnnotateIgnoreVersion()
    {
        Map testCoord = [Gender:'Male', Age: 42]
        Map searchOpts = [(SEARCH_ACTIVE_RECORDS_ONLY):true, (SEARCH_EXACT_MATCH_NAME):true]
        ApplicationID appId = defaultSnapshotApp
        String newVer = '1.1.0'
        NCube cube = createCube()
        cube.setCell(7d, testCoord)
        mutableClient.updateCube(cube)
        mutableClient.commitBranch(appId)
        mutableClient.releaseCubes(appId, newVer)

        appId = appId.asVersion(newVer)
        NCubeInfoDto record = mutableClient.loadCubeRecordById(mutableClient.search(appId, cube.name, null, searchOpts)[0].id as long, null)
        cube = NCube.createCubeFromRecord(record)
        cube.setCell(2d, [Gender:'Male', Age: 10])
        mutableClient.updateCube(cube)
        mutableClient.commitBranch(appId)
        newVer = '1.2.0'
        mutableClient.releaseCubes(appId, newVer)

        appId = appId.asVersion(newVer)
        record = mutableClient.loadCubeRecordById(mutableClient.search(appId, cube.name, null, searchOpts)[0].id as long, null)
        cube = NCube.createCubeFromRecord(record)
        cube.setCell(5d, [Gender:'Male', Age: 42])
        mutableClient.updateCube(cube)
        mutableClient.commitBranch(appId)
        newVer = '1.3.0'
        mutableClient.releaseCubes(appId, newVer)

        Set<Long> ids = []
        ids << cube.getAxis('Gender').findColumn('Male').id
        ids << cube.getAxis('Age').findColumn(42).id

        appId = appId.asVersion(newVer)
        List<NCubeInfoDto> revs = mutableClient.getCellAnnotation(appId.asHead(), cube.name, ids, true)
        assert 2 == revs.size()
        assert '1' == revs[0].revision
        assert '1.1.0' == revs[0].version
        assert '2' == revs[1].revision
        assert '1.3.0' == revs[1].version
    }

    @Test
    void testNCubeInfoDto()
    {
        NCube cube = createCube()
        def history = mutableClient.search(cube.applicationID, '*', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(2, history.size())     // sys.classpath too
        assertTrue history[0] instanceof NCubeInfoDto
        assertTrue history[1] instanceof NCubeInfoDto

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        mutableClient.updateCube(cube)
        history = mutableClient.search(cube.applicationID, '*', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(2, history.size())
        assertTrue history[0] instanceof NCubeInfoDto
        assertTrue history[1] instanceof NCubeInfoDto
    }

    @Test
    void testResolveClasspathWithInvalidUrl()
    {
        NCube cube = ncubeRuntime.getNCubeFromResource(defaultSnapshotApp, 'sys.classpath.invalid.url.json')
        mutableClient.updateCube(cube)
        createCube()

        // force reload from hsql and reget classpath
        assertNotNull(ncubeRuntime.getUrlClassLoader(defaultSnapshotApp, [:]))

        ncubeRuntime.clearCache(defaultSnapshotApp)
        assertNotNull(ncubeRuntime.getUrlClassLoader(defaultSnapshotApp, [:]))

        mutableClient.getCube(defaultSnapshotApp, 'test.AgeGender')
        GroovyClassLoader loader = (GroovyClassLoader) ncubeRuntime.getUrlClassLoader(defaultSnapshotApp, [:])
        assertEquals(0, loader.URLs.length)
    }

    @Test
    void testResolveClassPath()
    {
        loadTestClassPathCubes()

        Map map = [env:'DEV']
        NCube baseCube = mutableClient.getCube(defaultSnapshotApp, 'sys.classpath.base')

        assertEquals("${baseRemoteUrl}/tests/ncube/cp1/".toString(), baseCube.getCell(map))
        map.env = 'CERT'
        assertEquals("${baseRemoteUrl}/tests/ncube/cp2/".toString(), baseCube.getCell(map))

        NCube classPathCube = mutableClient.getCube(defaultSnapshotApp, 'sys.classpath')
        URLClassLoader loader = (URLClassLoader) classPathCube.getCell(map)
        assertEquals(1, loader.URLs.length)
        assertEquals("${baseRemoteUrl}/tests/ncube/cp2/".toString(), loader.URLs[0].toString())
    }

    @Test
    void testResolveRelativeUrl()
    {
        // Sets App classpath to http://files.cedarsoftware.com
        createRuntimeCubeFromResource(ApplicationID.testAppId, 'sys.classpath.cedar.json')

        // Rule cube that expects tests/ncube/hello.groovy to be relative to http://files.cedarsoftware.com
        NCube hello = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'resolveRelativeHelloGroovy.json')

        // When run, it will set up the classpath (first cube loaded for App), and then
        // it will run the rule cube.  This cube has a relative URL (relative to the classpath above).
        // The code from the website will be pulled down, executed, and the result (Hello, World.)
        // will be returned.
        String s = (String) hello.getCell([:])
        assertEquals('Hello, world.', s)

        URL absUrl = ncubeRuntime.getActualUrl(ApplicationID.testAppId, 'tests/ncube/hello.groovy', [:])
        assertEquals("${baseRemoteUrl}/tests/ncube/hello.groovy".toString(), absUrl.toString())
    }

    @Test
    void testResolveUrlBadArgs()
    {
        try
        {
            ncubeRuntime.getActualUrl(ApplicationID.testAppId, null, [:])
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'annot', 'null', 'empty', 'resolve')
        }
    }

    @Test
    void testResolveUrlFullyQualified()
    {
        String url = 'http://files.cedarsoftware.com'
        URL ret = ncubeRuntime.getActualUrl(ApplicationID.testAppId, url, [:])
        assertEquals(url, ret.toString())

        url = 'https://files.cedarsoftware.com'
        ret = ncubeRuntime.getActualUrl(ApplicationID.testAppId, url, [:])
        assertEquals(url, ret.toString())

        url = 'file://Users/joe/Development'
        ret = ncubeRuntime.getActualUrl(ApplicationID.testAppId, url, [:])
        assertEquals(url, ret.toString())
    }

    @Test
    void testResolveUrlBadApp()
    {
        try
        {
            ncubeRuntime.getActualUrl(new ApplicationID('foo', 'bar', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH), 'tests/ncube/hello.groovy', [:])
            fail()
        }
        catch (IllegalArgumentException ignored)
        { }
    }

    @Test
    void testMalformedUrl()
    {
        NCube cube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'urlContent.json')
        try
        {
            cube.getCell([Sites:'BadUrl'])
        }
        catch (CommandCellException e)
        {
            assert e.cause instanceof IllegalStateException
        }

        // Called 2nd time intentionally, to ensure that failOnErrors() is called.
        try
        {
            cube.getCell([Sites:'BadUrl'])
        }
        catch (CommandCellException e)
        {
            assert e.cause instanceof IllegalStateException
        }
    }

    @Test
    void testFastForward()
    {
        // Test the case where two users make the exact same change.  In this case, we can fast-forward the
        // HEAD sha1 in the branch (re-base it) because the cube in the branch is the same as the cube in HEAD,
        // even though the cube in the branch has an out-of-date HEAD SHA-1 (the HEAD changed since the
        // branch was changed).  But, because the branch owner made the same change as someone else, when they
        // go to update, it is recognized, and then their branch cube just has it's HEAD SHA1 updated.
        ApplicationID johnAppId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'deep.blue', '1.0.0', 'SNAPSHOT', 'jdereg')
        createCubeFromResource(johnAppId, 'testCube6.json')
        List<NCubeInfoDto> cubes = mutableClient.getBranchChangesForHead(johnAppId)
        mutableClient.commitBranch(johnAppId, cubes.toArray())
        ApplicationID kenAppId  = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'deep.blue', '1.0.0', 'SNAPSHOT', 'ken')
        mutableClient.copyBranch(kenAppId.asHead(), kenAppId)

        // At this point, there are three branches, john, ken, HEAD.  1 cube in all, same state
        cubes = mutableClient.search(johnAppId, null, null, null)
        assert cubes.size() == 1
        assert cubes[0].revision == '0'
        cubes = mutableClient.search(kenAppId, null, null, null)
        assert cubes.size() == 1
        assert cubes[0].revision == '0'
        cubes = mutableClient.search(johnAppId.asHead(), null, null, null)
        assert cubes.size() == 1
        assert cubes[0].revision == '0'

        // Ken's branch is going to modify cube and commit it to HEAD
        NCube cube = mutableClient.getCube(kenAppId, 'TestCube')
        cube.setCell('foo', [gender:'male'])
        String kenSha1 = cube.sha1()
        mutableClient.updateCube(cube)
        cubes = mutableClient.getBranchChangesForHead(kenAppId)
        assert cubes.size() == 1
        Map <String, Object> result = mutableClient.commitBranch(kenAppId, cubes.toArray())
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0
        NCube cubeHead = mutableClient.getCube(kenAppId.asHead(), 'TestCube')
        assert cubeHead.sha1() == cube.sha1()

        // At this point, HEAD and Ken are same, John's branch is behind.
        NCube johnCube = mutableClient.getCube(johnAppId, 'TestCube')
        assert johnCube.sha1() != kenSha1
        johnCube.setCell('foo', [gender:'male'])
        assert kenSha1 == cube.sha1()

        // I made the same changes on my branch to my cube.
        mutableClient.updateCube(johnCube)
        johnCube = mutableClient.getCube(johnAppId, 'TestCube')
        assert kenSha1 == johnCube.sha1()

        // Verify that before the Update Branch, we show one (1) branch change
        cubes = mutableClient.getBranchChangesForHead(johnAppId)
        assert cubes.size() == 0

        List dtos2 = mutableClient.getHeadChangesForBranch(johnAppId)
        assert dtos2.size() == 1
        NCubeInfoDto dto = dtos2[0]
        assert dto.name == 'TestCube'
        assert dto.changeType == ChangeType.FASTFORWARD.code

        // Update john branch (no changes are shown - it auto-merged)
        Map map = mutableClient.updateBranch(johnAppId)
        assert (map[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (map[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (map[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (map[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (map[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (map[mutableClient.BRANCH_REJECTS] as Map).size() == 0

        cubes = mutableClient.getBranchChangesForHead(johnAppId)
        assert cubes.size() == 0    // No changes being returned

        cubes = mutableClient.search(johnAppId, null, null, null)
        assert cubes.size() == 1
        assert cubes[0].revision == '1'

        // Stuck this code on the end, to test multiple answers for getVersions()
        johnAppId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'deep.blue', '1.1.0', 'SNAPSHOT', 'jdereg')
        cube = ncubeRuntime.getNCubeFromResource(johnAppId, 'testCube4.json')
        mutableClient.createCube(cube)

        Object[] versions = mutableClient.getVersions('deep.blue')
        assert versions.length == 3
        assert checkVersions(versions, '1.1.0')
        assert checkVersions(versions, '1.0.0')
        assert checkVersions(versions, '0.0.0')

        assert 2 == mutableClient.getBranchCount(johnAppId) //HEAD is always added
    }

    @Test
    void testCopyBranch()
    {
        ApplicationID copyAppId = defaultSnapshotApp.asBranch('copy')
        ApplicationID copyAppId2 = defaultSnapshotApp.asBranch('copy2')
        NCube cube = createCubeFromResource(defaultSnapshotApp, 'latlon.json')

        mutableClient.copyBranch(defaultSnapshotApp, copyAppId)
        List<NCubeInfoDto> dtos = mutableClient.search(copyAppId, cube.name, null, [(SEARCH_INCLUDE_NOTES):true])
        assert 1 == dtos.size()
        assertContainsIgnoreCase(dtos[0].notes, copyAppId.toString(), 'copied from', defaultSnapshotApp.toString())

        mutableClient.copyBranch(copyAppId, copyAppId2)
        dtos = mutableClient.search(copyAppId2, cube.name, null, [(SEARCH_INCLUDE_NOTES):true])
        assert 1 == dtos.size()
        assertContainsIgnoreCase(dtos[0].notes, copyAppId2.toString(), 'copied from', copyAppId.toString())
        assert -1 == dtos[0].notes.indexOf(defaultSnapshotApp.toString())
    }

    @Test
    void testCopyBranchWhenReleaseVersionAlreadyExists()
    {
        mutableClient.commitBranch(defaultSnapshotApp, mutableClient.search(defaultSnapshotApp, null, null, null).toArray())
        mutableClient.releaseCubes(defaultSnapshotApp, '2.0.0')
        ApplicationID copyAppId = defaultSnapshotApp.asBranch('copy')
        try
        {
            mutableClient.copyBranch(defaultSnapshotApp, copyAppId)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'exists')
        }
    }

    @Test
    void testThinCopyBranch()
    {
        ApplicationID copyAppId = defaultSnapshotApp.asBranch('copy')
        NCube cube = createCubeFromResource(defaultSnapshotApp, 'test.branch.1.json')
        mutableClient.commitBranch(defaultSnapshotApp)

        mutableClient.copyBranch(defaultSnapshotApp.asHead(), copyAppId)
        assert 1 == mutableClient.getRevisionHistory(copyAppId, cube.name).size()
        mutableClient.deleteBranch(copyAppId)

        mutableClient.copyBranch(defaultSnapshotApp, copyAppId)
        assert 1 == mutableClient.getRevisionHistory(copyAppId, cube.name).size()
        mutableClient.deleteBranch(copyAppId)

        cube.setCell('AAA', [Code : -15])
        mutableClient.updateCube(cube)
        cube.setCell('BBB', [Code : -15])
        mutableClient.updateCube(cube)
        assert 3 == mutableClient.getRevisionHistory(defaultSnapshotApp, cube.name).size()

        mutableClient.copyBranch(defaultSnapshotApp, copyAppId)
        assert 2 == mutableClient.getRevisionHistory(copyAppId, cube.name).size()
    }

    @Test
    void testBadSearchFlags()
    {
        Map options = [(SEARCH_ACTIVE_RECORDS_ONLY): true,
                       (SEARCH_DELETED_RECORDS_ONLY): true]
        try
        {
            mutableClient.search(defaultSnapshotApp, null, null, options)
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'activeRecordsOnly', 'deletedRecordsOnly', 'mutually exclusive')
        }
    }

    @Test
    void testMutateReleaseCube()
    {
        assertNotNull(mutableClient.getCube(defaultBootApp, SYS_LOCK))
        NCube cube = createCubeFromResource(defaultSnapshotApp, 'latlon.json')
        Object[] cubeInfos = mutableClient.search(defaultSnapshotApp, '*', null, [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertNotNull(cubeInfos)
        assertEquals(2, cubeInfos.length)
        mutableClient.releaseCubes(defaultSnapshotApp, "1.2.3")
        try
        {
            mutableClient.deleteCubes(defaultReleaseApp, [cube.name].toArray())
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'RELEASE', 'cube', 'cannot', 'deleted')
        }

        try
        {
            mutableClient.renameCube(defaultReleaseApp, cube.name, 'jumbo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'rename', 'RELEASE', 'cube')
        }

        try
        {
            mutableClient.restoreCubes(defaultReleaseApp, cube.name)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'RELEASE', 'cube', 'cannot', 'restore')
        }

        try
        {
            cube.applicationID = defaultReleaseApp
            mutableClient.updateCube(cube)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'RELEASE', 'cube', 'cannot', 'update')
        }

        try
        {
            mutableClient.changeVersionValue(defaultReleaseApp, '1.2.3')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'change', 'version', 'RELEASE')
        }

        try
        {
            mutableClient.duplicate(defaultSnapshotApp, defaultReleaseApp, cube.name, 'jumbo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'cannot', 'duplicate', 'RELEASE', 'version', 'cube')
        }
    }

    @Test
    void testRenameCubeThatDoesNotExist()
    {
        try {
            mutableClient.renameCube(defaultSnapshotApp, 'foo', 'bar')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'could not rename', 'does not exist')
        }
    }

    @Test
    void testCircularCubeReference()
    {
        createCubeFromResource(defaultSnapshotApp, 'a.json')
        createCubeFromResource(defaultSnapshotApp, 'b.json')
        createCubeFromResource(defaultSnapshotApp, 'c.json')

        Set<String> names = mutableClient.getReferencesFrom(defaultSnapshotApp, 'a')
        assertEquals(3, names.size())
        assertTrue(names.contains('a'))
        assertTrue(names.contains('b'))
        assertTrue(names.contains('c'))
    }

    @Test
    void testGetSystemParamsHappyPath()
    {
        testClient.clearSysParams()
        System.setProperty('NCUBE_PARAMS', '{"branch":"foo"}')
        assertEquals('foo', ncubeRuntime.systemParams.branch)
        assertNull(ncubeRuntime.systemParams.status)
        assertNull(ncubeRuntime.systemParams.app)
        assertNull(ncubeRuntime.systemParams.tenant)

        // ensure doesn't reparse second time.
        System.setProperty('NCUBE_PARAMS', '{}')
        assertEquals('foo', ncubeRuntime.systemParams.branch)
        assertNull(ncubeRuntime.systemParams.status)
        assertNull(ncubeRuntime.systemParams.app)
        assertNull(ncubeRuntime.systemParams.tenant)


        testClient.clearSysParams()
        System.setProperty("NCUBE_PARAMS", '{"status":"RELEASE", "app":"UD", "tenant":"foo", "branch":"bar"}')
        assertEquals('bar', ncubeRuntime.systemParams.branch)
        assertEquals('RELEASE', ncubeRuntime.systemParams.status)
        assertEquals('UD', ncubeRuntime.systemParams.app)
        assertEquals('foo', ncubeRuntime.systemParams.tenant)

        // ensure doesn't reparse second time.
        System.setProperty('NCUBE_PARAMS', '{}')
        assertEquals('bar', ncubeRuntime.systemParams.branch)
        assertEquals('RELEASE', ncubeRuntime.systemParams.status)
        assertEquals('UD', ncubeRuntime.systemParams.app)
        assertEquals('foo', ncubeRuntime.systemParams.tenant)

        // test invalid json, hands back nice empty map.
        testClient.clearSysParams()
        System.setProperty("NCUBE_PARAMS", '{"status":}')
        assertNull(ncubeRuntime.systemParams.branch)
        assertNull(ncubeRuntime.systemParams.status)
        assertNull(ncubeRuntime.systemParams.app)
        assertNull(ncubeRuntime.systemParams.tenant)
    }

    @Test
    void testGetBranches()
    {
        Set<String> branches = ncubeRuntime.getBranches(ApplicationID.testAppId) as Set<String>
        assert 2 == branches.size()
        assert branches.contains(ApplicationID.TEST_BRANCH)
    }

    @Test
    void testSysLockCubeCreatedWithApp()
    {
        NCube sysLockCube = mutableClient.getCube(defaultBootApp, SYS_LOCK)
        assertEquals(1, sysLockCube.getAxis(AXIS_SYSTEM).columns.size())
        assertNull(sysLockCube.getCell([(AXIS_SYSTEM):null]))
    }

    @Test
    void testSysPermissionsCreatedWithApp()
    {
        NCube sysPermCube = mutableClient.getCube(defaultBootApp, SYS_PERMISSIONS)
        assertEquals(3, sysPermCube.axes.size())
        assertEquals(5, sysPermCube.getAxis(AXIS_RESOURCE).columns.size())
        assertEquals(3, sysPermCube.getAxis(AXIS_ROLE).columns.size())
        assertEquals(4, sysPermCube.getAxis(AXIS_ACTION).columns.size())
    }

    @Test
    void testSysUsergroupsCreatedWithApp()
    {
        NCube sysUsergroupsCube = mutableClient.getCube(defaultBootApp, SYS_USERGROUPS)
        List<Column> userColumns = sysUsergroupsCube.getAxis(AXIS_USER).columns
        assertEquals(2, sysUsergroupsCube.axes.size())
        assertEquals(2, userColumns.size())
        assertEquals(System.getProperty('user.name'), userColumns.get(0).value)
        assertEquals(3, sysUsergroupsCube.getAxis(AXIS_ROLE).columns.size())
    }

    @Test
    void testCreateCubeCreatesSysInfoCubes()
    {
        NCubeInfoDto sysInfoRec = mutableClient.loadCubeRecord(ApplicationID.testAppId, SYS_INFO, [(SEARCH_ALLOW_SYS_INFO): true])
        NCube sysInfo = NCube.createCubeFromRecord(sysInfoRec)
        assert 1 == sysInfo.numDimensions
        Axis attribute = sysInfo.getAxis(AXIS_ATTRIBUTE)
        assert 1 == attribute.size()
        assert attribute.hasDefaultColumn()

        NCubeInfoDto sysInfo000Rec = mutableClient.loadCubeRecord(ApplicationID.testAppId.asBootVersion(), SYS_INFO, [(SEARCH_ALLOW_SYS_INFO): true])
        NCube sysInfo000 = NCube.createCubeFromRecord(sysInfo000Rec)
        assert 1 == sysInfo000.numDimensions
        Axis attribute000 = sysInfo000.getAxis(AXIS_ATTRIBUTE)
        assert 1 == attribute000.size()
        assert attribute000.hasDefaultColumn()
    }

    @Test
    void testUnableToDeleteSysInfo()
    {
        NCube sysClasspath = mutableClient.getCube(ApplicationID.testAppId, SYS_CLASSPATH)
        NCubeInfoDto sysInfo = mutableClient.loadCubeRecord(ApplicationID.testAppId, SYS_INFO, [(SEARCH_ALLOW_SYS_INFO): true])
        assert sysClasspath != null
        assert sysInfo != null

        mutableClient.deleteCubes(ApplicationID.testAppId, [SYS_INFO, SYS_CLASSPATH] as String[])

        sysClasspath = mutableClient.getCube(ApplicationID.testAppId, SYS_CLASSPATH)
        sysInfo = mutableClient.loadCubeRecord(ApplicationID.testAppId, SYS_INFO, [(SEARCH_ALLOW_SYS_INFO): true])
        assert sysClasspath == null
        assert sysInfo != null
    }

    @Test
    void testDuplicateCreatesSysInfo()
    {
        ApplicationID newApp = ApplicationID.testAppId.asBranch('TEST2')
        mutableClient.duplicate(ApplicationID.testAppId, newApp, SYS_CLASSPATH, SYS_CLASSPATH)
        NCube sysClasspath = mutableClient.getCube(newApp, SYS_CLASSPATH)
        NCubeInfoDto sysInfo = mutableClient.loadCubeRecord(newApp, SYS_INFO, [(SEARCH_ALLOW_SYS_INFO): true])
        assert sysClasspath != null
        assert sysInfo != null
    }

    @Test
    void testCommitCubesCreatesSysInfo()
    {
        ApplicationID headApp = ApplicationID.testAppId.asHead()
        NCubeInfoDto headSysInfo = mutableClient.loadCubeRecord(headApp, SYS_INFO, [(SEARCH_ALLOW_SYS_INFO): true])
        NCubeInfoDto headSysInfo000 = mutableClient.loadCubeRecord(headApp.asBootVersion(), SYS_INFO, [(SEARCH_ALLOW_SYS_INFO): true])
        assert headSysInfo == null
        assert headSysInfo000 != null // sys.info is created in 0.0.0 HEAD when permissions n-cubes are created

        mutableClient.commitBranch(ApplicationID.testAppId)
        headSysInfo = mutableClient.loadCubeRecord(headApp, SYS_INFO, [(SEARCH_ALLOW_SYS_INFO): true])
        headSysInfo000 = mutableClient.loadCubeRecord(headApp.asBootVersion(), SYS_INFO, [(SEARCH_ALLOW_SYS_INFO): true])
        assert headSysInfo != null
        assert headSysInfo000 != null
    }

    @Test
    void testSearchNotIncludeSysInfo()
    {
        List<NCubeInfoDto> infoDtos = mutableClient.search(ApplicationID.testAppId, null, null, null)
        assert 1 == infoDtos.size()
        assert SYS_CLASSPATH == infoDtos[0].name
    }

    @Test
    void testSysLockSecurity()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        String origUser = mutableClient.userId
        String otherUser = UniqueIdGenerator.uniqueId
        ApplicationID branchBootAppId = defaultBootApp.asBranch(origUser)
        Map lockCoord = [(AXIS_SYSTEM): null]

        // create branch
        mutableClient.copyBranch(branchBootAppId.asHead(), branchBootAppId)

        // give user branch permission
        NCube branchPermCube = mutableClient.getCube(branchBootAppId, SYS_BRANCH_PERMISSIONS)
        branchPermCube.addColumn(AXIS_USER, otherUser)
        branchPermCube.setCell(true, [(AXIS_USER):otherUser, (AXIS_RESOURCE):null])
        mutableClient.updateCube(branchPermCube)

        // update sys lock in branch
        NCube sysLockCube = mutableClient.getCube(branchBootAppId, SYS_LOCK)
        sysLockCube.setCell(origUser, lockCoord)
        mutableClient.updateCube(sysLockCube)

        // commit sys lock to HEAD
        Object[] cubeInfos = mutableClient.search(branchBootAppId, SYS_LOCK, null, [(SEARCH_ACTIVE_RECORDS_ONLY): true])
        Map<String, Object> commitResult = mutableClient.commitBranch(branchBootAppId, cubeInfos)
        assertEquals(1, (commitResult[mutableClient.BRANCH_UPDATES] as Map).size())

        // make sure HEAD took the lock
        sysLockCube = mutableClient.getCube(branchBootAppId, SYS_LOCK)
        NCube headSysLockCube = mutableClient.getCube(defaultBootApp, SYS_LOCK)
        assertEquals(sysLockCube.getCell(lockCoord), headSysLockCube.getCell(lockCoord))

        // try creating a new cube in branch, should get exception
        NCube testCube = new NCube('test')
        testCube.applicationID = branchBootAppId
        mutableClient.createCube(testCube)  // works without error because current user has the lock

        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        manager.userId = otherUser                   // change user
        try
        {
            mutableClient.updateCube(testCube)
            fail()
        }
        catch (SecurityException e)
        {
            assertTrue(e.message.contains('Application is not locked by you'))
        }
        finally
        {
            manager.userId = origUser
        }
    }

    @Test
    void testSystemIgnoresPermissions()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        String origUser = mutableClient.userId
        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(ApplicationID.testAppId, ncube.name, null, null)
        String prId = mutableClient.generatePullRequestHash(ApplicationID.testAppId, dtos.toArray())
        assert prId

        // assert admin can read prcube
        assert mutableClient.checkPermissions(sysAppId, "tx.${prId}".toString(), Action.READ)
        assert !mutableClient.search(sysAppId, "tx.${prId}", null, null).empty

        // assert other user can't read prcube
        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        manager.userId = UniqueIdGenerator.uniqueId as String
        try
        {
            assert !mutableClient.checkPermissions(sysAppId, "tx.${prId}".toString(), Action.READ)
            assert mutableClient.search(sysAppId, "tx.${prId}", null, null).empty
            // make sure other user can still merge the pr (ignoring permissions)
            Map map = mutableClient.mergePullRequest(prId)
            assert (map.adds as List).size() == 1
            assert (map.deletes as List).size() == 0
            assert (map.updates as List).size() == 0
            assert (map.restores as List).size() == 0
            assert (map.rejects as List).size() == 0
        }
        catch(SecurityException ignore)
        {   // should not get here
            fail()
        }
        finally
        {
            manager.userId = origUser
        }
    }

    @Test
    void testBranchPermissionsCubeCreatedOnNewBranch()
    {
        String userId = System.getProperty('user.name')
        ApplicationID branchAppId = defaultSnapshotApp.asBranch('newBranch')
        mutableClient.copyBranch(branchAppId.asHead(), branchAppId)
        NCube branchPermCube = mutableClient.getCube(branchAppId.asVersion('0.0.0'), SYS_BRANCH_PERMISSIONS)
        Axis userAxis = branchPermCube.getAxis(AXIS_USER)
        Axis resourceAxis = branchPermCube.getAxis(AXIS_RESOURCE)

        assert !branchPermCube.defaultCellValue
        assert 2 == userAxis.columns.size()
        assert 2 == resourceAxis.columns.size()
        assert branchPermCube.getCell([(AXIS_USER):userId, (AXIS_RESOURCE):SYS_BRANCH_PERMISSIONS]) as Boolean
        assert branchPermCube.getCell([(AXIS_USER):userId, (AXIS_RESOURCE):null]) as Boolean
        assert !(branchPermCube.getCell([(AXIS_USER):null, (AXIS_RESOURCE):SYS_BRANCH_PERMISSIONS]) as Boolean)
        assert !(branchPermCube.getCell([(AXIS_USER):null, (AXIS_RESOURCE):null]) as Boolean)
    }

    @Test
    void testBranchPermissionsCubeCreatedOnNewSysBootBranch()
    {
        ApplicationID branchBootAppId = defaultBootApp.asBranch('newBranch')
        mutableClient.copyBranch(defaultBootApp, branchBootAppId)
        NCube branchPermCube = mutableClient.getCube(branchBootAppId.asVersion('0.0.0'), SYS_BRANCH_PERMISSIONS)
        assertNotNull(branchPermCube)
    }

    @Test
    void testBranchPermissionsFail()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        String origUser = mutableClient.userId
        String testAxisName = 'testAxis'
        ApplicationID appId = defaultSnapshotApp.asBranch(origUser)

        //create new branch and make sure of permissions
        mutableClient.copyBranch(appId.asHead(), appId)
        NCube branchPermCube = mutableClient.getCube(defaultBootApp.asBranch(origUser), SYS_BRANCH_PERMISSIONS)
        Axis userAxis = branchPermCube.getAxis(AXIS_USER)
        List<Column> columnList = userAxis.columnsWithoutDefault
        assertEquals(1, columnList.size())
        assertEquals(origUser, columnList.get(0).value)

        //check app permissions cubes
        assertNotNull(mutableClient.getCube(defaultBootApp, SYS_PERMISSIONS))
        assertNotNull(mutableClient.getCube(defaultBootApp, SYS_USERGROUPS))
        assertNotNull(mutableClient.getCube(defaultBootApp, SYS_LOCK))

        //new cube on branch from good user
        NCube testCube = new NCube('test')
        testCube.applicationID = appId
        testCube.addAxis(new Axis(testAxisName, AxisType.DISCRETE, AxisValueType.STRING, true))
        mutableClient.createCube(testCube)

        //try to create a cube as a different user in that branch
        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        try
        {
            manager.userId = UniqueIdGenerator.uniqueId as String
            testCube.setCell('testval', [(testAxisName): null])
            mutableClient.updateCube(testCube)
            fail()
        }
        catch (SecurityException e)
        {
            assertTrue(e.message.contains('not performed'))
            assertTrue(e.message.contains(Action.UPDATE.name()))
            assertTrue(e.message.contains(testCube.name))
        }
        finally
        {
            manager.userId = origUser
        }
    }

    @Test
    void testIsAdminPass()
    {
        String userId = mutableClient.userId
        Map coord = [(AXIS_USER):userId,(AXIS_ROLE):ROLE_ADMIN]

        NCube sysUserCube = mutableClient.getCube(sysAppId, SYS_USERGROUPS)
        assertNotNull(sysUserCube)
        assert sysUserCube.at(coord)

        NCube appUserCube = mutableClient.getCube(defaultBootApp, SYS_USERGROUPS)
        assertNotNull(appUserCube)
        assert appUserCube.at(coord)

        // is sys admin
        assert mutableClient.sysAdmin
        assert mutableClient.isAppAdmin(defaultSnapshotApp)

        // is app admin only
        ApplicationID sysAppBranch = sysAppId.asBranch('test')
        mutableClient.copyBranch(sysAppId, sysAppBranch)
        NCube branchSysUserCube = mutableClient.getCube(sysAppBranch, SYS_USERGROUPS)
        branchSysUserCube.setCell(false, coord)
        mutableClient.updateCube(branchSysUserCube)
        mutableClient.commitBranch(sysAppBranch)

        // clear cached permissions
        testClient.clearPermCache()
        assert !mutableClient.sysAdmin
        assert mutableClient.isAppAdmin(defaultSnapshotApp)
    }

    @Test
    void testIsAdminFail()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        String origUser = manager.userId
        assertNotNull(mutableClient.getCube(defaultBootApp, SYS_USERGROUPS))
        manager.userId = UniqueIdGenerator.uniqueId as String
        try
        {
            assert !mutableClient.isAppAdmin(defaultSnapshotApp)
        }
        finally
        {
            manager.userId = origUser
        }
    }

    @Test
    void testAppPermissionsFail()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        String origUser = mutableClient.userId
        String otherUser = UniqueIdGenerator.uniqueId
        String testAxisName = 'testAxis'
        ApplicationID branchBootApp = defaultBootApp.asBranch(ApplicationID.TEST_BRANCH)

        //check app permissions cubes
        assertNotNull(mutableClient.getCube(defaultBootApp, SYS_PERMISSIONS))
        assertNotNull(mutableClient.getCube(defaultBootApp, SYS_USERGROUPS))
        assertNotNull(mutableClient.getCube(defaultBootApp, SYS_LOCK))

        //set otheruser as having branch permissions
        NCube branchPermCube = mutableClient.getCube(branchBootApp, SYS_BRANCH_PERMISSIONS)
        branchPermCube.getAxis(AXIS_USER).addColumn(otherUser)
        branchPermCube.setCell(true, [(AXIS_USER): otherUser, (AXIS_RESOURCE): null])
        mutableClient.updateCube(branchPermCube)

        //set otheruser as no app permissions
        NCube userCube = mutableClient.getCube(branchBootApp, SYS_USERGROUPS)
        userCube.getAxis(AXIS_USER).addColumn(otherUser)
        userCube.setCell(true, [(AXIS_USER): otherUser, (AXIS_ROLE): ROLE_READONLY])
        mutableClient.updateCube(userCube)
        List<NCubeInfoDto> dtos = mutableClient.search(branchBootApp, userCube.name, null, null)
        mutableClient.commitBranch(branchBootApp, dtos.toArray())
        NCube headUserCube = mutableClient.getCube(defaultBootApp, SYS_USERGROUPS)
        assertFalse(headUserCube.getCell([(AXIS_USER): otherUser, (AXIS_ROLE): ROLE_USER]) as Boolean)
        assertTrue(headUserCube.getCell([(AXIS_USER): otherUser, (AXIS_ROLE): ROLE_READONLY]) as Boolean)

        NCube testCube = new NCube('test')
        testCube.applicationID = defaultSnapshotApp
        testCube.addAxis(new Axis(testAxisName, AxisType.DISCRETE, AxisValueType.STRING, true))
        mutableClient.createCube(testCube)

        //try to update a cube from bad user
        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        try
        {
            manager.userId = otherUser
            testCube.setCell('testval', [(testAxisName): null])
            mutableClient.updateCube(testCube)
            fail()
        }
        catch (SecurityException e)
        {
            assertTrue(e.message.contains('not performed'))
            assertTrue(e.message.contains(Action.UPDATE.name()))
            assertTrue(e.message.contains(testCube.name))
        }
        finally
        {
            manager.userId = origUser
        }
    }

    @Test
    void testCannotRelease000Version()
    {
        try
        {
            mutableClient.releaseCubes(defaultBootApp, '0.0.1')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, ERROR_CANNOT_RELEASE_000)
        }

        try
        {
            mutableClient.releaseCubes(defaultSnapshotApp, '0.0.0')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, ERROR_CANNOT_RELEASE_TO_000)
        }
    }

    @Test
    void testSameRefAxisCubeCreatedInTwoBranches()
    {
        ApplicationID libraryBranch = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'Library', '1.0.0', ReleaseStatus.SNAPSHOT.name(), 'branch1')
        ApplicationID library1 = libraryBranch.asHead()
        NCube ref = new NCube('ref')
        ref.applicationID = libraryBranch
        Axis state = new Axis('state', AxisType.DISCRETE, AxisValueType.CISTRING, false)
        state.addColumn('OH')
        state.addColumn('IN')
        state.addColumn('KY')
        ref.addAxis(state)
        mutableClient.createCube(ref)
        mutableClient.commitBranch(libraryBranch)
        mutableClient.releaseCubes(library1, '2.0.0')
        mutableClient.releaseCubes(library1.asVersion('2.0.0'), '3.0.0')

        ApplicationID branch1 = defaultSnapshotApp.asBranch('branch1')
        ApplicationID branch2 = defaultSnapshotApp.asBranch('branch2')
        ApplicationID lib1rel = library1.asRelease()

        Map<String, Object> args = [:]
        args[REF_TENANT] = lib1rel.tenant
        args[REF_APP] = lib1rel.app
        args[REF_VERSION] = lib1rel.version
        args[REF_STATUS] = lib1rel.status
        args[REF_BRANCH] = lib1rel.branch
        args[REF_CUBE_NAME] = 'ref'
        args[REF_AXIS_NAME] = 'state'
        ReferenceAxisLoader refAxisLoader1 = new ReferenceAxisLoader('ref', 'state', args)
        NCube pointer1 = new NCube('pointer')
        pointer1.applicationID = branch1
        Axis refAxis1 = new Axis('state', 1, false, refAxisLoader1)
        pointer1.addAxis(refAxis1)
        mutableClient.createCube(pointer1)

        args[REF_VERSION] = '2.0.0'
        ReferenceAxisLoader refAxisLoader2 = new ReferenceAxisLoader('ref', 'state', args)
        NCube pointer2 = new NCube('pointer')
        pointer2.applicationID = branch2
        Axis refAxis2 = new Axis('state', 1, false, refAxisLoader2)
        pointer2.addAxis(refAxis2)
        pointer2.setCell('A', [state: 'OH'])
        mutableClient.createCube(pointer2)

        mutableClient.commitBranch(branch2)
        mutableClient.updateBranch(branch1)
        pointer2 = mutableClient.getCube(branch1, pointer1.name)
        assert 'A' == pointer2.getCell([state: 'OH'])
    }

    private void loadTestClassPathCubes()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'sys.versions.json')
        createCubeFromResource(defaultSnapshotApp, 'sys.versions.json')
        NCube ncube = ncubeRuntime.getNCubeFromResource(defaultSnapshotApp, 'sys.classpath.json')
        mutableClient.updateCube(ncube)
        createCubeFromResource(defaultSnapshotApp, 'sys.classpath.local.json')
        createCubeFromResource(defaultSnapshotApp, 'sys.classpath.base.json')
    }

    /**
     * Get List<NCubeInfoDto> for the given ApplicationID, filtered by the pattern.  If using
     * JDBC, it will be used with a LIKE clause.  For Mongo...TBD.
     * For any cube record loaded, for which there is no entry in the app's cube cache, an entry
     * is added mapping the cube name to the cube record (NCubeInfoDto).  This will be replaced
     * by an NCube if more than the name is required.
     */
    private static List<NCubeInfoDto> getDeletedCubesFromDatabase(ApplicationID appId, String pattern)
    {
        Map options = new HashMap()
        options.put(SEARCH_DELETED_RECORDS_ONLY, true)

        return mutableClient.search(appId, pattern, null, options)
    }

    private static boolean checkVersions(Object[] vers, String version)
    {
        boolean foundVer
        for (String ver : vers)
        {
            foundVer = checkContainsIgnoreCase(ver, version, 'SNAPSHOT')
            if (foundVer)
            {
                break

            }
        }
        return boolean
    }
}