package com.cedarsoftware.ncube

import com.cedarsoftware.util.io.MetaUtils
import com.marklogic.client.DatabaseClient
import com.marklogic.client.DatabaseClientFactory
import com.marklogic.client.Transaction
import groovy.transform.CompileStatic
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.reflect.MethodSignature
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import static com.cedarsoftware.ncube.NCubeConstants.LOG_ARG_LENGTH

/**
 * Before Advice that sets user ID on current thread.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com), Josh Snyder (joshsnyder@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br/><br/>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br/><br/>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
@Aspect
class NCubeManagerAdvice
{
    private static final Logger LOG = LoggerFactory.getLogger(NCubeManagerAdvice.class)
    private final NCubeMutableClient manager
    private final DatabaseClient client

    private final ThreadLocal<Transaction> transaction = new ThreadLocal<Transaction>() {
        Transaction initialValue()
        {
            return null
        }
    }

    NCubeManagerAdvice(NCubeMutableClient manager, String host, int port, DatabaseClientFactory.SecurityContext securityContext)
    {
        this.manager = manager
        client = DatabaseClientFactory.newClient(host, port, 'ncube', securityContext)
        MarkLogicPersisterAdapter persisterAdapter = (MarkLogicPersisterAdapter)NCubeAppContext.getBean('mlPersister')
        persisterAdapter.databaseClient = client
    }

    @Around("execution(* com.cedarsoftware.ncube.NCubeManager.*(..)) && !execution(* com.cedarsoftware.ncube.NCubeManager.getUserId(..))")
    def advise(ProceedingJoinPoint pjp)
    {
        // Place user on ThreadLocal
        String username = manager.userId

        long start = System.nanoTime()
        // Execute method
        def ret = null
        try
        {
            client.openTransaction()
            setTransaction(client.openTransaction())
            ret = pjp.proceed()
            getTransaction().commit()
        }
        catch (Exception e)
        {
            try
            {
                getTransaction()?.rollback()
            }
            catch (Exception ex)
            {
                LOG.warn('Exception occurred rolling back MarkLogic transaction', ex)
            }
            throw e
        }
        long end = System.nanoTime()
        long time = Math.round((end - start) / 1000000.0d)
        MethodSignature signature = (MethodSignature) pjp.signature
        String methodName = signature.method.name
        Object[] args = pjp.args

        if (time > 1000)
        {
            LOG.info("[SLOW CALL - ${time} ms] [${username}] ${MetaUtils.getLogMessage(methodName, args, LOG_ARG_LENGTH)}")
        }
        else if (LOG.debugEnabled)
        {
            LOG.debug("[CALL - ${time} ms] [${username}] ${MetaUtils.getLogMessage(methodName, args, LOG_ARG_LENGTH)}")
        }
        return ret
    }

    private setTransaction(Transaction trans)
    {
        transaction.set(trans)
    }

    Transaction getTransaction()
    {
        return transaction.get()
    }

}