package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.exception.*
import com.cedarsoftware.ncube.formatters.HtmlFormatter
import com.cedarsoftware.ncube.formatters.JsonFormatter
import com.cedarsoftware.ncube.formatters.NCubeTestReader
import com.cedarsoftware.ncube.formatters.NCubeTestWriter
import com.cedarsoftware.ncube.util.LongHashSet
import com.cedarsoftware.util.*
import com.cedarsoftware.util.io.JsonObject
import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.JsonWriter
import com.cedarsoftware.util.io.MetaUtils
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.Array
import java.lang.reflect.Field
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.regex.Matcher
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Implements an n-cube.  This is a hyper (n-dimensional) cube
 * of cells, made up of 'n' number of axes.  Each Axis is composed
 * of Columns that denote discrete nodes along an axis.  Use NCubeManager
 * to manage a list of NCubes.  Documentation on Github.
 *
 * Useful for pricing, rating, and configuration modeling.
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
class NCube<T>
{
    private static final Logger LOG = LoggerFactory.getLogger(NCube.class)
    public static final String DEFAULT_CELL_VALUE_TYPE = 'defaultCellValueType'
    public static final String DEFAULT_CELL_VALUE = 'defaultCellValue'
    public static final String DEFAULT_CELL_VALUE_URL = 'defaultCellValueUrl'
    public static final String DEFAULT_CELL_VALUE_CACHE = 'defaultCellValueCache'
    public static final String validCubeNameChars = '0-9a-zA-Z._-'
    public static final String RULE_EXEC_INFO = '_rule'
    public static final String METAPROPERTY_TEST_UPDATED = 'testUpdated'
    public static final String METAPROPERTY_TEST_DATA = '_testData'
    protected static final byte[] TRUE_BYTES = 't'.bytes
    protected static final byte[] FALSE_BYTES = 'f'.bytes
    private static final byte[] A_BYTES = 'a'.bytes
    private static final byte[] C_BYTES = 'c'.bytes
    private static final byte[] O_BYTES = 'o'.bytes
    private static final byte[] NULL_BYTES = 'null'.bytes
    private static final byte[] ARRAY_BYTES = 'array'.bytes
    private static final byte[] MAP_BYTES = 'map'.bytes
    private static final byte[] COL_BYTES = 'col'.bytes

    private String name
    private String sha1
    private final Map<String, Axis> axisList = new CaseInsensitiveMap<>()
    private final Map<Long, Axis> idToAxis = new HashMap<>(16, 0.8f)
    protected final Map<LongHashSet, T> cells = new HashMap<>(128, 0.8f)
    private T defaultCellValue
    private final Map<String, Advice> advices = [:]
    private Map metaProps = new CaseInsensitiveMap<>()
    private static ConcurrentMap primitives = new ConcurrentHashMap()
    //  Sets up the defaultApplicationId for cubes loaded in from disk.
    private transient ApplicationID appId = ApplicationID.testAppId
    private static final ThreadLocal<Deque<StackEntry>> executionStack = new ThreadLocal<Deque<StackEntry>>() {
        Deque<StackEntry> initialValue()
        {
            return new ArrayDeque<>()
        }
    }

    /**
     * Permanently add Custom Reader / Writer to json-io so that n-cube will use its native JSON
     * format when written or read with json-io.
     */
    static
    {
        JsonReader.addReaderPermanent(NCube.class, new NCubeReader())
        JsonWriter.addWriterPermanent(NCube.class, new NCubeWriter())
    }

    /**
     * Custom reader for NCube when used with json-io
     */
    static class NCubeReader implements JsonReader.JsonClassReaderEx
    {
        Object read(Object jOb, Deque<JsonObject<String, Object>> stack, Map<String, Object> args)
        {
            Map map = (Map)jOb
            if (map.size() == 1)
            {   // If "@type" was added, then you need to extract the n-cube instance from the "ncube" field.
                map = map.ncube as Map
            }
            NCube ncube = hydrateCube(map)
            String tenant = ncube.getMetaProperty('n-tenant')
            String app = ncube.getMetaProperty('n-app')
            String version = ncube.getMetaProperty('n-version')
            String status = ncube.getMetaProperty('n-status')
            String branch = ncube.getMetaProperty('n-branch')

            ApplicationID appId = new ApplicationID(tenant, app, version, status, branch)
            ncube.applicationID = appId

            stripFoistedAppId(ncube)
            return ncube
        }
    }

    /**
     * Custom writer for NCube when used with json-io
     */
    static class NCubeWriter implements JsonWriter.JsonClassWriterEx
    {
        void write(Object o, boolean showType, Writer output, Map<String, Object> args) throws IOException
        {
            NCube ncube = (NCube)o

            // Temporarily set App ID as meta-properties before json-io serialization
            ApplicationID appId1 = ncube.applicationID
            ncube.setMetaProperty('n-tenant', appId1.tenant)
            ncube.setMetaProperty('n-app', appId1.app)
            ncube.setMetaProperty('n-version', appId1.version)
            ncube.setMetaProperty('n-status', appId1.status)
            ncube.setMetaProperty('n-branch', appId1.branch)

            String json = ncube.toFormattedJson()
            if (showType)
            {   // {"@type":"com.cedarsoftware.ncube.NCube", "ncube": xxx }.  xxx = NCube's native JSON format.
                output.write(""""ncube":${json}""")
            }
            else
            {   // Write out NCube's native JSON format, minus the { and } at the beginning and end, as this is
                // written by json-io.
                output.write(json.substring(1, json.length() - 1))
            }

            stripFoistedAppId(ncube)
        }
    }

    private static void stripFoistedAppId(NCube ncube)
    {
        ncube.removeMetaProperty('n-tenant')
        ncube.removeMetaProperty('n-app')
        ncube.removeMetaProperty('n-version')
        ncube.removeMetaProperty('n-status')
        ncube.removeMetaProperty('n-branch')
    }

    /**
     * Creata a new NCube instance with the passed in name
     * @param name String name to use for the NCube.
     */
    NCube(String name)
    {
        if (name != null)
        {   // If name is null, likely being instantiated via serialization
            validateCubeName(name)
        }
        this.name = name
    }

    /**
     * Fetch n-cube meta properties (SHA1, HEAD_SHA1, and CHANGE_TYPE are not meta-properties.
     * Use their respective accessor functions to obtain those).
     * @return Map (case insensitive keys) containing meta (additional) properties for the n-cube.
     * Modifications to this Map do not modify the actual meta properties of n-cube.  To do that,
     * you need to use setMetaProperty(), addMetaProperty(), or remoteMetaProperty()
     */
    Map getMetaProperties()
    {
        return Collections.unmodifiableMap(metaProps)
    }

    /**
     * Fetch the value associated to the passed in Key from the MetaProperties (if any exist).  If
     * none exist, null is returned.
     */
    Object getMetaProperty(String key)
    {
        return metaProps[key]
    }

    /**
     * If a meta property value is fetched from an Axis or a Column, the value should be extracted
     * using this API, so as to allow executable values to be retrieved.
     * @param value Object value to be extracted.
     */
    Object extractMetaPropertyValue(Object value, Map input = [:], Map output = [:])
    {
        if (value instanceof CommandCell)
        {
            if (!(input instanceof TrackingMap))
            {
                input = new TrackingMap(input)
            }
            CommandCell cmd = (CommandCell) value
            value = executeExpression(prepareExecutionContext(input, output), cmd)
        }
        return value
    }

    /**
     * Set (add / overwrite) a Meta Property associated to this n-cube.
     * @param key String key name of meta property
     * @param value Object value to associate to key
     * @return prior value associated to key or null if none was associated prior
     */
    Object setMetaProperty(String key, Object value)
    {
        clearSha1()
        return metaProps[key] = value
    }

    /**
     * Remove a meta-property entry
     */
    Object removeMetaProperty(String key)
    {
        Object prop =  metaProps.remove(key)
        clearSha1()
        return prop
    }

    /**
     * Add a Map of meta properties all at once.
     * @param allAtOnce Map of meta properties to add
     */
    void addMetaProperties(Map<String, Object> allAtOnce)
    {
        for (entry in allAtOnce.entrySet())
        {
            final String key = entry.key
            metaProps[key] = entry.value
        }
        clearSha1()
    }

    /**
     * Remove all meta properties associated to this n-cube.
     */
    void clearMetaProperties()
    {
        metaProps.clear()
        clearSha1()
    }

    /**
     * Walk cell map and ensure all coordinates are fully resolvable
     */
    protected void dropOrphans(Set<Long> columnIds, long axisId)
    {
        Iterator<LongHashSet> i = cells.keySet().iterator()
        while (i.hasNext())
        {
            LongHashSet cols = i.next()
            for (id in cols)
            {
                Axis axis = getAxisFromColumnId(id, false)
                if (axis && axis.id == axisId)
                {
                    if (!columnIds.contains(id))
                    {
                        i.remove()
                        break
                    }
                }
            }
        }
    }

    /**
     * This is a "Pointer" (or Key) to a cell in an NCube.
     * It consists of a String cube Name and a Set of
     * Column references (one Column per axis).
     */
    private static class StackEntry
    {
        final String cubeName
        final Map coord

        StackEntry(String name, Map coordinate)
        {
            cubeName = name
            coord = coordinate
        }

        String toString()
        {
            StringBuilder s = new StringBuilder()
            s.append(cubeName)
            s.append(':[')

            Iterator<Map.Entry<String, Object>> i = coord.entrySet().iterator()
            while (i.hasNext())
            {
                Map.Entry<String, Object> coordinate = i.next()
                s.append(coordinate.key)
                s.append(':')
                s.append(coordinate.value)
                if (i.hasNext())
                {
                    s.append(',')
                }
            }
            s.append(']')
            return s.toString()
        }
    }

    /**
     * Add advice to this n-cube that will be called before / after any Controller Method or
     * URL-based Expression, for the given method
     */
    protected void addAdvice(Advice advice, String method)
    {
        advices["${advice.name}/${method}".toString()] = advice
    }

    /**
     * @return List<Advice> advices added to this n-cube.
     */
    List<Advice> getAdvices(String method)
    {
        List<Advice> result = []
        method = "/${method}"
        for (entry in advices.entrySet())
        {
            // Entry key = "AdviceName/MethodName"
            if (entry.key.endsWith(method))
            {   // Entry.Value = Advice instance
                result.add(entry.value)
            }
        }

        Collections.sort(result, new Comparator<Advice>() {
            int compare(Advice a1, Advice a2)
            {
                return a1.name.compareToIgnoreCase(a2.name)
            }
        })

        return result
    }

    /**
     * For testing, advices need to be removed after test completes.
     */
    protected void clearAdvices()
    {
        advices.clear()
    }

    /**
     * @return ReleaseStatus of this n-cube as it was loaded.
     */
    String getStatus()
    {
        return appId.status
    }

    /**
     * @return String version of this n-cube as it was loaded.
     */
    String getVersion()
    {
        return appId.version
    }

    /**
     * @return ApplicationID for this n-cube.  This contains the app name, version, etc. that this
     * n-cube is part of.
     */
    ApplicationID getApplicationID()
    {
        return appId
    }

    void setApplicationID(ApplicationID appId)
    {
        this.appId = appId
    }

    /**
     * This should only be called from NCubeManager when loading the cube from a database
     * It is mainly to prevent an unnecessary sha1 calculation after being loaded from a
     * db that already knows the sha1.
     * @param sha1 String SHA-1 value to set into this n-cube.  Should only be called internally
     * from code constructing this n-cube from a persistent store.
     */
    protected void setSha1(String sha1)
    {
        this.sha1 = sha1
    }

    /**
     * @return String name of the NCube
     */
    String getName()
    {
        return name
    }

    /**
     * Clear (remove) the cell at the given coordinate.  The cell is dropped
     * from the internal sparse storage. After this call, containsCell(coord) for the
     * same cell will return false.
     * @param coordinate Map coordinate of Cell to remove.
     * @return value of cell that was removed.
     * For RULE axes, the name of the Rule Axis must be bound to a rule name (e.g. the 'name'
     * attribute on the Column expression).  If you need to distinguish between a null
     * stored in a null, versus nothing being stored there, use containsCell() first.
     */
    T removeCell(final Map coordinate)
    {
        clearSha1()
        return cells.remove(getCoordinateKey(coordinate))
    }

    /**
     * Clear a cell directly from the cell sparse-matrix specified by the passed in Column
     * IDs. After this call, containsCell(coord) for the same coordinate would return false.
     */
    T removeCellById(final Set<Long> coordinate)
    {
        clearSha1()
        LongHashSet ids = ensureFullCoordinate(coordinate)
        if (ids == null)
        {
            return null
        }
        return cells.remove(ids)
    }

    /**
     * Test to see if a value is mapped at the given coordinate.  The 2nd argument allows
     * you to take into account the n-cube level Default Cell value or not. Set to true
     * to have the default value considered, false otherwise.
     * @param coordinate Map (coordinate) of a cell
     * @param useDefault (optional, defaults to false. Set to true, then if a non-default
     * value for the n-cube is set, this method will return true).
     * @return 1. boolean true if a defaultValue is set (non-null) and useDefault is true
     * 2. boolean true if a cell is located at the specified coordinate in the
     * sparse cell map.
     * For RULE axes, the name of the Rule Axis must be bound to a rule name
     * (e.g. the 'name' attribute on the Column expression).
     */
    boolean containsCell(final Map coordinate, boolean useDefault = false)
    {
        LongHashSet cols
        if (useDefault)
        {
            if (defaultCellValue != null)
            {   // n-cube default not-null, so yes it 'contains cell' (when useDefault true)
                return true
            }
            cols = getCoordinateKey(coordinate)
            if (getColumnDefault(cols) != null)
            {   // n-cube column default not-null, so yes it 'contains cell' (when useDefault true)
                return true
            }
        }
        else
        {
            cols = getCoordinateKey(coordinate)
        }

        return cells.containsKey(cols)
    }


    /**
     * @return true if and only if there is a cell stored at the location
     * specified by the Set<Long> coordinate.  If the IDs don't locate a coordinate,
     * no exception is thrown - simply false is returned.
     * If no coordinate is supplied for an axis (or axes) that has a default column, then the default
     * column will be bound to for that axis (or axes).
     */
    boolean containsCellById(final Collection<Long> coordinate)
    {
        LongHashSet ids = ensureFullCoordinate(coordinate)
        return cells.containsKey(ids)
    }

    /**
     * Store a value in the cell at the passed in coordinate.
     * @param value A value to store in the NCube cell.
     * @param coordinate Map coordinate used to identify what cell to update.
     * The Map contains keys that are axis names, and values that will
     * locate to the nearest column on the axis.
     * @return the prior cells value.
     */
    T setCell(final T value, final Map coordinate)
    {
        if (!(value instanceof byte[]) && value != null && value.class.array)
        {
            throw new IllegalArgumentException("Cannot set a cell to be an array type directly (except byte[]). Instead use GroovyExpression.")
        }
        clearSha1()
        return cells[getCoordinateKey(coordinate)] = (T) internValue(value)

    }

