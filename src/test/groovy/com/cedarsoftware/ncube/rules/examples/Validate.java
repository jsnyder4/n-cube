package com.cedarsoftware.ncube.rules.examples;

import com.cedarsoftware.ncube.rules.BusinessRule;
import com.cedarsoftware.ncube.rules.Documentation;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class Validate extends BusinessRule
{
    private Map policy;
    private Map line;
    private Set<String> specialStates = Stream.of("OH", "IN", "KY").collect(Collectors.toCollection(HashSet::new));

    public Validate(Map root)
    {
        super(root);
        policy = (Map) root.get("policy");
        line = (Map) policy.get("line");
    }

    @Documentation("Policy must have effectiveDate")
    void validateSimple()
    {
        if (!policy.containsKey("effectiveDate"))
        {
            addError("Invalid Request", "POL_DATE", "Policy effectiveDate is required");
        }
    }

    @Documentation("Line type must equal 'WorkComp'")
    void validateLine()
    {
        if ("WorkComp" != line.get("type"))
        {
            addError("Invalid Request", "LINE_TYPE", "Line type must be 'WorkComp'");
        }
    }

    @Documentation("Line sub type FIXED cannot be written in states: OH, KY, IN")
    void validateComplex()
    {
        String policyState = (String) policy.get("policyState");
        String lineSubType = (String) line.get("subType");
        if ("FIXED".equals(lineSubType) && specialStates.contains(policyState))
        {
            addError("Invalid Request", "LINE_SUB_TYPE", "Line sub type FIXED cannot be written in states: OH, KY, IN");
        }
    }
}
