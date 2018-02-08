package com.cedarsoftware.ncube

import com.cedarsoftware.util.Converter
import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeClient
import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static com.cedarsoftware.ncube.ReferenceAxisLoader.*
import static org.junit.Assert.fail

/**
 * Reference Axis Transform Tests
 *
 * @author John DeRegnaucourt (jdereg@gmail.com), Josh Snyder (joshsnyder@gmail.com)
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
class TestTransforms extends NCubeCleanupBaseTest
{
    private static ApplicationID library = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'Library', '1.0.0', ReleaseStatus.RELEASE.name(), ApplicationID.HEAD)
    private static ApplicationID transforms = new ApplicationID(ApplicationID.DEFAULT_TENANT, 'Transforms', '1.0.0', ReleaseStatus.RELEASE.name(), ApplicationID.HEAD)
    private static ApplicationID appId = ApplicationID.testAppId
    private static String cubeName = 'TestTransform'
    private static String refAxisName = 'reference'

    @Test
    void testAddStringThenRemoveTransform()
    {
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'add', value: 'GA'] as Map]
        setupDatabase(AxisType.DISCRETE, AxisValueType.STRING, standardStates, transformSpec)
        NCube appCube = ncubeClient.getCube(appId, cubeName)
        Axis reference = appCube.getAxis(refAxisName)
        assert 4 == reference.columns.size()
        assert reference.findColumn('OH')
        assert reference.findColumn('NJ')
        assert reference.findColumn('TX')
        assert reference.findColumn('GA')

        assert 1 == mutableClient.getReferenceAxes(appId).size()

        appCube.removeAxisReferenceTransform(refAxisName)
        String json = appCube.toFormattedJson()
        Axis reloadAxis = NCube.fromSimpleJson(json).getAxis(refAxisName)
        assert 3 == reloadAxis.columns.size()
        assert reloadAxis.findColumn('OH')
        assert reloadAxis.findColumn('NJ')
        assert reloadAxis.findColumn('TX')

        assert 1 == mutableClient.getReferenceAxes(appId).size()
    }

    @Test
    void testRemoveString()
    {
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'remove', value: 'OH, TX'] as Map]
        setupDatabase(AxisType.DISCRETE, AxisValueType.STRING, standardStates, transformSpec)
        NCube appCube = ncubeClient.getCube(appId, cubeName)
        Axis reference = appCube.getAxis(refAxisName)
        assert 1 == reference.columns.size()
        assert reference.findColumn('NJ')
    }

    @Test
    void testSubsetString()
    {
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'subset', value: 'OH, TX'] as Map]
        setupDatabase(AxisType.DISCRETE, AxisValueType.STRING, standardStates, transformSpec)
        NCube appCube = ncubeClient.getCube(appId, cubeName)
        Axis reference = appCube.getAxis(refAxisName)
        assert 2 == reference.columns.size()
        assert reference.findColumn('OH')
        assert reference.findColumn('TX')
    }

    @Test
    void testAddAxisString()
    {
        createLibraryCube(AxisType.DISCRETE, AxisValueType.STRING, ['CA', 'WA'], 'Reference2')
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'addAxis', value: 'Library, 1.0.0, Reference2, reference'] as Map]
        setupDatabase(AxisType.DISCRETE, AxisValueType.STRING, standardStates, transformSpec)
        NCube appCube = ncubeClient.getCube(appId, cubeName)
        Axis reference = appCube.getAxis(refAxisName)
        assert 5 == reference.columns.size()
        assert reference.findColumn('OH')
        assert reference.findColumn('NJ')
        assert reference.findColumn('TX')
        assert reference.findColumn('CA')
        assert reference.findColumn('WA')
    }

    @Test
    void testMultipleTransforms()
    {
        List<Map<String, Object>> transformSpec = [
                [transform: 1, type: 'add', value: 'GA'] as Map,
                [transform: 2, type: 'add', value: 'WI'] as Map,
                [transform: 3, type: 'remove', value: 'NJ, WI'] as Map,
                [transform: 4, type: 'subset', value: 'OH, GA'] as Map
        ]
        setupDatabase(AxisType.DISCRETE, AxisValueType.STRING, standardStates, transformSpec)
        NCube appCube = ncubeClient.getCube(appId, cubeName)
        Axis reference = appCube.getAxis(refAxisName)
        assert 2 == reference.columns.size()
        assert reference.findColumn('OH')
        assert reference.findColumn('GA')
    }

    @Test
    void testAddDate()
    {
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'add', value: '4/1/2017'] as Map]
        setupDatabase(AxisType.DISCRETE, AxisValueType.DATE, standardDates, transformSpec)
        NCube appCube = ncubeClient.getCube(appId, cubeName)
        Axis reference = appCube.getAxis(refAxisName)
        assert 4 == reference.columns.size()
        assert reference.findColumn('1/1/2017')
        assert reference.findColumn('2/1/2017')
        assert reference.findColumn('3/1/2017')
        assert reference.findColumn('4/1/2017')
    }

    @Test
    void testRemoveDate()
    {
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'remove', value: '2/1/2017'] as Map]
        setupDatabase(AxisType.DISCRETE, AxisValueType.DATE, standardDates, transformSpec)
        NCube appCube = ncubeClient.getCube(appId, cubeName)
        Axis reference = appCube.getAxis(refAxisName)
        assert 2 == reference.columns.size()
        assert reference.findColumn('1/1/2017')
        assert reference.findColumn('3/1/2017')
    }
    
    @Test
    void testImproperTransformCube()
    {
        NCube libraryCube = createLibraryCube(AxisType.DISCRETE, AxisValueType.STRING, standardStates)
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'add', value: 'GA'] as Map]
        NCube transformCube = createTransformCube(transformSpec, false)

        Map<String, Object> args = buildArgs(libraryCube.name, transformCube.name)
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        try
        {
            Axis axis = new Axis(refAxisName, 1, false, refAxisLoader)
            fail("Axis: ${axis.name} should not build.")
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to load', 'reference axis', 'transform n-cube', 'discrete', 'transform', 'property')
        }
    }

    @Test
    void testEmptyTransformType()
    {
        NCube libraryCube = createLibraryCube(AxisType.DISCRETE, AxisValueType.STRING, standardStates)
        List<Map<String, Object>> transformSpec = [[transform: 1, type: '', value: 'GA'] as Map] // empty type
        NCube transformCube = createTransformCube(transformSpec)
        Map<String, Object> args = buildArgs(libraryCube.name, transformCube.name)
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        try
        {
            Axis axis = new Axis(refAxisName, 1, false, refAxisLoader)
            fail("Axis: ${axis.name} should not build.")
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to load', 'reference axis', 'transform n-cube', 'enter', 'string for type')
        }
    }

    @Test
    void testInvalidTransformType()
    {
        NCube libraryCube = createLibraryCube(AxisType.DISCRETE, AxisValueType.STRING, standardStates)
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'foo', value: 'GA'] as Map] // invalid type
        NCube transformCube = createTransformCube(transformSpec)
        Map<String, Object> args = buildArgs(libraryCube.name, transformCube.name)
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        try
        {
            Axis axis = new Axis(refAxisName, 1, false, refAxisLoader)
            fail("Axis: ${axis.name} should not build.")
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to load', 'reference axis', 'transform n-cube', 'type must be', 'found')
        }
    }

    @Test
    void testEmptyTransformValue()
    {
        NCube libraryCube = createLibraryCube(AxisType.DISCRETE, AxisValueType.STRING, standardStates)
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'add', value: ''] as Map] // empty value
        NCube transformCube = createTransformCube(transformSpec)
        Map<String, Object> args = buildArgs(libraryCube.name, transformCube.name)
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        try
        {
            Axis axis = new Axis(refAxisName, 1, false, refAxisLoader)
            fail("Axis: ${axis.name} should not build.")
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to load', 'reference axis', 'transform n-cube', 'enter', 'string for value')
        }
    }

    @Test
    void testAddMultiple()
    {
        NCube libraryCube = createLibraryCube(AxisType.DISCRETE, AxisValueType.STRING, standardStates)
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'add', value: 'GA, FL'] as Map] // empty value
        NCube transformCube = createTransformCube(transformSpec)
        Map<String, Object> args = buildArgs(libraryCube.name, transformCube.name)
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        try
        {
            Axis axis = new Axis(refAxisName, 1, false, refAxisLoader)
            fail("Axis: ${axis.name} should not build.")
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to load', 'reference axis', 'transform n-cube', 'transform type', 'supports', 'one')
        }
    }

    @Test
    void testAddAxisError()
    {
        NCube libraryCube = createLibraryCube(AxisType.DISCRETE, AxisValueType.STRING, standardStates)
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'addaxis', value: 'Library, 1.0.0, Reference2'] as Map] // empty value
        NCube transformCube = createTransformCube(transformSpec)
        Map<String, Object> args = buildArgs(libraryCube.name, transformCube.name)
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        try
        {
            Axis axis = new Axis(refAxisName, 1, false, refAxisLoader)
            fail("Axis: ${axis.name} should not build.")
        }
        catch (IllegalArgumentException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to load', 'reference axis', 'transform n-cube', 'addaxis', 'with format')
        }
    }

    @Test
    void testNoTransformCube()
    {
        NCube libraryCube = createLibraryCube(AxisType.DISCRETE, AxisValueType.STRING, standardStates)
        Map<String, Object> args = buildArgs(libraryCube.name, 'noTransform')
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        try
        {
            Axis axis = new Axis(refAxisName, 1, false, refAxisLoader)
            fail("Axis: ${axis.name} should not build.")
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to load', 'reference axis', 'failed', 'transform n-cube', 'app')
        }
    }

    @Test
    void testNoReferenceCube()
    {
        Map<String, Object> args = buildArgs('noReference', 'noTransform')
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        try
        {
            Axis axis = new Axis(refAxisName, 1, false, refAxisLoader)
            fail("Axis: ${axis.name} should not build.")
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to load', 'reference axis', 'failed', 'referenced n-cube', 'app')
        }
    }

    @Test
    void testNoReferenceAxis()
    {
        NCube libraryCube = createLibraryCube(AxisType.DISCRETE, AxisValueType.STRING, standardStates)
        List<Map<String, Object>> transformSpec = [[transform: 1, type: 'add', value: 'GA'] as Map] // empty value
        NCube transformCube = createTransformCube(transformSpec)
        Map<String, Object> args = buildArgs(libraryCube.name, transformCube.name, 'noReferenceAxis')
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        try
        {
            Axis axis = new Axis(refAxisName, 1, false, refAxisLoader)
            fail("Axis: ${axis.name} should not build.")
        }
        catch (IllegalStateException e)
        {
            assertContainsIgnoreCase(e.message, 'unable to load', 'reference axis', 'not found', 'referenced n-cube')
        }
    }

    private static Map<String, Object> buildArgs(String librarycubeName, String transformCubeName, String axisName = refAxisName)
    {
        return [
                (REF_TENANT) : library.tenant,
                (REF_APP) : library.app,
                (REF_VERSION) : library.version,
                (REF_STATUS) : library.status,
                (REF_BRANCH) : library.branch,
                (REF_CUBE_NAME) : librarycubeName,
                (REF_AXIS_NAME) : axisName,
                (TRANSFORM_APP) : transforms.app,
                (TRANSFORM_VERSION) : transforms.version,
                (TRANSFORM_STATUS) : transforms.status,
                (TRANSFORM_BRANCH) : transforms.branch,
                (TRANSFORM_CUBE_NAME) : transformCubeName
        ] as Map
    }

    private static List<Date> getStandardDates()
    {
        List dates = [
                Converter.convert('1/1/2017', Date.class) as Date,
                Converter.convert('2/1/2017', Date.class) as Date,
                Converter.convert('3/1/2017', Date.class) as Date,
        ]
        return dates
    }

    private static List<String> getStandardStates()
    {
        return ['OH', 'NJ', 'TX']
    }

    private static void setupDatabase(AxisType refAxisType, AxisValueType refAxisValueType, List refColumns, List<Map<String, Object>> transformSpec)
    {
        NCube libraryCube = createLibraryCube(refAxisType, refAxisValueType, refColumns)
        NCube transformCube = createTransformCube(transformSpec)

        Map<String, Object> args = buildArgs(libraryCube.name, transformCube.name)
        ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, refAxisName, args)
        Axis refAxis = new Axis(refAxisName, 1, false, refAxisLoader)

        NCube ncube = new NCube(cubeName)
        ncube.applicationID = appId
        ncube.addAxis(refAxis)
        mutableClient.createCube(ncube)

        ncubeRuntime.clearCache(appId)
        ncubeRuntime.clearCache(library)
        ncubeRuntime.clearCache(transforms)
    }

    private static NCube createLibraryCube(AxisType type, AxisValueType valueType, List columns, String cubeName = 'Reference')
    {
        NCube referenceCube = new NCube(cubeName)
        Axis reference = new Axis(refAxisName, type, valueType, false)
        columns.each { Object comp ->
            reference.addColumn(new Column(comp as Comparable))
        }
        referenceCube.addAxis(reference)
        referenceCube.applicationID = library
        mutableClient.createCube(referenceCube)
        return referenceCube
    }

    private static NCube createTransformCube(List<Map<String, Object>> transformSpec, boolean properForm = true)
    {
        NCube transformCube = new NCube('Transform')
        Axis transform = new Axis('transform', AxisType.DISCRETE, AxisValueType.LONG, false)
        if (properForm)
        {
            transformCube.addAxis(transform)
        }
        Axis property = new Axis('property', AxisType.DISCRETE, AxisValueType.STRING, false)
        property.addColumn(new Column('type'))
        property.addColumn(new Column('value'))
        transformCube.addAxis(property)
        transformSpec.each { Map row ->
            transform.addColumn(new Column(row.transform as Long))
            transformCube.setCell(row.type, [transform: row.transform, property: 'type'])
            transformCube.setCell(row.value, [transform: row.transform, property: 'value'])
        }
        transformCube.applicationID = transforms
        mutableClient.createCube(transformCube)
        return transformCube
    }
}