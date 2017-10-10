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
 *         Licensed under the Apache License, Version 2.0 (the "License");
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
interface NCubeReadOnlyPersister
{
    /**
     * Search the persisted storage for n-cube (NCubeInfoDto's) that match are within the passed in ApplicationID,
     * and optionally match the supplied cube name pattern, and optionally further match (contain within) the
     * searchValue, using the passed in options Map.
     * @param appId ApplicationID containing the n-cube
     * @param cubeNamePattern String name pattern to match ('*' matches any character, '?' matches a single character)
     * and these can be used more than once.  null is allowed, which matches anything.  If a specific n-cube name is
     * supplied and the caller's intention is match only one, make sure to passed in the SEARCH_EXACT_MATCH_NAME
     * is set to true in the options Map.
     * @param searchValue Optional String which will be 'contains' matched against the JSON format of the n-cube.  It
     * may contain * and ? wildcard patterns.  This match is performed case-insensitively.
     * @param options - See NCubeConstants.SEARCH_* for all the search options.
     * @return List<NCubeInfoDto> records
     */
    List<NCubeInfoDto> search(ApplicationID appId, String cubeNamePattern, String searchValue, Map options, String username)

    /**
     * Load n-cube by SHA-1 (latest revision with that SHA-1)
     * @param appId ApplicationID containing the n-cube
     * @param name String name of the n-cube to load
     * @param sha1 String SHA-1 of the n-cube to load
     */
    NCube loadCubeBySha1(ApplicationID appId, String name, String sha1, String username)

    /**
     * Load n-cube by ID (specific n-cube)
     * @param id long id of n-cube to load
     * @param options Map of additional option to include test data
     * @return Map with keys of appId, bytes, cubeName, sha1, testData
     */
    NCubeInfoDto loadCubeRecordById(long id, Map options, String username)

    /**
     * Get all application names for the given tenant
     * @param tenant String name of tenant
     * @return List of all applications for the given tenant.  If none exist, an empty list
     * is returned.
     * @throws IllegalArgumentException if the tenant name is empty or null.
     */
    List<String> getAppNames(String tenant, String username)

    /**
     * Get all versions of an application (for a given tenant)
     * @param tenant String tenant name
     * @param app String application name
     * @return Map with two (2) entries.  ['SNAPSHOT'] = List<String>, ['RELEASE'] = List<String>
     *     The List is a list of String versions is in no specific order.
     */
    Map<String, List<String>> getVersions(String tenant, String app, String username)

    /**
     * Get the Revision History for a given cube (within the passed in ApplicationID).
     * @param appId ApplicationID containing the n-cube
     * @param cubeName String name of n-cube whose history will be returned
     * @param ignoreVersion if true, then no filtering is done using the version and status field from the
     * passed in ApplicationID, otherwise if false, then the version and status fields of the ApplicationID
     * will be used to further refine the results.
     * @return List of NCubeInfoDto's representing each n-cube record in the persisted storage.
     * The ordering of the items in the list will have the highest revision number to lowest (in absolute value).
     * The ordering of the versions is not specified.
     */
    List<NCubeInfoDto> getRevisions(ApplicationID appId, String cubeName, boolean ignoreVersion, String username)
    
    /**
     * Get the list of branches for the given ApplicationID
     * @param appId ApplicationID containing the tenant, app, version, and status values.  The branch field of
     * ApplicationID is ignored, and all branches are returned for the supplied tenant, app, version, and status.
     * @return Set of String branch names
     */
    Set<String> getBranches(ApplicationID appId, String username)

    /**
     * Fetch the TEST data (in JSON format) for the full given ApplicationID.
     * @param appId ApplicationID containing the n-cubes
     * @return Map of cube names and string of JSON representing the tests for the given cube.
     * @throws IllegalArgumentException if the passed in cubeName does not exist.
     */
    Map getAppTestData(ApplicationID appId, String username)

    /**
     * Fetch the TEST data (in JSON format) for the named n-cube within the given ApplicationID.
     * @param appId ApplicationID containing the n-cube
     * @param cubeName String name of n-cube whose history will be returned
     * @return String of JSON representing the tests for the given cube.
     * @throws IllegalArgumentException if the passed in cubeName does not exist.
     */
    String getTestData(ApplicationID appId, String cubeName, String username)

    String getTestData(Long cubeId, String user)
}