    /**
     * Set a cell directly into the cell sparse-matrix specified by the passed in
     * Column IDs.
     */
    T setCellById(final T value, final Set<Long> coordinate)
    {
        if (!(value instanceof byte[]) && value != null && value.class.array)
        {
            throw new IllegalArgumentException("Cannot set a cell to be an array type directly (except byte[]). Instead use GroovyExpression.")
        }
        clearSha1()
        LongHashSet ids = ensureFullCoordinate(coordinate)
        if (ids == null)
        {
            throw new InvalidCoordinateException("Unable to setCellById() into n-cube: ${name}, appId: ${appId} using coordinate: ${coordinate}. Add column(s) before assigning cells.", name)
        }
        return cells[ids] = (T)internValue(value)
    }

    /**
     * Mainly useful for displaying an ncube within an editor.  This will
     * get the actual stored cell, not execute it.  The caller will get
     * CommandCell instances for example, as opposed to the return value
     * of the executed CommandCell.
     */
    def getCellByIdNoExecute(final Set<Long> coordinate)
    {
        LongHashSet ids = ensureFullCoordinate(coordinate)
        return cells[ids]
    }

    /**
     * Fetch the actual 'formula' at the given cell.  In the case of primitives,
     * the primitive will be returned.  However, in the case of an Expression,
     * the Expression will be returned, not executed.
     * @return value stored at the given location.  Since this method will return
     * null when there is no value in an empty (unset) cell, use containsCell() if
     * you need to distinguish between not present and null.
     * @throws CoordinateNotFoundException if the coordinate does not represent a
     * coordinate with the space of this n-cube.
     */
    def getCellNoExecute(final Map coordinate)
    {
        LongHashSet ids = getCoordinateKey(coordinate)
        return cells[ids]
    }

    /**
     * Fetch the contents of the cell at the location specified by the coordinate argument.
     * Be aware that if you have any rule cubes in the execution path, they can execute
     * more than one cell.  The cell value returned is the value of the last cell executed.
     * Typically, in a rule cube, you are writing to specific keys within the rule cube, and
     * the calling code then accesses the 'output' Map to fetch the values at these specific
     * keys.
     * @param coordinate Map of String keys to values meant to bind to each axis of the n-cube.
     * @param output Map that can be written to by the code within the the n-cubes (for example,
     *               GroovyExpressions.
     * @param defaultValue Object placed here will be returned if there is no cell at the location
     *                     pinpointed by the input coordinate.  Normally, the defaulValue of the
     *                     n-cube is returned, but if this parameter is passed a non-null value,
     *                     then it will be returned.
     * @return Cell pinpointed by the input coordinate.  If there is nothing stored at this
     * location, then if there is an axis containing a column with a default value (set as
     * meta-property Column.DEFAULT_VALUE [key: 'default_value']), then that will be returned.
     * If there is no column with a default value, then the n-cube's default value will be
     * returned. If defaultValue is null, then then n-cube defaultValue argument will be returned.
     */
    T at(final Map coordinate, final Map output = [:], Object defaultValue = null)
    {
        return getCell(coordinate, output, defaultValue)
    }

    /**
     * <pre>Fetch the contents of the cell at the location specified by the coordinate argument.
     *
     * Note that if you have any rule axes, they can execute more than one time.  The value
     * returned by this method is the value of the last cell executed. Typically, in a rule cube,
     * you are writing to specific keys within the output Map, and the calling code then accesses
     * the 'output' Map to fetch the values at these specific keys.
     *
     * A rule axis name can have a String, Collection, Map, or nothing associated to it.
     *   - If the value is a String, then it is the name of the rule to begin execution
     *     (skips past rules ahead of it).
     *   - If the value associated to a rule axis name is a Collection, then it is considered a
     *     Collection of rule names to run (orchestration).  In that case, only the named rules
     *     will be executed (their conditions evaluated, and if true, the associated statements).
     *   - If the associated value is a Map, then the keys will be the names of meta-property keys
     *     associated to the rule condition column meta-property keys, and the values must match
     *     the value associated to the meta-property. The special value NCUBE.DONT_CARE can be
     *     associated to the key, in which case only the key name of the meta-property must match
     *     the key name in the passed in map in order for the rule to be selected.  If there
     *     is more than one entry in the passed in Map, then the rules must match on all entries.
     *     This is a conjunction (or AND) - the rules match all keys are selected.
     *   - If nothing is associated to the rule axis name (or null), then all rules are selected.
     *
     * Once the rules are selected, all rule conditions are executed.  If the condition is true,
     * then the associated statement is executed.
     *
     * Note: More than one rule axis can be added to an n-cube.  In this case, each rule axis
     * name can have its own orchestration (rule list) to select the rules on the given axis.
     * </pre>
     * @param coordinate Map of String keys to values meant to bind to each axis of the n-cube.
     * @param output Map that can be written to by the code within the the n-cubes (for example,
     *               GroovyExpressions.
     * @param defaultValue Object placed here will be returned if there is no cell at the location
     *                     pinpointed by the input coordinate.  Normally, the defaulValue of the
     *                     n-cube is returned, but if this parameter is passed a non-null value,
     *                     then it will be returned.
     * @return Cell pinpointed by the input coordinate.  If there is nothing stored at this
     * location, then if there is an axis containing a column with a default value (set as
     * meta-property Column.DEFAULT_VALUE [key: 'default_value']), then that will be returned.
     * If there is no column with a default value, then the n-cube's default value will be
     * returned. If defaultValue is null, then then n-cube defaultValue argument will be returned.
     */
    T getCell(final Map coordinate, final Map output = [:], Object defaultValue = null)
    {
        final RuleInfo ruleInfo = getRuleInfo(output)
        Map input = validateCoordinate(coordinate, output)
        T lastStatementValue = null

        if (!hasRuleAxis())
        {   // Perform fast bind and execute.
            lastStatementValue = getCellById(getCoordinateKey(input, output), input, output, defaultValue)
            ruleInfo.setLastExecutedStatement(lastStatementValue)
            return output.return = lastStatementValue
        }

        boolean run = true
        final List<Binding> bindings = ruleInfo.getAxisBindings()
        final int depth = executionStack.get().size()
        final int dimensions = numDimensions
        final String[] axisNames = axisList.keySet().toArray(new String[dimensions])
        Map ctx = prepareExecutionContext(input, output)

        while (run)
        {
            run = false
            final Map<String, List<Column>> selectedColumns = selectColumns(input, output)   // get [potential subset of] rule columns to execute, per Axis
            final Map<String, Integer> counters = getCountersPerAxis(axisNames)
            final Map<Long, Object> cachedConditionValues = [:]
            final Map<String, Integer> conditionsFiredCountPerAxis = [:]

            try
            {
                while (true)
                {
                    final Binding binding = new Binding(name, depth)

                    for (axis in axisList.values())
                    {
                        final String axisName = axis.name
                        final Column boundColumn = selectedColumns[axisName][counters[axisName] - 1]

                        if (axis.type == AxisType.RULE)
                        {
                            Object conditionValue
                            if (!cachedConditionValues.containsKey(boundColumn.id))
                            {   // Has the condition on the Rule axis been run this execution?  If not, run it and cache it.
                                CommandCell cmd = (CommandCell) boundColumn.value

                                // If the cmd == null, then we are looking at a default column on a rule axis.
                                // the conditionValue becomes 'true' for Default column when ruleAxisBindCount = 0
                                final Integer count = conditionsFiredCountPerAxis[axisName]
                                conditionValue = cmd == null ? isZero(count) : executeExpression(ctx, cmd)
                                final boolean conditionAnswer = isTrue(conditionValue)
                                cachedConditionValues[boundColumn.id] = conditionAnswer

                                if (conditionAnswer)
                                {   // Rule fired
                                    conditionsFiredCountPerAxis[axisName] = count == null ? 1 : count + 1
                                    if (!axis.fireAll)
                                    {   // Only fire one condition on this axis (fireAll is false)
                                        counters[axisName] = 1
                                        selectedColumns[axisName] = [boundColumn]
                                    }
                                    if (cmd == null)
                                    {
                                        trackUnboundAxis(output, name, axisName, coordinate[axisName])
                                    }
                                }
                            }
                            else
                            {   // re-use condition on this rule axis (happens when more than one rule axis on an n-cube)
                                conditionValue = cachedConditionValues[boundColumn.id]
                            }

                            // A rule column on a given axis can be accessed more than once (example: A, B, C on
                            // one rule axis, X, Y, Z on another).  This generates coordinate combinations
                            // (AX, AY, AZ, BX, BY, BZ, CX, CY, CZ).  The condition columns must be run only once, on
                            // subsequent access, the cached result of the condition is used.
                            if (isTrue(conditionValue))
                            {
                                binding.bind(axisName, boundColumn)
                            }
                            else
                            {   // Incomplete binding - no need to attempt further bindings on other axes.
                                break
                            }
                        }
                        else
                        {
                            binding.bind(axisName, boundColumn)
                        }
                    }

                    // Step #2 Execute cell and store return value, associating it to the Axes and Columns it bound to
                    if (binding.numBoundAxes == dimensions)
                    {   // Conditions on rule axes that do not evaluate to true, do not generate complete coordinates (intentionally skipped)
                        bindings.add(binding)
                        lastStatementValue = executeAssociatedStatement(input, output, ruleInfo, binding)
                    }

                    // Step #3 increment counters (variable radix increment)
                    if (!incrementVariableRadixCount(counters, selectedColumns, axisNames))
                    {
                        break
                    }
                }

                // Verify all rule axes were bound 1 or more times
                ensureAllRuleAxesBound(coordinate, conditionsFiredCountPerAxis)
            }
            catch (RuleStop ignored)
            {
                // ends this execution cycle
                ruleInfo.ruleStopThrown()
            }
            catch (RuleJump e)
            {
                input = e.coord
                run = true
            }
        }

        ruleInfo.setLastExecutedStatement(lastStatementValue)
        output.return = lastStatementValue
        return lastStatementValue
    }

    private Object executeExpression(Map ctx, CommandCell cmd)
    {
        try
        {
            Object ret = cmd.execute(ctx)
            trackInputKeysUsed((Map) ctx.input, (Map) ctx.output)
            return ret
        }
        catch (ThreadDeath | RuleStop | RuleJump e)
        {
            throw e
        }
        catch (CoordinateNotFoundException e)
        {
            String msg = e.message
            if (!msg.contains('-> cell:'))
            {
                throw new CoordinateNotFoundException("${e.message}\nerror occurred in cube: ${name}\n${stackToString()}",
                        e.cubeName, e.coordinate, e.axisName, e.value)
            }
            else
            {
                throw e
            }
        }
        catch (Throwable t)
        {
            throw new CommandCellException("Error occurred in cube: ${name}\n${stackToString()}", t)
        }
    }

    private T executeAssociatedStatement(Map input, Map output, RuleInfo ruleInfo, Binding binding)
    {
        try
        {
            final Set<Long> colIds = binding.idCoordinate
            T statementValue = getCellById(colIds, input, output)
            binding.value = statementValue
            return statementValue
        }
        catch (RuleStop e)
        {   // Statement threw at RuleStop
            binding.value = '[RuleStop]'
            // Mark that RULE_STOP occurred
            ruleInfo.ruleStopThrown()
            throw e
        }
        catch(RuleJump e)
        {   // Statement threw at RuleJump
            binding.value = '[RuleJump]'
            throw e
        }
        catch (Exception e)
        {
            Throwable t = e
            while (t.cause != null)
            {
                t = t.cause
            }
            String msg = t.message
            if (StringUtilities.isEmpty(msg))
            {
                msg = t.class.name
            }
            binding.value = "[${msg}]"
            throw e
        }
    }

    /**
     * @return boolean true if there is at least one rule axis, false if there are no rule axes.
     */
    boolean hasRuleAxis()
    {
        for (axis in axisList.values())
        {
            if (AxisType.RULE == axis.type)
            {
                return true
            }
        }
        return false
    }

    /**
     * Verify that at least one rule on each rule axis fired.  If not, then you have a
     * CoordinateNotFoundException.
     * @param coordinate Input (Map) coordinate for getCell()
     * @param conditionsFiredCountPerAxis Map that tracks AxisName to number of fired-columns bound to axis
     */
    private void ensureAllRuleAxesBound(Map coordinate, Map<String, Integer> conditionsFiredCountPerAxis)
    {
        for (axis in axisList.values())
        {
            if (AxisType.RULE == axis.type)
            {
                String axisName = axis.name
                Integer count = conditionsFiredCountPerAxis[axisName]
                if (count == null || count < 1)
                {
                    throw new CoordinateNotFoundException("No conditions on the rule axis: ${axisName} fired, and there is no default column on this axis, cube: ${name}, input: ${coordinate}",
                            name, coordinate, axisName)
                }
            }
        }
    }

    /**
     * The lowest level cell fetch.  This method uses the Set<Long> to fetch an
     * exact cell, while maintaining the original input coordinate that the location
     * was derived from (required because a given input coordinate could map to more
     * than one cell).  Once the cell is located, it is executed and the value from
     * the executed cell is returned. In the case of Command Cells, it is the return
     * value of the execution, otherwise the return is the value stored in the cell,
     * and if there is no cell, then a default value from NCube is returned, if one
     * is set. Default value ordering - first, a column level default is used if
     * one exists (under Column's meta-key: 'DEFAULT_CELL'). If no column-level
     * default is specified (no non-null value provided), then the NCube level default
     * is chosen (if it exists). If no NCube level default is specified, then the
     * defaultValue passed in is used, if it is non-null.
     * REQUIRED: The coordinate passed to this method must have already been run
     * through validateCoordinate(), which duplicates the coordinate and ensures the
     * coordinate has at least an entry for each axis (entry not needed for axes with
     * default column or rule axes).
     */
    protected T getCellById(final Set<Long> colIds, final Map coordinate, final Map output, Object defaultValue = null)
    {
        // First, get a ThreadLocal copy of an NCube execution stack
        Deque<StackEntry> stackFrame = (Deque<StackEntry>) executionStack.get()
        boolean pushed = false
        try
        {
            // Form fully qualified cell lookup (NCube name + coordinate)
            // Add fully qualified coordinate to ThreadLocal execution stack
            final StackEntry entry = new StackEntry(name, coordinate)
            stackFrame.push(entry)
            pushed = true
            T cellValue

// Handy trick for debugging a failed binding (like space after an input)
//            if (coordinate.containsKey("debug"))
//            {   // Dump out all kinds of binding info
//                LOG.info("*** DEBUG getCellById() ***")
//                LOG.info("Axes:")
//                for (Axis axis : axisList.values())
//                {
//                    LOG.info("  axis name: " + axis.name)
//                    LOG.info("  axis ID: " + axis.getId())
//                    LOG.info("  axis type: " + axis.getType())
//                    LOG.info("  axis valueType: " + axis.getValueType())
//                    LOG.info("  Columns:")
//                    for (Column column : axis.getColumns())
//                    {
//                        if (StringUtilities.hasContent(column.getColumnName()))
//                        {
//                            LOG.info("    column name: " + column.getColumnName())
//                        }
//                        LOG.info("    column value: " + column.value)
//                        LOG.info("    column id: " + column.getId())
//                    }
//                }
//                LOG.info("Cells:")
//                LOG.info("  " + cells)
//                LOG.info("Input:")
//                LOG.info("  coord IDs: " + idCoord)
//                LOG.info("  coord Map: " + coordinate)
//            }

            cellValue = cells.get(colIds)
            if (cellValue == null && !cells.containsKey(colIds))
            {   // No cell, look for default
                cellValue = (T) getColumnDefault(colIds)
                if (cellValue == null)
                {   // No Column Default, try NCube default, and finally passed in default
                    if (defaultCellValue != null)
                    {
                        cellValue = defaultCellValue
                    }
                    else
                    {
                        cellValue = (T) defaultValue
                    }
                }
            }

            if (cellValue instanceof CommandCell)
            {
                Map ctx = prepareExecutionContext(coordinate, output)
                return (T) executeExpression(ctx, cellValue as CommandCell)
            }
            else
            {
                trackInputKeysUsed(coordinate, output)
            }
            return cellValue
        }
        finally
        {	// Unwind stack: always remove if stacked pushed, even if Exception has been thrown
            if (pushed)
            {
                stackFrame.pop()
            }
        }
    }

