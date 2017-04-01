package com.cedarsoftware.ncube

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
class TestCommit extends NCubeCleanupBaseTest
{
    private static ApplicationID appId = ApplicationID.testAppId
    private static ApplicationID sysApp = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'sys.app', '0.0.0', ReleaseStatus.SNAPSHOT.toString(), ApplicationID.HEAD)

    @Test
    void testGenerateCommitLink()
    {
        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(appId, ncube.name, null, null)
        String commitId = mutableClient.generateCommitLink(appId, dtos.toArray())
        assert commitId

        NCube commitCube = mutableClient.getCube(sysApp, "tx.${commitId}")
        assert 'open' == commitCube.getCell([property: 'status'])

        String appIdStr = commitCube.getCell([property: 'appId'])
        ApplicationID commitApp = ApplicationID.convert(appIdStr)
        assert appId == commitApp

        String cubeNames = commitCube.getCell([property: 'cubeNames'])
        List commitInfos = JsonReader.jsonToJava(cubeNames) as List
        assert 1 == commitInfos.size()
        Map commitInfo = commitInfos[0]
        assert commitInfo.name == 'TestBranch'
        assert commitInfo.changeType == 'C'
        assert commitInfo.id
        assert commitInfo.head == null
    }

    @Test
    void testCancelAndReopenCommit()
    {
        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(appId, ncube.name, null, null)
        String commitId = mutableClient.generateCommitLink(appId, dtos.toArray())
        assert commitId

        NCube commitCube = mutableClient.getCube(sysApp, "tx.${commitId}")
        assert 'open' == commitCube.getCell([property: 'status'])

        // cancel commit
        mutableClient.cancelCommit(commitId)
        commitCube = mutableClient.getCube(sysApp, "tx.${commitId}")
        assert 'closed cancelled' == commitCube.getCell([property: 'status'])

        // attempt to cancel a previously cancelled commit
        try
        {
            mutableClient.cancelCommit(commitId)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'request', 'closed', 'status', 'requested', 'applicationid')
        }

        // reopen a commit
        mutableClient.reopenCommit(commitId)
        commitCube = mutableClient.getCube(sysApp, "tx.${commitId}")
        assert 'open' == commitCube.getCell([property: 'status'])

        // attempt to reopen a previously reopened commit
        try
        {
            mutableClient.reopenCommit(commitId)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'unable', 'reopen', 'status', 'requested', 'applicationid')
        }
    }

    @Test
    void testGetCommits()
    {
        preloadCubes(ApplicationID.testAppId, 'test.branch.1.json', 'test.branch.age.1.json')
        List<NCubeInfoDto> branchDtos = mutableClient.search(appId, 'TestBranch', null, null)
        List<NCubeInfoDto> ageDtos = mutableClient.search(appId, 'TestAge', null, null)
        mutableClient.generateCommitLink(appId, branchDtos.toArray())
        mutableClient.generateCommitLink(appId, ageDtos.toArray())

        Object[] commits = mutableClient.commits
        assert 2 == commits.length
    }

    @Test
    void testHonorCommit()
    {
        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(appId, ncube.name, null, null)

        List<NCubeInfoDto> headDtos = mutableClient.search(appId.asHead(), ncube.name, null, null)
        assert 0 == headDtos.size()

        String commitId = mutableClient.generateCommitLink(appId, dtos.toArray())
        mutableClient.honorCommit(commitId)

        headDtos = mutableClient.search(appId.asHead(), ncube.name, null, null)
        assert 1 == headDtos.size()

        // attempt to commit a request that's already been committed
        try
        {
            mutableClient.honorCommit(commitId)
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'request', 'closed', 'status', 'requested', 'committed', 'applicationid')
        }
    }

    @Test
    void testInvalidId()
    {
        try
        {
            mutableClient.honorCommit('123')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'invalid', 'id')
        }

        try
        {
            mutableClient.cancelCommit('123')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'invalid', 'id')
        }

        try
        {
            mutableClient.reopenCommit('123')
            fail()
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'invalid', 'id')
        }
    }

    @Test
    void testHonorCommitByOtherUser()
    {
        String origUser = mutableClient.userId
        String otherUser = 'otherUser'

        // give other user branch permissions
        ApplicationID tripZero = appId.asVersion('0.0.0')
        NCube permissions = mutableClient.getCube(tripZero, SYS_BRANCH_PERMISSIONS)
        permissions.addColumn(AXIS_USER, otherUser)
        permissions.setCell(true, [(AXIS_USER): otherUser, (AXIS_RESOURCE): null])
        mutableClient.updateCube(permissions)

        NCube ncube = createCubeFromResource('test.branch.1.json')
        List<NCubeInfoDto> dtos = mutableClient.search(appId, ncube.name, null, null)
        String commitId = mutableClient.generateCommitLink(appId, dtos.toArray())

        // change over to other user
        NCubeManager manager = NCubeAppContext.getBean(MANAGER_BEAN) as NCubeManager
        manager.userId = otherUser
        mutableClient.honorCommit(commitId)

        List<NCubeInfoDto> headDtos = mutableClient.search(appId.asHead(), ncube.name, null, null)
        String notes = headDtos[0].notes
        assertContainsIgnoreCase(notes, otherUser, 'merged pull request', origUser, 'HEAD')
        manager.userId = origUser
    }
}