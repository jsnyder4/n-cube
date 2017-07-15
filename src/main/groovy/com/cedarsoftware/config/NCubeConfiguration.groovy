package com.cedarsoftware.config

import com.cedarsoftware.ncube.util.GCacheManager
import com.cedarsoftware.ncube.util.NCubeRemoval
import com.cedarsoftware.util.HsqlSchemaCreator
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.MethodInvokingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

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

@CompileStatic
@Configuration
class NCubeConfiguration
{
    @Value('${ncube.cache.max.size}') int maxSizeNCubeCache
    @Value('${ncube.cache.evict.type}') String typeNCubeCache
    @Value('${ncube.cache.evict.duration}') int durationNCubeCache
    @Value('${ncube.cache.evict.units}') String unitsNCubeCache
    @Value('${ncube.cache.concurrency}') int concurrencyNCubeCache

    @Value('${perm.cache.max.size}') int maxSizePermCache
    @Value('${perm.cache.evict.type}') String typePermCache
    @Value('${perm.cache.evict.duration}') int durationPermCache
    @Value('${perm.cache.evict.units}') String unitsPermCache
    @Value('${perm.cache.concurrency}') int concurrencyPermCache

    @Value('${ncube.allow.mutable.methods}') boolean allowMutableMethods
    @Value('${target.scheme}') String scheme
    @Value('${target.host}') String host
    @Value('${target.port}') int port
    @Value('${target.context}') String context
    @Value('${target.username}') String username
    @Value('${target.password}') String password
    @Value('${target.numConnections}') int numConnections

    @Value('${ncube.sources.dir:#{null}}') String sourcesDirectory
    @Value('${ncube.classes.dir:#{null}}') String classesDirectory

    @Bean(name = 'ncubeRemoval')
    NCubeRemoval getNCubeRemoval()
    {
        return new NCubeRemoval()
    }

    @Bean(name = "ncubeCacheManager")
    GCacheManager getNcubeCacheManager()
    {
        GCacheManager cacheManager = new GCacheManager(getNCubeRemoval(), maxSizeNCubeCache, typeNCubeCache, durationNCubeCache, unitsNCubeCache, concurrencyNCubeCache)
        return cacheManager
    }

    @Bean(name = 'permCacheManager')
    GCacheManager getPermCacheManager()
    {
        GCacheManager cacheManager = new GCacheManager(getNCubeRemoval(), maxSizePermCache, typePermCache, durationPermCache, unitsPermCache, concurrencyPermCache)
        return cacheManager
    }

    @Bean(name = 'setSourcesDir')
    public MethodInvokingBean setSourcesDir() {
        MethodInvokingBean methodInvokingBean = new MethodInvokingBean();
        methodInvokingBean.setStaticMethod("com.cedarsoftware.ncube.GroovyBase.setGeneratedSourcesDirectory");
        methodInvokingBean.setArguments([sourcesDirectory] as Object []);
        return methodInvokingBean;
    }

    @Bean(name = 'setClassesDir')
    public MethodInvokingBean setClassesDir() {
        MethodInvokingBean methodInvokingBean = new MethodInvokingBean();
        methodInvokingBean.setStaticMethod("com.cedarsoftware.ncube.util.CdnClassLoader.setGeneratedClassesDirectory");
        methodInvokingBean.setArguments([classesDirectory] as Object []);
        return methodInvokingBean;
    }

    @Configuration
    @Profile('test-database')
    class TestDatabase
    {
        @Bean(name = 'hsqlSetup')
        HsqlSchemaCreator getSchemaCreator()
        {
            HsqlSchemaCreator schemaCreator = new HsqlSchemaCreator(
                    'org.hsqldb.jdbcDriver',
                    'jdbc:hsqldb:mem:testdb',
                    'sa',
                    '',
                    '/config/hsqldb-schema.sql')
            return schemaCreator
        }
    }
}
