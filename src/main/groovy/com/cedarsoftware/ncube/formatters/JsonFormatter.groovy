package com.cedarsoftware.ncube.formatters

import com.cedarsoftware.ncube.Axis
import com.cedarsoftware.ncube.CellInfo
import com.cedarsoftware.ncube.Column
import com.cedarsoftware.ncube.CommandCell
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.Range
import com.cedarsoftware.ncube.RangeSet
import com.cedarsoftware.ncube.proximity.LatLon
import com.cedarsoftware.ncube.proximity.Point2D
import com.cedarsoftware.ncube.proximity.Point3D
import com.cedarsoftware.util.Converter
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.io.JsonWriter
import groovy.transform.CompileStatic

import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_APP
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_AXIS_NAME
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_BRANCH
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_CUBE_NAME
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_STATUS
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_TENANT
import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_VERSION
import static com.cedarsoftware.ncube.ReferenceAxisLoader.TRANSFORM_APP
import static com.cedarsoftware.ncube.ReferenceAxisLoader.TRANSFORM_BRANCH
import static com.cedarsoftware.ncube.ReferenceAxisLoader.TRANSFORM_CUBE_NAME
import static com.cedarsoftware.ncube.ReferenceAxisLoader.TRANSFORM_METHOD_NAME
import static com.cedarsoftware.ncube.ReferenceAxisLoader.TRANSFORM_STATUS
import static com.cedarsoftware.ncube.ReferenceAxisLoader.TRANSFORM_VERSION

