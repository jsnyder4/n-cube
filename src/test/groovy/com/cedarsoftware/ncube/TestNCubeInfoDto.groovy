package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test

import static org.junit.Assert.assertEquals

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the 'License');
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an 'AS IS' BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */

@CompileStatic
class TestNCubeInfoDto
{
    @Test
    void testGetApplicationID()
    {
        NCubeInfoDto dto = new NCubeInfoDto()
        dto.app = "APP"
        dto.tenant = "NONE"
        dto.version = "1.2.0"
        dto.status = "RELEASE"
        dto.branch = "HEAD"

        assertEquals(new ApplicationID("NONE", "APP", "1.2.0", "RELEASE", "HEAD"), dto.applicationID)
    }
}