package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.util.CaseInsensitiveMap
import groovy.transform.CompileStatic

/**
 * This class loads an reference axis.  This entails loading the cube containing
 * the referenced axis, grabbing the axis columns from this cube, passing the
 * columns to an [optional] transformer cube, and then ultimately building the
 * axis from these columns.
 *
 * The source axis type, valueType, and meta properties are maintained.  Any
 * meta-properties on the referencing axis, are added to the meta-properties
 * from the referenced axis.  If both have the same key, the referring axis
 * meta-properties take priority.
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
class ReferenceAxisLoader implements Axis.AxisRefProvider
{
    public static final String REF_TENANT = 'referenceTenant'
    public static final String REF_APP = 'referenceApp'
    public static final String REF_VERSION = 'referenceVersion'
    public static final String REF_STATUS = 'referenceStatus'
    public static final String REF_BRANCH = 'referenceBranch'
    public static final String REF_CUBE_NAME = 'referenceCubeName'
    public static final String REF_AXIS_NAME = 'referenceAxisName'

    public static final String TRANSFORM_APP = 'transformApp'
    public static final String TRANSFORM_VERSION = 'transformVersion'
    public static final String TRANSFORM_STATUS = 'transformStatus'
    public static final String TRANSFORM_BRANCH = 'transformBranch'
    public static final String TRANSFORM_CUBE_NAME = 'transformCubeName'
    public static final String TRANSFORM_METHOD_NAME = 'transformMethodName'

    private String cubeName
    private String axisName
    private Map args

    /**
     * @param containingCubeName String name of cube that holds the 'pointer' (referring) axis
     * @param sourceAxisName String name of the referring axis
     * @param refAxisArgs Map containing all the key-value pairs to describe the referenced n-cube + axis
     * and an optional reference to a transformation cube.<br>
     * required keys: sourceTenant, sourceApp, sourceVersion, sourceStatus, sourceBranch, referenceCubeName, referenceAxisName
     * optional keys (transformer): transformApp, transformVersion, transformStatus, transformBranch, transformCubeName, transformMethodName
     */
    ReferenceAxisLoader(String containingCubeName, String sourceAxisName, Map<String , Object> args)
    {
        cubeName = containingCubeName
        axisName = sourceAxisName
        this.args = args
    }

    /**
     * Axis calls this method to load itself from this AxisRefProvider.
     * Keys that are added to input for the transformer to use:
     *  input.refCube = refCube
     *  input.refAxis = refAxis
     *  input.columns = refAxis.columnsWithoutDefault
     *  input.referencingAxis = axis
     * @param axis Axis to 'fill-up' from the reference and 'transform' from the optional transformer.
     */
    void load(Axis axis)
    {
        for (Map.Entry<String, Object> entry : args.entrySet())
        {
            axis.setMetaProperty(entry.key, entry.value)
        }

        NCube refCube = getReferencedCube(axis, args[REF_CUBE_NAME] as String, args[REF_AXIS_NAME] as String)
        NCube transformCube = null

        if (axis.referenceTransformed)
        {
            transformCube = getTransformCube(axis, args[TRANSFORM_CUBE_NAME] as String, args[TRANSFORM_METHOD_NAME] as String)
        }

        Axis refAxis = getReferencedAxis(refCube, args[REF_AXIS_NAME] as String, axis)

        Map<String, Object> input = new CaseInsensitiveMap<>()
        // Allow the transformer access to the referenced n-cube (from which they can get ApplicationID and another reference axis for combining)
        input.refCube = refCube
        input.refAxis = refAxis
        input.columns = refAxis.columnsWithoutDefault
        input.referencingAxis = axis
        Map<String, Object> output = new CaseInsensitiveMap<>()

        if (transformCube != null)
        {   // Allow this cube to manipulate the passed in Axis.
            input.method = args[TRANSFORM_METHOD_NAME]
            ensureMethodAxisExists(transformCube, axis)
            transform(transformCube, input, output, axis)
        }
        else
        {
            output.columns = input.columns
        }

        axis.name = axisName
        axis.type = refAxis.type
        axis.valueType = refAxis.valueType
        axis.fireAll = refAxis.fireAll

        // Bring over referenced axis meta properties
        for (Map.Entry<String, Object> entry : refAxis.metaProperties.entrySet())
        {
            if (axis.getMetaProperty(entry.key) == null)
            {   // only override properties not already set.
                axis.setMetaProperty(entry.key, entry.value)
            }
        }

        // Bring over columns
        List<Column> columns = output.columns as List
        for (Column column : columns)
        {
            Column colAdded = axis.addColumn(column)

            // Bring over referenced column meta properties
            for (Map.Entry<String, Object> entry : column.metaProperties.entrySet())
            {
                if (colAdded.getMetaProperty(entry.key) == null)
                {   // only override properties not already set.
                    colAdded.setMetaProperty(entry.key, entry.value)
                }
            }
        }
    }

    private void transform(NCube transformCube, Map<String, Object> input, Map<String, Object> output, Axis axis)
    {
        try
        {
            transformCube.getCell(input, output)
        }
        catch (CoordinateNotFoundException e)
        {
            throw new IllegalStateException("""\
Unable to load n-cube: ${cubeName}, which has a reference axis: ${axis.name}. \
Method: ${input.method} does not exist on the 'method' axis of the transformation \
n-cube: ${transformCube.name}, transform app: ${axis.transformApp}""", e)
        }
    }

    private void ensureMethodAxisExists(NCube transformCube, Axis axis)
    {
        if (transformCube.getAxis('method') == null)
        {
            throw new IllegalStateException("""\
Unable to load n-cube: ${cubeName}, which has a reference axis: ${axis.name}. \
No 'method' axis exists on the transformation n-cube: ${transformCube.name}, transform app: ${axis.transformApp}""")
        }
    }

    private Axis getReferencedAxis(NCube refCube, String refAxisName, Axis axis)
    {
        Axis refAxis = refCube.getAxis(refAxisName)
        if (refAxis == null)
        {
            throw new IllegalStateException("""\
Unable to load n-cube: ${cubeName}, which has a reference axis: ${axis.name}, \
but referenced axis: ${refAxisName} was not found on the referenced \
n-cube: ${refCube.name}, referenced app: ${axis.referencedApp}""")
        }
        return refAxis
    }

    private NCube getTransformCube(Axis axis, String transformCubeName, String transformMethodName)
    {
        NCube transformCube = NCubeManager.getCube(axis.transformApp, axis.getMetaProperty(TRANSFORM_CUBE_NAME) as String)
        if (transformCube == null)
        {
            throw new IllegalStateException("""\
Unable to load n-cube: ${cubeName}, which has a reference axis: ${axis.name}. \
Failed to load transform n-cube: ${transformCubeName}, method: ${transformMethodName}, transform app: ${axis.transformApp}""")
        }
        return transformCube
    }

    private NCube getReferencedCube(Axis axis, String refCubeName, String refAxisName)
    {
        NCube refCube = NCubeManager.getCube(axis.referencedApp, axis.getMetaProperty(REF_CUBE_NAME) as String)
        if (refCube == null)
        {
            throw new IllegalStateException("""\
Unable to load n-cube: ${cubeName}, which has a reference axis: ${axis.name}. \
Failed to load referenced n-cube: ${refCubeName}, axis: ${refAxisName}, referenced app: ${axis.referencedApp}""")
        }
        return refCube
    }
}
