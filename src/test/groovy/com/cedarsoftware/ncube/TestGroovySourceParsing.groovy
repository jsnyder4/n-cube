package com.cedarsoftware.ncube

import com.cedarsoftware.util.CaseInsensitiveMap
import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime
import static org.junit.Assert.assertEquals

/**
 * Test Groovy Source code parsing, including finding n-cube names within source (used with @, $, or APIs
 * like runRuleCube() or getCube(), as well as coordinate keys.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
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
@CompileStatic
class TestGroovySourceParsing extends NCubeBaseTest
{
    @Test
    void testFindCubeName()
    {
        NCube cube = ncubeRuntime.getNCubeFromResource(ApplicationID.testAppId, 'inlineExpression.json')
        Map<CaseInsensitiveMap, Set<String>> names = cube.referencedCubeNames as Map

        assertEquals(['RockRiverArms'] as Set, names[[Age:'jump2'] as CaseInsensitiveMap])
        assertEquals(['Bersa'] as Set, names[[Age:'absRef3'] as CaseInsensitiveMap])
        assertEquals(['Springfield'] as Set, names[[Age:'relRef4'] as CaseInsensitiveMap])
        assertEquals(['Marlin'] as Set, names[[Age:'jump3'] as CaseInsensitiveMap])
        assertEquals(['Glock'] as Set, names[[Age:'ncubeRef1'] as CaseInsensitiveMap])
        assertEquals(['Mossberg'] as Set, names[[Age:'relRef1'] as CaseInsensitiveMap])
        assertEquals(['Browning'] as Set, names[[Age:'absRef4'] as CaseInsensitiveMap])
        assertEquals(['Remington'] as Set, names[[Age:'jump4'] as CaseInsensitiveMap])
        assertEquals(['Car'] as Set, names[[Age:'ncubeRef2'] as CaseInsensitiveMap])
        assertEquals(['Beretta'] as Set, names[[Age:'absRef2'] as CaseInsensitiveMap])
        assertEquals(['Sig'] as Set, names[[Age:'runRuleRef1'] as CaseInsensitiveMap])
        assertEquals(['Kimber'] as Set, names[[Age:'relRef3'] as CaseInsensitiveMap])
        assertEquals(['Colt'] as Set, names[[Age:'runRuleRef2'] as CaseInsensitiveMap])
        assertEquals(['SnW'] as Set, names[[Age:'jump1'] as CaseInsensitiveMap])
        assertEquals(['FNHerstal'] as Set, names[[Age:'relRef2'] as CaseInsensitiveMap])
        assertEquals(['Winchester'] as Set, names[[Age:'absRef1'] as CaseInsensitiveMap])
    }
}
