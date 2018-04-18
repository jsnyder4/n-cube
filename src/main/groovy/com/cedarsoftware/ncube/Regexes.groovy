package com.cedarsoftware.ncube

import groovy.transform.CompileStatic

import java.util.regex.Pattern

import static com.cedarsoftware.ncube.ReferenceAxisLoader.REF_APP

/**
 * Regular Expressions used throughout n-cube implementation.
 *
 * @author John DeRegnaucourt (jdereg@gmail.com)
 *         <br>
 *         Copyright (c) Cedar Software LLC
 *         <br><br>
 *         Licensed under the Apache License, Version 2.0 (the "License")
 *         you may not use this file except in compliance with the License.
 *         You may obtain a copy of the License at
 *         <br><br>
 *         http://www.apache.org/licenses/LICENSE-2.0
 *         <br><br>
 *         Unless required by applicable law or agreed to in writing, software
 *         distributed under the License is distributed on an "AS IS" BASIS,
 *         WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *         See the License for the specific language governing permissions and
 *         limitations under the License.
 */
@CompileStatic
interface Regexes
{
    String invalidNames = "(?!\\b(?:Author|Autowired|Basic|Column|Configuration|Controller|ControllerClass|" +
            "ControllerMethod|DiscriminatorValue|Documented|Entity|Enumerated|IdClass|InitBinder|Interface|" +
            "JoinColumns|JoinColumn|Overrride|ModelAttribute|PackageScope|PreAuthorize|RequestMapping|" +
            "RequestParam|Resource|Retention|SessionAttributes|SmartCacheCmd|SuppressFBWarnings|SuppressWarnings|" +
            "Table|Target|Temporal|XmlAnyElement|XStreamAlias)\\b.*)"
    String validNameChars = NCube.validCubeNameChars
    String bracketMatch = '\\s*\\[.*?:.*?\\]'
    String varMatch = '[^)=]+'

    Pattern importPattern = ~/(?m)^(\s*import\s+(static\s+)?[^;\n"'\/ ]+;?)/
    Pattern grapePattern = ~/(@Grapes\s*?\((?:.*?|\n)+\]\s*?\))/
    Pattern grabPattern = ~/(@(?:Grab|GrabConfig|GrabExclude|GrabResolver)\s*?\(.*?\))/
    Pattern compileStaticPattern = ~/(@(?:CompileStatic)\s+?.*?)/
    Pattern typeCheckPattern = ~/(@(?:TypeChecked)\s*?(?:\(.*?\)|))/
    Pattern inputVar = ~/(?i)([^a-zA-Z0-9_.]|^)input[?]?[.]([a-zA-Z0-9_]+)/

    Pattern scripletPattern = ~/<%(.*?)%>/
    Pattern velocityPattern = ~/[$][{](.*?)[}]/

    Pattern validTenantName = ~/^[0-9A-Za-z-]+$/
    Pattern validBranch = ~/^[0-9A-Za-z-_.]+$/
    Pattern validVersion = ~/^\d+\.\d+\.\d+$/
    Pattern validCubeName = ~/^[$validNameChars]+$/

    Pattern valid2Doubles = ~/^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$/
    Pattern valid3Doubles = ~/^\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*,\s*(-?\d+(?:\.\d+)?)\s*$/

    Pattern groovyAbsRefCubeCellPattern =  ~/([^a-zA-Z0-9_]|^)[$]\s*(?<cubeName>[$validNameChars]+)\s*[(](?<input>$bracketMatch|$varMatch)[)]/
    Pattern groovyAbsRefCubeCellPatternA = ~/([^a-zA-Z0-9_]|^)[$]\s*(?<cubeName>[$validNameChars]+)\s*(?<input>$bracketMatch)/
    Pattern groovyAbsRefCellPattern =  ~/([^a-zA-Z0-9_]|^)[$]\s*[(](?<input>$bracketMatch|$varMatch)[)]/
    Pattern groovyAbsRefCellPatternA = ~/([^a-zA-Z0-9_]|^)[$]\s*(?<input>$bracketMatch)/
    Pattern groovyRelRefCubeCellPattern =  ~/([^a-zA-Z0-9_$]|^)@\s*$invalidNames(?<cubeName>[$validNameChars]+)\s*[(](?<input>$bracketMatch|$varMatch)[)]/
    Pattern groovyRelRefCubeCellPatternA = ~/([^a-zA-Z0-9_$]|^)@\s*(?<cubeName>[$validNameChars]+)[\s]*(?<input>$bracketMatch)/
    Pattern groovyRelRefCellPattern =  ~/([^a-zA-Z0-9_$]|^)@\s*[(](?<input>$bracketMatch|$varMatch)[)]/
    Pattern groovyRelRefCellPatternA = ~/([^a-zA-Z0-9_$]|^)@\s*(?<input>$bracketMatch)/
    Pattern groovyExplicitCubeRefPattern = ~/([^a-zA-Z0-9_$"']|^)getCube\s*[(]\s*['"](?<cubeName>[$validNameChars]+)['"]\s*[)]/
    Pattern groovyExplicitJumpPattern = ~/([^a-zA-Z0-9_$]|^)jump\s*[(]\s*['"](?<cubeName>[$validNameChars]+)['"].*?[)]/
    Pattern groovyExplicitAtPattern = ~/([^a-zA-Z0-9_$'"]|^)at\s*[(].*?,\s*['"](?<cubeName>[$validNameChars]+)['"](.*?)[)]/
    Pattern groovyExplicitGoPattern = ~/([^a-zA-Z0-9_$'"]|^)go\s*[(].*?,\s*['"](?<cubeName>[$validNameChars]+)['"](.*?)[)]/
    Pattern groovyExplicitUsePattern = ~/([^a-zA-Z0-9_$'"]|^)use\s*[(].*?,\s*['"](?<cubeName>[$validNameChars]+)['"](.*?)[)]/

    Pattern cdnUrlPattern = ~/^\/dyn\/([^\/]+)\/(.*)$/

    Pattern hasClassDefPattern = ~/(?s)^(package\s+(?<packageName>[a-zA-Z_0-9$.]+))?(|.*[;\s]+)class\s+(?<className>[a-zA-Z_0-9$.]+).*?\{.*?}.*$/

    Pattern isOraclePattern = ~/(?i)oracle/
    Pattern isHSQLDBPattern = ~/(?i)hsql/
    Pattern isMySQLPattern = ~/(?i)mysql/

    Pattern rangePattern = ~/\s*([^,]+)[,](.*)\s*$/

    Pattern refAppSearchPattern = ~/${REF_APP}/
}
