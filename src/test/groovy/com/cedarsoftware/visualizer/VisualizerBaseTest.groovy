package com.cedarsoftware.visualizer

import com.cedarsoftware.ncube.*
import groovy.transform.CompileStatic
import org.junit.After
import org.junit.Before
import org.junit.Ignore

import static com.cedarsoftware.ncube.NCubeAppContext.ncubeRuntime

@CompileStatic
@Ignore
class VisualizerBaseTest extends NCubeBaseTest implements VisualizerConstants, VisualizerTestConstants
{
    protected static final String TEST_APP_NAME = 'test.visualizer'
    protected static final String TEST_APP_VERSION = '1.0.9'
    protected static final ApplicationID appId = new ApplicationID(ApplicationID.DEFAULT_TENANT, TEST_APP_NAME, TEST_APP_VERSION, ReleaseStatus.RELEASE.name(), ApplicationID.HEAD)
    protected static List<NCube> testCubes = []

    protected Visualizer visualizer
    protected Map returnMap
    protected VisualizerInfo visInfo
    protected Set messages
    protected Map<Long, Map<String, Object>> nodes
    protected Map<Long, Map<String, Object>> edges
    protected Map<String, Object> selectedNode

    protected static final String DEFAULT_SCOPE_DATE = RpmVisualizerRelInfo.DATE_TIME_FORMAT.format(new Date())

    @Before
    void setup()
    {
        addTestCubesToCache()
        visualizer = getVisualizer()
        returnMap = null
        visInfo = null
        messages = null
        nodes = null
        edges = null
        selectedNode = null
    }

    @After
    void teardown()
    {
        testCubes = []
    }

    protected Visualizer getVisualizer()
    {
        return new Visualizer(ncubeRuntime)
    }

    protected VisualizerRelInfo getVisualizerRelInfo()
    {
        return new VisualizerRelInfo(ncubeRuntime, appId)
    }

    protected Map loadGraph(Map options, boolean hasMessages = false)
    {
        visInfo?.nodes = [:]
        visInfo?.edges = [:]
        Map returnMap = visualizer.loadGraph(appId, options)
        visInfo = returnMap.visInfo as VisualizerInfo
        messages = visInfo.messages
        if (!hasMessages)
        {
            assert !messages
        }
        nodes = visInfo.nodes as Map
        edges = visInfo.edges as Map
        return nodes[1l]
    }

    protected Map loadScopeChange(Map node, boolean hasMessages = false)
    {
        visInfo.selectedNodeId = node.id as Long
        Map returnMap = visualizer.loadScopeChange(appId, [visInfo: visInfo])
        visInfo = returnMap.visInfo as VisualizerInfo
        messages = visInfo.messages
        if (!hasMessages)
        {
            assert !messages
        }
        nodes = visInfo.nodes as Map
        edges = visInfo.edges as Map
        return nodes[node.id as Long]
    }

    protected Map loadNodeDetails(Map node, boolean hasMessages = false)
    {
        visInfo.selectedNodeId = node.id as Long
        Map returnMap = visualizer.loadNodeDetails(appId, [visInfo: visInfo])
        visInfo = returnMap.visInfo as VisualizerInfo
        messages = visInfo.messages
        if (!hasMessages)
        {
            assert !messages
        }
        nodes = visInfo.nodes as Map
        edges = visInfo.edges as Map
        return nodes[node.id as Long]
    }

    protected static void checkScopePromptTitle(Map node, String scopeKey, boolean required, String cubeNames = null, boolean derivedScopeKey = false, boolean unusedScopeKey = false)
    {
        String nodeDetails = node.details as String
        if (derivedScopeKey)
        {
            assert nodeDetails.contains("""title="Scope key ${scopeKey} was added by the visualizer and may not be changed""")
        }
        else if (unusedScopeKey)
        {
            assert nodeDetails.contains("""title="Scope key ${scopeKey} was added for a source class of this class, but is not used by this class""")
        }
        else if (required)
        {
            assert nodeDetails.contains("""title="Scope key ${scopeKey} is required to load""")
        }
        else
        {
            assert nodeDetails.contains("""title="Scope key ${scopeKey} is optional to load""")
        }
        if (cubeNames)
        {
            assert nodeDetails.contains(cubeNames)
        }
    }

    protected static void checkScopePromptDropdown(Map node, String scopeKey, String selectedScopeValue, List<String> availableScopeValues, List<String> unavailableScopeValues, String valueClass = '', boolean isTopNode = false, boolean isDerivedScopeKey = false)
    {
        String nodeDetails = node.details as String
        String placeHolder = availableScopeValues ? SELECT_OR_ENTER_VALUE : ENTER_VALUE
        String topNodeClass = isTopNode ? DETAILS_CLASS_TOP_NODE : ''
        String disabled = isDerivedScopeKey ? 'disabled="disabled"' : ''
        assert nodeDetails.contains("""<input id="${scopeKey}" value="${selectedScopeValue}" ${disabled} placeholder="${placeHolder}" class="scopeInput form-control ${valueClass} ${topNodeClass}""")
        if (!availableScopeValues && !unavailableScopeValues)
        {
            assert !nodeDetails.contains("""<li id="${scopeKey}:""")
            return
        }

        availableScopeValues.each{String scopeValue ->
            assert nodeDetails.contains("""<li id="${scopeKey}: ${scopeValue}" class="scopeClick ${topNodeClass}""")
        }
        unavailableScopeValues.each{String scopeValue ->
            assert !nodeDetails.contains("""<li id="${scopeKey}: ${scopeValue}" class="scopeClick ${topNodeClass}""")
        }
    }

    protected static void checkNoScopePrompt(Map node, String scopeKey = '')
    {
        String nodeDetails = node.details as String
        assert !nodeDetails.contains("""title="${scopeKey}""")
        assert !nodeDetails.contains("""<input id="${scopeKey}""")
        assert !nodeDetails.contains("""<li id="${scopeKey}""")
    }

    //TODO: Move methods below into NCubeCleanupBaseTest?

    protected void addTestCubesToCache()
    {
        if (!testCubes)
        {
            testCubes = getCubesFromResource(testCubesNames)
        }
        addCubes(testCubes)
    }

    protected static List<NCube> getCubesFromResource(List<String> fileNames)
    {
        List<NCube> cubes = []
        fileNames.each { String fileName ->
            String json = NCubeRuntime.getResourceAsString(fileName)
            NCube cube = NCube.fromSimpleJson(json)
            cube.applicationID = appId
            cubes << cube
        }
        return cubes
    }

    protected static void addCubes(List<NCube> cubes)
    {
        cubes.each {NCube cube ->
            ncubeRuntime.addCube(cube)
        }
    }

    List<String> getTestCubesNames()
    {
        return []
    }
}
