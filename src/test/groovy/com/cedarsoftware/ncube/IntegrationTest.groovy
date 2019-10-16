package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.SpringApplication
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.stereotype.Component
import org.springframework.test.context.junit4.SpringRunner

import javax.annotation.PostConstruct

@RunWith(SpringRunner.class)
@Component
@CompileStatic
@Ignore
class IntegrationTest
{
//    @PostConstruct
    void postConstruct() throws Exception
    {
        Properties properties = new Properties()
        properties.put('server.port', '9001')
        properties.put('server.servlet.context-path', '/ncube')

        new SpringApplicationBuilder(NCubeApplication).
                profiles('runtime-server').
                properties(properties).run()

//        SpringApplication storageServer = new SpringApplication(NCubeApplication)
//        storageServer.setAdditionalProfiles('storage-server', 'test-database')
//        properties = new Properties();
//        properties.put("server.port", '9002');
//        properties.put("server.servlet.context-path", '/ncube');
//        storageServer.setDefaultProperties(properties);
//        storageServer.run()

//        SpringApplicationBuilder runtimeServer = new SpringApplicationBuilder(NCubeApplication)
//            .profiles('runtime-server')
//            .properties('server.port=9001', 'servlet.contextPath=/scoobie')
//
//        environment.properties.put('server.port', '9001')
//        environment.properties.put('server.conextPath', '/ncube')
//        runtimeServer.run()
//
//        SpringApplication storageServer = new SpringApplication(NCubeApplication)
//        SpringApplicationBuilder ncubeServer = new SpringApplicationBuilder(NCubeApplication.class)
//            .profiles('storage-server', 'test-database')
//            .properties('server.port=9002')
//        ncubeServer.run()
    }
}