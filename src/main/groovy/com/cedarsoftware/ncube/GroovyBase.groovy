package com.cedarsoftware.ncube

import com.cedarsoftware.util.ByteUtilities
import com.cedarsoftware.util.EncryptionUtilities
import com.cedarsoftware.util.StringUtilities
import com.cedarsoftware.util.SystemUtilities
import com.cedarsoftware.util.UrlUtilities
import groovy.transform.CompileStatic
import ncube.grv.exp.NCubeGroovyExpression
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.Phases
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.tools.GroovyClass

import java.lang.reflect.InvocationTargetException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.regex.Matcher
import java.util.regex.Pattern

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
    static final String NCUBE_TARGET_JVM_VERSION = 'NCUBE_TARGET_JVM_VERSION'
    static final String NCUBE_CODEGEN_DEBUG = 'NCUBE_TARGET_JVM_VERSION'
    protected transient String L2CacheKey  // in-memory cache of (SHA-1(source) || SHA-1(URL + classpath.urls)) to compiled class
    private volatile transient Class runnableCode = null
    /**
     * This cache is 'per ApplicationID'.  This allows different applications to define the same
     * class (URL to groovy), yet have different source code for that class.
     */
    private static final ConcurrentMap<ApplicationID, ConcurrentMap<String, Class>> L2_CACHE = new ConcurrentHashMap<>()
    private static final TEMP_DIR = System.getProperty("java.io.tmpdir")

    static
    {
        new File("${TEMP_DIR}/src/main/groovy/").mkdirs()
        new File("${TEMP_DIR}/target/classes/").mkdirs()
    }

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

        if (url == null)
        {
            data = cmd
        }

        prepare(data, ctx)
        Object result = executeInternal(ctx)
        if (cacheable)
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
        Map<String, Class> L2Cache = getAppL2Cache(appId)
        L2Cache.clear()
    }

    protected static Map<String, Class> getAppL2Cache(ApplicationID appId)
    {
        ConcurrentMap<String, Class> map = L2_CACHE[appId]

        if (map == null)
        {
            map = new ConcurrentHashMap<>()
            ConcurrentMap mapRef = L2_CACHE.putIfAbsent(appId, map)
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
                throw new IllegalStateException("Code cleared while cell was executing, n-cube: ${ncube.name}, app: ${ncube.applicationID}")
            }
            final NCubeGroovyExpression exp = (NCubeGroovyExpression) code.newInstance()
            exp.input = getInput(ctx)
            exp.output = getOutput(ctx)
            exp.ncube = getNCube(ctx)
            return invokeRunMethod(exp, ctx)
        }
        catch (InvocationTargetException e)
        {
            throw e.targetException
        }
    }

    /**
     * Fetch constructor (from cache, if cached) and instantiate GroovyExpression
     */
    protected abstract Object invokeRunMethod(NCubeGroovyExpression instance, Map<String, Object> ctx) throws Throwable

    /**
     * Conditionally compile the passed in command.  If it is already compiled, this method
     * immediately returns.  Insta-check because it is just a reference check.
     */
    void prepare(Object data, Map<String, Object> ctx)
    {
        // check L1 cache
        if (getRunnableCode() != null)
        {   // If the code for the cell has already been compiled, do nothing.
            return
        }

        computeL2CacheKey(data, ctx)
        Map<String, Class> L2Cache = getAppL2Cache(getNCube(ctx).applicationID)

        // check L2 cache
        if (L2Cache.containsKey(L2CacheKey))
        {   // Already been compiled, re-use class (different cell, but has identical source or URL as other expression).
            setRunnableCode(L2Cache[L2CacheKey])
            return
        }

        // Pre-compiled check (e.g. source code was pre-compiled and instrumented for coverage)
        Map ret = getClassLoaderAndSource(ctx)
        if (ret.gclass instanceof Class)
        {   // Found class matching URL fileName.groovy already in JVM
            setRunnableCode(ret.gclass as Class)
            return
        }

        // check L3 cache
        GroovyClassLoader gcLoader = ret.loader as GroovyClassLoader
        String groovySource = ret.source as String
        String L3CacheKey = sourceAndFlagsToSha1(groovySource)
        byte[] rootClassBytes = getRootClassFromL3("${L3CacheKey}.class")

        if (rootClassBytes != null)
        {
            synchronized (L3CacheKey.intern())
            {
                Class clazz = L2Cache[L2CacheKey]
                if (clazz != null)
                {   // Another thread defined and persisted the class while this thread was blocked...
                    return
                }
                Class root = gcLoader.defineClass(null, rootClassBytes)
                defineInnerClassesFromL3(~/^${L3CacheKey}.+\.class$/, gcLoader)
                setRunnableCode(root)
                L2Cache[L2CacheKey] = root
            }
            return
        }

        // Newly encountered source - compile the source and store it in L1, L2, and L3 caches
        compile(gcLoader, groovySource, L3CacheKey, ctx)
    }

    protected Class compile(GroovyClassLoader gcLoader, String groovySource, String L3CacheKey, Map<String, Object> ctx)
    {
        Map<String, Class> L2Cache = getAppL2Cache(getNCube(ctx).applicationID)

        CompilerConfiguration compilerConfiguration = new CompilerConfiguration()
        compilerConfiguration.targetBytecode = targetByteCodeVersion
        compilerConfiguration.debug = NCubeCodeGenDebug
        compilerConfiguration.defaultScriptExtension = '.groovy'
        compilerConfiguration.optimizationOptions = [(CompilerConfiguration.INVOKEDYNAMIC): Boolean.TRUE]

        SourceUnit sourceUnit = new SourceUnit("ncube.grv.exp.N_${L2CacheKey}", groovySource, compilerConfiguration, gcLoader, null)

        CompilationUnit compilationUnit = new CompilationUnit()
        compilationUnit.addSource(sourceUnit)
        compilationUnit.configure(compilerConfiguration)
        compilationUnit.compile(Phases.CLASS_GENERATION)    // concurrently compile!

        Class generatedClass = defineClasses(L2Cache, compilationUnit.classes, gcLoader, L3CacheKey, groovySource)
        return generatedClass
    }

    protected Class defineClasses(Map<String, Class> L2Cache, List classes, GroovyClassLoader gcLoader, String L3CacheKey, String groovySource)
    {
        Class root = null

        synchronized(L3CacheKey.intern())
        {
            Class clazz = L2Cache[L2CacheKey]
            if (clazz != null)
            {   // Another thread defined and persisted the class while this thread was blocked...
                return clazz
            }

            int numClasses = classes.size()
            for (int i = 0; i < numClasses; i++)
            {
                GroovyClass gclass = classes[i] as GroovyClass
                String className = gclass.name
                def dollarPos = className.indexOf('$')
                boolean isRoot = dollarPos == -1

                // Add compiled class to classLoader
                clazz = gcLoader.defineClass(null, gclass.bytes)

                // Persist class bytes
                if (isRoot)
                {
                    if (NCubeGroovyExpression.isAssignableFrom(clazz))
                    {
                        // cache (L3) main class file
                        cacheClassInL3("${L3CacheKey}.class", gclass.bytes)
                        if (root == null)
                        {
                            root = clazz
                            cacheSourceInL3("${L3CacheKey}.groovy", groovySource)
                        }
                    }
                }
                else
                {   // cache (L3) inner class
                    cacheClassInL3("${L3CacheKey}${className.substring(dollarPos)}.class", gclass.bytes)
                }
            }

            if (root != null)
            {
                setRunnableCode(root)
                L2Cache[L2CacheKey] = root
            }
            return L2Cache[L2CacheKey]
        }
    }

    protected Map getClassLoaderAndSource(Map<String, Object> ctx)
    {
        NCube cube = getNCube(ctx)
        boolean isUrlUsed = StringUtilities.hasContent(url)
        Map output = [:]

        if (cube.name.toLowerCase().startsWith("sys."))
        {   // No URLs allowed, nor code from sys.classpath when executing these cubes
            output.loader = (GroovyClassLoader)NCubeManager.getLocalClassloader(cube.applicationID)
            output.source = cmd
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
                    output.gclass = Class.forName(className)
                }
                catch (Exception ignored)
                { }
            }

            URL groovySourceUrl = getActualUrl(ctx)
            output.loader = (GroovyClassLoader) NCubeManager.getUrlClassLoader(cube.applicationID, getInput(ctx))
            output.source = StringUtilities.createUtf8String(UrlUtilities.getContentFromUrl(groovySourceUrl, true))
        }
        else
        {   // inline code
            output.loader = (GroovyClassLoader)NCubeManager.getUrlClassLoader(cube.applicationID, getInput(ctx))
            output.source = cmd
        }
        output.source = expandNCubeShortCuts(buildGroovy(ctx, output.source as String))
        return output
    }

    /**
     * Compute SHA1 hash for this CommandCell.  The tricky bit here is that the command can be either
     * defined inline or via a URL.  If defined inline, then the command hash is SHA1(command text).  If
     * defined through a URL, then the command hash is SHA1(command URL + GroovyClassLoader URLs.toString).
     * This is done this way so that every time the same URL is encountered, it does not have to be
     * reread.
     */
    private void computeL2CacheKey(Object data, Map<String, Object> ctx)
    {
        String content
        if (url == null)
        {
            content = data != null ? data.toString() : ""
        }
        else
        {   // specified via URL, add classLoader URL strings to URL for SHA-1 source.
            NCube cube = getNCube(ctx)
            GroovyClassLoader gcLoader = (GroovyClassLoader) NCubeManager.getUrlClassLoader(cube.applicationID, getInput(ctx))
            URL[] urls = gcLoader.URLs
            StringBuilder s = new StringBuilder()
            for (URL url : urls)
            {
                s.append(url.toString())
                s.append('.')
            }
            s.append(url)
            content = s.toString()
        }
        L2CacheKey = EncryptionUtilities.calculateSHA1Hash(StringUtilities.getUTF8Bytes(content))
    }

    /**
     * Make SHA-1 key from entire groovy source (whether inline or URL).  If inline, this source includes
     * the 'wrapper' code.  If from a URL, it is the entire source loaded from the URL.  The SHA-1
     * also includes the target JVM version ("1.8") and "t" or "f" (debug or non-debug code generation).
     * The format of the input is [source]-[target JVM version string]-[t | f]
     * @param groovySource String groovy source code
     * @return SHA-1 of source + flags, e.g., "groovySource-1.8-false"
     */
    private String sourceAndFlagsToSha1(String groovySource)
    {
        byte sep = 45   // hyphen
        MessageDigest sha1Digest = EncryptionUtilities.SHA1Digest
        sha1Digest.update(groovySource.bytes)
        sha1Digest.update(sep)
        sha1Digest.update(targetByteCodeVersion.bytes)
        sha1Digest.update(sep)
        sha1Digest.update(NCubeCodeGenDebug ? 't'.bytes : 'f'.bytes)
        return ByteUtilities.encode(sha1Digest.digest())
    }

    private static String getTargetByteCodeVersion()
    {
        return SystemUtilities.getExternalVariable(NCUBE_TARGET_JVM_VERSION) ?: '1.8'
    }

    private static boolean isNCubeCodeGenDebug()
    {
        return 'true'.equalsIgnoreCase(SystemUtilities.getExternalVariable(NCUBE_CODEGEN_DEBUG))
    }

    // --------------------------------------------- L3 Cache APIs -----------------------------------------------------

    private static void cacheClassInL3(String cacheKey, byte[] byteCode)
    {
        new File("${TEMP_DIR}/target/classes/${cacheKey}").bytes = byteCode
    }

    private static void cacheSourceInL3(String cacheKey, String source)
    {
        new File("${TEMP_DIR}/src/main/groovy/${cacheKey}").bytes = StringUtilities.getUTF8Bytes(source)
    }

    private static byte[] getRootClassFromL3(String cacheKey)
    {
        File byteCode = new File("${TEMP_DIR}/target/classes/${cacheKey}")
        return byteCode.exists() ? byteCode.bytes : null
    }

    private static void defineInnerClassesFromL3(Pattern pattern, GroovyClassLoader gcLoader)
    {
        new File("${TEMP_DIR}/target/classes/").eachFileMatch(pattern) { File file ->
            gcLoader.defineClass(null, file.bytes)
        }
    }

    // ------------------------------------------ END L3 Cache APIs ----------------------------------------------------

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
        getCubeNamesFromText(cubeNames, cmd)
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
        Matcher m = Regexes.inputVar.matcher(cmd)
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
