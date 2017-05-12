package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static com.cedarsoftware.ncube.ReferenceAxisLoader.*
import static org.junit.Assert.*

@CompileStatic
class TestStringReferenceAxis extends CommonStringAxisTests {

    @Test
    void caseDiffers() {
        try {
            ncube.getCell([Gender: 'maLe'] as Map)
            fail()
        }
        catch (CoordinateNotFoundException e) {
            assertTrue(e.message.contains("alue"))
            assertTrue(e.message.contains("not"))
            assertTrue(e.message.contains("found"))
            assertTrue(e.message.contains("axis"))
            assertEquals(ncube.name, e.cubeName)
            assertEquals([Gender: 'maLe'] as Map, e.coordinate)
            assertEquals("Gender", e.axisName)
            assertEquals('maLe', e.value)
        }
    }

    @Test
    void findColumnCaseDiffers() {
        Column column = genderAxis.findColumn('maLe')
        assert null == column
    }

    @Before
    void setUp() {
        ApplicationID appId = ApplicationID.testAppId
        def referenceCube = new NCube<Integer>("SingleStringAxis")
        referenceCube.applicationID = appId
        def referenceAxis = NCubeBuilder.getGenderAxis(false)
        referenceCube.addAxis(referenceAxis)
        referenceCube.setCell(10, [Gender: 'Male'])
        referenceCube.setCell(11, [Gender: 'Female'])
        ncubeRuntime.addCube(referenceCube)
        ncube = new NCube<Integer>("SingleStringAxis")
        Map<String, Object> args = [:]

        args[REF_TENANT] = appId.tenant
        args[REF_APP] = appId.app
        args[REF_VERSION] = appId.version
        args[REF_STATUS] = appId.status
        args[REF_BRANCH] = appId.branch
        args[REF_CUBE_NAME] = 'SingleStringAxis'
        args[REF_AXIS_NAME] = 'Gender'

        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader('SingleStringAxis', 'Gender', args)
        genderAxis = new Axis('stateSource', 1, false, refAxisLoader)
        ncube.addAxis(genderAxis)
        ncube.setCell(0, [Gender: 'Male'])
        ncube.setCell(1, [Gender: 'Female'])
    }

}
