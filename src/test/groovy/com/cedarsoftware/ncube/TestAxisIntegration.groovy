package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_APP
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_AXIS_NAME
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_BRANCH
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_CUBE_NAME
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_STATUS
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_VERSION
import static org.junit.Assert.fail

@CompileStatic
class TestAxisIntegration extends NCubeCleanupBaseTest
{
    @Test
    void testCreateReferenceFromAxisNew()
    {
        ApplicationID appId = ApplicationID.testAppId.asBranch('mongoose')
        String refCubeName = 'refTest'
        String refAxisName = 'refAx'
        NCube cube = new NCube('test')
        Axis axis = new Axis('axis', AxisType.DISCRETE, AxisValueType.STRING, false)
        axis.addColumn('A')
        axis.addColumn('B')
        axis.addColumn('C')
        cube.addAxis(axis)
        cube.setCell('Aval', [axis: 'A'])
        cube.setCell('Bval', [axis: 'B'])
        cube.setCell('Cval', [axis: 'C'])
        cube.applicationID = appId
        int numCells = cube.numCells
        assert numCells == 3
        mutableClient.createCube(cube)
        mutableClient.createRefAxis(appId, cube.name, axis.name, appId, refCubeName, refAxisName)

        cube = mutableClient.getCube(appId, cube.name)
        NCube refCube = mutableClient.getCube(appId, refCubeName)

        assert refCube
        assert 1 == refCube.numDimensions
        Axis refAxis = refCube.getAxis(refAxisName)
        assert refAxis
        List<Column> refCols = refAxis.columns
        assert 3 == refCols.size()
        assert 'A' == refCols.get(0).value
        assert 'B' == refCols.get(1).value
        assert 'C' == refCols.get(2).value

        assert 1 == cube.numDimensions
        axis = cube.getAxis(axis.name)
        assert axis
        Map metaProps = axis.metaProperties
        assert axis.reference
        assert appId.app == metaProps[REF_APP]
        assert appId.version == metaProps[REF_VERSION]
        assert appId.status == metaProps[REF_STATUS]
        assert appId.branch == metaProps[REF_BRANCH]
        assert refCubeName == metaProps[REF_CUBE_NAME]
        assert refAxisName == metaProps[REF_AXIS_NAME]
        assert 'Aval' == cube.getCell((axis.name): 'A')
        assert 'Bval' == cube.getCell((axis.name): 'B')
        assert 'Cval' == cube.getCell((axis.name): 'C')
        assert cube.numCells == numCells
    }

    @Test
    void testCreateReferenceFromAxisExisting()
    {
        ApplicationID appId = ApplicationID.testAppId.asBranch('cobra')
        NCube refCube = new NCube('refTest')
        Axis refAxis = new Axis('axis', AxisType.DISCRETE, AxisValueType.STRING, false)
        refAxis.addColumn('A')
        refAxis.addColumn('B')
        refAxis.addColumn('C')
        refCube.addAxis(refAxis)
        refCube.applicationID = appId
        mutableClient.createCube(refCube)

        NCube cube = new NCube('test')
        Axis axis = new Axis('axis', AxisType.DISCRETE, AxisValueType.STRING, false)
        axis.addColumn('A')
        axis.addColumn('B')
        axis.addColumn('C')
        cube.addAxis(axis)
        cube.setCell('Aval', [axis: 'A'])
        cube.setCell('Bval', [axis: 'B'])
        cube.setCell('Cval', [axis: 'C'])
        cube.applicationID = appId
        int numCells = cube.numCells
        assert numCells == 3
        mutableClient.createCube(cube)
        mutableClient.createRefAxis(appId, cube.name, axis.name, appId, refCube.name, refAxis.name)

        cube = mutableClient.getCube(appId, cube.name)
        assert 1 == cube.numDimensions
        axis = cube.getAxis(axis.name)
        assert axis
        Map metaProps = axis.metaProperties
        assert axis.reference
        assert appId.app == metaProps[REF_APP]
        assert appId.version == metaProps[REF_VERSION]
        assert appId.status == metaProps[REF_STATUS]
        assert appId.branch == metaProps[REF_BRANCH]
        assert refCube.name == metaProps[REF_CUBE_NAME]
        assert refAxis.name == metaProps[REF_AXIS_NAME]
        assert 'Aval' == cube.getCell((axis.name): 'A')
        assert 'Bval' == cube.getCell((axis.name): 'B')
        assert 'Cval' == cube.getCell((axis.name): 'C')
        assert cube.numCells == numCells
    }

