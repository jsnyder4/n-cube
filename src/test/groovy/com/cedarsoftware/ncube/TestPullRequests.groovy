package com.cedarsoftware.ncube

import com.cedarsoftware.util.UniqueIdGenerator
import com.cedarsoftware.util.io.JsonReader
import groovy.transform.CompileStatic
import org.junit.Test

import static org.junit.Assert.fail

/**
 * NCubeController Tests
 *
 * @author John DeRegnaucourt (jdereg@gmail.com), Josh Snyder (joshsnyder@gmail.com)
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
class TestPullRequests extends NCubeCleanupBaseTest
{
    private static ApplicationID appId = ApplicationID.testAppId
    private static ApplicationID sysAppId = new ApplicationID(ApplicationID.DEFAULT_TENANT, NCubeConstants.SYS_APP, ApplicationID.SYS_BOOT_VERSION, ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)

    @Test
    void testGeneratePullRequestLink()
    {
        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(appId, ncube.name, null, null)
        String prId = mutableClient.generatePullRequestHash(appId, dtos.toArray())
        assert prId

        NCube prCube = mutableClient.getCube(sysAppId, "tx.${prId}")
        assert 'open' == prCube.getCell([property: 'status'])

        String appIdStr = prCube.getCell([property: 'appId'])
        ApplicationID prApp = ApplicationID.convert(appIdStr)
        assert appId == prApp

        String cubeNames = prCube.getCell([property: 'cubeNames'])
        List prInfos = JsonReader.jsonToJava(cubeNames) as List
        assert 1 == prInfos.size()
        Map prInfo = prInfos[0]
        assert prInfo.name == 'TestBranch'
        assert prInfo.changeType == 'C'
        assert prInfo.id
        assert prInfo.head == null

        try
        {
            mutableClient.generatePullRequestHash(appId, dtos.toArray())
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'request', 'exists')
        }
    }

    @Test
    void testGeneratePullRequestLinkWithoutPermissions()
    {
        createCubeFromResource('test.branch.1.json')

        NCube branchPermCube = mutableClient.getCube(appId.asVersion('0.0.0'), SYS_BRANCH_PERMISSIONS)
        branchPermCube.defaultCellValue = true
        mutableClient.updateCube(branchPermCube)

        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        String origUser = manager.userId
        manager.userId = UniqueIdGenerator.uniqueId as String
        try
        {
            mutableClient.commitBranch(appId)
            Object[] pullRequests = mutableClient.pullRequests
            assert 1 == pullRequests.length
        }
        catch (SecurityException ignore)
        {
            fail()
        }
        finally
        {
            manager.userId = origUser
        }
    }

    @Test
    void testCancelAndReopenPullRequest()
    {
        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(appId, ncube.name, null, null)
        String prId = mutableClient.generatePullRequestHash(appId, dtos.toArray())
        assert prId

        NCube prCube = mutableClient.getCube(sysAppId, "tx.${prId}")
        assert 'open' == prCube.getCell([property: 'status'])

        // cancel commit
        prCube = mutableClient.cancelPullRequest(prId)
        assert 'closed cancelled' == prCube.getCell([property: 'status'])

        // attempt to cancel a previously cancelled commit
        try
        {
            mutableClient.cancelPullRequest(prId)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'request', 'closed', 'status', 'requested', 'applicationid')
        }

        // reopen a commit
        prCube = mutableClient.reopenPullRequest(prId)
        assert 'open' == prCube.getCell([property: 'status'])

        // attempt to reopen a previously reopened commit
        try
        {
            mutableClient.reopenPullRequest(prId)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'unable', 'reopen', 'status', 'requested', 'applicationid')
        }
    }

    @Test
    void testGetPullRequests()
    {
        preloadCubes(ApplicationID.testAppId, 'test.branch.1.json', 'test.branch.age.1.json')
        List<NCubeInfoDto> branchDtos = mutableClient.search(appId, 'TestBranch', null, null)
        List<NCubeInfoDto> ageDtos = mutableClient.search(appId, 'TestAge', null, null)
        mutableClient.generatePullRequestHash(appId, branchDtos.toArray())
        mutableClient.generatePullRequestHash(appId, ageDtos.toArray())

        Object[] prs = mutableClient.getPullRequests(null, null)
        assert 2 == prs.length
    }

    @Test
    void testMergeOwnPullRequest()
    {
        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(appId, ncube.name, null, null)

        List<NCubeInfoDto> headDtos = mutableClient.search(appId.asHead(), ncube.name, null, null)
        assert headDtos.empty

        String prId = mutableClient.generatePullRequestHash(appId, dtos.toArray())
        mutableClient.mergePullRequest(prId)

        headDtos = mutableClient.search(appId.asHead(), ncube.name, null, null)
        assert 1 == headDtos.size()

        // attempt to commit a request that's already been committed
        try
        {
            mutableClient.mergePullRequest(prId)
            fail()
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'request', 'closed', 'status', 'requested', 'committed', 'applicationid')
        }
    }

    @Test
    void testMergePullRequestFromBranchWithoutPermissionsOnThatBranch()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        String origUser = manager.userId

        // set up PR from branch
        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(appId, ncube.name, null, null)
        List<NCubeInfoDto> headDtos = mutableClient.search(appId.asHead(), ncube.name, null, null)
        assert headDtos.empty
        String prId = mutableClient.generatePullRequestHash(appId, dtos.toArray())

        // test permissions for other user
        manager.userId = UniqueIdGenerator.uniqueId as String
        try
        {
            mutableClient.assertPermissions(appId, null, Action.COMMIT)
            manager.userId = origUser
            fail()
        }
        catch (SecurityException e)
        {
            assertContainsIgnoreCase(e.message, 'operation not performed')
        }

        try
        {
            // other user merges
            mutableClient.mergePullRequest(prId)

            headDtos = mutableClient.search(appId.asHead(), ncube.name, null, null)
            assert 1 == headDtos.size()
        }
        catch (SecurityException ignore)
        {
            fail()
        }
        finally
        {
            manager.userId = origUser
        }
    }

    @Test
    void testInvalidId()
    {
        try
        {
            mutableClient.mergePullRequest('123')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'invalid', 'id')
        }

        try
        {
            mutableClient.cancelPullRequest('123')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'invalid', 'id')
        }

        try
        {
            mutableClient.reopenPullRequest('123')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'invalid', 'id')
        }
    }

    @Test
    void testMergePullRequestByOtherUser()
    {
        if (NCubeAppContext.clientTest)
        {
            return
        }
        String origUser = mutableClient.userId
        String otherUser = UniqueIdGenerator.uniqueId as String

        // give other user branch permissions
        ApplicationID tripZero = appId.asVersion('0.0.0')
        NCube permissions = mutableClient.getCube(tripZero, SYS_BRANCH_PERMISSIONS)
        permissions.addColumn(AXIS_USER, otherUser)
        permissions.setCell(true, [(AXIS_USER): otherUser, (AXIS_RESOURCE): null])
        mutableClient.updateCube(permissions)

        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(appId, ncube.name, null, null)
        String prId = mutableClient.generatePullRequestHash(appId, dtos.toArray())

        // change over to other user
        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        manager.userId = otherUser
        mutableClient.mergePullRequest(prId)

        List<NCubeInfoDto> headDtos = mutableClient.search(appId.asHead(), ncube.name, null, null)
        String notes = headDtos[0].notes
        assertContainsIgnoreCase(notes, otherUser, 'merged pull request', origUser, 'HEAD')
        manager.userId = origUser
    }
}