package com.cedarsoftware.ncube

import com.cedarsoftware.util.EncryptionUtilities
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.UrlUtilities
import groovy.transform.CompileStatic
import ncube.grv.exp.NCubeGroovyExpression
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.regex.Matcher
/**
 * Base class for Groovy CommandCells.
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
abstract class GroovyBase extends UrlCommandCell
{
    private static final Logger LOG = LogManager.getLogger(GroovyBase.class)
    protected transient String cmdHash
    private volatile transient Class runnableCode = null
    private static final ConcurrentMap<ApplicationID, ConcurrentMap<String, Class>>  compiledClasses = new ConcurrentHashMap<>()

    //  Private constructor only for serialization.
    protected GroovyBase() {}

    GroovyBase(String cmd, String url, boolean cache)
    {
        super(cmd, url, cache)
    }

    /**
     * @return Class the compiled Class the code within this cell was compiled to (it could have been found
     * and re-used if the code is the same as another cell).  If the cell is marked cache=true, then this
     * method will return null. This is because the cell retains the cached value of the executed code and
     * therefore the class reference is no longer needed.
     */
    protected Class getRunnableCode()
    {
        return runnableCode
    }

    protected void setRunnableCode(Class runnableCode)
    {
        this.runnableCode = runnableCode
    }

    protected Object fetchResult(Map<String, Object> ctx)
    {
        Object data = null

        if (getUrl() == null)
        {
            data = getCmd()
        }

        prepare(data, ctx)
        Object result = executeInternal(ctx)
        if (isCacheable())
        {   // Remove the compiled class from the cell's cache.
            // This is because the cell is marked as cacheable meaning the result of the
            // execution is cached, so there is no need to hold a reference to the compiled class.
            setRunnableCode(null)
        }
        return result
    }

    protected abstract String buildGroovy(Map<String, Object> ctx, String theirGroovy)

    protected static void clearCache(ApplicationID appId)
    {
        Map<String, Class> compiledMap = getCache(appId, compiledClasses)
        compiledMap.clear()
    }

    protected static <T> Map<String, T> getCache(ApplicationID appId, ConcurrentMap<ApplicationID, ConcurrentMap<String, T>> container)
    {
        ConcurrentMap<String, T> map = container[appId]

        if (map == null)
        {
            map = new ConcurrentHashMap<>()
            ConcurrentMap mapRef = container.putIfAbsent(appId, map)
            if (mapRef != null)
            {
                map = mapRef
            }
        }
        return map
    }

    protected Object executeInternal(Map<String, Object> ctx)
    {
        try
        {
            Class code = getRunnableCode()
            if (code == null)
            {
                NCube ncube = getNCube(ctx)
                throw new IllegalStateException('Code cleared while getCell() was executing, n-cube: ' + ncube.name + ", app: " + ncube.applicationID)
            }
            final NCubeGroovyExpression exp = (NCubeGroovyExpression) code.newInstance()
            exp.input = getInput(ctx)
            exp.output = getOutput(ctx)
            exp.ncube = getNCube(ctx)
            return invokeRunMethod(exp, ctx)
        }
        catch (InvocationTargetException e)
        {
            throw e.getTargetException()
        }
    }

    /**
     * Fetch constructor (from cache, if cached) and instantiate GroovyExpression
     */
    protected abstract Object invokeRunMethod(NCubeGroovyExpression instance, Map<String, Object> ctx) throws Throwable

    /**
     * Conditionally compile the passed in command.  If it is already compiled, this method
     * immediately returns.  Insta-check because it is just a ref == null check.
     */
    void prepare(Object data, Map<String, Object> ctx)
    {
        if (getRunnableCode() != null)
        {   // If the code for the cell has already been compiled, return the compiled class.
            return
        }

        computeCmdHash(data, ctx)
        NCube cube = getNCube(ctx)
        Map<String, Class> compiledMap = getCache(cube.applicationID, compiledClasses)

        if (compiledMap.containsKey(cmdHash))
        {   // Already been compiled, re-use class (different cell, but has identical source or URL as other expression).
            setRunnableCode(compiledMap[cmdHash])
            return
        }

        Class compiledGroovy = compile(ctx)
        setRunnableCode(compiledGroovy)
    }

    protected Class compile(Map<String, Object> ctx)
    {
        NCube cube = getNCube(ctx)
        String url = getUrl()
        boolean isUrlUsed = StringUtilities.hasContent(url)
        String grvSrcCode
        GroovyClassLoader gcLoader

        if (cube.getName().toLowerCase().startsWith("sys."))
        {   // No URLs allowed, nor code from sys.classpath when executing these cubes
            gcLoader = (GroovyClassLoader)NCubeManager.getLocalClassloader(cube.applicationID)
            grvSrcCode = getCmd()
        }
        else if (isUrlUsed)
        {
            if (url.endsWith('.groovy'))
            {
                // If a class exists already with the same name as the groovy file (substituting slashes for dots),
                // then attempt to find and return that class without going through the resource location and parsing
                // code. This can be useful, for example, if a build process pre-builds and load coverage enhanced
                // versions of the classes.
                try
                {
                    String className = url - '.groovy'
                    className = className.replace('/', '.')
                    return Class.forName(className)
                }
                catch (Exception ignored)
                { }
            }

            URL groovySourceUrl = getActualUrl(ctx)
            gcLoader = (GroovyClassLoader) NCubeManager.getUrlClassLoader(cube.applicationID, getInput(ctx))
            grvSrcCode = StringUtilities.createString(UrlUtilities.getContentFromUrl(groovySourceUrl, true), "UTF-8")
        }
        else
        {
            gcLoader = (GroovyClassLoader)NCubeManager.getUrlClassLoader(cube.applicationID, getInput(ctx))
            grvSrcCode = getCmd()
        }

        String groovySource = expandNCubeShortCuts(buildGroovy(ctx, grvSrcCode))
        Map<String, Class> compiledMap = getCache(cube.applicationID, compiledClasses)

        synchronized (GroovyBase.class)
        {
            if (compiledMap.containsKey(cmdHash))
            {   // Already been compiled, re-use class (different cell, but has identical source or URL as other expression).
                return compiledMap[cmdHash]
            }

            Class clazz = gcLoader.parseClass(groovySource, 'N_' + cmdHash + '.groovy')
            compiledMap[cmdHash] = clazz
            return clazz
        }
    }

    /**
     * Compute SHA1 hash for this CommandCell.  The tricky bit here is that the command can be either
     * defined inline or via a URL.  If defined inline, then the command hash is SHA1(command text).  If
     * defined through a URL, then the command hash is SHA1(command URL + GroovyClassLoader URLs.toString).
     */
    private void computeCmdHash(Object data, Map<String, Object> ctx)
    {
        String content
        if (getUrl() == null)
        {
            content = data != null ? data.toString() : ""
        }
        else
        {   // specified via URL, add classLoader URL strings to URL for SHA-1 source.
            NCube cube = getNCube(ctx)
            GroovyClassLoader gcLoader = (GroovyClassLoader) NCubeManager.getUrlClassLoader(cube.applicationID, getInput(ctx))
            URL[] urls = gcLoader.getURLs()
            StringBuilder s = new StringBuilder()
            for (URL url : urls)
            {
                s.append(url.toString())
                s.append('.')
            }
            s.append(getUrl())
            content = s.toString()
        }
        cmdHash = EncryptionUtilities.calculateSHA1Hash(StringUtilities.getBytes(content, "UTF-8"))
    }

    protected static String expandNCubeShortCuts(String groovy)
    {
        Matcher m = Regexes.groovyAbsRefCubeCellPattern.matcher(groovy)
        String exp = m.replaceAll('$1go($3, \'$2\')')

        m = Regexes.groovyAbsRefCubeCellPatternA.matcher(exp)
        exp = m.replaceAll('$1go($3, \'$2\')')

        m = Regexes.groovyAbsRefCellPattern.matcher(exp)
        exp = m.replaceAll('$1go($2)')

        m = Regexes.groovyAbsRefCellPatternA.matcher(exp)
        exp = m.replaceAll('$1go($2)')

        m = Regexes.groovyRelRefCubeCellPattern.matcher(exp)
        exp = m.replaceAll('$1at($3,\'$2\')')

        m = Regexes.groovyRelRefCubeCellPatternA.matcher(exp)
        exp = m.replaceAll('$1at($3, \'$2\')')

        m = Regexes.groovyRelRefCellPattern.matcher(exp)
        exp = m.replaceAll('$1at($2)')

        m = Regexes.groovyRelRefCellPatternA.matcher(exp)
        exp = m.replaceAll('$1at($2)')
        return exp
    }

    void getCubeNamesFromCommandText(final Set<String> cubeNames)
    {
        getCubeNamesFromText(cubeNames, getCmd())
    }

    protected static void getCubeNamesFromText(final Set<String> cubeNames, final String text)
    {
        if (StringUtilities.isEmpty(text))
        {
            return
        }

        Matcher m = Regexes.groovyAbsRefCubeCellPattern.matcher(text)
        while (m.find())
        {
            cubeNames.add(m.group(2))  // based on Regex pattern - if pattern changes, this could change
        }

        m = Regexes.groovyAbsRefCubeCellPatternA.matcher(text)
        while (m.find())
        {
            cubeNames.add(m.group(2))  // based on Regex pattern - if pattern changes, this could change
        }

        m = Regexes.groovyRelRefCubeCellPattern.matcher(text)
        while (m.find())
        {
            cubeNames.add(m.group(2))  // based on Regex pattern - if pattern changes, this could change
        }

        m = Regexes.groovyRelRefCubeCellPatternA.matcher(text)
        while (m.find())
        {
            cubeNames.add(m.group(2))  // based on Regex pattern - if pattern changes, this could change
        }

        m = Regexes.groovyExplicitCubeRefPattern.matcher(text)
        while (m.find())
        {
            cubeNames.add(m.group(2))  // based on Regex pattern - if pattern changes, this could change
        }

        m = Regexes.groovyExplicitRunRulePattern.matcher(text)
        while (m.find())
        {
            cubeNames.add(m.group(2))  // based on Regex pattern - if pattern changes, this could change
        }

        m = Regexes.groovyExplicitJumpPattern.matcher(text)
        while (m.find())
        {
            cubeNames.add(m.group(2))  // based on Regex pattern - if pattern changes, this could change
        }

        m = Regexes.groovyExplicitAtPattern.matcher(text)
        while (m.find())
        {
            cubeNames.add(m.group(2))  // based on Regex pattern - if pattern changes, this could change
        }

        m = Regexes.groovyExplicitGoPattern.matcher(text)
        while (m.find())
        {
            cubeNames.add(m.group(2))  // based on Regex pattern - if pattern changes, this could change
        }
    }

    /**
     * Find all occurrences of 'input.variableName' in the Groovy code
     * and add the variableName as a scope (key).
     * @param scopeKeys Set to add required scope keys to.
     */
    void getScopeKeys(Set<String> scopeKeys)
    {
        Matcher m = Regexes.inputVar.matcher(getCmd())
        while (m.find())
        {
            scopeKeys.add(m.group(2))
        }
    }

    static Set<String> extractImportsAndAnnotations(String text, StringBuilder newGroovy)
    {
        // imports
        Matcher m1 = Regexes.importPattern.matcher(text)
        Set<String> extractedLines = new LinkedHashSet<>()
        while (m1.find())
        {
            extractedLines.add(m1.group(0))  // based on Regex pattern - if pattern changes, this could change
        }
        m1.reset()
        String sourceWithoutImports = m1.replaceAll('')

        // @Grapes
        Matcher m2 = Regexes.grapePattern.matcher(sourceWithoutImports)
        while (m2.find())
        {
            extractedLines.add(m2.group(0))  // based on Regex pattern - if pattern changes, this could change
        }

        m2.reset()
        String sourceWithoutGrape = m2.replaceAll('')

        // @Grab, @GrabResolver, @GrabExclude, @GrabConfig
        Matcher m3 = Regexes.grabPattern.matcher(sourceWithoutGrape)
        while (m3.find())
        {
            extractedLines.add(m3.group(0))  // based on Regex pattern - if pattern changes, this could change
        }

        m3.reset()
        String sourceWithoutGrab = m3.replaceAll('')

        newGroovy.append(sourceWithoutGrab)
        return extractedLines
    }
}
