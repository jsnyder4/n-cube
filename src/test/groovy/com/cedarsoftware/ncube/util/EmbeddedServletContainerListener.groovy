package com.cedarsoftware.ncube.util

import com.cedarsoftware.ncube.NCubeBaseTest
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.context.WebServerInitializedEvent
import org.springframework.context.ApplicationListener
import org.springframework.stereotype.Component

import java.util.regex.Pattern

/**
 * @author Greg Morefield (morefigs@hotmail.com)
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
@Slf4j
@Component
class EmbeddedServletContainerListener implements ApplicationListener<WebServerInitializedEvent>
{
    private static Pattern leadingSlash = ~/^[\/]?/
    private static Pattern trailingSlash = ~/[\/]?$/

    @Value('${server.servlet.context-path}')
    private String contextPath

    // allow for testing against a remote URL instead of embedded Tomcat (assuming static files available)
    @Value('${ncube.tests.baseRemoteUrl:}')
    private String baseRemoteUrl

    void onApplicationEvent(WebServerInitializedEvent event) {
        if (baseRemoteUrl)
        {
            NCubeBaseTest.baseRemoteUrl = baseRemoteUrl - leadingSlash
        }
        else
        {
            String host = 'localhost'
            int port = event.source.port
            String context = contextPath - leadingSlash - trailingSlash
            NCubeBaseTest.baseRemoteUrl = "http://${host}:${port}/${context}"
        }

        log.info("baseRemoteUrl set to: ${NCubeBaseTest.baseRemoteUrl}")
    }
}
