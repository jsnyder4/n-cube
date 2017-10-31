package com.cedarsoftware.ncube

import groovy.transform.CompileStatic

/**
 * Class used to carry the NCube meta-information
 * to the client.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
interface NCubePersister extends NCubeReadOnlyPersister
{
    // Basic CRUD operations
    void updateCube(NCube cube, String username)
    void createCube(NCube cube, String username)
    boolean renameCube(ApplicationID appId, String oldName, String newName, String username)
    boolean duplicateCube(ApplicationID oldAppId, ApplicationID newAppId, String oldName, String newName, String username)
    boolean deleteCubes(ApplicationID appId, Object[] cubeNames, boolean allowDelete, String username)
    boolean restoreCubes(ApplicationID appId, Object[] names, String username)

    // Branch Management
    List<NCubeInfoDto> commitCubes(ApplicationID appId, Object[] cubeIds, String username, String requestUser, String txId, String notes)
    int rollbackCubes(ApplicationID appId, Object[] names, String username)
    List<NCubeInfoDto> pullToBranch(ApplicationID appId, Object[] cubeIds, String username, long txId)
    boolean mergeAcceptTheirs(ApplicationID appId, String cubeName, String sourceBranch, String username)
    boolean mergeAcceptMine(ApplicationID appId, String cubeName, String username)
    NCubeInfoDto commitMergedCubeToHead(ApplicationID appId, NCube cube, String username, String requestUser, String txId, String notes)
    NCubeInfoDto commitMergedCubeToBranch(ApplicationID appId, NCube cube, String headSha1, String username, long txId)
    boolean updateBranchCubeHeadSha1(Long cubeId, String branchSha1, String headSha1, String username)
    int copyBranch(ApplicationID srcAppId, ApplicationID targetAppId, String username)
    int copyBranchWithHistory(ApplicationID srcAppId, ApplicationID targetAppId, String username)
    boolean deleteBranch(ApplicationID appId, String username)
    boolean deleteApp(ApplicationID appId, String username)
    boolean doCubesExist(ApplicationID appId, boolean ignoreStatus, String methodName, String username)

    // Release Management
    int changeVersionValue(ApplicationID appId, String newVersion, String username)
    int moveBranch(ApplicationID appId, String newSnapVer, String username)
    int releaseCubes(ApplicationID appId, String username)

    // Testing
    boolean updateNotes(ApplicationID appId, String cubeName, String notes, String username)
    void clearTestDatabase(String username)
}
