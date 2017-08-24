package com.cedarsoftware.ncube.util

import com.cedarsoftware.ncube.ApplicationID
import groovy.transform.CompileStatic

/**
 * Branch name Comparator that always places 'HEAD' first.
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
class BranchComparator implements Comparator<String>
{
    int compare(String s1, String s2)
    {
        boolean s1IsHead = ApplicationID.HEAD.equalsIgnoreCase(s1)
        boolean s2IsHead = ApplicationID.HEAD.equalsIgnoreCase(s2)
        if (s1IsHead && !s2IsHead)
            return -1
        if (!s1IsHead && s2IsHead)
            return 1
        if (s1IsHead && s2IsHead)
            return 0

        if (s1.equalsIgnoreCase(s2))
        {
            return s1.compareTo(s2)
        }
        return s1.compareToIgnoreCase(s2)
    }
}
