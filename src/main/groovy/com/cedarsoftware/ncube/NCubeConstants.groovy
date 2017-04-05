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
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */

@CompileStatic
interface NCubeConstants
{
    final String ERROR_CANNOT_MOVE_000 = 'Version 0.0.0 is for system configuration and cannot be move.'
    final String ERROR_CANNOT_MOVE_TO_000 = 'Version 0.0.0 is for system configuration and branch cannot be moved to it.'
    final String ERROR_CANNOT_RELEASE_000 = 'Version 0.0.0 is for system configuration and cannot be released.'
    final String ERROR_CANNOT_RELEASE_TO_000 = 'Version 0.0.0 is for system configuration and cannot be created from the release process.'

    final String SEARCH_CREATE_DATE_START = 'createStartDate'
    final String SEARCH_CREATE_DATE_END = 'createEndDate'
    final String SEARCH_INCLUDE_CUBE_DATA = 'includeCubeData'
    final String SEARCH_INCLUDE_TEST_DATA = 'includeTestData'
    final String SEARCH_INCLUDE_NOTES = 'includeNotes'
    final String SEARCH_DELETED_RECORDS_ONLY = 'deletedRecordsOnly'
    final String SEARCH_ACTIVE_RECORDS_ONLY = 'activeRecordsOnly'
    final String SEARCH_CHANGED_RECORDS_ONLY = 'changedRecordsOnly'
    final String SEARCH_EXACT_MATCH_NAME = 'exactMatchName'
    final String SEARCH_FILTER_INCLUDE = 'includeTags'
    final String SEARCH_FILTER_EXCLUDE = 'excludeTags'

    final String SYS_APP = 'sys.app'
    final String SYS_BOOT_VERSION = '0.0.0'
    final String SYS_BOOTSTRAP = 'sys.bootstrap'
    final String SYS_PROTOTYPE = 'sys.prototype'
    final String SYS_PERMISSIONS = 'sys.permissions'
    final String SYS_USERGROUPS = 'sys.usergroups'
    final String SYS_LOCK = 'sys.lock'
    final String SYS_BRANCH_PERMISSIONS = 'sys.branch.permissions'
    final String CLASSPATH_CUBE = 'sys.classpath'

    final String ROLE_ADMIN = 'admin'
    final String ROLE_USER = 'user'
    final String ROLE_READONLY = 'readonly'

    final String AXIS_ROLE = 'role'
    final String AXIS_USER = 'user'
    final String AXIS_RESOURCE = 'resource'
    final String AXIS_ACTION = 'action'
    final String AXIS_SYSTEM = 'system'

    final String PROPERTY_CACHE = 'cache'

    final String CUBE_TAGS = 'cube_tags'

    final String NCUBE_PARAMS = 'NCUBE_PARAMS'
    final String NCUBE_PARAMS_BYTE_CODE_DEBUG = 'byteCodeDebug'
    final String NCUBE_PARAMS_BYTE_CODE_VERSION = 'byteCodeVersion'
    final String NCUBE_ACCEPTED_DOMAINS = 'acceptedDomains'
    final String NCUBE_PARAMS_BRANCH = 'branch'

    final String PR_PROP = 'property'
    final String PR_STATUS = 'status'
    final String PR_APP = 'appId'
    final String PR_CUBES = 'cubeNames'
    final String PR_REQUESTER = 'requestUser'
    final String PR_REQUEST_TIME = 'requestTime'
    final String PR_ID = 'prId'
    final String PR_MERGER = 'commitUser'
    final String PR_MERGE_TIME = 'commitTime'
    final String PR_OPEN = 'open'
    final String PR_CLOSED = 'closed'
    final String PR_CANCEL= 'closed cancelled'
    final String PR_COMPLETE= 'closed complete'

    final String RUNTIME_BEAN = 'ncubeRuntime'
    final String MANAGER_BEAN = 'ncubeManager'
    final String CONTROLLER_BEAN = 'ncubeController'
}