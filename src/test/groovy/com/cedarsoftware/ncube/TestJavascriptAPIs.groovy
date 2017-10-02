package com.cedarsoftware.ncube

import com.cedarsoftware.controller.NCubeController
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.servlet.JsonCommandServlet
import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.JsonHttpProxy
import com.cedarsoftware.util.StringUtilities
import com.google.common.base.Joiner
import groovy.transform.CompileStatic
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito

import javax.servlet.http.HttpServletRequest
import java.lang.reflect.Method

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static com.cedarsoftware.ncube.ReferenceAxisLoader.*
import static org.junit.Assert.assertTrue
import static org.junit.Assert.fail

/**
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
class TestJavascriptAPIs extends NCubeCleanupBaseTest
{
    private static final ApplicationID BRANCH1 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'FOO')
    private static final ApplicationID BRANCH2 = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'test', '1.28.0', ReleaseStatus.SNAPSHOT.name(), 'BAR')

    @Before
    void setup()
    {
        super.setup()
        if (!NCubeAppContext.clientTest)
        {
            HttpServletRequest request = Mockito.mock(HttpServletRequest.class)
            Mockito.when(request.headerNames).thenReturn(new Enumeration<String>() {
                boolean hasMoreElements()
                {
                    return false
                }

                String nextElement()
                {
                    return null
                }
            })
            JsonCommandServlet.servletRequest.set(request)
        }
    }

    @Test
    void testControllerImplementsInterfaces()
    {
        // runtime methods removed because they are runtime only concerns (never go across the wire)
        Set<String> allowedRunTimeMethods = [
                'getCube(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String)',
                'addCube(class com.cedarsoftware.ncube.NCube)',
                'getBootVersion(class java.lang.String, class java.lang.String)',
                'getSystemParams()',
                'getTestCauses(class java.lang.Throwable)',
                'getUrlClassLoader(class com.cedarsoftware.ncube.ApplicationID, interface java.util.Map)',
                'getLocalClassloader(class com.cedarsoftware.ncube.ApplicationID)',
                'getApplicationID(class java.lang.String, class java.lang.String, interface java.util.Map)',
                'getNCubeFromResource(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String)',
                'getNCubesFromResource(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String)',
                'addAdvice(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String, interface com.cedarsoftware.ncube.Advice)',
                'getActualUrl(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String, interface java.util.Map)',
                'clearCache(class com.cedarsoftware.ncube.ApplicationID, interface java.util.Collection)'
        ] as Set

        // mutable methods removed because of transformed return type (usually Object[] for Javascript clients)
        Set<String> allowedMutableMethods = [
                'getCube(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String)',
                'getCellAnnotation(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String, interface java.util.Set)',
                'getCellAnnotation(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String, interface java.util.Set, boolean)',
                'mergeDeltas(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String, interface java.util.List)',
                'assertPermissions(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String, class com.cedarsoftware.ncube.Action)',
                'checkPermissions(class com.cedarsoftware.ncube.ApplicationID, class java.lang.String, class com.cedarsoftware.ncube.Action)'
        ] as Set

        Set<String> runtimeMethods = getMethods(NCubeRuntimeClient.class.methods)
        Set<String> mutableMethods = getMethods(NCubeMutableClient.class.methods)
        Set<String> controllerMethods = getMethods(NCubeController.class.methods)

        runtimeMethods.removeAll(allowedRunTimeMethods)
        runtimeMethods.removeAll(controllerMethods)
        assert runtimeMethods.empty

        mutableMethods.removeAll(allowedMutableMethods)
        mutableMethods.removeAll(controllerMethods)
        assert mutableMethods.empty
    }

    private static Set<String> getMethods(Method[] methods)
    {
        Set<String> methodNames = [] as Set
        for (Method method : methods)
        {
            methodNames.add(methodToString(method))
        }
        return methodNames
    }

    private static String methodToString(Method method)
    {
        StringBuilder sb = new StringBuilder(method.name)
        sb.append('(')
        sb.append(Joiner.on(', ').join(method.parameterTypes))
        sb.append(')')
        return sb.toString()
    }
    
    @Test
    void testGetJson()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        NCube ncube1 = NCubeBuilder.discrete1D
        NCube ncube2 = NCubeBuilder.discrete1D
        ncube1.applicationID = BRANCH1
        ncube2.applicationID = BRANCH2
        mutableClient.createCube(ncube1)
        String cubeName = ncube1.name

        Map<String, Object> args = [
                (REF_TENANT): BRANCH1.tenant,
                (REF_APP): BRANCH1.app,
                (REF_VERSION): BRANCH1.version,
                (REF_STATUS): BRANCH1.status,
                (REF_BRANCH): BRANCH1.branch,
                (REF_CUBE_NAME): cubeName,
                (REF_AXIS_NAME): 'state'
        ] as Map<String, Object>

        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, 'stateSource', args)
        Axis axis = new Axis('stateSource', 1, false, refAxisLoader)
        ncube2.addAxis(axis)
        mutableClient.createCube(ncube2)

        // test valid cube json calls
        String json = call('getJson', [BRANCH2, cubeName, [mode:'json']])
        assert StringUtilities.hasContent(json)
        json = call('getJson', [BRANCH2, cubeName, [mode:'json-index']])
        assert StringUtilities.hasContent(json)

        mutableClient.deleteBranch(BRANCH1)
        call('clearCache', [BRANCH2])

        // invalid cube json, but raw json should still load
        json = call('getJson', [BRANCH2, cubeName, [mode:'json']])
        assert StringUtilities.hasContent(json)

        try
        { // invalid cube json should cause exception
            call('getJson', [BRANCH2, cubeName, [mode:'json-index']])
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'error reading cube from stream')
        }
    }

    @Test
    void testCommitCube()
    {
        NCube cube = NCubeBuilder.discrete1D
        cube.applicationID = BRANCH1
        mutableClient.createCube(cube)
        call('commitCube', [BRANCH1, cube.name])
        List<NCubeInfoDto> dtos = mutableClient.search(BRANCH1, cube.name, null, null)
        assert 1 == dtos.size()
    }

    @Test
    void testIsCubeUpToDate()
    {
        NCube ncube1 = NCubeBuilder.discrete1D
        NCube ncube2 = NCubeBuilder.discrete1D
        ncube1.applicationID = BRANCH1
        mutableClient.createCube(ncube1)
        ncube2.applicationID = BRANCH2
        mutableClient.createCube(ncube2)

        assert mutableClient.isCubeUpToDate(ncube1.applicationID, ncube1.name)    // new state (cube not in HEAD) is considered up-to-date
        mutableClient.commitBranch(ncube1.applicationID)

        assert !mutableClient.isCubeUpToDate(ncube2.applicationID, ncube2.name)    // Not up-to-date because BRANCH2 created cube (no HEAD sha1) which matches a cube in HEAD

        assert mutableClient.isCubeUpToDate(ncube1.applicationID, ncube1.name)    // same as HEAD, up-to-date
        assert !mutableClient.isCubeUpToDate(ncube2.applicationID, ncube2.name)    // same as HEAD, but no HEAD SHA1

        mutableClient.updateBranch(BRANCH2)                                       // pick up changes from HEAD
        assert mutableClient.isCubeUpToDate(ncube2.applicationID, ncube2.name)    // same as HEAD, with HEAD-SHA1 now on branch cube

        ncube2.addColumn('state', 'AL')
        mutableClient.updateCube(ncube2)
        mutableClient.commitBranch(BRANCH2)
        assert !mutableClient.isCubeUpToDate(ncube1.applicationID, ncube1.name)    // out of date
        assert mutableClient.isCubeUpToDate(ncube2.applicationID, ncube2.name)

        mutableClient.updateBranch(BRANCH1)                                       // pick up changes from HEAD
        assert mutableClient.isCubeUpToDate(ncube1.applicationID, ncube1.name)    // up to date

        assert mutableClient.isCubeUpToDate(BRANCH1.asHead(), ncube1.name)          // HEAD is always true
    }

    @Test
    void testHeartBeat()
    {
        Map health = call('heartBeat', [[:]]) as Map
        assert health
        Map stats = health.compareResults as Map
        assert stats.isEmpty()
    }

    @Test
    void testGetApplicationID()
    {
        System.setProperty("NCUBE_PARAMS", '{"branch":"jose"}')

        ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'ncube.test', '0.0.0', 'SNAPSHOT', 'jose')
        createCubeFromResource(appId, "sys.bootstrap.test.1.json")

        ApplicationID bootId = ncubeRuntime.getApplicationID(appId.tenant, appId.app, [env:null]) as ApplicationID
        assert bootId.tenant == 'NONE'
        assert bootId.app == 'ncube.test'
        assert bootId.version == '1.28.0'
        assert bootId.status == 'RELEASE'
        assert bootId.branch == 'HEAD'
    }
    
    @Test
    void testFetchJsonRevDiffs()
    {
        createCubeFromResource(BRANCH1, 'test.branch.1.json')
        List<NCubeInfoDto> cubes = ncubeRuntime.search(BRANCH1, 'TestBranch', null, null)
        assert cubes.size() == 1
        NCubeInfoDto origDto = cubes[0]
        long origId = Converter.convert(origDto.id, long.class) as long
        NCube foo = ncubeRuntime.getNCubeFromResource(BRANCH1, 'test.branch.2.json')
        mutableClient.updateCube(foo)
        List<NCubeInfoDto> cubes2 = ncubeRuntime.search(BRANCH1, 'TestBranch', null, null)
        assert cubes2.size() == 1
        NCubeInfoDto newDto = cubes2[0]
        long newId = Converter.convert(newDto.id, long.class) as long

        List<Delta> result = call('fetchJsonRevDiffs', [newId, origId]) as List
        assert result.size() == 4
    }

    @Test
    void testFetchJsonBranchDiffs()
    {
        createCubeFromResource(BRANCH1, 'test.branch.1.json')
        createCubeFromResource(BRANCH2, 'test.branch.2.json')
        List<NCubeInfoDto> cubes = ncubeRuntime.search(BRANCH1, 'TestBranch', null, null)
        assert cubes.size() == 1
        NCubeInfoDto origDto = cubes[0]
        List<NCubeInfoDto> cubes2 = ncubeRuntime.search(BRANCH2, 'TestBranch', null, null)
        assert cubes2.size() == 1
        NCubeInfoDto newDto = cubes2[0]

        List<Delta> result = call('fetchJsonBranchDiffs', [newDto, origDto]) as List
        assert result.size() == 4
    }

    @Test
    void testGetCellNoExecuteByCoordinate()
    {
        NCube cube = createCubeFromResource(BRANCH1, 'test.branch.1.json')
        String cubeCall = cube.getCellNoExecute([Code:0]) as String
        CellInfo apiCall = call('getCellNoExecuteByCoordinate', [BRANCH1, cube.name, [Code:0]]) as CellInfo
        assert cubeCall == apiCall.value

        try
        {
            call('getCellNoExecuteByCoordinate', [BRANCH1, cube.name, [Code:1]])
            fail()
        }
        catch(CoordinateNotFoundException e)
        {
            assertContainsIgnoreCase(e.message, 'not found on axis')
        }
    }

    @Test
    void testGetMenu()
    {
        try
        {
            Map menu = call('getMenu', [BRANCH1]) as Map
            assert menu.title != null
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'user code', 'cannot', 'executed', 'attempted', 'getMenu')
        }
    }

    @Test
    void testExecute()
    {
        createCubeFromResource(BRANCH1, 'test.execute.json')
        try
        {
            def result = call('execute', [BRANCH1, 'test.execute', 'plus', [value: 2.0d, term: 3.0d]])
            assert result != null
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'user code', 'cannot', 'executed', 'attempted', 'execute')
        }
    }

    @Test
    void testCheckPermissions()
    {
        String read = Action.READ.name()
        String update = Action.UPDATE.name()
        NCube cube = createCubeFromResource(BRANCH1, 'test.branch.1.json')
        Map perms = call('checkMultiplePermissions', [BRANCH1, cube.name, [read, update].toArray()]) as Map
        assertTrue perms[read] as Boolean
        assertTrue perms[update] as Boolean
    }

    @Test
    void testMergePullRequestWhenBranchDeleted()
    {
        NCube ncube = createCubeFromResource(BRANCH1, 'test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(BRANCH1, ncube.name, null, null)
        String prId = mutableClient.generatePullRequestHash(BRANCH1, dtos.toArray())

        mutableClient.deleteBranch(BRANCH1)

        try
        {
            call('mergePullRequest', [prId])
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'branch', 'request', 'obsolete', 'requested', 'applicationid')
            Map<String, Object> pr = mutableClient.pullRequests.first() as Map
            assert pr[PR_STATUS] == PR_OBSOLETE
        }
    }

    @Test
    void testMergePullRequestCubeNotExist()
    {
        NCube ncube = createCubeFromResource(BRANCH1, 'test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(BRANCH1, ncube.name, null, null)
        String prId = mutableClient.generatePullRequestHash(BRANCH1, dtos.toArray())

        mutableClient.deleteBranch(BRANCH1)
        createCubeFromResource(BRANCH1, 'test.branch.age.1.json')

        try
        {
            call('mergePullRequest', [prId])
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'cube', 'valid', 'request', 'obsolete', 'requested', 'applicationid')
            Map<String, Object> pr = mutableClient.pullRequests.first() as Map
            assert pr[PR_STATUS] == PR_OBSOLETE
        }
    }

    @Test
    void testMergePullRequestCubeChanged()
    {
        NCube ncube = createCubeFromResource(BRANCH1, 'test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(BRANCH1, ncube.name, null, null)
        String prId = mutableClient.generatePullRequestHash(BRANCH1, dtos.toArray())
        ncube.setCell('FOO', [Code : 10])
        mutableClient.updateCube(ncube)

        try
        {
            call('mergePullRequest', [prId])
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'cube', 'changed', 'request', 'obsolete', 'requested', 'applicationid')
            Map<String, Object> pr = mutableClient.pullRequests.first() as Map
            assert pr[PR_STATUS] == PR_OBSOLETE
        }
    }

    @Test
    void testMapReduce()
    {
        try
        {
            createCubeFromResource(BRANCH1, 'sys.classpath.tests.json')
            createCubeFromResource(BRANCH1, 'selectQueryTest.json')
            Map queryResult = call('mapReduce', [BRANCH1, 'Test.Select', 'key', 'query', "{ Map input -> input.foo == 'AL' }", [:], [:], [] as Set, [] as Set]) as Map

            assert queryResult.size() == 1

            Map row = queryResult['A']
            assert row['foo'] == 'AL'
            assert row['bar'] == 123
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'user code', 'cannot', 'executed', 'attempted', 'mapReduce')
        }
    }

    private static Object call(String methodName, List args)
    {
        if (NCubeAppContext.clientTest)
        {
            JsonHttpProxy proxy = NCubeAppContext.getBean('callableBean') as JsonHttpProxy
            proxy.invokeMethod('call', ['ncubeController', methodName, args])
        }
        else
        {
            NCubeController controller = NCubeAppContext.getBean(CONTROLLER_BEAN) as NCubeController
            controller.invokeMethod(methodName, args)
        }
    }
}
