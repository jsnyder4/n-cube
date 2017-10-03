package com.cedarsoftware.ncube

import com.cedarsoftware.util.ArrayUtilities
import com.cedarsoftware.util.StringUtilities
import groovy.transform.CompileStatic

/**
 * Class used to carry the NCube meta-information
 * to the client.
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
class NCubeInfoDto
{
    public String id
    public String tenant
    public String app
    public String version
    public String status
    public String branch
    public String name
    public String sha1
    public String headSha1
    public String revision
    public Date createDate
    public String createHid
    public String notes
    public boolean changed
    public String changeType
    public byte[] bytes
    public String testData

    ApplicationID getApplicationID()
	{
        if (StringUtilities.isEmpty(tenant))
        {
            tenant = ApplicationID.DEFAULT_TENANT
        }
		return new ApplicationID(tenant, app, version, status, branch)
	}

	String toString()
	{
        String br = branch == null ? ApplicationID.HEAD : branch
		return "${tenant}/${app}/${version}/${status}/${br}/${name}/${sha1}/${revision}/${createDate}/${createHid}/${notes}"
	}

    boolean isChanged()
    {
        return changed
    }
    void setChanged(boolean state)
    {
        changed = state
    }

    boolean hasCubeData()
    {
        return !ArrayUtilities.isEmpty(bytes)
    }

    boolean hasTestData()
    {
        return StringUtilities.hasContent(testData)
    }
}
