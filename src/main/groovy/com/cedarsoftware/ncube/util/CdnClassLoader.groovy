package com.cedarsoftware.ncube.util

import groovy.transform.CompileStatic

import java.util.concurrent.ConcurrentHashMap

/**
 *  @author Ken Partlow (kpartlow@gmail.com)
 *  @author John DeRegnaucourt (jdereg@gmail.com)
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
    private final ClassLoader parentClassLoader = super.getParent()
    private final Map<String, URL> resourceCache = new ConcurrentHashMap<>()
    private final Map<String, Enumeration<URL>> resourcesCache = new ConcurrentHashMap<>()
    private final Map<String, Class> classCache = new ConcurrentHashMap<>()
    private final URL nullUrl = new URL('http://null.com:8080')

    /**
     * creates a GroovyClassLoader using the given ClassLoader as parent
     */
    CdnClassLoader(ClassLoader loader, boolean preventRemoteBeanInfo, boolean preventRemoteCustomizer)
    {
        super(loader, null)
        _preventRemoteBeanInfo = preventRemoteBeanInfo
        _preventRemoteCustomizer = preventRemoteCustomizer
    }

    CdnClassLoader(List<String> list)
    {
        this(CdnClassLoader.class.getClassLoader(), true, true)
        addURLs(list)
    }

    protected Class<?> findClass(final String name) throws ClassNotFoundException
    {
        if (classCache.containsKey(name))
        {
            Class clazz = classCache[name]
            if (Class.class.is(clazz))
            {
//                println '=====> findClass: [cached ClassNotFoundException] ' + name
                throw new ClassNotFoundException('Class not found in classpath, name: ' + name)
            }
//            println '=====> findClass: [cacheHit] ' + name
            return clazz
        }

        // NOTE: This list needs to match (weed out) imports automatically brought in by Groovy as well as
        // those GroovyExpression adds to the source file.
        if (name.startsWith('ncube.grv.') ||
            name.startsWith('ncube.grv$') ||
            name.startsWith('ncube$grv$') ||
            name.startsWith('java.') ||
            name.startsWith('javax.') ||
            name.startsWith('groovy.') ||
            name.startsWith('com.google.common.') ||
            name.startsWith('com.cedarsoftware$') ||
            name.startsWith('com.cedarsoftware.'))
        {
            if (!name.startsWith('ncube.grv.closure'))
            {   // local only
                return classCache[name] = parentClassLoader.loadClass(name)
            }
        }

        if (_preventRemoteBeanInfo && name.endsWith('BeanInfo'))
        {   // local only
            return classCache[name] = parentClassLoader.loadClass(name)
        }

        if (_preventRemoteCustomizer && name.endsWith('Customizer'))
        {   // local only
            return classCache[name] = parentClassLoader.loadClass(name)
        }

        try
        {
            Class clazz = super.findClass(name)
//            println '=====> findClass: ' + name + ', class cache size: ' + classCache.size()
            return classCache[name] = clazz
        }
        catch (ClassNotFoundException e)
        {
//            println '=====> findClass: [classNotFoundException] + ' + name
            classCache[name] = Class.class
            throw e
        }
    }

    private void addURLs(List<String> list)
    {
        for (url in list)
        {
            addURL(url)
        }
    }

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
     * @param name Name of resource
     * @return true if we should only look locally.
     */
    protected boolean isLocalOnlyResource(String name)
    {
        if ('META-INF/services/org.codehaus.groovy.transform.ASTTransformation' == name || name.endsWith(".class"))
        {
            return true
        }

        // NOTE: This list needs to match (weed out) imports automatically brought in by Groovy as well as
        // those GroovyExpression adds to the source file.  Must be in 'path' form (using slashes)
        if (name.startsWith('ncube/grv/') ||
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

    Enumeration<URL> findResources(String name) throws IOException
    {
        if (resourcesCache.containsKey(name))
        {
//            println '-----> findResources: [cache hit] ' + name
            return resourcesCache[name]
        }
        if (isLocalOnlyResource(name))
        {
            Enumeration<URL> nullEnum = new Enumeration() {
                public boolean hasMoreElements() { return false }
                public URL nextElement() { throw new NoSuchElementException() }
            }
            resourcesCache[name] = nullEnum
            return nullEnum
        }
//        println '-----> findResources: ' + name
        Enumeration<URL> res = super.findResources(name)
        return resourcesCache[name] = res
    }

    URL findResource(String name)
    {
        if (resourceCache.containsKey(name))
        {
            URL url = resourceCache[name]
//            println '-----> findResource: [cache hit] ' + name
            return nullUrl.is(url) ? null : url
        }

        if (isLocalOnlyResource(name))
        {
            resourceCache.put(name, nullUrl)
            return null
        }

        URL res = super.findResource(name)
        resourceCache[name] = res ?: nullUrl
//        println '-----> findResource: ' + name
        return res
    }

    void clearCache()
    {
        resourceCache.clear()
        resourcesCache.clear()
        classCache.clear()
        super.clearCache()
    }
}
