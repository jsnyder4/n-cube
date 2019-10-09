package com.cedarsoftware.ncube

import groovy.transform.CompileStatic
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.boot.SpringApplication
import org.springframework.test.context.junit4.SpringRunner

@RunWith(SpringRunner.class)
@CompileStatic
@Ignore
class IntegrationTest
{
    @Test
    void startServers() throws Exception
    {
        SpringApplication runtimeServer = new SpringApplication(NCubeApplication)
        runtimeServer.setAdditionalProfiles('runtime-server')
        Properties properties = new Properties();
        properties.put("server.port", '9001');
        runtimeServer.setDefaultProperties(properties);
        runtimeServer.run()

        SpringApplication storageServer = new SpringApplication(NCubeApplication)
        storageServer.setAdditionalProfiles('storage-server', 'test-database')
        properties = new Properties();
        properties.put("server.port", '9002');
        storageServer.setDefaultProperties(properties);
        storageServer.run()

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