package com.cedarsoftware.ncube.exception

import groovy.transform.CompileStatic

/**
 * Sub-class of RuntimeException for extra clarification if needed.
 *
 * @author Ken Partlow (kpartlow@gmail.com)
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
class BranchMergeException extends RuntimeException
{
    private final Map<String, Object> errors

    // placeholder later for change log or something like that.
    BranchMergeException(String message, Map<String, Object> errors)
    {
        super(message)
        this.errors = errors
    }

    Map<String, Object> getErrors()
    {
        return errors
    }
}
