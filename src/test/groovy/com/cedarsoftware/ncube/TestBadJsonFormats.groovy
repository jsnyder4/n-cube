package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime;

/**
 * Test improper JSON formats
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the 'License')
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
class TestBadJsonFormats extends NCubeBaseTest
{
    @Test
    void testNCubeMissingColumn()
    {
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'ncube-missing-column-error.json')
        assert ncube != null
        Axis axis = ncube.getAxis('Lat / Lon')
        assert axis.size() == 0
    }

    @Test(expected=RuntimeException.class)
    void testNCubeEmptyColumnsError()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'ncube-column-not-array-error.json')
    }

    @Test
    void testNCubeEmptyAxesParseError()
    {
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'ncube-empty-axes-error.json')
        assert ncube.name == 'EmptyAxesTest'
        assert ncube.numDimensions == 0
        assert ncube.numCells == 0
    }

    @Test
    void testNCubeMissingAxesParseError()
    {
        NCube ncube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'ncube-missing-axes-error.json')
        assert ncube.name == 'MissingAxesError'
        assert ncube.numDimensions == 0
        assert ncube.numCells == 0
    }

    @Test(expected=RuntimeException.class)
    void testNCubeMissingNameParseError()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'ncube-missing-name-error.json')
    }

    @Test(expected=RuntimeException.class)
    void testLatLongParseError()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'lat-lon-parse-error.json')
    }

    @Test(expected=RuntimeException.class)
    void testDateParseError()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'date-parse-error.json')
    }

    @Test(expected=RuntimeException.class)
    void testPoint2dParseError()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'point2d-parse-error.json')
    }

    @Test(expected=RuntimeException.class)
    void testPoint3dParseError()
    {
        ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'point3d-parse-error.json')
    }

    @Test
    void testNoNcubeRoot()
    {
        try
        {
            ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'badJsonNoNcubeRoot.json')
        }
        catch (IllegalArgumentException e)
        {
            assert e.message.contains('must have a root')
        }
    }

    @Test
    void testToJson()
    {
        assert '"Hello"' == NCube.toJson('Hello') // String at root (now valid)
        String s = NCube.toJson(['Hello']) // Array at root
        assert s.contains('["Hello"]')

        try
        {
            NCube.createCubeFromStream(null)
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'error reading cube from stream')
        }
    }

    @Test
    void testNoCells()
    {
        NCube cube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'no-cells.json')
        assert cube.sha1().length() == 40
        assert cube.toFormattedJson().contains('cells')
    }
}
