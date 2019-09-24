import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.Axis
import com.cedarsoftware.ncube.Column
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.exception.RuleJump
import com.cedarsoftware.ncube.exception.RuleStop
import com.cedarsoftware.util.CaseInsensitiveMap
import com.cedarsoftware.util.IOUtilities
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.TrackingMap

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime

NCube getCube(String name = ncube.name, boolean quiet = false)
{
    if (StringUtilities.equalsIgnoreCase(ncube.name, name))
    {
        return ncube
    }
    NCube cube = ncubeRuntime.getCube(ncube.applicationID, name)
    if (cube == null && !quiet)
    {
        throw new IllegalArgumentException("n-cube: ${name} not found.")
    }
    return cube
}

Axis getAxis(String axisName, String cubeName = ncube.name)
{
    Axis axis = getCube(cubeName).getAxis(axisName)
    if (axis == null)
    {
        throw new IllegalArgumentException("Axis: ${axisName}, does not exist on n-cube: ${cubeName}, app: ${ncube.applicationID}")
    }
    return axis
}

Column getColumn(Comparable value, String axisName, String cubeName = ncube.name)
{
    return getAxis(axisName, cubeName).findColumn(value)
}

def at(Map coord, String cubeName = ncube.name, def defaultValue = null)
{
    Map copy = inputWithoutTrackingMap
    copy = dupe(copy)
    copy.putAll(coord)
    return getCube(cubeName).getCell(copy, output, defaultValue)
}

def at(Map coord, NCube cube, def defaultValue = null)
{
    Map copy = inputWithoutTrackingMap
    copy = dupe(copy)
    copy.putAll(coord)
    return cube.getCell(copy, output, defaultValue)
}

def at(Map coord, String cubeName, def defaultValue, ApplicationID appId)
{
    NCube target = ncubeRuntime.getCube(appId, cubeName)
    if (target == null)
    {
        throw new IllegalArgumentException("n-cube: ${cubeName} not found, app: ${appId}")
    }
    Map copy = inputWithoutTrackingMap
    copy = dupe(copy)
    copy.putAll(coord)
    return target.getCell(copy, output, defaultValue)
}

def go(Map coord, String cubeName = ncube.name, def defaultValue = null)
{
    if (coord.is(input))
    {
        coord = dupe(inputWithoutTrackingMap)
    }
    return getCube(cubeName).getCell(coord, output, defaultValue)
}

def go(Map coord, NCube cube, def defaultValue = null)
{
    if (coord.is(input))
    {
        coord = dupe(inputWithoutTrackingMap)
    }
    return cube.getCell(coord, output, defaultValue)
}

def go(Map coord, String cubeName, def defaultValue, ApplicationID appId)
{
    NCube target = ncubeRuntime.getCube(appId, cubeName)
    if (target == null)
    {
        throw new IllegalArgumentException("n-cube: ${cubeName} not found, app: ${appId}")
    }
    if (coord.is(input))
    {
        coord = dupe(inputWithoutTrackingMap)
    }
    return target.getCell(coord, output, defaultValue)
}

def use(Map altInput, String cubeName = ncube.name, def defaultValue = null)
{
    Map copy = inputWithoutTrackingMap
    Map origInput = dupe(copy)
    Map modInput = dupe(copy)
    modInput.putAll(altInput)
    return getCube(cubeName).use(modInput, origInput, output, defaultValue)
}

def use(Map altInput, String cubeName, def defaultValue, ApplicationID appId)
{
    NCube target = ncubeRuntime.getCube(appId, cubeName)
    if (target == null)
    {
        throw new IllegalArgumentException("n-cube: ${cubeName} not found, app: ${appId}")
    }

    Map copy = inputWithoutTrackingMap
    Map origInput = dupe(copy)
    Map modInput = dupe(copy)
    modInput.putAll(altInput)
    return getCube(cubeName).use(modInput, origInput, output, defaultValue)
}

Map getInputWithoutTrackingMap()
{
    Map copy = input
    while (copy instanceof TrackingMap)
    {
        copy = ((TrackingMap)input).getWrappedMap()
    }
    return copy
}

Map dupe(Map map)
{
    return new CaseInsensitiveMap(map)
}

Map mapReduce(String colAxisName, Closure where = { true }, Map options = [:], String cubeName = null, ApplicationID appId = null)
{
    NCube target
    if (cubeName)
    {
        appId = appId ?: applicationID
        target = ncubeRuntime.getCube(appId, cubeName)
    }
    else
    {
        target = ncube
    }
    options.input = input
    options.output = output
    return target.mapReduce(colAxisName, where, options)
}

String url(String url)
{
    byte[] bytes = urlToBytes(url)
    if (bytes == null)
    {
        return null
    }
    return StringUtilities.createUtf8String(bytes)
}

byte[] urlToBytes(String url)
{
    InputStream inStream = getClass().getResourceAsStream(url)
    byte[] bytes = IOUtilities.inputStreamToBytes(inStream)
    IOUtilities.close(inStream as Closeable)
    return bytes
}

def ruleStop()
{
    throw new RuleStop()
}

def jump(Map coord = [:])
{
    input.putAll(coord);
    throw new RuleJump(input)
}

static long now()
{
    return System.nanoTime()
}

static double elapsedMillis(long begin, long end)
{
    return (double) (end - begin) / 1000000.0d
}
