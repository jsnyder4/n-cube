package com.cedarsoftware.config

import com.cedarsoftware.ncube.GroovyBase
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.UrlCommandCell
import com.cedarsoftware.ncube.util.CdnClassLoader
import com.cedarsoftware.ncube.util.GCacheManager
import com.cedarsoftware.util.HsqlSchemaCreator
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.config.MethodInvokingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer

import javax.annotation.PostConstruct

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
    @Value('${ncube.cache.max.size:0}') int maxSizeNCubeCache
    @Value('${ncube.cache.evict.type:expireAfterAccess}') String typeNCubeCache
    @Value('${ncube.cache.evict.duration:4}') int durationNCubeCache
    @Value('${ncube.cache.evict.units:hours}') String unitsNCubeCache
    @Value('${ncube.cache.concurrency:16}') int concurrencyNCubeCache

    @Value('${ncube.perm.cache.max.size:100000}') int maxSizePermCache
    @Value('${ncube.perm.cache.evict.type:expireAfterAccess}') String typePermCache
    @Value('${ncube.perm.cache.evict.duration:3}') int durationPermCache
    @Value('${ncube.perm.cache.evict.units:minutes}') String unitsPermCache
    @Value('${ncube.perm.cache.concurrency:16}') int concurrencyPermCache

    @Value('${ncube.allow.mutable.methods:false}') boolean allowMutableMethods
    @Value('${ncube.accepted.domains:}') String ncubeAcceptedDomains
    @Value('${ncube.target.scheme:http}') String scheme
    @Value('${ncube.target.host:localhost}') String host
    @Value('${ncube.target.port:9000}') int port
    @Value('${ncube.target.context:ncube}') String context
    @Value('${ncube.target.username:#{null}}') String username
    @Value('${ncube.target.password:#{null}}') String password
    @Value('${ncube.target.numConnections:100}') int numConnections

    @Value('${ncube.sources.dir:#{null}}') String sourcesDirectory
    @Value('${ncube.classes.dir:#{null}}') String classesDirectory

    @Value('${ncube.stackEntry.coordinate.value.max:1000}') int stackEntryCoordinateValueMaxSize

    @Bean
    static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer()
    {
        return new PropertySourcesPlaceholderConfigurer()
    }

    @Bean(name = 'ncubeRemoval')
    Closure getNcubeRemoval()
    {
        return { Object value ->
            if (value instanceof NCube)
            {
                NCube ncube = value as NCube
                for (Object cellValue : ncube.cellMap.values())
                {
                    if (cellValue instanceof UrlCommandCell)
                    {
                        UrlCommandCell cell = cellValue as UrlCommandCell
                        cell.clearClassLoaderCache(ncube.applicationID)
                    }
                }
            }
            return true
        }
    }

    @Bean(name = "ncubeCacheManager")
    GCacheManager getNcubeCacheManager()
    {
        GCacheManager cacheManager = new GCacheManager(ncubeRemoval, maxSizeNCubeCache, typeNCubeCache, durationNCubeCache, unitsNCubeCache, concurrencyNCubeCache)
        return cacheManager
    }

    @Bean(name = 'permCacheManager')
    GCacheManager getPermCacheManager()
    {
        GCacheManager cacheManager = new GCacheManager(null, maxSizePermCache, typePermCache, durationPermCache, unitsPermCache, concurrencyPermCache)
        return cacheManager
    }

    @PostConstruct
    void init()
    {
        CdnClassLoader.generatedClassesDirectory = classesDirectory
        GroovyBase.generatedSourcesDirectory = sourcesDirectory
        NCube.stackEntryCoordinateValueMaxSize = stackEntryCoordinateValueMaxSize
        CdnClassLoader.ncubeAcceptedDomains = ncubeAcceptedDomains
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
