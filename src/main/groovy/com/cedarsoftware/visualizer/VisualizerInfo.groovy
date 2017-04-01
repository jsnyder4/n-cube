package com.cedarsoftware.visualizer

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.NCubeRuntimeClient
import com.cedarsoftware.util.CaseInsensitiveMap
import com.cedarsoftware.util.CaseInsensitiveSet
import groovy.transform.CompileStatic

import static com.cedarsoftware.visualizer.VisualizerConstants.JSON_FILE_PREFIX
import static com.cedarsoftware.visualizer.VisualizerConstants.JSON_FILE_SUFFIX
import static com.cedarsoftware.visualizer.VisualizerConstants.VISUALIZER_CONFIG_CUBE_NAME
import static com.cedarsoftware.visualizer.VisualizerConstants.VISUALIZER_CONFIG_NETWORK_OVERRIDES_CUBE_NAME
import static com.cedarsoftware.visualizer.VisualizerConstants.CONFIG_ITEM
import static com.cedarsoftware.visualizer.VisualizerConstants.CONFIG_ALL_TYPES
import static com.cedarsoftware.visualizer.VisualizerConstants.CUBE_TYPE
import static com.cedarsoftware.visualizer.VisualizerConstants.CUBE_TYPE_DEFAULT
import static com.cedarsoftware.visualizer.VisualizerConstants.CONFIG_NETWORK_OVERRIDES_BASIC
import static com.cedarsoftware.visualizer.VisualizerConstants.CONFIG_NETWORK_OVERRIDES_FULL
import static com.cedarsoftware.visualizer.VisualizerConstants.CONFIG_ALL_GROUPS
import static com.cedarsoftware.visualizer.VisualizerConstants.CONFIG_GROUP_SUFFIX


/**
 * Provides information to visualize n-cubes.
 */

@CompileStatic
class VisualizerInfo
{
    protected ApplicationID appId
    protected Long selectedNodeId
    protected Map<Long, Map<String, Object>> nodes
    protected Map<Long, Map<String, Object>> edges

    protected Map<String, Object> inputScope

    protected long maxLevel
    protected long nodeIdCounter
    protected long edgeIdCounter
    protected Map<Integer, Integer> levelCumulativeNodeCount
    protected long defaultLevel
    protected String cellValuesLabel
    protected String nodeLabel

    protected  Map<String,String> allGroups
    protected Set<String> allGroupsKeys
    protected String groupSuffix
    protected Set<String> availableGroupsAllLevels
    protected Set<String> messages

    protected Map<String, Object> networkOverridesBasic
    protected Map<String, Object> networkOverridesFull

    protected Map<String, Set<String>> requiredScopeKeysByCube = [:]

    protected Map<String, List<String>> typesToAddMap = [:]
    protected static NCubeRuntimeClient runtimeClient

    VisualizerInfo(){}

    VisualizerInfo(NCubeRuntimeClient runtimeClient, ApplicationID applicationID)
    {
        this.runtimeClient = runtimeClient
        appId = applicationID
        loadConfigurations(cubeType)
    }

    protected void init(Map options = null)
    {
        inputScope = options?.scope as CaseInsensitiveMap ?: new CaseInsensitiveMap()
        messages = new LinkedHashSet()
        nodes = [:]
        edges = [:]
        nodeIdCounter = 1
        edgeIdCounter = 0
        selectedNodeId = 1
        maxLevel = 1
        availableGroupsAllLevels = new LinkedHashSet()
    }

    protected void initScopeChange()
    {
        if (1l == selectedNodeId)
        {
            init()
        }
        else
        {
            messages = new LinkedHashSet()
            nodes.remove(selectedNodeId)
            removeSourceEdges()
            removeTargets(edges)
            removeTargets(nodes)
            maxLevel = 1
            availableGroupsAllLevels = new LinkedHashSet()
        }
    }

    private void removeTargets(Map<Long, Map> nodes)
    {
        List<Long> toRemove = []
        nodes.each{Long id, Map node ->
            List<Long> sourceTrail = node.sourceTrail as List
            if (sourceTrail.contains(selectedNodeId))
            {
                toRemove << (node.id as Long)
            }
        }
        nodes.keySet().removeAll(toRemove)
    }

