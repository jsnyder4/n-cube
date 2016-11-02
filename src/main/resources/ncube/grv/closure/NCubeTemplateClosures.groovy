import com.cedarsoftware.ncube.*
import com.cedarsoftware.ncube.exception.*
import com.cedarsoftware.ncube.formatters.*
import com.cedarsoftware.ncube.proximity.*
import com.cedarsoftware.ncube.util.*
import com.cedarsoftware.util.*
import com.cedarsoftware.util.io.*
import com.google.common.base.*
import com.google.common.collect.*
import com.google.common.net.*


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

Axis getAxis(String axisName, String cubeName = ncube.name)
{
    Axis axis = getCube(cubeName).getAxis(axisName)
    if (axis == null)
    {
        throw new IllegalArgumentException('Axis: ' + axisName + ', does not exist on n-cube: ' + cubeName + ', app: ' + ncube.applicationID)
    }
    return axis
}

Column getColumn(Comparable value, String axisName, String cubeName = ncube.name)
{
    return getAxis(axisName, cubeName).findColumn(value)
}

def at(Map coord, String cubeName = ncube.name, def defaultValue = null)
{
    input.putAll(coord)
    return getCube(cubeName).getCell(input, output, defaultValue)
}

def at(Map coord, NCube cube, def defaultValue = null)
{
    input.putAll(coord)
    return cube.getCell(input, output, defaultValue)
}

def at(Map coord, String cubeName, def defaultValue, ApplicationID appId)
{
    NCube target = NCubeManager.getCube(appId, cubeName)
    input.putAll(coord)
    return target.getCell(input, output, defaultValue)
}

def go(Map coord, String cubeName = ncube.name, def defaultValue = null)
{
    return getCube(cubeName).getCell(coord, output, defaultValue)
}

def go(Map coord, NCube cube, def defaultValue = null)
{
    return cube.getCell(coord, output, defaultValue)
}

def go(Map coord, String cubeName, def defaultValue, ApplicationID appId)
{
    NCube target = NCubeManager.getCube(appId, cubeName)
    return target.getCell(coord, output, defaultValue)
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

def jump(Map coord)
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
