package com.cedarsoftware.ncube

import groovy.transform.CompileStatic

/**
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
interface NCubeClient
{
    /**
     * Fetch an NCube.  If it was already loaded into the runtime cache, this will
     * return the cached version.  If not, it will make a request to the storage
     * server to fetch the NCube and then cache it.
     * @param appId ApplicationID of the NCube being requested.
     * @param cubeName String name of the NCube being requested.
     * @return NCube
     */
    NCube getCube(ApplicationID appId, String cubeName)

    /**
     * Get JSON format of an NCube.
     * @param appId ApplicationID of the NCube being requested.
     * @param cubeName String name of the NCube being requested.
     * @param options Map of options. See NCube.formatCube() for options.
     * @return String JSON of n-cube, controlled by the passed in options.
     */
    String getJson(ApplicationID appId, String cubeName, Map options)

    /**
     * Get a list of NCubeInfoDto's (record info) for the passed in search arguments.
     * @param appId ApplicationID of the NCube being requested.
     * @param cubeNamePattern String pattern containing '*' (match anything) and '?' match a single character in the
     * NCube name. Can be null meaning '*' (all).
     * @param content String pattern to match against JSON content of NCube.  Can contain '*' and '?'.  Can be null,
     * mean no matching is done against the content (faster).
     * @param options, keys from the NCubeConstants SEARCH_* constants.  Examples: SEARCH_DELETED_RECORDS_ONLY: true,
     * or SEARCH_ACTIVE_RECORDS_ONLY: true, etc.
     * @return List<NCubeInfoDto> which is all the meta-information about the NCubes matched in the search.
     */
    List<NCubeInfoDto> search(ApplicationID appId, String cubeNamePattern, String content, Map options)

    /**
     * Fetch all NCube tests for the given AppId.
     * @param appId ApplicationID of the NCube being requested.
     * @return Map of NCubeTests, where the keys are the names of the NCube's and the values are a List of NCubeTest
     */
    Map getAppTests(ApplicationID appId)

    /**
     * Fetch all NCube tests for a given NCube
     * @param appId ApplicationID of the NCube being requested.
     * @param cubeName String name of the NCube being requested.
     * @return an Object[] of NCubeTests for the given appId / NCube.  The Object[] will contain NCubeTest instances.
     */
    Object[] getTests(ApplicationID appId, String cubeName)

    /**
     * Fetch the notes field for the indicated NCube.
     * @param appId ApplicationID of the NCube being requested.
     * @param cubeName String name of the NCube being requested.
     * @return String notes from the NCube record
     */
    String getNotes(ApplicationID appId, String cubeName)

    /**
     * @return Object[] of String application names - all Application names in the system.
     */
    Object[] getAppNames()

    /**
     * Fetch all version numbers for the given ApplicationID.
     * @param appId ApplicationID for which to get all versions.
     * @return Object[] of String version numbers, in the format 0.0.0-SNAPSHOT (or 0.0.0-RELEASE),
     * for all versions in the named 'app'.
     */
    Object[] getVersions(String app)

    /**
     * Fetch all branch names for the given ApplicationID.
     * @param appId ApplicationID for which to get all branches.
     * @return Object[] of String branch names for the given 'app'.
     */
    Object[] getBranches(ApplicationID appId)
}