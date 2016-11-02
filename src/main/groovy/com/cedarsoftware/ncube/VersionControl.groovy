package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.BranchMergeException
import com.cedarsoftware.util.CaseInsensitiveMap
import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.UniqueIdGenerator
import groovy.transform.CompileStatic

/**
 * This class provides version control for persisted NCubes.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
class VersionControl
{
    public static final String BRANCH_ADDS = 'adds'
    public static final String BRANCH_DELETES = 'deletes'
    public static final String BRANCH_UPDATES = 'updates'
    public static final String BRANCH_FASTFORWARDS = 'fastforwards'
    public static final String BRANCH_REJECTS = 'rejects'
    public static final String BRANCH_RESTORES = 'restores'

    /**
     * Update a branch from the HEAD.  Changes from the HEAD are merged into the
     * supplied branch.  If the merge cannot be done perfectly, an exception is
     * thrown indicating the cubes that are in conflict.
     */
    static List<NCubeInfoDto> getHeadChangesForBranch(ApplicationID appId)
    {
        NCubeManager.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        NCubeManager.assertNotLockBlocked(appId)
        NCubeManager.assertPermissions(appId, null, Action.READ)

        ApplicationID headAppId = appId.asHead()

        List<NCubeInfoDto> records = NCubeManager.search(appId, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):false])
        if (records.empty)
        {
            return []
        }
        Map<String, NCubeInfoDto> branchRecordMap = new CaseInsensitiveMap<>()

        for (NCubeInfoDto info : records)
        {
            branchRecordMap[info.name] = info
        }

        List<NCubeInfoDto> headRecords = NCubeManager.search(headAppId, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):false])
        List<NCubeInfoDto> cubeDiffs = []

        for (NCubeInfoDto head : headRecords)
        {
            head.branch = appId.branch  // using HEAD's DTO as return value, therefore setting the branch to the passed in AppId's branch
            NCubeInfoDto info = branchRecordMap[head.name]
            long headRev = (long) Converter.convert(head.revision, long.class)

            if (info == null)
            {   // HEAD has cube that branch does not have
                head.changeType = headRev < 0 ? ChangeType.DELETED.code : ChangeType.CREATED.code
                cubeDiffs.add(head)
                continue
            }

            long infoRev = (long) Converter.convert(info.revision, long.class)
            boolean activeStatusMatches = (infoRev < 0) == (headRev < 0)
            boolean branchHeadSha1MatchesHeadSha1 = StringUtilities.equalsIgnoreCase(info.headSha1, head.sha1)
            boolean branchSha1MatchesHeadSha1 = StringUtilities.equalsIgnoreCase(info.sha1, head.sha1)

            // Did branch cube change?
            if (!info.changed)
            {   // No change on branch cube
                if (activeStatusMatches)
                {
                    if (!branchHeadSha1MatchesHeadSha1)
                    {   // HEAD cube changed, branch cube did not
                        head.changeType = ChangeType.UPDATED.code
                        cubeDiffs.add(head)
                    }
                }
                else
                {   // 1. The active/deleted statuses don't match, or
                    // 2. HEAD has different SHA1 but branch cube did not change, safe to update branch (fast forward)
                    // In both cases, the cube was marked NOT changed in the branch, so safe to update.
                    if (headRev < 0)
                    {
                        head.changeType = ChangeType.DELETED.code
                    }
                    else
                    {
                        head.changeType = ChangeType.RESTORED.code
                    }
                    cubeDiffs.add(head)
                }
            }
            else if (branchSha1MatchesHeadSha1)
            {   // If branch cube is 'changed' but has same SHA-1 as head cube (same change in branch as HEAD)
                if (branchHeadSha1MatchesHeadSha1)
                {   // no show - branch cube deleted or restored - will show on commit
                }
                else
                {   // branch cube out of sync
                    if (activeStatusMatches)
                    {
                        head.changeType = ChangeType.FASTFORWARD.code
                    }
                    else
                    {
                        head.changeType = ChangeType.CONFLICT.code
                    }
                    cubeDiffs.add(head)
                }
            }
            else
            {   // branch cube has content change
                if (branchHeadSha1MatchesHeadSha1)
                {   // head cube is still as it was when branch cube was created
                    if (activeStatusMatches)
                    {   // no show - in sync with head but branch cube has changed
                    }
                    else
                    {
                        head.changeType = ChangeType.CONFLICT.code
                        cubeDiffs.add(head)
                    }
                }
                else
                {   // Cube is different than HEAD, AND it is not based on same HEAD cube, but it could be merge-able.
                    NCube cube = mergeCubesIfPossible(info, head, true)
                    if (cube == null)
                    {
                        head.changeType = ChangeType.CONFLICT.code
                    }
                    else
                    {
                        if (activeStatusMatches)
                        {
                            if (StringUtilities.equalsIgnoreCase(cube.sha1(), info.sha1))
                            {   // NOTE: could be different category
                                head.changeType = ChangeType.FASTFORWARD.code
                            }
                            else
                            {
                                head.changeType = ChangeType.UPDATED.code
                            }
                        }
                        else
                        {
                            head.changeType = ChangeType.CONFLICT.code
                        }
                    }
                    cubeDiffs.add(head)
                }
            }
        }

        return cubeDiffs
    }

    /**
     * Get a list of NCubeInfoDto's that represent the n-cubes that have been made to
     * this branch.  This is the source of n-cubes for the 'Commit' and 'Rollback' lists.
     */
    static List<NCubeInfoDto> getBranchChangesForHead(ApplicationID appId)
    {
        NCubeManager.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        NCubeManager.assertNotLockBlocked(appId)
        NCubeManager.assertPermissions(appId, null, Action.READ)

        ApplicationID headAppId = appId.asHead()
        Map<String, NCubeInfoDto> headMap = new CaseInsensitiveMap<>()

        List<NCubeInfoDto> branchList = NCubeManager.search(appId, null, null, [(NCubeManager.SEARCH_CHANGED_RECORDS_ONLY):true])
        List<NCubeInfoDto> headList = NCubeManager.search(headAppId, null, null, null)   // active and deleted
        List<NCubeInfoDto> list = []

        //  build map of head objects for reference.
        for (NCubeInfoDto headCube : headList)
        {
            headMap[headCube.name] = headCube
        }

        // Loop through changed (added, deleted, created, restored, updated) records
        for (NCubeInfoDto updateCube : branchList)
        {
            long revision = (long) Converter.convert(updateCube.revision, long.class)
            NCubeInfoDto head = headMap[updateCube.name]

            if (head == null)
            {
                if (revision >= 0)
                {
                    updateCube.changeType = ChangeType.CREATED.code
                    list.add(updateCube)
                }
                continue
            }

            long headRev = (long) Converter.convert(head.revision, long.class)
            boolean activeStatusMatches = (revision < 0) == (headRev < 0)
            boolean branchSha1MatchesHeadSha1 = StringUtilities.equalsIgnoreCase(updateCube.sha1, head.sha1)
            boolean branchHeadSha1MatchesHeadSha1 = StringUtilities.equalsIgnoreCase(updateCube.headSha1, head.sha1)

            if (branchHeadSha1MatchesHeadSha1)
            {   // branch in sync with HEAD (not considering delete/restore status)
                if (branchSha1MatchesHeadSha1)
                {   // only net change could be revision deleted or restored.  check HEAD.
                    if (!activeStatusMatches)
                    {   // deleted or restored in branch
                        updateCube.changeType = revision < 0 ? ChangeType.DELETED.code : ChangeType.RESTORED.code
                        list.add(updateCube)
                    }
                }
                else
                {   // branch has content change
                    if (activeStatusMatches)
                    {   // standard update case
                        updateCube.changeType = ChangeType.UPDATED.code
                    }
                    else
                    {
                        updateCube.changeType = revision < 0 ? ChangeType.DELETED.code : ChangeType.UPDATED.code
                    }
                    list.add(updateCube)
                }
            }
            else
            {   // branch cube not in sync with HEAD
                NCube cube = mergeCubesIfPossible(updateCube, head, false)
                if (cube == null)
                {
                    updateCube.changeType = ChangeType.CONFLICT.code
                    list.add(updateCube)
                }
                else
                {   // merge-able
                    if (activeStatusMatches)
                    {
                        if (StringUtilities.equalsIgnoreCase(cube.sha1(), head.sha1))
                        {   // no show (fast-forward)
                        }
                        else
                        {
                            updateCube.changeType = ChangeType.UPDATED.code
                            list.add(updateCube)
                        }
                    }
                    else
                    {
                        updateCube.changeType = ChangeType.CONFLICT.code
                        list.add(updateCube)
                    }
                }
            }
        }

        return list
    }

    /**
     * Update a branch from the HEAD.  Changes from the HEAD are merged into the
     * supplied branch.  If the merge cannot be done perfectly, an exception is
     * thrown indicating the cubes that are in conflict.
     */
    static List<NCubeInfoDto> getBranchChangesForMyBranch(ApplicationID appId, String branch)
    {
        ApplicationID branchAppId = appId.asBranch(branch)
        NCubeManager.validateAppId(appId)
        NCubeManager.validateAppId(branchAppId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        NCubeManager.assertNotLockBlocked(appId)
        NCubeManager.assertPermissions(appId, null, Action.READ)
        NCubeManager.assertPermissions(branchAppId, null, Action.READ)

        List<NCubeInfoDto> records = NCubeManager.search(appId, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):false])
        Map<String, NCubeInfoDto> branchRecordMap = new CaseInsensitiveMap<>()

        for (NCubeInfoDto info : records)
        {
            branchRecordMap[info.name] = info
        }

        List<NCubeInfoDto> otherBranchRecords = NCubeManager.search(branchAppId, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):false])
        if (otherBranchRecords.empty)
        {
            return []
        }
        List<NCubeInfoDto> cubeDiffs = []

        for (NCubeInfoDto otherBranchCube : otherBranchRecords)
        {
            otherBranchCube.branch = appId.branch  // using other branch's DTO as return value, therefore setting the branch to the passed in AppId's branch
            NCubeInfoDto info = branchRecordMap[otherBranchCube.name]
            long otherBranchCubeRev = (long) Converter.convert(otherBranchCube.revision, long.class)

            if (info == null)
            {   // Other branch has cube that my branch does not have
                if (otherBranchCubeRev >= 0)
                {
                    otherBranchCube.changeType = ChangeType.CREATED.code
                    cubeDiffs.add(otherBranchCube)
                }
                else
                {
                    // Don't show a cube that is deleted in other's branch but I don't have.
                }
                continue
            }

            long infoRev = (long) Converter.convert(info.revision, long.class)
            boolean activeStatusMatches = (infoRev < 0) == (otherBranchCubeRev < 0)
            boolean myBranchSha1MatchesOtherBranchSha1 = StringUtilities.equalsIgnoreCase(info.sha1, otherBranchCube.sha1)

            // No change on my branch cube
            if (activeStatusMatches)
            {
                if (infoRev >= 0)
                {
                    if (myBranchSha1MatchesOtherBranchSha1)
                    {
                        // skip - the cubes are the same
                    }
                    else
                    {   // Cubes are different, mark as UPDATE
                        otherBranchCube.changeType = ChangeType.UPDATED.code
                        cubeDiffs.add(otherBranchCube)
                    }
                }
                else
                {
                    // skip - you both have it deleted
                }
            }
            else
            {   // 1. The active/deleted statuses don't match, or
                // 2. HEAD has different SHA1 but branch cube did not change, safe to update branch (fast forward)
                // In both cases, the cube was marked NOT changed in the branch, so safe to update.
                if (otherBranchCubeRev < 0)
                {
                    otherBranchCube.changeType = ChangeType.DELETED.code
                }
                else
                {
                    otherBranchCube.changeType = ChangeType.RESTORED.code
                }
                cubeDiffs.add(otherBranchCube)
            }
        }

        return cubeDiffs
    }

    /**
     * Update the branch represented by the passed in ApplicationID (appId), with the cubes to be updated
     * identified by cubeNames, and the sourceBranch is the branch (could be HEAD) source of the cubes
     * from which to update.
     * @param appId ApplicationID of the destination branch
     * @param cubeNames [optional] Object[] of NCubeInfoDto's to limit the update to. Only n-cubes matching these
     * will be updated.  This can be null, in which case all possible updates will be performed.  If not supplied, this
     * will default to null.
     * @param sourceBranch [optional] String name of branch to update from.  This is often 'HEAD' as HEAD is the most
     * common branch from which to pull updates.  However, it could be the name of another user's branch,
     * in which case the updates will be pulled from that branch (and optionally filtered by cubeNames).  If not
     * supplied, this defaults to HEAD.
     * <br>
     * Update a branch from the HEAD.  Changes from the HEAD are merged into the
     * supplied branch.  The return Map contains a Map with String keys for
     * 'adds'      ==> added count<br>
     * 'deletes'   ==> deleted count<br>
     * 'updates'   ==> updated count<br>
     * 'merges'    ==> merged count<br>
     * 'conflicts' ==> Map[cube name, subMap]<br>
     * &nbsp;&nbsp;subMap maps <br>
     * 'message'  --> Merge conflict error message<br>
     * 'sha1'     --> SHA-1 of destination branch n-cube<br>
     * 'headSha1' --> SHA-1 of HEAD (or source branch n-cube being merged from)<br>
     * 'diff'     --> List[Delta's]
     */
    static Map<String, Object> updateBranch(ApplicationID appId, Object[] cubeDtos = null)
    {
        if (cubeDtos != null && cubeDtos.length == 0)
        {
            throw new IllegalArgumentException('Nothing selected for update.')
        }
        NCubeManager.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        NCubeManager.assertNotLockBlocked(appId)
        NCubeManager.assertPermissions(appId, null, Action.UPDATE)

        List<NCubeInfoDto> adds = []
        List<NCubeInfoDto> deletes = []
        List<NCubeInfoDto> updates = []
        List<NCubeInfoDto> merges = []
        List<NCubeInfoDto> restores = []
        List<NCubeInfoDto> fastforwards = []
        List<NCubeInfoDto> rejects = []
        List<NCubeInfoDto> finalUpdates
        long txId = UniqueIdGenerator.uniqueId
        Map<String, NCubeInfoDto> newDtos = new CaseInsensitiveMap<>()
        List<NCubeInfoDto> newDtoList = getHeadChangesForBranch(appId)
        List<NCubeInfoDto> cubesToUpdate = []
        if (cubeDtos == null)
        {
            cubesToUpdate = newDtoList
        }
        else
        {
            newDtoList.each { newDtos[it.name] = it }
            (cubeDtos.toList() as List<NCubeInfoDto>).each { NCubeInfoDto oldDto ->
                // make reject list by comparing with refresh records
                NCubeInfoDto newDto = newDtos[oldDto.name]
                if (newDto == null || newDto.id != oldDto.id)
                {   // if in oldDtos but no in newDtos OR if something happened while we were away
                    rejects.add(oldDto)
                }
                else
                {
                    if (oldDto.changeType == null)
                    {
                        oldDto.changeType = newDto.changeType
                    }
                    cubesToUpdate.add(oldDto)
                }
            }
        }
        for (NCubeInfoDto updateCube : cubesToUpdate)
        {
            switch(updateCube.changeType)
            {
                case ChangeType.CREATED.code:
                    adds.add(updateCube)
                    break
                case ChangeType.RESTORED.code:
                    restores.add(updateCube)
                    break
                case ChangeType.UPDATED.code:
                    NCubeInfoDto branchCube = getCubeInfo(appId, updateCube)
                    if (branchCube.changed)
                    {   // Cube is different than HEAD, AND it is not based on same HEAD cube, but it could be merge-able.
                        NCube cube1 = mergeCubesIfPossible(branchCube, updateCube, true)
                        if (cube1 != null)
                        {
                            NCubeInfoDto mergedDto = persister.commitMergedCubeToBranch(appId, cube1, updateCube.sha1, NCubeManager.userId, txId)
                            merges.add(mergedDto)
                        }
                    }
                    else
                    {
                        updates.add(updateCube)
                    }
                    break
                case ChangeType.DELETED.code:
                    deletes.add(updateCube)
                    break
                case ChangeType.FASTFORWARD.code:
                    // Fast-Forward branch
                    // Update HEAD SHA-1 on branch directly (no need to insert)
                    NCubeInfoDto branchCube = getCubeInfo(appId, updateCube)
                    persister.updateBranchCubeHeadSha1((Long) Converter.convert(branchCube.id, Long.class), updateCube.sha1)
                    fastforwards.add(updateCube)
                    break
                case ChangeType.CONFLICT.code:
                    rejects.add(updateCube)
                    break
                default:
                    throw new IllegalArgumentException('No change type on passed in cube to update.')
            }
        }
        NCubeManager.clearCache(appId)
        finalUpdates = persister.pullToBranch(appId, buildIdList(updates), NCubeManager.userId, txId)
        finalUpdates.addAll(merges)
        Map<String, Object> ret = [:]
        ret[BRANCH_ADDS] = persister.pullToBranch(appId, buildIdList(adds), NCubeManager.userId, txId)
        ret[BRANCH_DELETES] = persister.pullToBranch(appId, buildIdList(deletes), NCubeManager.userId, txId)
        ret[BRANCH_UPDATES] = finalUpdates
        ret[BRANCH_RESTORES] = persister.pullToBranch(appId, buildIdList(restores), NCubeManager.userId, txId)
        ret[BRANCH_FASTFORWARDS] = fastforwards
        ret[BRANCH_REJECTS] = rejects
        return ret
    }

    /**
     * Commit the passed in changed cube records identified by NCubeInfoDtos.
     * @return array of NCubeInfoDtos that are to be committed.
     */
    static Map<String, Object> commitBranch(ApplicationID appId, Object[] inputCubes = null)
    {
        NCubeManager.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        NCubeManager.assertNotLockBlocked(appId)
        NCubeManager.assertPermissions(appId, null, Action.COMMIT)

        List<NCubeInfoDto> adds = []
        List<NCubeInfoDto> deletes = []
        List<NCubeInfoDto> updates = []
        List<NCubeInfoDto> merges = []
        List<NCubeInfoDto> restores = []
        List<NCubeInfoDto> rejects = []
        List<NCubeInfoDto> finalUpdates

        long txId = UniqueIdGenerator.uniqueId
        Map<String, NCubeInfoDto> newDtos = new CaseInsensitiveMap<>()
        List<NCubeInfoDto> newDtoList = getBranchChangesForHead(appId)
        List<NCubeInfoDto> cubesToUpdate = []

        if (inputCubes == null)
        {
            cubesToUpdate = newDtoList
        }
        else
        {
            newDtoList.each { newDtos[it.name] = it }
            (inputCubes.toList() as List<NCubeInfoDto>).each { NCubeInfoDto oldDto ->
                // make reject list by comparing with refresh records
                NCubeInfoDto newDto = newDtos[oldDto.name]
                if (newDto == null || newDto.id != oldDto.id)
                {   // if in oldDtos but no in newDtos OR if something happened while we were away
                    rejects.add(oldDto)
                }
                else
                {
                    cubesToUpdate.add(newDto)
                }
            }
        }

        for (NCubeInfoDto updateCube : cubesToUpdate)
        {
            switch(updateCube.changeType)
            {
                case ChangeType.CREATED.code:
                    adds.add(updateCube)
                    break
                case ChangeType.RESTORED.code:
                    restores.add(updateCube)
                    break
                case ChangeType.UPDATED.code:
                    NCubeInfoDto headCube = getCubeInfo(appId.asHead(), updateCube)
                    if (StringUtilities.equalsIgnoreCase(updateCube.headSha1, headCube.sha1))
                    {
                        if (!StringUtilities.equalsIgnoreCase(updateCube.sha1, headCube.sha1))
                        {   // basic update case
                            updates.add(updateCube)
                        }
                        else
                        {
                            rejects.add(updateCube)
                        }
                    }
                    else
                    {
                        NCubeInfoDto branchCube = getCubeInfo(appId, updateCube)
                        NCube cube = mergeCubesIfPossible(branchCube, headCube, false)
                        if (cube != null)
                        {
                            NCubeInfoDto mergedDto = persister.commitMergedCubeToHead(appId, cube, userId, txId)
                            merges.add(mergedDto)
                        }
                    }
                    break
                case ChangeType.DELETED.code:
                    deletes.add(updateCube)
                    break
                case ChangeType.CONFLICT.code:
                    rejects.add(updateCube)
                    break
                default:
                    throw new IllegalArgumentException('No change type on passed in cube to commit.')
            }
        }

        NCubeManager.clearCache(appId)
        NCubeManager.clearCache(appId.asHead())

        finalUpdates = persister.commitCubes(appId, buildIdList(updates), userId, txId)
        finalUpdates.addAll(merges)
        Map<String, Object> ret = [:]
        ret[BRANCH_ADDS] = persister.commitCubes(appId, buildIdList(adds), userId, txId)
        ret[BRANCH_DELETES] = persister.commitCubes(appId, buildIdList(deletes), userId, txId)
        ret[BRANCH_UPDATES] = finalUpdates
        ret[BRANCH_RESTORES] = persister.commitCubes(appId, buildIdList(restores), userId, txId)
        ret[BRANCH_REJECTS] = rejects

        if (!rejects.isEmpty())
        {
            int rejectSize = rejects.size()
            throw new BranchMergeException("Unable to commit ${rejectSize} ${rejectSize == 1 ? 'cube' : 'cubes'}.", ret)
        }
        return ret
    }

    /**
     * Rollback the passed in list of n-cubes.  Each one will be returned to the state is was
     * when the branch was created.  This is an insert cube (maintaining revision history) for
     * each cube passed in.
     */
    static int rollbackCubes(ApplicationID appId, Object[] names)
    {
        NCubeManager.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        NCubeManager.assertNotLockBlocked(appId)

        for (Object name : names)
        {
            String cubeName = name as String
            NCubeManager.assertPermissions(appId, cubeName, Action.UPDATE)
        }
        int count = persister.rollbackCubes(appId, names, userId)
        NCubeManager.clearCache(appId)
        return count
    }

    /**
     * Forcefully merge the branch cubes passed in, into head, making them the latest revision in head.
     * This API is typically only called after verification from user that they understand there is a conflict,
     * and the user is choosing to take the cube in their branch as the next revision, ignoring the content
     * in the cube with the same name in the HEAD branch.
     * @param appId ApplicationID for the passed in cube names
     * @param cubeNames Object[] of String names of n-cube
     * @return int the number of n-cubes merged.
     */
    static int mergeAcceptMine(ApplicationID appId, Object[] cubeNames)
    {
        NCubeManager.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        int count = 0

        NCubeManager.assertNotLockBlocked(appId)
        for (Object cubeName : cubeNames)
        {
            String cubeNameStr = cubeName as String
            NCubeManager.assertPermissions(appId, cubeNameStr, Action.UPDATE)
            persister.mergeAcceptMine(appId, cubeNameStr, userId)
            NCubeManager.removeCachedCube(appId, cubeNameStr)
            count++
        }
        return count
    }

    /**
     * Forcefully update the branch cubes with the cube with the same name from the HEAD branch.  The
     * branch is specified on the ApplicationID.  This API is typically only be called after verification
     * from the user that they understand there is a conflict, but they are choosing to overwrite their
     * changes in their branch with the cube with the same name, from HEAD.
     * @param appId ApplicationID for the passed in cube names
     * @param cubeNames Object[] of String names of n-cube
     * @param Object[] of String SHA-1's for each of the cube names in the branch.
     * @return int the number of n-cubes merged.
     */
    static int mergeAcceptTheirs(ApplicationID appId, Object[] cubeNames, String sourceBranch = ApplicationID.HEAD)
    {
        NCubeManager.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        NCubeManager.assertNotLockBlocked(appId)
        int count = 0

        for (int i = 0; i < cubeNames.length; i++)
        {
            String cubeNameStr = cubeNames[i] as String
            NCubeManager.assertPermissions(appId, cubeNameStr, Action.UPDATE)
            NCubeManager.assertPermissions(appId.asBranch(sourceBranch), cubeNameStr, Action.READ)
            persister.mergeAcceptTheirs(appId, cubeNameStr, sourceBranch, userId)
            NCubeManager.removeCachedCube(appId, cubeNameStr)
            count++
        }

        return count
    }

    // -------------------------------- Non API methods --------------------------------------

    private static NCube mergeCubesIfPossible(NCubeInfoDto branchInfo, NCubeInfoDto headInfo, boolean headToBranch)
    {
        long branchCubeId = (long) Converter.convert(branchInfo.id, long.class)
        long headCubeId = (long) Converter.convert(headInfo.id, long.class)
        NCube branchCube = persister.loadCubeById(branchCubeId)
        NCube headCube = persister.loadCubeById(headCubeId)
        NCube baseCube, headBaseCube
        Map branchDelta, headDelta

        if (branchInfo.headSha1 != null)
        {   // Cube is based on a HEAD cube (not created new)
            baseCube = persister.loadCubeBySha1(branchInfo.applicationID.asHead(), branchInfo.name, branchInfo.headSha1)
            headDelta = DeltaProcessor.getDelta(baseCube, headCube)
        }
        else
        {   // No HEAD cube to base this cube on.  Treat it as new cube by creating stub cube as
            // basis cube, and then the deltas will describe the full-build of the n-cube.
            baseCube = branchCube.createStubCube()
            headBaseCube = headCube.createStubCube()
            headDelta = DeltaProcessor.getDelta(headBaseCube, headCube)
        }

        branchDelta = DeltaProcessor.getDelta(baseCube, branchCube)

        if (DeltaProcessor.areDeltaSetsCompatible(branchDelta, headDelta, headToBranch))
        {
            if (headToBranch)
            {
                DeltaProcessor.mergeDeltaSet(headCube, branchDelta)
                return headCube // merged n-cube (HEAD cube with branch changes in it)
            }
            else
            {
                DeltaProcessor.mergeDeltaSet(branchCube, headDelta)
                return branchCube   // merge n-cube (branch cube with HEAD changes in it)
            }
        }

        List<Delta> diff = DeltaProcessor.getDeltaDescription(branchCube, headCube)
        if (diff.size() > 0)
        {
            return null
        }
        else
        {
            return branchCube
        }
    }

    private static NCubeInfoDto getCubeInfo(ApplicationID appId, NCubeInfoDto dto)
    {
        List<NCubeInfoDto> cubeDtos = NCubeManager.search(appId, dto.name, null, [(NCubeManager.SEARCH_EXACT_MATCH_NAME):true, (NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):false])
        if (cubeDtos.empty)
        {
            throw new IllegalStateException('Cube ' + dto.name + ' does not exist (' + dto + ')')
        }
        if (cubeDtos.size() > 1)
        {
            throw new IllegalStateException('More than one cube return when attempting to load ' + dto.name + ' (' + dto + ')')
        }
        return cubeDtos.first()
    }

    private static Object[] buildIdList(List<NCubeInfoDto> dtos)
    {
        Object[] ids = new Object[dtos.size()]
        int i=0
        dtos.each { NCubeInfoDto dto ->
            ids[i++] = dto.id
        }
        return ids
    }

    private static String getUserId()
    {
        return NCubeManager.userId
    }

    private static NCubePersister getPersister()
    {
        return NCubeManager.persister
    }
}
