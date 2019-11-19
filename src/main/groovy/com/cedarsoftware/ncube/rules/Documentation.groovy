package com.cedarsoftware.ncube.rules

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.METHOD, ElementType.TYPE])
@interface Documentation
{
    /**
     * English description of the rule
     */
    String value()

    /**
     * Array of NCube names used in the rule
     */
    String[] ncubes() default []

    /**
     * NCube ApplicationID in the format of tenant/app/version/status/branch/
     * Only necessary to define if the NCube is in a different ApplicationID than what's registered with the rules engine
     */
    String appId() default ''

}