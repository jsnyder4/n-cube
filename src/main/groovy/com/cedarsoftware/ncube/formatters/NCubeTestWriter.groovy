package com.cedarsoftware.ncube.formatters

import com.cedarsoftware.ncube.CellInfo
import com.cedarsoftware.ncube.NCubeTest
import groovy.transform.CompileStatic

/**
 * @author Ken Partlow (kpartlow@gmail.com)
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
class NCubeTestWriter extends BaseJsonFormatter
{
    String format(Object[] tests)
    {
        if (tests != null && tests.length > 0)
        {
            startArray()
            boolean firstPass = true
            for (Object test : tests)
            {
                if (!firstPass)
                {
                    comma()
                }
                writeTest((NCubeTest) test)
                firstPass = false
            }
            endArray()
        }
        return builder.toString()
    }

    private void writeTest(NCubeTest test)
    {
        startObject()
        writeObjectKeyValue("name", test.name, true)
        writeObjectKey("coord")
        writeCoord(test.coord)
        comma()
        writeObjectKey("assertions")
        writeAssertions(test.assertions)
        endObject()
    }

    private void writeCoord(Map coord)
    {
        startArray()
        if (coord != null && coord.size() > 0)
        {
            boolean firstPass = true
            for (entry in coord)
            {
                if (!firstPass)
                {
                    comma()
                }
                startObject()
                writeObjectKey((String) entry.key)
                writeCellInfo((CellInfo) entry.value)
                endObject()
                firstPass = false
            }
        }
        endArray()
    }

    private void writeAssertions(CellInfo[] assertions)
    {
        startArray()
        if (assertions != null && assertions.length > 0)
        {
            boolean firstPass = true
            for (CellInfo item : assertions)
            {
                if (!firstPass)
                {
                    comma()
                }
                writeCellInfo(item)
                firstPass = false
            }
        }
        endArray()
    }

    private void writeCellInfo(CellInfo info)
    {
        startObject()
        if (info != null)
        {
            writeObjectKeyValue('type', info.dataType, true)
            writeObjectKeyValue('isUrl', info.isUrl, true)
            writeObjectKeyValue('value', info.value, false)
        }
        endObject()
    }
}
