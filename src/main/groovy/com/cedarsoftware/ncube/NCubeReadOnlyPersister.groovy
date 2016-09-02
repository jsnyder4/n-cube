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
    // Load n-cube by ID (specific revision)
    NCube loadCubeById(long id)

    // Load n-cube by name (latest revision)
    NCube loadCube(ApplicationID appId, String name)

    // Load n-cube by SHA-1 (latest revision with that SHA-1)
    NCube loadCubeBySha1(ApplicationID appId, String name, String sha1)

    List<String> getAppNames(String tenant)
    Map<String, List<String>> getVersions(String tenant, String app)

    List<NCubeInfoDto> getRevisions(ApplicationID appId, String cubeName, boolean ignoreVersion)
    List<NCubeInfoDto> search(ApplicationID appId, String cubeNamePattern, String searchValue, Map options)

    Set<String> getBranches(ApplicationID appId)

    String getTestData(ApplicationID appId, String cubeName)
}