    @Test
    void testConvertingAxisToExistingReferenceWithFewerColumnsToSeeIfCellsAreProperlyOrphaned()
    {
        ApplicationID fooApp = ApplicationID.testAppId.asBranch('foo')
        NCube refCube = new NCube('refTest')
        refCube.applicationID = fooApp
        Axis refAxis = new Axis('axis', AxisType.DISCRETE, AxisValueType.STRING, false)
        refAxis.addColumn('A')
        refAxis.addColumn('C')
        refAxis.addColumn('E')
        refCube.addAxis(refAxis)
        refCube.applicationID = fooApp
        mutableClient.createCube(refCube)

        ApplicationID barApp = ApplicationID.testAppId.asBranch('bar')
        NCube cube = new NCube('test')
        cube.applicationID = barApp
        Axis axis = new Axis('axis', AxisType.DISCRETE, AxisValueType.STRING, false)
        axis.addColumn('A')
        axis.addColumn('B')
        axis.addColumn('C')
        axis.addColumn('D')
        axis.addColumn('E')
        cube.addAxis(axis)
        cube.setCell('Aval', [axis: 'A'])
        cube.setCell('Bval', [axis: 'B'])

        mutableClient.createCube(cube)
        mutableClient.createRefAxis(barApp, cube.name, axis.name, fooApp, refCube.name, refAxis.name)
        cube = mutableClient.getCube(barApp, cube.name)
        axis = cube.getAxis('axis')

        assert axis.contains('A')
        assert axis.contains('C')
        assert axis.contains('E')
        assert axis.size() == 3
        assert cube.numCells == 1
    }

    @Test
    void testConvertExistingAxisToRefAxisWithNoMatchingColumns()
    {
        ApplicationID fooApp = ApplicationID.testAppId.asBranch('foo')
        NCube refCube = new NCube('refTest')
        refCube.applicationID = fooApp
        Axis refAxis = new Axis('axis', AxisType.DISCRETE, AxisValueType.STRING, false)
        refAxis.addColumn('A')
        refAxis.addColumn('B')
        refAxis.addColumn('C')
        refCube.addAxis(refAxis)
        refCube.applicationID = fooApp
        mutableClient.createCube(refCube)

        ApplicationID barApp = ApplicationID.testAppId.asBranch('bar')
        NCube cube = new NCube('test')
        cube.applicationID = barApp
        Axis axis = new Axis('axis', AxisType.DISCRETE, AxisValueType.STRING, false)
        axis.addColumn('X')
        axis.addColumn('Y')
        axis.addColumn('Z')
        cube.addAxis(axis)
        cube.setCell('Xval', [axis: 'X'])

        mutableClient.createCube(cube)
        mutableClient.createRefAxis(barApp, cube.name, axis.name, fooApp, refCube.name, refAxis.name)
        cube = mutableClient.getCube(barApp, cube.name)
        axis = cube.getAxis('axis')

        assert axis.contains('A')
        assert axis.contains('B')
        assert axis.contains('C')
        assert axis.size() == 3
        assert cube.numCells == 0
    }

    @Test
    void testConvertExistingAxisToRefAxisSelfReference()
    {
        ApplicationID fooApp = ApplicationID.testAppId.asBranch('foo')
        NCube cube = new NCube('test')
        cube.applicationID = fooApp
        Axis refAxis = new Axis('axis', AxisType.DISCRETE, AxisValueType.STRING, false)
        refAxis.addColumn('A')
        refAxis.addColumn('B')
        refAxis.addColumn('C')
        cube.addAxis(refAxis)
        cube.applicationID = fooApp
        mutableClient.createCube(cube)

        try
        {
            mutableClient.createRefAxis(fooApp, cube.name, refAxis.name, fooApp, cube.name, refAxis.name)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'axis cube', 'reference', 'different')
        }
    }
}
