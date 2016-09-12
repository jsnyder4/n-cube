package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.BranchMergeException
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.fail
/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class TestDelta
{
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

    @Test
    void testDeltaApis()
    {
        Delta x = new Delta(Delta.Location.AXIS, Delta.Type.ADD, "foo")
        assert x.location == Delta.Location.AXIS
        assert x.type == Delta.Type.ADD
        assert x.description == 'foo'
        assert x.toString() == 'foo'
    }

    @Test
    void testBadInputToChangeSetComparator()
    {
        assert !DeltaProcessor.areDeltaSetsCompatible(null, [:], false)
        assert !DeltaProcessor.areDeltaSetsCompatible([:], null, false)
    }

    @Test
    void testDiscreteMergeRemoveCol()
    {
        // Delete a column from cube 1, which not only deletes the column, but also the cells pointing to it.
        NCube cube1 = NCubeBuilder.get5DTestCube()
        NCube cube2 = NCubeBuilder.get5DTestCube()
        NCube orig = NCubeBuilder.get5DTestCube()

        assert '4' == getCellIgnoreRule(cube1, [age:16, salary:65000, log:100, state:'OH', rule:'process'] as Map)
        assert '5' == getCellIgnoreRule(cube1, [age:16, salary:65000, log:100, state:'GA', rule:'process'] as Map)
        assert '6' == getCellIgnoreRule(cube1, [age:16, salary:65000, log:100, state:'TX', rule:'process'] as Map)

        assert '46' == getCellIgnoreRule(cube1, [age:20, salary:85000, log:1000, state:'OH', rule:'process'] as Map)
        assert '47' == getCellIgnoreRule(cube1, [age:20, salary:85000, log:1000, state:'GA', rule:'process'] as Map)
        assert '48' == getCellIgnoreRule(cube1, [age:20, salary:85000, log:1000, state:'TX', rule:'process'] as Map)

        // Verify deletion occurred
        int count = cube1.cellMap.size()
        assert count == 48
        cube1.deleteColumn('state', 'GA')
        assert cube1.cellMap.size() == 32
        assert cube2.cellMap.size() == 48

        // Compute delta between copy of original cube and the cube with deleted column.
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.cellMap.size() == 32

        count = cube2.getAxis("state").getColumns().size()
        assert cube2.getAxis('state').findColumn('OH') != null
        assert cube2.getAxis('state').findColumn('TX') != null
        assert cube2.getAxis('state').findColumn('GA') == null
        assert count == 2

        assert '4' == getCellIgnoreRule(cube2, [age:16, salary:65000, log:100, state:'OH', rule:'process'] as Map)
        assert '6' == getCellIgnoreRule(cube2, [age:16, salary:65000, log:100, state:'TX', rule:'process'] as Map)
        try
        {
            getCellIgnoreRule(cube2, [age:16, salary:65000, log:100, state:'GA', rule:'process'] as Map)
            fail()
        }
        catch (CoordinateNotFoundException ignored)
        { }

        assert '46' == getCellIgnoreRule(cube2, [age:20, salary:85000, log:1000, state:'OH', rule:'process'] as Map)
        assert '48' == getCellIgnoreRule(cube2, [age:20, salary:85000, log:1000, state:'TX', rule:'process'] as Map)
        try
        {
            assert '47' == getCellIgnoreRule(cube2, [age:20, salary:85000, log:1000, state:'GA', rule:'process'] as Map)
        }
        catch (CoordinateNotFoundException ignored)
        { }
    }

    @Test
    void testDiscreteChangeSameColumnDifferently()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.getDiscrete1D()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.getDiscrete1DAlt()
        assert cube1 == cube2

        Axis state1 = cube1.getAxis('state')
        Axis state2 = cube2.getAxis('state')

        Column oh1 = state1.findColumn("OH")
        Column oh2 = state2.findColumn("OH")
        cube1.updateColumn(oh1.id, 'GA')
        cube2.updateColumn(oh2.id, 'AZ')

        assert cube1.getNumCells() == 2
        assert cube2.getNumCells() == 2

        assert cube1 != cube2

        NCube<String> orig1 = (NCube<String>) NCubeBuilder.getDiscrete1D()
        NCube<String> orig2 = (NCube<String>) NCubeBuilder.getDiscrete1DAlt()

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig1, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig2, cube2)  // Other guy made no changes

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)

        state2 = cube2.getAxis('state')
        assert cube2.getNumCells() == 2
        assert state2.getColumns().size() == 3
        assert state2.findColumn('AZ')
        assert state2.findColumn('TX')
        assert state2.findColumn('GA')
    }

    @Test
    void testDiscreteRemoveSameColumnDifferently()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.getDiscrete1D()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.getDiscrete1DAlt()
        assert cube1 == cube2

        Axis state1 = cube1.getAxis('state')
        Axis state2 = cube2.getAxis('state')

        Column oh1 = state1.findColumn("OH")
        cube1.updateColumn(oh1.id, 'GA')
        cube2.deleteColumn('state', 'OH')

        assert cube1 != cube2

        NCube<String> orig1 = (NCube<String>) NCubeBuilder.getDiscrete1D()
        NCube<String> orig2 = (NCube<String>) NCubeBuilder.getDiscrete1DAlt()

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig1, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig2, cube2)  // Other guy made no changes

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)

        state2 = cube2.getAxis('state')
        assert state2.getColumns().size() == 2
        assert state2.findColumn('TX')
        assert state2.findColumn('GA')
    }

    @Test
    void testDiscreteMergeAddCol()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        // Verify addition occurred
        int count = cube1.cellMap.size()
        assert count == 48
        cube1.addColumn('state', 'AL')

        assert cube1.cellMap.size() == 48
        Map coord = [age: 16, salary: 60000, log: 1000, state: 'AL', rule: 'process'] as Map
        cube1.setCell('foo', coord)
        assert cube1.cellMap.size() == 49
        assert 'foo' == getCellIgnoreRule(cube1, coord)

        // Compute delta between copy of original cube and the cube with deleted column.
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)  // Other guy made no changes

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.cellMap.size() == 49

        assert 'foo' == getCellIgnoreRule(cube2, [age: 16, salary: 60000, log: 1000, state: 'AL', rule:'process'] as Map)
    }

    @Test
    void testRuleMergeRemoveColumnWithName()
    {
        // Delete a column from cube 1, which not only deletes the column, but also the cells pointing to it.
        NCube cube1 = NCubeBuilder.get5DTestCube()
        NCube cube2 = NCubeBuilder.get5DTestCube()
        NCube orig = NCubeBuilder.get5DTestCube()

        assert '4' == getCellIgnoreRule(cube1, [age:16, salary:65000, log:100, state:'OH', rule:'process'] as Map)
        assert '5' == getCellIgnoreRule(cube1, [age:16, salary:65000, log:100, state:'GA', rule:'process'] as Map)
        assert '6' == getCellIgnoreRule(cube1, [age:16, salary:65000, log:100, state:'TX', rule:'process'] as Map)

        // Verify deletion occurred
        int count = cube1.cellMap.size()
        assert count == 48
        cube1.deleteColumn('rule', 'process')
        assert cube1.cellMap.size() == 24
        assert cube2.cellMap.size() == 48

        // Compute delta between copy of original cube and the cube with deleted column.
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)  // Other guy made no changes

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.cellMap.size() == 24

        count = cube2.getAxis("rule").getColumns().size()
        assert count == 1
        assert cube2.getAxis('rule').findColumn('init') != null
        assert cube2.getAxis('rule').findColumn('process') == null

        try
        {
            getCellIgnoreRule(cube1, [age:16, salary:65000, log:100, state:'OH', rule:'process'] as Map)
            fail()
        }
        catch (CoordinateNotFoundException ignored)
        { }

        assert '1' == getCellIgnoreRule(cube2, [age:16, salary:65000, log:100, state:'OH', rule:'init'] as Map)
        assert '2' == getCellIgnoreRule(cube2, [age:16, salary:65000, log:100, state:'GA', rule:'init'] as Map)
        assert '3' == getCellIgnoreRule(cube2, [age:16, salary:65000, log:100, state:'TX', rule:'init'] as Map)
    }

    @Test
    void testRuleMergeAddColumnWithName()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        assert cube1.cellMap.size() == 48
        cube1.addColumn('rule', 'false', 'jones')
        Map coord = [age:16, salary:65000, log:100, state:'OH', rule:'jones'] as Map
        cube1.setCell('alpha', coord)
        assert cube1.cellMap.size() == 49

        // Compute delta between copy of original cube and the cube with deleted column.
        // Apply this delta to the 2nd cube to force the same changes on it.
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)  // Other guy made no changes

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        assert cube2.cellMap.size() == 48
        try
        {
            cube2.getCell(coord)
            fail()
        }
        catch (CoordinateNotFoundException e)
        { }
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.cellMap.size() == 49
        assert 'alpha' == getCellIgnoreRule(cube2, coord)
    }

    @Test
    void testRuleMergeAddColumnWithoutName()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        int numCols = cube1.getAxis('rule').getColumns().size()
        cube1.addColumn('rule', 'true')

        // Compute delta between copy of original cube and the cube with deleted column.
        // Apply this delta to the 2nd cube to force the same changes on it.
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)  // Other guy made no changes

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis axis = cube2.getAxis('rule')
        assert axis.getColumns().size() == numCols + 1
    }

    @Test
    void testDiscreteMergeAddAddUniqueColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        // Verify addition occurred
        int count = cube1.cellMap.size()
        assert count == 48
        cube1.addColumn('state', 'AL')

        Map coord = [age: 16, salary: 60000, log: 1000, state: 'AL', rule: 'process'] as Map
        cube1.setCell('foo', coord)
        assert cube1.cellMap.size() == 49
        assert 'foo' == getCellIgnoreRule(cube1, coord)

        count = cube2.cellMap.size()
        assert count == 48
        cube2.addColumn('state', 'WY')

        coord = [age: 16, salary: 60000, log: 1000, state: 'WY', rule: 'process'] as Map
        cube2.setCell('bar', coord)
        assert cube2.cellMap.size() == 49
        assert 'bar' == getCellIgnoreRule(cube2, coord)

        // Compute delta between copy of original cube
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube1)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.cellMap.size() == 50

        assert 'foo' == getCellIgnoreRule(cube2, [age: 16, salary: 60000, log: 1000, state: 'AL', rule: 'process'] as Map)
        assert 'bar' == getCellIgnoreRule(cube2, [age: 16, salary: 60000, log: 1000, state: 'WY', rule: 'process'] as Map)
    }

    @Test
    void testDiscreteMergeAddAddSameColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        // Verify addition occurred
        int count = cube1.cellMap.size()
        assert count == 48
        cube1.addColumn('state', 'AL')

        Map coord = [age: 16, salary: 60000, log: 1000, state: 'AL', rule: 'process'] as Map
        cube1.setCell('foo', coord)
        assert cube1.cellMap.size() == 49
        assert 'foo' == getCellIgnoreRule(cube1, coord)

        count = cube2.cellMap.size()
        assert count == 48
        cube2.addColumn('state', 'AL')

        Map coord2 = [age: 16, salary: 60000, log: 1000, state: 'AL', rule: 'init'] as Map
        cube2.setCell('bar', coord2)
        assert cube2.cellMap.size() == 49
        assert 'bar' == getCellIgnoreRule(cube2, coord2)

        // Compute delta between copy of original cube
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube1)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.cellMap.size() == 50

        assert 'foo' == getCellIgnoreRule(cube2, coord)
        assert 'bar' == getCellIgnoreRule(cube2, coord2)
    }

    @Test
    void testDiscreteMergeRemoveRemoveUniqueColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        assert cube1.cellMap.size() == 48
        cube1.deleteColumn('state', 'OH')
        assert cube1.cellMap.size() == 32

        assert cube2.cellMap.size() == 48
        cube2.deleteColumn('state', 'GA')
        assert cube2.cellMap.size() == 32

        // Compute delta between copy of original cube
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis state = cube2.getAxis('state')
        assertNotNull state.findColumn('TX')
        assertNull state.findColumn('OH')
        assertNull state.findColumn('GA')
        assert cube2.cellMap.size() == 16
    }

    @Test
    void testDiscreteMergeRemoveRemoveSameColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        assert cube1.cellMap.size() == 48
        cube1.deleteColumn('state', 'OH')
        assert cube1.cellMap.size() == 32

        assert cube2.cellMap.size() == 48
        cube2.deleteColumn('state', 'OH')
        assert cube2.cellMap.size() == 32

        // Compute delta between copy of original cube
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis state = cube2.getAxis('state');
        assertNotNull state.findColumn('TX')
        assertNotNull state.findColumn('GA')
        assertNull state.findColumn('OH')
        assert cube2.cellMap.size() == 32
    }

    @Test
    void testRuleMergeAddAddUniqueColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        assert cube1.cellMap.size() == 48
        assert cube2.cellMap.size() == 48
        cube1.addColumn('rule', 'true', 'Summary')
        Map coord = [age: 16, salary: 60000, log: 1000, state: 'OH', rule: 'Summary'] as Map
        cube1.setCell('99', coord)

        cube2.addColumn('rule', '1 < 2', 'Finalize')
        coord.rule = 'Finalize'
        cube2.setCell('88', coord)

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 4
        coord = [age: 16, salary: 60000, log: 1000, state: 'OH', rule: 'Summary'] as Map
        assert '99' == getCellIgnoreRule(cube2, coord)
        coord.rule = 'Finalize'
        assert '88' == getCellIgnoreRule(cube2, coord)
        assert cube2.getNumCells() == 50
    }

    @Test
    void testRuleMergeAddAddSameColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        assert cube1.cellMap.size() == 48
        assert cube2.cellMap.size() == 48
        cube1.addColumn('rule', 'true', 'Summary')
        Map coord = [age: 16, salary: 60000, log: 1000, state: 'OH', rule: 'Summary'] as Map
        cube1.setCell('99', coord)

        cube2.addColumn('rule', '1 < 2', 'Finalize')
        coord.rule = 'Finalize'
        cube2.setCell('88', coord)

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 4
        coord = [age: 16, salary: 60000, log: 1000, state: 'OH', rule: 'Summary'] as Map
        assert '99' == getCellIgnoreRule(cube2, coord)
        coord.rule = 'Finalize'
        assert '88' == getCellIgnoreRule(cube2, coord)
        assert cube2.getNumCells() == 50
    }

    @Test
    void testRuleMergeAddAddSameColumnConflict()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        assert cube1.cellMap.size() == 48
        assert cube2.cellMap.size() == 48
        cube1.addColumn('rule', 'true', 'Summary')
        Map coord = [age: 16, salary: 60000, log: 1000, state: 'OH', rule: 'Summary'] as Map
        cube1.setCell('99', coord)

        cube2.addColumn('rule', '1 < 2', 'Summary')
        coord.rule = 'Summary'
        cube2.setCell('99', coord)

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert !compatibleChange
    }

    @Test
    void testRuleMergeRemoveRemoveUniqueColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        cube1.deleteColumn('rule', 'init')
        cube2.deleteColumn('rule', 'process')

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 0
        assert cube2.numCells == 0
    }

    @Test
    void testRuleMergeRemoveRemoveSameColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        cube1.deleteColumn('rule', 'init')
        cube2.deleteColumn('rule', 'init')

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 1
        assert rule.findColumn('process') != null
        assert cube2.numCells == 24
    }

    @Test
    void testRangeAdd()
    {   // Change 2 axes
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        cube1.addColumn('age', new Range(30, 40))
        Map coord = [age: 35, salary: 60000, log: 1000, state: 'OH', rule: 'init'] as Map
        cube1.setCell('love', coord)
        assert 'love' == getCellIgnoreRule(cube1, coord)
        assert cube1.getNumCells() == 49

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis age = cube2.getAxis('age')
        assert age.size() == 3
        assert 'love' == getCellIgnoreRule(cube2, coord)
        assert cube2.getNumCells() == 49
    }

    @Test
    void testRuleRangeAddBoth()
    {   // Change 2 axes
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        cube1.addColumn('age', new Range(30, 40))
        Map coord = [age: 35, salary: 60000, log: 1000, state: 'OH', rule: 'init'] as Map
        cube1.setCell('love', coord)
        assert 'love' == getCellIgnoreRule(cube1, coord)
        assert cube1.getNumCells() == 49
        cube1.addColumn('rule', 'true', 'summary')
        Map coord2 = [age: 35, salary: 60000, log: 1000, state: 'OH', rule: 'summary'] as Map
        cube1.setCell('fear', coord2)
        assert cube1.getNumCells() == 50

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis age = cube2.getAxis('age')
        assert age.size() == 3
        assert 'love' == getCellIgnoreRule(cube2, coord)
        assert cube2.getNumCells() == 50

        assert 'fear' == getCellIgnoreRule(cube2, coord2)
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 3
    }

    @Test
    void testRuleRangeRemoveColumnBoth()
    {   // Change 2 axes
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        cube1.deleteColumn('age', 20)
        cube1.deleteColumn('rule', 'init')

        assert 12 == cube1.numCells
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 1
        assert rule.findColumn('process') != null
        assert rule.findColumn('init') == null
        Axis age = cube2.getAxis('age')
        assert age.size() == 1
        assert age.findColumn(16) != null
        assert age.findColumn(20) == null
        assert 12 == cube2.numCells
    }

    @Test
    void testRuleMergeRemoveColumnWithNoName()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        Column column = cube1.getAxis('rule').getColumns()[0]
        cube1.deleteColumn('rule', column.id)

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 1
        assert rule.findColumn('process') != null
        assert rule.findColumn('init') == null
    }

    @Test
    void testDeleteColumnWithNoNameFromRuleAxis()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.getTestRuleCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.getTestRuleCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.getTestRuleCube()

        assert cube1.numCells == 3
        Column col1 = cube1.getAxis('rule').columns[0]
        cube1.deleteColumn('rule', col1.id)
        assert cube1.numCells == 2

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        assert cube2.numCells == 3
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 3

        DeltaProcessor.mergeDeltaSet(cube2, delta1)

        assert cube2.numCells == 2
        assert rule.size() == 2
    }

    @Test
    void testChangeColumnWithNoNameOnRuleAxis()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.getTestRuleCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.getTestRuleCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.getTestRuleCube()

        assert cube1.numCells == 3
        Column col1 = cube1.getAxis('rule').columns[0]
        cube1.updateColumn(col1.id, '1 < 2')

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        Axis rule = cube2.getAxis('rule')
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.numCells == 3

        Column col2 = rule.getColumns()[0]
        assert '1 < 2' == col2.getValue().toString()
    }

    @Test
    void testChangeColumnWithNameOnRuleAxis()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get5DTestCube()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get5DTestCube()

        assert cube1.numCells == 48
        Map coord = [age: 17, salary: 60000, log: 1000, state: 'OH', rule: 'init']
        assert '7' == getCellIgnoreRule(cube1, coord)
        Axis rule = (Axis)cube1['rule']
        Column col = rule.findColumn('init')
        cube1.updateColumn(col.id, '34 < 40')
        assert '7' == getCellIgnoreRule(cube1, coord)

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        rule = (Axis)cube2['rule']
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.numCells == 48
        assert '7' == getCellIgnoreRule(cube2, coord)
        Column col2 = rule.findColumn('init')
        assert '34 < 40' == col2.toString()
    }

    @Test
    void testDiscreteAddDefaultColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.getDiscrete1D()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.getDiscrete1D()
        NCube<String> orig = (NCube<String>) NCubeBuilder.getDiscrete1D()

        Axis state = (Axis) cube1['state']
        assert state.size() == 2
        cube1.addColumn('state', null)
        assert state.size() == 3
        cube1.setCell('3', [:])

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        state = (Axis) cube2['state']
        assert state.size() == 2
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert state.size() == 3
        assert '3' == cube2.getCell([:])
    }

    @Test
    void testDiscreteRemoveDefaultColumn()
    {
        NCube<String> orig = (NCube<String>) NCubeBuilder.getDiscrete1D()
        orig.addColumn('state', null)
        orig.setCell('3', [:])  // associate '3' to default col

        NCube<String> cube1 = orig.duplicate('cube')
        NCube<String> cube2 = orig.duplicate('cube')

        assert cube1.numCells == 3
        assert '3' == cube1.getCell([:])
        cube1.deleteColumn('state', null)
        try
        {
            cube1.getCell([:])
            fail()
        }
        catch (IllegalArgumentException e)
        {
            e.getMessage().contains('required scope')
        }
        assert cube1.numCells == 2

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange

        assert '3' == cube2.getCell([:])
        assert cube2.numCells == 3
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        try
        {
            cube2.getCell([:])
            fail()
        }
        catch (IllegalArgumentException e)
        {
            e.getMessage().contains('required scope')
        }
        assert cube2.numCells == 2
    }

    @Test
    void testRuleAddDefaultColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.getRule1D()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.getRule1D()
        NCube<String> orig = (NCube<String>) NCubeBuilder.getRule1D()

        Axis rules = (Axis) cube1['rule']
        assert rules.size() == 2
        cube1.addColumn('rule', null, 'summary')
        assert rules.size() == 3
        cube1.setCell('3', [:])

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        rules = (Axis) cube2['rule']
        assert rules.size() == 2
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert rules.size() == 3
        assert '3' == getCellIgnoreRule(cube2, [:])
    }

    @Test
    void testRuleRemoveDefaultColumn()
    {
        NCube<String> orig = (NCube<String>) NCubeBuilder.getRule1D()
        orig.addColumn('rule', null)
        orig.setCell('3', [:])  // associate '3' to default col

        NCube<String> cube1 = orig.duplicate('cube')
        NCube<String> cube2 = orig.duplicate('cube')

        assert cube1.numCells == 3
        assert '3' == getCellIgnoreRule(cube1, [:])
        cube1.deleteColumn('rule', null)
        try
        {
            getCellIgnoreRule(cube1, [:])
            fail()
        }
        catch (CoordinateNotFoundException ignore)
        { }
        assert cube1.numCells == 2

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange

        assert '3' == getCellIgnoreRule(cube2, [:])
        assert cube2.numCells == 3
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        try
        {
            getCellIgnoreRule(cube2, [:])
            fail()
        }
        catch (CoordinateNotFoundException ignore)
        {
        }
        assert cube2.numCells == 2
    }

    @Test
    void testUpdateRemoveRuleColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.getRule1D()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.getRule1D()
        NCube<String> orig = (NCube<String>) NCubeBuilder.getRule1D()

        Axis rule = (Axis) cube1['rule']
        Column process = rule.findColumn('process')
        cube1.updateColumn(process.id, '1 < 2')

        rule = (Axis) cube2['rule']
        rule.deleteColumn('process')

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert !compatibleChange
    }

    @Test
    void testUpdateRemoveRuleDefaultColumn()
    {
        NCube<String> orig = (NCube<String>) NCubeBuilder.getRule1D()
        orig.addColumn('rule', null)
        orig.setCell('3', [:])  // associate '3' to default col

        NCube<String> cube1 = orig.duplicate('cube')
        NCube<String> cube2 = orig.duplicate('cube')

        assert cube1.numCells == 3
        assert '3' == getCellIgnoreRule(cube1, [:])

        Axis rule = (Axis) cube1['rule']
        Column process = rule.findColumn(null)
        cube1.updateColumn(process.id, '1 < 2')

        rule = (Axis) cube2['rule']
        rule.deleteColumn(null)

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert !compatibleChange
    }

    @Test
    void testIncompatibleAxisType()
    {
        Axis axis1 = new Axis("state", AxisType.DISCRETE, AxisValueType.STRING, true)
        Axis axis2 = new Axis("state", AxisType.RANGE, AxisValueType.STRING, true)
        NCube ncube1 = new NCube("one")
        ncube1.addAxis(axis1)
        NCube ncube2 = new NCube("two")
        ncube2.addAxis(axis2)
        assert null == DeltaProcessor.getDelta(ncube1, ncube2)
    }

    @Test
    void testIncompatibleAxisValueType()
    {
        Axis axis1 = new Axis("state", AxisType.DISCRETE, AxisValueType.STRING, true)
        Axis axis2 = new Axis("state", AxisType.DISCRETE, AxisValueType.LONG, true)
        NCube ncube1 = new NCube("one")
        ncube1.addAxis(axis1)
        NCube ncube2 = new NCube("two")
        ncube2.addAxis(axis2)
        assert null == DeltaProcessor.getDelta(ncube1, ncube2)
    }

    @Test
    void testColumnDeltaToString()
    {
        ColumnDelta delta = new ColumnDelta(AxisType.DISCRETE, new Column('OH', 1), 'OH', DeltaProcessor.DELTA_COLUMN_ADD)
        assert delta.toString().contains('DISCRETE')
        assert delta.toString().contains('OH')
        assert delta.toString().contains('add')
    }

    @Test
    void testMergeSortedToNonSorted()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.get3StatesNotSorted()
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.get3StatesNotSorted()
        NCube<String> orig = (NCube<String>) NCubeBuilder.get3StatesNotSorted()

        Axis state = cube1['state'] as Axis
        assert state.columnOrder == Axis.DISPLAY
        List cols = state.getColumns()
        assert cols[0].value == 'OH'
        assert cols[1].value == 'GA'
        assert cols[2].value == 'TX'
        state.columnOrder = Axis.SORTED

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2, false)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)

        state = cube2['state'] as Axis
        assert state.columnOrder == Axis.SORTED      // sort indicator updated
        cols = state.getColumns()
        assert cols[0].value == 'GA'                 // actual sort order honored
        assert cols[1].value == 'OH'
        assert cols[2].value == 'TX'
    }

    @Test
    void testMergeReferenceAxisHigherVersion()
    {
        setupLibrary()
        setupLibraryReference()
        ApplicationID appIdKpartlow = setupBranch('kpartlow', '1.0.1')
        ApplicationID appIdjdereg = setupBranch('jdereg', '1.0.2')

        // Commit a change in 'kpartlow' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.1
        List<NCubeInfoDto> list = NCubeManager.getBranchChangesForHead(appIdKpartlow)
        NCubeManager.commitBranch(appIdKpartlow, list as Object[])

        // Commit a change in 'jdereg' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.2
        list = NCubeManager.getBranchChangesForHead(appIdjdereg)
        list = NCubeManager.commitBranch(appIdjdereg, list as Object[])
        NCubeInfoDto dto = list[0]
        assert dto.notes.contains('merged')

        NCube referrer = NCubeManager.loadCube(appIdKpartlow.asHead(), 'States')
        Axis axis = referrer['state'] as Axis
        assert axis.metaProperties.referenceVersion == '1.0.2'
    }

    @Test
    void testMergeReferenceAxisLowerVersion()
    {
        setupLibrary()
        setupLibraryReference()
        ApplicationID appIdKpartlow = setupBranch('kpartlow', '1.0.1')
        ApplicationID appIdjdereg = setupBranch('jdereg', '1.0.2')

        // Commit a change in 'jdereg' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.2
        List<NCubeInfoDto> list = NCubeManager.getBranchChangesForHead(appIdjdereg)
        NCubeManager.commitBranch(appIdjdereg, list as Object[])

        // Commit a change in 'kpartlow' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.1
        list = NCubeManager.getBranchChangesForHead(appIdKpartlow)
        try
        {   // Should fail because kpartlow branch is behind and needs to be updated (merged) first
            NCubeManager.commitBranch(appIdKpartlow, list as Object[])
            fail()
        }
        catch (BranchMergeException ignored)
        { }

        // Update branch 1.0.1 -> 1.0.2
        Map map = NCubeManager.updateBranch(appIdKpartlow)
        assert (map.updates as List).isEmpty()
        assert (map.conflicts as Map).isEmpty()
        assert (map.merges as List).size() == 1

        // TODO: Write many more reference axis tests
        // auto-merge reference axis (with diff transform)
        // conflict-merge reference axis (one of the components of the reference axis changed - fail)
        // conflict-merge reference axis (reference to non-reference)
        // conflict-merge reference axis (non-reference to reference)
        // Merge new cube in one branch to another branch, no cube in HEAD
    }

    @Test
    void testMergeBreakReferenceAxis()
    {
        setupLibrary()
        setupLibraryReference()
        ApplicationID appIdKpartlow = setupBranch('kpartlow', '1.0.1')
        ApplicationID appIdjdereg = setupBranch('jdereg', '1.0.2')

        // Commit a change in 'kpartlow' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.1
        List<NCubeInfoDto> list = NCubeManager.getBranchChangesForHead(appIdKpartlow)
        NCubeManager.commitBranch(appIdKpartlow, list as Object[])

        NCube statesJdereg = NCubeManager.loadCube(appIdjdereg, 'States')
        statesJdereg.breakAxisReference('state')
        NCubeManager.updateCube(appIdjdereg, statesJdereg, true)

        list = NCubeManager.getBranchChangesForHead(appIdjdereg)
        try
        {
            NCubeManager.commitBranch(appIdjdereg, list as Object[])
            fail()
        }
        catch (BranchMergeException e)
        { }
    }

    @Test
    void testMergeBreakReferenceAxisFail()
    {
        setupLibrary()
        setupLibraryReference()
        ApplicationID appIdKpartlow = setupBranch('kpartlow', '1.0.1')
        ApplicationID appIdjdereg = setupBranch('jdereg', '1.0.2')

        // Commit a change in 'jdereg' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.2
        List<NCubeInfoDto> list = NCubeManager.getBranchChangesForHead(appIdjdereg)
        NCubeManager.commitBranch(appIdjdereg, list as Object[])

        NCube statesKpartlow = NCubeManager.loadCube(appIdKpartlow, 'States')
        statesKpartlow.breakAxisReference('state')
        NCubeManager.updateCube(appIdKpartlow, statesKpartlow, true)

        list = NCubeManager.getBranchChangesForHead(appIdKpartlow)
        try
        {
            NCubeManager.commitBranch(appIdKpartlow, list as Object[])
            fail()
        }
        catch (BranchMergeException ignored)
        { }
    }

    @Test
    void testDefaultCellDelta()
    {
        NCube ncube1 = NCubeBuilder.discrete1D
        ncube1.defaultCellValue = 10
        NCube ncube2 = ncube1.duplicate(ncube1.name)
        ncube2.defaultCellValue = 3.14
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(ncube2, ncube1)
        assert deltas.size() == 1
        Delta delta = deltas[0]
        delta.type == Delta.Type.UPDATE
        delta.location == Delta.Location.NCUBE
        String desc = delta.description.toLowerCase()
        assert desc.contains('default cell')
        assert desc.contains('changed from')
        assert desc.contains("'10' to '3.14'")
    }

    // TODO: Swap order of above test
    // TODO: Have jdereg break reference and merge to HEAD
    // TODO: Then, kpartlow should be able to updated from HEAD because it will have a super set of columns

    static void setupLibrary()
    {
        NCube<String> states4 = (NCube<String>) NCubeBuilder.get4StatesNotSorted()
        NCube<String> states3 = (NCube<String>) NCubeBuilder.get3StatesNotSorted()
        NCube<String> states2 = (NCube<String>) NCubeBuilder.getDiscrete1D()

        ApplicationID appId = ApplicationID.testAppId.asVersion('1.0.0')
        NCubeManager.updateCube(appId, states2, true)
        List<NCubeInfoDto> list = NCubeManager.getBranchChangesForHead(appId)
        NCubeManager.commitBranch(appId, list as Object[])
        NCubeManager.releaseCubes(appId, '1.0.1')

        appId = ApplicationID.testAppId.asVersion('1.0.1')
        NCubeManager.updateCube(appId, states3, true)
        list = NCubeManager.getBranchChangesForHead(appId)
        NCubeManager.commitBranch(appId, list as Object[])
        NCubeManager.releaseCubes(appId, '1.0.2')

        appId = ApplicationID.testAppId.asVersion('1.0.2')
        NCubeManager.updateCube(appId, states4, true)
        list = NCubeManager.getBranchChangesForHead(appId)
        NCubeManager.commitBranch(appId, list as Object[])
        NCubeManager.releaseCubes(appId, '1.0.3')
    }

    static void setupLibraryReference()
    {
        NCube<String> statesRef = NCubeBuilder.getStateReferrer()

        ApplicationID appId = ApplicationID.testAppId.asVersion('2.0.0')
        NCubeManager.updateCube(appId, statesRef, true)
        List<NCubeInfoDto> list = NCubeManager.getBranchChangesForHead(appId)
        NCubeManager.commitBranch(appId, list as Object[])

        list = NCubeManager.search(appId.asHead(), null, null, null)
        assert list.size() == 1
        statesRef = NCubeManager.getCube(appId.asHead(), 'states') as NCube
        Map input = [state:'OH']
        assert 1 == statesRef.getCell(input) as int
        input.state = 'TX'
        assert 2 == statesRef.getCell(input) as int
    }

    static ApplicationID setupBranch(String branch, String refVer)
    {
        NCube<String> states = (NCube<String>) NCubeBuilder.getStateReferrer()
        Axis state = states.getAxis('state')
        state.setMetaProperty(ReferenceAxisLoader.REF_VERSION, refVer)
        ApplicationID appId = ApplicationID.testAppId.asBranch(branch).asSnapshot().asVersion('2.0.0')
        NCubeManager.copyBranch(appId.asHead(), appId)
        NCubeManager.updateCube(appId, states, true)
        return appId
    }

    static def getCellIgnoreRule(NCube ncube, Map coord)
    {
        Set<Long> idCoord = ncube.getCoordinateKey(coord)
        return ncube.getCellById(idCoord, coord, [:])
    }
}
