package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static com.cedarsoftware.ncube.ReferenceAxisLoader.*

@CompileStatic
class TestCIStringReferenceAxis extends CommonStringAxisTests {

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
        ApplicationID appId = ApplicationID.testAppId
        def referenceCube = new NCube<Integer>("SingleStringAxis")
        referenceCube.applicationID = appId
        def referenceAxis = NCubeBuilder.getCaseInsensitiveAxis(false)
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
