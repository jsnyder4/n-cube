package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import java.sql.Connection
/**
 * This adapter could be replaced by an adapting proxy.  Then you could
 * implement the interface and the class and not need this class.
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
class NCubeJdbcPersisterAdapter implements NCubePersister
{
    private final NCubeJdbcPersister persister = new NCubeJdbcPersister()
    private final JdbcConnectionProvider connectionProvider
    private static final Logger LOG = LogManager.getLogger(NCubeJdbcPersisterAdapter.class)

    NCubeJdbcPersisterAdapter(JdbcConnectionProvider provider)
    {
        connectionProvider = provider
    }

    Object jdbcOperation(Closure closure, String msg = closure.getClass().getSimpleName())
    {
        Connection c = connectionProvider.getConnection()
        try
        {
            long start = System.nanoTime()
            Object ret = closure(c)
            long end = System.nanoTime()
            long time = Math.round((end - start) / 1000000.0D)
            if (time > 1000)
            {
                LOG.info('    [' + NCubeManager.getUserId() + '] '+ msg + " took " + time + ' ms [SLOW]')\
            }
            else
            {
                LOG.info('    [' + NCubeManager.getUserId() + '] '+ msg + " took " + time + ' ms')
            }
            return ret
        }
        finally
        {
            connectionProvider.releaseConnection(c)
        }
    }

    Connection getConnection()
    {
        return connectionProvider.getConnection()
    }

    void updateCube(ApplicationID appId, NCube cube, final String username)
    {
        jdbcOperation({ Connection c -> persister.updateCube(c, appId, cube, username) },
                'updateCube(' + cube.name + ')')
    }

    NCube loadCubeById(long cubeId)
    {
        return (NCube) jdbcOperation({ Connection c -> persister.loadCubeById(c, cubeId) },
                'loadCubeById(' + cubeId + ')')
    }

    NCube loadCube(ApplicationID appId, String name)
    {
        return (NCube) jdbcOperation({ Connection c -> persister.loadCube(c, appId, name) },
                'loadCube(' + appId.cacheKey(name) + ')')
    }

    NCube loadCubeBySha1(ApplicationID appId, String name, String sha1)
    {
        return (NCube) jdbcOperation({ Connection c -> persister.loadCubeBySha1(c, appId, name, sha1) },
                'loadCubeBySha1(' + appId.cacheKey(name) + ', ' + sha1 + ')')
    }

    boolean restoreCubes(ApplicationID appId, Object[] names, String username)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.restoreCubes(c, appId, names, username) },
                'restoreCubes(' + appId + ')')
    }

    List<NCubeInfoDto> getRevisions(ApplicationID appId, String cubeName, boolean ignoreVersion)
    {
        return (List<NCubeInfoDto>) jdbcOperation({ Connection c -> persister.getRevisions(c, appId, cubeName, ignoreVersion) },
                'getRevisions(' + appId.cacheKey(cubeName) + ')')
    }

    Set<String> getBranches(ApplicationID appId)
    {
        return (Set<String>) jdbcOperation({ Connection c -> persister.getBranches(c, appId) },
                'getBranches(' + appId + ')')
    }

    boolean deleteBranch(ApplicationID branchId)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.deleteBranch(c, branchId) },
                'deleteBranch(' + branchId + ')')
    }

    boolean deleteCubes(ApplicationID appId, Object[] cubeNames, boolean allowDelete, String username)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.deleteCubes(c, appId, cubeNames, allowDelete, username) },
                'deleteCubes(' + appId + ')')
    }

    List<String> getAppNames(String tenant)
    {
        return (List<String>) jdbcOperation({ Connection c -> persister.getAppNames(c, tenant) },
                'getAppNames()')
    }

    Map<String, List<String>> getVersions(String tenant, String app)
    {
        return (Map<String, List<String>>) jdbcOperation({ Connection c -> persister.getVersions(c, tenant, app) },
                'getVersions(' + app + ')')
    }

    int changeVersionValue(ApplicationID appId, String newVersion)
    {
        return (int) jdbcOperation({ Connection c -> persister.changeVersionValue(c, appId, newVersion) },
                'changeVersionValue(' + appId + '->' + newVersion + ')')
    }

    int moveBranch(ApplicationID appId, String newSnapVer)
    {
        return (int) jdbcOperation({ Connection c -> persister.moveBranch(c, appId, newSnapVer) },
                'moveBranch(' + appId + ', new snap: ' + newSnapVer + ')')
    }

    int releaseCubes(ApplicationID appId, String newSnapVer)
    {
        return (int) jdbcOperation({ Connection c -> persister.releaseCubes(c, appId, newSnapVer) },
                'releaseCubes(' + appId + ', new snap: ' + newSnapVer + ')')
    }

    int copyBranch(ApplicationID srcAppId, ApplicationID targetAppId)
    {
        return (int) jdbcOperation({ Connection c -> persister.copyBranch(c, srcAppId, targetAppId) },
                'copyBranch(' + srcAppId + '->' + targetAppId + ')')
    }

    int copyBranchWithHistory(ApplicationID srcAppId, ApplicationID targetAppId)
    {
        return (int) jdbcOperation({ Connection c -> persister.copyBranchWithHistory(c, srcAppId, targetAppId) },
                'copyBranchWithHistory(' + srcAppId + '->' + targetAppId + ')')
    }

    boolean renameCube(ApplicationID appId, String oldName, String newName, String username)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.renameCube(c, appId, oldName, newName, username) },
                'renameCube(' + appId.cacheKey(oldName) + '->' + newName + ')')
    }

    boolean mergeAcceptTheirs(ApplicationID appId, String cubeName, String branchSha1, String username)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.mergeAcceptTheirs(c, appId, cubeName, branchSha1, username) },
                'mergeAcceptTheirs(' + appId.cacheKey(cubeName) + ', ' + branchSha1 + ')')
    }

    boolean mergeAcceptMine(ApplicationID appId, String cubeName, String username)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.mergeAcceptMine(c, appId, cubeName, username) },
                'mergeAcceptMine(' + appId.cacheKey(cubeName) + ')')
    }

    boolean duplicateCube(ApplicationID oldAppId, ApplicationID newAppId, String oldName, String newName, String username)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.duplicateCube(c, oldAppId, newAppId, oldName, newName, username) },
                'duplicateCube(' + oldAppId.cacheKey(oldName) + '->' + newAppId.cacheKey(newName) + ')')
    }

    boolean updateNotes(ApplicationID appId, String cubeName, String notes)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.updateNotes(c, appId, cubeName, notes) },
                'updateNotes(' + appId.cacheKey(cubeName) + ')')
    }

    boolean updateTestData(ApplicationID appId, String cubeName, String testData)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.updateTestData(c, appId, cubeName, testData) },
                'updateTestData(' + appId.cacheKey(cubeName) + ')')
    }

    String getTestData(ApplicationID appId, String cubeName)
    {
        return (String) jdbcOperation({ Connection c -> persister.getTestData(c, appId, cubeName) },
                'getTestData(' + appId.cacheKey(cubeName) + ')')
    }

    NCubeInfoDto commitMergedCubeToHead(ApplicationID appId, NCube cube, String username, long txId)
    {
        return (NCubeInfoDto) jdbcOperation({ Connection c -> persister.commitMergedCubeToHead(c, appId, cube, username, txId) },
                'commitMergedCubeToHead(' + appId.cacheKey(cube.name) + ')')
    }

    NCubeInfoDto commitMergedCubeToBranch(ApplicationID appId, NCube cube, String headSha1, String username, long txId)
    {
        return (NCubeInfoDto) jdbcOperation({ Connection c -> persister.commitMergedCubeToBranch(c, appId, cube, headSha1, username, txId) },
                'commitMergedCubeToBranch(' + appId.cacheKey(cube.name) + ', headSHA1=' + headSha1 + ')')
    }

    List<NCubeInfoDto> commitCubes(ApplicationID appId, Object[] cubeIds, String username, long txId)
    {
        return (List<NCubeInfoDto>) jdbcOperation({ Connection c -> persister.commitCubes(c, appId, cubeIds, username, txId) },
                'commitCubes(' + appId + ')')
    }

    int rollbackCubes(ApplicationID appId, Object[] names, String username)
    {
        return (int) jdbcOperation({ Connection c -> persister.rollbackCubes(c, appId, names, username) },
                'rollbackCubes(' + appId + ')')
    }

    List<NCubeInfoDto> pullToBranch(ApplicationID appId, Object[] cubeIds, String username, long txId)
    {
        return (List<NCubeInfoDto>) jdbcOperation({ Connection c -> persister.pullToBranch(c, appId, cubeIds, username, txId) },
                'pullToBranch(' + appId + ')')
    }

    boolean updateBranchCubeHeadSha1(Long cubeId, String headSha1)
    {
        return (boolean) jdbcOperation({ Connection c -> persister.updateBranchCubeHeadSha1(c, cubeId, headSha1) },
                'updateBranchCubeHeadSha1(cubeId=' + cubeId + ', headSHA1=' + headSha1 + ')')
    }

    List<NCubeInfoDto> search(ApplicationID appId, String cubeNamePattern, String searchValue, Map options)
    {
        return (List<NCubeInfoDto>) jdbcOperation({ Connection c -> persister.search(c, appId, cubeNamePattern, searchValue, options) },
                'search(' + appId + ', ' + cubeNamePattern + ', ' + searchValue + ')')
    }
}
