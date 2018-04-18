package com.cedarsoftware.ncube

import org.junit.Test

import java.util.regex.Matcher

import static org.junit.Assert.assertEquals
import static org.junit.Assert.assertFalse

/**
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
class TestRegexes
{
    @Test
    void testImport1()
    {
        String code = """package foo
import java.lang.*;
import java.util.*
  import x;
   import z
 import a.b.* // comment after
 import a.b.*; /* comment after */
class x { }"""
        Matcher m = Regexes.importPattern.matcher(code)
        assert m.find()
        assert 'import java.lang.*;' == m.group(1)
        assert m.find()
        assert 'import java.util.*' == m.group(1)
        assert m.find()
        assert '  import x;' == m.group(1)
        assert m.find()
        assert '   import z' == m.group(1)
        assert m.find()
        assert ' import a.b.*' == m.group(1)
        assert m.find()
        assert ' import a.b.*;' == m.group(1)
        assert !m.find()

        m.reset()
        String left = m.replaceAll('')
        assert left.startsWith('package foo')
        assert left.endsWith('class x { }')
    }

    @Test
    void testImport2()
    {
        String code = """println 'yes'
import java.lang.*
println 'code'"""
        Matcher m = Regexes.importPattern.matcher(code)
        assert m.find()
        assert 'import java.lang.*' == m.group(1)
        assert !m.find()

        m.reset()
        code = m.replaceAll("")
        assert code.startsWith("println 'yes'")
        assert code.endsWith("println 'code'")
    }

    @Test
    void testImport3()
    {
        String code = """package foo
import java.lang.*
import this.is.ok  // are you sure?
import static java.static.*
class x { println " import i.fooled.it;" }"""
        Matcher m = Regexes.importPattern.matcher(code)
        assert m.find()
        assert 'import java.lang.*' == m.group(1)
        assert m.find()
        assert 'import this.is.ok' == m.group(1)
        assert m.find()
        assert 'import static java.static.*' == m.group(1)
        assert !m.find()

        m.reset()
        code = m.replaceAll('')
        assert code.startsWith('package foo')
        assert code.endsWith('class x { println " import i.fooled.it;" }')
    }

    @Test
    void testImport4()
    {
        String code = """import foo.bar.baz.Qux; if (true) { return true } else { return false }"""
        Matcher m = Regexes.importPattern.matcher(code)
        assert m.find()
        assert 'import foo.bar.baz.Qux;' == m.group(1)
        assert !m.find()

        m.reset()
        code = m.replaceAll('')
        assert code.startsWith(' if (true)')
        assert code.endsWith('false }')
    }

    @Test
    void testInputVarHunting()
    {
        String code = """input.startDate > input.endDate"""
        Matcher m = Regexes.inputVar.matcher(code)
        assert m.find()
        assert 'startDate' == m.group(2)
        assert m.find()
        assert 'endDate' == m.group(2)
        assert !m.find()
    }

    @Test
    void testInputVarHunting2()
    {
        String code = """
println 'is it going to count input.what have we got here'
"""
        Matcher m = Regexes.inputVar.matcher(code)
        assert m.find()
        // Not that I like it, but let's verify how it is currently working.
        assert 'what' == m.group(2)
    }

    @Test
    void testScriptletPattern()
    {
        String code = """<%=input.state%>"""
        Matcher m = Regexes.scripletPattern.matcher(code)
        assert m.find()
        // Not that I like it, but let's verify how it is currently working.
        code = m.group(1)
        assert !code.contains('<%')
        assert !code.contains('%>')
        assert code.contains("input.state")

        code = """Other stuff
