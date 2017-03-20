package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Test

@CompileStatic
class TestCIStringAxis extends CommonStringAxisTests {
    @Test
    void caseDiffers() {
        assert 0 == ncube.getCell([Gender: 'maLe'])
        assert 1 == ncube.getCell([Gender: 'fEMALE'])
        assert 3 == TestNCube.countMatches(ncube.toHtml(), "<tr")
    }


    @Test
    void findColumnCaseDiffers() {
        Column column = genderAxis.findColumn('maLe')
        assert 'Male' == column.value
    }

    @Before
    void setUp() {
        ncube = new NCube<Integer>("SingleStringAxis")
        genderAxis = NCubeBuilder.getCaseInsensitiveAxis(false)
        ncube.addAxis(genderAxis)
        ncube.setCell(0, [Gender: 'Male'])
        ncube.setCell(1, [Gender: 'Female'])
    }
}
