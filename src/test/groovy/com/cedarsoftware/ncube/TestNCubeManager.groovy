package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.CommandCellException
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.ncube.formatters.HtmlFormatter
import com.cedarsoftware.ncube.formatters.NCubeTestReader
import com.cedarsoftware.ncube.formatters.NCubeTestWriter
import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.DeepEquals
import com.cedarsoftware.util.io.JsonWriter
import org.junit.After
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail
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
class TestNCubeManager
{
    public static final String APP_ID = 'ncube.test'
    public static final String USER_ID = NCubeManager.userId
    public static ApplicationID defaultSnapshotApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '1.0.0', ReleaseStatus.SNAPSHOT.name(), ApplicationID.TEST_BRANCH)
    public static ApplicationID defaultReleaseApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '1.0.0', ReleaseStatus.RELEASE.name(), ApplicationID.TEST_BRANCH)
    public static ApplicationID defaultBootApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '0.0.0', ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)

    @Before
    public void setUp()
    {
        TestingDatabaseHelper.setupDatabase()
    }

    @After
    public void tearDown()
    {
        TestingDatabaseHelper.tearDownDatabase()
    }

    private static NCubeTest[] createTests()
    {
        CellInfo foo = new CellInfo('int', '5', false, false)
        CellInfo bar = new CellInfo('string', 'none', false, false)
        Map<String, CellInfo> pairs = ['foo': foo, 'bar': bar]
        CellInfo[] cellInfos = [foo, bar] as CellInfo[]

        return [new NCubeTest('foo', pairs, cellInfos)] as NCubeTest[]
    }

    private static NCube createCube()
    {
        NCube<Double> ncube = NCubeBuilder.getTestNCube2D(true)

        def coord = [gender:'male', age:47]
        ncube.setCell(1.0, coord)

        coord.gender = 'female'
        ncube.setCell(1.1d, coord)

        coord.age = 16
        ncube.setCell(1.5d, coord)

        coord.gender = 'male'
        ncube.setCell(1.8d, coord)

        NCubeManager.updateCube(defaultSnapshotApp, ncube, true)
        NCubeManager.updateTestData(defaultSnapshotApp, ncube.name, new NCubeTestWriter().format(createTests()))
        NCubeManager.updateNotes(defaultSnapshotApp, ncube.name, 'notes follow')
        return ncube
    }

    @Test
    void testLoadCubes()
    {
        NCube ncube = NCubeBuilder.getTestNCube2D(true)

        def coord = [gender:'male', age:47]
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
        NCubeManager.updateCube(appId, ncube, true)
        NCubeManager.updateTestData(appId, ncube.name, JsonWriter.objectToJson(coord))
        NCubeManager.updateNotes(appId, ncube.name, 'notes follow')

        NCubeManager.search(appId, name1, null, [(NCubeManager.SEARCH_EXACT_MATCH_NAME) : true]);

        ncube = NCubeBuilder.testNCube3D_Boolean
        String name2 = ncube.name
        NCubeManager.updateCube(appId, ncube, true)

        NCubeManager.clearCache(appId)
        NCubeManager.search(appId, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])

        NCube ncube1 = NCubeManager.getCube(appId, name1)
        NCube ncube2 = NCubeManager.getCube(appId, name2)
        assertNotNull(ncube1)
        assertNotNull(ncube2)
        assertEquals(name1, ncube1.name)
        assertEquals(name2, ncube2.name)
        assertTrue(NCubeManager.isCubeCached(appId, name1))
        assertTrue(NCubeManager.isCubeCached(appId, name2))
        NCubeManager.clearCache(appId)
        assertFalse(NCubeManager.isCubeCached(appId, name1))
        assertFalse(NCubeManager.isCubeCached(appId, name2))

        NCubeManager.deleteCubes(appId, [name1].toArray(), true)
        NCubeManager.deleteCubes(appId, [name2].toArray(), true)

        Map options = [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY) : true, (NCubeManager.SEARCH_EXACT_MATCH_NAME) : true];
        Object[] cubeInfo = NCubeManager.search(appId, name1, null, options)
        assertEquals(0, cubeInfo.length)
        cubeInfo = NCubeManager.search(appId, name2, null, options)
        assertEquals(0, cubeInfo.length)
    }

    @Test
    void testDuplicateWhereNameAndAppIdAreIdentical()
    {
        try {
            NCubeManager.duplicate(defaultSnapshotApp, defaultSnapshotApp, 'test', 'test');
            fail();
        } catch (IllegalArgumentException e) {
            assertTrue(e.message.contains('Could not duplicate'))
        }
    }

    @Test
    void testUpdateSavesTestData()
    {
        NCube cube = createCube()
        assertNotNull(cube)

        Object[] expectedTests = createTests()

        // reading from cache.
        String data = NCubeManager.getTestData(defaultSnapshotApp, 'test.Age-Gender')
        assertTrue(DeepEquals.deepEquals(expectedTests, new NCubeTestReader().convert(data).toArray(new NCubeTest[0])))

        // reload from db
        NCubeManager.clearCache()
        data = NCubeManager.getTestData(defaultSnapshotApp, 'test.Age-Gender')
        assertTrue(DeepEquals.deepEquals(expectedTests, new NCubeTestReader().convert(data).toArray(new NCubeTest[0])))

        //  update cube
        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        data = NCubeManager.getTestData(defaultSnapshotApp, 'test.Age-Gender')
        assertTrue(DeepEquals.deepEquals(expectedTests, new NCubeTestReader().convert(data).toArray(new NCubeTest[0])))

        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, [cube.name].toArray(), true))
    }

    @Test
    void testGetReferencedCubesThatLoadsTwoCubes()
    {
        try
        {
            Set<String> set = new HashSet<>()
            NCubeManager.getReferencedCubeNames(defaultSnapshotApp, 'AnyCube', set)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertNull(e.cause)
        }
    }

    @Test
    void testRenameWithMatchingNames()
    {
        try
        {
            NCubeManager.renameCube(defaultSnapshotApp, 'foo', 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertNull(e.cause)
        }
    }

    @Test
    void testBadCommandCellCommandWithJdbc()
    {
        NCube<Object> continentCounty = new NCube<>('test.ContinentCountries')
        continentCounty.applicationID = defaultSnapshotApp
        NCubeManager.addCube(defaultSnapshotApp, continentCounty)
        continentCounty.addAxis(NCubeBuilder.continentAxis)
        Axis countries = new Axis('Country', AxisType.DISCRETE, AxisValueType.STRING, true)
        countries.addColumn('Canada')
        countries.addColumn('USA')
        countries.addColumn('Mexico')
        continentCounty.addAxis(countries)

        NCube<Object> canada = new NCube<>('test.Provinces')
        canada.applicationID = defaultSnapshotApp
        NCubeManager.addCube(defaultSnapshotApp, canada)
        canada.addAxis(NCubeBuilder.provincesAxis)

        NCube<Object> usa = new NCube<>('test.States')
        usa.applicationID = defaultSnapshotApp
        NCubeManager.addCube(defaultSnapshotApp, usa)
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

        NCubeManager.updateCube(defaultSnapshotApp, continentCounty, true)
        NCubeManager.updateCube(defaultSnapshotApp, usa, true)
        NCubeManager.updateCube(defaultSnapshotApp, canada, true)

        assertEquals(4, NCubeManager.getCubeNames(defaultSnapshotApp).size())

        // make sure items aren't in cache for next load from db for next getCubeNames call
        // during create they got added to database.
        NCubeManager.clearCache()

        assertEquals(4, NCubeManager.getCubeNames(defaultSnapshotApp).size())

        NCubeManager.clearCache()

        NCubeManager.search(defaultSnapshotApp, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        NCube test = NCubeManager.getCube(defaultSnapshotApp, 'test.ContinentCountries')
        assertEquals((Double) test.getCell(coord1), 1.0d, 0.00001d)

        NCubeManager.deleteCubes(defaultSnapshotApp, ['test.ContinentCountries'].toArray(), false)
        NCubeManager.deleteCubes(defaultSnapshotApp, ['test.States'].toArray(), false)
        NCubeManager.deleteCubes(defaultSnapshotApp, ['test.Provinces'].toArray(), false)
        assertEquals(1, NCubeManager.getCubeNames(defaultSnapshotApp).size())

        HtmlFormatter formatter = new HtmlFormatter();
        String s = formatter.format(continentCounty);
        assertTrue(s.contains("column-code"));
    }

    @Test
    void testGetReferencedCubeNames()
    {
        NCube n1 = NCubeManager.getNCubeFromResource('template1.json')
        NCube n2 = NCubeManager.getNCubeFromResource('template2.json')

        NCubeManager.updateCube(defaultSnapshotApp, n1, true)
        NCubeManager.updateCube(defaultSnapshotApp, n2, true)

        Set refs = new TreeSet()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n1.name, refs)

        assertEquals(2, refs.size())
        assertTrue(refs.contains('Template2Cube'))

        refs.clear()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n2.name, refs)
        assertEquals(2, refs.size())
        assertTrue(refs.contains('Template1Cube'))

        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, [n1.name].toArray(), true))
        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, [n2.name].toArray(), true))

        try
        {
            NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n2.name, null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('not get referenced')
            assert e.message.toLowerCase().contains('null passed in for set')
        }
    }

    @Test
    void testGetReferencedCubeNamesSimple()
    {
        NCube n1 = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'aa.json')
        NCube n2 = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'bb.json')

        Set refs = new TreeSet()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n1.name, refs)

        assertEquals(1, refs.size())
        assertTrue(refs.contains('bb'))

        refs.clear()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, n2.name, refs)
        assertEquals(0, refs.size())
    }

    @Test
    void testReferencedCubeCoordinateNotFound()
    {
        NCube n1 = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'aa.json')
        NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'bb.json')

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
        }
    }

    @Test
    void testDuplicateNCubeWithinSameApp()
    {
        NCube n1 = NCubeManager.getNCubeFromResource('stringIds.json')
        NCubeManager.updateCube(defaultSnapshotApp, n1, true)
        ApplicationID newId = new ApplicationID(ApplicationID.DEFAULT_TENANT, APP_ID, '1.1.2', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)

        NCubeManager.duplicate(defaultSnapshotApp, newId, n1.name, n1.name)
        NCube n2 = NCubeManager.getCube(defaultSnapshotApp, n1.name)

        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, [n1.name].toArray(), true))
        assertTrue(NCubeManager.deleteCubes(newId, [n2.name].toArray(), true))
        assertTrue(n1.equals(n2))
    }

    @Test
    void testDuplicateNCubeToNewApp()
    {
        NCube n1 = NCubeManager.getNCubeFromResource('stringIds.json')
        NCubeManager.updateCube(defaultSnapshotApp, n1, true)
        ApplicationID newId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'some.new.app', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)

        NCubeManager.duplicate(defaultSnapshotApp, newId, n1.name, n1.name)
        NCube n2 = NCubeManager.getCube(newId, n1.name)
        assertNotNull(n2)

        //check for permissions cubes in new app
        ApplicationID newBootId = newId.asVersion('0.0.0')
        assertNotNull(NCubeManager.loadCube(newBootId, NCubeManager.SYS_BRANCH_PERMISSIONS))
        assertNotNull(NCubeManager.loadCube(newBootId, NCubeManager.SYS_PERMISSIONS))
        assertNotNull(NCubeManager.loadCube(newBootId, NCubeManager.SYS_USERGROUPS))
        assertNotNull(NCubeManager.loadCube(newBootId, NCubeManager.SYS_LOCK))
    }

    @Test
    void testGetAppNames()
    {
        NCube n1 = NCubeManager.getNCubeFromResource('stringIds.json')
        NCubeManager.updateCube(defaultSnapshotApp, n1, true)

        List<String> names = NCubeManager.getAppNames(defaultSnapshotApp.DEFAULT_TENANT)
        boolean foundName = false
        for (String name : names)
        {
            if ('ncube.test'.equals(name))
            {
                foundName = true
                break
            }
        }

        Map<String, List<String>> vers = NCubeManager.getVersions(defaultSnapshotApp.DEFAULT_TENANT, APP_ID)
        boolean foundVer = false
        String version = '1.0.0'
        for (String ver : vers['SNAPSHOT'])
        {
            if (version.equals(ver))
            {
                foundVer = true
                break
            }
        }

        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, [n1.name].toArray(), true))
        assertTrue(foundName)
        assertTrue(foundVer)
    }

    @Test
    void testChangeVersionValue()
    {
        NCube n1 = NCubeManager.getNCubeFromResource('stringIds.json')
        ApplicationID newId = defaultSnapshotApp.createNewSnapshotId('1.1.20')

        assertNull(NCubeManager.getCube(defaultSnapshotApp, 'idTest'))
        assertNull(NCubeManager.getCube(newId, 'idTest'))
        NCubeManager.updateCube(defaultSnapshotApp, n1, true)

        assertNotNull(NCubeManager.getCube(defaultSnapshotApp, 'idTest'))
        assertNull(NCubeManager.getCube(newId, 'idTest'))
        NCubeManager.changeVersionValue(defaultSnapshotApp, '1.1.20')

        assertNotNull(NCubeManager.getCube(newId, 'idTest'))

        NCube n2 = NCubeManager.getCube(newId, 'idTest')
        assertEquals(n1, n2)

        assertTrue(NCubeManager.deleteCubes(newId, [n1.name].toArray(), true))
    }

    @Test
    void testUpdateOnDeletedCube()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean

        NCubeManager.updateCube(defaultSnapshotApp, ncube1, true)

        assertTrue(ncube1.numDimensions == 3)

        NCubeManager.deleteCubes(defaultSnapshotApp, [ncube1.name].toArray())

        assertTrue(NCubeManager.updateCube(defaultSnapshotApp, ncube1, true))
    }

    @Test
    void testGetBranchChangesFromDatabaseWithInvalidAppIdOfHead()
    {
        try
        {
            VersionControl.getBranchChangesForHead(defaultSnapshotApp.asHead())
            fail 'should not make it here'
        }
        catch (IllegalArgumentException e)
        {
            assertEquals(e.message, "Branch cannot be 'HEAD'")
        }
    }

    @Test
    void testUpdateTestDataOnDeletedCube()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCubeManager.updateCube(defaultSnapshotApp, ncube1, true)
        assertTrue(ncube1.numDimensions == 3)
        NCubeManager.deleteCubes(defaultSnapshotApp, [ncube1.name].toArray())
        NCubeManager.updateTestData(defaultSnapshotApp, ncube1.name, 'test data')
        String testData = NCubeManager.getTestData(defaultSnapshotApp, ncube1.name)
        assert 'test data' == testData
    }

    @Test
    void testConstruction()
    {
        assertNotNull(new NCubeManager())
    }

    @Test
    void testUpdateNotesOnDeletedCube()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCubeManager.updateCube(defaultSnapshotApp, ncube1, true)
        assertTrue(ncube1.numDimensions == 3)
        NCubeManager.deleteCubes(defaultSnapshotApp, [ncube1.name].toArray())
        NCubeManager.updateNotes(defaultSnapshotApp, ncube1.name, 'new notes')
        String notes = NCubeManager.getNotes(defaultSnapshotApp, ncube1.name)
        assert 'new notes' == notes
    }

    @Test
    void testGetNullPersister()
    {
        NCubeManager.setNCubePersister(null)

        try
        {
            NCubeManager.persister
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.toLowerCase().contains('persister not set'))
        }
    }

    @Test
    void testReleaseCubesWhenNoCubesExist()
    {
        try
        {
            NCubeManager.releaseCubes(new ApplicationID('foo', 'bar', '1.0.0', 'SNAPSHOT', 'john'), "1.0.1")
            // No exception should be thrown, just nothing to promote.
        }
        catch (Exception e)
        {
            fail()
        }
    }

    @Test
    void testChangeVersionWhenNoCubesExist()
    {
        try
        {
            NCubeManager.changeVersionValue(new ApplicationID('foo', 'bar', '1.0.0', 'SNAPSHOT', 'john'), "1.0.1")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('no snapshot n-cubes found with version 1.0.0')
        }
    }

    @Test
    void testUpdateNotesNonExistingCube()
    {
        try
        {
            NCubeManager.updateNotes(new ApplicationID('foo', 'bar', '1.0.0', 'SNAPSHOT', 'john'), 'a', "notes")
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('cannot update notes')
            assert e.message.toLowerCase().contains('cube: a does not exist')
        }
    }

    @Test
    void testGetNCubes()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCube ncube2 = NCubeBuilder.getTestNCube2D(true)

        NCubeManager.updateCube(defaultSnapshotApp, ncube1, true)
        NCubeManager.updateCube(defaultSnapshotApp, ncube2, true)

        Object[] cubeList = NCubeManager.search(defaultSnapshotApp, 'test.%', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])

        assertTrue(cubeList != null)
        assertTrue(cubeList.length == 2)

        assertTrue(ncube1.numDimensions == 3)
        assertTrue(ncube2.numDimensions == 2)

        ncube1.deleteAxis('bu')
        NCubeManager.updateCube(defaultSnapshotApp, ncube1, true)
        NCube cube1 = NCubeManager.getCube(defaultSnapshotApp, 'test.ValidTrailorConfigs')
        assertTrue(cube1.numDimensions == 2)    // used to be 3

        // 0 below, because there were no HEAD cubes, so release here, just MOVEs the existing cubes to the next snapshot version
        assertEquals(0, NCubeManager.releaseCubes(defaultSnapshotApp, "1.2.3"))
        ApplicationID next = defaultSnapshotApp.createNewSnapshotId("1.2.3");
        cubeList = NCubeManager.search(next, 'test.*', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        // Two cubes at the new 1.2.3 SNAPSHOT version.
        assert cubeList.length == 2

        String notes1 = NCubeManager.getNotes(next, 'test.ValidTrailorConfigs')
        NCubeManager.getNotes(next, 'test.ValidTrailorConfigs')

        NCubeManager.updateNotes(next, 'test.ValidTrailorConfigs', null)
        notes1 = NCubeManager.getNotes(next, 'test.ValidTrailorConfigs')
        assertTrue(''.equals(notes1))

        NCubeManager.updateNotes(next, 'test.ValidTrailorConfigs', 'Trailer Config Notes')
        notes1 = NCubeManager.getNotes(next, 'test.ValidTrailorConfigs')
        assertTrue('Trailer Config Notes'.equals(notes1))

        NCubeManager.updateTestData(next, 'test.ValidTrailorConfigs', null)
        String testData = NCubeManager.getTestData(next, 'test.ValidTrailorConfigs')
        assertTrue(''.equals(testData))

        NCubeManager.updateTestData(next, 'test.ValidTrailorConfigs', 'This is JSON data')
        testData = NCubeManager.getTestData(next, 'test.ValidTrailorConfigs')
        assertTrue('This is JSON data'.equals(testData))

        // Delete new SNAPSHOT cubes
        assertTrue(NCubeManager.deleteCubes(next, [ncube1.name].toArray(), false))
        assertTrue(NCubeManager.deleteCubes(next, [ncube2.name].toArray(), false))

        // Ensure that all test ncubes are deleted
        cubeList = NCubeManager.search(defaultSnapshotApp, 'test.*', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertTrue(cubeList.length == 0)
    }

    @Test
    void testNotAllowedToDeleteReleaseCubesOrRerelease()
    {
        NCube cube = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'latlon.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        Object[] cubeInfos = NCubeManager.search(defaultSnapshotApp, '*', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertNotNull(cubeInfos)
        assertEquals(2, cubeInfos.length)
        VersionControl.commitBranch(defaultSnapshotApp, cubeInfos)

        try
        {
            NCubeManager.releaseCubes(defaultSnapshotApp, '1.0.0')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('1.0.0 already exists')
        }

        assert 2 == NCubeManager.releaseCubes(defaultSnapshotApp, '1.2.3')

        try
        {
            NCubeManager.deleteCubes(defaultSnapshotApp, [cube.name].toArray())
        }
        catch (IllegalArgumentException e)
        {
            e.message.contains('does not exist')
        }

        try
        {
            NCubeManager.releaseCubes(defaultSnapshotApp, '1.2.3')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('1.0.0 already exists')
        }
    }

    @Test
    void testRenameNCube()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCube ncube2 = NCubeBuilder.getTestNCube2D(true)

        try
        {
            NCubeManager.renameCube(defaultSnapshotApp, ncube1.name, 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Could not rename'))
            assertTrue(e.message.contains('does not exist'))
        }

        NCubeManager.updateCube(defaultSnapshotApp, ncube1, true)
        NCubeManager.updateCube(defaultSnapshotApp, ncube2, true)

        NCubeManager.renameCube(defaultSnapshotApp, ncube1.name, 'test.Floppy')

        Object[] cubeList = NCubeManager.search(defaultSnapshotApp, 'test.*', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])

        assertTrue(cubeList.length == 2)

        NCubeInfoDto nc1 = (NCubeInfoDto) cubeList[0]
        NCubeInfoDto nc2 = (NCubeInfoDto) cubeList[1]

        assertTrue(nc1.toString().startsWith('NONE/ncube.test/1.0.0/SNAPSHOT/TEST/test.Age-Gender') || nc2.toString().startsWith('NONE/ncube.test/1.0.0/SNAPSHOT/TEST/test.Age-Gender'))
        assertTrue(nc1.toString().startsWith('NONE/ncube.test/1.0.0/SNAPSHOT/TEST/test.Floppy') || nc2.toString().startsWith('NONE/ncube.test/1.0.0/SNAPSHOT/TEST/test.Floppy'))

        assertTrue(nc1.name.equals('test.Floppy') || nc2.name.equals('test.Floppy'))
        assertFalse(nc1.name.equals('test.Floppy') && nc2.name.equals('test.Floppy'))

        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, ['test.Floppy'].toArray(), true))
        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, [ncube2.name].toArray(), true))

        assertFalse(NCubeManager.deleteCubes(defaultSnapshotApp, ['test.Floppy'].toArray(), true))
    }

    @Test
    void testNCubeManagerGetCubes()
    {
        NCube ncube1 = NCubeBuilder.testNCube3D_Boolean
        NCube ncube2 = NCubeBuilder.getTestNCube2D(true)

        NCubeManager.updateCube(defaultSnapshotApp, ncube1, true)
        NCubeManager.updateCube(defaultSnapshotApp, ncube2, true)

        // This proves that null is turned into '%' (no exception thrown)
        Object[] cubeList = NCubeManager.search(defaultSnapshotApp, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])

        assertEquals(3, cubeList.length)

        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, [ncube1.name].toArray(), true))
        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, [ncube2.name].toArray(), true))
    }

    @Test
    void testUpdateCubeWithSysClassPath()
    {
        String name = 'Fire'
        //  from setup, assert initial classloader condition (files.cedarsoftware.com)
        ApplicationID customId = new ApplicationID('NONE', 'updateCubeSys', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        assertNotNull(NCubeManager.getUrlClassLoader(customId, [:]))
        assertEquals(1, NCubeManager.getCacheForApp(customId).size())

        NCube testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')

        assertEquals(1, NCubeManager.getUrlClassLoader(customId, [:]).URLs.length)
        assertEquals(2, NCubeManager.getCacheForApp(customId).size())

        testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')   // reload to clear classLoader inside the cell
        NCubeManager.updateCube(customId, testCube, true)

        Map<String, Object> cache = NCubeManager.getCacheForApp(customId)
        assertEquals(1, cache.size())
        assertEquals(testCube, cache.get('sys.classpath'))

        assertTrue(NCubeManager.updateCube(customId, testCube, true))
        assertNotNull(NCubeManager.getUrlClassLoader(customId, [:]))
        assertEquals(2, NCubeManager.getCacheForApp(customId).size())

        testCube = NCubeManager.getCube(customId, 'sys.classpath')
        cache = NCubeManager.getCacheForApp(customId)
        assertEquals(2, cache.size())
        assertEquals(1, NCubeManager.getUrlClassLoader(customId, [:]).URLs.length)

        //  validate item got added to cache.
        assertEquals(testCube, cache.get('sys.classpath'))
    }

    @Test
    void testRenameCubeWithSysClassPath()
    {
        String name = 'Dude'
        //  from setup, assert initial classloader condition (files.cedarsoftware.com)
        ApplicationID customId = new ApplicationID('NONE', 'renameCubeSys', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        final URLClassLoader urlClassLoader1 = NCubeManager.getUrlClassLoader(customId, [:])
        assertNotNull(urlClassLoader1)
        assertEquals(1, NCubeManager.getCacheForApp(customId).size())

        NCube testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')

        final URLClassLoader urlClassLoader = NCubeManager.getUrlClassLoader(customId, [:])
        assertEquals(1, urlClassLoader.URLs.length)
        assertEquals(2, NCubeManager.getCacheForApp(customId).size())

        NCubeManager.clearCache()
        testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')        // reload so that it does not attempt to write classLoader cells (which will blow up)
        testCube.name = 'sys.mistake'
        NCubeManager.updateCube(customId, testCube, true)

        Map<String, Object> cache = NCubeManager.getCacheForApp(customId)
        assertEquals(2, cache.size())     // both sys.mistake and sys.classpath are in the cache

        //  validate item got added to cache.
        assertEquals(testCube, cache.get('sys.mistake'))


        assertTrue(NCubeManager.renameCube(customId, 'sys.mistake', 'sys.classpath'))
        assertNotNull(NCubeManager.getUrlClassLoader(customId, [:]))
        assertEquals(2, NCubeManager.getCacheForApp(customId).size())

        testCube = NCubeManager.getCube(customId, 'sys.classpath')
        assertEquals(2, NCubeManager.getCacheForApp(customId).size())
        assertEquals(1, NCubeManager.getUrlClassLoader(customId, [:]).URLs.length)

        //  validate item got added to cache.
        assertEquals(testCube, cache.get('sys.classpath'))
    }

    @Test
    void testDuplicateCubeWithSysClassPath()
    {
        String name = 'Dude'
        //  from setup, assert initial classloader condition (files.cedarsoftware.com)
        ApplicationID customId = new ApplicationID('NONE', 'renameCubeSys', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        final URLClassLoader urlClassLoader1 = NCubeManager.getUrlClassLoader(customId, [:])
        assertNotNull(urlClassLoader1)
        assertEquals(1, NCubeManager.getCacheForApp(customId).size())

        NCube testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')

        final URLClassLoader urlClassLoader = NCubeManager.getUrlClassLoader(customId, [:])
        assertEquals(1, urlClassLoader.URLs.length)
        assertEquals(2, NCubeManager.getCacheForApp(customId).size())

        NCubeManager.clearCache()
        testCube = NCubeManager.getNCubeFromResource(customId, 'sys.classpath.tests.json')        // reload so that it does not attempt to write classLoader cells (which will blow up)
        testCube.name = 'sys.mistake'
        NCubeManager.updateCube(customId, testCube, true)

        Map<String, Object> cache = NCubeManager.getCacheForApp(customId)
        assertEquals(2, cache.size())     // both sys.mistake and sys.classpath are in the cache

        //  validate item got added to cache.
        assertEquals(testCube, cache.get('sys.mistake'))


        NCubeManager.duplicate(customId, customId, 'sys.mistake', 'sys.classpath')
        assertNotNull(NCubeManager.getUrlClassLoader(customId, [:]))
        assertEquals(2, NCubeManager.getCacheForApp(customId).size())

        testCube = NCubeManager.getCube(customId, 'sys.classpath')
        assertEquals(2, NCubeManager.getCacheForApp(customId).size())
        assertEquals(1, NCubeManager.getUrlClassLoader(customId, [:]).URLs.length)

        //  validate item got added to cache.
        assertEquals(testCube, cache.get('sys.classpath'))
    }

    @Test
    void testMissingBootstrapException()
    {
        try
        {
            NCubeManager.getApplicationID('foo', 'bar', new HashMap())
            fail()
        }
        catch (IllegalStateException e)
        {
            assertTrue(e.message.contains('Missing sys.bootstrap cube'))
            assertTrue(e.message.contains('0.0.0 version'))
        }
    }

    @Test
    void testNCubeManagerUpdateCubeExceptions()
    {
        try
        {
            NCubeManager.updateCube(defaultSnapshotApp, null, true)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('cannot be null'))
        }
    }

    @Test
    void testNCubeManagerCreateCubes()
    {
        ApplicationID id = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'DASHBOARD', ApplicationID.DEFAULT_VERSION, ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        try
        {
            NCubeManager.updateCube(id, null, true)
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('cannot be null'))
        }

        NCube ncube1 = createCube()
        List<NCubeInfoDto> dtos = NCubeManager.getRevisionHistory(defaultSnapshotApp, ncube1.name);
        assertEquals(1, dtos.size());

        createCube()
        dtos = NCubeManager.getRevisionHistory(defaultSnapshotApp, ncube1.name);
        assertEquals(1, dtos.size());

        NCubeManager.deleteCubes(defaultSnapshotApp, [ncube1.name].toArray(), true)
    }

    @Test
    void testNCubeManagerDeleteNotExistingCube()
    {
        ApplicationID id = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'DASHBOARD', '0.1.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH)
        assertFalse(NCubeManager.deleteCubes(id, ['DashboardRoles'].toArray(), true))
    }

    @Test
    void testNotes()
    {
        try
        {
            NCubeManager.getNotes(defaultSnapshotApp, 'DashboardRoles')
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Could not fetch'))
            assertTrue(e.message.contains('notes'))
        }

        createCube()
        String notes = NCubeManager.getNotes(defaultSnapshotApp, 'test.Age-Gender')
        assertNotNull(notes)
        assertTrue(notes.length() > 0)

        try
        {
            NCubeManager.updateNotes(defaultSnapshotApp, 'test.funky', null)
            fail('should not make it here')
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains('not'))
            assertTrue(e.message.contains('update'))
            assertTrue(e.message.contains('exist'))
        }

        try
        {
            ApplicationID newId = defaultSnapshotApp.createNewSnapshotId('0.1.1')
            NCubeManager.getNotes(newId, 'test.Age-Gender')
            fail('Should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Could not fetch'))
            assertTrue(e.message.contains('notes'))
        }

        NCubeManager.deleteCubes(defaultSnapshotApp, ['test.Age-Gender'].toArray(), true)
    }

    @Test
    void testNCubeManagerTestData()
    {
        try
        {
            NCubeManager.getTestData(defaultSnapshotApp, 'DashboardRoles')
            fail('should not make it here')
        }
        catch (Exception e)
        {
            assertTrue(e instanceof IllegalArgumentException)
        }

        createCube()
        String testData = NCubeManager.getTestData(defaultSnapshotApp, 'test.Age-Gender')
        assertNotNull(testData)
        assertTrue(testData.length() > 0)

        try
        {
            NCubeManager.updateTestData(defaultSnapshotApp, 'test.funky', null)
            fail('should not make it here')
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains('no'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('exist'))
        }

        ApplicationID newId = defaultSnapshotApp.createNewSnapshotId('0.1.1')
        try
        {
            NCubeManager.getTestData(newId, 'test.Age-Gender')
            fail('Should not make it here')
        }
        catch (Exception e)
        {
            assertTrue(e.message.contains('no'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('exist'))
        }

        assertTrue(NCubeManager.deleteCubes(defaultSnapshotApp, ['test.Age-Gender'].toArray()))
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
        NCubeManager.deleteCubes(defaultSnapshotApp, [ncube.name].toArray(), true)
    }

    @Test
    void testLoadCubesWithNullApplicationID()
    {
        try
        {
            // This API is now package friendly and only to be used by tests or NCubeManager implementation work.
            NCubeManager.search(null, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('cannot be null')
        }
    }

    @Test(expected = RuntimeException.class)
    void testGetNCubesFromResourceException()
    {
        NCubeManager.getNCubesFromResource(null)
    }

    @Test
    void testRestoreCubeWithEmptyArray()
    {
        try
        {
            NCubeManager.restoreCubes(defaultSnapshotApp)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('empty array'))
            assertTrue(e.message.contains('to be restored'))
        }
    }

    @Test
    void testRollbackBranchWhenCubeDoesntExist()
    {
        Object[] names = new Object[2]
        names[0] = "TestCube"

        int x = VersionControl.rollbackCubes(defaultSnapshotApp, names)
        assert 0 == x
    }

    @Test
    void testRollbackMultiple()
    {
        ApplicationID appId = new ApplicationID('NONE', 'none', '1.0.0', 'SNAPSHOT', 'jdereg')

        NCube cube1 = NCubeManager.getNCubeFromResource(appId, 'testCube1.json')
        NCubeManager.updateCube(appId, cube1, true)
        String sha1 = cube1.sha1()

        NCube cube2 = NCubeManager.getNCubeFromResource(appId, 'template1.json')
        NCubeManager.updateCube(appId, cube2, true)
        String sha2 = cube2.sha1()

        NCube cube3 = NCubeManager.getNCubeFromResource(appId, 'urlPieces.json')
        NCubeManager.updateCube(appId, cube3, true)
        String sha3 = cube3.sha1()

        NCube cube4 = NCubeManager.getNCubeFromResource(appId, 'months.json')
        NCubeManager.updateCube(appId, cube4, true)
        String sha4 = cube4.sha1()

        List<NCubeInfoDto> changes = VersionControl.getBranchChangesForHead(appId)
        assert 4 == changes.size()

        Object[] cubes = [cube1.name, cube2.name, cube3.name, cube4.name].toArray()
        assert 4 == VersionControl.rollbackCubes(appId, cubes)
        changes = VersionControl.getBranchChangesForHead(appId)
        assert 0 == changes.size()  // Looks like we've got no cubes (4 deleted ones but 0 active)

        List<NCubeInfoDto> deleted = NCubeManager.search(appId, null, null, [(NCubeManager.SEARCH_DELETED_RECORDS_ONLY): true])
        assert 4 == deleted.size()  // Rollback of a create is a DELETE

        NCubeManager.restoreCubes(appId, cubes)
        assert 4 == NCubeManager.getCubeNames(appId).size()

        changes = VersionControl.getBranchChangesForHead(appId)
        assert 4 == changes.size()

        NCubeManager.loadCube(appId, cubes[0]).sha1() == sha1
        NCubeManager.loadCube(appId, cubes[1]).sha1() == sha2
        NCubeManager.loadCube(appId, cubes[2]).sha1() == sha3
        NCubeManager.loadCube(appId, cubes[3]).sha1() == sha4
    }

    @Test
    void testRestoreCubeWithNullArray()
    {
        try
        {
            NCubeManager.restoreCubes(defaultSnapshotApp, null)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('empty array'))
            assertTrue(e.message.contains('to be restored'))
        }
    }

    @Test
    void testRestoreExistingCube()
    {
        NCube cube = createCube()
        try
        {
            NCubeManager.restoreCubes(defaultSnapshotApp, cube.name)
            fail('should not make it here')
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.toLowerCase().contains('cannot restore'))
            assertTrue(e.message.contains('not deleted in app'))
        }
        NCubeManager.deleteCubes(defaultSnapshotApp, [cube.name].toArray())
    }

    @Test
    void testRestoreDeletedCube()
    {
        NCube cube = createCube()
        Object[] records = NCubeManager.search(defaultSnapshotApp, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(2, records.length) // 2 (because of sys.classpath)

        records = NCubeManager.search(defaultSnapshotApp, cube.name, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(1, records.length)
        String sha1 = records[0].sha1

        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())

        NCubeManager.deleteCubes(defaultSnapshotApp, [cube.name].toArray())

        assertEquals(1, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())

        records = NCubeManager.search(defaultSnapshotApp, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(1, records.length)

        NCubeManager.restoreCubes(defaultSnapshotApp, cube.name)
        records = NCubeManager.search(defaultSnapshotApp, 'test*', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(1, records.length)

        NCube ncube = NCubeManager.loadCube(defaultSnapshotApp, cube.name);
        assertNotNull ncube
        assert ncube.sha1() == cube.sha1()
        assert ncube.sha1() == sha1

        NCubeManager.deleteCubes(defaultSnapshotApp, [cube.name].toArray())
    }

    @Test
    void testLoadCubeWithNonExistingName()
    {
        NCube ncube = NCubeManager.loadCube(defaultSnapshotApp, 'sdflsdlfk');
        assertNull ncube
    }

    @Test
    void testRestoreCubeWithCubeThatDoesNotExist()
    {
        try
        {
            NCubeManager.restoreCubes(defaultSnapshotApp, 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.toLowerCase().contains('cannot restore'))
            assertTrue(e.message.contains('not deleted in app'))
        }
    }

    @Test
    void testGetRevisionHistory()
    {
        try
        {
            NCubeManager.getRevisionHistory(defaultSnapshotApp, 'foo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('Cannot fetch'))
            assertTrue(e.message.contains('does not exist'))
        }
    }

    @Test
    void testDeleteWithRevisions()
    {
        NCube cube = createCube()
        assertEquals(2, NCubeManager.search(defaultSnapshotApp, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, null).size())
        assertEquals(1, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).size())

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        assertEquals(2, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).size())
        assertEquals(2, NCubeManager.search(defaultSnapshotApp, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())

        Axis conAxis = NCubeBuilder.continentAxis
        cube.addAxis(conAxis)

        NCubeManager.updateCube(defaultSnapshotApp, cube, true)

        assertEquals(3, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).size())
        assertEquals(2, NCubeManager.search(defaultSnapshotApp, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())

        NCubeManager.deleteCubes(defaultSnapshotApp, [cube.name].toArray())

        assertEquals(1, NCubeManager.search(defaultSnapshotApp, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(1, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())
        assertEquals(4, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).size())

        NCubeManager.restoreCubes(defaultSnapshotApp, cube.name)
        NCube restored = NCubeManager.loadCube(defaultSnapshotApp, cube.name)
        assert cube.sha1() == restored.sha1()

        assertEquals(2, NCubeManager.search(defaultSnapshotApp, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(0, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())
        assertEquals(5, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).size())

        NCubeManager.deleteCubes(defaultSnapshotApp, [cube.name].toArray())

        assertEquals(1, NCubeManager.search(defaultSnapshotApp, '', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true]).size())
        assertEquals(1, getDeletedCubesFromDatabase(defaultSnapshotApp, '').size())
        assertEquals(6, NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name).size())
    }

    @Test
    void testRevisionHistory()
    {
        NCube cube = createCube()
        Object[] his = NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name)
        NCubeInfoDto[] history = (NCubeInfoDto[]) his;
        assertEquals(1, history.length)
        assert history[0].name == 'test.Age-Gender'
        assert history[0].revision == '0'
        assert history[0].createHid == USER_ID
        assert history[0].notes == 'notes follow'
        assertNotNull history[0].toString()

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        his = NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name)
        history = (NCubeInfoDto[]) his;
        assertEquals(2, history.length)
        assert history[1].name == 'test.Age-Gender'
        assert history[0].revision == '1'
        assert history[1].revision == '0'
        assert history[1].createHid == USER_ID
        assert history[1].notes == 'notes follow'

        long rev0Id = Converter.convert(history[1].id, long.class)
        long rev1Id = Converter.convert(history[0].id, long.class)
        NCube rev0 = NCubeManager.loadCubeById(rev0Id)
        NCube rev1 = NCubeManager.loadCubeById(rev1Id)

        assert rev0.getNumDimensions() == 2
        assert rev1.getNumDimensions() == 3
    }

    @Test
    void testRevisionHistoryIgnoreVersion()
    {
        NCube cube = createCube()
        Object[] his = NCubeManager.getRevisionHistory(defaultSnapshotApp, cube.name)
        NCubeInfoDto[] history = (NCubeInfoDto[]) his;
        assertEquals(1, history.length)
        assert history[0].name == 'test.Age-Gender'
        assert history[0].revision == '0'
        assert history[0].createHid == USER_ID
        assert history[0].notes == 'notes follow'
        assertNotNull history[0].toString()

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        Map<String, Object> result = VersionControl.commitBranch(defaultSnapshotApp, NCubeManager.search(defaultSnapshotApp, cube.name, null, null))
        assert result[VersionControl.BRANCH_ADDS].size() == 1
        assert result[VersionControl.BRANCH_DELETES].size() == 0
        assert result[VersionControl.BRANCH_UPDATES].size() == 0
        assert result[VersionControl.BRANCH_RESTORES].size() == 0
        assert result[VersionControl.BRANCH_REJECTS].size() == 0
        NCubeManager.releaseVersion(defaultSnapshotApp, '2.0.0')
        List<NCubeInfoDto> fullHistory = NCubeManager.getRevisionHistory(defaultSnapshotApp.asVersion('2.0.0').asHead(), cube.name, true)
        assert fullHistory.size() == 1
    }

    @Test
    void testNCubeInfoDto()
    {
        NCube cube = createCube()
        def history = NCubeManager.search(cube.getApplicationID(), '*', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(2, history.size())     // sys.classpath too
        assertTrue history[0] instanceof NCubeInfoDto
        assertTrue history[1] instanceof NCubeInfoDto

        Axis oddAxis = NCubeBuilder.getOddAxis(true)
        cube.addAxis(oddAxis)

        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        history = NCubeManager.search(cube.getApplicationID(), '*', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertEquals(2, history.size())
        assertTrue history[0] instanceof NCubeInfoDto
        assertTrue history[1] instanceof NCubeInfoDto
    }

    @Test
    void testResolveClasspathWithInvalidUrl()
    {
        NCubeManager.clearCache()
        NCube cube = NCubeManager.getNCubeFromResource('sys.classpath.invalid.url.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        createCube()

        // force reload from hsql and reget classpath
        assertNotNull(NCubeManager.getUrlClassLoader(defaultSnapshotApp, [:]))

        NCubeManager.clearCache(defaultSnapshotApp)
        assertNotNull(NCubeManager.getUrlClassLoader(defaultSnapshotApp, [:]))

        NCubeManager.getCube(defaultSnapshotApp, 'test.AgeGender')
        GroovyClassLoader loader = (GroovyClassLoader) NCubeManager.getUrlClassLoader(defaultSnapshotApp, [:])
        assertEquals(0, loader.URLs.length)
    }

    @Test
    void testResolveClassPath()
    {
        loadTestClassPathCubes()

        Map map = [env:'DEV']
        NCube baseCube = NCubeManager.getCube(defaultSnapshotApp, 'sys.classpath.base')

        assertEquals('http://files.cedarsoftware.com/tests/ncube/cp1/', baseCube.getCell(map))
        map.env = 'CERT'
        assertEquals('http://files.cedarsoftware.com/tests/ncube/cp2/', baseCube.getCell(map))

        NCube classPathCube = NCubeManager.getCube(defaultSnapshotApp, 'sys.classpath')
        URLClassLoader loader = (URLClassLoader) classPathCube.getCell(map)
        assertEquals(1, loader.URLs.length);
        assertEquals('http://files.cedarsoftware.com/tests/ncube/cp2/', loader.URLs[0].toString());
    }

    @Test
    void testResolveRelativeUrl()
    {
        // Sets App classpath to http://files.cedarsoftware.com
        NCubeManager.getNCubeFromResource(ApplicationID.testAppId, 'sys.classpath.cedar.json')

        // Rule cube that expects tests/ncube/hello.groovy to be relative to http://files.cedarsoftware.com
        NCube hello = NCubeManager.getNCubeFromResource(ApplicationID.testAppId, 'resolveRelativeHelloGroovy.json')

        // When run, it will set up the classpath (first cube loaded for App), and then
        // it will run the rule cube.  This cube has a relative URL (relative to the classpath above).
        // The code from the website will be pulled down, executed, and the result (Hello, World.)
        // will be returned.
        String s = (String) hello.getCell([:])
        assertEquals('Hello, world.', s)

        URL absUrl = NCubeManager.getActualUrl(ApplicationID.testAppId, 'tests/ncube/hello.groovy', [:])
        assertEquals('http://files.cedarsoftware.com/tests/ncube/hello.groovy', absUrl.toString())
    }

    @Test
    void testResolveUrlBadArgs()
    {
        try
        {
            NCubeManager.getActualUrl(ApplicationID.testAppId, null, [:])
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('resolve'))
            assertTrue(e.message.contains('null'))
            assertTrue(e.message.contains('empty'))
        }
    }

    @Test
    void testResolveUrlFullyQualified()
    {
        String url = 'http://files.cedarsoftware.com'
        URL ret = NCubeManager.getActualUrl(ApplicationID.testAppId, url, [:])
        assertEquals(url, ret.toString())

        url = 'https://files.cedarsoftware.com'
        ret = NCubeManager.getActualUrl(ApplicationID.testAppId, url, [:])
        assertEquals(url, ret.toString())

        url = 'file://Users/joe/Development'
        ret = NCubeManager.getActualUrl(ApplicationID.testAppId, url, [:])
        assertEquals(url, ret.toString())
    }

    @Test
    void testResolveUrlBadApp()
    {
        try
        {
            NCubeManager.getActualUrl(new ApplicationID('foo', 'bar', '1.0.0', ApplicationID.DEFAULT_STATUS, ApplicationID.TEST_BRANCH), 'tests/ncube/hello.groovy', [:])
            fail()
        }
        catch (IllegalArgumentException ignored)
        { }
    }

    @Test
    void testMalformedUrl()
    {
        NCube cube = NCubeManager.getNCubeFromResource("urlContent.json")
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
        ApplicationID johnAppId = new ApplicationID('ibm', 'deep.blue', '1.0.0', 'SNAPSHOT', 'jdereg')
        NCube cube = NCubeManager.getNCubeFromResource(johnAppId, 'testCube6.json')
        NCubeManager.updateCube(johnAppId, cube, true)
        List<NCubeInfoDto> cubes = VersionControl.getBranchChangesForHead(johnAppId)
        VersionControl.commitBranch(johnAppId, cubes.toArray())
        ApplicationID kenAppId  = new ApplicationID('ibm', 'deep.blue', '1.0.0', 'SNAPSHOT', 'ken')
        NCubeManager.copyBranch(kenAppId.asHead(), kenAppId)

        // At this point, there are three branches, john, ken, HEAD.  1 cube in all, same state
        cubes = NCubeManager.search(johnAppId, null, null, null)
        assert cubes.size() == 1
        assert cubes[0].revision == '0'
        cubes = NCubeManager.search(kenAppId, null, null, null)
        assert cubes.size() == 1
        assert cubes[0].revision == '0'
        cubes = NCubeManager.search(johnAppId.asHead(), null, null, null)
        assert cubes.size() == 1
        assert cubes[0].revision == '0'

        // Ken's branch is going to modify cube and commit it to HEAD
        cube = NCubeManager.loadCube(kenAppId, 'TestCube')
        cube.setCell('foo', [gender:'male'])
        String kenSha1 = cube.sha1()
        NCubeManager.updateCube(kenAppId, cube, true)
        cubes = VersionControl.getBranchChangesForHead(kenAppId)
        assert cubes.size() == 1
        Map <String, Object> result = VersionControl.commitBranch(kenAppId, cubes)
        assert result[VersionControl.BRANCH_ADDS].size() == 0
        assert result[VersionControl.BRANCH_DELETES].size() == 0
        assert result[VersionControl.BRANCH_UPDATES].size() == 1
        assert result[VersionControl.BRANCH_RESTORES].size() == 0
        assert result[VersionControl.BRANCH_REJECTS].size() == 0
        NCube cubeHead = NCubeManager.loadCube(kenAppId.asHead(), 'TestCube')
        assert cubeHead.sha1() == cube.sha1()

        // At this point, HEAD and Ken are same, John's branch is behind.
        NCube johnCube = NCubeManager.loadCube(johnAppId, 'TestCube')
        assert johnCube.sha1() != kenSha1
        johnCube.setCell('foo', [gender:'male'])
        assert kenSha1 == cube.sha1()

        // I made the same changes on my branch to my cube.
        NCubeManager.updateCube(johnAppId, johnCube, true)
        johnCube = NCubeManager.loadCube(johnAppId, 'TestCube')
        assert kenSha1 == johnCube.sha1()

        // Verify that before the Update Branch, we show one (1) branch change
        cubes = VersionControl.getBranchChangesForHead(johnAppId)
        assert cubes.size() == 0

        List dtos2 = VersionControl.getHeadChangesForBranch(johnAppId)
        assert dtos2.size() == 1
        assert dtos2[0].name == 'TestCube'
        assert dtos2[0].changeType == ChangeType.FASTFORWARD.code

        // Update john branch (no changes are shown - it auto-merged)
        Map map = VersionControl.updateBranch(johnAppId)
        assert map.adds.size() == 0
        assert map.deletes.size() == 0
        assert map.updates.size() == 0
        assert map.restores.size() == 0
        assert map.fastforwards.size() == 1
        assert map.rejects.size() == 0

        cubes = VersionControl.getBranchChangesForHead(johnAppId)
        assert cubes.size() == 0    // No changes being returned

        cubes = NCubeManager.search(johnAppId, null, null, null)
        assert cubes.size() == 1
        assert cubes[0].revision == '1'

        // Stuck this code on the end, to test multiple answers for getVersions()
        johnAppId = new ApplicationID('ibm', 'deep.blue', '1.1.0', 'SNAPSHOT', 'jdereg')
        cube = NCubeManager.getNCubeFromResource(johnAppId, 'testCube4.json')
        NCubeManager.updateCube(johnAppId, cube, true)

        Map<String, List<String>> versions = NCubeManager.getVersions('ibm', 'deep.blue')
        assert versions.size() == 2
        assert versions['SNAPSHOT'].size() == 3
        assert versions['SNAPSHOT'].contains('1.0.0')
        assert versions['SNAPSHOT'].contains('1.1.0')
        assert versions['RELEASE'].size() == 0

        assert 1 == NCubeManager.getBranchCount(johnAppId)
    }

    @Test
    void testCopyBranch()
    {
        ApplicationID copyAppId = defaultSnapshotApp.asBranch('copy')
        NCube cube = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'latlon.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, true)

        NCubeManager.copyBranch(defaultSnapshotApp, copyAppId)
        NCube copiedCube = NCubeManager.loadCube(copyAppId, cube.name)
        assertNotNull(copiedCube);
    }

    @Test
    void testCopyBranchWhenReleaseVersionAlreadyExists()
    {
        VersionControl.commitBranch(defaultSnapshotApp, NCubeManager.search(defaultSnapshotApp, null, null, null).toArray())
        NCubeManager.releaseCubes(defaultSnapshotApp, '2.0.0')
        ApplicationID copyAppId = defaultSnapshotApp.asBranch('copy')
        try
        {
            NCubeManager.copyBranch(defaultSnapshotApp, copyAppId)
            fail()
        } catch(IllegalArgumentException e) {
            assertTrue(e.message.contains('exists'))
        }
    }

    @Test
    void testLoadCubeByUsingNonExistingSha1()
    {
        NCube cube = createCube()
        try
        {
            NCubeManager.persister.loadCubeBySha1(cube.applicationID, cube.name, "phonySha1")
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('unable to find')
        }
    }

    @Test
    void testUpdateBranchCubeHeadSha1BadArgs()
    {
        try
        {
            NCubeManager.persister.updateBranchCubeHeadSha1(null, 'badSha1')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('id cannot be empty')
        }
        try
        {
            NCubeManager.persister.updateBranchCubeHeadSha1(75, '')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('sha-1 cannot be empty')
        }

        try
        {
            NCubeManager.persister.updateBranchCubeHeadSha1(75, 'phony')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.toLowerCase().contains('no record found')
        }
    }

    @Test
    void testBadSearchFlags()
    {
        Map options = [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY): true,
                       (NCubeManager.SEARCH_DELETED_RECORDS_ONLY): true]
        try
        {
            NCubeManager.search(defaultSnapshotApp, null, null, options)
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.contains('activeRecordsOnly and deletedRecordsOnly are mutually exclusive')
        }
    }

//    @Test
//    void testGetApplicationId()
//    {
//        // TODO: Cannot get the Mock to work
//        loadTestClassPathCubes()
//        loadTestBootstrapCubes()
//
//        String tenant = defaultSnapshotApp.tenant
//        String app = defaultSnapshotApp.app
//
//        ApplicationID bootTestAppId = new ApplicationID(tenant, app, "0.0.0", ReleaseStatus.SNAPSHOT.name(), ApplicationID.TEST_BRANCH)
//
//        MockFor META_MOCK = new MockFor(ApplicationID)
//        META_MOCK.demand.getBootVersion() { return bootTestAppId }
//
//        ApplicationID bootAppId = NCubeManager.getApplicationID(bootTestAppId.tenant, bootTestAppId.app, null)
//        assertEquals(new ApplicationID(tenant, app, "1.0.0", ApplicationID.DEFAULT_STATUS, "TEST"), bootAppId)
//
//        Map map = new HashMap()
//        map.put('env', 'DEV')
//
//        bootAppId = NCubeManager.getApplicationID(defaultSnapshotApp.tenant, defaultSnapshotApp.app, map)
//        assertEquals(defaultSnapshotApp.tenant, bootAppId.tenant)
//        assertEquals(defaultSnapshotApp.app, bootAppId.app)
//        assertEquals(defaultSnapshotApp.version, '1.0.0')
//        assertEquals(defaultSnapshotApp.status, bootAppId.status)
//    }

    @Test
    void testMutateReleaseCube()
    {
        assertNotNull(NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_LOCK))
        NCube cube = NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'latlon.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        Object[] cubeInfos = NCubeManager.search(defaultSnapshotApp, '*', null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        assertNotNull(cubeInfos)
        assertEquals(2, cubeInfos.length)
        NCubeManager.releaseCubes(defaultSnapshotApp, "1.2.3")
        try
        {
            NCubeManager.deleteCubes(defaultReleaseApp, [cube.name].toArray())
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('cannot'))
            assertTrue(e.message.contains('deleted'))
        }

        try
        {
            NCubeManager.renameCube(defaultReleaseApp, cube.name, 'jumbo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('rename'))
        }

        try
        {
            NCubeManager.restoreCubes(defaultReleaseApp, cube.name)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('restore'))
        }

        try
        {
            NCubeManager.updateCube(defaultReleaseApp, cube, true)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('update'))
        }

        try
        {
            NCubeManager.changeVersionValue(defaultReleaseApp, '1.2.3')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('change'))
            assertTrue(e.message.contains('version'))
        }

        try
        {
            NCubeManager.duplicate(defaultSnapshotApp, defaultReleaseApp, cube.name, 'jumbo')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains('RELEASE'))
            assertTrue(e.message.contains('cube'))
            assertTrue(e.message.contains('annot'))
            assertTrue(e.message.contains('duplicate'))
            assertTrue(e.message.contains('version'))
        }
    }

    @Test
    void testRenameCubeThatDoesNotExist()
    {
        try {
            NCubeManager.renameCube(defaultSnapshotApp, 'foo', 'bar')
            fail();
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains("Could not rename"));
            assertTrue(e.message.contains("does not exist"))
        }
    }

    @Test
    void testCircularCubeReference()
    {
        NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'a.json')
        NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'b.json')
        NCubeManager.getNCubeFromResource(defaultSnapshotApp, 'c.json')

        Set<String> names = new TreeSet<>()
        NCubeManager.getReferencedCubeNames(defaultSnapshotApp, 'a', names)
        assertEquals(3, names.size())
        assertTrue(names.contains('a'))
        assertTrue(names.contains('b'))
        assertTrue(names.contains('c'))
    }

    @Test
    void testGetSystemParamsHappyPath()
    {
        NCubeManager.systemParams = null;
        System.setProperty("NCUBE_PARAMS", '{"branch":"foo"}')
        assertEquals('foo', NCubeManager.getSystemParams().branch);
        assertNull(NCubeManager.getSystemParams().status);
        assertNull(NCubeManager.getSystemParams().app);
        assertNull(NCubeManager.getSystemParams().tenant);

        // ensure doesn't reparse second time.
        System.setProperty('NCUBE_PARAMS', '{}')
        assertEquals('foo', NCubeManager.getSystemParams().branch);
        assertNull(NCubeManager.getSystemParams().status);
        assertNull(NCubeManager.getSystemParams().app);
        assertNull(NCubeManager.getSystemParams().tenant);


        NCubeManager.systemParams = null;
        System.setProperty("NCUBE_PARAMS", '{"status":"RELEASE", "app":"UD", "tenant":"foo", "branch":"bar"}')
        assertEquals('bar', NCubeManager.getSystemParams().branch);
        assertEquals('RELEASE', NCubeManager.getSystemParams().status);
        assertEquals('UD', NCubeManager.getSystemParams().app);
        assertEquals('foo', NCubeManager.getSystemParams().tenant);

        // ensure doesn't reparse second time.
        System.setProperty('NCUBE_PARAMS', '{}')
        assertEquals('bar', NCubeManager.getSystemParams().branch);
        assertEquals('RELEASE', NCubeManager.getSystemParams().status);
        assertEquals('UD', NCubeManager.getSystemParams().app);
        assertEquals('foo', NCubeManager.getSystemParams().tenant);

        // test invalid json, hands back nice empty map.
        NCubeManager.systemParams = null;
        System.setProperty("NCUBE_PARAMS", '{"status":}')
        assertNull(NCubeManager.getSystemParams().branch);
        assertNull(NCubeManager.getSystemParams().status);
        assertNull(NCubeManager.getSystemParams().app);
        assertNull(NCubeManager.getSystemParams().tenant);
    }

    @Test
    void testGetBranches()
    {
        Set<String> branches = NCubeManager.getBranches(ApplicationID.testAppId)
        assertEquals(1, branches.size())
        assertEquals(ApplicationID.TEST_BRANCH, branches.getAt(0))
    }

    @Test
    void testSysLockCubeCreatedWithApp()
    {
        NCube sysLockCube = NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_LOCK)
        assertEquals(1, sysLockCube.getAxis(NCubeManager.AXIS_SYSTEM).getColumns().size())
        assertNull(sysLockCube.getCell([(NCubeManager.AXIS_SYSTEM):null]))
    }

    @Test
    void testSysPermissionsCreatedWithApp()
    {
        NCube sysPermCube = NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_PERMISSIONS)
        assertEquals(3, sysPermCube.axes.size())
        assertEquals(5, sysPermCube.getAxis(NCubeManager.AXIS_RESOURCE).columns.size())
        assertEquals(3, sysPermCube.getAxis(NCubeManager.AXIS_ROLE).columns.size())
        assertEquals(4, sysPermCube.getAxis(NCubeManager.AXIS_ACTION).columns.size())
    }

    @Test
    void testSysUsergroupsCreatedWithApp()
    {
        NCube sysUsergroupsCube = NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_USERGROUPS)
        List<Column> userColumns = sysUsergroupsCube.getAxis(NCubeManager.AXIS_USER).columns
        assertEquals(2, sysUsergroupsCube.axes.size())
        assertEquals(2, userColumns.size())
        assertEquals(NCubeManager.userId, userColumns.get(0).value)
        assertEquals(3, sysUsergroupsCube.getAxis(NCubeManager.AXIS_ROLE).columns.size())
    }

    @Test
    void testSysLockSecurity()
    {
        String userId = NCubeManager.userId
        ApplicationID branchBootAppId = defaultBootApp.asBranch(userId)
        Map lockCoord = [(NCubeManager.AXIS_SYSTEM):null]

        // create branch
        NCubeManager.copyBranch(branchBootAppId.asHead(), branchBootAppId)

        // update sys lock in branch
        NCube sysLockCube = NCubeManager.loadCube(branchBootAppId, NCubeManager.SYS_LOCK)
        sysLockCube.setCell(userId, lockCoord)
        NCubeManager.updateCube(branchBootAppId, sysLockCube, true)

        // commit sys lock to HEAD
        Object[] cubeInfos = NCubeManager.search(branchBootAppId, NCubeManager.SYS_LOCK, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
        Map<String, Object> commitResult = VersionControl.commitBranch(branchBootAppId, cubeInfos)
        assertEquals(1, commitResult[VersionControl.BRANCH_UPDATES].size())

        // make sure HEAD took the lock
        sysLockCube = NCubeManager.loadCube(branchBootAppId, NCubeManager.SYS_LOCK)
        NCube headSysLockCube = NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_LOCK)
        assertEquals(sysLockCube.getCell(lockCoord), headSysLockCube.getCell(lockCoord))

        // try creating a new cube in branch, should get exception
        NCube testCube = new NCube('test')
        testCube.setApplicationID(branchBootAppId)
        NCubeManager.updateCube(branchBootAppId, testCube, true)  // works without error because current user has the lock
        String currUser = NCubeManager.userId
        NCubeManager.setUserId('garpley')                   // change user
        try
        {
            NCubeManager.updateCube(branchBootAppId, testCube, true)
            fail()
        }
        catch (SecurityException e)
        {
            assertTrue(e.message.contains('Application is not locked by you'))
        }
        NCubeManager.setUserId(currUser)
    }

    @Test
    void testBranchPermissionsCubeCreatedOnNewBranch()
    {
        ApplicationID branchAppId = defaultSnapshotApp.asBranch('newBranch')
        NCubeManager.copyBranch(branchAppId.asHead(), branchAppId)
        NCube branchPermCube = NCubeManager.loadCube(branchAppId.asVersion('0.0.0'), NCubeManager.SYS_BRANCH_PERMISSIONS)
        Axis userAxis = branchPermCube.getAxis(NCubeManager.AXIS_USER)
        Axis resourceAxis = branchPermCube.getAxis(NCubeManager.AXIS_RESOURCE)

        assertFalse(branchPermCube.getDefaultCellValue())
        assertEquals(2, userAxis.columns.size())
        assertEquals(2, resourceAxis.columns.size())
        assertTrue(branchPermCube.getCell([(NCubeManager.AXIS_USER):NCubeManager.userId, (NCubeManager.AXIS_RESOURCE):NCubeManager.SYS_BRANCH_PERMISSIONS]))
        assertTrue(branchPermCube.getCell([(NCubeManager.AXIS_USER):NCubeManager.userId, (NCubeManager.AXIS_RESOURCE):null]))
        assertFalse(branchPermCube.getCell([(NCubeManager.AXIS_USER):null, (NCubeManager.AXIS_RESOURCE):NCubeManager.SYS_BRANCH_PERMISSIONS]))
        assertFalse(branchPermCube.getCell([(NCubeManager.AXIS_USER):null, (NCubeManager.AXIS_RESOURCE):null]))
    }

    @Test
    void testBranchPermissionsFail()
    {
        String testAxisName = 'testAxis'
        String origUser = NCubeManager.userId
        ApplicationID appId = defaultSnapshotApp.asBranch(origUser)

        //create new branch and make sure of permissions
        NCubeManager.copyBranch(appId.asHead(), appId)
        NCube branchPermCube = NCubeManager.loadCube(defaultBootApp.asBranch(origUser), NCubeManager.SYS_BRANCH_PERMISSIONS)
        Axis userAxis = branchPermCube.getAxis(NCubeManager.AXIS_USER)
        List<Column> columnList = userAxis.getColumnsWithoutDefault()
        assertEquals(1, columnList.size())
        assertEquals(origUser, columnList.get(0).value)

        //check app permissions cubes
        assertNotNull(NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_PERMISSIONS))
        assertNotNull(NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_USERGROUPS))
        assertNotNull(NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_LOCK))

        //new cube on branch from good user
        NCube testCube = new NCube('test')
        testCube.setApplicationID(appId)
        testCube.addAxis(new Axis(testAxisName, AxisType.DISCRETE, AxisValueType.STRING, true))
        NCubeManager.updateCube(appId, testCube, true)

        //try to create a cube as a different user in that branch
        try {
            NCubeManager.setUserId('otherUser')
            testCube.setCell('testval', [(testAxisName):null])
            NCubeManager.updateCube(appId, testCube, true)
            fail()
        } catch (SecurityException e) {
            assertTrue(e.message.contains('not performed'))
            assertTrue(e.message.contains(Action.UPDATE.name()))
            assertTrue(e.message.contains(testCube.name))
        } finally {
            NCubeManager.setUserId(origUser)
        }
    }

    @Test
    void testIsAdminPass()
    {
        //without user cube present
        assertTrue(NCubeManager.isAdmin(defaultSnapshotApp))

        assertNotNull(NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_USERGROUPS))
        assertTrue(NCubeManager.isAdmin(defaultSnapshotApp))
    }

    @Test
    void testIsAdminFail()
    {
        assertNotNull(NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_USERGROUPS))
        NCubeManager.setUserId('bad')
        assert !NCubeManager.isAdmin(defaultSnapshotApp)
    }

    @Test
    void testAppPermissionsFail()
    {
        String origUser = NCubeManager.userId
        String otherUser = 'otherUser'
        String testAxisName = 'testAxis'
        ApplicationID branchBootApp = defaultBootApp.asBranch(ApplicationID.TEST_BRANCH)

        //check app permissions cubes
        assertNotNull(NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_PERMISSIONS))
        assertNotNull(NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_USERGROUPS))
        assertNotNull(NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_LOCK))

        //set otheruser as having branch permissions
        NCube branchPermCube = NCubeManager.loadCube(branchBootApp, NCubeManager.SYS_BRANCH_PERMISSIONS)
        branchPermCube.getAxis(NCubeManager.AXIS_USER).addColumn(otherUser)
        branchPermCube.setCell(true, [(NCubeManager.AXIS_USER):otherUser, (NCubeManager.AXIS_RESOURCE):null])
        NCubeManager.updateCube(branchBootApp, branchPermCube, true)

        //set otheruser as no app permissions
        NCube userCube = NCubeManager.loadCube(defaultBootApp, NCubeManager.SYS_USERGROUPS)
        userCube.getAxis(NCubeManager.AXIS_USER).addColumn(otherUser)
        userCube.setCell(true, [(NCubeManager.AXIS_USER):otherUser, (NCubeManager.AXIS_ROLE):NCubeManager.ROLE_READONLY])
        NCubeManager.persister.updateCube(defaultBootApp, userCube, NCubeManager.userId)
        assertFalse(userCube.getCell([(NCubeManager.AXIS_USER):otherUser, (NCubeManager.AXIS_ROLE):NCubeManager.ROLE_USER]))
        assertTrue(userCube.getCell([(NCubeManager.AXIS_USER):otherUser, (NCubeManager.AXIS_ROLE):NCubeManager.ROLE_READONLY]))

        NCube testCube = new NCube('test')
        testCube.setApplicationID(defaultSnapshotApp)
        testCube.addAxis(new Axis(testAxisName, AxisType.DISCRETE, AxisValueType.STRING, true))
        NCubeManager.updateCube(defaultSnapshotApp, testCube, true)

        //try to update a cube from bad user
        try
        {
            NCubeManager.setUserId(otherUser)
            testCube.setCell('testval', [(testAxisName):null])
            NCubeManager.updateCube(defaultSnapshotApp, testCube, true)
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
            NCubeManager.setUserId(origUser)
        }
    }

    @Test
    void testCannotRelease000Version()
    {
        try
        {
            NCubeManager.releaseCubes(defaultBootApp, '0.0.1')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains(NCubeManager.ERROR_CANNOT_RELEASE_000))
        }

        try
        {
            NCubeManager.releaseCubes(defaultSnapshotApp, '0.0.0')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertTrue(e.message.contains(NCubeManager.ERROR_CANNOT_RELEASE_TO_000))
        }
    }

    private static void loadTestClassPathCubes()
    {
        NCube cube = NCubeManager.getNCubeFromResource(ApplicationID.testAppId, 'sys.versions.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        cube = NCubeManager.getNCubeFromResource('sys.classpath.local.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        cube = NCubeManager.getNCubeFromResource('sys.classpath.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
        cube = NCubeManager.getNCubeFromResource('sys.classpath.base.json')
        NCubeManager.updateCube(defaultSnapshotApp, cube, true)
    }

    private static void loadTestBootstrapCubes()
    {
        ApplicationID appId = defaultSnapshotApp.createNewSnapshotId('0.0.0')

        NCube cube = NCubeManager.getNCubeFromResource(appId, 'sys.bootstrap.json')
        NCubeManager.updateCube(appId, cube, true)
        cube = NCubeManager.getNCubeFromResource('sys.version.json')
        NCubeManager.updateCube(appId, cube, true)
        cube = NCubeManager.getNCubeFromResource('sys.status.json')
        NCubeManager.updateCube(appId, cube, true)
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
        Map options = new HashMap();
        options.put(NCubeManager.SEARCH_DELETED_RECORDS_ONLY, true);

        return NCubeManager.search(appId, pattern, null, options);
    }
}
