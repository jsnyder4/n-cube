package com.cedarsoftware.ncube.util

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.NCubeAppContext
import com.cedarsoftware.ncube.NCubeClient
import com.cedarsoftware.ncube.Regexes
import com.cedarsoftware.util.StringUtilities
import groovy.transform.CompileStatic
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.regex.Matcher

/**
 * Accept inbound requests for static content, route via n-cube.
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
class CdnRouter
{
    private static NCubeClient ncubeClient
    private static CdnRoutingProvider provider
    private static final Logger LOG = LoggerFactory.getLogger(CdnRouter.class)
    public static final String TENANT = 'router.tenant'
    public static final String APP = 'router.app'
    public static final String CUBE_VERSION = 'router.version'
    public static final String STATUS = 'router.status'
    public static final String BRANCH = 'router.branch'
    public static final String CUBE_NAME = 'router.cubeName'
    public static final String CONTENT_TYPE = 'content.type'
    public static final String CONTENT_NAME = 'content.name'
    public static final String HTTP_REQUEST = 'http.request'
    public static final String HTTP_RESPONSE = 'http.response'

    CdnRouter()
    {
        ncubeClient = NCubeAppContext.ncubeClient
    }

    static void setCdnRoutingProvider(CdnRoutingProvider p)
    {
        provider = p
    }

    /**
     * Route the given request based on configured routing within n-cube
     */
    void route(HttpServletRequest request, HttpServletResponse response)
    {
        try
        {
            if (provider == null)
            {
                String msg = "CdnRouter - CdnRoutingProvider has not been set into the CdnRouter."
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg)
                return
            }

            // Peel off mime type and logical file name
            final String servletPath = request.getServletPath()
            String[] info = getPathComponents(servletPath)
            if (info == null)
            {
                String msg = "CdnRouter - Invalid ServletPath request: " + servletPath
                sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, msg)
                return
            }
            String type = info[0]
            String logicalName = info[1]

            // Check for authorization
            if (!provider.isAuthorized(type))
            {
                String msg = "CdnRouter - Unauthorized access, request: " + request.getRequestURL()
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, msg)
                return
            }

            // Allow provider to establish base coordinate
            Map coord = [:]
            provider.setupCoordinate(coord)

            String tenant = (String) coord[TENANT]
            String app = (String) coord[APP]
            String version = (String) coord[CUBE_VERSION]
            String status = (String) coord[STATUS]
            String branch = (String) coord[BRANCH]
            String cubeName = (String) coord[CUBE_NAME]
            String msg = null

            if (StringUtilities.isEmpty(tenant))
            {
                msg = TENANT
            }
            else if (StringUtilities.isEmpty(app))
            {
                msg = APP
            }
            else if (StringUtilities.isEmpty(version))
            {
                msg = CUBE_VERSION
            }
            else if (StringUtilities.isEmpty(status))
            {
                msg = STATUS
            }
            else if (StringUtilities.isEmpty(branch))
            {
                msg = BRANCH
            }
            else if (StringUtilities.isEmpty(cubeName))
            {
                msg = CUBE_NAME
            }

            if (msg != null)
            {
                msg = "CdnRouter - CdnRoutingProvider did not set up '" + msg + "' in the Map coordinate."
                sendErrorResponse(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, msg)
                return
            }

            coord[CONTENT_NAME] = logicalName
            coord[CONTENT_TYPE] = type
            coord[HTTP_REQUEST] = request
            coord[HTTP_RESPONSE] = response

            ApplicationID appId = new ApplicationID(tenant, app, version, status, branch)
            NCube routingCube = ncubeClient.getCube(appId, cubeName)
            if (routingCube == null)
            {
                throw new IllegalStateException("Could not load routing cube using app: " + appId + ", cube name: " + cubeName)
            }
            routingCube.getCell(coord)
        }
        catch (Exception e)
        {
            LOG.error("CdnRouter exception occurred", e)
            // Required, so that error message is not double logged.
            try
            {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "CdnRouter - Error occurred: " + e.message)
            }
            catch (Exception ignore)
            { }
        }
    }

    /**
     * Send an HTTP error response
     */
    private static void sendErrorResponse(HttpServletResponse response, int error, String msg)
    {
        try
        {
            LOG.error(msg)
            response.sendError(error, msg)
        }
        catch (Exception ignore)
        { }
    }

    private static String[] getPathComponents(String pathInfo)
    {
        if (pathInfo == null)
        {
            return null
        }

        Matcher matcher = Regexes.cdnUrlPattern.matcher(pathInfo)
        if (!matcher.find())
        {
            return null
        }

        String[] info = new String[2]
        info[0] = matcher.group(1)
        info[1] = matcher.group(2)
        return info
    }
}