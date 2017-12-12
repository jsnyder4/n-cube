package com.cedarsoftware.ncube

import com.marklogic.client.DatabaseClient
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.sql.Connection

/**
 * Adapter for MarkLogic document storage
 *
 * @author John DeRegnaucourt (jdereg@gmail.com), Josh Snyder (joshsnyder@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */

@CompileStatic
class MarkLogicPersisterAdapter implements NCubePersister
{
    private static final Logger LOG = LoggerFactory.getLogger(MarkLogicPersisterAdapter.class)
    private MarkLogicPersister persister

    void setDatabaseClient(DatabaseClient client)
    {
        this.persister = new MarkLogicPersister(client)
    }

    Object mlOperation(Closure closure, String msg, String username = 'no user set')
    {
        long start = System.nanoTime()
        Object ret = closure()
        long end = System.nanoTime()
        long time = Math.round((end - start) / 1000000.0d)
        if (time > 1000)
        {
            LOG.info("    [SLOW ML - ${time} ms] [${username}] ${msg}")
        }
        else if (LOG.debugEnabled)
        {
            LOG.debug("    [ML - ${time} ms] [${username}] ${msg}")
        }
        return ret
    }


    void updateCube(NCube cube, String username)
    {
        mlOperation({ persister.updateCube(cube, username) },
                "updateCube(${cube.applicationID.cacheKey(cube.name)})", username)
    }

    void createCube(NCube cube, String username)
    {
        mlOperation({ persister.createCube(cube, username) },
                "createCube(${cube.applicationID.cacheKey(cube.name)})", username)
    }

    boolean renameCube(ApplicationID appId, String oldName, String newName, String username)
    {
        return false
    }

    boolean duplicateCube(ApplicationID oldAppId, ApplicationID newAppId, String oldName, String newName, String username)
    {
        return false
    }

    boolean deleteCubes(ApplicationID appId, Object[] cubeNames, boolean allowDelete, String username)
    {
        return (boolean) mlOperation({ persister.deleteCubes(appId, cubeNames, allowDelete, username) },
                "deleteCubes(${appId})", username)
    }

    boolean restoreCubes(ApplicationID appId, Object[] names, String username)
    {
        return false
    }

    List<NCubeInfoDto> commitCubes(ApplicationID appId, Object[] cubeIds, String username, String requestUser, String txId, String notes)
    {
        return (List<NCubeInfoDto>) mlOperation({ persister.commitCubes(appId, cubeIds, username, requestUser, txId, notes) },
                "commitCubes(${appId}, txId=${txId})", username)
    }

    int rollbackCubes(ApplicationID appId, Object[] names, String username)
    {
        return 0
    }

    List<NCubeInfoDto> pullToBranch(ApplicationID appId, Object[] cubeIds, String username, long txId)
    {
        return null
    }

    boolean mergeAcceptTheirs(ApplicationID appId, String cubeName, String sourceBranch, String username)
    {
        return false
    }

    boolean mergeAcceptMine(ApplicationID appId, String cubeName, String username)
    {
        return false
    }

    NCubeInfoDto commitMergedCubeToHead(ApplicationID appId, NCube cube, String username, String requestUser, String txId, String notes)
    {
        return null
    }

    NCubeInfoDto commitMergedCubeToBranch(ApplicationID appId, NCube cube, String headSha1, String username, long txId)
    {
        return null
    }

    boolean updateBranchCubeHeadSha1(Long cubeId, String branchSha1, String headSha1, String username)
    {
        return false
    }

    int copyBranch(ApplicationID srcAppId, ApplicationID targetAppId, String username)
    {
        return (int) mlOperation({ persister.copyBranch(srcAppId, targetAppId, username) },
                "copyBranch(${srcAppId} -> ${targetAppId})", username)
    }

    int copyBranchWithHistory(ApplicationID srcAppId, ApplicationID targetAppId, String username)
    {
        return (int) mlOperation({ persister.copyBranchWithHistory(srcAppId, targetAppId, username) },
                "copyBranchWithHistory(${srcAppId} -> ${targetAppId})", username)
    }

    boolean deleteBranch(ApplicationID appId, String username)
    {
        return false
    }

    boolean deleteApp(ApplicationID appId, String username)
    {
        return false
    }

    boolean doCubesExist(ApplicationID appId, boolean ignoreStatus, String methodName, String username)
    {
        return (boolean) mlOperation({ persister.doCubesExist(appId, ignoreStatus, methodName) },
                "doCubesExist(${appId})", username)
    }

    int changeVersionValue(ApplicationID appId, String newVersion, String username)
    {
        return 0
    }

    int moveBranch(ApplicationID appId, String newSnapVer, String username)
    {
        return 0
    }

    int releaseCubes(ApplicationID appId, String username)
    {
        return 0
    }

    boolean updateNotes(ApplicationID appId, String cubeName, String notes, String username)
    {
        return false
    }

    void clearTestDatabase(String username)
    {
        mlOperation({ persister.clearTestDatabase() },
                "clearTestDatabase()", username)
    }

    List<NCubeInfoDto> search(ApplicationID appId, String cubeNamePattern, String searchValue, Map options, String username)
    {
        return (List<NCubeInfoDto>) mlOperation({ persister.search(appId, cubeNamePattern, searchValue, options) },
                "search(${appId}, ${cubeNamePattern}, ${searchValue})", username)
    }

    NCube loadCubeBySha1(ApplicationID appId, String name, String sha1, String username)
    {
        return null
    }

    NCubeInfoDto loadCubeRecordById(long id, Map options, String username)
    {
        return (NCubeInfoDto) mlOperation({ persister.loadCubeRecordById(id, options) },
                "loadCubeRecordById(${id})", username)
    }

    List<String> getAppNames(String tenant, String username)
    {
        return (List<String>) mlOperation({ persister.getAppNames(tenant) },
                "getAppNames(${tenant})", username)
    }

    Map<String, List<String>> getVersions(String tenant, String app, String username)
    {
        return (Map<String, List<String>>) mlOperation({ persister.getVersions(tenant, app) },
                "getVersions(${tenant}, ${app})", username)
    }

    List<NCubeInfoDto> getRevisions(ApplicationID appId, String cubeName, boolean ignoreVersion, String username)
    {
        return null
    }

    Set<String> getBranches(ApplicationID appId, String username)
    {
        return (Set<String>) mlOperation({ persister.getBranches(appId) },
                "getBranches(${appId})", username)
    }

    Map getAppTestData(ApplicationID appId, String username)
    {
        return null
    }

    String getTestData(ApplicationID appId, String cubeName, String username)
    {
        return null
    }

    String getTestData(Long cubeId, String user)
    {
        return null
    }

}
