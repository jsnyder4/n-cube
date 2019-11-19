package com.cedarsoftware.ncube.rules

import groovy.transform.CompileStatic
import org.junit.Test

import static com.cedarsoftware.util.TestUtil.assertContainsIgnoreCase
import static org.junit.Assert.fail

@CompileStatic
class RulesExceptionTest
{
    private static final List<RulesError> ERRORS = [new RulesError('foo', 'bar', 'baz')]

    @Test
    void testConstructor()
    {

        RulesException exception = new RulesException(ERRORS)
        assert 1 == exception.errors.size()
        assert exception instanceof RuntimeException

        try
        {
            throwException(exception)
            fail()
        }
        catch (RulesException e)
        {
            assertContainsIgnoreCase(e.message, '[baz]')
        }
    }

    @Test
    void testConstructor_Message()
    {
        RulesException exception = new RulesException('message', ERRORS)
        assert 1 == exception.errors.size()

        try
        {
            throwException(exception)
            fail()
        }
        catch (RulesException e)
        {
            assertContainsIgnoreCase(e.message, 'message', '[baz]')
        }
    }

    @Test
    void testConstructor_MessageCause()
    {
        RulesException exception = new RulesException('message', new IllegalArgumentException('wrong'), ERRORS)
        assert 1 == exception.errors.size()

        try
        {
            throwException(exception)
            fail()
        }
        catch (RulesException e)
        {
            assertContainsIgnoreCase(e.message, 'message', '[baz]')
        }
    }

    @Test
    void testConstructor_Cause()
    {
        RulesException exception = new RulesException(new IllegalArgumentException('wrong'), ERRORS)
        assert 1 == exception.errors.size()

        try
        {
            throwException(exception)
            fail()
        }
        catch (RulesException e)
        {
            assertContainsIgnoreCase(e.message, '[baz]')
        }
    }

    private static void throwException(RulesException exception)
    {
        throw exception
    }
}