<% println 'This is a string: ' + input.state %>around
"""
        m = Regexes.scripletPattern.matcher(code)
        assert m.find()
        // Not that I like it, but let's verify how it is currently working.
        code = m.group(1)
        assert !code.contains('<%')
        assert !code.contains('%>')
        assert code.contains("println 'This is a string: ' + input.state")
    }

    @Test
    void testVelocityPattern()
    {
        String code = '${input.state}'
        Matcher m = Regexes.velocityPattern.matcher(code)
        assert m.find()
        // Not that I like it, but let's verify how it is currently working.
        code = m.group(1)
        assert !code.contains('${')
        assert !code.contains('}')
        assert code.contains("input.state")

        code = 'Other stuff\n${ println \'This is a string: \' + input.state }around'
        m = Regexes.velocityPattern.matcher(code)
        assert m.find()
        // Not that I like it, but let's verify how it is currently working.
        code = m.group(1)
        assert !code.contains('${')
        assert !code.contains('}')
        assert code.contains("println 'This is a string: ' + input.state")
    }

    @Test
    void testLatLongRegex()
    {
        Matcher m = Regexes.valid2Doubles.matcher '25.8977899,56.899988'
        assert m.matches()
        assert '25.8977899' == m.group(1)
        assert '56.899988' == m.group(2)

        m = Regexes.valid2Doubles.matcher ' 25.8977899, 56.899988 '
        assert m.matches()
        assert '25.8977899' == m.group(1)
        assert '56.899988' == m.group(2)

        m = Regexes.valid2Doubles.matcher '-25.8977899,-56.899988 '
        assert m.matches()
        assert '-25.8977899' == m.group(1)
        assert '-56.899988' == m.group(2)

        m = Regexes.valid2Doubles.matcher 'N25.8977899, 56.899988 '
        assert !m.matches()

        m = Regexes.valid2Doubles.matcher '25.8977899, E56.899988 '
        assert !m.matches()

        m = Regexes.valid2Doubles.matcher '25., 56.899988 '
        assert !m.matches()

        m = Regexes.valid2Doubles.matcher '25.891919, 56. '
        assert !m.matches()
    }

    @Test
    void testPoint3d()
    {
        Matcher m = Regexes.valid3Doubles.matcher '25.8977899,56.899988,3.1415'
        assert m.matches()
        assert '25.8977899' == m.group(1)
        assert '56.899988' == m.group(2)
        assert '3.1415' == m.group(3)

        m = Regexes.valid3Doubles.matcher '25,56,3'
        assert m.matches()
        assert '25' == m.group(1)
        assert '56' == m.group(2)
        assert '3' == m.group(3)

        m = Regexes.valid3Doubles.matcher ' 25.8977899, 56.899988, 3.14 '
        assert m.matches()
        assert '25.8977899' == m.group(1)
        assert '56.899988' == m.group(2)
        assert '3.14' == m.group(3)

        m = Regexes.valid3Doubles.matcher ' 25, 56, 3 '
        assert m.matches()
        assert '25' == m.group(1)
        assert '56' == m.group(2)
        assert '3' == m.group(3)

        m = Regexes.valid3Doubles.matcher '-25.8977899,-56.899988,-3.14'
        assert m.matches()
        assert '-25.8977899' == m.group(1)
        assert '-56.899988' == m.group(2)
        assert '-3.14' == m.group(3)

        m = Regexes.valid3Doubles.matcher '-25,-56,-3'
        assert m.matches()
        assert '-25' == m.group(1)
        assert '-56' == m.group(2)
        assert '-3' == m.group(3)

        m = Regexes.valid3Doubles.matcher 'N25.8977899, 56.899988, 3.14 '
        assert !m.matches()

        m = Regexes.valid3Doubles.matcher '25.8977899, E56.899988, 3.14 '
        assert !m.matches()

        m = Regexes.valid3Doubles.matcher '25., 56.899988, 3.14 '
        assert !m.matches()

        m = Regexes.valid3Doubles.matcher '25.891919, 56., 3.14 '
        assert !m.matches()
    }

    @Test
    void testNCubeNameParser()
    {
        String name = "['Less than \$10,000':['startIncurredAmount':'0','endIncurredAmount':'10000'],'\$10,000 - \$25,000':['startIncurredAmount':'10000','endIncurredAmount':'25000'],'\$25,000 - \$50,000':['startIncurredAmount':'25000','endIncurredAmount':'50000'],'More than \$50,000':['startIncurredAmount':'50000','endIncurredAmount':'0']]";
        Matcher m = Regexes.groovyRelRefCubeCellPatternA.matcher(name)
        assertFalse(m.find())

        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assertFalse(m.find())

        m = Regexes.groovyAbsRefCubeCellPattern.matcher(name)
        assertFalse(m.find())

        m = Regexes.groovyAbsRefCubeCellPatternA.matcher(name)
        assertFalse(m.find())

        name = "@Foo([:])"

        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        m.find()
        assertEquals("Foo", m.group(2))

        name = "@Foo([:])"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        m.find()
        assertEquals("Foo", m.group(2))

        name = "\$Foo([alpha:'bravo'])"
        m = Regexes.groovyAbsRefCubeCellPattern.matcher(name)
        m.find()
        assertEquals("Foo", m.group(2))

        name = "\$Foo[:]"
        m = Regexes.groovyAbsRefCubeCellPatternA.matcher(name)
        m.find()
        assertEquals("Foo", m.group(2))
    }

    @Test
    void testPositiveRelativeCubeBracketAnnotations()
    {
        def name = "@PackageScope[a:1]" // Legal because of square brackets
        def m = Regexes.groovyRelRefCubeCellPatternA.matcher(name)
        assert m.find()

        name = "@ PackageScope [a:1, b:2, c:'three', 'd':\"four\"]" // Legal because of square brackets
        m = Regexes.groovyRelRefCubeCellPatternA.matcher(name)
        assert m.find()

        name = "@PackageScope[:]" // Legal because of square brackets
        m = Regexes.groovyRelRefCubeCellPatternA.matcher(name)
        assert m.find()

        name = "@Very_Ugly.Name[:]" // Legal because of square brackets and safe name
        m = Regexes.groovyRelRefCubeCellPatternA.matcher(name)
        assert m.find()
    }

    @Test
    void testNestedNamesWithAtSymbol()
    {
        def name = "@rpm.rating.mappings.product[fieldName: @rpm.rule.bogus[hello:'Dolly']]" // Legal because of square brackets
        def m = Regexes.groovyRelRefCubeCellPatternA.matcher(name)
        assert m.find()
        assert m.group('cubeName') == 'rpm.rating.mappings.product'
        // Note, ignoring additional lookup in parameters - extract that to local var ahead of call
    }

    @Test
    void testNestedNamesWithAt()
    {
        def name = "at([InsurableValueRangeByDeductibleCOLState: input.name , Range: 'min'], 'rule.utility.validate')"
        def m = Regexes.groovyExplicitAtPattern.matcher(name)
        assert m.find()
        assert m.group('cubeName') == 'rule.utility.validate'
        // Note, ignoring additional lookup in parameters - extract that to local var ahead of call
    }

    @Test
    void testNestedNames()
    {
        def name = "@rpm.rating.mappings.product[fieldName: @rpm.rule.bogus[hello:'Dolly']]" // Legal because of square brackets
        def m = Regexes.groovyRelRefCubeCellPatternA.matcher(name)
        assert m.find()
        assert m.group('cubeName') == 'rpm.rating.mappings.product'
        // Note, ignoring additional lookup in parameters - extract that to local var ahead of call
    }

    @Test
    void testNegativeRelativeCubeBracketAnnotations()
    {
        def name = "@PackageScope[]" // Illegal because of no colon within brackets
        def m = Regexes.groovyRelRefCubeCellPatternA.matcher(name)
        assertFalse m.find()

        name = "@Foo[map]" // Illegal because of no colon within brackets
        m = Regexes.groovyRelRefCubeCellPatternA.matcher(name)
        assertFalse m.find()
    }

    @Test
    void testPositiveRelativeCubeParenAnnotations()
    {
        def name = "@PostAuthorize(jim)"    // valid, and jim better be a map
        def m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assert m.find()

        name = "@PostAuthorize( jim )"    // valid, and jim better be a map
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assert m.find()

        name = "@PreAuthorizeStart([foo:bar])"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assert m.find()

        name = "@PreAuthorizeStart( [ foo : bar ] )"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assert m.find()

        name = "@PreAuthorizeStart([foo:'bar'])"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assert m.find()

        name = "@PreAuthorizeStart([foo:\"bar\"])"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assert m.find()

        name = '@PreAuthorizeStart([\'foo\':com.foo.bar.Qux$b])'
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assert m.find()

        name = '@ PreAuthorizeStart ( [  "foo"  :  com.foo.bar.Qux$b  ]  )'
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assert m.find()
    }

    @Test
    void testNegativeRelativeCubeParenAnnotations()
    {
        def name = "@PreAuthorize(jim)"    // Not allowed to use PreAuthorize
        def m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assertFalse m.find()

        name = "@PreAuthorize(resourceURI = \"/resource/submission\", actionURI = \"/action/view\")"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assertFalse m.find()

        name = "@ControllerClass"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assertFalse m.find()

        name = "@PackageScope( )"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assertFalse m.find()

        name = "@PackageScope(METHODS)"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assertFalse m.find()

        name = "@SuppressWarnings(\"foo\")"
        m = Regexes.groovyRelRefCubeCellPattern.matcher(name)
        assertFalse m.find()
    }

    @Test
    void testAtPattern()
    {
        String src = "at(coord, 'foo', null, new ApplicationID()) && at([:],'bar')"
        Matcher m = Regexes.groovyExplicitAtPattern.matcher(src)

        m.find()
        assert 'foo' == m.group('cubeName')

        m.find()
        assert 'bar' == m.group('cubeName')

        src = "if (at(coord, 'foo', null, new ApplicationID()) && at([:],'bar'))"
        m = Regexes.groovyExplicitAtPattern.matcher(src)

        m.find()
        assert 'foo' == m.group(2)

        m.find()
        assert 'bar' == m.group(2)

        src = "if (at(coord, cubeName, null, new ApplicationID()) && at([:],'bar'))"
        m = Regexes.groovyExplicitAtPattern.matcher(src)

        m.find()
        assert 'bar' == m.group(2)

        src = "if (at([:], 'foo'))"
        m = Regexes.groovyExplicitAtPattern.matcher(src)

        m.find()
        assert 'foo' == m.group(2)

        src = "if (at([:], cubeName))"
        m = Regexes.groovyExplicitAtPattern.matcher(src)

        assertFalse m.find()

        src = "if (cat([:], 'foo'))"
        m = Regexes.groovyExplicitAtPattern.matcher(src)

        assertFalse m.find()

        src = "cat([:], 'foo')"
        m = Regexes.groovyExplicitAtPattern.matcher(src)

        assertFalse m.find()
    }

    @Test
    void testGoPattern()
    {
        String src = "go(coord, 'foo', null, new ApplicationID()) && go([:],'bar')"
        Matcher m = Regexes.groovyExplicitGoPattern.matcher(src)

        m.find()
        assert 'foo' == m.group(2)

        m.find()
        assert 'bar' == m.group(2)

        src = "if (go(coord, 'foo', null, new ApplicationID()) && go([:],'bar'))"
        m = Regexes.groovyExplicitGoPattern.matcher(src)

        m.find()
        assert 'foo' == m.group(2)

        m.find()
        assert 'bar' == m.group(2)

        src = "if (go(coord, cubeName, null, new ApplicationID()) && go([:],'bar'))"
        m = Regexes.groovyExplicitGoPattern.matcher(src)

        m.find()
        assert 'bar' == m.group(2)

        src = "if (go([:], 'foo'))"
        m = Regexes.groovyExplicitGoPattern.matcher(src)

        m.find()
        assert 'foo' == m.group(2)

        src = "if (go([:], cubeName))"
        m = Regexes.groovyExplicitGoPattern.matcher(src)

        assertFalse m.find()

        src = "if (ego([:], 'foo'))"
        m = Regexes.groovyExplicitGoPattern.matcher(src)

        assertFalse m.find()

        src = "ego([:], 'foo')"
        m = Regexes.groovyExplicitGoPattern.matcher(src)

        assertFalse m.find()
    }
}