/**
 * Format an NCube into an JSON document
 *
 * @author Ken Partlow (kpartlow@gmail.com), John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain axis copy of the License at
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
public class JsonFormatter extends BaseJsonFormatter implements NCubeFormatter
{
    private static final String MAX_INT = Long.toString(Integer.MAX_VALUE)

    public JsonFormatter() {}

    public JsonFormatter(OutputStream stream) { super(stream) }

    /**
     * Use this API to generate JSON view of this NCube.
     */
    public String format(NCube ncube, Map<String, Object> options = null)
    {
        if (!(builder instanceof StringWriter))
        {
            throw new IllegalStateException("Builder is not a StringWriter.  Use formatCube(ncube) to write to your stream.")
        }

        formatCube(ncube, options)
        return builder.toString()
    }

    public void formatCube(NCube ncube, Map<String, Object> options)
    {
        if (ncube == null)
        {
            throw new IllegalArgumentException("Cube to format cannot be null")
        }
        if (options == null)
        {
            options = [:]
        }

        String name = ncube.getName()
        try
        {
            startObject()
            writeObjectKeyValue("ncube", name, true)
            Object defCellValue = ncube.getDefaultCellValue()

            if (defCellValue != null)
            {
                String valType = CellInfo.getType(defCellValue, "defaultCell")
                if (valType != null)
                {
                    writeObjectKeyValue(NCube.DEFAULT_CELL_VALUE_TYPE, valType, true)
                }
                if (defCellValue instanceof CommandCell)
                {
                    CommandCell cmd = (CommandCell) defCellValue

                    if (cmd.isCacheable())
                    {   // Only write 'cache' when the cache value is true.  Leave false be default.
                        writeObjectKeyValue(NCube.DEFAULT_CELL_VALUE_CACHE, true, true)
                    }
                    if (cmd.getUrl() != null)
                    {
                        writeObjectKeyValue(NCube.DEFAULT_CELL_VALUE_URL, cmd.getUrl(), true)
                    }
                    else
                    {
                        writeObjectKeyValue(NCube.DEFAULT_CELL_VALUE, cmd.getCmd(), true)
                    }
                }
                else
                {
                    writeObjectKeyValue(NCube.DEFAULT_CELL_VALUE, defCellValue, true)
                }
            }

            if (ncube.getMetaProperties().size() > 0)
            {
                writeMetaProperties(ncube.getMetaProperties())
                comma()
            }
            writeAxes(ncube.getAxes(), options)
            writeCells(ncube.getCellMap() as Map, options)
            endObject()
            closeStream()
        }
        catch (Exception e)
        {
            throw new IllegalStateException(String.format("Unable to format NCube '%s' into JSON", name), e)
        }
    }

    private void writeMetaProperties(Map<String, Object> metaProps) throws IOException
    {
        if (metaProps.size() < 1)
        {
            return
        }

        Iterator<Map.Entry<String, Object>> i = metaProps.entrySet().iterator()
        while (i.hasNext())
        {
            Map.Entry<String, Object> entry = i.next()
            final String key = entry.key
            Object value = entry.value

            if (value instanceof String || value instanceof Boolean || value == null)
            {   // Allows for simple key ==> value associations to be written when value is JSON supported type
                writeObjectKeyValue(key, value, false)
            }
            else if (value instanceof CellInfo)
            {   // If meta-prop value already a cell info, then write it out as-is
                CellInfo cell = (CellInfo) value
                writeObjectKey(key)
                startObject()
                writeType(cell.dataType)
                writeCellValue(cell.recreate())
                endObject()
            }
            else
            {
                // Convert unknown value to CellInfo (handles, all primitives, CommandCells, and Point2D, Point3D, and LatLon)
                CellInfo cell = new CellInfo(value)
                writeObjectKey(key)
                startObject()
                writeType(cell.dataType)
                writeCellValue(cell.recreate())
                endObject()
            }

            if (i.hasNext())
            {
                comma()
            }
        }
    }

    void writeAxes(List<Axis> axes, Map<String, Object> options) throws IOException
    {
        if (axes.isEmpty())
        {   // Make sure the formatter writes valid JSON
            options.indexFormat ? append('"axes":{},') : append('"axes":[],')
            return
        }

        writeObjectKey("axes")

        if (options.indexFormat)
        {
            startObject()
            Iterator i = axes.iterator()
            while (i.hasNext())
            {
                writeIndexAxis(i.next(), options)
                if (i.hasNext())
                {
                    comma()
                }
            }
            endObject()
        }
        else
        {
            startArray()
            Iterator i = axes.iterator()
            while (i.hasNext())
            {
                writeAxis(i.next(), options)
                if (i.hasNext())
                {
                    comma()
                }
            }
            endArray()
        }
        comma()
    }

    // default is false, so no need to write those out.
    void writeAxis(Axis axis, Map<String, Object> options) throws IOException
    {
        startObject()

        // required inputs
        writeAxisGuts(axis, options)
    }

    // default is false, so no need to write those out.
    void writeIndexAxis(Axis axis, Map<String, Object> options) throws IOException
    {
        // required inputs
        writeObjectKey(axis.getName().toLowerCase())
        startObject()
        writeAxisGuts(axis, options)
    }

    private void writeAxisGuts(Axis axis, Map<String, Object> options)
    {
        writeObjectKeyValue("name", axis.getName(), true)
        writeObjectKeyValue("hasDefault", axis.hasDefaultColumn(), true)
        if (axis.isReference())
        {
            writeReferenceAxisInfo(axis)
            if (options.indexFormat)
            {
                comma()
            }
        }

        if (options.indexFormat || !axis.isReference())
        {
            writeObjectKeyValue("type", axis.getType().name(), true)
            writeObjectKeyValue("valueType", axis.getValueType().name(), true)

            //  optional inputs that can use defaults
            writeObjectKeyValue("preferredOrder", axis.getColumnOrder(), true)
            writeObjectKeyValue("fireAll", axis.isFireAll(), true)
            if (axis.getMetaProperties().size() > 0)
            {
                writeMetaProperties(axis.getMetaProperties())
                comma()
            }
            writeColumns(axis.getColumns(), options)
        }
        endObject()
    }

    private void writeReferenceAxisInfo(Axis axis)
    {
        Map meta = axis.getMetaProperties()
        boolean hasTransformer = axis.isReferenceTransformed()

        writeObjectKeyValue("isRef", true, true)
        writeObjectKeyValue(REF_TENANT, meta[REF_TENANT], true)
        writeObjectKeyValue(REF_APP, meta[REF_APP], true)
        writeObjectKeyValue(REF_VERSION, meta[REF_VERSION], true)
        writeObjectKeyValue(REF_STATUS, meta[REF_STATUS], true)
        writeObjectKeyValue(REF_BRANCH, meta[REF_BRANCH], true)
        writeObjectKeyValue(REF_CUBE_NAME, meta[REF_CUBE_NAME], true)
        writeObjectKeyValue(REF_AXIS_NAME, meta[REF_AXIS_NAME], false)

        if (hasTransformer)
        {
            comma()
            writeObjectKeyValue(TRANSFORM_APP, meta[TRANSFORM_APP], true)
            writeObjectKeyValue(TRANSFORM_VERSION, meta[TRANSFORM_VERSION], true)
            writeObjectKeyValue(TRANSFORM_STATUS, meta[TRANSFORM_STATUS], true)
            writeObjectKeyValue(TRANSFORM_BRANCH, meta[TRANSFORM_BRANCH], true)
            writeObjectKeyValue(TRANSFORM_CUBE_NAME, meta[TRANSFORM_CUBE_NAME], true)
            writeObjectKeyValue(TRANSFORM_METHOD_NAME, meta[TRANSFORM_METHOD_NAME], false)
        }
        if (axis.getMetaProperties().size() > 0)
        {
            comma()
        }
        writeMetaProperties(axis.getMetaProperties())
    }

    void writeColumns(List<Column> columns, Map<String, Object> options) throws IOException
    {
        append('"columns":')
        options.indexFormat ? startObject() : startArray()

        boolean firstPass = true

        for (Column column : columns)
        {
            if (!column.isDefault())
            {
                if (!firstPass)
                {
                    comma()
                }
                writeColumn(column, options)
                firstPass = false
            }
        }

        options.indexFormat ? endObject() : endArray()
    }

    void writeColumn(Column column, Map<String, Object> options) throws IOException
    {
        String columnType = getColumnType(column.getValue())
        if (options.indexFormat)
        {
            writeObjectKey(String.valueOf(column.id))
            startObject()
        }
        else
        {
            startObject()

            //  Check to see if id exists anywhere. then optimize
            writeId(column.getId(), true)
        }
        writeType(columnType)
        if (column.getMetaProperties().size() > 0)
        {
            writeMetaProperties(column.getMetaProperties())
            comma()
        }
        if (column.getValue() instanceof CommandCell)
        {
            writeCommandCell((CommandCell) column.getValue())
        }
        else
        {
            writeObjectKeyValue("value", column.getValue(), false)
        }
        endObject()
    }

    void writeCommandCell(CommandCell cmd) throws IOException
    {
        if (cmd.isCacheable())
        {
            writeObjectKeyValue("cache", cmd.isCacheable(), true)
        }
        if (cmd.getUrl() != null)
        {
            writeObjectKeyValue("url", cmd.getUrl(), false)
        }
        else
        {
            writeObjectKeyValue("value", cmd.getCmd(), false)
        }
    }

    /**
     * According to parseJsonValue reading in, if your item is one of the following end types it does not need to
     * specify the end type:  String, Long, Boolean, Double.  These items will all be picked up automatically
     * so to save on those types I don't write out the type.
     * @param type Type to write, if null don't write anything because its axis default type
     */
    void writeType(String type) throws IOException
    {
        if (type == null)
        {
            return
        }

        writeObjectKeyValue("type", type, true)
    }

    void writeCells(Map<Set<Long>, Object> cells, Map<String, Object> options) throws IOException
    {
        append('"cells":')
        if (cells == null || cells.isEmpty() || options.nocells)
        {
            options.indexFormat ? append('{}') : append("[]")
            return
        }

        if (options.indexFormat)
        {   // {"1000000000001_2000000000001":{"type":"string", "value":10}}
            startObject()
            Iterator i = cells.entrySet().iterator()
            while (i.hasNext())
            {
                // Write key, e.g. "1000000000001_2000000000001"
                Map.Entry<Set<Long>, Object> entry = i.next()
                append('"')
                Iterator j = entry.key.iterator()
                while (j.hasNext())
                {
                    append(j.next())
                    if (j.hasNext())
                    {
                        append('_')
                    }
                }
                append('":')

                Object value = entry.value
                startObject()
                writeType(CellInfo.getType(value, "cell"))
                writeCellValue(value)
                endObject()

                if (i.hasNext())
                {
                    comma()
                }
            }
            endObject()
        }
        else
        {
            startArray()
            Iterator i = cells.entrySet().iterator()
            while (i.hasNext())
            {
                writeCell(i.next())
                if (i.hasNext())
                {
                    comma()
                }
            }
            endArray()
        }
    }

    private void writeCell(Map.Entry<Set<Long>, Object> cell) throws IOException
    {
        startObject()
        writeIds(cell.getKey())
        writeType(CellInfo.getType(cell.getValue(), "cell"))
        writeCellValue(cell.value)
        endObject()
    }

    private void writeCellValue(Object value)
    {
        if (value instanceof CommandCell)
        {
            writeCommandCell(value as CommandCell)
        }
        else
        {
            writeObjectKeyValue("value", value, false)
        }
    }

    void writeIds(Set<Long> colIds)
    {
        append('"id":')
        startArray()

        boolean firstPass = true

        for (Long colId : colIds)
        {
            final String idAsString = Long.toString(colId)
            if (!idAsString.endsWith(MAX_INT))
            {
                writeIdValue(colId, !firstPass)
                firstPass = false
            }
        }

        endArray()
        comma()
    }

    void writeId(Long longId, boolean addComma) throws IOException
    {
        writeObjectKeyValue("id", longId, addComma)
    }

    void writeIdValue(Long longId, boolean addComma)
    {
        if (addComma)
        {
            comma()
        }

        append(longId)
    }

    public static String getColumnType(Object o)
    {
        if (o instanceof Range || o instanceof RangeSet)
        {
            return null
        }

        return CellInfo.getType(o, "column")
    }

    protected void writeObjectValue(Object o) throws IOException
    {
        if (o == null)
        {
            builder.append("null")
        }
        else if (o instanceof String)
        {
            StringWriter w = new StringWriter()
            JsonWriter.writeJsonUtf8String(o.toString(), w)
            builder.append(w.toString())
        }
        else if (o instanceof BigInteger)
        {
            BigInteger i = o as BigInteger
            builder.append('"')
            builder.append(i.toString())
            builder.append('"')
        }
        else if (o instanceof BigDecimal)
        {
            BigDecimal d = o as BigDecimal
            builder.append('"')
            builder.append(d.stripTrailingZeros().toPlainString())
            builder.append('"')
        }
        else if (o instanceof Number)
        {
            builder.append(o.toString())
        }
        else if (o instanceof Date)
        {
            builder.append('"')
            builder.append(dateFormat.format(o))
            builder.append('"')
        }
        else if (o instanceof LatLon)
        {
            LatLon l = o as LatLon
            builder.append('"')
            builder.append(l.toString())
            builder.append('"')
        }
        else if (o instanceof Point2D)
        {
            Point2D pt = o as Point2D
            builder.append('"')
            builder.append(pt.toString())
            builder.append('"')
        }
        else if (o instanceof Point3D)
        {
            Point3D pt = o as Point3D
            builder.append('"')
            builder.append(pt.toString())
            builder.append('"')
        }
        else if (o instanceof Range)
        {
            Range r = o as Range
            startArray()
            writeObjectValue(r.getLow())
            comma()
            writeObjectValue(r.getHigh())
            endArray()
        }
        else if (o instanceof RangeSet)
        {
            RangeSet r = o as RangeSet
            Iterator i = r.iterator()
            startArray()
            boolean firstPass = true
            while (i.hasNext())
            {
                if (!firstPass)
                {
                    comma()
                }
                writeObjectValue(i.next())
                firstPass = false
            }
            endArray()
        }
        else if (o instanceof byte[])
        {
            builder.append('"')
            builder.append(StringUtilities.encode(o as byte[]))
            builder.append('"')
        }
        else if (o.getClass().isArray())
        {
            throw new IllegalArgumentException("Cell cannot be an array (except byte[]). Use Groovy Expression to make cell an array, a List, or a Map, etc.")
        }
        else
        {
            builder.append(o.toString())
        }
    }
}