package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.BranchMergeException
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.ncube.exception.InvalidCoordinateException
import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.DeltaProcessor.DELTA_AXES
import static com.cedarsoftware.ncube.DeltaProcessor.DELTA_AXIS_REF_CHANGE
import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static com.cedarsoftware.ncube.ReferenceAxisLoader.*
import static org.junit.Assert.*

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
class TestDelta extends NCubeCleanupBaseTest
{
    private static final ApplicationID BRANCH1 = ApplicationID.testAppId.asBranch('branch1')
    private static final ApplicationID BRANCH2 = ApplicationID.testAppId.asBranch('branch2')

    @Test
    void testDeltaApis()
    {
        Delta x = new Delta(Delta.Location.AXIS, Delta.Type.ADD, 'foo', null, null, null, null, null)
        assert x.location == Delta.Location.AXIS
        assert x.type == Delta.Type.ADD
        assert x.description == 'foo'
        assert x.toString() == 'foo'
    }

    @Test
    void testBadInputToChangeSetComparator()
    {
        assert !DeltaProcessor.areDeltaSetsCompatible(null, [:])
        assert !DeltaProcessor.areDeltaSetsCompatible([:], null)
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.cellMap.size() == 32

        count = cube2.getAxis("state").columns.size()
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
    void testDiscreteChangeSameColumnDifferentlyOld()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.discrete1D
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.discrete1DAlt
        assert cube1 == cube2

        Axis state1 = cube1.getAxis('state')
        Axis state2 = cube2.getAxis('state')

        Column oh1 = state1.findColumn("OH")
        Column oh2 = state2.findColumn("OH")
        cube1.updateColumn(oh1.id, 'GA')
        cube2.updateColumn(oh2.id, 'AZ')

        assert cube1.numCells == 2
        assert cube2.numCells == 2

        assert cube1 != cube2

        NCube<String> orig1 = (NCube<String>) NCubeBuilder.discrete1D
        NCube<String> orig2 = (NCube<String>) NCubeBuilder.discrete1DAlt

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig1, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig2, cube2)

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert !compatibleChange
    }

    @Test
    void testDiscreteChangeSameColumnDifferently()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.discrete1D
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.discrete1DAlt
        assert cube1 == cube2

        Axis state1 = cube1.getAxis('state')
        Axis state2 = cube2.getAxis('state')

        Column oh1 = state1.findColumn("OH")
        Column oh2 = state2.findColumn("OH")
        cube1.updateColumn(oh1.id, 'GA')
        cube2.updateColumn(oh2.id, 'AZ')

        assert cube1.numCells == 2
        assert cube2.numCells == 2

        assert cube1 != cube2

        NCube<String> orig1 = (NCube<String>) NCubeBuilder.discrete1D
        NCube<String> orig2 = (NCube<String>) NCubeBuilder.discrete1DAlt

        List<Delta> delta1 = DeltaProcessor.getDeltaDescription(cube1, orig1)
        List<Delta> delta2 = DeltaProcessor.getDeltaDescription(cube2, orig2)
