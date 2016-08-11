package com.cedarsoftware.ncube
import com.cedarsoftware.util.StringUtilities
import groovy.transform.CompileStatic
import ncube.grv.exp.NCubeGroovyExpression
import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

import java.util.regex.Matcher
/**
 * This class is used to hold Groovy Expressions.  This means that
 * the code can start without any class or method signatures.  For
 * example, an expression could look like this:<pre>
 *    1) input.resource.KEY == 10
 *    2) $([BU:'agr',state:'OH') * 1.045
 *    3) return input.state == 'OH' ? 1.0 : 2.0
 *    4) output.result = 'answer computed'; return 1.4
 *
 * </pre>
 * Of course, Java syntax can be used, however, there are many nice
 * short-hands you can use if you know the Groovy language additions.
 * For example, Maps can be accessed like this (the following 3 are
 * equivalent): <pre>
 *    1) input.get('BU')
 *    2) input['BU']
 *    3) input.BU
 * </pre>
 * There are variables available to you to access in your expression
 * supplied by ncube.  <b>input</b> which is the input coordinate Map
 * that was used to get to the the cell containing the expression.
 * <b>output</b> is a Map that your expression can write to.  This allows
 * the program calling into the ncube to get multiple return values, with
 * possible structure to each one (a graph return).  <b>ncube</b> is the
 * current ncube of the cell containing the expression.  <b>ncubeMgr</b>
 * is a reference to the NCubeManager class so that you can access other
 * ncubes. <b>stack</b> which is a List of StackEntry's where element 0 is
 * the StackEntry for the currently executing cell.  Element 1 would be the
 * cell that called into the current cell (if it was during the same execution
 * cycle).  The StackEntry contains the name of the cube as one field, and
 * the coordinate that called into this cell.
 *
 * @author John DeRegnaucourt
 * Copyright (c) 2012-2016, John DeRegnaucourt.  All rights reserved.
 */
@CompileStatic
class GroovyExpression extends GroovyBase
{
    public static final String EXP_IMPORTS = "exp.imports"
    public static final String EXP_CLASS = "exp.class"
    public static final String SYS_PROPERTY = "sys.property"
    private static final Logger LOG = LogManager.getLogger(GroovyExpression.class)

    //  Private constructor only for serialization.
    private GroovyExpression() { }
    GroovyExpression(String cmd, String url, boolean cache)
    {
        super(cmd, url, cache)
    }

    protected String buildGroovy(Map<String, Object> ctx, String theirGroovy)
    {
        Matcher m = Regexes.hasClassDefPattern.matcher(theirGroovy)
        if (m.find())
        {   // If they include a class ... { in their source, then we do not add the 'apartment' around the content.
            return theirGroovy
        }

        StringBuilder groovyCodeWithoutImportsAndAnnotations = new StringBuilder()
        Set<String> lines = extractImportsAndAnnotations(theirGroovy, groovyCodeWithoutImportsAndAnnotations)
        StringBuilder groovy = new StringBuilder("""\
package ncube.grv.exp
import com.cedarsoftware.ncube.*
import com.cedarsoftware.ncube.exception.*
import com.cedarsoftware.ncube.formatters.*
import com.cedarsoftware.ncube.proximity.*
import com.cedarsoftware.ncube.util.*
import com.cedarsoftware.util.*
import com.cedarsoftware.util.io.*
import com.google.common.base.*
import com.google.common.collect.*
import com.google.common.net.*
""")

        // Attempt to load sys.prototype cube
        // If loaded, add the import statement list from this cube to the list of imports for generated cells
        String expClassName = null
        NCube ncube = getNCube(ctx)

        if (!NCubeManager.SYS_PROTOTYPE.equalsIgnoreCase(ncube.name))
        {
            NCube prototype = getSysPrototype(ncube.applicationID)

            if (prototype != null)
            {
                addPrototypeExpImports(ctx, groovy, prototype)

                // Attempt to find class to inherit from
                expClassName = getPrototypeExpClass(ctx, prototype)
            }
        }

        // Add in import and annotations extracted from the expression cell
        for (String line : lines)
        {
            groovy.append(line)
            groovy.append('\n')
        }

        if (StringUtilities.isEmpty(expClassName))
        {
            expClassName = "ncube.grv.exp.NCubeGroovyExpression"
        }

        String className = "N_" + cmdHash
        groovy.append("class ")
        groovy.append(className)
        groovy.append(" extends ")
        groovy.append(expClassName)
        groovy.append("\n{\n\tdef run()\n\t{\n\t")
        groovy.append(groovyCodeWithoutImportsAndAnnotations.toString())
        groovy.append("\n\t}\n}")
        return groovy.toString()
    }