    /**
     * Pre-compile command cells, meta-properties, and rule conditions that are expressions
     */
    void compile()
    {
        cells.each { ids, cell ->
            if(cell instanceof GroovyBase) {
                compileCell(getCoordinateFromIds(ids), cell as GroovyBase)
            }
        }

        metaProps.each { key, value ->
            if (value instanceof GroovyBase) {
                compileCell([metaProp:key],value as GroovyBase)
            }
        }

        axisList.each { axisName, axis ->
            axis.columns.each { column ->
                if (column.value instanceof GroovyBase) {
                    compileCell([axis:axisName,column:column.columnName],column.value as GroovyBase)
                }

                if (column.metaProps) {
                    column.metaProps.each { key, value ->
                        if (value instanceof GroovyBase) {
                            compileCell([axis:axisName,column:column.columnName,metaProp:key],value as GroovyBase)
                        }
                    }
                }
            }

            if (axis.metaProps) {
                axis.metaProps.each { key, value ->
                    if (value instanceof GroovyBase) {
                        compileCell([axis:axisName,metaProp:key],value as GroovyBase)
                    }
                }
            }
        }
    }

    private void compileCell(Map input, GroovyBase groovyBase) {
        try
        {
            groovyBase.prepare(groovyBase.cmd ?: groovyBase.url, prepareExecutionContext(input,[:]))
        }
        catch (Exception e)
        {
            LOG.warn("Failed to compile cell for cube:${this.name} with coords:${input.toString()}",e)
        }
    }

    /**
     * Given the passed in column IDs, return the column level default value
     * if one exists or null otherwise.  In the case of intersection, then null
     * is returned, meaning that the n-cube level default cell value will be
     * returned at intersections.
     */
    def getColumnDefault(Set<Long> colIds)
    {
        def colDef = null

        for (colId in colIds)
        {
            Axis axis = getAxisFromColumnId(colId)
            if (axis == null)
            {   // bad column id, continue check rest of column ids
                continue
            }
            Column boundCol = axis.getColumnById(colId)
            def metaValue = boundCol.getMetaProperty(Column.DEFAULT_VALUE)
            if (metaValue != null)
            {
                if (colDef != null)
                {   // More than one specified in this set (intersection), therefore return null (use n-cube level default)
                    if (colDef != metaValue)
                    {
                        return null
                    }
                }
                colDef = metaValue
            }
        }

        return colDef
    }

    private static void trackInputKeysUsed(Map input, Map output)
    {
        if (input instanceof TrackingMap)
        {
            RuleInfo ruleInfo = getRuleInfo(output)
            ruleInfo.addInputKeysUsed(((TrackingMap)input).keysUsed())
        }
    }

    /**
     * Prepare the execution context by providing it with references to
     * important items like the input coordinate, output map, stack,
     * and this (ncube).
     */
    protected Map prepareExecutionContext(final Map coord, final Map output)
    {
        return [input: coord, output: output, ncube: this]  // Input coordinate is already a duplicate at this point
    }

    /**
     * Get a Map of column values and corresponding cell values where all axes
     * but one are held to a fixed (single) column, and one axis allows more than
     * one value to match against it.
     * @param coordinate Map - A coordinate where the keys are axis names, and the
     * values are intended to match a column on each axis, with one exception.  One
     * of the axis values in the coordinate input map must be an instanceof a Set.
     * If the set is empty, all columns and cell values for the given axis will be
     * returned in a Map.  If the Set has values in it, then only the columns
     * on the 'wildcard' axis that match the values in the set will be returned (along
     * with the corresponding cell values).
     * @param output Map that can be written to by the code within the the n-cubes (for example,
     *               GroovyExpressions.  Optional.
     * @param defaultValue Object placed here will be returned if there is no cell at the location
     *                     pinpointed by the input coordinate.  Normally, the defaulValue of the
     *                     n-cube is returned, but if this parameter is passed a non-null value,
     *                     then it will be returned.  Optional.
     * @return a Map containing Axis names and values to bind to those axes.  One of the
     * axes must have a Set bound to it.
     */
    Map<Object, T> getMap(final Map coordinate, Map output = [:], Object defaultValue = null)
    {
        final Map coord = validateCoordinate(coordinate, [:])
        final Axis wildcardAxis = getWildcardAxis(coord)
        final List<Column> columns = getWildcardColumns(wildcardAxis, coord)
        final Map<Object, T> result = [:]
        final String axisName = wildcardAxis.name

        for (column in columns)
        {
            coord[axisName] = column.valueThatMatches
            result[column.value] = getCell(coord, output, defaultValue)
        }

        return result
    }

    /**
     * Filter rows of an n-cube.  Use this API to fetch a subset of an n-cube, similar to SQL SELECT.
     * @param rowAxisName String name of axis acting as the ROW axis.
     * @param colAxisName String name of axis acting as the COLUMN axis.
     * @param where String groovy statement block (or expression) written as condition in terms of the columns on the colAxisName.
     * Example: "(input.state == 'TX' || input.state == 'OH') && (input.attribute == 'fuzzy')".  This will only return rows
     * where this condition is met ('state' and 'attribute' are two column values from the colAxisName).  The values for each
     * row in the rowAxis is bound to the where expression for each row.  If the row passes the 'where' condition, it is
     * included in the output.
     * @param output Map the output Map use to write multiple return values to, just like getCell() or at().
     * @param input Map the input Map just like it is used for getCell() or at().  Only needed when there are three (3)
     * or more dimensions.  All values in the input map (excluding the axis specified by rowAxisName and colAxisName) are
     * bound just as they are in getCell() or at().
     * @param columnsToSearch Set which allows reducing the number of columns bound for use in the where clause.  If not
     * specified, all columns on the colAxisName can be used.  For example, if you had an axis named 'attribute', and it
     * has 10 columns on it, you could list just two (2) of the columns here, and only those columns would be placed into
     * values accessible to the where clause via input.xxx == 'someValue'.
     * @param columnsToReturn Set of values to indicate which columns to return.  If not specified, the entire 'row' is
     * returned.  For example, if you had an axis named 'attribute', and it has 10 columns on it, you could list just
     * two (2) of the columns here, in the returned Map of rows, only these two columns will be in the returned Map.
     * The columnsToSearch and columnsToReturn can be completely different, overlap, or not be specified. The mapReduce()
     * API runs faster when fewer columns are included in the columnsToSearch.
     * @return Map of Maps - The outer Map is keyed by the column values of all row columns.  If the row Axis is a discrete
     * axis, then the keys of the map are all the values of the columns.  If a non-discrete axis is used, then the keys
     * are the name meta-key for each column.  If a non-discrete axis is used and there are no name attributes on the columns,
     * and exception will be thrown.  The 'value' associated to the key (column value or column name) is a Map record,
     * where the keys are the column values (or names) for axis named colAxisName.  The associated values are the values
     * for each cell in the same column, for when the 'where' condition holds true (groovy true).
     */
    Map mapReduce(String rowAxisName, String colAxisName, String where = 'true', Map output = [:], Map input = [:], Set columnsToSearch = null, Set columnsToReturn = null)
    {
        throwIf(!rowAxisName, new IllegalArgumentException('The row axis name cannot be null'))
        throwIf(!colAxisName, new IllegalArgumentException('The column axis name cannot be null'))
        throwIf(!where, new IllegalArgumentException('The where clause cannot be null'))

        Set<Long> boundColumns = hydrateBoundColumns(rowAxisName, colAxisName, input)

        Axis rowAxis = axisList[rowAxisName]
        Axis colAxis = axisList[colAxisName]
        boolean isRowNotDiscrete = rowAxis.type != AxisType.DISCRETE
        boolean isColNotDiscrete = colAxis.type != AxisType.DISCRETE

        Map matchingRows = [:] as Map
        List<Column> whereColumns
        if (columnsToSearch)
        {
            whereColumns = []
            for (columnToSearch in columnsToSearch)
            {
                whereColumns << (isColNotDiscrete ? colAxis.findColumnByName(columnToSearch as String) : colAxis.findColumn(columnToSearch as Comparable))
            }
        }
        else
        {
            whereColumns = colAxis.columns
        }

        GroovyExpression exp = new GroovyExpression(where, null, false)
        LongHashSet ids = new LongHashSet(boundColumns)
        Map commandInput = new CaseInsensitiveMap<>(input)
        for (column in rowAxis.columns)
        {
            Map alreadyExecuted = [:] as Map
            Map queryMap = [:] as Map
            commandInput[rowAxisName] = column.valueThatMatches

            long colId = column.id
            ids << colId
            for (whereColumn in whereColumns)
            {
                long whereId = whereColumn.id
                ids << whereId
                def cellValue = determineCommandCell(ids, commandInput, colAxisName, column, output)
                setMapByAxisType(isColNotDiscrete, colAxis, whereColumn, cellValue, queryMap, alreadyExecuted)
                ids.remove(whereId)
            }

            def result = executeExpression([input: queryMap, output: output, ncube: this] as Map, exp)
            if (isTrue(result))
            {
                setMapByAxisType(isRowNotDiscrete, rowAxis, column, buildMapReduceResult(colAxis, columnsToReturn, alreadyExecuted, ids, commandInput, output), matchingRows)
            }
            ids.remove(colId)
        }
        return matchingRows
    }

    /**
     * Get / Create the RuleInfo Map stored at output[NCube.RULE_EXEC_INFO]
     */
    static RuleInfo getRuleInfo(Map output)
    {
        final RuleInfo ruleInfo
        if (output.containsKey(RULE_EXEC_INFO))
        {   // RULE_EXEC_INFO Map already exists, must be a recursive call.
            return (RuleInfo) output[RULE_EXEC_INFO]
        }
        // RULE_EXEC_INFO Map does not exist, create it.
        ruleInfo = new RuleInfo()
        output[RULE_EXEC_INFO] = ruleInfo
        return ruleInfo
    }

    /**
     * Follow the exact same treatment of TRUTH as Groovy
     */
    static boolean isTrue(Object ruleValue)
    {
        if (ruleValue == null)
        {   // null indicates rule did NOT fire
            return false
        }

        if (ruleValue instanceof Boolean)
        {
            return ruleValue == true
        }

        if (ruleValue instanceof Number)
        {
            boolean isZero = ((byte) 0) == ruleValue ||
                    ((short) 0) == ruleValue ||
                    0 == ruleValue ||
                    ((long) 0) == ruleValue ||
                    0.0d == ruleValue ||
                    0.0f == ruleValue ||
                    BigInteger.ZERO == ruleValue ||
                    BigDecimal.ZERO == ruleValue
            return !isZero
        }

        if (ruleValue instanceof String)
        {
            return "" != ruleValue
        }

        if (ruleValue instanceof Map)
        {
            return (ruleValue as Map).size() > 0
        }

        if (ruleValue instanceof Collection)
        {
            return (ruleValue as Collection).size() > 0
        }

        if (ruleValue instanceof Enumeration)
        {
            return (ruleValue as Enumeration).hasMoreElements()
        }

        if (ruleValue instanceof Iterator)
        {
            return (ruleValue as Iterator).hasNext()
        }

        return true
    }

    private static boolean isZero(Integer count)
    {
        return count == null || count == 0
    }

    /**
     * Bind the input coordinate to each axis.  The reason the column is a List of columns that the coordinate
     * binds to on the axis, is to support RULE axes.  On a regular axis, the coordinate binds
     * to a column (with a binary search or hashMap lookup), however, on a RULE axis, the act
     * of binding to an axis results in a List<Column>.
     * @param input The passed in input coordinate to bind (or multi-bind) to each axis.
     */
    private Map<String, List<Column>> selectColumns(Map<String, Object> input, Map output)
    {
        Map<String, List<Column>> bindings = new CaseInsensitiveMap<>()
        for (entry in axisList.entrySet())
        {
            final String axisName = entry.key
            final Axis axis = entry.value
            final Object value = input[axisName]

            if (AxisType.RULE == axis.type)
            {   // For RULE axis, all possible columns must be added (they are tested later during execution)
                if (value instanceof String)
                {
                    bindings[axisName] = axis.getRuleColumnsStartingAt((String) value)
                }
                else if (value instanceof Collection)
                {   // Collection of rule names to select (orchestration)
                    Collection<String> orchestration = value as Collection
                    bindings[axisName] = axis.findColumns(orchestration)
                    assertAtLeast1Rule(bindings[axisName], "No rule selected on rule-axis: ${axis.name}, rule names ${orchestration}, cube: ${name}")
                }
                else if (value instanceof Map)
                {   // key-value pairs that meta-properties of rule columns must match to select rules.
                    Map<String, Object> required = value as Map
                    bindings[axisName] = axis.findColumns(required)
                    assertAtLeast1Rule(bindings[axisName], "No rule selected on rule-axis: ${axis.name}, meta-properties must match ${required}, cube: ${name}")
                }
                else if (value instanceof Closure)
                {
                    bindings[axisName] = axis.findColumns(value as Closure)
                    assertAtLeast1Rule(bindings[axisName], "No rule selected on rule-axis: ${axis.name}, meta-properties must match closure, cube: ${name}")
                }
                else
                {
                    bindings[axisName] = axis.columns
                }
            }
            else
            {   // Find the single column that binds to the input coordinate on a regular axis.
                final Column column = axis.findColumn(value as Comparable)
                if (column == null || column.default)
                {
                    trackUnboundAxis(output, name, axisName, value)
                }
                if (column == null)
                {
                    throw new CoordinateNotFoundException("Value '${value}' not found on axis: ${axisName}, cube: ${name}",
                            name, input, axisName, value)
                }
                bindings[axisName] = [column]    // Binding is a List of one column on non-rule axis
            }
        }

        return bindings
    }

