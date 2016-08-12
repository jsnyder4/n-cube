package ncube.grv.exp

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.Axis
import com.cedarsoftware.ncube.Column
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.NCubeInfoDto
import com.cedarsoftware.ncube.NCubeManager
import com.cedarsoftware.ncube.exception.RuleJump
import com.cedarsoftware.ncube.exception.RuleStop
import com.cedarsoftware.util.CaseInsensitiveSet
import com.cedarsoftware.util.IOUtilities
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.UrlUtilities
import groovy.transform.CompileStatic

/**
 * Base class for all GroovyExpression and GroovyMethod's within n-cube CommandCells.
 * @see com.cedarsoftware.ncube.GroovyBase
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
class NCubeGroovyExpression
{
    public Map input
    public Map output
    public NCube ncube

    /**
     * Fetch the named n-cube from the NCubeManager.  It looks at the same
     * account, app, and version as the running n-cube.
     * @param name String n-cube name (optional, defaults to name of currently executing cube).
     * @param quiet boolean (optional, defaults to false).  Set to true if you want null returned
     * when the cube is not found (as opposed to an exception being thrown).
     * @return NCube with the given name.
     */
    NCube getCube(String name = ncube.name, boolean quiet = false)
    {
        if (StringUtilities.equalsIgnoreCase(ncube.name, name))
        {
            return ncube
        }
        NCube cube = NCubeManager.getCube(ncube.applicationID, name)
        if (cube == null && !quiet)
        {
            throw new IllegalArgumentException('n-cube: ' + name + ' not found.')
        }
        return cube
    }

    /**
     * Short-cut to fetch ApplicationID for current cell.
     */
    ApplicationID getApplicationID()
    {
        return ncube.applicationID
    }

    /**
     * Fetch all cube names in the current application.
     * @return Set<String> cube names>
     */
    Set<String> getCubeNames(boolean activeOnly = true)
    {
        List<NCubeInfoDto> infoDtoList = NCubeManager.search(ncube.applicationID, null, null, [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):activeOnly])
        Set<String> names = new CaseInsensitiveSet()
        infoDtoList.each {
            names.add(it.name)
        }
        return names
    }

    /**
     * @deprecated - Use search()
     */
    @Deprecated
    List<NCubeInfoDto> getCubeRecords(String pattern, boolean activeOnly = true)
    {
        Map options = [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):activeOnly]
        return search(pattern, null, options)
    }

    /**
     * Fetch cube records that match the given pattern.
     * @param namePattern String text pattern or exact file name used to filter cube name(s)
     * @param textPattern String text pattern filter cubes returned.  This is matched
     * against the JSON content (contains() search).
     * @param options Map of NCubeManager.SEARCH_* options. Optional.  Defaults to active records only.
     * @return Object[] of NCubeInfoDto instances.
     */
    List<NCubeInfoDto> search(String namePattern, String textPattern, Map options = [(NCubeManager.SEARCH_ACTIVE_RECORDS_ONLY):true])
    {
        return NCubeManager.search(ncube.applicationID, namePattern, textPattern, options)
    }

    /**
     * @deprecated - use at()
     */
    @Deprecated
    def getRelativeCubeCell(String cubeName, Map coord, def defaultValue = null)
    {
        input.putAll(coord)
        return getCube(cubeName).getCell(input, output, defaultValue)
    }

    /**
     * @deprecated - use at()
     */
    @Deprecated
    def runRuleCube(String cubeName, Map coord, def defaultValue = null)
    {
        input.putAll(coord)
        return getCube(cubeName).getCell(input, output, defaultValue)
    }

    /**
     * Using the input Map passed in, fetch the coordinate at that location.
     * @param coord Map containing precise coordinate to use.
     * @param cubeName String n-cube name.  This argument is optional and defaults
     * to the same cube as the cell currently executing.
     * @param defaultValue Object to return when no cell exists at the target coordinate
     * and the cube does not have a cube-level default.  This argument is optional.
     * @return executed cell contents at the given coordinate.
     */
    def go(Map coord, String cubeName = ncube.name, def defaultValue = null)
    {
        return getCube(cubeName).getCell(coord, output, defaultValue)
    }

    /**
     * Using the input Map passed in, fetch the coordinate at that location.
     * @param coord Map containing precise coordinate to use.
     * @param cubeName String n-cube name.  This argument is optional and defaults
     * to the same cube as the cell currently executing.
     * @param defaultValue Object to return when no cell exists at the target coordinate
     * and the cube does not have a cube-level default.  This argument is optional.
     * @param ApplicationID of a different application (reference data application for
     * example) from which the running cube exists.
     * @return executed cell contents at the given coordinate.
     */
    def go(Map coord, String cubeName, def defaultValue, ApplicationID appId)
    {
        NCube target = NCubeManager.getCube(appId, cubeName)
        if (target == null)
        {
            throw new IllegalArgumentException('n-cube: ' + cubeName + ' not found, app: ' + appId)
        }
        return target.getCell(coord, output, defaultValue)
    }

    /**
     * Fetch the cell contents using the current input coordinate and specified n-cube,
     * but first apply any updates from the passed in coordinate.
     * @param coord Map containing 'updates' to the current input coordinate.
     * @param cubeName String n-cube name.  This argument is optional and defaults
     * to the same cube as the cell currently executing.
     * @param defaultValue Object to return when no cell exists at the target coordinate
     * and the cube does not have a cube-level default.  This argument is optional.
     * @return executed cell contents at the current input location and specified n-cube,
     * but first apply updates to the current input coordinate from the passed in coord.
     */
    def at(Map coord, String cubeName = ncube.name, def defaultValue = null)
    {
        input.putAll(coord)
        return getCube(cubeName).getCell(input, output, defaultValue)
    }

    /**
     * Fetch the cell contents using the current input coordinate and specified n-cube,
     * but first apply any updates from the passed in coordinate.
     * @param coord Map containing 'updates' to the current input coordinate.
     * @param cubeName String n-cube name.  This argument is optional and defaults
     * to the same cube as the cell currently executing.
     * @param defaultValue Object to return when no cell exists at the target coordinate
     * and the cube does not have a cube-level default.  This argument is optional.
     * @param ApplicationID of a different application (reference data application for
     * example) from which the running cube exists.
     * @return executed cell contents at the current input location and specified n-cube,
     * but first apply updates to the current input coordinate from the passed in coord.
     */
    def at(Map coord, String cubeName, def defaultValue, ApplicationID appId)
    {
        NCube target = NCubeManager.getCube(appId, cubeName)
        if (target == null)
        {
            throw new IllegalArgumentException('n-cube: ' + cubeName + ' not found, app: ' + appId)
        }
        input.putAll(coord)
        return target.getCell(input, output, defaultValue)
    }

    /**
     * Restart rule execution.  The Map contains the names of rule axes to rule names.  For any rule axis
     * specified in the map, the rule step counter will be moved (jumped) to the named rule.  More than one
     * rule axis step counter can be moved by including multiple entries in the map.
     * @param coord Map of rule axis names, to rule names.  If the map is empty, it is the same as calling
     * jump() with no args.
     */
    void jump(Map coord)
    {
        input.putAll(coord)
        throw new RuleJump(input)
    }

    /**
     * Stop rule execution from going any further.
     */
    void ruleStop()
    {
        throw new RuleStop()
    }

    /**
     * Fetch the Column instance at the location along the axis specified by value.
     * @param axisName String axis name.
     * @param value Comparable value to bind to the axis.
     * @return Column instance at the specified location (value) along the specified axis (axisName).
     */
    Column getColumn(String axisName, Comparable value)
    {
        Axis axis = getAxis(axisName)
        return axis.findColumn(value)
    }

    /**
     * Fetch the Column instance at the location along the axis specified by value within
     * the named n-cube.
     * @param cubeName String n-cube name.
     * @param axisName String axis name.
     * @param value Comparable value to bind to the axis.
     * @return Column instance at the specified location (value) within the specified cube (cubeName)
     * along the specified axis (axisName).
     */
    Column getColumn(String cubeName, String axisName, Comparable value)
    {
        Axis axis = getAxis(cubeName, axisName)
        return axis.findColumn(value)
    }

    /**
     * Fetch the Axis within the current n-cube with the specified name.
     * @param axisName String axis name.
     * @return Axis instance from the current n-cube that has the specified (axisName) name.
     */
    Axis getAxis(String axisName)
    {
        Axis axis = (Axis) ncube[axisName]
        if (axis == null)
        {
            throw new IllegalArgumentException("Axis '" + axisName + "' does not exist on n-cube: " + ncube.getName())
        }

        return axis
    }

    /**
     * Fetch the Axis within the passed in n-cube with the specified name.
     * @param cubeName String n-cube name.
     * @param axisName String axis name.
     * @return Axis instance from the specified n-cube (cubeName) that has the
     * specified (axisName) name.
     */
    Axis getAxis(String cubeName, String axisName)
    {
        Axis axis = (Axis) getCube(cubeName)[axisName]
        if (axis == null)
        {
            throw new IllegalArgumentException("Axis '" + axisName + "' does not exist on n-cube: " + cubeName)
        }

        return axis
    }

    /**
     * Fetch content from the passed in URL.  The URL can be relative or absolute.  If it is
     * relative, then the sys.classpath cube in the same ApplicationID space will be used
     * to anchor it.
     * @param url String URL
     * @return String content fetched from the passed in URL.
     */
    String url(String url)
    {
        byte[] bytes = urlToBytes(url)
        if (bytes == null)
        {
            return null
        }
        return StringUtilities.createUtf8String(bytes)
    }

    /**
     * Fetch content from the passed in URL.  The URL can be relative or absolute.  If it is
     * relative, then the sys.classpath cube in the same ApplicationID space will be used
     * to anchor it.
     * @param url String URL
     * @return byte[] content fetched from the passed in URL.
     */
    byte[] urlToBytes(String url)
    {
        URL actualUrl = NCubeManager.getActualUrl(getApplicationID(), url, input)
        return UrlUtilities.getContentFromUrl(actualUrl, true)
    }

    /**
     * @return long Current time in nano seconds (used to compute how long something takes to execute)
     */
    long now()
    {
        return System.nanoTime()
    }

    /**
     * Get floating point millisecond value for how much time elapsed.
     * @param begin long value from call to now()
     * @param end long value from call to now()
     * @return double elapsed time in milliseconds.
     */
    double elapsedMillis(long begin, long end)
    {
        return (double) (end - begin) / 1000000.0d
    }

    def run()
    {
        throw new IllegalStateException('run() should never be called on ' + getClass().getName() + '. This can occur for a cell marked GroovyExpression which should be set to GroovyMethod.')
    }
}