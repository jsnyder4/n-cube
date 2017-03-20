package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Test

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

@CompileStatic
class TestStringAxis extends CommonStringAxisTests {

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
        ncube = new NCube<Integer>("SingleStringAxis")
        genderAxis = NCubeBuilder.getGenderAxis(false)
        ncube.addAxis(genderAxis)
        ncube.setCell(0, [Gender: 'Male'])
        ncube.setCell(1, [Gender: 'Female'])
    }
}