    private static void trackUnboundAxis(Map output, String cubeName, String axisName, Object value)
    {
        RuleInfo ruleInfo = getRuleInfo(output)
        ruleInfo.addUnboundAxis(cubeName, axisName, value)
    }

    private static void assertAtLeast1Rule(Collection<Column> columns, String errorMessage)
    {
        if (columns.empty)
        {   // Match default (if it exists) and none of the orchestration columns have matched
            throw new CoordinateNotFoundException(errorMessage)
        }
    }

    private static Map<String, Integer> getCountersPerAxis(final String[] axisNames)
    {
        final Map<String, Integer> counters = new CaseInsensitiveMap<>()

        // Set counters to 1
        for (axisName in axisNames)
        {
            counters[axisName] = 1
        }
        return counters
    }

    /**
     * Make sure the returned Set<Long> contains a column ID for each axis, even if the input set does
     * not have a coordinate for an axis, but the axis has a default column (the default column's ID will
     * be added to the returned Set).
     * @return Set<Long> that contains only the necessary coordinates from the passed in Collection.  If it cannot
     * bind, null is returned.
     */
    protected LongHashSet ensureFullCoordinate(Collection<Long> coordinate)
    {
        if (coordinate == null)
        {
            coordinate = new HashSet<>()
        }
        Set<Long> ids = new TreeSet<>()
        for (axis in axisList.values())
        {
            Column bindColumn = null
            for (id in coordinate)
            {
                bindColumn = axis.getColumnById(id)
                if (bindColumn != null)
                {
                    break
                }
            }
            if (bindColumn == null)
            {
                bindColumn = axis.defaultColumn
                if (bindColumn == null)
                {
                    return null
                }
            }
            ids.add(bindColumn.id)
        }
        return new LongHashSet(ids)
    }

    /**
     * Convert an Object to a Map.  This allows an object to then be passed into n-cube as a coordinate.  Of course
     * the returned map can have additional key/value pairs added to it after calling this method, but before calling
     * getCell().
     * @param o Object any Java object to bind to an NCube.
     * @return Map where the fields of the object are the field names from the class, and the associated values are
     * the values associated to the fields on the object.
     */
    static Map objectToMap(final Object o)
    {
        if (o == null)
        {
            throw new IllegalArgumentException("null passed into objectToMap.  No possible way to convert null into a Map.")
        }

        try
        {
            final Collection<Field> fields = ReflectionUtils.getDeepDeclaredFields(o.class)
            final Iterator<Field> i = fields.iterator()
            final Map newCoord = new CaseInsensitiveMap<>()

            while (i.hasNext())
            {
                final Field field = i.next()
                final String fieldName = field.name
                final Object fieldValue = field.get(o)
                if (newCoord.containsKey(fieldName))
                {   // This can happen if field name is same between parent and child class (dumb, but possible)
                    newCoord[field.declaringClass.name + '.' + fieldName] = fieldValue
                }
                else
                {
                    newCoord[fieldName] = fieldValue
                }
            }
            return newCoord
        }
        catch (Exception e)
        {
            throw new RuntimeException("Failed to access field of passed in object.", e)
        }
    }

    private static String stackToString()
    {
        final Deque<StackEntry> stack = executionStack.get()
        final Iterator<StackEntry> i = stack.descendingIterator()
        final StringBuilder s = new StringBuilder()

        while (i.hasNext())
        {
            final StackEntry key = i.next()
            s.append('-> cell:')
            s.append(key.toString())
            if (i.hasNext())
            {
                s.append('\n')
            }
        }

        return s.toString()
    }

    /**
     * Increment the variable radix number passed in.  The number is represented by a Map, where the keys are the
     * digit names (axis names), and the values are the associated values for the number.
     * @return false if more incrementing can be done, otherwise true.
     */
    private static boolean incrementVariableRadixCount(final Map<String, Integer> counters,
                                                       final Map<String, List<Column>> bindings,
                                                       final String[] axisNames)
    {
        int digit = axisNames.length - 1

        while (true)
        {
            final String axisName = axisNames[digit]
            final int count = counters[axisName]
            final List<Column> cols = bindings[axisName]

            if (count >= cols.size())
            {   // Reach max value for given dimension (digit)
                if (digit == 0)
                {   // we have reached the max radix for the most significant digit - we are done
                    return false
                }
                counters[axisNames[digit--]] = 1
            }
            else
            {
                counters[axisName] = count + 1  // increment counter
                return true
            }
        }
    }

    /**
     * @param coordinate passed in coordinate for accessing this n-cube
     * @return Axis the axis that has a Set specified for it rather than a non-Set value.
     * The Set associated to the input coordinate field indicates that the caller is
     * matching more than one value against this axis.
     */
    private Axis getWildcardAxis(final Map<String, Object> coordinate)
    {
        int count = 0
        Axis wildcardAxis = null

        for (entry in coordinate.entrySet())
        {
            if (entry.value instanceof Set)
            {
                count++
                wildcardAxis = axisList[entry.key]      // intentional case insensitive match
            }
        }

        if (count == 0)
        {
            throw new IllegalArgumentException("No 'Set' value found within input coordinate, cube: ${name}")
        }

        if (count > 1)
        {
            throw new IllegalArgumentException("More than one 'Set' found as value within input coordinate, cube: ${name}")
        }

        return wildcardAxis
    }

    /**
     * @param coordinate Map containing Axis names as keys, and Comparable's as
     * values.  The coordinate key matches an axis name, and then the column on the
     * axis is found that best matches the input coordinate value.  Use this when
     * the input is expected to only match one value on an axis.  For example, for a
     * RULE axis, the key name is the RULE axis name, and the value is the rule name
     * to match.
     * @return a Set key in the form of Column1,Column2,...Column-n where the Columns
     * are the Column IDs along the axis that match the value associated to the key (axis
     * name) of the passed in input coordinate. The ordering is the order the axes are
     * stored within in NCube.  The returned Set is the 'key' of NCube's cells Map, which
     * maps a coordinate (Set of column IDs) to the cell value.
     */
    LongHashSet getCoordinateKey(final Map coordinate, Map output = new CaseInsensitiveMap())
    {
        Map safeCoord

        if (coordinate instanceof TrackingMap)
        {
            TrackingMap trackMap = coordinate as TrackingMap
            if (trackMap.getWrappedMap() instanceof CaseInsensitiveMap)
            {
                safeCoord = coordinate
            }
            else
            {
                safeCoord = new CaseInsensitiveMap<>(coordinate)
            }
        }
        else if (coordinate instanceof CaseInsensitiveMap)
        {
            safeCoord = coordinate
        }
        else
        {
            safeCoord = (coordinate == null) ? new CaseInsensitiveMap<>() : new CaseInsensitiveMap<>(coordinate)
        }

        LongHashSet ids = new LongHashSet()
        Iterator<Axis> i = axisList.values().iterator()

        while (i.hasNext())
        {
            Axis axis = (Axis) i.next()
            String axisName = axis.name
            final Comparable value = (Comparable) safeCoord[axisName]
            final Column column = (Column) axis.findColumn(value)
            if (column == null || column.default)
            {
                trackUnboundAxis(output, name, axisName, value)
            }
            if (column == null)
            {
                throw new CoordinateNotFoundException("Value '${coordinate}' not found on axis: ${axisName}, cube: ${name}",
                        name, coordinate, axisName, value)
            }
            ids.add(column.id)
        }

        return ids
    }

    /**
     * Ensure that the Map coordinate dimensionality satisfies this nCube.
     * This method verifies that all axes are listed by name in the input coordinate.
     * @param coordinate Map input coordinate
     */
    private Map validateCoordinate(final Map coordinate, final Map output)
    {
        if (coordinate == null)
        {
            throw new IllegalArgumentException("'null' passed in for coordinate Map, n-cube: ${name}")
        }

        // Duplicate input coordinate
        final Map copy = new CaseInsensitiveMap<>()
        copy.putAll(coordinate)

        // Ensure required scope is supplied within the input coordinate
        Set<String> requiredScope = getRequiredScope(coordinate, output)

        for (scopeKey in requiredScope)
        {
            if (!copy.containsKey(scopeKey))
            {
                Set coordinateKeys = coordinate.keySet()
                throw new InvalidCoordinateException("Input coordinate: ${coordinateKeys}, does not contain all of the required scope keys: ${requiredScope}, cube: ${name}, appId: ${appId}",
                        name, coordinateKeys, requiredScope)
            }
        }

        return new TrackingMap(copy)
    }

    /**
     * @param coordinate Map containing Axis names as keys, and Comparable's as
     * values.  The coordinate key matches an axis name, and then the column on the
     * axis is found that best matches the input coordinate value. The input coordinate
     * must contain one Set as a value for one of the axes of the NCube.  If empty,
     * then the Set is treated as '*' (star).  If it has 1 or more elements in
     * it, then for each entry in the Set, a column position value is returned.
     *
     * @return a List of all columns that match the values in the Set, or in the
     * case of an empty Set, all columns on the axis.
     */
    private List<Column> getWildcardColumns(final Axis wildcardAxis, final Map coordinate)
    {
        final List<Column> columns = []
        final Set<Comparable> wildcardSet = (Set<Comparable>) coordinate[wildcardAxis.name]

        // To support '*', an empty Set is bound to the axis such that all columns are returned.
        if (wildcardSet.empty)
        {
            columns.addAll(wildcardAxis.columns)
        }
        else
        {
            // This loop grabs all the columns from the axis which match the values in the Set
            for (value in wildcardSet)
            {
                final Column column = wildcardAxis.findColumn(value)
                if (column == null)
                {
                    String axisName = wildcardAxis.name
                    throw new CoordinateNotFoundException("Value '${value}' not found using Set on axis: ${axisName}, cube: ${name}",
                            name, coordinate, axisName, value)
                }

                columns.add(column)
            }
        }

        return columns
    }

    /**
     * @return T the default value that will be returned when a coordinate specifies
     * a cell that has no entry associated to it.  This is a space-saving technique,
     * as the most common cell value can be set as the defaultCellValue, and then the
     * cells that would have had this value can be left empty.
     */
    T getDefaultCellValue()
    {
        return defaultCellValue
    }

    /**
     * Set the default cell value for this n-cube.  This is a space-saving technique,
     * as the most common cell value can be set as the defaultCellValue, and then the
     * cells that would have had this value can be left empty.
     * @param defaultCellValue T the default value that will be returned when a coordinate
     * specifies a cell that has no entry associated to it.
     */
    void setDefaultCellValue(final T defaultCellValue)
    {
        this.defaultCellValue = defaultCellValue
        clearSha1()
    }

    /**
     * Clear all cell values.  All axes and columns remain.
     */
    void clearCells()
    {
        cells.clear()
        clearSha1()
    }

    /**
     * Add a column to the n-cube
     * @param axisName String name of the Axis to which the column will be added.
     * @param value Comparable that will be the value for the given column.  Cannot be null.
     * @param colName The optional name of the column, useful for RULE axis columns.  Optional.
     * @param suggestedId Long id.  If id is not valid for the column (unique id, and axis portion matches axis on which
     *                    it is added), then the ID will be generated.  Optional.
     * @return Column the added Column.
     */
    Column addColumn(final String axisName, final Comparable value, String colName = null, Long suggestedId = null)
    {
        final Axis axis = getAxis(axisName)
        if (axis == null)
        {
            throw new IllegalArgumentException("Could not add column. Axis name '${axisName}' was not found on cube: ${name}")
        }
        Column newCol = axis.addColumn(value, colName, suggestedId)
        clearSha1()
        return newCol
    }

    /**
     * Delete a column from the named axis.  All cells that reference this
     * column will be deleted.
     * @param axisName String name of Axis contains column to be removed.
     * @param value Comparable value used to identify column (Long if identifying a RULE column)
     * @return boolean true if deleted, false otherwise
     */
    boolean deleteColumn(final String axisName, final Comparable value)
    {
        final Axis axis = getAxis(axisName)
        if (axis == null)
        {
            throw new IllegalArgumentException("Could not delete column. Axis name '${axisName}' was not found on cube: ${name}")
        }

        Column column
        if (axis.type == AxisType.RULE)
        {   // Rule axes are deleted by ID, name, or null (default - can be deleted with null or ID).
            if (value instanceof Long)
            {
                column = axis.deleteColumnById(value as Long)
            }
            else if (value instanceof String)
            {
                column = axis.findColumnByName(value as String)
                if (column != null)
                {
                    axis.deleteColumnById(column.id)
                }
            }
            else if (value == null)
            {
                column = axis.deleteColumn(null)
            }
            else
            {
                return false
            }
        }
        else
        {
            column = axis.deleteColumn(value)
        }
        if (column == null)
        {
            return false
        }

        clearSha1()
        long colId = column.id

        // Remove all cells that reference the deleted column
        final Iterator<LongHashSet> i = cells.keySet().iterator()

        while (i.hasNext())
        {
            final LongHashSet key = i.next()
            // Locate the uniquely identified column, regardless of axis order
            if (key.contains(colId))
            {
                i.remove()
            }
        }
        return true
    }

    /**
     * Convenience method to locate column when you have the axis name as a String and the value to find.
     * If the named axis is a rule axis, then it is expected that value is either a String name of the rule
     * or the long ID of the rule column.
     * @param axisName String name of axis.  Case does not matter when locating by name.
     * @param value Comparable value used to find the column.
     * @return Column instance if located, otherwise null.
     */
    Column findColumn(String axisName, Comparable value)
    {
        Axis axis = getAxis(axisName)
        if (!axis)
        {
            return null
        }

        return axis.findColumn(value)
    }

    /**
     * Change the value of a Column along an axis.
     * @param id long indicates the column to change
     * @param value Comparable new value to set into the column
     * @param order int (optional) new display order for column
     */
    void updateColumn(long id, Comparable value, String name = null, int order = -1i)
    {
        Axis axis = getAxisFromColumnId(id)
        if (axis == null)
        {
            throw new IllegalArgumentException("No column exists with the id ${id} within cube: ${name}")
        }
        clearSha1()
        axis.updateColumn(id, value, name, order)
    }

