package com.cedarsoftware.ncube

import com.cedarsoftware.servlet.JsonCommandServlet
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.ImportResource
import org.springframework.web.filter.GenericFilterBean
import org.springframework.web.filter.HiddenHttpMethodFilter
import org.springframework.web.filter.HttpPutFormContentFilter
import org.springframework.web.filter.RequestContextFilter
import org.slf4j.LoggerFactory
import org.slf4j.Logger

/**
 * This class defines allowable actions against persisted n-cubes
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License");
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

@Configuration
@ImportResource("classpath:config/ncube-beans.xml")
@SpringBootApplication()
class NCubeApplication
{
    private static final Logger LOG = LoggerFactory.getLogger(NCubeApplication.class)

    static void main(String[] args)
    {
        try
        {
            SpringApplication.run(NCubeApplication.class, args)
        }
        catch (Throwable t)
        {
            LOG.error('Exception occurred', t)
        }
        finally
        {
            LOG.info('NCUBE server started.')
        }
    }

    @Bean
    ServletRegistrationBean servletRegistrationBean()
    {
        ServletRegistrationBean bean = new ServletRegistrationBean(new JsonCommandServlet(), "/cmd/*")
        return bean
    }

    @Bean
    FilterRegistrationBean filterRegistrationBean1()
    {
        GenericFilterBean filter = new HiddenHttpMethodFilter()
        FilterRegistrationBean registration = new FilterRegistrationBean(filter)
        registration.enabled = false
        return registration
    }

    @Bean
    FilterRegistrationBean filterRegistrationBean2()
    {
        GenericFilterBean filter = new HttpPutFormContentFilter()
        FilterRegistrationBean registration = new FilterRegistrationBean(filter)
        registration.enabled = false
        return registration
    }

    @Bean
    FilterRegistrationBean filterRegistrationBean3()
    {
        GenericFilterBean filter = new RequestContextFilter()
        FilterRegistrationBean registration = new FilterRegistrationBean(filter)
        registration.enabled = false
        return registration
    }
}
