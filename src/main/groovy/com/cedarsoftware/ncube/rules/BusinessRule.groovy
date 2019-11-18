package com.cedarsoftware.ncube.rules

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.util.Converter
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

import static com.cedarsoftware.ncube.NCubeAppContext.getNcubeRuntime

@Slf4j
@CompileStatic
class BusinessRule
{
    List<RulesError> errors = []
    private Object root
    Map input
    Map output
    ApplicationID appId

    @SuppressWarnings(value = "unused")
    private BusinessRule() {}

    BusinessRule(Object root)
    {
        this.root = root
    }

    Object getRoot()
    {
        return root
    }

    /**
     * Add an error to a list as rules get executed
     * @param category String Group errors by categories if necessary
     * @param code String Unique identifier for specific problems
     * @param message String English readable message with as much context as possible for fixing the error
     */
    void addError(String category, String code, String message)
    {
        RulesError e = new RulesError(category, code, message)
        errors.add(e)
    }

    /**
     * Return a List<Map> from field when the value of the field can either be a List or a Map.
     * This method is useful in conjunction with the json-java library https://github.com/stleary/JSON-java
     * When converting from xml to json, the library will create an object instead of an array if there is only one "instance"
     * of the given object.
     * @param object Map representing a json object
     * @param field String
     * @return List<Map> from either a List<Map> or a Map
     */
    List<Map> getMapOrList(Map object, String field)
    {
        if (!object.containsKey(field))
        {
            addError('Invalid Request', 'INVALID', "Field: ${field} does not exist on map: ${object}")
            return []
        }

        def value = object[field]
        if (value instanceof Map)
        {
            return [(Map)value]
        }
        else if (value instanceof List<Map>)
        {
            return value
        }
        else
        {
            addError('Invalid Request', 'INVALID', "Value for field: ${field} is not a List or a Map on map: ${object}")
            return []
        }
    }

    /**
     * Attempt to parse any Object into a Date. Instead of failing with an exception, add an error to
     * List<RulesError> errors and return null
     * @param date Object representing a date
     * @param code String error code to add to List<RulesError> errors
     * @param message String error message to add to List<RulesError> errors
     * @return Date or null if failed to parse
     */
    Date safeParseDate(Object date, String code, String message)
    {
        try
        {
            return Converter.convertToDate(date)
        }
        catch (Exception e)
        {
            log.info("${message} : ${e.message}")
            addError('Invalid Request', code, message)
            return null
        }
    }

    /**
     * Return the value of an externalized constant. Useful for externalizing constant values such as Sets and Lists from code.
     * In order to use this method, create an NCube named lookup.constants. Create an axis named 'name' with column names
     * that represent the constant names. Fill in the cells for each column with the value associated to the constant.
     * To get a constant, pass the name of the column (constant name) to this method to return the value.
     * @param name
     * @return NCube cell value
     */
    Object getConstant(String name)
    {
        NCube constants = ncubeRuntime.getCube(appId, 'lookup.constants')
        if (constants == null)
        {
            throw new IllegalStateException("NCube: lookup.constants does not exist in appId: ${appId}. To use BusinessRule.getConstant(), please create it.")
        }
        return constants.getCell([name: name])
    }

}