    /**
     * Update all of the columns along an axis at once.  Any cell referencing a column that
     * is deleted, will also be deleted from the internal sparse matrix (Map) of cells.
     * @param axisName String axis name to update
     * @param newCols  List<Column> display ordered list of columns.  An n-cube editor program,
     *             for example, would call this API with an axisName and set of new columns.
     * @return Set<Long> column ids, indicating which columns were deleted.
     */
    Set<Long> updateColumns(final String axisName, final Collection<Column> newCols, boolean allowPositiveColumnIds = false)
    {
        if (newCols == null)
        {
            throw new IllegalArgumentException("Cannot pass in null for list of columns when updating columns, cube: ${name}")
        }
        if (!axisList.containsKey(axisName))
        {
            throw new IllegalArgumentException("No axis exists with the name: ${axisName}, cube: ${name}")
        }

        final Axis axisToUpdate = axisList[axisName]
        final Set<Long> colsToDel = axisToUpdate.updateColumns(newCols, allowPositiveColumnIds)

        if (!colsToDel.empty)
        {   // If there are columns to delete, then delete any cells referencing those columns
            Iterator<LongHashSet> i = cells.keySet().iterator()
            while (i.hasNext())
            {
                LongHashSet cols = i.next()

                for (id in cols)
                {
                    if (colsToDel.contains(id))
                    {   // If cell referenced deleted column, drop the cell
                        i.remove()
                        break
                    }
                }
            }
        }

        clearSha1()
        return colsToDel
    }

    /**
     * Given the passed in Column ID, return the axis that contains the column.
     * @param id Long id of a Column on one of the Axes within this n-cube.
     * @param columnMustExist boolean, defaults to true. The axis will only be
     * returned if the column id passed in is that of a column on the axis. For
     * example, a deleted column ID, while it may contain the correct axis id,
     * it is no longer on the axis.  If this is false, then the axis will still
     * be returned even if the column id represents a column no longer on the
     * axis.  If true, both the axis and column must exist.
     * @return Axis containing the column id, or null if the id does not match
     * any columns.
     */
    Axis getAxisFromColumnId(long id, boolean columnMustExist = true)
    {
        Axis axis = idToAxis.get(id.intdiv(Axis.BASE_AXIS_ID).longValue())
        if (axis == null)
        {
            return null
        }

        if (columnMustExist)
        {
            return axis.getColumnById(id) != null ? axis : null
        }
        else
        {
            return axis
        }
    }

    /**
     * @return int total number of cells that are uniquely set (non default)
     * within this NCube.
     */
    int getNumCells()
    {
        return cells.size()
    }

    /**
     * @return long number of potential cells this n-cube potentially has
     */
    long getNumPotentialCells()
    {
        long space = 1
        for (axis in axisList.values())
        {
            space *= axis.size()
        }
        return space
    }

    /**
     * @return read-only copy of the n-cube cells.
     */
    Map<LongHashSet, T> getCellMap()
    {
        return Collections.unmodifiableMap(cells)
    }

    /**
     * Retrieve an axis (by name) from this NCube.
     * @param axisName String name of Axis to fetch.
     * @return Axis instance requested by name, or null
     * if it does not exist.
     */
    Axis getAxis(final String axisName)
    {
        return axisList[axisName]
    }

    /**
     * Add an Axis to this NCube.
     * If the axis has a default column, all cells will be added to the default column.
     * Otherwise, all cells will be cleared.
     * @param axis Axis to add
     */
    void addAxis(final Axis axis)
    {
        String axisName = axis.name
        long startId = 1
        long originalId = axis.id
        while (idToAxis.containsKey(originalId))
        {
            originalId = startId++
        }
        if (originalId != axis.id)
        {
            axis.reindex(originalId)
        }

        if (axisList.containsKey(axisName))
        {
            throw new IllegalArgumentException("An axis with the name '${axisName}' already exists on cube: ${name}")
        }

        for (axe in axisList.values())
        {
            if (axe.id == axis.id)
            {
                throw new IllegalArgumentException("An axis with the id '${axe.id}' already exists on cube: ${name}")
            }
        }

        if (axis.hasDefaultColumn())
        {   // Add default column ID of the new axis to all populated cells, effectively shifting them to the
            // default column on the new axis.
            Collection<Map.Entry<LongHashSet, T>> newCells = new ArrayDeque<>()
            long defaultColumnId = axis.defaultColId
            for (cell in cells)
            {
                LongHashSet cellKey = cell.key
                cellKey.add(defaultColumnId)
                newCells.add(cell)
            }

            cells.clear()
            for (cell in newCells)
            {
                cells[cell.key] = (T)internValue(cell.value)
            }
        }
        else
        {
            cells.clear()
        }

        axisList[axisName] = axis
        idToAxis[axis.id] = axis
        clearSha1()
    }

    /**
     * Rename an axis
     * @param oldName String old name
     * @param newName String new name
     */
    void renameAxis(final String oldName, final String newName)
    {
        if (StringUtilities.isEmpty(oldName) || StringUtilities.isEmpty(newName))
        {
            throw new IllegalArgumentException("Axis name cannot be empty or blank")
        }
        if (getAxis(newName) != null)
        {
            throw new IllegalArgumentException("There is already an axis named '${oldName}' on cube: ${name}")
        }
        final Axis axis = getAxis(oldName)
        if (axis == null)
        {
            throw new IllegalArgumentException("Axis '${oldName}' not on cube: ${name}")
        }
        axisList.remove(oldName)
        axis.name = newName
        axisList[newName] = axis
        clearSha1()
    }

    /**
     * Convert a reference axis to a non-reference axis.  'Break the Reference.'
     * @param axisName String name of reference axis to convert.
     */
    void breakAxisReference(final String axisName)
    {
        Axis axis = getAxis(axisName)
        axis.breakReference()
        clearSha1()
    }

    /**
     * Remove transform from a reference axis.
     * @param axisName String name of reference axis.
     */
    void removeAxisReferenceTransform(final String axisName)
    {
        Axis axis = getAxis(axisName)
        axis.removeTransform()
        clearSha1()
    }

    /**
     * Remove an axis from an NCube.
     * All cells will be cleared when an axis is deleted.
     * @param axisName String name of axis to remove
     * @return boolean true if removed, false otherwise
     */
    boolean deleteAxis(final String axisName)
    {
        Axis axis = axisList[axisName]
        if (!axis)
        {
            return false
        }
        cells.clear()
        clearSha1()
        idToAxis.remove(axis.id)
        return axisList.remove(axisName) != null
    }

    /**
     * @return int the number of axis (dimensions) for this n-cube.
     */
    int getNumDimensions()
    {
        return axisList.size()
    }

    /**
     * @return List<Axis> a List of all axis within this n-cube.
     */
    List<Axis> getAxes()
    {
        return new ArrayList<>(axisList.values())
    }

    /**
     * @return Set<String> containing all axis names on this n-cube.
     */
    Set<String> getAxisNames()
    {
        return new CaseInsensitiveSet<>(axisList.keySet())
    }

    /**
     * Get the optional scope keys. These are keys that if supplied, might change the returned value, but if not
     * supplied a value is still returned.  For example, an axis that has a Default column is an optional scope.
     * If no value is supplied for that axis, the Default column is chosen.  However, supplying a value for it
     * *may* change the column selected.  Similarly, a cube may reference another cube, and the 'sub-cube' may
     * use different scope keys than the calling cube.  These additional keys are located and added as optional
     * scope.
     *
     * @return Set of String scope key names that are optional.
     */
    Set<String> getOptionalScope(Map input, Map output)
    {
        final Set<String> optionalScope = new CaseInsensitiveSet<>()

        for (axis in axisList.values())
        {   // Use original axis name (not .toLowerCase() version)
            if (axis.hasDefaultColumn() || axis.type == AxisType.RULE)
            {   // Rule axis is always optional scope - it does not need a axisName to value binding like the other axis types.
                optionalScope.add(axis.name)
            }
        }

        Collection<String> declaredOptionalScope = (Collection<String>) extractMetaPropertyValue(getMetaProperty('optionalScopeKeys'), input, output)
        optionalScope.addAll(declaredOptionalScope == null ? new CaseInsensitiveSet<String>() : new CaseInsensitiveSet<>(declaredOptionalScope))
        return optionalScope
    }

    /**
     * Determine the required 'scope' needed to access all cells within this
     * NCube.  Effectively, you are determining how many axis names (keys in
     * a Map coordinate) are required to be able to access any cell within this
     * NCube.  Keep in mind, that CommandCells allow this NCube to reference
     * other NCubes and therefore the referenced NCubes must be checked as
     * well.  This code will not get stuck in an infinite loop if one cube
     * has cells that reference another cube, and it has cells that reference
     * back (it has cycle detection).
     * @return Set<String> names of axes that will need to be in an input coordinate
     * in order to use all cells within this NCube.
     */
    Set<String> getRequiredScope(Map input, Map output)
    {
        final Set<String> requiredScope = requiredAxes
        requiredScope.addAll(getDeclaredScope(input, output))
        return requiredScope
    }

    /**
     * @return Set<String> Axis names that do not have default columns.  Note, a RULE axis name will never be included
     * as a required Axis, even if it does not have a default column.  This is because you typically do not bind a
     * value to a rule axis name, but instead the columns on the rule axis execute in order.
     */
    protected Set<String> getRequiredAxes()
    {
        final Set<String> required = new CaseInsensitiveSet<>()

        for (axis in axisList.values())
        {   // Use original axis name (not .toLowerCase() version)
            if (!axis.hasDefaultColumn() && !(AxisType.RULE == axis.type))
            {
                required.add(axis.name)
            }
        }
        return required
    }

    /**
     * Get the declared required scope keys for this n-cube.  These keys are required to
     * 'talk' to this cube and any of it's subordinate cubes.  Note that the keys can be
     * a list of Strings of an expression (which could join another n-cube).
     * @param input Map containing input
     * @param output Map for writing output.
     * @return Set<String> required scope keys.
     */
    protected Set<String> getDeclaredScope(Map input, Map output)
    {
        Collection<String> declaredRequiredScope = (Collection<String>) extractMetaPropertyValue(getMetaProperty("requiredScopeKeys"), input, output)
        return declaredRequiredScope == null ? new CaseInsensitiveSet<String>() : new CaseInsensitiveSet<>(declaredRequiredScope)
    }

    /**
     * @return Map<Map, Set<String>> A map keyed by cell coordinates within this specific NCube.
     * Each map entry contains the cube names referenced by the cell coordinate in question.
     * It is not recursive.
     */
    Map<Map, Set<String>> getReferencedCubeNames()
    {
        Map<Map, Set<String>> refs = new LinkedHashMap<>()

        cells.each { LongHashSet ids, T cell ->
            if (cell instanceof CommandCell)
            {
                Map<String, Object> coord = getDisplayCoordinateFromIds(ids)
                getReferences(refs, coord, cell as CommandCell)
            }
        }

        for (axis in axes)
        {
            if (axis.type == AxisType.RULE)
            {
                for (column in axis.columnsWithoutDefault)
                {
                    Map<String, Object> coord = getDisplayCoordinateFromIds([column.id] as LongHashSet)
                    getReferences(refs, coord, column.value as CommandCell)
                }
            }

            for (column in axis.columns)
            {
                Object defaultValue = column.metaProperties[Column.DEFAULT_VALUE]
                if (defaultValue instanceof CommandCell)
                {
                    Map<String, Object> coord = getDisplayCoordinateFromIds([column.id] as LongHashSet)
                    getReferences(refs, coord, defaultValue as CommandCell)
                }
            }
        }

        // If the DefaultCellValue references another n-cube, add it into the dependency list.
        if (defaultCellValue instanceof CommandCell)
        {
            getReferences(refs, [:], defaultCellValue as CommandCell)
        }
        return refs
    }

    private static Map<Map, Set<String>> getReferences(Map<Map, Set<String>> refs, Map coord, CommandCell cmdCell)
    {
        final Set<String> cubeNames = new CaseInsensitiveSet<>()
        cmdCell.getCubeNamesFromCommandText(cubeNames)
        if (cubeNames)
        {
            refs[coord] = cubeNames
        }
        return refs
    }

    /**
     * Use this API to generate an HTML view of this NCube.
     * @param headers String list of axis names to place at top.  If more than one is listed, the first axis encountered that
     * matches one of the passed in headers, will be the axis chosen to be displayed at the top.
     * @return String containing an HTML view of this NCube.
     */
    String toHtml(String ... headers)
    {
        return new HtmlFormatter(headers).format(this)
    }

    /**
     * Format this n-cube into JSON using the passed in 'options' Map to control the desired output.
     * See the JsonFormatter's format() API for available options.
     * @return String JSON representing this entire n-cube, in the format controlled by the passed
     * in options Map.
     */
    String toFormattedJson(Map options = null)
    {
        return new JsonFormatter().format(this, options)
    }

    String toString()
    {
        return toFormattedJson()
    }

    // ----------------------------
    // Overall cube management APIs
    // ----------------------------

    /**
     * Use this API to create NCubes from a simple JSON format.
     *
     * @param json Simple JSON format
     * @return NCube instance created from the passed in JSON.  It is
     * not added to the static list of NCubes.  If you want that, call
     * addCube() after creating the NCube with this API.
     */
    static <T> NCube<T> fromSimpleJson(final String json)
    {
        try
        {
            Map options = [:]
            options[JsonReader.USE_MAPS] = true
            Map jsonNCube = (Map) JsonReader.jsonToJava(json, options)
            return hydrateCube(jsonNCube)
        }
        catch (RuntimeException | ThreadDeath e)
        {
            throw e
        }
        catch (Throwable e)
        {
            throw new IllegalStateException("Error reading cube from passed in JSON", e)
        }
    }

    /**
     * Use this API to create NCubes from a simple JSON format.
     *
     * @param stream Simple JSON format
     * @return NCube instance created from the passed in JSON.  It is
     * not added to the static list of NCubes.  If you want that, call
     * addCube() after creating the NCube with this API.
     */
    static <T> NCube<T> fromSimpleJson(final InputStream stream)
    {
        try
        {
            Map options = [:]
            options[JsonReader.USE_MAPS] = true
            Map jsonNCube = (Map) JsonReader.jsonToJava(stream, options)
            return hydrateCube(jsonNCube)
        }
        catch (RuntimeException | ThreadDeath e)
        {
            throw e
        }
        catch (Throwable e)
        {
            throw new IllegalStateException("Error reading cube from passed in JSON", e)
        }
    }

