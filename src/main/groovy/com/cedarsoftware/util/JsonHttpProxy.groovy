package com.cedarsoftware.util

import com.cedarsoftware.servlet.JsonCommandServlet
import com.cedarsoftware.util.io.JsonReader
import com.cedarsoftware.util.io.JsonWriter
import com.cedarsoftware.util.io.MetaUtils
import groovy.transform.CompileStatic
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.auth.AuthScope
import org.apache.http.auth.UsernamePasswordCredentials
import org.apache.http.client.AuthCache
import org.apache.http.client.CredentialsProvider
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.protocol.HttpClientContext
import org.apache.http.conn.routing.HttpRoute
import org.apache.http.entity.ContentType
import org.apache.http.entity.StringEntity
import org.apache.http.impl.auth.BasicScheme
import org.apache.http.impl.client.*
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.servlet.http.HttpServletRequest

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
class JsonHttpProxy implements CallableBean
{
    private final CloseableHttpClient httpClient
    private CredentialsProvider credsProvider
    private AuthCache authCache
    private final HttpHost httpHost
    private final String context
    private final String username
    private final String password
    private final int numConnections
    private static final Logger LOG = LoggerFactory.getLogger(JsonHttpProxy.class)

    JsonHttpProxy(String scheme, String hostname, int port, String context, String username = null, String password = null, int numConnections = 6)
    {
        httpHost = new HttpHost(hostname, port, scheme)
        this.context = context
        this.username = username
        this.password = password
        this.numConnections = numConnections
        httpClient = createClient()

        if (username && password)
        {
            credsProvider = new BasicCredentialsProvider()
            AuthScope authScope = new AuthScope(httpHost.hostName, httpHost.port)
            UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(username, password)
            credsProvider.setCredentials(authScope, credentials)
            authCache = new BasicAuthCache()
            authCache.put(httpHost, new BasicScheme())
        }
    }

    /**
     * Creates the client object with the proxy and cookie store for later use.
     *
     * @return A {@link CloseableHttpClient} with the GAIG proxy
     */
    protected CloseableHttpClient createClient()
    {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager()
        cm.maxTotal = numConnections // Max total connection
        cm.defaultMaxPerRoute = numConnections // Default max connection per route
        cm.setMaxPerRoute(new HttpRoute(httpHost), numConnections) // Max connections per route

        HttpClientBuilder builder = HttpClientBuilder.create()
        builder.connectionManager = cm
        builder.defaultCookieStore = new BasicCookieStore()
        CloseableHttpClient httpClient = builder.build()
        return httpClient
    }

    Object call(String bean, String methodName, List args)
    {
        String jArgs = JsonWriter.objectToJson(args.toArray())
        String jsonArgs = URLEncoder.encode(jArgs, 'UTF-8')

        if (LOG.debugEnabled)
        {
            LOG.debug("${bean}.${MetaUtils.getLogMessage(methodName, args.toArray())}")
        }
        long start = System.nanoTime()

        HttpClientContext clientContext = HttpClientContext.create()
        HttpPost request = new HttpPost("${httpHost.toURI()}/${context}/cmd/${bean}/${methodName}")
        if (username && password)
        {
            clientContext.credentialsProvider = credsProvider
            clientContext.authCache = authCache
        }
        else
        {
            addHeaders(request)
        }

        String poser = System.getProperty('ncube.fakeuser')
        if (StringUtilities.hasContent(poser))
        {
            request.setHeader('fakeuser', poser)
        }
        request.entity = new StringEntity(jsonArgs, ContentType.APPLICATION_JSON)
        HttpResponse response = httpClient.execute(request, clientContext)
        String json = EntityUtils.toString(response.entity)
        EntityUtils.consume(response.entity)

        long stop = System.nanoTime()
        if (LOG.debugEnabled)
        {
            LOG.debug("    ${Math.round((stop - start) / 1000000.0d)}ms - ${json}")
        }

        Map envelope = JsonReader.jsonToJava(json) as Map
        if (envelope.exception != null)
        {
            throw envelope.exception
        }
        if (envelope.status == false)
        {
            String msg
            if (envelope.data instanceof String)
            {
                msg = envelope.data
            }
            else if (envelope.data != null)
            {
                msg = envelope.data.toString()
            }
            else
            {
                msg = 'no extra info provided.'
            }
            throw new RuntimeException("REST call [${bean}.${methodName}] indicated failure on server: ${msg}")
        }
        return envelope.data
    }

    private void addHeaders(HttpPost proxyRequest)
    {
        HttpServletRequest servletRequest = JsonCommandServlet.servletRequest.get()
        if (servletRequest instanceof HttpServletRequest)
        {
            Enumeration<String> e = servletRequest.headerNames
            while (e.hasMoreElements())
            {
                String headerName = e.nextElement()
                String headerValue = servletRequest.getHeader(headerName)
                proxyRequest.setHeader(headerName, headerValue)
            }
        }
    }
}