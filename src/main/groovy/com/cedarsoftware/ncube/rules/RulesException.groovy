package com.cedarsoftware.ncube.rules

import groovy.transform.CompileStatic

@CompileStatic
class RulesException extends RuntimeException
{
    List<RulesError> errors

    RulesException(List<RulesError> errors)
    {
        super(errors.toString())
        this.errors = errors
    }

    RulesException(String s, List<RulesError> errors)
    {
        super("${s} - ${errors.toString()}")
        this.errors = errors
    }

    RulesException(String message, Throwable cause, List<RulesError> errors)
    {
        super("${message} - ${errors.toString()}", cause)
        this.errors = errors
    }

    RulesException(Throwable cause, List<RulesError> errors)
    {
        super("${errors.toString()}", cause)
        this.errors = errors
    }
}