    /**
     * Load an n-cube from JSON format, which was read in by json-io as a Map of Maps.  This is
     * the 'regular' JSON format that is output from the JsonFormatter with no special settings.
     * For example, the JSON format created when indexFormat=true is not loadable by this method.
     * @param jsonNCube Map is the regular JSON format read and returned by json-io in Map of Map format.
     * @return NCube created from the passed in Map of Maps, created by json-io, from the regular JSON
     * format, which was created by the JsonFormatter.
     */
    private static <T> NCube<T> hydrateCube(Map jsonNCube)
    {
        final String cubeName = getString(jsonNCube, "ncube")  // new cubes always have ncube as they key in JSON storage
        if (StringUtilities.isEmpty(cubeName))
        {
            throw new IllegalArgumentException("JSON format must have a root 'ncube' field containing the String name of the cube.")
        }
        final NCube ncube = new NCube(cubeName)
        ncube.metaProps = new CaseInsensitiveMap()
        ncube.metaProps.putAll(jsonNCube)
        ncube.metaProps.remove('ncube')
        ncube.metaProps.remove(DEFAULT_CELL_VALUE)
        ncube.metaProps.remove(DEFAULT_CELL_VALUE_TYPE)
        ncube.metaProps.remove(DEFAULT_CELL_VALUE_URL)
        ncube.metaProps.remove(DEFAULT_CELL_VALUE_CACHE)
        ncube.metaProps.remove('ruleMode')
        ncube.metaProps.remove('axes')
        ncube.metaProps.remove('cells')
        ncube.metaProps.remove('sha1')
        transformMetaProperties(ncube.metaProps)

        String defType = jsonNCube.containsKey(DEFAULT_CELL_VALUE_TYPE) ? getString(jsonNCube, DEFAULT_CELL_VALUE_TYPE) : null
        String defUrl = jsonNCube.containsKey(DEFAULT_CELL_VALUE_URL) ? getString(jsonNCube, DEFAULT_CELL_VALUE_URL) : null
        boolean defCache = getBoolean(jsonNCube, DEFAULT_CELL_VALUE_CACHE)
        ncube.setDefaultCellValue(CellInfo.parseJsonValue(jsonNCube[DEFAULT_CELL_VALUE], defUrl, defType, defCache))

        if (!jsonNCube.containsKey("axes"))
        {
            throw new IllegalArgumentException("Must specify a list of axes for the ncube, under the key 'axes' as [{axis 1}, {axis 2}, ... {axis n}], cube: ${cubeName}")
        }

        Object[] axes = jsonNCube.axes as Object[]

        if (ArrayUtilities.isEmpty(axes))
        {
            throw new IllegalArgumentException("Must be at least one axis defined in the JSON format, cube: ${cubeName}")
        }

        Map<Object, Long> userIdToUniqueId = new CaseInsensitiveMap<>()
        long idBase = 1

        // Read axes
        for (item in axes)
        {
            Map jsonAxis = item as Map
            long axisId
            if (jsonAxis.id)
            {
                axisId = jsonAxis.id as Long
            }
            else
            {    // Older n-cube format with no 'id' on the 'axes' in the JSON
                axisId = idBase++
            }
            final String axisName = getString(jsonAxis, 'name')
            final boolean hasDefault = getBoolean(jsonAxis, 'hasDefault')
            boolean isRef = getBoolean(jsonAxis, 'isRef')

            // Remove these so they are not kept as meta-properties
            jsonAxis.remove('id')
            jsonAxis.remove('name')
            jsonAxis.remove('isRef')
            jsonAxis.remove('hasDefault')
            boolean hasColumnsKey = jsonAxis.containsKey('columns')
            Object[] columns = (Object[]) jsonAxis.remove('columns')
            transformMetaProperties(jsonAxis)

            if (isRef)
            {   // reference axis
                ReferenceAxisLoader refAxisLoader = new ReferenceAxisLoader(cubeName, axisName, jsonAxis)
                Axis newAxis = new Axis(axisName, axisId, hasDefault, refAxisLoader)
                ncube.addAxis(newAxis)
                for (column in newAxis.columns)
                {
                    userIdToUniqueId[column.id] = column.id
                }

                moveAxisMetaPropsToDefaultColumn(newAxis)
                if (columns)
                {
                    columns.each { Map column ->
                        Column col = newAxis.getColumnById(column.id as Long)
                        if (col)
                        {    // skip deleted columns
                            Iterator<Map.Entry<String, Object>> i = column.entrySet().iterator()
                            while (i.hasNext())
                            {
                                Map.Entry<String, Object> entry = i.next()
                                String key = entry.key
                                if ('id' != key)
                                {
                                    col.setMetaProperty(key, entry.value)
                                }
                            }
                            transformMetaProperties(col.metaProps)
                        }
                    }
                }
            }
            else
            {
                if (!hasColumnsKey)
                {
                    throw new IllegalArgumentException("'columns' must be specified, axis: ${axisName}, cube: ${cubeName}")
                }

                AxisType type = AxisType.valueOf(getString(jsonAxis, 'type'))
                AxisValueType valueType = AxisValueType.valueOf(getString(jsonAxis, 'valueType'))
                final int preferredOrder = getLong(jsonAxis, 'preferredOrder').intValue()
                boolean fireAll = true
                if (jsonAxis.containsKey('fireAll'))
                {
                    fireAll = getBoolean(jsonAxis, 'fireAll')
                }

                // Remove known fields so that they are not listed as meta properties.
                jsonAxis.remove('type')
                jsonAxis.remove('valueType')
                jsonAxis.remove('preferredOrder')
                jsonAxis.remove('multiMatch')
                jsonAxis.remove('fireAll')

                Axis axis = new Axis(axisName, type, valueType, hasDefault, preferredOrder, axisId, fireAll)
                ncube.addAxis(axis)
                axis.metaProps = new CaseInsensitiveMap<>()
                axis.metaProps.putAll(jsonAxis)

                if (axis.metaProps.size() < 1)
                {
                    axis.metaProps = null
                }
                else
                {
                    moveAxisMetaPropsToDefaultColumn(axis)
                }

                // Temporary - eventually should be removed.  Fixes rule columns with no or non-unique names
                healUnamedRules(type, columns)

                // Read columns
                for (col in columns)
                {
                    Map jsonColumn = col as Map
                    Object value = jsonColumn['value']
                    String url = jsonColumn['url'] as String
                    String colType = jsonColumn['type'] as String
                    Object id = jsonColumn['id']
                    String colName = jsonColumn[Column.NAME] as String

                    if (value == null)
                    {
                        if (id == null)
                        {
                            throw new IllegalArgumentException("Missing 'value' field on column or it is null, axis: ${axisName}, cube: ${cubeName}")
                        }
                        else
                        {   // Allows you to skip setting both id and value to the same value.
                            value = id
                        }
                    }

                    boolean cache = false
                    if (jsonColumn.containsKey('cache'))
                    {
                        cache = getBoolean(jsonColumn, 'cache')
                    }

                    Column colAdded
                    Long suggestedId = (id instanceof Long) ? id as Long: null
                    if (type == AxisType.DISCRETE || type == AxisType.NEAREST)
                    {
                        colAdded = ncube.addColumn(axis.name, CellInfo.parseJsonValue(value, null, colType, false) as Comparable, colName, suggestedId)
                    }
                    else if (type == AxisType.RANGE)
                    {
                        Object[] rangeItems = value as Object[]
                        if (rangeItems.length != 2)
                        {
                            throw new IllegalArgumentException("Range must have exactly two items, axis: ${axisName}, cube: ${cubeName}")
                        }
                        Comparable low = CellInfo.parseJsonValue(rangeItems[0], null, colType, false) as Comparable
                        Comparable high = CellInfo.parseJsonValue(rangeItems[1], null, colType, false) as Comparable
                        colAdded = ncube.addColumn(axis.name, new Range(low, high), colName, suggestedId)
                    }
                    else if (type == AxisType.SET)
                    {
                        Object[] rangeItems = value as Object[]
                        RangeSet rangeSet = new RangeSet()
                        for (pt in rangeItems)
                        {
                            if (pt instanceof Object[])
                            {
                                Object[] rangeValues = pt as Object[]
                                if (rangeValues.length != 2)
                                {
                                    throw new IllegalArgumentException("Set Ranges must have two values only, range length: ${rangeValues.length}, axis: ${axisName}, cube: ${cubeName}")
                                }
                                Comparable low = CellInfo.parseJsonValue(rangeValues[0], null, colType, false) as Comparable
                                Comparable high = CellInfo.parseJsonValue(rangeValues[1], null, colType, false) as Comparable
                                Range range = new Range(low, high)
                                rangeSet.add(range)
                            }
                            else
                            {
                                rangeSet.add(CellInfo.parseJsonValue(pt, null, colType, false) as Comparable)
                            }
                        }
                        colAdded = ncube.addColumn(axis.name, rangeSet, colName, suggestedId)
                    }
                    else if (type == AxisType.RULE)
                    {
                        Object cmd = CellInfo.parseJsonValue(value, url, colType, cache)
                        if (!(cmd instanceof CommandCell))
                        {
                            cmd = new GroovyExpression('false', null, cache)
                        }
                        colAdded = ncube.addColumn(axis.name, cmd as CommandCell, colName, suggestedId)
                    }
                    else
                    {
                        throw new IllegalArgumentException("Unsupported Axis Type '${type}' for simple JSON input, axis: ${axisName}, cube: ${cubeName}")
                    }

                    if (id != null)
                    {
                        userIdToUniqueId[id] = colAdded.id
                    }

                    colAdded.metaProps = new CaseInsensitiveMap<>()
                    colAdded.metaProps.putAll(jsonColumn)
                    colAdded.metaProps.remove('id')
                    colAdded.metaProps.remove('value')
                    colAdded.metaProps.remove('type')
                    colAdded.metaProps.remove('url')
                    colAdded.metaProps.remove('cache')

                    if (colAdded.metaProps.size() < 1)
                    {
                        colAdded.metaProps = null
                    }
                    else
                    {
                        transformMetaProperties(colAdded.metaProps)
                    }
                }
            }
        }

        // Read cells
        if (jsonNCube.containsKey('cells'))
        {   // Allow JSON to have no cells - empty cube
            Object[] cells = (Object[]) jsonNCube['cells']

            for (cell in cells)
            {
                JsonObject cMap = cell as JsonObject
                Object ids = cMap['id']
                String type = cMap['type'] as String
                String url = cMap['url'] as String
                boolean cache = false

                if (cMap.containsKey('cache'))
                {
                    cache = getBoolean(cMap, 'cache')
                }

                Object v = CellInfo.parseJsonValue(cMap['value'], url, type, cache)

                if (ids instanceof Object[])
                {   // If specified as ID array, build coordinate that way
                    LongHashSet colIds = new LongHashSet()
                    for (id in (Object[])ids)
                    {
                        if (!userIdToUniqueId.containsKey(id))
                        {
                            continue
                        }
                        colIds.add(userIdToUniqueId[id])
                    }
                    try
                    {
                        ncube.setCellById(v, colIds)
                    }
                    catch (InvalidCoordinateException ignore)
                    {
                        LOG.debug("Orphaned cell on n-cube: ${cubeName}, ids: ${colIds}")
                    }
                }
                else
                {
                    if (!(cMap['key'] instanceof JsonObject))
                    {
                        throw new IllegalArgumentException("'key' must be a JSON object {}, cube: ${cubeName}")
                    }

                    JsonObject<String, Object> keys = (JsonObject<String, Object>) cMap['key']
                    for (entry in keys.entrySet())
                    {
                        keys[entry.key] = CellInfo.parseJsonValue(entry.value, null, null, false)
                    }
                    try
                    {
                        ncube.setCell(v, keys)
                    }
                    catch (CoordinateNotFoundException ignore)
                    {
                        LOG.debug("Orphaned cell on n-cube: ${cubeName}, coord: ${keys}")
                    }
                }
            }
        }

        return ncube
    }

    private static void healUnamedRules(AxisType type, Object[] columns)
    {
        if (type != AxisType.RULE)
        {
            return
        }

        int count = 1
        Set<String> names = new CaseInsensitiveSet<>()

        for (Object col : columns)
        {
            Map column = col as Map
            String name = column.name
            if (!name || names.contains(name))
            {
                MapEntry result = generateRuleName(names, count)
                column.name = result.key
                count = result.value as int
                names.add(column.name as String)
            }
            else
            {
                names.add(name)
            }
        }
    }

    private static MapEntry generateRuleName(Set<String> names, int count)
    {
        String name
        while (names.contains(name = "BR${count++}"))
        ;
        return new MapEntry(name, count)
    }

    /**
     * Snag all meta-properties on Axis that start with Axis.DEFAULT_COLUMN_PREFIX, as this
     * is where the default column's meta properties are stored, and copy them to the default
     * column (if one exists)
     */
    private static void moveAxisMetaPropsToDefaultColumn(Axis axis)
    {
        Column defCol = axis.defaultColumn
        if (!defCol)
        {
            return
        }
        Iterator<Map.Entry<String, Object>> i = axis.metaProps.entrySet().iterator()
        while (i.hasNext())
        {
            Map.Entry<String, Object> entry = i.next()
            String key = entry.key
            if (key.startsWith(JsonFormatter.DEFAULT_COLUMN_PREFIX))
            {
                defCol.setMetaProperty(key - JsonFormatter.DEFAULT_COLUMN_PREFIX, entry.value)
                i.remove()  // do not leave the column_default_* properties on the Axis
            }
        }
    }

    /**
     * Convert the values on the value side of the Map from JsonObject to an appropriate
     * CellInfo.  If the value is not a JsonObject, it is left alone (primitives).
     * @param props Map of String meta-property keys to values
     */
    protected static void transformMetaProperties(Map props)
    {
        List<MapEntry> entriesToUpdate = []
        for (entry in props.entrySet())
        {
            if (entry.value instanceof JsonObject)
            {
                JsonObject map = (JsonObject) entry.value
                Boolean cache = (Boolean) map['cache']
                Object value = CellInfo.parseJsonValue(map['value'], (String) map['url'], (String) map['type'], cache == null ? false : cache)
                entriesToUpdate.add(new MapEntry(entry.key, value))
            }
            else if (entry.value instanceof CellInfo)
            {
                CellInfo info = entry.value as CellInfo
                entriesToUpdate.add(new MapEntry(entry.key, info.recreate()))
            }
        }

        for (entry in entriesToUpdate)
        {
            props[entry.key] = entry.value
        }
    }

    protected static String getString(Map obj, String key)
    {
        Object val = obj[key]
        if (val instanceof String)
        {
            return (String) val
        }
        String clazz = val == null ? "null" : val.class.name
        throw new IllegalArgumentException("Expected 'String' for key '${key}' but instead found: ${clazz}")
    }

    protected static Long getLong(Map obj, String key)
    {
        Object val = obj[key]
        if (val instanceof Number)
        {
            return ((Number) val).longValue()
        }
        if (val instanceof String)
        {
            try
            {
                return Long.parseLong(val as String)
            }
            catch(Exception ignored)
            { }
        }
        String clazz = val == null ? "null" : val.class.name
        throw new IllegalArgumentException("Expected 'Long' for key '${key}' but instead found: ${clazz}")
    }

    protected static Boolean getBoolean(Map obj, String key)
    {
        Object val = obj[key]
        if (val instanceof Boolean)
        {
            return (Boolean) val
        }
        if (val instanceof String)
        {
            return "true".equalsIgnoreCase((String) val)
        }
        if (val == null)
        {
            return false
        }
        String clazz = val.class.name
        throw new IllegalArgumentException("Expected 'Boolean' for key '${key}' but instead found: ${clazz}")
    }

