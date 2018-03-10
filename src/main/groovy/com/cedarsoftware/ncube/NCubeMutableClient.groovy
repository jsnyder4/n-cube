package com.cedarsoftware.ncube

import groovy.transform.CompileStatic

/**
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
interface NCubeMutableClient extends NCubeClient
{
    public static final String BRANCH_ADDS = 'adds'
    public static final String BRANCH_DELETES = 'deletes'
    public static final String BRANCH_UPDATES = 'updates'
    public static final String BRANCH_FASTFORWARDS = 'fastforwards'
    public static final String BRANCH_REJECTS = 'rejects'
    public static final String BRANCH_RESTORES = 'restores'

    String getUserId()

    NCubeInfoDto loadCubeRecord(ApplicationID appId, String cubeName, Map options)

    NCubeInfoDto loadCubeRecordById(long id, Map options)

    Boolean updateCube(NCube ncube)

    Boolean updateCube(ApplicationID appId, String cubeName, byte[] cubeBytes)

    void createCube(NCube ncube)

    void createCube(ApplicationID appId, String cubeName, byte[] cubeBytes)

    Boolean duplicate(ApplicationID oldAppId, ApplicationID newAppId, String oldName, String newName)

    Boolean assertPermissions(ApplicationID appId, String resource, Action action)

    Map checkMultiplePermissions(ApplicationID appId, String resource, Object[] actions)

    Boolean checkPermissions(ApplicationID appId, String resource, Action action)

    Boolean isSysAdmin()

    Boolean isAppAdmin(ApplicationID appId)

    String getAppLockedBy(ApplicationID appId)

    Boolean lockApp(ApplicationID appId, boolean shouldLock)

    Integer moveBranch(ApplicationID appId, String newSnapVer)

    Integer releaseVersion(ApplicationID appId)

    Integer releaseVersion(ApplicationID appId, String newSnapVer)

    Integer releaseCubes(ApplicationID appId)

    Integer releaseCubes(ApplicationID appId, String newSnapVer)

    Boolean restoreCubes(ApplicationID appId, Object[] cubeNames)

    List<NCubeInfoDto> getRevisionHistory(ApplicationID appId, String cubeName)

    List<NCubeInfoDto> getRevisionHistory(ApplicationID appId, String cubeName, boolean ignoreVersion)

    List<NCubeInfoDto> getCellAnnotation(ApplicationID appId, String cubeName, Set<Long> ids)

    List<NCubeInfoDto> getCellAnnotation(ApplicationID appId, String cubeName, Set<Long> ids, boolean ignoreVersion)

    Integer copyBranch(ApplicationID srcAppId, ApplicationID targetAppId)

    Integer copyBranch(ApplicationID srcAppId, ApplicationID targetAppId, boolean copyWithHistory)

    Integer getBranchCount(ApplicationID appId)

    Boolean deleteBranch(ApplicationID appId)

    Boolean deleteApp(ApplicationID appId)

    NCube mergeDeltas(ApplicationID appId, String cubeName, List<Delta> deltas)

    Boolean deleteCubes(ApplicationID appId, Object[] cubeNames)

    void changeVersionValue(ApplicationID appId, String newVersion)

    Boolean renameCube(ApplicationID appId, String oldName, String newName)

    void updateReferenceAxes(Object[] axisRefs)

    void updateAxisMetaProperties(ApplicationID appId, String cubeName, String axisName, Map<String, Object> newMetaProperties)

    Boolean updateNotes(ApplicationID appId, String cubeName, String notes)

    Set<String> getReferencesFrom(ApplicationID appId, String cubeName)

    List<AxisRef> getReferenceAxes(ApplicationID appId)

    List<NCubeInfoDto> getHeadChangesForBranch(ApplicationID appId)

    List<NCubeInfoDto> getBranchChangesForHead(ApplicationID appId)

    List<NCubeInfoDto> getBranchChangesForMyBranch(ApplicationID appId, String branch)

    Map<String, Object> updateBranch(ApplicationID appId)

    Map<String, Object> updateBranch(ApplicationID appId, Object[] cubeDtos)

    String generatePullRequestHash(ApplicationID appId, Object[] infoDtos)

    String generatePullRequestHash(ApplicationID appId, Object[] infoDtos, String notes)

    Map<String, Object> mergePullRequest(String prId)

    void obsoletePullRequest(String prId)

    void cancelPullRequest(String prId)

    void reopenPullRequest(String prId)

    Object[] getPullRequests()
    Object[] getPullRequests(Date startDate, Date endDate)
    Object[] getPullRequests(Date startDate, Date endDate, String prId)

    Map<String, Object> commitBranch(ApplicationID appId)

    Map<String, Object> commitBranch(ApplicationID appId, Object[] inputCubes)

    Map<String, Object> commitBranch(ApplicationID appId, Object[] inputCubes, String notes)

    Integer rollbackBranch(ApplicationID appId, Object[] names)

    Integer acceptMine(ApplicationID appId, Object[] cubeNames)

    Integer acceptTheirs(ApplicationID appId, Object[] cubeNames)

    Integer acceptTheirs(ApplicationID appId, Object[] cubeNames, String sourceBranch)

    Boolean isCubeUpToDate(ApplicationID appId, String cubeName)

    void createRefAxis(ApplicationID appId, String cubeName, String axisName, ApplicationID refAppId, String refCubeName, String refAxisName)

    NCubeInfoDto promoteRevision(long cubeId)

    List<Delta> fetchJsonRevDiffs(long newCubeId, long oldCubeId)

    List<Delta> fetchJsonBranchDiffs(NCubeInfoDto newInfoDto, NCubeInfoDto oldInfoDto)
}