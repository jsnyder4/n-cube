package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
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
import static org.mockito.Matchers.any
import static org.mockito.Matchers.any
import static org.mockito.Matchers.anyMap
import static org.mockito.Matchers.anyMap
import static org.mockito.Matchers.eq
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.mock
import static org.mockito.Mockito.when
import static org.mockito.Mockito.when
import static org.mockito.Mockito.when
import static org.mockito.Mockito.when

@CompileStatic
class TestCIStringReferenceAxis extends CommonStringAxisTests {
    NCubePersister existingPersister = null
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

    @After
    void tearDown()
    {
        TestingDatabaseHelper.tearDownDatabase()
    }

    @Before
    void setUp() {
        TestingDatabaseHelper.setupDatabase()
        def referenceCube = new NCube<Integer>("SingleStringAxis")
        def referenceAxis = NCubeBuilder.getCaseInsensitiveAxis(false)
        referenceCube.addAxis(referenceAxis)
        referenceCube.setCell(10, [Gender: 'Male'])
        referenceCube.setCell(11, [Gender: 'Female'])
        NCubeManager.addCube(ApplicationID.testAppId, referenceCube)
        ncube = new NCube<Integer>("SingleStringAxis")
        Map<String, Object> args = [:]

        ApplicationID appId = ApplicationID.testAppId
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
