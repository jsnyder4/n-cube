package ncube.grv.method

import com.cedarsoftware.ncube.Advice
import com.cedarsoftware.ncube.ApplicationID
import groovy.transform.CompileStatic
import ncube.grv.exp.NCubeGroovyExpression
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

/**
 * Base class for all GroovyExpression and GroovyMethod's within n-cube CommandCells.
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
 * @see com.cedarsoftware.ncube.GroovyBase
 */
@CompileStatic
class NCubeGroovyController extends NCubeGroovyExpression
{
    protected static final Logger LOG = LoggerFactory.getLogger(NCubeGroovyController.class)

    // Cache reflective method look ups
    private static final ConcurrentMap<ApplicationID, ConcurrentMap<String, Method>> methodCache = new ConcurrentHashMap<>()

    static void clearCache(ApplicationID appId)
    {
        Map<String, Method> methodMap = getMethodCache(appId)
        methodMap.clear()
    }

    /**
     * Fetch the Map of n-cubes for the given ApplicationID.  If no
     * cache yet exists, a new empty cache is added.
     */
    private static Map<String, Method> getMethodCache(ApplicationID appId)
    {
        ConcurrentMap<String, Method> methodMap = methodCache[appId]

        if (methodMap == null)
        {
            methodMap = new ConcurrentHashMap<>()
            ConcurrentMap ref = methodCache.putIfAbsent(appId, methodMap)
            if (ref != null)
            {
                methodMap = ref
            }
        }
        return methodMap
    }

    /**
     * Run the groovy method named by the column on the 'method' axis.
     *
     * @param signature String SHA1 of the source file.  This is used to
     *                  ensure the method cache 'key' is unique.  If someone uses the same
     *                  package and class name for two classes, but their source is different,
     *                  their methods will be keyed uniquely in the cache.
     */
    Object run(String signature) throws Throwable
    {
        final String methodName = (String) input.method
        final String methodKey = "${methodName}.${signature}"
        final Map<String, Method> methodMap = getMethodCache(ncube.applicationID)
        Method method = methodMap[methodKey]

        if (method == null)
        {
            method = getClass().getMethod(methodName)
            methodMap[methodKey] = method
        }

        // If 'around' Advice has been added to n-cube, invoke it before calling Groovy method or expression
        final List<Advice> advices = ncube.getAdvices(methodName)
        if (!advices.empty)
        {
            for (Advice advice : advices)
            {
                if (!advice.before(method, ncube, input, output))
                {
                    return null
                }
            }
        }

        // Invoke the Groovy method named in the input Map at the key 'method'.
        Throwable t = null
        Object ret = null

        try
        {
            ret = method.invoke(this)
        }
        catch (ThreadDeath e)
        {
            throw e
        }
        catch (InvocationTargetException e)
        {
            t = e.targetException
        }
        catch (Throwable e)
        {
            t = e  // Save exception thrown by method call
        }

        // If 'around' Advice has been added to n-cube, invoke it after calling Groovy method
        // or expression
        final int len = advices.size()
        if (len > 0)
        {
            for (int i = len - 1; i >= 0; i--)
            {
                final Advice advice = advices[i]
                try
                {
                    advice.after(method, ncube, input, output, ret, t)  // pass exception (t) to advice (or null)
                }
                catch (ThreadDeath e)
                {
                    throw e
                }
                catch (Throwable e)
                {
                    LOG.error("An exception occurred calling 'after' advice: ${advice.name} on method: ${method.name}", e)
                }
            }
        }
        if (t == null)
        {
            return ret
        }
        throw t
    }
}