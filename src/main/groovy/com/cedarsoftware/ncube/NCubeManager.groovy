package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.BranchMergeException
import com.cedarsoftware.ncube.formatters.NCubeTestReader
import com.cedarsoftware.ncube.util.VersionComparator
import com.cedarsoftware.util.ArrayUtilities
import com.cedarsoftware.util.CaseInsensitiveMap
import com.cedarsoftware.util.CaseInsensitiveSet
import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.EncryptionUtilities
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.UniqueIdGenerator
import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.JsonWriter
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.cache.Cache
import org.springframework.cache.CacheManager

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.regex.Pattern

import static com.cedarsoftware.ncube.NCubeConstants.*
import static com.cedarsoftware.ncube.ReferenceAxisLoader.*

/**
 * This class manages a list of NCubes.  This class is referenced
 * by NCube in one place - when it joins to other cubes, it consults
 * the NCubeManager to find the joined NCube.
 * <p/>
 * This class takes care of creating, loading, updating, releasing,
 * and deleting NCubes.  It also allows you to get a list of NCubes
 * matching a wildcard (SQL Like) string.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
class NCubeManager implements NCubeMutableClient, NCubeTestServer
{
    // Maintain cache of 'wildcard' patterns to Compiled Pattern instance
    private final ConcurrentMap<String, Pattern> wildcards = new ConcurrentHashMap<>()
    private NCubePersister nCubePersister
    private static final Logger LOG = LoggerFactory.getLogger(NCubeManager.class)
    private final CacheManager permCacheManager
    
    private final ThreadLocal<String> userId = new ThreadLocal<String>() {
        String initialValue()
        {
            return System.getProperty('user.name')
        }
    }

    private ThreadLocal<Boolean> isSystemRequest = new ThreadLocal<Boolean>() {
        Boolean initialValue()
        {
            return false
        }
    }

    private static final List CUBE_MUTATE_ACTIONS = [Action.COMMIT, Action.UPDATE]

    NCubeManager(NCubePersister persister, CacheManager permCacheManager)
    {
        nCubePersister = persister
        this.permCacheManager = permCacheManager
    }
    
    NCubePersister getPersister()
    {
        if (nCubePersister == null)
        {
            throw new IllegalStateException('Persister not set into NCubeManager.')
        }
        return nCubePersister
    }

    NCube getCube(ApplicationID appId, String cubeName)
    {
        return loadCube(appId, cubeName)
    }

    /**
     * Load n-cube, bypassing any caching.  This is necessary for n-cube-editor (IDE time
     * usage).  If the IDE environment is clustered, cannot be getting stale copies from
     * cache.  Any advices in the manager will be applied to the n-cube.
     * @return NCube of the specified name from the specified AppID, or null if not found.
     */
    NCube loadCube(ApplicationID appId, String cubeName)
    {
        return loadCube(appId, cubeName, null)
    }

    NCube loadCube(ApplicationID appId, String cubeName, Map options)
    {
        assertPermissions(appId, cubeName)
        return loadCubeInternal(appId, cubeName, options)
    }

    private NCube loadCubeInternal(ApplicationID appId, String cubeName, Map options = null)
    {
        NCube ncube = persister.loadCube(appId, cubeName, options, getUserId())
        return ncube
    }

    /**
     * Load the n-cube with the specified id.  This is useful in n-cube editors, where a user wants to pick
     * an older revision and load / compare it.
     * @param id long n-cube id.
     * @return NCube that has the passed in id.
     */
    NCube loadCubeById(long id)
    {
        return loadCubeById(id, null)
    }

    NCube loadCubeById(long id, Map options)
    {
        NCube ncube = persister.loadCubeById(id, options, getUserId())
        assertPermissions(ncube.applicationID, ncube.name, Action.READ)
        return ncube
    }

    void createCube(NCube ncube)
    {
        detectNewAppId(ncube.applicationID)
        persister.createCube(ncube, getUserId())
    }

    /**
     * Retrieve all cube names that are deeply referenced by ApplicationID + n-cube name.
     */
    Set<String> getReferencesFrom(ApplicationID appId, String name, Set<String> refs = new CaseInsensitiveSet<>())
    {
        ApplicationID.validateAppId(appId)
        NCube.validateCubeName(name)
        NCube ncube = loadCube(appId, name)
        if (ncube == null)
        {
            throw new IllegalArgumentException("Could not get referenced cube names, n-cube: ${name} does not exist in app: ${appId}")
        }

        Map<Map, Set<String>> subCubeRefs = ncube.referencedCubeNames

        // TODO: Use explicit stack, NOT recursion

        subCubeRefs.values().each { Set<String> cubeNames ->
            cubeNames.each { String cubeName ->
                if (!refs.contains(cubeName))
                {
                    refs.add(cubeName)
                    getReferencesFrom(appId, cubeName, refs)
                }
            }
        }
        return refs
    }

    /**
     * Restore a previously deleted n-cube.
     */
    Boolean restoreCubes(ApplicationID appId, Object[] cubeNames)
    {
        ApplicationID.validateAppId(appId)
        appId.validateBranchIsNotHead()

        if (appId.release)
        {
            throw new IllegalArgumentException("${ReleaseStatus.RELEASE.name()} cubes cannot be restored, app: ${appId}")
        }

        if (ArrayUtilities.isEmpty(cubeNames))
        {
            throw new IllegalArgumentException('Error, empty array of cube names passed in to be restored.')
        }

        assertNotLockBlocked(appId)
        for (String cubeName : cubeNames)
        {
            assertPermissions(appId, cubeName, Action.UPDATE)
        }

        // Batch restore
        return persister.restoreCubes(appId, cubeNames, getUserId())
    }

    /**
     * Get a List<NCubeInfoDto> containing all history for the given cube.
     */
    List<NCubeInfoDto> getRevisionHistory(ApplicationID appId, String cubeName, boolean ignoreVersion = false)
    {
        ApplicationID.validateAppId(appId)
        NCube.validateCubeName(cubeName)
        assertPermissions(appId, cubeName)
        List<NCubeInfoDto> revisions = persister.getRevisions(appId, cubeName, ignoreVersion, getUserId())
        return revisions
    }

    /**
     * Get a List<NCubeInfoDto> containing all history for the given cell of a cube.
     */
    List<NCubeInfoDto> getCellAnnotation(ApplicationID appId, String cubeName, Set<Long> ids, boolean ignoreVersion = false)
    {
        ApplicationID.validateAppId(appId)
        NCube.validateCubeName(cubeName)
        assertPermissions(appId, cubeName)
        List<NCubeInfoDto> revisions = persister.getRevisions(appId, cubeName, ignoreVersion,getUserId()).sort(true, {NCubeInfoDto rev -> rev.revision as long})
        List<NCubeInfoDto> relevantRevDtos = []
        NCubeInfoDto prevDto
        NCube oldCube
        for (NCubeInfoDto revDto : revisions)
        {
            NCube newCube = loadCubeById(revDto.id as long)
            if (prevDto && oldCube)
            {
                List<Delta> diffs = DeltaProcessor.getDeltaDescription(newCube, oldCube)
                for (Delta diff : diffs)
                {
                    if (diff.location == Delta.Location.CELL && diff.locId == ids)
                    {
                        if (relevantRevDtos.empty)
                        {
                            relevantRevDtos.add(prevDto)
                        }
                        relevantRevDtos.add(revDto)
                    }
                }
            }
            prevDto = revDto
            oldCube = newCube
        }
        return relevantRevDtos ?: [revisions.first()]
    }

    /**
     * Return a List of Strings containing all unique App names for the given tenant.
     */
    List<String> getAppNames()
    {
        return persister.getAppNames(tenant, getUserId())
    }

    /**
     * Get all of the versions that exist for the given ApplicationID (app name).
     * @return Object[] version numbers.
     */
    Object[] getVersions(String app)
    {
        ApplicationID.validateApp(app)
        Set<String> versions = []
        Map<String, List<String>> versionMap = persister.getVersions(tenant, app, getUserId())
        versionMap.keySet().each {String status ->
            addVersions(versions, versionMap[status], status)
        }
        return versions.toArray()
    }

    private void addVersions(Set<String> set, List<String> versions, String suffix)
    {
        for (String version : versions)
        {
            set.add("${version}-${suffix}".toString())
        }
    }

    /**
     * Get the lastest version for the given tenant, app, and SNAPSHOT or RELEASE.
     * @return String version number in the form "major.minor.patch" where each of the
     * values (major, minor, patch) is numeric.
     */
    String getLatestVersion(ApplicationID appId)
    {
        String tenant = appId.tenant
        String app = appId.app
        ApplicationID.validateTenant(tenant)
        ApplicationID.validateApp(app)
        Map<String, List<String>> versionsMap = persister.getVersions(tenant, app, getUserId())
        Set<String> versions = new TreeSet<>(new VersionComparator())
        versions.addAll(versionsMap[appId.status])
        return versions.first() as String
    }

    /**
     * Duplicate the given n-cube specified by oldAppId and oldName to new ApplicationID and name,
     */
    Boolean duplicate(ApplicationID oldAppId, ApplicationID newAppId, String oldName, String newName)
    {
        ApplicationID.validateAppId(oldAppId)
        ApplicationID.validateAppId(newAppId)

        newAppId.validateBranchIsNotHead()

        if (newAppId.release)
        {
            throw new IllegalArgumentException("Cubes cannot be duplicated into a ${ReleaseStatus.RELEASE} version, cube: ${newName}, app: ${newAppId}")
        }

        NCube.validateCubeName(oldName)
        NCube.validateCubeName(newName)

        if (oldName.equalsIgnoreCase(newName) && oldAppId == newAppId)
        {
            throw new IllegalArgumentException("Could not duplicate, old name cannot be the same as the new name when oldAppId matches newAppId, name: ${oldName}, app: ${oldAppId}")
        }

        assertPermissions(oldAppId, oldName, Action.READ)
        if (oldAppId != newAppId)
        {   // Only see if branch permissions are needed to be created when destination cube is in a different ApplicationID
            detectNewAppId(newAppId)
        }
        assertPermissions(newAppId, newName, Action.UPDATE)
        assertNotLockBlocked(newAppId)
        return persister.duplicateCube(oldAppId, newAppId, oldName, newName, getUserId())
    }

    /**
     * Update the passed in NCube.  Only SNAPSHOT cubes can be updated.
     *
     * @param ncube      NCube to be updated.
     * @return boolean true on success, false otherwise
     */
    Boolean updateCube(NCube ncube, boolean updateHead = false)
    {
        if (ncube == null)
        {
            throw new IllegalArgumentException('NCube cannot be null')
        }
        ApplicationID appId = ncube.applicationID
        ApplicationID.validateAppId(appId)
        NCube.validateCubeName(ncube.name)

        if (appId.release)
        {
            throw new IllegalArgumentException("${ReleaseStatus.RELEASE.name()} cubes cannot be updated, cube: ${ncube.name}, app: ${appId}")
        }

        if (!updateHead)
        {
            appId.validateBranchIsNotHead()
        }

        final String cubeName = ncube.name
        assertPermissions(appId, cubeName, Action.UPDATE)
        assertNotLockBlocked(appId)
        persister.updateCube(ncube, getUserId())
        ncube.applicationID = appId
        return true
    }

    /**
     * Copy branch from one app id to another
     * @param srcAppId Branch copied from (source branch)
     * @param targetAppId Branch copied to (must not exist)
     * @return int number of n-cubes in branch (number copied - revision depth is not copied)
     */
    Integer copyBranch(ApplicationID srcAppId, ApplicationID targetAppId, boolean copyWithHistory = false)
    {
        assertPermissions(srcAppId, null, Action.READ)
        assertPermissions(targetAppId, null, Action.UPDATE)
        ApplicationID.validateAppId(srcAppId)
        ApplicationID.validateAppId(targetAppId)
        targetAppId.validateStatusIsNotRelease()
        if (!search(targetAppId.asRelease(), null, null, [(SEARCH_ACTIVE_RECORDS_ONLY): true]).empty)
        {
            throw new IllegalArgumentException("A RELEASE version ${targetAppId.version} already exists, app: ${targetAppId}")
        }
        assertNotLockBlocked(targetAppId)
        if (targetAppId.version != '0.0.0')
        {
            detectNewAppId(targetAppId)
        }
        int rows = copyWithHistory ? persister.copyBranchWithHistory(srcAppId, targetAppId, getUserId()) : persister.copyBranch(srcAppId, targetAppId, getUserId())
        return rows
    }

    /**
     * Merge the passed in List of Delta's into the named n-cube.
     * @param appId ApplicationID containing the named n-cube.
     * @param cubeName String name of the n-cube into which the Delta's will be merged.
     * @param deltas List of Delta instances
     * @return the NCube t
     */
    NCube mergeDeltas(ApplicationID appId, String cubeName, List<Delta> deltas)
    {
        NCube ncube = loadCube(appId, cubeName, [(SEARCH_INCLUDE_TEST_DATA):true])
        if (ncube == null)
        {
            throw new IllegalArgumentException("No ncube exists with the name: ${cubeName}, no changes will be merged, app: ${appId}")
        }

        assertPermissions(appId, cubeName, Action.UPDATE)
        deltas.each { Delta delta ->
            if ([Delta.Location.AXIS, Delta.Location.AXIS_META, Delta.Location.COLUMN, Delta.Location.COLUMN_META].contains(delta.location))
            {
                String axisName
                switch (delta.location)
                {
                    case Delta.Location.AXIS:
                        axisName = ((delta.sourceVal ?: delta.destVal) as Axis).name
                        break
                    case Delta.Location.AXIS_META:
                    case Delta.Location.COLUMN:
                        axisName = delta.locId as String
                        break
                    case Delta.Location.COLUMN_META:
                        axisName = (delta.locId as Map<String, Object>).axis
                        break
                    default:
                        throw new IllegalArgumentException("Invalid properties on delta, no changes will be merged, app: ${appId}, cube: ${cubeName}")
                }
                assertPermissions(appId, cubeName + '/' + axisName, Action.UPDATE)
            }
        }

        ncube.mergeDeltas(deltas)
        updateCube(ncube)
        return ncube
    }

    /**
     * Move the branch specified in the appId to the newer snapshot version (newSnapVer).
     * @param ApplicationID indicating what to move
     * @param newSnapVer String version to move cubes to
     * @return number of rows moved (count includes revisions per cube).
     */
    Integer moveBranch(ApplicationID appId, String newSnapVer)
    {
        ApplicationID.validateAppId(appId)
        if (ApplicationID.HEAD == appId.branch)
        {
            throw new IllegalArgumentException('Cannot move the HEAD branch')
        }
        if ('0.0.0' == appId.version)
        {
            throw new IllegalStateException(ERROR_CANNOT_MOVE_000)
        }
        if ('0.0.0' == newSnapVer)
        {
            throw new IllegalStateException(ERROR_CANNOT_MOVE_TO_000)
        }
        assertLockedByMe(appId)
        assertPermissions(appId, null, Action.RELEASE)
        int rows = persister.moveBranch(appId, newSnapVer, getUserId())
        return rows
    }

    /**
     * Perform release (SNAPSHOT to RELEASE) for the given ApplicationIDs n-cubes.
     */
    Integer releaseVersion(ApplicationID appId, String newSnapVer)
    {
        ApplicationID.validateAppId(appId)
        assertPermissions(appId, null, Action.RELEASE)
        assertLockedByMe(appId)
        ApplicationID.validateVersion(newSnapVer)
        if ('0.0.0' == appId.version)
        {
            throw new IllegalArgumentException(ERROR_CANNOT_RELEASE_000)
        }
        if ('0.0.0' == newSnapVer)
        {
            throw new IllegalArgumentException(ERROR_CANNOT_RELEASE_TO_000)
        }
        if (!search(appId.asRelease(), null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).empty)
        {
            throw new IllegalArgumentException("A RELEASE version ${appId.version} already exists, app: ${appId}")
        }

        validateReferenceAxesAppIds(appId)

        int rows = persister.releaseCubes(appId, newSnapVer, getUserId())
        updateOpenPullRequestVersions(appId, newSnapVer)
        return rows
    }

    /**
     * Perform release (SNAPSHOT to RELEASE) for the given ApplicationIDs n-cubes.
     */
    Integer releaseCubes(ApplicationID appId, String newSnapVer)
    {
        assertPermissions(appId, null, Action.RELEASE)
        ApplicationID.validateAppId(appId)
        ApplicationID.validateVersion(newSnapVer)
        if ('0.0.0' == appId.version)
        {
            throw new IllegalArgumentException(ERROR_CANNOT_RELEASE_000)
        }
        if ('0.0.0' == newSnapVer)
        {
            throw new IllegalArgumentException(ERROR_CANNOT_RELEASE_TO_000)
        }
        if (!search(appId.asVersion(newSnapVer), null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).empty)
        {
            throw new IllegalArgumentException("A SNAPSHOT version ${appId.version} already exists, app: ${appId}")
        }
        if (!search(appId.asRelease(), null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):true]).empty)
        {
            throw new IllegalArgumentException("A RELEASE version ${appId.version} already exists, app: ${appId}")
        }

        validateReferenceAxesAppIds(appId)

        lockApp(appId, true)
        if (!NCubeAppContext.test)
        {   // Only sleep when running in production (not by JUnit)
            sleep(10000)
        }

        Set<String> branches = getBranches(appId)
        for (String branch : branches)
        {
            if (!ApplicationID.HEAD.equalsIgnoreCase(branch))
            {
                ApplicationID branchAppId = appId.asBranch(branch)
                moveBranch(branchAppId, newSnapVer)
            }
        }
        int rows = persister.releaseCubes(appId, newSnapVer, getUserId())
        persister.copyBranch(appId.asRelease(), appId.asSnapshot().asHead().asVersion(newSnapVer), getUserId())
        updateOpenPullRequestVersions(appId, newSnapVer)
        lockApp(appId, false)
        return rows
    }

    protected void updateOpenPullRequestVersions(ApplicationID appId, String newSnapVer, boolean onlyCurrentBranch = false)
    {
        Map prAppIdCoord = [(PR_PROP):PR_APP]
        Map prStatusCoord = [(PR_PROP):PR_STATUS]
        Date startDate = new Date() - 60
        List<NCube> prCubes = getPullRequestCubes(startDate, null)
        runSystemRequest {
            for (NCube prCube : prCubes)
            {
                Object prAppIdObj = prCube.getCell(prAppIdCoord)
                Object prStatus = prCube.getCell(prStatusCoord)
                ApplicationID prAppId = prAppIdObj instanceof ApplicationID ? prAppIdObj as ApplicationID : ApplicationID.convert(prAppIdObj as String)
                if (prStatus == PR_OPEN)
                {
                    boolean shouldChange = onlyCurrentBranch ? prAppId == appId : prAppId.equalsNotIncludingBranch(appId)
                    if (shouldChange)
                    {
                        prAppId = prAppId.asVersion(newSnapVer)
                        prCube.setCell(prAppId.toString(), prAppIdCoord)
                        updateCube(prCube, true)
                    }
                }
            }
        }
    }

    void changeVersionValue(ApplicationID appId, String newVersion)
    {
        ApplicationID.validateAppId(appId)

        if (appId.release)
        {
            throw new IllegalArgumentException("Cannot change the version of a ${ReleaseStatus.RELEASE.name()}, app: ${appId}")
        }
        ApplicationID.validateVersion(newVersion)
        assertPermissions(appId, null, Action.RELEASE)
        assertNotLockBlocked(appId)
        persister.changeVersionValue(appId, newVersion, getUserId())
        updateOpenPullRequestVersions(appId, newVersion, true)
    }

    Boolean renameCube(ApplicationID appId, String oldName, String newName)
    {
        ApplicationID.validateAppId(appId)
        appId.validateBranchIsNotHead()

        if (appId.release)
        {
            throw new IllegalArgumentException("Cannot rename a ${ReleaseStatus.RELEASE.name()} cube, cube: ${oldName}, app: ${appId}")
        }

        assertNotLockBlocked(appId)

        NCube.validateCubeName(oldName)
        NCube.validateCubeName(newName)

        if (oldName == newName)
        {
            throw new IllegalArgumentException("Could not rename, old name cannot be the same as the new name, name: ${oldName}, app: ${appId}")
        }

        assertPermissions(appId, oldName, Action.UPDATE)
        assertPermissions(appId, newName, Action.UPDATE)

        boolean result = persister.renameCube(appId, oldName, newName, getUserId())
        return result
    }

    Boolean deleteBranch(ApplicationID appId)
    {
        appId.validateBranchIsNotHead()
        assertPermissions(appId, null, Action.UPDATE)
        assertNotLockBlocked(appId)
        return persister.deleteBranch(appId, getUserId())
    }

    /**
     * Delete the named NCube from the database
     *
     * @param cubeNames  Object[] of String cube names to be deleted (soft deleted)
     */
    Boolean deleteCubes(ApplicationID appId, Object[] cubeNames)
    {
        appId.validateBranchIsNotHead()
        assertNotLockBlocked(appId)
        for (Object name : cubeNames)
        {
            assertPermissions(appId, name as String, Action.UPDATE)
        }
        return deleteCubes(appId, cubeNames, false)
    }

    Boolean deleteCubes(ApplicationID appId, Object[] cubeNames, boolean allowDelete)
    {
        ApplicationID.validateAppId(appId)
        if (!allowDelete)
        {
            if (appId.release)
            {
                throw new IllegalArgumentException("${ReleaseStatus.RELEASE.name()} cubes cannot be hard-deleted, app: ${appId}")
            }
        }

        assertNotLockBlocked(appId)
        for (Object name : cubeNames)
        {
            assertPermissions(appId, name as String, Action.UPDATE)
        }

        if (persister.deleteCubes(appId, cubeNames, allowDelete, getUserId()))
        {
            return true
        }
        return false
    }

    Map getAppTests(ApplicationID appId)
    {
        Map ret = [:]
        ApplicationID.validateAppId(appId)
        Map appTests = persister.getAppTestData(appId, getUserId())
        for (Map.Entry cubeTest : appTests.entrySet())
        {
            String cubeName = cubeTest.key
            assertPermissions(appId, cubeName)
            List<NCubeTest> tests = NCubeTestReader.convert(cubeTest.value as String)
            ret[cubeName] = tests
        }
        return ret
    }

    Object[] getTests(ApplicationID appId, String cubeName)
    {
        ApplicationID.validateAppId(appId)
        NCube.validateCubeName(cubeName)
        assertPermissions(appId, cubeName)
        String s = persister.getTestData(appId, cubeName, getUserId())
        return convertTests(s)
    }

    private static Object[] convertTests(String s)
    {
        if (StringUtilities.isEmpty(s))
        {
            return null
        }
        List<NCubeTest> tests = NCubeTestReader.convert(s)
        return tests.toArray()
    }

    Boolean updateNotes(ApplicationID appId, String cubeName, String notes)
    {
        ApplicationID.validateAppId(appId)
        NCube.validateCubeName(cubeName)
        assertPermissions(appId, cubeName, Action.UPDATE)
        assertNotLockBlocked(appId)
        return persister.updateNotes(appId, cubeName, notes, getUserId())
    }

    String getNotes(ApplicationID appId, String cubeName)
    {
        ApplicationID.validateAppId(appId)
        NCube.validateCubeName(cubeName)
        assertPermissions(appId, cubeName)

        Map<String, Object> options = [:]
        options[SEARCH_INCLUDE_NOTES] = true
        options[SEARCH_EXACT_MATCH_NAME] = true
        List<NCubeInfoDto> infos = search(appId, cubeName, null, options)

        if (infos.empty)
        {
            throw new IllegalArgumentException("Could not fetch notes, no cube: ${cubeName} in app: ${appId}")
        }
        return infos[0].notes
    }

    Set<String> getBranches(ApplicationID appId)
    {
        appId.validate()
        assertPermissions(appId, null)
        Set<String> realBranches = persister.getBranches(appId, getUserId())
        realBranches.add(ApplicationID.HEAD)
        return realBranches
    }

    Integer getBranchCount(ApplicationID appId)
    {
        Set<String> branches = getBranches(appId)
        return branches.size()
    }

    String getCubeRawJson(ApplicationID appId, String cubeName)
    {
        return persister.loadCubeRawJson(appId, cubeName, getUserId())
    }

    /**
     *
     * Fetch an array of NCubeInfoDto's where the cube names match the cubeNamePattern (contains) and
     * the content (in JSON format) 'contains' the passed in content String.
     * @param appId ApplicationID on which we are working
     * @param cubeNamePattern cubeNamePattern String pattern to match cube names
     * @param content String value that is 'contained' within the cube's JSON
     * @param options map with possible keys:
     *                changedRecordsOnly - default false ->  Only searches changed records if true.
     *                activeRecordsOnly - default false -> Only searches non-deleted records if true.
     *                deletedRecordsOnly - default false -> Only searches deleted records if true.
     *                cacheResult - default false -> Cache the cubes that match this result..
     * @return List<NCubeInfoDto>
     */
    List<NCubeInfoDto> search(ApplicationID appId, String cubeNamePattern, String content, Map options)
    {
        ApplicationID.validateAppId(appId)

        if (options == null)
        {
            options = [:]
        }

        if (!options[SEARCH_EXACT_MATCH_NAME])
        {
            cubeNamePattern = handleWildCard(cubeNamePattern)
        }

        content = handleWildCard(content)

        Map permInfo = getPermInfo(appId)
        List<NCubeInfoDto> cubes = persister.search(appId, cubeNamePattern, content, options, getUserId())
        if (!permInfo.skipPermCheck && !systemRequest)
        {
            cubes.removeAll { !fastCheckPermissions(appId, it.name, Action.READ, permInfo) }
        }
        return cubes
    }

    List<NCube> cubeSearch(ApplicationID appId, String cubeNamePattern, String content, Map options = [:])
    {
        ApplicationID.validateAppId(appId)

        if (!options[SEARCH_EXACT_MATCH_NAME])
        {
            cubeNamePattern = handleWildCard(cubeNamePattern)
        }

        content = handleWildCard(content)

        Map permInfo = getPermInfo(appId)
        List<NCube> cubes = persister.cubeSearch(appId, cubeNamePattern, content, options, getUserId())
        if (!permInfo.skipPermCheck && !systemRequest)
        {
            cubes.removeAll { !fastCheckPermissions(appId, it.name, Action.READ, permInfo) }
        }
        return cubes
    }

    /**
     * This API will hand back a List of AxisRef, which is a complete description of a Reference
     * Axis pointer. It includes the Source ApplicationID, source Cube Name, source Axis Name,
     * and all the referenced cube/axis and filter (cube/method) parameters.
     * @param appId ApplicationID of the cube-set from which to fetch all the reference axes.
     * @return List<AxisRef>
     */
    List<AxisRef> getReferenceAxes(ApplicationID appId)
    {
        ApplicationID.validateAppId(appId)
        assertPermissions(appId, null)

        // Step 1: Fetch all NCubeInfoDto's for the passed in ApplicationID
        List<NCube> list = cubeSearch(appId, null, "*${REF_APP}*", [(SEARCH_ACTIVE_RECORDS_ONLY):true])
        List<AxisRef> refAxes = []

        for (NCube source : list)
        {
            try
            {
                for (Axis axis : source.axes)
                {
                    if (axis.reference)
                    {
                        AxisRef ref = new AxisRef()
                        ref.srcAppId = appId
                        ref.srcCubeName = source.name
                        ref.srcAxisName = axis.name

                        ApplicationID refAppId = axis.referencedApp
                        ref.destApp = refAppId.app
                        ref.destVersion = refAppId.version
                        ref.destStatus = refAppId.status
                        ref.destBranch = refAppId.branch
                        ref.destCubeName = axis.getMetaProperty(REF_CUBE_NAME)
                        ref.destAxisName = axis.getMetaProperty(REF_AXIS_NAME)

                        ApplicationID transformAppId = axis.transformApp
                        if (transformAppId)
                        {
                            ref.transformApp = transformAppId.app
                            ref.transformVersion = transformAppId.version
                            ref.transformStatus = transformAppId.status
                            ref.transformBranch = transformAppId.branch
                            ref.transformCubeName = axis.getMetaProperty(TRANSFORM_CUBE_NAME)
                        }

                        refAxes.add(ref)
                    }
                }
            }
            catch (Exception e)
            {
                LOG.warn("Unable to load cube: ${source.name}, app: ${source.applicationID}", e)
            }
        }
        return refAxes
    }

    private static Set<ApplicationID> getReferenceAxesAppIds(Object[] axisRefs, boolean source)
    {
        Set<ApplicationID> uniqueAppIds = new HashSet()
        for (Object obj : axisRefs)
        {
            AxisRef axisRef = obj as AxisRef
            ApplicationID srcApp = axisRef.srcAppId
            ApplicationID destAppId = new ApplicationID(srcApp.tenant, axisRef.destApp, axisRef.destVersion, axisRef.destStatus, axisRef.destBranch)
            if (source)
            {
                uniqueAppIds.add(srcApp)
            }
            else
            {
                uniqueAppIds.add(destAppId)
            }

            if (axisRef.transformApp != null && axisRef.transformVersion != null && axisRef.transformStatus != null && axisRef.transformBranch != null)
            {
                ApplicationID transformAppId = new ApplicationID(srcApp.tenant, axisRef.transformApp, axisRef.transformVersion, axisRef.transformStatus, axisRef.transformBranch)
                if (!source)
                {
                    uniqueAppIds.add(transformAppId)
                }
            }
        }
        return uniqueAppIds
    }

    private void validateReferenceAxesAppIds(ApplicationID appId, List<NCubeInfoDto> infoDtos = null)
    {
        String snapshot = ReleaseStatus.SNAPSHOT.name()
        List<AxisRef> allAxisRefs = getReferenceAxes(appId)
        List<AxisRef> checkAxisRefs = allAxisRefs
        if (infoDtos)
        {
            checkAxisRefs = new ArrayList<AxisRef>()
            for (NCubeInfoDto infoDto : infoDtos)
            {
                List<AxisRef> foundAxisRefs = allAxisRefs.findAll { AxisRef axisRef ->
                    axisRef.srcCubeName == infoDto.name
                }
                checkAxisRefs.addAll(foundAxisRefs)
            }
        }
        for (AxisRef ref : checkAxisRefs)
        {
            ApplicationID destAppId = new ApplicationID(ref.srcAppId.tenant, ref.destApp, ref.destVersion, ref.destStatus, ref.destBranch)
            destAppId.validateAppId(destAppId)
            assertPermissions(destAppId, ref.destCubeName)
            if (ref.transformApp && ref.transformVersion && ref.transformStatus && ref.transformBranch) {
                ApplicationID transformAppId = new ApplicationID(ref.srcAppId.tenant, ref.transformApp, ref.transformVersion, ref.transformStatus, ref.transformBranch)
                destAppId.validateAppId(transformAppId)
                assertPermissions(transformAppId, ref.destCubeName)
            }
        }
        Set<ApplicationID> uniqueAppIds = getReferenceAxesAppIds(checkAxisRefs.toArray(), false)
        Map<String, ApplicationID> checklist = [:]
        for (ApplicationID refAppId : uniqueAppIds)
        {
            if (refAppId.status == snapshot)
            {
                throw new IllegalStateException("Operation not performed. Axis references pointing to snapshot version, referenced app: ${refAppId}")
            }
            ApplicationID checkedApp = checklist[refAppId.app]
            if (checkedApp)
            {
                if (checkedApp != refAppId)
                {
                    throw new IllegalStateException("Operation not performed. Axis references pointing to differing versions per app, referenced app: ${refAppId.app}")
                }
            }
            else
            {
                checklist[refAppId.app] = refAppId
            }
        }

        Set<ApplicationID> committedAppIds = getReferenceAxesAppIds(allAxisRefs.toArray(), false)
        for (ApplicationID refAppId : committedAppIds)
        {
            ApplicationID checkedApp = checklist[refAppId.app]
            if (checkedApp && checkedApp != refAppId)
            {
                throw new IllegalStateException("Operation not performed. Axis references pointing to differing versions per app, referenced app: ${refAppId.app}")
            }
        }
    }

    void updateReferenceAxes(Object[] axisRefs)
    {
        Set<ApplicationID> uniqueAppIds = getReferenceAxesAppIds(axisRefs, true)
        for (ApplicationID appId : uniqueAppIds)
        {
            assertPermissions(appId, null, Action.UPDATE)
            // Make sure we are not lock blocked on any of the appId's that are being updated.
            assertNotLockBlocked(appId)
        }

        for (Object obj : axisRefs)
        {
            AxisRef axisRef = obj as AxisRef
            axisRef.with {
                assertPermissions(srcAppId, srcCubeName, Action.UPDATE)
                NCube ncube = persister.loadCube(srcAppId, srcCubeName, null, getUserId())
                Axis axis = ncube.getAxis(srcAxisName)

                axis.setMetaProperty(REF_APP, destApp)
                axis.setMetaProperty(REF_VERSION, destVersion)
                axis.setMetaProperty(REF_STATUS, destStatus)
                axis.setMetaProperty(REF_BRANCH, destBranch)
                axis.setMetaProperty(REF_CUBE_NAME, destCubeName)
                axis.setMetaProperty(REF_AXIS_NAME, destAxisName)
                ApplicationID appId = new ApplicationID(srcAppId.tenant, destApp, destVersion, destStatus, destBranch)
                assertPermissions(appId, null)

                NCube target = persister.loadCube(appId, destCubeName, null, getUserId())
                if (target == null)
                {
                    throw new IllegalArgumentException("""\
Cannot point reference axis to non-existing cube: ${destCubeName}. \
Source axis: ${srcAppId.cacheKey(srcCubeName)}.${srcAxisName}, \
target axis: ${destApp} / ${destVersion} / ${destCubeName}.${destAxisName}""")
                }

                if (target.getAxis(destAxisName) == null)
                {
                    throw new IllegalArgumentException("""\
Cannot point reference axis to non-existing axis: ${destAxisName}. \
Source axis: ${srcAppId.cacheKey(srcCubeName)}.${srcAxisName}, \
target axis: ${destApp} / ${destVersion} / ${destCubeName}.${destAxisName}""")
                }

                axis.setMetaProperty(TRANSFORM_APP, transformApp)
                axis.setMetaProperty(TRANSFORM_VERSION, transformVersion)
                axis.setMetaProperty(TRANSFORM_STATUS, transformStatus)
                axis.setMetaProperty(TRANSFORM_BRANCH, transformBranch)
                axis.setMetaProperty(TRANSFORM_CUBE_NAME, transformCubeName)

                if (transformApp && transformVersion && transformStatus && transformBranch && transformCubeName)
                {   // If transformer cube reference supplied, verify that the cube exists
                    ApplicationID txAppId = new ApplicationID(srcAppId.tenant, transformApp, transformVersion, transformStatus, transformBranch)
                    assertPermissions(txAppId, null)
                    NCube transformCube = persister.loadCube(txAppId, transformCubeName, null, getUserId())
                    if (transformCube == null)
                    {
                        throw new IllegalArgumentException("""\
Cannot point reference axis transformer to non-existing cube: ${transformCubeName}. \
Source axis: ${srcAppId.cacheKey(srcCubeName)}.${srcAxisName}, \
target axis: ${transformApp} / ${transformVersion} / ${transformCubeName}""")
                    }
                }
                else
                {
                    axis.removeTransform()
                }

                ncube.clearSha1()   // changing meta properties does not clear SHA-1 for recalculation.
                persister.updateCube(ncube, getUserId())
            }
        }
    }

    /**
     * Update an Axis meta-properties
     */
    void updateAxisMetaProperties(ApplicationID appId, String cubeName, String axisName, Map<String, Object> newMetaProperties)
    {
        NCube.transformMetaProperties(newMetaProperties)
        String resourceName = cubeName + '/' + axisName
        assertPermissions(appId, resourceName, Action.UPDATE)
        NCube ncube = loadCube(appId, cubeName)
        Axis axis = ncube.getAxis(axisName)
        axis.updateMetaProperties(newMetaProperties, cubeName, { Set<Long> colIds ->
            ncube.dropOrphans(colIds, axis.id)
        })
        ncube.clearSha1()
        updateCube(ncube)
    }

    void clearTestDatabase()
    {
        if (NCubeAppContext.test)
        {
            persister.clearTestDatabase(getUserId())
        }
        else
        {
            throw new IllegalStateException('clearTestDatabase() is only available during testing.')
        }
    }

    // ---------------------- Broadcast APIs for notifying other services in cluster of cache changes ------------------
    protected void broadcast(ApplicationID appId)
    {
        // Write to 'system' tenant, 'NCE' app, version '0.0.0', SNAPSHOT, cube: sys.cache
        // Separate thread reads from this table every 1 second, for new commands, for
        // example, clear cache
        appId.toString()
    }

    // --------------------------------------- Permissions -------------------------------------------------------------

    /**
     * Assert that the requested permission is allowed.  Throw a SecurityException if not.
     */
    Boolean assertPermissions(ApplicationID appId, String resource, Action action = Action.READ)
    {
        action = action ?: Action.READ
        if (systemRequest || checkPermissions(appId, resource, action.name()))
        {
            return true
        }
        throw new SecurityException("Operation not performed.  You do not have ${action} permission to ${resource}, app: ${appId}")
    }

    protected boolean assertNotLockBlocked(ApplicationID appId)
    {
        String lockedBy = getAppLockedBy(appId)
        if (lockedBy == null || lockedBy == getUserId())
        {
            return true
        }
        throw new SecurityException("Application is not locked by you, app: ${appId}")
    }

    private void assertLockedByMe(ApplicationID appId)
    {
        final ApplicationID bootAppId = getBootAppId(appId)
        final NCube sysLockCube = loadCubeInternal(bootAppId, SYS_LOCK)
        if (sysLockCube == null)
        {   // If there is no sys.lock cube, then no permissions / locking being used.
            if (NCubeAppContext.test)
            {
                return
            }
            throw new SecurityException("Application is not locked by you, no sys.lock n-cube exists in app: ${appId}")
        }

        final String lockOwner = getAppLockedBy(bootAppId)
        if (getUserId() == lockOwner)
        {
            return
        }
        throw new SecurityException("Application is not locked by you, app: ${appId}")
    }

    private ApplicationID getBootAppId(ApplicationID appId)
    {
        return new ApplicationID(appId.tenant, appId.app, '0.0.0', ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
    }

    private String getPermissionCacheKey(String resource, Action action)
    {
        final String sep = '/'
        final StringBuilder builder = new StringBuilder()
        builder.append(getUserId())
        builder.append(sep)
        builder.append(resource)
        builder.append(sep)
        builder.append(action)
        return builder.toString()
    }

    private static Boolean checkPermissionCache(Cache cache, String key)
    {
        Cache.ValueWrapper item = cache.get(key)
        return item?.get()
    }

    Map checkMultiplePermissions(ApplicationID appId, String resource, String[] actions)
    {
        Map ret = [:]
        for (String action : actions)
        {
            ret[action] = checkPermissions(appId, resource, action)
        }
        return ret
    }

    /**
     * Verify whether the action can be performed against the resource (typically cube name).
     * @param appId ApplicationID containing the n-cube being checked.
     * @param resource String cubeName or cubeName with wildcards('*' or '?') or cubeName / axisName (with wildcards).
     * @param action Action To be attempted.
     * @return boolean true if allowed, false if not.  If the permissions cubes restricting access have not yet been
     * added to the same App, then all access is granted.
     */
    Boolean checkPermissions(ApplicationID appId, String resource, String actionName)
    {
        Action action = Action.valueOf(actionName.toUpperCase())
        Cache permCache = permCacheManager.getCache(appId.cacheKey())
        String key = getPermissionCacheKey(resource, action)
        Boolean allowed = checkPermissionCache(permCache, key)
        if (allowed instanceof Boolean)
        {
            return allowed
        }

        if (Action.READ == action && SYS_LOCK.equalsIgnoreCase(resource))
        {
            permCache.put(key, true)
            return true
        }

        ApplicationID bootVersion = getBootAppId(appId)
        NCube permCube = loadCubeInternal(bootVersion, SYS_PERMISSIONS)
        if (permCube == null)
        {   // Allow everything if no permissions are set up.
            permCache.put(key, true)
            return true
        }

        NCube userToRole = loadCubeInternal(bootVersion, SYS_USERGROUPS)
        if (userToRole == null)
        {   // Allow everything if no user roles are set up.
            permCache.put(key, true)
            return true
        }

        // Step 1: Get user's roles
        Set<String> roles = getRolesForUser(userToRole)

        if (!roles.contains(ROLE_ADMIN) && CUBE_MUTATE_ACTIONS.contains(action))
        {   // If user is not an admin, check branch permissions.
            NCube branchPermCube = loadCubeInternal(bootVersion.asBranch(appId.branch), SYS_BRANCH_PERMISSIONS)
            if (branchPermCube != null && !checkBranchPermission(branchPermCube, resource))
            {
                permCache.put(key, false)
                return false
            }
        }

        // Step 2: Make sure one of the user's roles allows access
        final String actionNameLower = action.lower()
        for (String role : roles)
        {
            if (checkResourcePermission(permCube, role, resource, actionNameLower))
            {
                permCache.put(key, true)
                return true
            }
        }

        permCache.put(key, false)
        return false
    }

    /**
     * Faster permissions check that should be used when filtering a list of n-cubes.  Before calling this
     * API, call getPermInfo(AppId) to get the 'permInfo' Map to be used in this API.
     */
    private boolean fastCheckPermissions(ApplicationID appId, String resource, Action action, Map permInfo)
    {
        Cache permCache = permCacheManager.getCache(appId.cacheKey())
        String key = getPermissionCacheKey(resource, action)
        Boolean allowed = checkPermissionCache(permCache, key)
        if (allowed instanceof Boolean)
        {
            return allowed
        }

        if (Action.READ == action && SYS_LOCK.equalsIgnoreCase(resource))
        {
            permCache.put(key, true)
            return true
        }

        Set<String> roles = permInfo.roles as Set
        if (!roles.contains(ROLE_ADMIN) && CUBE_MUTATE_ACTIONS.contains(action))
        {   // If user is not an admin, check branch permissions.
            NCube branchPermCube = (NCube)permInfo.branchPermCube
            if (branchPermCube != null && !checkBranchPermission(branchPermCube, resource))
            {
                permCache.put(key, false)
                return false
            }
        }

        // Step 2: Make sure one of the user's roles allows access
        final String actionName = action.lower()
        NCube permCube = permInfo.permCube as NCube
        for (String role : roles)
        {
            if (checkResourcePermission(permCube, role, resource, actionName))
            {
                permCache.put(key, true)
                return true
            }
        }

        permCache.put(key, false)
        return false
    }

    private Map getPermInfo(ApplicationID appId)
    {
        Map<String, Object> info = [skipPermCheck:false] as Map
        ApplicationID bootVersion = getBootAppId(appId)
        info.bootVersion = bootVersion
        NCube permCube = loadCubeInternal(bootVersion, SYS_PERMISSIONS)
        if (permCube == null)
        {   // Allow everything if no permissions are set up.
            info.skipPermCheck = true
        }
        info.permCube = permCube

        NCube userToRole = loadCubeInternal(bootVersion, SYS_USERGROUPS)
        if (userToRole == null)
        {   // Allow everything if no user roles are set up.
            info.skipPermCheck = true
        }
        else
        {
            info.roles = getRolesForUser(userToRole)
        }

        info.branch000 = bootVersion.asBranch(appId.branch)
        info.branchPermCube = loadCubeInternal((ApplicationID) info.branch000, SYS_BRANCH_PERMISSIONS)
        return info
    }

    private boolean checkBranchPermission(NCube branchPermissions, String resource)
    {
        final List<Column> resourceColumns = getResourcesToMatch(branchPermissions, resource)
        final String userId = getUserId()
        final Column column = resourceColumns.find { branchPermissions.getCell([resource: it.value, user: userId])}
        return column != null
    }

    private boolean checkResourcePermission(NCube resourcePermissions, String role, String resource, String action)
    {
        final List<Column> resourceColumns = getResourcesToMatch(resourcePermissions, resource)
        final Column column = resourceColumns.find {resourcePermissions.getCell([(AXIS_ROLE): role, resource: it.value, action: action]) }
        return column != null
    }

    private Set<String> getRolesForUser(NCube userGroups)
    {
        Axis role = userGroups.getAxis(AXIS_ROLE)
        Set<String> groups = new HashSet()
        for (Column column : role.columns)
        {
            if (userGroups.getCell([(AXIS_ROLE): column.value, (AXIS_USER): getUserId()]))
            {
                groups.add(column.value as String)
            }
        }
        return groups
    }

    private List<Column> getResourcesToMatch(NCube permCube, String resource)
    {
        List<Column> matches = []
        Axis resourcePermissionAxis = permCube.getAxis(AXIS_RESOURCE)
        if (resource != null)
        {
            String[] splitResource = resource.split('/')
            boolean shouldCheckAxis = splitResource.length > 1
            String resourceCube = splitResource[0]
            String resourceAxis = shouldCheckAxis ? splitResource[1] : null

            for (Column resourcePermissionColumn : resourcePermissionAxis.columnsWithoutDefault)
            {
                String columnResource = resourcePermissionColumn.value
                String[] curSplitResource = columnResource.split('/')
                boolean resourceIncludesAxis = curSplitResource.length > 1
                String curResourceCube = curSplitResource[0]
                String curResourceAxis = resourceIncludesAxis ? curSplitResource[1] : null
                boolean resourceMatchesCurrentResource = doStringsWithWildCardsMatch(resourceCube, curResourceCube)

                if ((shouldCheckAxis && resourceMatchesCurrentResource && doStringsWithWildCardsMatch(resourceAxis, curResourceAxis))
                        || (!shouldCheckAxis && !resourceIncludesAxis && resourceMatchesCurrentResource))
                {
                    matches << resourcePermissionColumn
                }
            }
        }
        if (matches.empty)
        {
            matches.add(resourcePermissionAxis.defaultColumn)
        }
        return matches
    }

    private boolean doStringsWithWildCardsMatch(String text, String pattern)
    {
        if (pattern == null)
        {
            return false
        }

        Pattern p = wildcards[pattern]
        if (p != null)
        {
            return p.matcher(text).matches()
        }

        String regexString = '(?i)' + StringUtilities.wildcardToRegexString(pattern)
        p = Pattern.compile(regexString)
        wildcards[pattern] = p
        return p.matcher(text).matches()
    }

    Boolean isAppAdmin(ApplicationID appId)
    {
        NCube userCube = loadCubeInternal(getBootAppId(appId), SYS_USERGROUPS)
        if (userCube == null)
        {   // Allow everything if no permissions are set up.
            return true
        }
        return isUserInGroup(userCube, ROLE_ADMIN)
    }

    private boolean isUserInGroup(NCube userCube, String groupName)
    {
        return userCube.getCell([(AXIS_ROLE): groupName, (AXIS_USER): null]) || userCube.getCell([(AXIS_ROLE): groupName, (AXIS_USER): getUserId()])
    }

    protected void detectNewAppId(ApplicationID appId)
    {
        if (!persister.doCubesExist(appId, true, 'detectNewAppId', getUserId()))
        {
            addAppPermissionsCubes(appId)
            if (!appId.head)
            {
                addBranchPermissionsCube(appId)
            }
        }
    }

    private void addBranchPermissionsCube(ApplicationID appId)
    {
        ApplicationID permAppId = appId.asVersion('0.0.0')
        if (loadCubeInternal(permAppId, SYS_BRANCH_PERMISSIONS) != null)
        {
            return
        }

        String userId = getUserId()
        NCube branchPermCube = new NCube(SYS_BRANCH_PERMISSIONS)
        branchPermCube.applicationID = permAppId
        branchPermCube.defaultCellValue = false

        Axis resourceAxis = new Axis(AXIS_RESOURCE, AxisType.DISCRETE, AxisValueType.STRING, true)
        resourceAxis.addColumn(SYS_BRANCH_PERMISSIONS)
        branchPermCube.addAxis(resourceAxis)

        Axis userAxis = new Axis(AXIS_USER, AxisType.DISCRETE, AxisValueType.STRING, true)
        userAxis.addColumn(userId)
        branchPermCube.addAxis(userAxis)

        branchPermCube.setCell(true, [(AXIS_USER):userId, (AXIS_RESOURCE):SYS_BRANCH_PERMISSIONS])
        branchPermCube.setCell(true, [(AXIS_USER):userId, (AXIS_RESOURCE):null])

        persister.createCube(branchPermCube, getUserId())
        updateBranch(permAppId)
    }

    private void addAppPermissionsCubes(ApplicationID appId)
    {
        ApplicationID permAppId = getBootAppId(appId)
        addAppUserGroupsCube(permAppId)
        addAppPermissionsCube(permAppId)
        addSysLockingCube(permAppId)
    }

    private void addSysLockingCube(ApplicationID appId)
    {
        if (loadCubeInternal(appId, SYS_LOCK) != null)
        {
            return
        }

        NCube sysLockCube = new NCube(SYS_LOCK)
        sysLockCube.applicationID = appId
        sysLockCube.setMetaProperty(PROPERTY_CACHE, false)
        sysLockCube.addAxis(new Axis(AXIS_SYSTEM, AxisType.DISCRETE, AxisValueType.STRING, true))
        persister.createCube(sysLockCube, getUserId())
    }

    /**
     * Determine if the ApplicationID is locked.  This is an expensive call because it
     * always hits the database.  Use judiciously (obtain value before loops, etc.)
     */
    String getAppLockedBy(ApplicationID appId)
    {
        NCube sysLockCube = loadCubeInternal(getBootAppId(appId), SYS_LOCK)
        if (sysLockCube == null)
        {
            return null
        }
        return sysLockCube.getCell([(AXIS_SYSTEM):null])
    }

    /**
     * Lock the given appId so that no changes can be made to any cubes within it
     * @param appId ApplicationID to lock
     * @param shouldLock - true to lock, false to unlock
     */
    Boolean lockApp(ApplicationID appId, boolean shouldLock)
    {
        assertPermissions(appId, null, Action.RELEASE)
        String userId = getUserId()
        ApplicationID bootAppId = getBootAppId(appId)
        NCube sysLockCube = loadCubeInternal(bootAppId, SYS_LOCK)
        if (sysLockCube == null)
        {
            return false
        }
        if (shouldLock)
        {
            String lockOwner = getAppLockedBy(appId)
            if (userId == lockOwner)
            {
                return false
            }
            if (lockOwner != null)
            {
                throw new SecurityException("Application ${appId} already locked by ${lockOwner}")
            }
            sysLockCube.setCell(userId, [(AXIS_SYSTEM):null])
        }
        else
        {
            String lockOwner = getAppLockedBy(appId)
            if (userId != lockOwner && !isAppAdmin(appId))
            {
                throw new SecurityException("Application ${appId} locked by ${lockOwner}")
            }
            sysLockCube.removeCell([(AXIS_SYSTEM):null])
        }
        persister.updateCube(sysLockCube, getUserId())
        return true
    }

    private void addAppUserGroupsCube(ApplicationID appId)
    {
        if (loadCubeInternal(appId, SYS_USERGROUPS) != null)
        {
            return
        }

        String userId = getUserId()
        NCube userGroupsCube = new NCube(SYS_USERGROUPS)
        userGroupsCube.applicationID = appId
        userGroupsCube.defaultCellValue = false

        Axis userAxis = new Axis(AXIS_USER, AxisType.DISCRETE, AxisValueType.STRING, true)
        userAxis.addColumn(userId)
        userGroupsCube.addAxis(userAxis)

        Axis roleAxis = new Axis(AXIS_ROLE, AxisType.DISCRETE, AxisValueType.STRING, false)
        roleAxis.addColumn(ROLE_ADMIN)
        roleAxis.addColumn(ROLE_READONLY)
        roleAxis.addColumn(ROLE_USER)
        userGroupsCube.addAxis(roleAxis)

        userGroupsCube.setCell(true, [(AXIS_USER):userId, (AXIS_ROLE):ROLE_ADMIN])
        userGroupsCube.setCell(true, [(AXIS_USER):userId, (AXIS_ROLE):ROLE_USER])
        userGroupsCube.setCell(true, [(AXIS_USER):null, (AXIS_ROLE):ROLE_USER])

        persister.createCube(userGroupsCube, getUserId())
    }

    private void addAppPermissionsCube(ApplicationID appId)
    {
        if (loadCubeInternal(appId, SYS_PERMISSIONS))
        {
            return
        }

        boolean isSysApp = appId.app == SYS_APP

        NCube appPermCube = new NCube(SYS_PERMISSIONS)
        appPermCube.applicationID = appId
        appPermCube.defaultCellValue = false

        Axis resourceAxis = new Axis(AXIS_RESOURCE, AxisType.DISCRETE, AxisValueType.STRING, true)
        resourceAxis.addColumn(SYS_PERMISSIONS)
        resourceAxis.addColumn(SYS_USERGROUPS)
        resourceAxis.addColumn(SYS_BRANCH_PERMISSIONS)
        resourceAxis.addColumn(SYS_LOCK)
        if (isSysApp)
        {
            resourceAxis.addColumn(SYS_TRANSACTIONS)
        }
        appPermCube.addAxis(resourceAxis)

        Axis roleAxis = new Axis(AXIS_ROLE, AxisType.DISCRETE, AxisValueType.STRING, false)
        roleAxis.addColumn(ROLE_ADMIN)
        roleAxis.addColumn(ROLE_READONLY)
        roleAxis.addColumn(ROLE_USER)
        appPermCube.addAxis(roleAxis)

        Axis actionAxis = new Axis(AXIS_ACTION, AxisType.DISCRETE, AxisValueType.STRING, false)
        actionAxis.addColumn(Action.UPDATE.lower(), null, null, [(Column.DEFAULT_VALUE):true as Object])
        actionAxis.addColumn(Action.READ.lower(), null, null, [(Column.DEFAULT_VALUE):true as Object])
        actionAxis.addColumn(Action.RELEASE.lower())
        actionAxis.addColumn(Action.COMMIT.lower())
        appPermCube.addAxis(actionAxis)

        appPermCube.setCell(false, [(AXIS_RESOURCE):SYS_BRANCH_PERMISSIONS, (AXIS_ROLE):ROLE_READONLY, (AXIS_ACTION):Action.UPDATE.lower()])

        appPermCube.setCell(false, [(AXIS_RESOURCE):SYS_PERMISSIONS, (AXIS_ROLE):ROLE_READONLY, (AXIS_ACTION):Action.UPDATE.lower()])
        appPermCube.setCell(true, [(AXIS_RESOURCE):SYS_PERMISSIONS, (AXIS_ROLE):ROLE_ADMIN, (AXIS_ACTION):Action.COMMIT.lower()])

        appPermCube.setCell(false, [(AXIS_RESOURCE):SYS_USERGROUPS, (AXIS_ROLE):ROLE_READONLY, (AXIS_ACTION):Action.UPDATE.lower()])
        appPermCube.setCell(true, [(AXIS_RESOURCE):SYS_USERGROUPS, (AXIS_ROLE):ROLE_ADMIN, (AXIS_ACTION):Action.COMMIT.lower()])

        appPermCube.setCell(false, [(AXIS_RESOURCE):SYS_LOCK, (AXIS_ROLE):ROLE_READONLY, (AXIS_ACTION):Action.UPDATE.lower()])
        appPermCube.setCell(true, [(AXIS_RESOURCE):SYS_LOCK, (AXIS_ROLE):ROLE_ADMIN, (AXIS_ACTION):Action.COMMIT.lower()])

        appPermCube.setCell(true, [(AXIS_RESOURCE):null, (AXIS_ROLE):ROLE_ADMIN, (AXIS_ACTION):Action.RELEASE.lower()])
        appPermCube.setCell(true, [(AXIS_RESOURCE):null, (AXIS_ROLE):ROLE_ADMIN, (AXIS_ACTION):Action.COMMIT.lower()])
        appPermCube.setCell(true, [(AXIS_RESOURCE):null, (AXIS_ROLE):ROLE_USER, (AXIS_ACTION):Action.COMMIT.lower()])
        appPermCube.setCell(false, [(AXIS_RESOURCE):null, (AXIS_ROLE):ROLE_READONLY, (AXIS_ACTION):Action.UPDATE.lower()])

        if (isSysApp)
        {
            appPermCube.setCell(true, [(AXIS_RESOURCE):SYS_TRANSACTIONS, (AXIS_ROLE):ROLE_ADMIN, (AXIS_ACTION):Action.RELEASE.lower()])
            appPermCube.setCell(true, [(AXIS_RESOURCE):SYS_TRANSACTIONS, (AXIS_ROLE):ROLE_ADMIN, (AXIS_ACTION):Action.COMMIT.lower()])
            appPermCube.setCell(false, [(AXIS_RESOURCE):SYS_TRANSACTIONS, (AXIS_ROLE):ROLE_USER, (AXIS_ACTION):Action.UPDATE.lower()])
            appPermCube.setCell(false, [(AXIS_RESOURCE):SYS_TRANSACTIONS, (AXIS_ROLE):ROLE_USER, (AXIS_ACTION):Action.READ.lower()])
            appPermCube.setCell(false, [(AXIS_RESOURCE):SYS_TRANSACTIONS, (AXIS_ROLE):ROLE_READONLY, (AXIS_ACTION):Action.UPDATE.lower()])
            appPermCube.setCell(false, [(AXIS_RESOURCE):SYS_TRANSACTIONS, (AXIS_ROLE):ROLE_READONLY, (AXIS_ACTION):Action.READ.lower()])
        }

        persister.createCube(appPermCube, getUserId())
    }

    /**
     * Set the user ID on the current thread
     * @param user String user Id
     */
    void setUserId(String user)
    {
        userId.set(user?.trim())
    }

    /**
     * Retrieve the user ID from the current thread
     * @return String user ID of the user associated to the requesting thread
     */
    String getUserId()
    {
        return userId.get()
    }

    /**
     * Set whether permissions should be checked on the current thread
     * @param boolean isSystemRequest
     */
    void setSystemRequest(boolean isSystemRequest)
    {
        this.isSystemRequest.set(isSystemRequest)
    }

    /**
     * Retrieve whether permissions should be checked on the current thread
     * @return boolean
     */
    boolean isSystemRequest()
    {
        return isSystemRequest.get()
    }

    /**
     * Add wild card symbol at beginning and at end of string if not already present.
     * Remove wild card symbol if only character present.
     * @return String
     */
    private String handleWildCard(String value)
    {
        if (value)
        {
            if (!value.startsWith('*'))
            {
                value = '*' + value
            }
            if (!value.endsWith('*'))
            {
                value += '*'
            }
            if ('*' == value)
            {
                value = null
            }
        }
        return value
    }
    // --------------------------------------- Version Control ---------------------------------------------------------

    /**
     * Update a branch from the HEAD.  Changes from the HEAD are merged into the
     * supplied branch.  If the merge cannot be done perfectly, an exception is
     * thrown indicating the cubes that are in conflict.
     */
    List<NCubeInfoDto> getHeadChangesForBranch(ApplicationID appId)
    {
        ApplicationID.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        assertNotLockBlocked(appId)
        assertPermissions(appId, null, Action.READ)

        ApplicationID headAppId = appId.asHead()
        List<NCubeInfoDto> records = search(appId, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):false])
        Map<String, NCubeInfoDto> branchRecordMap = new CaseInsensitiveMap<>()

        for (NCubeInfoDto info : records)
        {
            branchRecordMap[info.name] = info
        }

        List<NCubeInfoDto> headRecords = search(headAppId, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):false])
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
                        if (infoRev < 0)
                        {   // If branch cube was changed and then deleted...
                            // don't care
                        }
                        else
                        {
                            head.changeType = ChangeType.CONFLICT.code
                            cubeDiffs.add(head)
                        }
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
    List<NCubeInfoDto> getBranchChangesForHead(ApplicationID appId)
    {
        ApplicationID.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        assertNotLockBlocked(appId)
        assertPermissions(appId, null, Action.READ)

        ApplicationID headAppId = appId.asHead()
        Map<String, NCubeInfoDto> headMap = new CaseInsensitiveMap<>()

        List<NCubeInfoDto> branchList = search(appId, null, null, [(SEARCH_CHANGED_RECORDS_ONLY):true])
        List<NCubeInfoDto> headList = search(headAppId, null, null, null)   // active and deleted
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
    List<NCubeInfoDto> getBranchChangesForMyBranch(ApplicationID appId, String branch)
    {
        ApplicationID branchAppId = appId.asBranch(branch)
        ApplicationID.validateAppId(appId)
        ApplicationID.validateAppId(branchAppId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        assertNotLockBlocked(appId)
        assertPermissions(appId, null, Action.READ)
        assertPermissions(branchAppId, null, Action.READ)

        List<NCubeInfoDto> records = search(appId, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):false])
        Map<String, NCubeInfoDto> branchRecordMap = new CaseInsensitiveMap<>()

        for (NCubeInfoDto info : records)
        {
            branchRecordMap[info.name] = info
        }

        List<NCubeInfoDto> otherBranchRecords = search(branchAppId, null, null, [(SEARCH_ACTIVE_RECORDS_ONLY):false])
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
    Map<String, Object> updateBranch(ApplicationID appId, Object[] cubeDtos = null)
    {
        if (cubeDtos != null && cubeDtos.length == 0)
        {
            throw new IllegalArgumentException('Nothing selected for update.')
        }
        ApplicationID.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        assertNotLockBlocked(appId)
        assertPermissions(appId, null, Action.UPDATE)

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
                            NCubeInfoDto mergedDto = persister.commitMergedCubeToBranch(appId, cube1, updateCube.sha1, getUserId(), txId)
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
                    persister.updateBranchCubeHeadSha1((Long) Converter.convert(branchCube.id, Long.class), branchCube.sha1, updateCube.sha1, getUserId())
                    fastforwards.add(updateCube)
                    break
                case ChangeType.CONFLICT.code:
                    rejects.add(updateCube)
                    break
                default:
                    throw new IllegalArgumentException('No change type on passed in cube to update.')
            }
        }
        finalUpdates = persister.pullToBranch(appId, buildIdList(updates), getUserId(), txId)
        finalUpdates.addAll(merges)
        Map<String, Object> ret = [:]
        ret[BRANCH_ADDS] = persister.pullToBranch(appId, buildIdList(adds), getUserId(), txId)
        ret[BRANCH_DELETES] = persister.pullToBranch(appId, buildIdList(deletes), getUserId(), txId)
        ret[BRANCH_UPDATES] = finalUpdates
        ret[BRANCH_RESTORES] = persister.pullToBranch(appId, buildIdList(restores), getUserId(), txId)
        ret[BRANCH_FASTFORWARDS] = fastforwards
        ret[BRANCH_REJECTS] = rejects
        return ret
    }

    String getTenant()
    {
        return ApplicationID.DEFAULT_TENANT
    }

    String generatePullRequestHash(ApplicationID appId, Object[] infoDtos)
    {
        ApplicationID sysAppId = new ApplicationID(tenant, SYS_APP, SYS_BOOT_VERSION, ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
        List<Map<String, String>> commitRecords = getCommitRecords(appId, infoDtos)

        if (commitRecords.empty)
        {
            throw new IllegalArgumentException('A pull request cannot be created because there are no cubes to be committed.')
        }

        commitRecords.sort(true, {Map it -> it.id})
        String prInfoJson = JsonWriter.objectToJson(commitRecords)
        String sha1 = EncryptionUtilities.calculateSHA1Hash(prInfoJson.getBytes('UTF-8'))

        if (getCube(sysAppId, 'tx.' + sha1))
        {
            throw new IllegalArgumentException('A pull request already exists for this change set.')
        }

        NCube prCube = new NCube("tx.${sha1}")
        prCube.applicationID = sysAppId
        prCube.addAxis(new Axis(PR_PROP, AxisType.DISCRETE, AxisValueType.STRING, false, Axis.DISPLAY, 1))
        prCube.addColumn(PR_PROP, PR_STATUS)
        prCube.addColumn(PR_PROP, PR_APP)
        prCube.addColumn(PR_PROP, PR_CUBES)
        prCube.addColumn(PR_PROP, PR_REQUESTER)
        prCube.addColumn(PR_PROP, PR_REQUEST_TIME)
        prCube.addColumn(PR_PROP, PR_ID)
        prCube.addColumn(PR_PROP, PR_MERGER)
        prCube.addColumn(PR_PROP, PR_MERGE_TIME)
        prCube.setCell(PR_OPEN, [(PR_PROP):PR_STATUS])
        prCube.setCell(appId.toString(), [(PR_PROP):PR_APP])
        prCube.setCell(prInfoJson, [(PR_PROP):PR_CUBES])
        prCube.setCell(getUserId(), [(PR_PROP):PR_REQUESTER])
        prCube.setCell(new Date().format('M/d/yyyy HH:mm:ss'), [(PR_PROP):PR_REQUEST_TIME])

        runSystemRequest { createCube(prCube) }
        return sha1
    }

    private def runSystemRequest(Closure closure)
    {
        try
        {
            systemRequest = true
            return closure()
        }
        finally
        {
            systemRequest = false
        }
    }

    Map<String, Object> mergePullRequest(String prId)
    {
        NCube prCube = loadPullRequestCube(prId)

        String status = prCube.getCell([(PR_PROP):PR_STATUS])
        String appIdString = prCube.getCell([(PR_PROP):PR_APP])
        ApplicationID prAppId = ApplicationID.convert(appIdString)
        String requestUser = prCube.getCell([(PR_PROP): PR_REQUESTER])
        String commitUser = prCube.getCell([(PR_PROP): PR_MERGER])

        if (status.contains(PR_CLOSED) || status == PR_OBSOLETE)
        {
            throw new IllegalStateException("Pull request already closed. Status: ${status}, Requested by: ${requestUser}, Committed by: ${commitUser}, ApplicationID: ${prAppId}")
        }
        else if (!persister.doCubesExist(prAppId, true, 'detectNewAppId', getUserId()))
        {
            throw new IllegalStateException("Branch no longer exists; pull request will be marked obsolete. Requested by: ${requestUser}, ApplicationID: ${prAppId}")
        }

        String prInfoJson = prCube.getCell([(PR_PROP):PR_CUBES]) as String

        Object[] prDtos = null
        if (prInfoJson != null)
        {
            List<Map<String, String>> prInfo = JsonReader.jsonToJava(prInfoJson) as List
            Object[] allDtos = getBranchChangesForHead(prAppId)
            prDtos = allDtos.findAll {
                NCubeInfoDto dto = it as NCubeInfoDto
                prInfo.find { Map<String, String> info ->
                    if (info.name == dto.name)
                    {
                        if (info.id == dto.id)
                        {
                            return info
                        }
                        throw new IllegalStateException("Cube has been changed since request was made; pull request will be marked obsolete. Requested by: ${requestUser}, ApplicationID: ${prAppId}, Cube: ${dto.name}")
                    }
                }
            }
            prInfo.any { Map<String, String> info ->
                Object foundDto = prDtos.find {
                    NCubeInfoDto dto = it as NCubeInfoDto
                    info.name == dto.name
                }
                if (!foundDto)
                {
                    throw new IllegalStateException("Cube no longer valid; pull request will be marked obsolete. Requested by: ${requestUser}, ApplicationID: ${prAppId}, Cube: ${info.name}")
                }
            }
        }

        Map ret = commitBranchFromRequest(prAppId, prDtos, requestUser)
        ret[PR_APP] = prAppId
        ret[PR_CUBE] = prCube

        updatePullRequest(prId, null, null, PR_COMPLETE)
        return ret
    }

    NCube obsoletePullRequest(String prId)
    {
        Closure exceptionTest = { String status ->
            return status == PR_OBSOLETE
        }
        String exceptionText = 'Pull request is already obsolete.'
        NCube prCube = updatePullRequest(prId, exceptionTest, exceptionText, PR_OBSOLETE)
        return prCube
    }

    NCube cancelPullRequest(String prId)
    {
        Closure exceptionTest = { String status ->
            return status.contains(PR_CLOSED) || status == PR_OBSOLETE
        }
        String exceptionText = 'Pull request is already closed.'
        NCube prCube = updatePullRequest(prId, exceptionTest, exceptionText, PR_CANCEL)
        return prCube
    }

    NCube reopenPullRequest(String prId)
    {
        Closure exceptionTest = { String status ->
            return [PR_COMPLETE, PR_OBSOLETE, PR_OPEN].contains(status)
        }
        String exceptionText = 'Unable to reopen pull request.'
        NCube prCube = updatePullRequest(prId, exceptionTest, exceptionText, PR_OPEN, true)
        return prCube
    }

    private void fillPullRequestUpdateInfo(NCube prCube, String status)
    {
        prCube.setCell(status, [(PR_PROP):PR_STATUS])
        prCube.setCell(getUserId(), [(PR_PROP):PR_MERGER])
        prCube.setCell(new Date().format('M/d/yyyy HH:mm:ss'), [(PR_PROP):PR_MERGE_TIME])
    }

    private NCube updatePullRequest(String prId, Closure exceptionTest, String exceptionText, String newStatus, boolean bumpVersion = false)
    {
        NCube prCube = loadPullRequestCube(prId)

        String status = prCube.getCell([(PR_PROP):PR_STATUS])
        if (exceptionTest && exceptionTest(status))
        {
            String requestUser = prCube.getCell([(PR_PROP): PR_REQUESTER])
            String appIdString = prCube.getCell([(PR_PROP):PR_APP])
            ApplicationID prAppId = ApplicationID.convert(appIdString)
            throw new IllegalArgumentException("${exceptionText} Status: ${status}, Requested by: ${requestUser}, ApplicationID: ${prAppId}")
        }

        if (bumpVersion)
        {
            Object appIdObj = prCube.getCell([(PR_PROP):PR_APP])
            ApplicationID appId = appIdObj instanceof ApplicationID ? appIdObj as ApplicationID : ApplicationID.convert(appIdObj as String)
            ApplicationID newAppId = appId.asVersion(getLatestVersion(appId))
            prCube.setCell(newAppId.toString(), [(PR_PROP):PR_APP])
        }
        fillPullRequestUpdateInfo(prCube, newStatus)
        runSystemRequest { updateCube(prCube, true) }
        return prCube
    }

    private NCube loadPullRequestCube(String prId)
    {
        runSystemRequest {
            ApplicationID sysAppId = new ApplicationID(tenant, SYS_APP, SYS_BOOT_VERSION, ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
            List<NCubeInfoDto> dtos = search(sysAppId, "tx.${prId}", null, [(SEARCH_ACTIVE_RECORDS_ONLY): true, (SEARCH_EXACT_MATCH_NAME): true])
            if (dtos.empty) {
                throw new IllegalArgumentException("Invalid pull request id: ${prId}")
            }
            NCube prCube = loadCubeById(dtos.first().id as long)
            return prCube
        } as NCube
    }

    Object[] getPullRequests(Date startDate = null, Date endDate = null)
    {
        List<Map> results = []
        List<NCube> cubes = getPullRequestCubes(startDate, endDate)
        for (NCube cube : cubes)
        {
            Map prInfo = cube.getMap([(PR_PROP):[] as Set])
            prInfo[PR_APP] = ApplicationID.convert(prInfo[PR_APP] as String)
            prInfo[PR_CUBES] = JsonReader.jsonToJava(prInfo[PR_CUBES] as String)
            prInfo[PR_TXID] = cube.name.substring(3)
            results.add(prInfo)
        }
        results.sort(true, {Map a, Map b -> Converter.convert(b[PR_REQUEST_TIME], Date.class) as Date <=> Converter.convert(a[PR_REQUEST_TIME], Date.class) as Date})
        return results as Object[]
    }

    private List<NCube> getPullRequestCubes(Date startDate, Date endDate)
    {
        ApplicationID sysAppId = new ApplicationID(tenant, SYS_APP, SYS_BOOT_VERSION, ReleaseStatus.SNAPSHOT.name(), ApplicationID.HEAD)
        Map options = [(SEARCH_ACTIVE_RECORDS_ONLY):true, (SEARCH_CREATE_DATE_START):startDate, (SEARCH_CREATE_DATE_END):endDate]
        runSystemRequest { cubeSearch(sysAppId, 'tx.*', null, options) } as List<NCube>
    }

    /**
     * Commit the passed in changed cube records identified by NCubeInfoDtos.
     * @return array of NCubeInfoDtos that are to be committed.
     */
    Map<String, Object> commitBranch(ApplicationID appId, Object[] inputCubes = null)
    {
        String prId = generatePullRequestHash(appId, inputCubes)
        return mergePullRequest(prId)
    }

    private Map<String, Object> commitBranchFromRequest(ApplicationID appId, Object[] inputCubes, String requestUser)
    {
        List<NCubeInfoDto> adds = []
        List<NCubeInfoDto> deletes = []
        List<NCubeInfoDto> updates = []
        List<NCubeInfoDto> merges = []
        List<NCubeInfoDto> restores = []
        List<NCubeInfoDto> rejects = []
        List<NCubeInfoDto> finalUpdates

        long txId = UniqueIdGenerator.uniqueId
        List<NCubeInfoDto> cubesToUpdate = getCubesToUpdate(appId, inputCubes, rejects, true)

        String commitAction = Action.COMMIT.name()
        String readAction = Action.READ.name()
        ApplicationID headAppId = appId.asHead()
        for (NCubeInfoDto updateCube : cubesToUpdate)
        {
            String cubeName = updateCube.name
            if (!checkPermissions(headAppId, cubeName, commitAction) || !checkPermissions(appId, cubeName, readAction))
            {
                rejects.add(updateCube)
                continue
            }

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
                            NCubeInfoDto mergedDto = persister.commitMergedCubeToHead(appId, cube, getUserId(), txId)
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

        finalUpdates = persister.commitCubes(appId, buildIdList(updates), getUserId(), requestUser, txId)
        finalUpdates.addAll(merges)
        Map<String, Object> ret = [:]
        ret[BRANCH_ADDS] = persister.commitCubes(appId, buildIdList(adds), getUserId(), requestUser, txId)
        ret[BRANCH_DELETES] = persister.commitCubes(appId, buildIdList(deletes), getUserId(), requestUser, txId)
        ret[BRANCH_UPDATES] = finalUpdates
        ret[BRANCH_RESTORES] = persister.commitCubes(appId, buildIdList(restores), getUserId(), requestUser, txId)
        ret[BRANCH_REJECTS] = rejects

        if (!rejects.empty)
        {
            int rejectSize = rejects.size()
            String errorMessage = "Unable to commit ${rejectSize} ${rejectSize == 1 ? 'cube' : 'cubes'}."
            throw new BranchMergeException(errorMessage, ret)
        }
        return ret
    }

    private List<Map<String, String>> getCommitRecords(ApplicationID appId, Object[] inputCubes)
    {
        List<Map<String, String>> commitRecords = []
        List<NCubeInfoDto> rejects = []
        List<NCubeInfoDto> cubesToUpdate = getCubesToUpdate(appId, inputCubes, rejects)
        ApplicationID headAppId = appId.asHead()

        String commitAction = Action.COMMIT.name()
        for (NCubeInfoDto updateCube : cubesToUpdate)
        {
            if (!checkPermissions(appId, updateCube.name, commitAction) || updateCube.changeType == ChangeType.CONFLICT.code)
            {
                rejects.add(updateCube)
            }
            String headId = null
            if (updateCube.headSha1)
            {
                NCubeInfoDto headDto = search(headAppId, updateCube.name, null, [(SEARCH_ACTIVE_RECORDS_ONLY): false, (SEARCH_EXACT_MATCH_NAME): true]).first()
                headId = headDto.id
            }
            commitRecords.add([name: updateCube.name, changeType: updateCube.changeType, id: updateCube.id, head: headId])
        }

        Map<String, Object> ret = [:]
        ret[BRANCH_ADDS] = []
        ret[BRANCH_DELETES] = []
        ret[BRANCH_UPDATES] = []
        ret[BRANCH_RESTORES] = []
        ret[BRANCH_REJECTS] = rejects

        if (!rejects.empty)
        {
            int rejectSize = rejects.size()
            String errorMessage = "Unable to commit ${rejectSize} ${rejectSize == 1 ? 'cube' : 'cubes'}."
            throw new BranchMergeException(errorMessage, ret)
        }
        return commitRecords
    }

    private List<NCubeInfoDto> getCubesToUpdate(ApplicationID appId, Object[] inputCubes, List<NCubeInfoDto> rejects, boolean isMerge = false)
    {
        ApplicationID.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        validateReferenceAxesAppIds(appId, inputCubes as List<NCubeInfoDto>)
        assertNotLockBlocked(appId)
        if (!isMerge)
        {
            assertPermissions(appId, null, Action.COMMIT)
        }

        List<NCubeInfoDto> newDtoList = getBranchChangesForHead(appId)
        if (!inputCubes)
        {
            return newDtoList
        }
        else
        {
            List<NCubeInfoDto> cubesToUpdate = []
            Map<String, NCubeInfoDto> newDtos = new CaseInsensitiveMap<>()
            newDtoList.each { newDtos[it.name] = it }
            (inputCubes.toList() as List<NCubeInfoDto>).each { NCubeInfoDto oldDto ->
                // make reject list by comparing with refresh records
                NCubeInfoDto newDto = newDtos[oldDto.name]
                if (newDto == null || newDto.id != oldDto.id)
                {   // if in oldDtos but not in newDtos OR if something happened while we were away
                    rejects.add(oldDto)
                }
                else
                {
                    cubesToUpdate.add(newDto)
                }
            }
            return cubesToUpdate
        }
    }

    /**
     * Rollback the passed in list of n-cubes.  Each one will be returned to the state is was
     * when the branch was created.  This is an insert cube (maintaining revision history) for
     * each cube passed in.
     */
    Integer rollbackBranch(ApplicationID appId, Object[] names)
    {
        ApplicationID.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        assertNotLockBlocked(appId)

        for (Object name : names)
        {
            String cubeName = name as String
            assertPermissions(appId, cubeName, Action.UPDATE)
        }
        int count = persister.rollbackCubes(appId, names, getUserId())
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
    Integer acceptMine(ApplicationID appId, Object[] cubeNames)
    {
        ApplicationID.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        int count = 0

        assertNotLockBlocked(appId)
        for (Object cubeName : cubeNames)
        {
            String cubeNameStr = cubeName as String
            assertPermissions(appId, cubeNameStr, Action.UPDATE)
            persister.mergeAcceptMine(appId, cubeNameStr, getUserId())
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
    Integer acceptTheirs(ApplicationID appId, Object[] cubeNames, String sourceBranch = ApplicationID.HEAD)
    {
        ApplicationID.validateAppId(appId)
        appId.validateBranchIsNotHead()
        appId.validateStatusIsNotRelease()
        assertNotLockBlocked(appId)
        int count = 0

        for (int i = 0; i < cubeNames.length; i++)
        {
            String cubeNameStr = cubeNames[i] as String
            assertPermissions(appId, cubeNameStr, Action.UPDATE)
            assertPermissions(appId.asBranch(sourceBranch), cubeNameStr, Action.READ)
            persister.mergeAcceptTheirs(appId, cubeNameStr, sourceBranch, getUserId())
            count++
        }

        return count
    }

    Boolean isCubeUpToDate(ApplicationID appId, String cubeName)
    {
        if (appId.branch == ApplicationID.HEAD)
        {
            return true
        }
        Map options = [:]
        options[(SEARCH_ACTIVE_RECORDS_ONLY)] = true
        options[(SEARCH_EXACT_MATCH_NAME)] = true

        List<NCubeInfoDto> list = search(appId, cubeName, null, options)
        if (list.size() != 1)
        {
            return false
        }

        NCubeInfoDto branchDto = list.first()     // only 1 because we used exact match
        list = search(appId.asHead(), cubeName, null, options)
        if (list.empty)
        {   // New n-cube - up-to-date because it does not yet exist in HEAD - the branch n-cube is the Creator.
            return true
        }
        else if (list.size() != 1)
        {   // Should never happen
            return false
        }

        NCubeInfoDto headDto = list.first()     // only 1 because we used exact match
        return StringUtilities.equalsIgnoreCase(branchDto.headSha1, headDto.sha1)
    }

    // -------------------------------- Non API methods --------------------------------------

    NCube mergeCubesIfPossible(NCubeInfoDto branchInfo, NCubeInfoDto headInfo, boolean headToBranch)
    {
        long branchCubeId = (long) Converter.convert(branchInfo.id, long.class)
        long headCubeId = (long) Converter.convert(headInfo.id, long.class)
        NCube branchCube = persister.loadCubeById(branchCubeId, null, getUserId())
        NCube headCube = persister.loadCubeById(headCubeId, null, getUserId())
        NCube baseCube, headBaseCube
        Map branchDelta, headDelta

        String branchCubeTests = persister.getTestData(branchCubeId, getUserId())
        String headCubeTests = persister.getTestData(headCubeId, getUserId())
        branchCube.testData = NCubeTestReader.convert(branchCubeTests).toArray()
        headCube.testData = NCubeTestReader.convert(headCubeTests).toArray()

        if (branchInfo.headSha1 != null)
        {   // Cube is based on a HEAD cube (not created new)
            baseCube = persister.loadCubeBySha1(branchInfo.applicationID.asHead(), branchInfo.name, branchInfo.headSha1, getUserId())
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

        if (DeltaProcessor.areDeltaSetsCompatible(branchDelta, headDelta))
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

        List<Delta> diff
        if (headToBranch)
        {
            diff = DeltaProcessor.getDeltaDescription(headCube, branchCube)
        }
        else
        {
            diff = DeltaProcessor.getDeltaDescription(branchCube, headCube)
        }

        if (diff.empty)
        {
            return branchCube
        }
        return null
    }

    private NCubeInfoDto getCubeInfo(ApplicationID appId, NCubeInfoDto dto)
    {
        List<NCubeInfoDto> cubeDtos = search(appId, dto.name, null, [(SEARCH_EXACT_MATCH_NAME):true, (SEARCH_ACTIVE_RECORDS_ONLY):false])
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

    private Object[] buildIdList(List<NCubeInfoDto> dtos)
    {
        Object[] ids = new Object[dtos.size()]
        int i=0
        dtos.each { NCubeInfoDto dto ->
            ids[i++] = dto.id
        }
        return ids
    }
}