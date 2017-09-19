package com.cedarsoftware.ncube.exception

import groovy.transform.CompileStatic

@CompileStatic
class PullRequestException extends RuntimeException
{
    PullRequestException(String message)
    {
        super(message)
    }
}
