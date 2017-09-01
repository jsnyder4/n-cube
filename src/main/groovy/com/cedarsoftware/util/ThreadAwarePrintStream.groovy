package com.cedarsoftware.util

import groovy.transform.CompileStatic

import org.springframework.util.FastByteArrayOutputStream

/**
 * Thread-aware PrintStream.  Use to separate different threads' output
 * to System.output, System.err so that this output can be captured per
 * thread.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
class ThreadAwarePrintStream extends PrintStream
{
    private final OutputStream original
    
    private static final ThreadLocal<Boolean> redirect = new ThreadLocal<Boolean>() {
        Boolean initialValue()
        {
            return false
        }
    }

    private static final ThreadLocal<FastByteArrayOutputStream> output = new ThreadLocal<FastByteArrayOutputStream>() {
        FastByteArrayOutputStream initialValue()
        {
            return new FastByteArrayOutputStream()
        }
    }

    ThreadAwarePrintStream(OutputStream original)
    {
        super(output.get())
        this.original = original
    }

    void setRedirect(boolean reDir)
    {
        redirect.set(reDir)
    }

    void write(int b)
    {
        if (redirect.get())
        {
            output.get().write(b)
        }
        else
        {
            original.write(b)
        }
    }

    void write(byte[] buf, int off, int len)
    {
        if (redirect.get())
        {
            output.get().write(buf, off, len)
        }
        else
        {
            original.write(buf, off, len)
        }
    }

    static String getContent()
    {
        byte[] contents = output.get().toByteArrayUnsafe()
        output.get().reset()
        return StringUtilities.createString(contents, "UTF-8")
    }
}