    private void removeSourceEdges()
    {
        List<Long> toRemove = []
        edges.each{Long id, Map edge ->
            if (selectedNodeId == edge.to as Long)
            {
                toRemove << (edge.id as Long)
            }
        }
        edges.keySet().removeAll(toRemove)
    }

    protected String getCubeType()
    {
        return CUBE_TYPE_DEFAULT
    }

    protected NCube loadConfigurations(String cubeType)
    {
        NCube configCube = runtimeClient.getNCubeFromResource(appId, JSON_FILE_PREFIX + VISUALIZER_CONFIG_CUBE_NAME + JSON_FILE_SUFFIX)
        NCube networkConfigCube = runtimeClient.getNCubeFromResource(appId, JSON_FILE_PREFIX + VISUALIZER_CONFIG_NETWORK_OVERRIDES_CUBE_NAME + JSON_FILE_SUFFIX)

        networkOverridesBasic = networkConfigCube.getCell([(CONFIG_ITEM): CONFIG_NETWORK_OVERRIDES_BASIC, (CUBE_TYPE): cubeType]) as Map
        networkOverridesFull = networkConfigCube.getCell([(CONFIG_ITEM): CONFIG_NETWORK_OVERRIDES_FULL, (CUBE_TYPE): cubeType]) as Map
        allGroups = configCube.getCell([(CONFIG_ITEM): CONFIG_ALL_GROUPS, (CUBE_TYPE): cubeType]) as Map
        allGroupsKeys = new CaseInsensitiveSet(allGroups.keySet())
        String groupSuffix = configCube.getCell([(CONFIG_ITEM): CONFIG_GROUP_SUFFIX, (CUBE_TYPE): cubeType]) as String
        this.groupSuffix = groupSuffix ?: ''
        loadTypesToAddMap(configCube)
        cellValuesLabel = getCellValuesLabel()
        nodeLabel = getNodeLabel()
        return configCube
    }

    protected List getTypesToAdd(String group)
    {
        return typesToAddMap[allGroups[group]]
    }

    protected void loadTypesToAddMap(NCube configCube)
    {
        typesToAddMap = [:]
        Set<String> allTypes = configCube.getCell([(CONFIG_ITEM): CONFIG_ALL_TYPES, (CUBE_TYPE): cubeType]) as Set
        allTypes.each{String type ->
            Map.Entry<String, String> entry = allGroups.find{ String key, String value ->
                value == type
            }
            typesToAddMap[entry.value] = allTypes as List
        }
    }

    protected void calculateAggregateInfo()
    {
        //Determine maxLevel and availableGroupsAllLevels
        Map<Integer, Integer> levelNodeCount = [:]
        nodes.values().each{Map node ->
            availableGroupsAllLevels << (node.group as String) - groupSuffix
            long nodeLevel = node.level as long
            maxLevel = maxLevel < nodeLevel ? nodeLevel : maxLevel
            int nodeCountAtLevel = levelNodeCount[nodeLevel as int] ?: 0
            nodeCountAtLevel++
            levelNodeCount[nodeLevel as int] = nodeCountAtLevel
        }

        //Determine cumulative node count at each level + determine defaultLevel
        levelCumulativeNodeCount = [:]
        for (int i = 1; i < levelNodeCount.size() + 1; i++)
        {
            if (1 == i)
            {
                levelCumulativeNodeCount[i] = levelNodeCount[i]
            }
            else
            {
                levelCumulativeNodeCount[i] = levelCumulativeNodeCount[i - 1] + levelNodeCount[i]
            }
            if (levelCumulativeNodeCount[i] <= 100 )
            {
                defaultLevel = i as long
            }
        }
    }

    protected String getLoadTarget(boolean showingHidingCellValues)
    {
        return showingHidingCellValues ? "${cellValuesLabel}" : "the ${nodeLabel}"
    }

    protected String getNodeLabel()
    {
        'n-cube'
    }

    protected String getNodesLabel()
    {
        return 'cubes'
    }

    protected String getCellValuesLabel()
    {
        return 'cell values'
    }

}