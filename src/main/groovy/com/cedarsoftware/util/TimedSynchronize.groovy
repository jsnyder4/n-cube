package com.cedarsoftware.util

import groovy.transform.CompileStatic

import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock

/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
class TimedSynchronize
{
    static void synchronize(Lock lock, long retryInterval, TimeUnit units, String errMsg = "Dead lock detected", int maxAttempts = 100)
    {
        int attempts = 0
        while (!lock.tryLock(retryInterval, units))
        {
            attempts++
            if (attempts > maxAttempts)
            {
                throw new IllegalStateException(errMsg)
            }
        }
    }
}
