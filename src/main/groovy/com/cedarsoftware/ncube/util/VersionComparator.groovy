package com.cedarsoftware.ncube.util

import com.cedarsoftware.ncube.ApplicationID
import groovy.transform.CompileStatic

/**
 * Version number Comparator that compares Strings with version number - status like
 * 1.0.1-RELEASE to 1.2.0-SNAPSHOT.  The numeric portion takes priority, however, if
 * the numeric portion is equal, then RELEASE comes before SNAPSHOT.
 * The version number components are compared numerically, not alphabetically.
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
class VersionComparator implements Comparator<String>
{
    int compare(String s1, String s2)
    {
        long v1 = ApplicationID.getVersionValue(s1)
        long v2 = ApplicationID.getVersionValue(s2)
        long diff = v2 - v1    // Reverse order (high revisions will show first)
        if (diff != 0)
        {
            return diff
        }
        return s1.compareToIgnoreCase(s2)
    }
}