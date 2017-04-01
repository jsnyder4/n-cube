package com.cedarsoftware.ncube

import com.cedarsoftware.util.UrlUtilities
import groovy.transform.CompileStatic
import org.slf4j.LoggerFactory
import org.slf4j.Logger

/**
 * Process a binary type (byte[]) that is specified at a URL.
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
class BinaryUrlCmd extends ContentCmdCell
{
    private static final Logger LOG = LoggerFactory.getLogger(BinaryUrlCmd.class)
    //  Private constructor only for serialization.
    private BinaryUrlCmd() {}

    BinaryUrlCmd(String url, boolean cache)
    {
        super(null, url, cache)
    }

    protected Object grab(URL u)
    {
        return UrlUtilities.getContentFromUrl(u, true)
    }
}
