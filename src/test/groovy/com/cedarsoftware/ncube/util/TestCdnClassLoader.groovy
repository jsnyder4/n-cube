package com.cedarsoftware.ncube.util

import com.cedarsoftware.ncube.NCubeBaseTest
import groovy.transform.CompileStatic
import org.junit.Test

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertNull
import static org.junit.Assert.assertTrue
/**
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br/>
 *         Copyright (c) Cedar Software LLC
 *         <br/><br/>
 *         Licensed under the Apache License, Version 2.0 (the 'License');
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
@CompileStatic
class TestCdnClassLoader extends NCubeBaseTest
{
    @Test
    void testLocalResources()
    {
        CdnClassLoader testLoader1 = new CdnClassLoader(TestCdnClassLoader.class.classLoader)
        assert !testLoader1.isLocalOnlyResource("META-INF/org.codehaus.groovy.transform.ASTTransformation")
        assert testLoader1.isLocalOnlyResource("ncube/grv/exp/GroovyExpression")
        assert testLoader1.isLocalOnlyResource("ncube/grv/method/GroovyMethod")
        assert testLoader1.isLocalOnlyResource("FooBeanInfo.groovy")
        assert testLoader1.isLocalOnlyResource("FooCustomizer.groovy")

        CdnClassLoader testLoader2 = new CdnClassLoader(TestCdnClassLoader.class.classLoader, false, false)
        assert !testLoader2.isLocalOnlyResource("META-INF/org.codehaus.groovy.transform.ASTTransformation")
        assert testLoader2.isLocalOnlyResource("ncube/grv/exp/NCubeGroovyExpression.groovy")
        assert testLoader2.isLocalOnlyResource("ncube/grv/method/NCubeGroovyController.groovy")
        assert !testLoader2.isLocalOnlyResource("FooBeanInfo.groovy")
        assert !testLoader2.isLocalOnlyResource("FooCustomizer.groovy")
    }

    @Test
    void testFindResource()
    {
        CdnClassLoader testLoader1 = new CdnClassLoader(TestCdnClassLoader.class.classLoader)
        assertNull testLoader1.findResource("cdnRouter.json")
        assertNull testLoader1.findResource("ncube/grv/method/NCubeGroovyController.class") // .class not allowed
        assertNotNull TestCdnClassLoader.class.classLoader.getResource("ncube/grv/method/NCubeGroovyController.class")
    }

    @Test
    void testFindResources()
    {
        CdnClassLoader testLoader1 = new CdnClassLoader(TestCdnClassLoader.class.classLoader)
        assert !testLoader1.findResources("cdnRouter.json").hasMoreElements()
        assert !testLoader1.findResources("ncube/grv/method/NCubeGroovyController.class").hasMoreElements() // .class not allowed
        assert TestCdnClassLoader.class.classLoader.getResources("ncube/grv/method/NCubeGroovyController.class").hasMoreElements()
    }

    @Test
    void testGetResourcesWithLocalResource()
    {
        CdnClassLoader testLoader1 = new CdnClassLoader(TestCdnClassLoader.class.classLoader)
        testLoader1.getResources("config/hsqldb-schema.sql").nextElement()
        try
        {
            new CdnClassLoader(TestCdnClassLoader.class.classLoader).getResources("ddl/xhsqldb-schema.sql").nextElement()
        }
        catch (NoSuchElementException ignore)
        { }
    }

    @Test
    void testGetResourcesMultipleTimes()
    {
        CdnClassLoader loader = new CdnClassLoader(TestCdnClassLoader.class.classLoader)

        String resourceName = 'config/hsqldb-schema.sql'
        Enumeration<URL> urls = loader.getResources(resourceName)
        assertTrue(urls.hasMoreElements())
        assertNotNull(urls.nextElement())
        assertFalse(urls.hasMoreElements())

        urls = loader.getResources(resourceName)
        assertTrue(urls.hasMoreElements())
        assertNotNull(urls.nextElement())
        assertFalse(urls.hasMoreElements())
    }
}