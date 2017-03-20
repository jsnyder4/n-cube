package com.cedarsoftware.ncube

import groovy.transform.CompileStatic

/**
 * @author Josh Snyder
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
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either eƒfetxpress or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */

@CompileStatic
interface NCubeConstants
{
    static final String ERROR_CANNOT_MOVE_000 = 'Version 0.0.0 is for system configuration and cannot be move.'
    static final String ERROR_CANNOT_MOVE_TO_000 = 'Version 0.0.0 is for system configuration and branch cannot be moved to it.'
    static final String ERROR_CANNOT_RELEASE_000 = 'Version 0.0.0 is for system configuration and cannot be released.'
    static final String ERROR_CANNOT_RELEASE_TO_000 = 'Version 0.0.0 is for system configuration and cannot be created from the release process.'

    static final String SEARCH_INCLUDE_CUBE_DATA = 'includeCubeData'
    static final String SEARCH_INCLUDE_TEST_DATA = 'includeTestData'
    static final String SEARCH_INCLUDE_NOTES = 'includeNotes'
    static final String SEARCH_DELETED_RECORDS_ONLY = 'deletedRecordsOnly'
    static final String SEARCH_ACTIVE_RECORDS_ONLY = 'activeRecordsOnly'
    static final String SEARCH_CHANGED_RECORDS_ONLY = 'changedRecordsOnly'
    static final String SEARCH_EXACT_MATCH_NAME = 'exactMatchName'
    static final String SEARCH_FILTER_INCLUDE = 'includeTags'
    static final String SEARCH_FILTER_EXCLUDE = 'excludeTags'

    static final String SYS_BOOTSTRAP = 'sys.bootstrap'
    static final String SYS_PROTOTYPE = 'sys.prototype'
    static final String SYS_PERMISSIONS = 'sys.permissions'
    static final String SYS_USERGROUPS = 'sys.usergroups'
    static final String SYS_LOCK = 'sys.lock'
    static final String SYS_BRANCH_PERMISSIONS = 'sys.branch.permissions'
    static final String CLASSPATH_CUBE = 'sys.classpath'

    static final String ROLE_ADMIN = 'admin'
    static final String ROLE_USER = 'user'
    static final String ROLE_READONLY = 'readonly'

    static final String AXIS_ROLE = 'role'
    static final String AXIS_USER = 'user'
    static final String AXIS_RESOURCE = 'resource'
    static final String AXIS_ACTION = 'action'
    static final String AXIS_SYSTEM = 'system'

    static final String PROPERTY_CACHE = 'cache'

    static final String CUBE_TAGS = 'cube_tags'

    static final String NCUBE_PARAMS = 'NCUBE_PARAMS'
    static final String NCUBE_PARAMS_BYTE_CODE_DEBUG = 'byteCodeDebug'
    static final String NCUBE_PARAMS_BYTE_CODE_VERSION = 'byteCodeVersion'
    static final String NCUBE_ACCEPTED_DOMAINS = 'acceptedDomains'
    static final String NCUBE_PARAMS_BRANCH = 'branch'
}