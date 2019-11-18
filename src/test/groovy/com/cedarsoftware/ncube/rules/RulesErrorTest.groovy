package com.cedarsoftware.ncube.rules

import groovy.transform.CompileStatic
import org.junit.Test

@CompileStatic
class RulesErrorTest
{
    @Test
    void testConstructor()
    {
        RulesError error = new RulesError('foo', 'bar', 'baz')
        assert 'foo' == error.category
        assert 'bar' == error.code
        assert 'baz' == error.message
    }

    @Test
    void testToString()
    {
        RulesError error = new RulesError('foo', 'bar', 'baz')
        assert 'baz' == error.toString()
    }
}
