package com.cedarsoftware.ncube

import com.cedarsoftware.ncube.util.CdnClassLoader
import com.cedarsoftware.util.StringUtilities
import org.codehaus.groovy.runtime.StackTraceUtils
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.test.context.TestContextManager

import java.util.concurrent.ConcurrentLinkedQueue

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertNotNull

/**
 * @author Greg Morefield (morefigs@hotmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the 'License')
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an 'AS IS' BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */

@RunWith(Parameterized.class)
class TestThreading extends NCubeCleanupBaseTest
{
    // TestContextManager is used because @RunWith(Parameterized.class) is overriding @RunWith(SpringRunner.class)
    private TestContextManager testContextManager
    private Map testArgs
    private NCube cp

    private static final Logger LOG = LoggerFactory.getLogger(TestThreading.class)
    private static String savedSourcesDir
    private static String savedClassesDir

    @Parameterized.Parameters(name = "{0}")
    static Collection<Object[]> data() {
        def data = []

        int sleep = 0L
        int loopTest = 5
        int load = 25
        int threads = 5
        int count = 5

        data << [ [load:load,threads:threads,count:count, clearCache:false, loopTest:loopTest, preCache:false, sleep:sleep] ]
        data << [ [load:load,threads:threads,count:count,clearCache:true, loopTest:loopTest, preCache:false, sleep:sleep] ]
        data << [ [load:load,threads:threads,count:count,clearCache:false, loopTest:loopTest, preCache:true, sleep:sleep] ]
        data << [ [load:load,threads:threads,count:count,clearCache:true, loopTest:loopTest, preCache:true, sleep:sleep] ]

        load = 50; threads = 5; count = 5
        data << [ [load:load * 2,threads:threads,count:count,clearCache:false, loopTest:loopTest, preCache:false, sleep:sleep] ]
        data << [ [load:load * 2,threads:threads,count:count,clearCache:true, loopTest:loopTest, preCache:false, sleep:sleep] ]
        data << [ [load:load * 2,threads:threads,count:count,clearCache:false, loopTest:loopTest, preCache:true, sleep:sleep] ]
        data << [ [load:load * 2,threads:threads,count:count,clearCache:true, loopTest:loopTest, preCache:true, sleep:sleep] ]

        load = 25; threads = 5; count = 15
        data << [ [load:load,threads:threads,count:count*10,clearCache:false, loopTest:loopTest, preCache:false, sleep:sleep] ]
        data << [ [load:load,threads:threads,count:count*10,clearCache:true, loopTest:loopTest, preCache:false, sleep:sleep] ]
        data << [ [load:load*2,threads:threads,count:count,clearCache:true, loopTest:loopTest, preCache:true, sleep:sleep] ]

        load = 25; threads = 15; count = 15
        data << [ [load:load,threads:threads,count:count,clearCache:false, loopTest:loopTest, preCache:false, sleep:sleep] ]
        data << [ [load:load,threads:threads,count:count,clearCache:true, loopTest:loopTest, preCache:false, sleep:sleep] ]
        data << [ [load:load,threads:threads,count:count,clearCache:true, loopTest:loopTest, preCache:true, sleep:sleep] ]

        return data as Object [][]
    }

    TestThreading(Map args)
    {
        testArgs = args
    }

    @Test
    void testQuiet()
    {
    }

    @Test
    void test()
    {
        runTest testArgs
    }

    @Before
    void setup()
    {
        testContextManager = new TestContextManager(getClass())
        testContextManager.prepareTestInstance(this)
        super.setup()
        cp = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'sys.classpath.threading.json')
        ncubeRuntime.clearCache(ApplicationID.testAppId)
        ncubeRuntime.addCube(cp)

        savedSourcesDir = GroovyBase.generatedSourcesDirectory
        savedClassesDir = CdnClassLoader.generatedClassesDirectory

        File genSourcesDir = new File('target/generated-sources')
        File genClassesDir = new File('target/generated-classes')

        String srcDirPath = genSourcesDir.path
        String clsDirPath = genClassesDir.path

