package com.cedarsoftware.ncube.rules

import groovy.transform.CompileStatic

@CompileStatic
class RulesError
{
    String category
    String code
    String message

    RulesError(String category, String code, String message)
    {
        this.category = category
        this.code = code
        this.message = message
    }

    String toString()
    {
        return message
    }
}
