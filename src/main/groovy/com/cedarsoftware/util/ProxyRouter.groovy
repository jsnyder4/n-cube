package com.cedarsoftware.util

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.Axis
import com.cedarsoftware.ncube.AxisType
import com.cedarsoftware.ncube.AxisValueType
import com.cedarsoftware.ncube.CommandCell
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.StringUrlCmd
import com.cedarsoftware.ncube.util.CdnRouter
import com.cedarsoftware.util.io.JsonReader
import groovy.transform.CompileStatic
import org.slf4j.LoggerFactory
import org.slf4j.Logger

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Spring Transaction Based JDBC Connection Provider
 *
 * @author Raja Gade
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License");
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
class ProxyRouter
{
    static final String SYS_CLASSPATH_PREFIX = 'sys.classpath.prefix'

    private static final Logger LOG = LoggerFactory.getLogger(ProxyRouter.class)

    /**
     * Route the given request based on configured routing within n-cube
     */
    void route(HttpServletRequest request, HttpServletResponse response)
    {   // use n-cube
        Map<String, String[]> requestParams = request.parameterMap
        if (!requestParams.containsKey('appId'))
        {
            try
            {
                String msg = '"appId" parameter missing - it is required and should contain the ApplicationID fields app, verison, status, branch in JSON format.'
                LOG.error(msg)
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, msg);
            }
            catch (Exception ignore)
            { }
        }

        try
        {
            ApplicationID appId = buildAppId(requestParams)
            NCube finder = new NCube('router')
            finder.applicationID = appId
            finder.addAxis(new Axis('dontcare', AxisType.DISCRETE, AxisValueType.STRING, true, Axis.DISPLAY, 1))
            String sysPathPrefix = request.getAttribute(SYS_CLASSPATH_PREFIX)
            String path = "/${sysPathPrefix}/"
            String actualPath = request.servletPath - path     // Groovy String subtraction

            CommandCell cmd = new StringUrlCmd(actualPath, false)
            finder.setCell(cmd, [:])
            Map input = [(CdnRouter.HTTP_REQUEST): request, (CdnRouter.HTTP_RESPONSE): response]
            finder.getCell(input)
        }
        catch (Exception e)
        {
            String msg = "'appId' parameter not parsing as valid JSON: ${requestParams.appId}"
            try
            {
                LOG.error(msg, e)
                response.sendError(HttpServletResponse.SC_NOT_FOUND, msg);
            }
            catch (Exception ignore)
            { }
        }
    }

    private ApplicationID buildAppId(Map<String, String[]> requestParams)
    {
        Map appParam = (Map) JsonReader.jsonToJava(requestParams.appId[0], [(JsonReader.USE_MAPS):true] as Map)
        new ApplicationID(ApplicationID.DEFAULT_TENANT, (String) appParam.app, (String) appParam.version, (String) appParam.status, (String) appParam.branch)
    }
}