    static NCube getSysPrototype(ApplicationID appId)
    {
        try
        {
            return NCubeManager.getCube(appId, NCubeManager.SYS_PROTOTYPE)
        }
        catch (Exception e)
        {
            handleException(e, "Exception occurred fetching " + NCubeManager.SYS_PROTOTYPE)
            return null
        }
    }

    protected static void handleException(Exception e, String msg)
    {
        if (LOG.isDebugEnabled())
        {
            LOG.debug(msg, e)
        }
        else
        {
            LOG.info(msg + ", " + e.getMessage())
        }
    }

    protected static void addPrototypeExpImports(Map<String, Object> ctx, StringBuilder groovy, NCube prototype)
    {
        try
        {
            Map<String, Object> input = getInput(ctx)
            input.put(SYS_PROPERTY, EXP_IMPORTS)
            Object importList = prototype.getCell(input)
            if (importList instanceof Collection)
            {
                Collection<String> impList = (Collection) importList
                for (String importLine : impList)
                {
                    groovy.append("import ")
                    groovy.append(importLine)
                    groovy.append('\n')
                }
            }
        }
        catch (Exception e)
        {
            handleException(e, "Exception occurred fetching imports from " + NCubeManager.SYS_PROTOTYPE)
        }
    }

    protected static String getPrototypeExpClass(Map<String, Object> ctx, NCube prototype)
    {
        try
        {
            Map input = getInput(ctx)
            input[SYS_PROPERTY] = EXP_CLASS
            Object className = prototype.getCell(input)
            if (className instanceof String && StringUtilities.hasContent((String)className))
            {
                return (String) className
            }
        }
        catch (Exception e)
        {
            handleException(e, "Exception occurred fetching base class for Groovy Expression cells from " + NCubeManager.SYS_PROTOTYPE)
        }
        return null
    }

    protected Object invokeRunMethod(NCubeGroovyExpression instance, Map<String, Object> ctx) throws Throwable
    {
        // If 'around' Advice has been added to n-cube, invoke it before calling Groovy expression's run() method
        NCube ncube = getNCube(ctx)
        Map input = getInput(ctx)
        Map output = getOutput(ctx)
        List<Advice> advices = ncube.getAdvices('run')
        for (Advice advice : advices)
        {
            if (!advice.before(null, ncube, input, output))
            {
                return null
            }
        }

        Throwable t = null
        Object ret = null

        try
        {
            ret = instance.run()
        }
        catch (ThreadDeath e)
        {
            throw e
        }
        catch (Throwable e)
        {   // Save exception
            t = e
        }

        // If 'around' Advice has been added to n-cube, invoke it after calling Groovy expression's run() method
        final int len = advices.size()
        for (int i = len - 1; i >= 0; i--)
        {
            Advice advice = advices.get(i)
            try
            {
                advice.after(null, ncube, input, output, ret, t)  // pass exception (t) to advice (or null)
            }
            catch (ThreadDeath e)
            {
                throw e
            }
            catch (Throwable e)
            {
                LOG.error("An exception occurred calling 'after' advice: " + advice.getName(), e)
            }
        }
        if (t == null)
        {
            return ret
        }
        throw t
    }
}
