package com.cedarsoftware.ncube.util

import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.UrlCommandCell
import groovy.transform.CompileStatic

/**
 * @author John DeRegnaucourt (jdereg@gmail.com), Josh Snyder (joshsnyder@gmail.com)
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
class NCubeRemoval extends Closure<Boolean>
{
    NCubeRemoval()
    {
        super(null)
    }

    Boolean doCall(Object value)
    {
        if (value instanceof NCube)
        {
            NCube ncube = value as NCube
            for (Object cellValue : ncube.cellMap.values())
            {
                if (cellValue instanceof UrlCommandCell)
                {
                    UrlCommandCell cell = cellValue as UrlCommandCell
                    cell.clearClassLoaderCache()
                }
            }
        }
        return true
    }
}