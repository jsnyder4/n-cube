package com.cedarsoftware.ncube.util

import groovy.transform.CompileStatic
import org.codehaus.groovy.GroovyBugError
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.control.ClassNodeResolver
import org.codehaus.groovy.control.CompilationFailedException
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.SourceUnit
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.util.concurrent.ConcurrentHashMap

/**
 *  @author Ken Partlow (kpartlow@gmail.com)
 *          Greg Morefield (morefigs@hotmail.com)
 *          John DeRegnaucourt (jdereg@gmail.com)
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
class CdnClassLoader extends GroovyClassLoader
{
    private final boolean _preventRemoteBeanInfo
    private final boolean _preventRemoteCustomizer
    private final Map<String, URL> resourceCache = new ConcurrentHashMap<>()
    private final Map<String, List<URL>> resourcesCache = new ConcurrentHashMap<>()
    private final URL nullUrl = new URL('http://null.com:8080')
    private final List<String> whiteList
    private ClassNodeResolver classNodeResolver = new PreferClassNodeResolver()

    private static String generatedClassesDir = ''
    private static String ncubeAcceptedDomains
    private static final Logger LOG = LoggerFactory.getLogger(CdnClassLoader.class)

    /**
     * Create a GroovyClassLoader using the given ClassLoader as parent
     */
    CdnClassLoader(ClassLoader loader, boolean preventRemoteBeanInfo = true, boolean preventRemoteCustomizer = true, List<String> acceptedDomains = null)
    {
        super(configureParentClassLoader(loader), null)
        _preventRemoteBeanInfo = preventRemoteBeanInfo
        _preventRemoteCustomizer = preventRemoteCustomizer

        if (acceptedDomains == null)
        {
            if (ncubeAcceptedDomains)
            {
                whiteList = Arrays.asList(ncubeAcceptedDomains.split(";"))
            }
            else
            {
                whiteList = null
            }
        }
        else
        {
            whiteList = acceptedDomains
        }
    }

    /**
     * Injects URLClassLoader as parent to pickup generated classes directory, if configured
     */
    private static ClassLoader configureParentClassLoader(ClassLoader parent) {
        String classesDir = generatedClassesDirectory
        if (classesDir)
        {
            File classesFile = new File(classesDir)
            return new URLClassLoader([classesFile.toURI().toURL()] as URL [], parent)
        }
        return parent
    }

    /**
     * Create a class loader that will have the additional URLs added to the classpath.
     * @param urlList List of String URLs to be added to the classpath.
     * @param acceptedDomains List of String prefixes of white-list domains which are
     * allowed to be searched for dynamic code or resources.
     */
    CdnClassLoader(List<String> urlList, List<String> acceptedDomains = null)
    {
        this(CdnClassLoader.class.classLoader, true, true, acceptedDomains)
        addURLs(urlList)
    }

    /**
     * Caches the class, if name is supplied and caching is configured,
     * then delegates to super class to defineClass from raw bytes
     *
     * @param name String name of class to define, or null, if unknown
     * @param byteCode byte [] of raw Class bytes
     * @return generated Class definition
     */
    Class defineClass(String name, byte[] byteCode) {
        if (name && generatedClassesDir)
        {
            dumpGeneratedClass(name,byteCode)
        }

        return super.defineClass(name, byteCode)
    }

    /**
     * Writes the generated Groovy class to the directory identified by the NCUBE_PARAM:genClsDir
     * @param name String of fully qualified name of the class
     * @param byteCode byte [] of Class to write
     */
    private void dumpGeneratedClass(String name, byte [] byteCode) {
        File classFile = null
        try
        {
            classFile = new File("${generatedClassesDir}/${name.replace('.',File.separator)}.class")
            if (ensureDirectoryExists(classFile.parentFile))
            {
                classFile.bytes = byteCode
            }
        }
        catch (Exception e)
        {
            LOG.warn("Failed to write class file with path=${classFile?.path}",e)
        }
    }

    /**
     * Finds and loads the class with the specified name from the URL search
     * path. Any URLs referring to JAR files are loaded and opened as needed
     * until the class is found.
     *
     * @param name the name of the class
     * @return the resulting class
     * @exception ClassNotFoundException if the class could not be found,
     *            or if the loader is closed.
     * @exception NullPointerException if {@code name} is {@code null}.
     */
    protected Class<?> findClass(final String name) throws ClassNotFoundException
    {
//        println "findClass(${name})"
        if (_preventRemoteBeanInfo && name.endsWith('BeanInfo'))
        {
            throw new ClassNotFoundException(name)
        }

        if (_preventRemoteCustomizer && name.endsWith('Customizer'))
        {
            throw new ClassNotFoundException(name)
        }

        if (whiteList)
        {
            for (item in whiteList)
            {
                if (name.startsWith(item))
                {
                    Class clazz = super.findClass(name)
                    return clazz
                }
            }
        }
        throw new ClassNotFoundException(name)
    }

    private void addURLs(List<String> list)
    {
        for (url in list)
        {
            addURL(url)
        }
    }

    /**
     * Add the passed in String URL to the classpath.
     * @param url String url to add to the classpath.
     */
    void addURL(String url)
    {
        if (url)
        {
            if (!url.endsWith("/"))
            {
                url += '/'
            }
            addURL(new URL(url))
        }
    }

    /**
     * Prevent dynamic code from certain packages, like java, javax, groovy, com/cedarsoftware, com/google.
     * @param name Name of resource
     * @return true if we should only look locally.
     */
    protected boolean isLocalOnlyResource(String name)
    {
//        println "isLocalOnlyResource(${name})"
        if (!whiteList)
        {   // If there is no whiteList, then we can skip the HTTP HEAD check for ASTTransformation
            if (name.endsWith('.class'))
            {
                return true
            }

            if (name == 'META-INF/services/org.codehaus.groovy.transform.ASTTransformation')
            {
                return true
            }
        }

        // NOTE: This list needs to match (weed out) imports automatically brought in by Groovy as well as
        // those GroovyExpression adds to the source file.  Must be in 'path' form (using slashes)
        if (name.contains('ncube/grv/') ||
                name.startsWith('java/') ||
                name.startsWith('javax/') ||
                name.startsWith('groovy/') ||
                name.startsWith('com/google/common/') ||
                name.startsWith('com/cedarsoftware/'))
        {
            if (name.startsWith('ncube/grv/closure/'))
            {
                return false
            }
            return true
        }

        if (_preventRemoteBeanInfo && name.endsWith('BeanInfo.groovy'))
        {
            return true
        }

        if (_preventRemoteCustomizer && name.endsWith('Customizer.groovy'))
        {
            return true
        }

        return false
    }

    /**
     * Finds all the resources with the given name. A resource is some data
     * (images, audio, text, code, etc) that can be accessed by class code in a
     * way that is independent of the location of the code.
     *
     * <p>The name of a resource is a <tt>/</tt>-separated path name that
     * identifies the resource.
     *
     * <p> The search order is described in the documentation for
     * #getResource(String).</p>
     *
     * @apiNote When overriding this method it is recommended that an
     * implementation ensures that any delegation is consistent with the
     * getResource(String) method. This should ensure that the first element
     * returned by the Enumeration's {@code nextElement} method is the same
     * resource that the {@code getResource(String)} method would return.
     *
     * @param  name
     *         The resource name
     *
     * @return  An enumeration of {@link java.net.URL <tt>URL</tt>} objects for
     *          the resource.  If no resources could  be found, the enumeration
     *          will be empty.  Resources that the class loader doesn't have
     *          access to will not be in the enumeration.
     *
     * @throws  IOException
     *          If I/O errors occur
     *
     * @see  #findResources(String)
     */
    Enumeration<URL> getResources(String name) throws IOException
    {
//        println "getResources(${name})"
        if (!resourcesCache.containsKey(name))
        {
            if (isLocalOnlyResource(name))
            {
                resourcesCache[name] = []
            }
            else
            {
                resourcesCache[name] = super.getResources(name).toList()
            }
        }
        return Collections.enumeration(resourcesCache[name])
    }

    /**
     * Finds the resource with the given name.  A resource is some data
     * (images, audio, text, etc) that can be accessed by class code in a way
     * that is independent of the location of the code.
     *
     * <p> The name of a resource is a '<tt>/</tt>'-separated path name that
     * identifies the resource.
     *
     * This implementation caches local resource paths to URLs so that multiple
     * requests for the same relative resource will be answered without any
     * network traffic.
     *
     * @param  name
     *         The resource name
     *
     * @return  A <tt>URL</tt> object for reading the resource, or
     *          <tt>null</tt> if the resource could not be found or the invoker
     *          doesn't have adequate  privileges to get the resource.
     */
    URL getResource(String name)
    {
//        println "getResource(${name})"
        if (resourceCache.containsKey(name))
        {
            URL url = resourceCache[name]
            return nullUrl.is(url) ? null : url
        }

        if (isLocalOnlyResource(name))
        {
            resourceCache.put(name, nullUrl)
            return null
        }

        URL res = super.getResource(name)
        resourceCache[name] = res ?: nullUrl
        return res
    }

    /**
     * Returns an Enumeration of URLs representing all of the resources
     * on the URL search path having the specified name.
     *
     * @param name the resource name
     * @exception IOException if an I/O exception occurs
     * @return an {@code Enumeration} of {@code URL}s
     *         If the loader is closed, the Enumeration will be empty.
     */
    Enumeration<URL> findResources(String name) throws IOException
    {
//        println "findResources(${name})"
        if (!resourcesCache.containsKey(name))
        {
            if (isLocalOnlyResource(name))
            {
                resourcesCache[name] = []
            }
            else
            {
                resourcesCache[name] = super.findResources(name).toList()
            }
        }
        return Collections.enumeration(resourcesCache[name])
    }

    /**
     * Finds the resource with the specified name on the URL search path.
     *
     * @param name the name of the resource
     * @return a {@code URL} for the resource, or {@code null}
     * if the resource could not be found, or if the loader is closed.
     */
    URL findResource(String name)
    {
//        println "findResource(${name})"
        if (resourceCache.containsKey(name))
        {
            URL url = resourceCache[name]
            return nullUrl.is(url) ? null : url
        }

        if (isLocalOnlyResource(name))
        {
            resourceCache.put(name, nullUrl)
            return null
        }

        URL res = super.findResource(name)
        resourceCache[name] = res ?: nullUrl
        return res
    }

    /**
     * Clear any internal caches.  The resource caches which map relative paths to
     * fully qualified URLs are cleared, as well as the parent class loader is told
     * to clear its internal class cache.
     */
    void clearCache()
    {
        resourceCache.clear()
        resourcesCache.clear()
        super.clearCache()
    }

    /**
     * Specifies directory to use for caching Class files generated during Groovy compiles.
     * If the directory doesn't exist and can't be created, the Class caching will be disabled
     * @param classesDir String containing relative or absolute path to use for Class caching. A null or empty string will disable caching
     */
    static void setGeneratedClassesDirectory(String classesDir)
    {
        try
        {
            if (classesDir)
            {
                generatedClassesDir = ensureDirectoryExists(new File(classesDir)) ? classesDir : ''
            }
            else
            {
                generatedClassesDir = ''
            }

            if (generatedClassesDir)
            {
                LOG.info("Generated classes configured to use path=${generatedClassesDir}")
            }
        }
        catch (Exception e)
        {
            LOG.warn("Unable to set classes directory to ${classesDir}", e)
            generatedClassesDir = ''
        }
    }

    /**
     * Returns directory to use for Class caching.
     * @return String path to directory to use for Class caching, if enabled; otherwise, empty string
     */
    static String getGeneratedClassesDirectory()
    {
        return generatedClassesDir
    }

    static String setNcubeAcceptedDomains(String acceptedDomains)
    {
        ncubeAcceptedDomains = acceptedDomains
    }

    static String getNcubeAcceptedDomains()
    {
        return ncubeAcceptedDomains
    }

    /**
     * Tries to validate the directory specified.
     *
     * @param dir File containing directory path to validate/create
     * @return true if directory exists or can be created; otherwise, false
     * @throws SecurityException from mkdirs invocation
     */
    private static boolean ensureDirectoryExists(File dir)
    {
        if (!dir.exists())
        {
            dir.mkdirs()
        }
        boolean valid = dir.directory
        if (!valid)
        {
            LOG.warn("Failed to locate or create generated classes directory with path=${dir.path}")
        }
        return valid
    }

    ClassNodeResolver getClassNodeResolver()
    {
        return classNodeResolver
    }

    static class PreferClassNodeResolver extends ClassNodeResolver
    {
        // Map to store cached classes
        private Map<String,ClassNode> cachedClasses = new ConcurrentHashMap<>();

        /**
         * Method adapted from ClassNodeResolver.tryAsLoaderClassOrScript, only looking up source if class doesn't exist
         * @param name
         * @param compilationUnit
         * @return
         */
        @Override
        ClassNodeResolver.LookupResult findClassNode(String name, CompilationUnit compilationUnit) {
            GroovyClassLoader loader = compilationUnit.getClassLoader();
            Class cls;
            try {
                // NOTE: it's important to do no lookup against script files
                // here since the GroovyClassLoader would create a new CompilationUnit
                cls = loader.loadClass(name, false, true);
            } catch (ClassNotFoundException cnfe) {
                return tryAsScript(name, compilationUnit)
            } catch (CompilationFailedException cfe) {
                throw new GroovyBugError("The lookup for "+name+" caused a failed compilaton. There should not have been any compilation from this call.", cfe);
            }

            ClassNode cn = ClassHelper.make(cls);
            return new ClassNodeResolver.LookupResult(null,cn);
        }

        /**
         * Method adapted from ClassNodeResolver.tryAsScript, only doing script lookup
         */
        private static ClassNodeResolver.LookupResult tryAsScript(String name, CompilationUnit compilationUnit) {
            ClassNodeResolver.LookupResult lr = null;

            if (name.startsWith("java.")) return lr;
            //TODO: don't ignore inner static classes completely
            if (name.indexOf('$') != -1) return lr;

            // try to find a script from classpath*/
            GroovyClassLoader gcl = compilationUnit.getClassLoader();
            URL url = null;
            try {
                url = gcl.getResourceLoader().loadGroovySource(name);
            } catch (MalformedURLException e) {
                // fall through and let the URL be null
            }
            if (url != null) {
                SourceUnit su = compilationUnit.addSource(url);
                return new ClassNodeResolver.LookupResult(su,null);
            }
            return lr;
        }

        /**
         * caches a ClassNode (taken from ClassNodeResolver.cacheClass)
         * @param name - the name of the class
         * @param res - the ClassNode for that name
         */
        void cacheClass(String name, ClassNode res) {
            cachedClasses.put(name, res);
        }

        /**
         * returns whatever is stored in the class cache for the given name (taken from ClassNodeResolver.getFromClassCache)
         * @param name - the name of the class
         * @return the result of the lookup, which may be null
         */
        ClassNode getFromClassCache(String name) {
            // We use here the class cache cachedClasses to prevent
            // calls to ClassLoader#loadClass. Disabling this cache will
            // cause a major performance hit.
            ClassNode cached = cachedClasses.get(name);
            return cached;
        }
    }
}
