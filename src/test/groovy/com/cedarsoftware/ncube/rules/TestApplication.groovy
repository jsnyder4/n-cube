package com.cedarsoftware.ncube.rules

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.NCubeRuntime
import groovy.transform.CompileStatic
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ImportResource

import static com.cedarsoftware.ncube.NCubeAppContext.getNcubeRuntime

//@SpringBootApplication
//@ImportResource(["classpath:config/ncube-beans.xml"])
@CompileStatic
class TestApplication
{
//    static void main(String[] args)
//    {
//        SpringApplication.run(TestApplication, args)
//    }

    static void createAndCacheNCube(ApplicationID appId = ApplicationID.testAppId, String fileName)
    {
        String json = NCubeRuntime.getResourceAsString(fileName)
        NCube ncube = NCube.fromSimpleJson(json)
        ncube.applicationID = appId
        ncubeRuntime.addCube(ncube)
    }
}