    /**
     * Create an equivalent n-cube as 'this'.
     */
    NCube duplicate(String newName = name)
    {
        NCube copy = createCubeFromBytes(cubeAsGzipJsonBytes)
        copy.setName(newName)
        copy.applicationID = this.applicationID
        return copy
    }

    /**
     * Create an 'empty' NCube that matches this NCube, but with no columns on it (same number of axes).
     */
    protected NCube createStubCube()
    {
        NCube stub = duplicate(name)
        stub.axes.each { Axis axis ->
            axis.columns.each { Column column ->
                stub.deleteColumn(axis.name, column.valueThatMatches)
            }
        }
        return stub
    }

    boolean equals(Object other)
    {
        if (!(other instanceof NCube))
        {
            return false
        }

        if (this.is(other))
        {
            return true
        }

        return sha1().equalsIgnoreCase(((NCube) other).sha1())
    }

    int hashCode()
    {
        return name.hashCode()
    }

    void clearSha1()
    {
        sha1 = null
    }

    /**
     * @return List<Map<String, T>> which is a List of coordinates, one for each populated cell within the
     * n-cube. These coordinates can be used with setCell(), removeCell(), containsCell(), and getCellNoExecute()
     * APIs.  To use with getCell(), any rule axis bindings in the coordinate would need to be removed.  This is
     * because it is expected that getCell() will run all conditions on a rule axis.
     */
    List<Map<String, T>> getPopulatedCellCoordinates()
    {
        List<Map<String, T>> coords = []
        for (entry in cells.entrySet())
        {
            LongHashSet colIds = entry.key
            Map<String, T> coord = (Map<String, T>) getCoordinateFromIds(colIds)
            coords.add(coord)
        }

        return coords
    }

    /**
     * @return SHA1 value for this n-cube.  The value is durable in that Axis order and
     * cell order do not affect the SHA1 value.
     */
    String sha1()
    {
        // Check if the SHA1 is already calculated.  If so, return it.
        // In order to cache it successfully, all mutable operations on n-cube must clear the SHA1.
        if (StringUtilities.hasContent(sha1))
        {
            return sha1
        }

        final byte sep = 0
        MessageDigest sha1Digest = EncryptionUtilities.SHA1Digest
        sha1Digest.update(name == null ? ''.bytes : name.bytes)
        sha1Digest.update(sep)

        deepSha1(sha1Digest, defaultCellValue, sep)
        deepSha1(sha1Digest, new TreeMap<>(metaProperties), sep)

        // Need deterministic ordering (sorted by Axis name will do that)
        Map<String, Axis> sortedAxes = new TreeMap<>(axisList)
        sha1Digest.update(A_BYTES)       // a=axes
        sha1Digest.update(sep)

        for (entry in sortedAxes.entrySet())
        {
            Axis axis = entry.value
            sha1Digest.update(axis.name.toLowerCase().bytes)
            sha1Digest.update(sep)
            sha1Digest.update(String.valueOf(axis.columnOrder).bytes)
            sha1Digest.update(sep)
            sha1Digest.update(axis.type.name().bytes)
            sha1Digest.update(sep)
            sha1Digest.update(axis.valueType.name().bytes)
            sha1Digest.update(sep)
            sha1Digest.update(axis.hasDefaultColumn() ? TRUE_BYTES : FALSE_BYTES)
            sha1Digest.update(sep)
            if (!axis.fireAll)
            {   // non-default value, add to SHA1 because it's been changed (backwards sha1 compatible)
                sha1Digest.update(O_BYTES)
                sha1Digest.update(sep)
            }
            if (!MapUtilities.isEmpty(axis.metaProps))
            {
                deepSha1(sha1Digest, new TreeMap<>(axis.metaProps), sep)
            }
            sha1Digest.update(sep)
            boolean displayOrder = axis.columnOrder == Axis.DISPLAY
            if (axis.reference)
            {
                for (column in axis.columns)
                {
                    if (!MapUtilities.isEmpty(column.metaProps))
                    {
                        deepSha1(sha1Digest, column.metaProps, sep)
                    }
                }
            }
            else
            {
                for (column in axis.columnsWithoutDefault)
                {
                    Object v = column.value
                    Object safeVal = (v == null) ? '' : v
                    sha1Digest.update(safeVal.toString().bytes)
                    sha1Digest.update(sep)
                    if (!MapUtilities.isEmpty(column.metaProps))
                    {
                        deepSha1(sha1Digest, column.metaProps, sep)
                    }
                    sha1Digest.update(sep)
                    if (displayOrder)
                    {
                        String order = String.valueOf(column.displayOrder)
                        sha1Digest.update(order.bytes)
                        sha1Digest.update(sep)
                    }
                }

                if (axis.hasDefaultColumn() && !MapUtilities.isEmpty(axis.defaultColumn.metaProperties))
                {
                    deepSha1(sha1Digest, axis.defaultColumn.metaProperties, sep)
                }
            }
        }

        // Deterministic ordering of cell values with coordinates.
        // 1. Build String SHA-1 of coordinate + SHA-1 of cell contents.
        // 2. Combine and then sort.
        // 3. Build SHA-1 from this.
        sha1Digest.update(C_BYTES)  // c = cells
        sha1Digest.update(sep)

        if (numCells > 0)
        {
            List<String> sha1s = new ArrayList<>(cells.size()) as List
            MessageDigest tempDigest = EncryptionUtilities.SHA1Digest

            for (entry in cells.entrySet())
            {
                String keySha1 = columnIdsToString(entry.key)
                deepSha1(tempDigest, entry.value, sep)
                String valueSha1 = StringUtilities.encode(tempDigest.digest())
                sha1s.add(EncryptionUtilities.calculateSHA1Hash((keySha1 + valueSha1).bytes))
                tempDigest.reset()
            }

            Collections.sort(sha1s)

            for (sha_1 in sha1s)
            {
                sha1Digest.update(sha_1.bytes)
            }
        }
        sha1 = StringUtilities.encode(sha1Digest.digest())
        return sha1
    }

    private String columnIdsToString(Set<Long> columns)
    {
        List<String> list = new ArrayList(columns.size())
        for (colId in columns)
        {
            Axis axis = getAxisFromColumnId(colId)
            if (axis != null)
            {   // Rare case where a column has an invalid ID.
                Column column = axis.getColumnById(colId)
                Object value = column.value
                list.add(value == null ? 'null' : column.value.toString())
            }
        }
        Collections.sort(list)
        StringBuilder s = new StringBuilder()
        for (str in list)
        {
            s.append(str)
            s.append('|')
        }
        return s.toString()
    }

    private static void deepSha1(MessageDigest md, Object value, byte sep)
    {
        Deque<Object> stack = new LinkedList<>()
        stack.addFirst(value)
        Set<Object> visited = new HashSet<>()

        while (!stack.empty)
        {
            value = stack.removeFirst()
            if (visited.contains(value))
            {
                continue
            }
            visited.add(value)

            if (value == null)
            {
                md.update(NULL_BYTES)
                md.update(sep)
            }
            else if (value.class.array)
            {
                int len = Array.getLength(value)

                md.update(ARRAY_BYTES)
                md.update(String.valueOf(len).bytes)
                md.update(sep)
                for (int i=0; i < len; i++)
                {
                    stack.addFirst(Array.get(value, i))
                }
            }
            else if (value instanceof Collection)
            {
                Collection col = (Collection) value
                md.update(COL_BYTES)
                md.update(String.valueOf(col.size()).bytes)
                md.update(sep)
                stack.addAll(col)
            }
            else if (value instanceof Map)
            {
                Map map = value as Map
                md.update(MAP_BYTES)
                md.update(String.valueOf(map.size()).bytes)
                md.update(sep)

                for (entry in map.entrySet())
                {
                    stack.addFirst(entry.value)
                    stack.addFirst(entry.key)
                }
            }
            else
            {
                if (value instanceof String)
                {
                    md.update((value as String).bytes)
                    md.update(sep)
                }
                else if (value instanceof CommandCell)
                {
                    CommandCell cmdCell = value as CommandCell
                    md.update(cmdCell.class.name.bytes)
                    md.update(sep)
                    if (cmdCell.url != null)
                    {
                        md.update(cmdCell.url.bytes)
                        md.update(sep)
                    }
                    if (cmdCell.cmd != null)
                    {
                        md.update(cmdCell.cmd.bytes)
                        md.update(sep)
                    }
                    md.update(cmdCell.url != null ? TRUE_BYTES : FALSE_BYTES)  // t (url) or f (no url)
                    md.update(sep)
                    md.update(cmdCell.cacheable ? TRUE_BYTES : FALSE_BYTES)
                    md.update(sep)
                }
                else
                {
                    String strKey = value.toString()
                    if (strKey.contains('@'))
                    {
                        md.update(toJson(value).bytes)
                    }
                    else
                    {
                        md.update(strKey.bytes)
                    }
                }
                md.update(sep)
            }
        }
    }

    /**
     * Test if another n-cube is 'comparable' with this n-cube.  This means that they have the same number of
     * dimensions (axes) and each axis has the same name.  This test will allow many operations to
     * be performed on two cubes once it is known they are 'compatible' such as union, intersection, even matrix
     * operations like multiply, etc.
     * @param other NCube to compare to this ncube.
     * @return boolean true if the passed in cube has the same number of axes, the axes have the same names, otherwise
     * false.
     */
    boolean isComparableCube(NCube<T> other)
    {
        if (numDimensions != other.numDimensions)
        {   // Must have same dimensionality
            return false
        }

        Set<String> a1 = new CaseInsensitiveSet<>(axisList.keySet())
        Set<String> a2 = new CaseInsensitiveSet<>(other.axisList.keySet())
        a1.removeAll(a2)

        if (!a1.empty)
        {   // Axis names must be all be the same (ignoring case)
            return false
        }

        for (axis in axisList.values())
        {
            Axis otherAxis = other.getAxis(axis.name)
            if (axis.type != otherAxis.type)
            {   // Axes must be same type (DISCRETE, RANGE, SET, NEAREST, or RULE)
                return false
            }
            if (axis.valueType != otherAxis.valueType)
            {   // Axes must be same value type (LONG, DOUBLE, DATE, etc.)
                return false
            }
        }

        return true
    }

    /**
     * Merge the passed in List of Delta's into this n-cube.
     * @param deltas List of Delta instances.
     */
    void mergeDeltas(List<Delta> deltas)
    {
        List<Delta> columnReorders = []
        for (Delta delta : deltas)
        {
            switch (delta.location)
            {
                case Delta.Location.NCUBE:
                    switch (delta.locId)
                    {
                        case 'NAME':
                            name = delta.destVal
                            break

                        case 'DEFAULT_CELL':
                            CellInfo cellInfo = delta.destVal as CellInfo
                            Object cellValue = cellInfo.isUrl ?
                                    CellInfo.parseJsonValue(null, cellInfo.value, cellInfo.dataType, cellInfo.isCached) :
                                    CellInfo.parseJsonValue(cellInfo.value, null, cellInfo.dataType, cellInfo.isCached)
                            setDefaultCellValue((T) cellValue)
                            break
                    }
                    break

                case Delta.Location.NCUBE_META:
                    switch (delta.type)
                    {
                        case Delta.Type.ADD:
                        case Delta.Type.UPDATE:
                            MapEntry entry = delta.destVal as MapEntry
                            setMetaProperty(entry.key as String, entry.value)
                            break

                        case Delta.Type.DELETE:
                            MapEntry entry = delta.sourceVal as MapEntry
                            removeMetaProperty(entry.key as String)
                            break
                    }
                    break

                case Delta.Location.AXIS:
                    Axis receiverAxis = delta.sourceVal as Axis
                    Axis transmitterAxis = delta.destVal as Axis

                    switch (delta.type)
                    {
                        case Delta.Type.ADD:
                            if (getAxis(transmitterAxis.name) == null)
                            {   // Only add if not already there.
                                addAxis(transmitterAxis)
                            }
                            break

                        case Delta.Type.UPDATE:
                            if (receiverAxis)
                            {
                                receiverAxis.columnOrder = transmitterAxis.columnOrder
                                receiverAxis.fireAll = transmitterAxis.fireAll
                            }
                            break

                        case Delta.Type.DELETE:
                            deleteAxis(receiverAxis.name)
                            break
                    }
                    break

                case Delta.Location.AXIS_META:
                    Axis axis = getAxis(delta.locId as String)
                    if (!axis)
                    {
                        break
                    }
                    switch (delta.type)
                    {
                        case Delta.Type.ADD:
                        case Delta.Type.UPDATE:
                            MapEntry entry = delta.destVal as MapEntry
                            axis.setMetaProperty(entry.key as String, entry.value)
                            break
                        case Delta.Type.DELETE:
                            MapEntry entry = delta.sourceVal as MapEntry
                            axis.removeMetaProperty(entry.key as String)
                            break
                    }
                    break

                case Delta.Location.COLUMN:
                    String axisName = delta.locId as String
                    Axis axis = getAxis(axisName)
                    if (!axis)
                    {   // axis not found
                        break
                    }
                    switch (delta.type)
                    {
                        case Delta.Type.ADD:
                            Column column = delta.destVal as Column
                            if (column.default)
                            {
                                if (!axis.hasDefaultColumn())
                                {
                                    addColumn(axisName, null, column.columnName)
                                }
                            }
                            else
                            {
                                Column existingCol = axis.locateDeltaColumn(column)
                                if (!existingCol || existingCol.default)
                                {   // Only add column if it is not already there
                                    addColumn(axisName, column.value, column.columnName, column.id)
                                }
                            }
                            break

                        case Delta.Type.DELETE:
                            Column column = delta.sourceVal as Column
                            Column existingCol = axis.locateDeltaColumn(column)
                            if (axis.type == AxisType.RULE)
                            {
                                deleteColumn(axisName, existingCol.columnName ?: existingCol.id as Long)
                            }
                            else
                            {
                                deleteColumn(axisName, existingCol.value)
                            }
                            break

                        case Delta.Type.UPDATE:
                            Column oldCol = delta.sourceVal as Column
                            Column newCol = delta.destVal as Column
                            Column existingCol = axis.locateDeltaColumn(oldCol)
                            if (existingCol)
                            {
                                updateColumn(existingCol.id, newCol.value)
                            }
                            break

                        case Delta.Type.ORDER:
                            columnReorders.add(delta)
                            break
                    }
                    break

                case Delta.Location.COLUMN_META:
                    Map<String, Object> helperId = delta.locId as Map<String, Object>
                    Axis axis = getAxis(helperId.axis as String)
                    if (!axis)
                    {
                        break
                    }
                    Column column = axis.locateDeltaColumn(helperId.column as Column)
                    if (!column)
                    {
                        break
                    }
                    MapEntry oldPair = delta.sourceVal as MapEntry
                    MapEntry newPair = delta.destVal as MapEntry

                    switch (delta.type)
                    {
                        case Delta.Type.ADD:
                        case Delta.Type.UPDATE:
                            column.setMetaProperty(newPair.key as String, newPair.value)
                            break

                        case Delta.Type.DELETE:
                            column.removeMetaProperty(oldPair.key as String)
                            break
                    }
                    break

                case Delta.Location.CELL:
                    Set<Long> coords = delta.locId as Set<Long>

                    switch (delta.type)
                    {
                        case Delta.Type.ADD:
                        case Delta.Type.UPDATE:
                            setCellById((T) (delta.destVal as CellInfo).recreate(), coords)
                            break

                        case Delta.Type.DELETE:
                            removeCellById(coords)
                            break
                    }
                    break

                case Delta.Location.CELL_META:
                    // TODO - cell meta-properties not yet implemented
                    break

                case Delta.Location.TEST:
                    List<NCubeTest> tests = delta.sourceList.toList() as List<NCubeTest>

                    switch (delta.type)
                    {
                        case Delta.Type.ADD:
                            tests.add(delta.destVal as NCubeTest)
                            break
                        case Delta.Type.DELETE:
                            String testName = delta.locId as String
                            NCubeTest test = tests.find { NCubeTest cubeTest -> cubeTest.name == testName }
                            tests.remove(test)
                            break
                    }

                    testData = tests.toArray()
                    break

                case Delta.Location.TEST_COORD:
                    List<NCubeTest> tests = delta.sourceList.toList() as List<NCubeTest>
                    String testName = delta.locId as String
                    NCubeTest test = tests.find { NCubeTest cubeTest -> cubeTest.name == testName }
                    Map<String, CellInfo> coords = test.coord

                    switch (delta.type)
                    {
                        case Delta.Type.ADD:
                        case Delta.Type.UPDATE:
                            Map.Entry<String, CellInfo> newCoordEntry = delta.destVal as Map.Entry<String, CellInfo>
                            coords[newCoordEntry.key] = newCoordEntry.value
                            break
                        case Delta.Type.DELETE:
                            Map.Entry<String, CellInfo> oldCoordEntry = delta.sourceVal as Map.Entry<String, CellInfo>
                            coords.remove(oldCoordEntry.key)
                            break
                    }

                    NCubeTest newTest = new NCubeTest(test.name, coords, test.assertions)
                    tests.remove(test)
                    tests.add(newTest)
                    testData = tests.toArray()
                    break

                case Delta.Location.TEST_ASSERT:
                    List<NCubeTest> tests = delta.sourceList.toList() as List<NCubeTest>
                    String testName = delta.locId as String
                    NCubeTest test = tests.find { NCubeTest cubeTest -> cubeTest.name == testName }
                    List<CellInfo> assertions = test.assertions.toList()
                    CellInfo newAssert = delta.destVal as CellInfo
                    CellInfo oldAssert = delta.sourceVal as CellInfo

                    switch (delta.type)
                    {
                        case Delta.Type.ADD:
                            assertions.add(newAssert)
                            break
                        case Delta.Type.UPDATE:
                            assertions.remove(oldAssert)
                            assertions.add(newAssert)
                            break
                        case Delta.Type.DELETE:
                            assertions.remove(oldAssert)
                            break
                    }

                    NCubeTest newTest = new NCubeTest(test.name, test.coord, assertions.toArray() as CellInfo[])
                    tests.remove(test)
                    tests.add(newTest)
                    testData = tests.toArray()
                    break
            }
        }

        for (Delta delta : columnReorders)
        {
            String axisName = delta.locId as String
            if (axisName)
            {
                Axis axis = getAxis(delta.locId as String)
                if (axis)
                {
                    Set<Column> updatedCols = []
                    Set<Column> oldCols = delta.destVal as Set<Column>
                    for (Column oldCol : oldCols)
                    {
                        Column newCol = axis.getColumnById(oldCol.id)
                        if (newCol)
                        {
                            newCol.displayOrder = oldCol.displayOrder
                            updatedCols.add(newCol)
                        }
                    }
                    updateColumns(axisName, updatedCols, true)
                }
            }
        }

        clearSha1()
    }