        GroovyBase.generatedSourcesDirectory = srcDirPath
        CdnClassLoader.generatedClassesDirectory = genClassesDir.path
        assertEquals(srcDirPath,GroovyBase.generatedSourcesDirectory)
        assertEquals(clsDirPath,CdnClassLoader.generatedClassesDirectory)
    }

    @After
    void tearDown()
    {
        GroovyBase.generatedSourcesDirectory = savedSourcesDir
        CdnClassLoader.generatedClassesDirectory = savedClassesDir
        super.teardown()
    }

//    @Ignore
//    @Test
//    public void testCaching_NonConflicting() {
//        NCube cube = NCube.createCubeFromBytes(cachingDef.getBytes())
//        assertNotNull(cube)
//        assertEquals('static',cube.getCell(['nm':'static']))
//        assertEquals(2,cube.getAxis('nm').getColumns().size())
//
//        assertEquals('test1',cube.getCell(['nm':'test1']))
//        assertEquals(3,cube.getAxis('nm').getColumns().size())
//        assertEquals('test1',cube.getCell(['nm':'test1']))
//        assertEquals(3,cube.getAxis('nm').getColumns().size())
//
//        def threads = new ConcurrentLinkedQueue()
//        def dtos = new ConcurrentLinkedQueue()
//        println 'creating threads'
//        maxThreads.times { tid ->
//            def t = new Thread({
//                maxCount.times { cnt ->
//                    def nm='test-'+tid+'-'+cnt
//                    assertEquals(nm,cube.getCell(['nm':nm]))
//                }
//            } as Runnable)
//            threads << t
//        }
//
//        println 'starting threads'
//        threads.each{ it.start() }
//
//        println 'waiting for threads'
//        def aliveCount = threads.size()
//        while (aliveCount > 0) {
//            aliveCount = 0
//            threads.each{ thread ->
//                if (thread.isAlive()) aliveCount++
//            }
//        }
//
//        println 'validating threads'
//        assertEquals((threads.size()*maxCount)+3,cube.getAxis('nm').getColumns().size())
//        threads.eachWithIndex{ thread, tid ->
//            maxCount.times { cnt ->
//                def nm='test-'+tid+'-'+cnt
//                assertEquals(nm,cube.getCell(['nm':nm]))
//            }
//        }
//    }

    private List<Exception> runTest( Map args ) {

        int load = args.get('load',1)
        int maxThreads = args.get('threads',5)
        int count = args.get('count',100)
        int loopCount = args.get('loopCount',1)
        int loopTest = args.get('loopTest',1)
        int sleepTime = args.get('sleep',0L)
        boolean warm = args.get('warm',false)
        boolean remove = args.get('remove',false)
        boolean sync = args.get('sync',false)
        boolean clearCache = args.get('clearCache',false)
        boolean preCache = args.get('preCache',false)
        boolean ifc = args.get('interface',false)

        LOG.info "Running test with load=${load}, threads=${maxThreads}, count=${count}, loopCount=${loopCount}, clearCache=${clearCache}, loopTest=${loopTest}, preCache=${preCache}, sleep=${sleepTime}, sync=${sync}, remove=${remove}, warm=${warm}"
        buildAccessCube(maxThreads,count,warm)
        NCube cube = ncubeRuntime.getCube(ApplicationID.testAppId, 'thread')
        NCube supportingCube = ncubeRuntime.getCube(ApplicationID.testAppId, 'threadCount')

        def allFailures = new ConcurrentLinkedQueue<Exception>()
        long totalDuration = 0
        loopTest.times { i ->
            long start = System.currentTimeMillis()
            if (i>0) LOG.debug "->loop: ${i}"
            if (clearCache) {
                LOG.debug '==>clear cache'
                ncubeRuntime.clearCache(ApplicationID.testAppId)

                NCube ncube1 = NCube.fromSimpleJson(cp.toFormattedJson())
                NCube ncube2 = NCube.fromSimpleJson(supportingCube.toFormattedJson())
                NCube ncube3 = NCube.fromSimpleJson(cube.toFormattedJson())
                ncube1.applicationID = ApplicationID.testAppId
                ncube2.applicationID = ApplicationID.testAppId
                ncube2.applicationID = ApplicationID.testAppId
                ncubeRuntime.addCube(ncube1)
                ncubeRuntime.addCube(ncube2)
                ncubeRuntime.addCube(ncube3)
            }

            if (preCache) {
                long startCache = System.currentTimeMillis()
                ncubeRuntime.getCube(ApplicationID.testAppId,'thread').compile()
                ncubeRuntime.getCube(ApplicationID.testAppId,'threadCount').compile()
                LOG.info "==>pre-cache, took ${System.currentTimeMillis()-startCache}ms"
            }

            LOG.debug '==>creating threads'
            def threads = new ConcurrentLinkedQueue<>()
            def failures = new ConcurrentLinkedQueue<>()
            load.times {
                maxThreads.times { tid ->
                    def t = new Thread({
                        loopCount.times {
                            count.times { cnt ->
                                def nm = 'test-' + tid + '-' + cnt
                                def output = [:]
                                try {
                                    def val = cube.getCell(['tid': tid, 'cnt': cnt, 'sleep':sleepTime, 'sync':sync, 'remove':remove, 'interface':ifc], output)
                                    if (nm != val) {
                                        throw new RuntimeException("Cell value=" + val + " does not match expected")
                                    }
                                }
                                catch (Exception e)
                                {
                                    Throwable rootCause = StackTraceUtils.extractRootCause(e)
                                    if (!rootCause?.message?.toLowerCase()?.contains('code cleared while'))
                                    {
                                        failures.add(rootCause)
                                    }
                                }
                            }
                        }
                    } as Runnable)
                    t.daemon = true
                    threads << t
                }
            }

            LOG.debug '==>starting threads'
            threads.each { thread ->
                thread.start()
            }

            LOG.debug '==>waiting for threads'
            def aliveCount = threads.size()
            while (aliveCount > 0) {
                aliveCount = 0
                threads.each { thread ->
                    if (thread.isAlive()) aliveCount++
                }
            }

            validateRunnableCode(maxThreads,count,failures)

            long duration = System.currentTimeMillis()-start
            totalDuration += duration
            LOG.info "Loop ${i} took " + duration + "ms with failure rate of " + failures.size() + "/" + (maxThreads*count*loopCount*load)
            dumpFailures(failures)
            allFailures.addAll(failures)
        }

        LOG.info "total time of " + totalDuration + "ms and average of " + (totalDuration/loopTest) + "ms with failure rate of " + allFailures.size() + "/" + (loopTest*(maxThreads*count*loopCount*load))
        dumpFailures(allFailures)
        assertEquals(0,allFailures.size())
        return allFailures as List
    }

    private void validateRunnableCode(int maxThreads, int maxCount, Collection failures) {
        NCube threadCube = ncubeRuntime.getCube(ApplicationID.testAppId, 'threadCount')
        ClassLoader cdnLoader = ncubeRuntime.getCube(ApplicationID.testAppId,cp.name).getCell([:],[:])

        maxThreads.times { int tid ->
            maxCount.times { int cnt ->
                GroovyBase cell = threadCube.getCellNoExecute(['tid':tid,'cnt':cnt,'sleep':0L]) as GroovyBase
                ClassLoader cellLoader = cell.runnableCode.classLoader
                if (cdnLoader != cellLoader && cdnLoader.parent != cellLoader) {
                    def nm = "test-${tid}-${cnt}"
                    failures.add( new IllegalStateException("ClassLoader did not match for cell"))
                }
            }
        }

    }

    private static void dumpFailures(ConcurrentLinkedQueue<Exception> failures) {
        def uniqueFailures = [:]
        failures.each { f ->
            def msg = f.message
            if (StringUtilities.hasContent(msg))
            {
                if (msg.contains('@'))
                {
                    msg = msg.split('@')[0]
                }
                if (uniqueFailures.containsKey(msg))
                {
                    uniqueFailures[msg]++
                }
                else
                {
                    uniqueFailures[msg] = 1L
                }
            }
        }
        uniqueFailures.each { k,v ->
            LOG.info "${v}: ${k}"
        }
    }

    private NCube buildAccessCube(int maxThreads, int maxCount, boolean warm) {
        LOG.info '==>Creating cube...'
        NCube threadCube = NCube.fromSimpleJson(threadDef)
        assertNotNull(threadCube)
        NCube cube = NCube.fromSimpleJson(threadCountDef)
        Axis axisTid = cube.getAxis("tid")
        Axis axisCnt = cube.getAxis("cnt")
        assertNotNull(cube)
        assertNotNull(axisTid)
        assertNotNull(axisCnt)

        LOG.debug 'columns...'
        maxThreads.times { int tid -> threadCube.getAxis('tid').addColumn(tid)}
        maxThreads.times { int tid -> axisTid.addColumn(tid) }
        maxCount.times { int cnt -> axisCnt.addColumn(cnt) }

        LOG.debug 'cells...'
        maxThreads.times { tid ->
            threadCube.setCell(new GroovyExpression('@threadCount[:]', null, false), ['tid':tid])
            maxCount.times { cnt ->

                GroovyExpression cell = null
                switch (cnt % 3) {
                    case 0:
                        cell = new GroovyExpression("if (input.get('sleep',0L)>0) sleep(input.sleep)\n'test-${tid}-${cnt}'",null, false)
                        break
                    case 1:
                        cell = new GroovyExpression("if (input.get('sleep',0L)>0) sleep(input.sleep)\n'test-' + input.tid + '-' + input.cnt",null, false)
                        break
                    case 2:
                        cell = new GroovyExpression(null,'files/ncube/threadCount.groovy', false)
                        break
                }

                cube.setCell(cell,['tid':tid,'cnt':cnt])
            }
        }

        LOG.debug 'recreating...'
        cube = NCube.fromSimpleJson(cube.toFormattedJson())
        threadCube = NCube.fromSimpleJson(threadCube.toFormattedJson())
        cube.applicationID = ApplicationID.testAppId
        threadCube.applicationID = ApplicationID.testAppId
        ncubeRuntime.addCube(threadCube)
        ncubeRuntime.addCube(cube)

        if (warm) {
            LOG.info 'warming...'
            maxThreads.times { int tid ->
                maxCount.times { int cnt ->
                    def nm = "test-${tid}-${cnt}"
                    try
                    {
                        assertEquals(nm,threadCube.getCell(['tid':tid,'cnt':cnt,'sleep':0L]))
                    }
                    catch (Exception e)
                    {
                        Throwable rootCause = StackTraceUtils.extractRootCause(e)
                        if (!rootCause.message.toLowerCase().contains('code cleared while'))
                        {
                            failures.add(rootCause)
                        }
                    }
                }
            }
        }

        LOG.debug 'done'
        return cube
    }

    static String threadCountDef ='''{
            "ncube": "threadCount",
            "axes": [
                {
                    "name": "tid",
                    "type": "DISCRETE",
                    "valueType": "STRING",
                    "hasDefault": true,
                    "preferredOrder": 0,
                    "columns": []
                },
                {
                    "name": "cnt",
                    "type": "DISCRETE",
                    "valueType": "STRING",
                    "hasDefault": true,
                    "preferredOrder": 0,
                    "columns": []
                }
            ],
            "cells": [
                {
                    "id": [],
                    "type": "exp",
                    "value": "'fail'"
                }
            ]
            }'''

    static String threadDef='''{
            "ncube": "thread",
            "axes": [
                {
                    "name": "tid",
                    "type": "DISCRETE",
                    "valueType": "STRING",
                    "hasDefault": true,
                    "preferredOrder": 0,
                    "columns": []
                }
            ],
            "cells": [
                {
                    "id": [],
                    "type": "exp",
                    "value": "@threadCount[:]"
                }
            ]
            }'''

}