//        List<Delta> delta3 = DeltaProcessor.getDeltaDescription(cube2, cube1)

        assert delta1.size() == delta2.size()
        //TODO assert delta sets are incompatible
    }

    @Test
    void testDiscreteRemoveSameColumnDifferently()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.discrete1D
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.discrete1DAlt
        assert cube1 == cube2

        Axis state1 = cube1.getAxis('state')
        Column oh1 = state1.findColumn("OH")
        cube1.updateColumn(oh1.id, 'GA')
        cube2.deleteColumn('state', 'OH')

        assert cube1 != cube2

        NCube<String> orig1 = (NCube<String>) NCubeBuilder.discrete1D
        NCube<String> orig2 = (NCube<String>) NCubeBuilder.discrete1DAlt

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig1, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig2, cube2)  // Other guy made no changes

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert !compatibleChange
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.cellMap.size() == 24

        count = cube2.getAxis("rule").columns.size()
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        assert cube2.cellMap.size() == 48
        try
        {
            cube2.getCell(coord)
            fail()
        }
        catch (CoordinateNotFoundException e)
        {
            assert !e.cubeName
            assert !e.coordinate
            assert "rule" == e.axisName
            assert "jones" == e.value
        }
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

        int numCols = cube1.getAxis('rule').columns.size()
        cube1.addColumn('rule', 'true', 'init-rule')

        // Compute delta between copy of original cube and the cube with deleted column.
        // Apply this delta to the 2nd cube to force the same changes on it.
        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)  // Other guy made no changes

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis axis = cube2.getAxis('rule')
        assert axis.columns.size() == numCols + 1
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis state = cube2.getAxis('state')
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 4
        coord = [age: 16, salary: 60000, log: 1000, state: 'OH', rule: 'Summary'] as Map
        assert '99' == getCellIgnoreRule(cube2, coord)
        coord.rule = 'Finalize'
        assert '88' == getCellIgnoreRule(cube2, coord)
        assert cube2.numCells == 50
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis rule = cube2.getAxis('rule')
        assert rule.size() == 4
        coord = [age: 16, salary: 60000, log: 1000, state: 'OH', rule: 'Summary'] as Map
        assert '99' == getCellIgnoreRule(cube2, coord)
        coord.rule = 'Finalize'
        assert '88' == getCellIgnoreRule(cube2, coord)
        assert cube2.numCells == 50
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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

        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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
        assert cube1.numCells == 49

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis age = cube2.getAxis('age')
        assert age.size() == 3
        assert 'love' == getCellIgnoreRule(cube2, coord)
        assert cube2.numCells == 49
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
        assert cube1.numCells == 49
        cube1.addColumn('rule', 'true', 'summary')
        Map coord2 = [age: 35, salary: 60000, log: 1000, state: 'OH', rule: 'summary'] as Map
        cube1.setCell('fear', coord2)
        assert cube1.numCells == 50

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        Axis age = cube2.getAxis('age')
        assert age.size() == 3
        assert 'love' == getCellIgnoreRule(cube2, coord)
        assert cube2.numCells == 50

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
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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

        Column column = cube1.getAxis('rule').columns[0]
        cube1.deleteColumn('rule', column.id)

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.testRuleCube
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.testRuleCube
        NCube<String> orig = (NCube<String>) NCubeBuilder.testRuleCube

        assert cube1.numCells == 3
        Column col1 = cube1.getAxis('rule').columns[0]
        cube1.deleteColumn('rule', col1.id)
        assert cube1.numCells == 2

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.testRuleCube
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.testRuleCube
        NCube<String> orig = (NCube<String>) NCubeBuilder.testRuleCube

        assert cube1.numCells == 3
        Column col1 = cube1.getAxis('rule').columns[0]
        cube1.updateColumn(col1.id, '1 < 2')

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        Axis rule = cube2.getAxis('rule')
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.numCells == 3

        Column col2 = rule.columns[0]
        assert '1 < 2' == col2.value.toString()
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
        Axis rule = (Axis)cube1.getAxis('rule')
        Column col = rule.findColumn('init')
        cube1.updateColumn(col.id, '34 < 40')
        assert '7' == getCellIgnoreRule(cube1, coord)

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        rule = (Axis)cube2.getAxis('rule')
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert cube2.numCells == 48
        assert '7' == getCellIgnoreRule(cube2, coord)
        Column col2 = rule.findColumn('init')
        assert '34 < 40' == col2.toString()
    }

    @Test
    void testDiscreteAddDefaultColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.discrete1D
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.discrete1D
        NCube<String> orig = (NCube<String>) NCubeBuilder.discrete1D

        Axis state = (Axis) cube1.getAxis('state')
        assert state.size() == 2
        cube1.addColumn('state', null)
        assert state.size() == 3
        cube1.setCell('3', [:])

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        state = (Axis) cube2.getAxis('state')
        assert state.size() == 2
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert state.size() == 3
        assert '3' == cube2.getCell([:])
    }

    @Test
    void testDiscreteRemoveDefaultColumn()
    {
        NCube<String> orig = (NCube<String>) NCubeBuilder.discrete1D
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
        catch (InvalidCoordinateException e)
        {
            e.message.contains('required scope')
            assert cube1.name == e.cubeName
            assert !e.coordinateKeys
            assert e.requiredKeys.contains('state')
        }
        assert cube1.numCells == 2

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange

        assert '3' == cube2.getCell([:])
        assert cube2.numCells == 3
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        try
        {
            cube2.getCell([:])
            fail()
        }
        catch (InvalidCoordinateException e)
        {
            e.message.contains('required scope')
            assert cube2.name == e.cubeName
            assert !e.coordinateKeys
            assert e.requiredKeys.contains('state')
        }
        assert cube2.numCells == 2
    }

    @Test
    void testRuleAddDefaultColumn()
    {
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.rule1D
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.rule1D
        NCube<String> orig = (NCube<String>) NCubeBuilder.rule1D

        Axis rules = (Axis) cube1.getAxis('rule')
        assert rules.size() == 2
        cube1.addColumn('rule', null, 'summary')
        assert rules.size() == 3
        cube1.setCell('3', [:])

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        rules = (Axis) cube2.getAxis('rule')
        assert rules.size() == 2
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        assert rules.size() == 3
        assert '3' == getCellIgnoreRule(cube2, [:])
    }

    @Test
    void testRuleRemoveDefaultColumn()
    {
        NCube<String> orig = (NCube<String>) NCubeBuilder.rule1D
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
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
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
        NCube<String> cube1 = (NCube<String>) NCubeBuilder.rule1D
        NCube<String> cube2 = (NCube<String>) NCubeBuilder.rule1D
        NCube<String> orig = (NCube<String>) NCubeBuilder.rule1D

        Axis rule = (Axis) cube1.getAxis('rule')
        Column process = rule.findColumn('process')
        cube1.updateColumn(process.id, '1 < 2')

        rule = (Axis) cube2.getAxis('rule')
        rule.deleteColumn('process')

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert !compatibleChange
    }

    @Test
    void testUpdateRemoveRuleDefaultColumn()
    {
        NCube<String> orig = (NCube<String>) NCubeBuilder.rule1D
        orig.addColumn('rule', null)
        orig.setCell('3', [:])  // associate '3' to default col

        NCube<String> cube1 = orig.duplicate('cube')
        NCube<String> cube2 = orig.duplicate('cube')

        assert cube1.numCells == 3
        assert '3' == getCellIgnoreRule(cube1, [:])

        Axis rule = (Axis) cube1.getAxis('rule')
        Column process = rule.findColumn('process')
        cube1.updateColumn(process.id, '1 < 2')

        rule = (Axis) cube2.getAxis('rule')
        rule.deleteColumn(null)

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)
        DeltaProcessor.mergeDeltaSet(cube1, delta2)

        assert cube2.getAxis('rule').findColumn('process').value == new GroovyExpression('1 < 2', null, false)
        assert !cube1.getAxis('rule').hasDefaultColumn()
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

        Axis state = cube1.getAxis('state') as Axis
        assert state.columnOrder == Axis.DISPLAY
        List<Column> cols = state.columns
        assert cols[0].value == 'OH'
        assert cols[1].value == 'GA'
        assert cols[2].value == 'TX'
        state.columnOrder = Axis.SORTED

        Map<String, Object> delta1 = DeltaProcessor.getDelta(orig, cube1)
        Map<String, Object> delta2 = DeltaProcessor.getDelta(orig, cube2)
        boolean compatibleChange = DeltaProcessor.areDeltaSetsCompatible(delta1, delta2)
        assert compatibleChange
        DeltaProcessor.mergeDeltaSet(cube2, delta1)

        state = cube2.getAxis('state') as Axis
        assert state.columnOrder == Axis.SORTED      // sort indicator updated
        cols = state.columns
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
        List<NCubeInfoDto> list = mutableClient.getBranchChangesForHead(appIdKpartlow)
        mutableClient.commitBranch(appIdKpartlow, list as Object[])

        // Commit a change in 'jdereg' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.2
        list = mutableClient.getBranchChangesForHead(appIdjdereg)
        try
        {
            mutableClient.commitBranch(appIdjdereg, list as Object[])
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Map).size() == 1
        }

        Map map = mutableClient.updateBranch(appIdjdereg)
        assert (map.adds as List).size() == 0
        assert (map.deletes as List).size() == 0
        assert (map.updates as List).size() == 0
        assert (map.restores as List).size() == 0
        assert (map.fastforwards as List).size() == 0
        assert (map.rejects as List).size() == 1
    }

    @Test
    void testMergeReferenceAxisLowerVersion()
    {
        setupLibrary()
        setupLibraryReference()
        ApplicationID appIdKpartlow = setupBranch('kpartlow', '1.0.1')
        ApplicationID appIdjdereg = setupBranch('jdereg', '1.0.2')

        // Commit a change in 'jdereg' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.2
        mutableClient.commitBranch(appIdjdereg)

        // Commit a change in 'kpartlow' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.1
        // Should fail because kpartlow branch is behind and needs to be updated (merged) first
        try
        {
            mutableClient.commitBranch(appIdKpartlow)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Map).size() == 1
        }

        // Update branch 1.0.1 -> 1.0.2 - conflict because HEAD moved to 1.0.2 and branch moved to 1.0.1
        Map map = mutableClient.updateBranch(appIdKpartlow)
        assert (map.adds as List).size() == 0
        assert (map.deletes as List).size() == 0
        assert (map.updates as List).size() == 0
        assert (map.restores as List).size() == 0
        assert (map.fastforwards as List).size() == 0
        assert (map.rejects as List).size() == 1

        // TODO: Write many more reference axis tests
        // auto-merge reference axis (with diff transform)
        // conflict-merge reference axis (one of the components of the reference axis changed - fail)
        // conflict-merge reference axis (reference to non-reference)
        // conflict-merge reference axis (non-reference to reference)
        // Merge new cube in one branch to another branch, no cube in HEAD
    }

    @Test
    void testUpdateAxisMetaProperties()
    {
        String axisName = 'state'
        setupLibrary()
        setupLibraryReference()
        ApplicationID appId = setupBranch('MyBranch', '1.0.2')
        NCube cube = mutableClient.getCube(appId, 'States')

        // Add non-reference axis
        Axis property = new Axis('property', AxisType.DISCRETE, AxisValueType.STRING, true)
        cube.addAxis(property)
        mutableClient.updateCube(cube)
        // Update meta-properties on a non-reference axis
        property.addMetaProperties([hip: 'hop'] as Map)
        mutableClient.updateAxisMetaProperties(appId, 'States', 'property', property.metaProperties)
        cube = mutableClient.getCube(appId, 'States')

        // Add default column
        cube.addColumn(axisName, null)
        // Setup cell values
        cube.setCell(1, [(axisName): 'AL'])
        cube.setCell(2, [(axisName): 'GA'])
        cube.setCell(3, [(axisName): 'OH'])
        cube.setCell(4, [(axisName): 'TX'])
        // Setup column meta properties
        Axis axis = cube.getAxis(axisName)
        Column al = axis.findColumn('AL')
        Column ga = axis.findColumn('GA')
        Column oh = axis.findColumn('OH')
        Column tx = axis.findColumn('TX')
        Column defCol = axis.findColumn(null)
        al.setMetaProperty('foo', 'bar')
        ga.setMetaProperty('foo', 'baz')
        oh.setMetaProperty('foo', 'qux')
        tx.setMetaProperty('foo', 'garply')
        al.setMetaProperty('num', 1)
        ga.setMetaProperty('num', 2)
        oh.setMetaProperty('num', 3)
        tx.setMetaProperty('num', 4)
        defCol.setMetaProperty('meta', 'prop')
        mutableClient.updateCube(cube)

        // Initial basic setup assertions
        assert axis.hasDefaultColumn()
        assert 5 == axis.columns.size()
        assert null != al
        assert null != ga
        assert null != oh
        assert null != tx
        assert null == cube.getCell([(axisName): 'KY'])

        // Update reference axis via meta properties
        axis.addMetaProperties([referenceVersion: '1.0.1'] as Map)

        mutableClient.updateAxisMetaProperties(appId, 'States', axisName, axis.metaProperties)
        cube = mutableClient.getCube(appId, 'States')
        axis = cube.getAxis(axisName)
        ga = axis.findColumn('GA')
        oh = axis.findColumn('OH')
        tx = axis.findColumn('TX')
        defCol = axis.findColumn(null)
        // Changed structure assertions
        assert axis.hasDefaultColumn()
        assert 4 == axis.columns.size()
        assert null != ga
        assert null != oh
        assert null != tx
        // Assert no cell value for default column
        assert null == cube.getCell([(axisName): null])
        // Cell and meta property assertions
        assert 2 == cube.getCell([(axisName): 'GA'])
        assert 3 == cube.getCell([(axisName): 'OH'])
        assert 4 == cube.getCell([(axisName): 'TX'])
        Map<String, Object> defProps = defCol.metaProperties
        assert 1 == defProps.size()
        assert 'prop' == defProps.meta
        Map<String, Object> gaProps = ga.metaProperties
        assert 2 == gaProps.size()
        assert 'baz' == gaProps.foo
        assert 2 == gaProps.num
        Map<String, Object> ohProps = oh.metaProperties
        assert 2 == ohProps.size()
        assert 'qux' == ohProps.foo
        assert 3 == ohProps.num
        Map<String, Object> txProps = tx.metaProperties
        assert 2 == txProps.size()
        assert 'garply' == txProps.foo
        assert 4 == txProps.num

        // Update reference axis via meta properties to previous version
        axis.addMetaProperties([referenceVersion: '1.0.2'] as Map)

        mutableClient.updateAxisMetaProperties(appId, 'States', axisName, axis.metaProperties)
        cube = mutableClient.getCube(appId, 'States')
        axis = cube.getAxis(axisName)
        al = axis.findColumn('AL')
        ga = axis.findColumn('GA')
        oh = axis.findColumn('OH')
        tx = axis.findColumn('TX')
        defCol = axis.findColumn(null)
        assert defCol.default
        // Changed structure assertions
        assert axis.hasDefaultColumn()
        assert 5 == axis.columns.size()
        assert null != al
        assert null != ga
        assert null != oh
        assert null != tx
        // Assert no cell value for default column
        assert null == cube.getCell([(axisName): null])
        // Assert no cell value of meta properties for re-added column
        assert null == cube.getCell([(axisName): 'AL'])
        assert 0 == al.metaProperties.size()
    }

    @Test
    void testMergeBreakReferenceAxis()
    {
        setupLibrary()
        setupLibraryReference()
        ApplicationID appIdKpartlow = setupBranch('kpartlow', '1.0.1')
        ApplicationID appIdjdereg = setupBranch('jdereg', '1.0.2')

        // Commit a change in 'kpartlow' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.1
        mutableClient.commitBranch(appIdKpartlow)

        NCube statesJdereg = mutableClient.getCube(appIdjdereg, 'States')
        statesJdereg.breakAxisReference('state')
        mutableClient.updateCube(statesJdereg)

        try
        {
            mutableClient.commitBranch(appIdjdereg)
            fail()
        }
        catch (BranchMergeException e)
        {
            assert (e.errors[mutableClient.BRANCH_ADDS] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_DELETES] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_UPDATES] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_RESTORES] as Map).size() == 0
            assert (e.errors[mutableClient.BRANCH_REJECTS] as Map).size() == 1
        }
    }

    @Test
    void testMergeBreakReferenceAxisFail()
    {
        setupLibrary()
        setupLibraryReference()
        ApplicationID appIdKpartlow = setupBranch('kpartlow', '1.0.1')
        ApplicationID appIdjdereg = setupBranch('jdereg', '1.0.2')

        // Commit a change in 'jdereg' branch that moves HEAD 'states' cube from reference 1.0.0 to 1.0.2
        mutableClient.commitBranch(appIdjdereg)

        NCube statesKpartlow = mutableClient.getCube(appIdKpartlow, 'States')
        statesKpartlow.breakAxisReference('state')
        mutableClient.updateCube(statesKpartlow)

        try
        {
            mutableClient.commitBranch(appIdKpartlow)
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
    void testMergeReferenceAxisNoChange()
    {
        setupLibrary()
        setupLibraryReference()
        ApplicationID branch1 = setupBranch('branch1', '1.0.1')
        ApplicationID branch2 = setupBranch('branch2', '1.0.1')
        NCube branch1Cube = mutableClient.getCube(branch1, 'States')
        NCube branch2Cube = mutableClient.getCube(branch2, 'States')
        Map deltas = DeltaProcessor.getDelta(branch1Cube, branch2Cube)
        Map stateRefAxisDeltas = deltas[DELTA_AXES]['State'][DELTA_AXIS_REF_CHANGE] as Map
        assert stateRefAxisDeltas.size() == 0
    }

    @Test
    void testDeleteColumnOnNonReferenceAxisNotLostWhenUpdatingReferenceAxis()
    {
        setupLibrary()
        NCube base = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, '2D1Ref.json')
        NCube change = base.duplicate('george')
        change.deleteColumn('Column', 'C')
        base.getAxis('state').setMetaProperty('referenceVersion', '1.0.1')
        Map deltas = DeltaProcessor.getDelta(base, change)
        DeltaProcessor.mergeDeltaSet(base, deltas)
        assert base.getAxis('Column').findColumn('C') == null
    }

    @Test
    void testAddColumnOnNonReferenceAxisNotLostWhenUpdatingReferenceAxis()
    {
        setupLibrary()
        NCube base = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, '2D1Ref.json')
        NCube change = base.duplicate('george')
        change.addColumn('Column', 'D')
        base.getAxis('state').setMetaProperty('referenceVersion', '1.0.1')
        Map deltas = DeltaProcessor.getDelta(base, change)
        DeltaProcessor.mergeDeltaSet(base, deltas)
        assert base.getAxis('Column').findColumn('D') != null
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
        assert desc.contains('change from')
        assert desc.contains("'10' to '3.14'")
    }

    @Test
    void testRefAxisWithColumnMetaProps()
    {
        NCube<String> states = (NCube<String>) NCubeBuilder.discrete1DEmptyWithDefault
        ApplicationID appId = ApplicationID.testAppId.asVersion('1.0.0')
        states.applicationID = appId
        mutableClient.createCube(states)
        List<NCubeInfoDto> list = mutableClient.getBranchChangesForHead(appId)
        mutableClient.commitBranch(appId, list as Object[])
        mutableClient.releaseCubes(appId, '1.0.1')

        NCube ref = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, '2D1Ref.json')
        Axis state = ref.getAxis('state')
        ref.addColumn('state', null)
        Column ohio = state.findColumn('OH')
        Column defCol = state.defaultColumn
        ohio.setMetaProperty('default_value', 'btc')
        ohio.setMetaProperty('foo', 'bar')
        defCol.setMetaProperty('default_value', 'bitcoin')

        assert 'btc' == ref.getCell([state:'OH', column: 'A'])
        assert 'btc' == ref.getCell([state:'OH', column: 'B'])
        assert 'btc' == ref.getCell([state:'OH', column: 'C'])
        assert null == ref.getCell([state:'TX', column: 'A'])
        assert null == ref.getCell([state:'TX', column: 'B'])
        assert null == ref.getCell([state:'TX', column: 'C'])
        assert 'bitcoin' == ref.getCell([state:'AZ', column: 'A'])
        assert 'bitcoin' == ref.getCell([state:'AZ', column: 'B'])
        assert 'bitcoin' == ref.getCell([state:'AZ', column: 'C'])

        String json = ref.toFormattedJson([indexFormat:false])
        NCube copy = NCube.fromSimpleJson(json)
        Axis stateCopy = copy.getAxis('state')
        Column ohioCopy = stateCopy.findColumn('OH')
        Column defCopy = stateCopy.defaultColumn

        assert 'btc' == ohioCopy.getMetaProperty('default_value')
        assert 'bar' == ohioCopy.getMetaProperty('foo')
        assert 'bitcoin' == defCopy.getMetaProperty('default_value')
    }

    // TODO: Swap order of above test
    // TODO: Have jdereg break reference and merge to HEAD
    // TODO: Then, kpartlow should be able to updated from HEAD because it will have a super set of columns

    @Test
    void testDeltaClass()
    {
        Delta delta1 = new Delta(Delta.Location.AXIS, Delta.Type.ADD, 'add axis', null, null, null, null, null)
        Delta delta2 = new Delta(Delta.Location.AXIS, Delta.Type.DELETE, 'delete axis', null, null, null, null, null)
        assert !delta1.equals(delta2)

        Map map = [:]
        map[(delta1)] = 'd1'
        map[(delta2)] = 'd2'

        assert map[(delta1)] == 'd1'
        assert map[(delta2)] == 'd2'

        assert delta1.equals(delta1)
    }

    @Test
    void testDeltaLocationOrder()
    {
        Delta delta1 = new Delta(Delta.Location.NCUBE, Delta.Type.UPDATE, 'updated default', null, null, null, null, null)
        Delta delta2 = new Delta(Delta.Location.NCUBE_META, Delta.Type.ADD, 'added meta-property', null, null, null, null, null)
        Delta delta3 = new Delta(Delta.Location.AXIS, Delta.Type.ADD, 'add axis', null, null, null, null, null)
        Delta delta4 = new Delta(Delta.Location.AXIS_META, Delta.Type.UPDATE, 'updated axis meta-prop', null, null, null, null, null)
        Delta delta5 = new Delta(Delta.Location.COLUMN, Delta.Type.UPDATE, 'updated column value', null, null, null, null, null)
        Delta delta6 = new Delta(Delta.Location.COLUMN_META, Delta.Type.DELETE, 'deleted column meta-prop', null, null, null, null, null)
        Delta delta7 = new Delta(Delta.Location.CELL, Delta.Type.ADD, 'added cell', null, null, null, null, null)
        Delta delta8 = new Delta(Delta.Location.CELL_META, Delta.Type.DELETE, 'added cell meta-prop', null, null, null, null, null)

        Set<Delta> set = [delta8, delta7, delta6, delta5, delta4, delta3, delta2, delta1] as TreeSet
        List<Delta> items = set.toList()
        assert items[0].location == Delta.Location.NCUBE
        assert items[1].location == Delta.Location.NCUBE_META
        assert items[2].location == Delta.Location.AXIS
        assert items[3].location == Delta.Location.AXIS_META
        assert items[4].location == Delta.Location.COLUMN
        assert items[5].location == Delta.Location.COLUMN_META
        assert items[6].location == Delta.Location.CELL
        assert items[7].location == Delta.Location.CELL_META
    }

    @Test
    void testDeltaMessageOrder()
    {
        Delta delta1 = new Delta(Delta.Location.NCUBE, Delta.Type.UPDATE, 'alpha', null, null, null, null, null)
        Delta delta2 = new Delta(Delta.Location.NCUBE, Delta.Type.UPDATE, 'bravo', null, null, null, null, null)
        Delta delta3 = new Delta(Delta.Location.NCUBE, Delta.Type.UPDATE, 'charlie', null, null, null, null, null)
        Set<Delta> set = [delta3, delta2, delta1] as TreeSet
        List<Delta> items = set.toList()
        assert items[0].description == 'alpha'
        assert items[1].description == 'bravo'
        assert items[2].description == 'charlie'
    }

    @Test
    void testDeltaTypeOrder()
    {
        Delta delta1 = new Delta(Delta.Location.AXIS, Delta.Type.ADD, 'z', null, null, null, null, null)
        Delta delta2 = new Delta(Delta.Location.AXIS, Delta.Type.DELETE, 'm', null, null, null, null, null)
        Delta delta3 = new Delta(Delta.Location.AXIS, Delta.Type.UPDATE, 'a', null, null, null, null, null)
        Set<Delta> set = [delta3, delta1, delta2] as TreeSet
        List<Delta> items = set.toList()
        assert items[0] == delta1
        assert items[1] == delta2
        assert items[2] == delta3
    }

    @Test
    void testEmptyDeltaHashCode()
    {
        Delta empty = new Delta(Delta.Location.COLUMN, Delta.Type.ADD, null, null, null, null, null, null)
        assert empty.hashCode() == 0
        assert empty.compareTo(empty) == 0

        Delta delta = new Delta(Delta.Location.COLUMN, Delta.Type.ADD, 'hey', null, null, null, null, null)
        assert empty.compareTo(delta) == -1
    }

    @Test
    void testDeltaEqualsNull()
    {
        Delta delta = new Delta(Delta.Location.COLUMN, Delta.Type.ADD, 'foo', null, null, null, null, null)
        assert !delta.equals(null)
        assert delta.compareTo(null) != 0
    }

    @Test
    void testMergeDeltaNCubeProps()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D
        consumer.setMetaProperty('a', 'alpha')
        consumer.setMetaProperty('b', 'beta')
        producer.setMetaProperty('a', 'alphabet')
        producer.setMetaProperty('foo', 'bar')
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 3
        consumer.mergeDeltas(deltas)
        assert consumer.sha1() == producer.sha1()
    }

    @Test
    void testMergeDeltaAddAxis()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D
        Axis axis = new Axis('code', AxisType.SET, AxisValueType.LONG, true, Axis.SORTED)
        axis.addColumn(new Range(0, 21))
        axis.addColumn(new Range(21, 65))
        axis.addColumn(new Range(65, 199))
        producer.addAxis(axis)
        assert producer.numCells == 2
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.sha1() == producer.sha1()
    }

    @Test
    void testupdateAxisProperties()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D
        Axis axis = producer.getAxis('state')
        axis.columnOrder = Axis.SORTED
        axis.fireAll = true
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.sha1() == producer.sha1()
    }

    @Test
    void testMergeDeltaUpdateColumn()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D
        Axis axis = producer.getAxis('state')
        Column oh = axis.findColumn('OH')
        producer.updateColumn(oh.id, 'OHIO')
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.sha1() == producer.sha1()
    }


    @Test
    void testMergeDeltaDeleteRuleColumn()
    {
        NCube producer = NCubeBuilder.testRuleCube
        NCube consumer = NCubeBuilder.testRuleCube
        Axis axis = producer.getAxis('rule')
        int numCols = axis.columns.size()
        Column br1 = axis.findColumn('br1')
        producer.deleteColumn('rule', br1.id)
        assert axis.columns.size() == numCols - 1
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.sha1() == producer.sha1()
    }

    @Test
    void testMergeDeltaDeleteAxisMetaProperty()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D
        Axis axis = consumer.getAxis('state')
        axis.setMetaProperty('foo', 'bar')
        axis.setMetaProperty('bar', 'baz')
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(consumer, producer)  // backward just to get both cubes matching
        producer.mergeDeltas(deltas)
        axis = producer.getAxis('state')
        axis.removeMetaProperty('foo')
        deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        consumer.mergeDeltas(deltas)
        axis = consumer.getAxis('state')
        axis.metaProperties.size() == 1
        assert consumer.sha1() == producer.sha1()
    }

    @Test
    void testMergeDeltaDeleteColumnMetaProperty()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D
        Axis axis = consumer.getAxis('state')
        Column column = axis.findColumn('TX')
        column.setMetaProperty('foo', 'bar')
        column.setMetaProperty('bar', 'baz')
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(consumer, producer)  // backward just to get both cubes matching
        producer.mergeDeltas(deltas)
        axis = producer.getAxis('state')
        column = axis.findColumn('TX')
        column.removeMetaProperty('foo')
        deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        consumer.mergeDeltas(deltas)
        axis = consumer.getAxis('state')
        column = axis.findColumn('TX')
        column.metaProperties.size() == 1
        assert consumer.sha1() == producer.sha1()
    }

    @Test
    void testColumnCaseChange()
    {
        NCube cube1 = NCubeBuilder.discrete1D
        NCube cube2 = NCubeBuilder.discrete1D
        NCube cube3 = NCubeBuilder.discrete1D

        Axis state = cube1.getAxis('state')
        Column col1 = state.findColumn('OH')
        col1.setMetaProperty('foo', 'bar')
        state.updateColumn(col1.id, 'oh')
        Column col2 = state.findColumn('TX')
        state.updateColumn(col2.id, 'KY')

        Map<String, Object> deltaMap = DeltaProcessor.getDelta(cube2, cube1)
        DeltaProcessor.mergeDeltaSet(cube2, deltaMap)
        Axis cube2state = cube2.getAxis('state')
        assert 2 == cube2state.columns.size()
        assert col1.id == cube2state.findColumn('oh').id
        assert col2.id == cube2state.findColumn('KY').id

        List<Delta> deltaList = DeltaProcessor.getDeltaDescription(cube1, cube3)
        cube3.mergeDeltas(deltaList)
        Axis cube3state = cube3.getAxis('state')
        Column oh = cube3state.findColumn('oh')
        assert 2 == cube3state.columns.size()
        assert 'bar' == oh.getMetaProperty('foo')
        assert col1.id == oh.id
        assert col2.id == cube3state.findColumn('KY').id
    }

    @Test
    void testUpdateFromHeadWithMetaProperties()
    {
        setupLibrary()
        setupLibraryReference()
        ApplicationID branch1 = setupBranch('branch1', '1.0.0')
        ApplicationID branch2 = setupBranch('branch2', '1.0.0')
        testClient.clearCache()

        NCube ncube1 = mutableClient.getCube(branch1, 'States')
        ncube1.setMetaProperty('foo', 1000)
        Axis other = new Axis('other', AxisType.DISCRETE, AxisValueType.STRING, false)
        other.setMetaProperty('x', 100)
        Column foo = new Column('foo')
        foo.setMetaProperty('a', 1)
        foo.setMetaProperty('b', 2)
        other.addColumn(foo)
        ncube1.addAxis(other)
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(branch1)
        mutableClient.updateBranch(branch2)

        AxisRef axisRef = mutableClient.getReferenceAxes(branch1)[0]
        axisRef.destVersion = '1.0.1'
        mutableClient.updateReferenceAxes([axisRef].toArray())
        mutableClient.commitBranch(branch1)

        NCube ncube2 = mutableClient.getCube(branch2, 'States')
        ncube2.setMetaProperty('bar', 2000)
        Axis axis = ncube2.getAxis('other')
        axis.setMetaProperty('y' , 200)
        Column column = ncube2.findColumn('other', 'foo')
        column.setMetaProperty('default_value', 5)
        column.setMetaProperty('a', 10)
        column.removeMetaProperty('b')
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        mutableClient.updateBranch(branch2)

        ncube2 = mutableClient.getCube(branch2, 'States')
        assert 2000 == ncube2.getMetaProperty('bar')
        axis = ncube2.getAxis('other')
        assert 200 == axis.getMetaProperty('y')
        column = ncube2.findColumn('other', 'foo')
        assert 5 == column.getMetaProperty('default_value')
        assert 10 == column.getMetaProperty('a')
        assert !column.getMetaProperty('b')
    }

    @Test
    void testGetAndUpdateReferenceAxesWithInvalidState()
    {
        String snapVer = '1.0.3'
        String nextSnapVer = '1.0.4'
        String cubeName = 'States'

        setupLibrary()
        setupLibraryReference()
        ApplicationID branch1 = setupBranch('branch1', '1.0.0')
        testClient.clearCache()

        AxisRef axisRef = mutableClient.getReferenceAxes(branch1)[0]
        axisRef.destVersion = snapVer
        axisRef.destStatus = ReleaseStatus.SNAPSHOT.name()
        mutableClient.updateReferenceAxes([axisRef].toArray())

        NCube ncube1 = mutableClient.getCube(branch1, cubeName)
        Map<String, Object> args = [:]
        args[REF_TENANT] = branch1.tenant
        args[REF_APP] = ApplicationID.DEFAULT_APP
        args[REF_VERSION] = snapVer
        args[REF_STATUS] = ReleaseStatus.SNAPSHOT.name()
        args[REF_BRANCH] = ApplicationID.HEAD
        args[REF_CUBE_NAME] = 'SimpleDiscrete'
        args[REF_AXIS_NAME] = 'state'

        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, 'stateSource1', args)
        Axis axis = new Axis('stateSource1', 1, false, refAxisLoader)
        ncube1.addAxis(axis)
        mutableClient.updateCube(ncube1)

        // make ref axes invalid by releasing library
        mutableClient.releaseCubes(branch1.asVersion(snapVer).asHead(), nextSnapVer)

        try
        { // make sure we can't load cube
            NCubeInfoDto record = mutableClient.loadCubeRecord(branch1, cubeName, null)
            NCube.createCubeFromRecord(record)
            fail()
        }
        catch(IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'error reading cube from stream')
        }

        // update to new version to make cube valid
        List<AxisRef> axisRefs = mutableClient.getReferenceAxes(branch1)
        assert 2 == axisRefs.size()
        axisRefs[0].destVersion = nextSnapVer
        axisRefs[1].destVersion = nextSnapVer
        mutableClient.updateReferenceAxes(axisRefs.toArray())

        // make sure ref axes were updated successfully
        axisRefs = mutableClient.getReferenceAxes(branch1)
        assert 2 == axisRefs.size()
        assert nextSnapVer == axisRefs[0].destVersion
        assert nextSnapVer == axisRefs[1].destVersion

        // make sure we can load cube again
        NCubeInfoDto record = mutableClient.loadCubeRecord(branch1, cubeName, null)
        NCube ncube = NCube.createCubeFromRecord(record)
        assertNotNull(ncube)
    }

    @Test
    void testSearchWithInvalidReferenceAxisState()
    {
        String snapVer = '1.0.3'
        String cubeName = 'States'

        setupLibrary()
        setupLibraryReference()
        ApplicationID branch1 = setupBranch('branch1', '1.0.0')
        testClient.clearCache()

        AxisRef axisRef = mutableClient.getReferenceAxes(branch1)[0]
        axisRef.destVersion = snapVer
        axisRef.destStatus = ReleaseStatus.SNAPSHOT.name()
        mutableClient.updateReferenceAxes([axisRef].toArray())

        NCube ncube1 = mutableClient.getCube(branch1, cubeName)
        Map<String, Object> args = [:]
        args[REF_TENANT] = branch1.tenant
        args[REF_APP] = ApplicationID.DEFAULT_APP
        args[REF_VERSION] = snapVer
        args[REF_STATUS] = ReleaseStatus.SNAPSHOT.name()
        args[REF_BRANCH] = ApplicationID.HEAD
        args[REF_CUBE_NAME] = 'SimpleDiscrete'
        args[REF_AXIS_NAME] = 'state'

        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, 'stateSource1', args)
        Axis axis = new Axis('stateSource1', 1, false, refAxisLoader)
        ncube1.addAxis(axis)
        mutableClient.updateCube(ncube1)

        // make ref axes invalid by releasing library
        mutableClient.releaseCubes(branch1.asVersion(snapVer).asHead())

        // can also search if there's an invalid reference
        NCube newStates = NCubeBuilder.stateReferrer
        newStates.name = 'newStates'
        newStates.applicationID = branch1
        mutableClient.createCube(newStates)

        try
        {
            List<NCubeInfoDto> searchDtos = mutableClient.search(branch1, null, 'OH', [:])
            assert 1 == searchDtos.size() // should return only the cube that is valid
            assert newStates.name == searchDtos[0].name
        }
        catch(IllegalStateException ignore)
        {   // bug fix should have taken care of this
            fail('Search failed to handle invalid reference axis.')
        }
    }

    @Test
    void testColumnMetaBranchesUpdateToDifferentValue()
    {
        setupMetaPropertyTest()

        // BRANCH1 update meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Column oh = ncube1.findColumn('state', 'OH')
        oh.setMetaProperty('a', 2)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 update meta-property to different value
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Column column = ncube2.getAxis('state').findColumn('OH')
        column.setMetaProperty('a', 3)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testColumnMetaRemoveUpdatedProperty()
    {
        setupMetaPropertyTest()

        // BRANCH1 update meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Column oh = ncube1.findColumn('state', 'OH')
        oh.setMetaProperty('a', 10)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 remove meta-property
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Column column = ncube2.getAxis('state').findColumn('OH')
        column.removeMetaProperty('a')
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testColumnMetaUpdateRemovedProperty()
    {
        setupMetaPropertyTest()

        // BRANCH1 remove meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Column oh = ncube1.findColumn('state', 'OH')
        oh.removeMetaProperty('a')
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 update meta-property
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Column column = ncube2.getAxis('state').findColumn('OH')
        column.setMetaProperty('a', 3)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testColumnMetaAddSameValue()
    {
        setupMetaPropertyTest()

        // BRANCH1 add meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Column oh = ncube1.findColumn('state', 'OH')
        oh.setMetaProperty('c', 10)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 add meta-property with same value
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Column column = ncube2.getAxis('state').findColumn('OH')
        column.setMetaProperty('c', 10)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testColumnMetaUpdateToSameValue()
    {
        setupMetaPropertyTest()

        // BRANCH1 update meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Column oh = ncube1.findColumn('state', 'OH')
        oh.setMetaProperty('a', 2)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 update meta-property to same value
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Column column = ncube2.getAxis('state').findColumn('OH')
        column.setMetaProperty('a', 2)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testAxisMetaBranchesUpdateToDifferentValue()
    {
        setupMetaPropertyTest()

        // BRANCH1 update meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Axis state = ncube1.getAxis('state')
        state.setMetaProperty('x', 200)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 update meta-property to different value
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Axis axis = ncube2.getAxis('state')
        axis.setMetaProperty('x', 300)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testAxisMetaRemoveUpdatedProperty()
    {
        setupMetaPropertyTest()

        // BRANCH1 update meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Axis state = ncube1.getAxis('state')
        state.setMetaProperty('x', 300)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 remove meta-property
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Axis axis = ncube2.getAxis('state')
        axis.removeMetaProperty('x')
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testAxisMetaUpdateRemovedProperty()
    {
        setupMetaPropertyTest()

        // BRANCH1 remove meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Axis state = ncube1.getAxis('state')
        state.removeMetaProperty('x')
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 update meta-property
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Axis axis = ncube2.getAxis('state')
        axis.setMetaProperty('x', 300)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testAxisMetaAddSameValue()
    {
        setupMetaPropertyTest()

        // BRANCH1 add meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Axis state = ncube1.getAxis('state')
        state.setMetaProperty('z', 10)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 add meta-property with same value
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Axis axis = ncube2.getAxis('state')
        axis.setMetaProperty('z', 10)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testAxisMetaUpdateToSameValue()
    {
        setupMetaPropertyTest()

        // BRANCH1 update meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        Axis state = ncube1.getAxis('state')
        state.setMetaProperty('x', 200)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 update meta-property to same value
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        Axis axis = ncube2.getAxis('state')
        axis.setMetaProperty('x', 200)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testNCubeMetaBranchesUpdateToDifferentValue()
    {
        setupMetaPropertyTest()

        // BRANCH1 update meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        ncube1.setMetaProperty('foo', 3000)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 update meta-property to different value
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        ncube2.setMetaProperty('foo', 4000)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testNCubeMetaRemoveUpdatedProperty()
    {
        setupMetaPropertyTest()

        // BRANCH1 update meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        ncube1.setMetaProperty('foo', 4000)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 remove meta-property
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        ncube2.removeMetaProperty('foo')
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testNCubeMetaUpdateRemovedProperty()
    {
        setupMetaPropertyTest()

        // BRANCH1 remove meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        ncube1.removeMetaProperty('foo')
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 update meta-property
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        ncube2.setMetaProperty('foo', 300)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 1
    }

    @Test
    void testNCubeMetaAddSameValue()
    {
        setupMetaPropertyTest()

        // BRANCH1 add meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        ncube1.setMetaProperty('baz', 3000)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 add meta-property with same value
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        ncube2.setMetaProperty('baz', 3000)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testNCubeMetaUpdateToSameValue()
    {
        setupMetaPropertyTest()

        // BRANCH1 update meta-property
        NCube ncube1 = mutableClient.getCube(BRANCH1, 'SimpleDiscrete')
        ncube1.setMetaProperty('foo', 200)
        ncube1.clearSha1()
        mutableClient.updateCube(ncube1)
        mutableClient.commitBranch(BRANCH1)

        // BRANCH2 update meta-property to same value
        NCube ncube2 = mutableClient.getCube(BRANCH2, 'SimpleDiscrete')
        ncube2.setMetaProperty('foo', 200)
        ncube2.clearSha1()
        mutableClient.updateCube(ncube2)
        Map<String, Object> result = mutableClient.updateBranch(BRANCH2)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0
    }

    @Test
    void testChangedFlag()
    {
        // create 2 branches (and HEAD) with simple n-cube
        NCube ncube1 = NCubeBuilder.discrete1D
        String ncubeName = ncube1.name
        ncube1.applicationID = BRANCH1
        mutableClient.createCube(ncube1)
        mutableClient.commitBranch(BRANCH1)
        mutableClient.updateBranch(BRANCH2)

        // BRANCH1 change TX from 2->20
        ncube1.setCell(20, [state: 'TX'])
        mutableClient.updateCube(ncube1)

        // BRANCH2 change OH from 1->10 and commit
        NCube ncube2 = mutableClient.getCube(BRANCH2, ncubeName)
        ncube2.setCell(10, [state: 'OH'])
        mutableClient.updateCube(ncube2)
        mutableClient.commitBranch(BRANCH2)

        // BRANCH1 "steals" OH change from BRANCH2
        List<Delta> deltas = DeltaProcessor.getDeltaDescription(ncube2, ncube1)
        List<Delta> deltasToMerge = deltas.findAll { it.description.contains("OH")}
        mutableClient.mergeDeltas(BRANCH1, ncubeName, deltasToMerge)

        // Update from HEAD shows fast forward because update was "stolen" previously
        Map<String, Object> result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 1
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0

        NCubeInfoDto dto = mutableClient.search(BRANCH1, ncubeName, null, null)[0]
        assert dto.changed

        // BRANCH2 change OH from 10->100 and commit
        ncube2.setCell(100, [state: 'OH'])
        mutableClient.updateCube(ncube2)
        mutableClient.commitBranch(BRANCH2)

        // Update from HEAD shows update because there's a new change in HEAD
        result = mutableClient.updateBranch(BRANCH1)
        assert (result[mutableClient.BRANCH_ADDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_DELETES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_UPDATES] as Map).size() == 1
        assert (result[mutableClient.BRANCH_RESTORES] as Map).size() == 0
        assert (result[mutableClient.BRANCH_FASTFORWARDS] as Map).size() == 0
        assert (result[mutableClient.BRANCH_REJECTS] as Map).size() == 0

        ncube1 = mutableClient.getCube(BRANCH1, ncubeName)
        assert 20 == ncube1.getCell([state: 'TX'])
    }

    @Test
    void testMergeDeltaAddTest()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D

        addTestToCube(producer)

        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.testData.size() == 1
    }

    @Test
    void testMergeDeltaDeleteTest()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D

        addTestToCube(producer)
        addTestToCube(consumer)

        producer.removeMetaProperty(NCube.METAPROPERTY_TEST_DATA)

        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert !consumer.testData
    }

    @Test
    void testMergeDeltaAddCoord()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D

        addTestToCube(producer)
        addTestToCube(consumer)

        NCubeTest test = producer.testData[0]
        Map<String, CellInfo> coord = test.coord
        coord.testInput2 = new CellInfo('test2')
        producer.testData = [new NCubeTest(test.name, coord, test.assertions)].toArray()

        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.testData[0].coord.size() == 2
    }

    @Test
    void testMergeDeltaRemoveCoord()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D

        addTestToCube(producer)
        addTestToCube(consumer)

        NCubeTest test = producer.testData[0]
        Map<String, CellInfo> coord = test.coord
        coord.remove('testInput')
        producer.testData = [new NCubeTest(test.name, coord, test.assertions)].toArray()

        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.testData[0].coord.size() == 0
    }

    @Test
    void testMergeDeltaUpdateCoord()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D

        addTestToCube(producer)
        addTestToCube(consumer)

        NCubeTest test = producer.testData[0]
        Map<String, CellInfo> coord = test.coord
        coord.testInput = new CellInfo('test2')
        producer.testData = [new NCubeTest(test.name, coord, test.assertions)].toArray()

        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.testData[0].coord.testInput.value == 'test2'
    }

    @Test
    void testMergeDeltaAddAssert()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D

        addTestToCube(producer)
        addTestToCube(consumer)

        NCubeTest test = producer.testData[0]
        List<CellInfo> asserts = test.assertions.toList() as List<CellInfo>
        asserts.add(new CellInfo('output2'))
        producer.testData = [new NCubeTest(test.name, test.coord, asserts.toArray() as CellInfo[])].toArray()

        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.testData[0].assertions.size() == 2
    }

    @Test
    void testMergeDeltaRemoveAssert()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D

        addTestToCube(producer)
        addTestToCube(consumer)

        NCubeTest test = producer.testData[0]
        producer.testData = [new NCubeTest(test.name, test.coord, [].toArray() as CellInfo[])].toArray()

        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.testData[0].assertions.size() == 0
    }

    @Test
    void testMergeDeltaUpdateAssert()
    {
        NCube producer = NCubeBuilder.discrete1D
        NCube consumer = NCubeBuilder.discrete1D

        addTestToCube(producer)
        addTestToCube(consumer)

        NCubeTest test = producer.testData[0]
        List<CellInfo> asserts = test.assertions.toList() as List<CellInfo>
        asserts[0].isUrl = true
        producer.testData = [new NCubeTest(test.name, test.coord, asserts.toArray() as CellInfo[])].toArray()

        List<Delta> deltas = DeltaProcessor.getDeltaDescription(producer, consumer)
        assert deltas.size() == 1
        consumer.mergeDeltas(deltas)
        assert consumer.testData[0].assertions[0].isUrl
    }

    private static addTestToCube(NCube cube)
    {
        String testName = 'test'
        Map<String, CellInfo> coord = [testInput: new CellInfo('test')]
        CellInfo[] asserts = [new CellInfo('output')].toArray() as CellInfo[]
        cube.testData = [new NCubeTest(testName, coord, asserts)].toArray()
    }

    static void setupMetaPropertyTest()
    {
        NCube ncube = NCubeBuilder.discrete1D
        ncube.applicationID = BRANCH1
        ncube.setMetaProperty('foo', 1000)
        ncube.setMetaProperty('bar', 2000)
        Axis state = ncube.getAxis('state')
        state.setMetaProperty('x', 100)
        state.setMetaProperty('y', 200)
        Column oh = ncube.findColumn('state','OH')
        oh.setMetaProperty('a', 1)
        oh.setMetaProperty('b', 2)
        mutableClient.createCube(ncube)
        mutableClient.commitBranch(BRANCH1)
        mutableClient.updateBranch(BRANCH2)
    }

    static void setupLibrary()
    {
        NCube<String> states4 = (NCube<String>) NCubeBuilder.get4StatesNotSorted()
        NCube<String> states3 = (NCube<String>) NCubeBuilder.get3StatesNotSorted()
        NCube<String> states2 = (NCube<String>) NCubeBuilder.discrete1D

        ApplicationID appId = ApplicationID.testAppId.asVersion('1.0.0')
        states2.applicationID = appId
        mutableClient.createCube(states2)
        List<NCubeInfoDto> list = mutableClient.getBranchChangesForHead(appId)
        mutableClient.commitBranch(appId, list as Object[])
        mutableClient.releaseCubes(appId, '1.0.1')

        appId = ApplicationID.testAppId.asVersion('1.0.1')
        states3.applicationID = appId
        mutableClient.updateCube(states3)
        list = mutableClient.getBranchChangesForHead(appId)
        mutableClient.commitBranch(appId, list as Object[])
        mutableClient.releaseCubes(appId, '1.0.2')

        appId = ApplicationID.testAppId.asVersion('1.0.2')
        states4.applicationID = appId
        mutableClient.updateCube(states4)
        list = mutableClient.getBranchChangesForHead(appId)
        mutableClient.commitBranch(appId, list as Object[])
        mutableClient.releaseCubes(appId, '1.0.3')
    }

    static void setupLibraryReference()
    {
        NCube<String> statesRef = NCubeBuilder.stateReferrer

        ApplicationID appId = ApplicationID.testAppId.asVersion('2.0.0')
        statesRef.applicationID = appId
        mutableClient.createCube(statesRef)
        List<NCubeInfoDto> list = mutableClient.getBranchChangesForHead(appId)
        mutableClient.commitBranch(appId, list as Object[])

        list = mutableClient.search(appId.asHead(), null, null, null)
        assert list.size() == 1
        statesRef = mutableClient.getCube(appId.asHead(), 'states') as NCube
        Map input = [state:'OH']
        assert 1 == statesRef.getCell(input) as int
        input.state = 'TX'
        assert 2 == statesRef.getCell(input) as int
    }

    static ApplicationID setupBranch(String branch, String refVer)
    {
        NCube<String> states = (NCube<String>) NCubeBuilder.stateReferrer
        Axis state = states.getAxis('state')
        state.setMetaProperty(REF_VERSION, refVer)
        ApplicationID appId = ApplicationID.testAppId.asBranch(branch).asSnapshot().asVersion('2.0.0')
        states.applicationID = appId
        mutableClient.copyBranch(appId.asHead(), appId, false)
        mutableClient.updateCube(states)
        return appId
    }

    static Object getCellIgnoreRule(NCube ncube, Map coord)
    {
        Set<Long> idCoord = ncube.getCoordinateKey(coord)
        return ncube.getCellById(idCoord, coord, [:])
    }
}