    List<NCubeTest> getTestData()
    {
        NCubeTestReader.convert(getMetaProperty(METAPROPERTY_TEST_DATA) as String)
    }

    void setTestData(Object[] tests)
    {
        setMetaProperty(METAPROPERTY_TEST_DATA, new NCubeTestWriter().format(tests))
    }

    /**
     * Fetch a 'display' coordinate from the passed in Set of IDs.  The returned coordinate
     * will have the String 'default column' for column ids that represent default columns.
     * If any of the columns have a name (like columns on a rule axis), the name will be
     * included in the String associated to the axis name entry.
     * @param idCoord Set<Long> column IDs.
     * @return Map<String, T> where the keys of the Map are axis names, and the values
     * are the associated values that would bind to the axes, except for default column
     * and rule axes.  This API is typically used for display purposes.
     */
    Map<String, Object> getDisplayCoordinateFromIds(Set<Long> idCoord)
    {
        Map<String, Object> properCoord = new CaseInsensitiveMap<>()
        for (colId in idCoord)
        {
            Axis axis = getAxisFromColumnId(colId)
            if (axis == null)
            {
                continue
            }
            Column column = axis.getColumnById(colId)
            Object value = column.valueThatMatches
            if (value == null)
            {
                value = "default column"
            }

            String name = column.columnName
            if (name != null)
            {
                properCoord[axis.name] = "(${name.toString()}): ${value}"
            }
            else
            {
                properCoord[axis.name] = value
            }
        }
        return properCoord
    }

    /**
     * Turn a Set of column IDs into a 'normal' coordinate that has values that will
     * bind to axes the 'normal' way.  For RULE axes, the key will be the rule axis name
     * and the value will be the rule name.  If there is no rule name, then the value will
     * be the long ID of the column on the rule axis.
     * @param idCoord Set<Long> ids that describe a binding to a cell
     * @return Map standard coordinate.  Note that this coordinate has an
     * entry for a rule axis, which typically would NOT be used for a call to getCell(),
     * as normally rule axes have no binding (so all conditions on the rule axis execute).
     * The returned coordinate, however, is useful for setCell(), removeCell(), containsCell(),
     * and getCellNoExecute() APIs.  To use with getCell(), remove the entry or entries
     * that have rule axis names.
     */
    Map getCoordinateFromIds(Set<Long> idCoord)
    {
        Map coord = new CaseInsensitiveMap<>()
        for (colId in idCoord)
        {
            Axis axis = getAxisFromColumnId(colId)
            if (axis == null)
            {
                continue
            }
            Object value
            if (axis.type == AxisType.RULE)
            {   // Favor rule name first, then use column ID if no rule name exists.
                Column column = axis.getColumnById(colId)
                String name = column.columnName
                if (name != null)
                {
                    value = name
                }
                else
                {
                    value = colId
                }
            }
            else
            {
                Column column = axis.getColumnById(colId)
                value = column.valueThatMatches
            }
            coord[axis.name] = value
        }
        return coord
    }

    /**
     * Determine highest ID used by the axes.  When adding an additional axis, it is recommended to use this value
     * plus 1 for adding another axis.
     * @return long the highest ID used by any axis.
     */
    long getMaxAxisId()
    {
        long max = 0
        for (axis in axisList.values())
        {
            long axisId = axis.id
            if (axisId > max)
            {
                max = axisId
            }
        }
        return max
    }

    protected static String toJson(Object o)
    {
        if (o == null)
        {
            return "null"
        }
        try
        {
            return JsonWriter.objectToJson(o)
        }
        catch (Exception ignore)
        {
            throw new IllegalStateException("Unable to convert value to JSON: ${o.toString()}")
        }
    }

    /**
     * Ensure that the passed in name is a valid n-cube name,
     * @param cubeName String cube name to test.
     * @throws IllegalArgumentException if the name is invalid, otherwise it is silent.
     */
    static void validateCubeName(String cubeName)
    {
        if (StringUtilities.isEmpty(cubeName))
        {
            throw new IllegalArgumentException("n-cube name cannot be null or empty")
        }

        Matcher m = Regexes.validCubeName.matcher(cubeName)
        if (m.matches())
        {
            return
        }
        throw new IllegalArgumentException("Invalid n-cube name: '${cubeName}'. Name can only contain a-z, A-Z, 0-9, '.', '_', '-'")
    }

    /**
     * Create a cube from a byte[] of JSON bytes, or a gzip byte[] of JSON bytes, both
     * are JSON content representing an n-cube.  Calling ncube.toFormattedJson() is the source
     * of the JSON format used.
     */
    static <T> NCube<T> createCubeFromBytes(byte[] bytes)
    {
        return createCubeFromStream(new ByteArrayInputStream(bytes))
    }

    /**
     * Create an n-cube from a stream of bytes.  The stream can be either a JSON stream
     * of an n-cube or a g-zip JSON stream.
     */
    static <T> NCube<T> createCubeFromStream(InputStream stream)
    {
        try
        {
            return fromSimpleJson(rawStreamToInputStream(stream))
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Error reading cube from stream.", e)
        }
        finally
        {
            IOUtilities.close(stream)
        }
    }

    static protected InputStream rawStreamToInputStream(InputStream stream)
    {
        if (stream == null)
        {
            throw new IllegalArgumentException("Stream cannot be null to create cube.")
        }

        byte[] header = new byte[2]
        InputStream newStream = new BufferedInputStream(stream)
        newStream.mark(5)

        int count = newStream.read(header)
        if (count < 2)
        {
            throw new IllegalStateException("Invalid cube existing of 0 or 1 bytes")
        }

        newStream.reset()
        return ByteUtilities.isGzipped(header) ? new GZIPInputStream(newStream) : newStream
    }

    /**
     * @return byte[] containing the bytes of this N-Cube when converted to JSON format and then gzipped.
     */
    byte[] getCubeAsGzipJsonBytes()
    {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream()
        OutputStream gzipOut = null

        try
        {
            gzipOut = new GZIPOutputStream(byteOut, 8192)
            new JsonFormatter(gzipOut).formatCube(this, null)
        }
        catch (Exception e)
        {
            throw new IllegalStateException("Error writing cube to stream", e)
        }
        finally
        {
            IOUtilities.close(gzipOut)
        }
        return byteOut.toByteArray()
    }

    /**
     * Set the name of this n-cube
     * @param name String name
     */
    void setName(String name)
    {
        this.name = name
        clearSha1()
    }

    /**
     * This API allows Groovy code to do this:
     * ncube.axisName or ncube['axisName'] to fetch an axis.
     * @param axisName String name of axis to get (case ignored)
     * @return Axis if found, null otherwise.
     */
    Axis get(String axisName)
    {
        return axisList[axisName]
    }

    /**
     * Intern the passed in value.  Collapses (folds) equivalent instances into same instance.
     * @param value Object to intern (if possible)
     * @return interned instance (if internable) otherwise passed-in instance is returned.
     */
    private Object internValue(Object value)
    {
        if (value == null)
        {
            return null
        }

        if (!MetaUtils.isLogicalPrimitive(value.class))
        {   // don't attempt to intern null (NPE) or non-primitive instances
            return value
        }

        if (primitives.containsKey(value))
        {   // intern it (re-use instance)
            return primitives[value]
        }

        Object singletonInstance = primitives.putIfAbsent(value, value)
        if (singletonInstance != null)
        {
            return singletonInstance
        }
        return value
    }

    private void throwIf(boolean throwCondition, Exception ex)
    {
        if(throwCondition)
        {
            throw ex
        }
    }

    private Set<Long> hydrateBoundColumns(String rowAxisName, String colAxisName, Map addlBindings)
    {
        Set<Long> boundColumns = [] as Set
        Set<String> axisNames = axisList.keySet()
        if(axisNames.size() > 2)
        {
            Set<String> otherAxes = axisNames - [rowAxisName, colAxisName]
            if(!addlBindings.keySet().containsAll(otherAxes))
            {
                throw new IllegalArgumentException("Using row axis [${rowAxisName}] and query axis [${colAxisName}] for cube [${this.name}] - bindings for axes ${otherAxes} must be supplied.")
            }

            for(other in otherAxes)
            {
                Axis otherAxis = axisList[other]
                def value = addlBindings[other]
                Column column = otherAxis.findColumn(value as Comparable)
                if(!column)
                {
                    throw new CoordinateNotFoundException("Column [${value}] not found on axis [${other}] on cube [${this.name}]", name, null, other, value)
                }
                boundColumns << column.id
            }
        }
        return boundColumns
    }

    private void setMapByAxisType(boolean isNotDiscrete, Axis axis, Column column, def cellValue, Map queryMap, Map alreadyExecuted = null)
    {
        if(isNotDiscrete)
        {
            String name = column.columnName
            if(!name)
            {
                throw new IllegalStateException("Axis [${axis.name}] on cube [${this.name}] is not a discrete axis. All columns on a non-discrete axis must be named to be used in select.")
            }
            queryMap[name] = cellValue
            if(alreadyExecuted != null)
            {
                alreadyExecuted[name] = cellValue
            }
        }
        else
        {
            def value = column.value
            queryMap[value] = cellValue
            if(alreadyExecuted != null)
            {
                alreadyExecuted[value] = cellValue
            }
        }
    }

    private def determineCommandCell(LongHashSet ids, Map input, String axisName, Column column, Map output)
    {
        def cellValue = cells[ids]
        if(cellValue instanceof CommandCell)
        {
            input[axisName] = column.valueThatMatches
            cellValue = executeExpression([input: input, output: output, ncube: this] as Map, cellValue as CommandCell)
        }
        return cellValue
    }

    private Map buildMapReduceResult(Axis searchAxis, Set columnsToReturn, Map alreadyExecuted, LongHashSet ids, Map commandInput, Map output)
    {
        String axisName = searchAxis.name
        boolean isDiscrete = searchAxis.type == AxisType.DISCRETE

        Map result = [:] as Map
        if(!columnsToReturn)
        {
            List<Column> allColumns = searchAxis.columns
            for(column in allColumns)
            {
                long colId = column.id
                ids << colId
                def cellValue = determineCommandCell(ids, commandInput, axisName, column, output)
                result[isDiscrete ? column.value : column.columnName] = cellValue
                ids.remove(colId)
            }
            return result
        }

        for(columnToReturn in columnsToReturn)
        {
            if(alreadyExecuted.containsKey(columnToReturn))
            {
                result[columnToReturn] = alreadyExecuted[columnToReturn]
                continue
            }
            Column column = isDiscrete ? searchAxis.findColumn(columnToReturn as Comparable) : searchAxis.findColumnByName(columnToReturn as String)
            result[columnToReturn] = isDiscrete ? column.value : column.columnName

            long colId = column.id
            ids << colId
            def cellValue = determineCommandCell(ids, commandInput, axisName, column, output)
            result[columnToReturn] = cellValue
            ids.remove(colId)
        }
        return result
    }
}
