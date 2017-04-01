package com.cedarsoftware.util

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * @author John DeRegnaucourt (jdereg@gmail.com), Josh Snyder (joshsnyder@gmail.com)
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
class ReflectiveProxy implements CallableBean, ApplicationContextAware
{
    private static ApplicationContext ctx
    private static final Map<String, Method> METHOD_MAP = new ConcurrentHashMap<>()

    Object call(String beanName, String methodName, List args)
    {
        Object bean = ctx.getBean(beanName)
        Method method = getMethod(bean, beanName, methodName, args.size())
        try
        {
            return method.invoke(bean, args.toArray())
        }
        catch (InvocationTargetException e)
        {
            throw e.targetException
        }
    }

    void setApplicationContext(ApplicationContext applicationContext)
    {
        ctx = applicationContext
    }

    /**
     * Fetch the named method from the controller. First a local cache will be checked, and if not
     * found, the method will be found reflectively on the controller.  If the method is found, then
     * it will be checked for a ControllerMethod annotation, which can indicate that it is NOT allowed
     * to be called.  This permits a public controller method to be blocked from remote access.
     * @param bean Object on which the named method will be found.
     * @param beanName String name of the controller (Spring name, n-cube name, etc.)
     * @param methodName String name of method to be located on the controller.
     * @param argCount int number of arguments.  This is used as part of the cache key to allow for
     * duplicate method names as long as the argument list length is different.
     */
    private static Method getMethod(Object bean, String beanName, String methodName, int argCount)
    {
        String methodKey = "${beanName}.${methodName}.${argCount}"
        Method method = METHOD_MAP[methodKey]
        if (method == null)
        {
            method = getMethod(bean.class, methodName, argCount)
            if (method == null)
            {
                throw new IllegalArgumentException("Cannot call method: ${methodName} on bean: ${beanName}")
            }
            METHOD_MAP[methodKey] = method
        }
        return method
    }

    /**
     * Reflectively find the requested method on the requested class.
     * @param c Class containing the method
     * @param methodName String method name
     * @param argc int number of arguments
     * @return Method instance located on the passed in class.
     */
    private static Method getMethod(Class c, String methodName, int argc)
    {
        Method[] methods = c.methods
        for (Method method : methods)
        {
            if (methodName == method.name && method.parameterTypes.length == argc)
            {
                return method
            }
        }
        return null
    }
}