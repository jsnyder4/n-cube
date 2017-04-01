package com.cedarsoftware.visualizer

import com.cedarsoftware.ncube.NCube
import com.cedarsoftware.ncube.NCubeRuntimeClient
import groovy.transform.CompileStatic

/**
 * Processes an rpm class and all its referenced rpm classes and provides information to
 * visualize the classes and their relationships.
 */

@CompileStatic
class RpmVisualizer extends Visualizer implements RpmVisualizerConstants
{
	private RpmVisualizerHelper helper

	RpmVisualizer(NCubeRuntimeClient runtimeClient)
	{
		super(runtimeClient)
	}

	@Override
	protected VisualizerInfo getVisualizerInfo(Map options)
	{
		RpmVisualizerInfo visInfo
		Object optionsVisInfo = options.visInfo
		if (optionsVisInfo && optionsVisInfo instanceof RpmVisualizerInfo)
		{
			visInfo = optionsVisInfo as RpmVisualizerInfo
			visInfo.appId = appId
		}
		else
		{
			visInfo = new RpmVisualizerInfo(runtimeClient, appId)
		}
		return visInfo
	}

	protected VisualizerRelInfo getVisualizerRelInfo()
	{
		return new RpmVisualizerRelInfo(runtimeClient, appId)
	}

	@Override
	protected void processCube(VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		if (relInfo.targetCube.name.startsWith(RPM_CLASS))
		{
			processClassCube((RpmVisualizerInfo) visInfo, (RpmVisualizerRelInfo) relInfo)
		}
		else
		{
			processEnumCube((RpmVisualizerInfo) visInfo, (RpmVisualizerRelInfo) relInfo)
		}
	}

	private void processClassCube(RpmVisualizerInfo visInfo, RpmVisualizerRelInfo relInfo)
	{
		boolean cubeLoaded
		String targetCubeName = relInfo.targetCube.name

		cubeLoaded = relInfo.loadCube(visInfo)

		if (relInfo.sourceCube)
		{
			Long edgeId = visInfo.edgeIdCounter += 1
			visInfo.edges[edgeId] = relInfo.createEdge(edgeId)
		}

		if (!visited.add(targetCubeName + relInfo.availableTargetScope.toString()))
		{
			return
		}

		visInfo.nodes[relInfo.targetId] = relInfo.createNode(visInfo)

		if (cubeLoaded)
		{
			relInfo.targetTraits.each { String targetFieldName, Map targetTraits ->
				if (CLASS_TRAITS != targetFieldName)
				{
					String targetFieldRpmType = targetTraits[R_RPM_TYPE]
					if (!visualizerHelper.isPrimitive(targetFieldRpmType))
					{
						String nextTargetCubeName = ""
						if (targetTraits.containsKey(V_ENUM))
						{
							nextTargetCubeName = RPM_ENUM_DOT + targetTraits[V_ENUM]
						}
						else if (targetFieldRpmType)
						{
							nextTargetCubeName = RPM_CLASS_DOT + targetFieldRpmType
						}
						addToStack(visInfo, relInfo, nextTargetCubeName, targetFieldRpmType, targetFieldName)
					}
				}
			}
		}
	}

	private void processEnumCube(RpmVisualizerInfo visInfo, RpmVisualizerRelInfo relInfo)
	{
		String group = UNSPECIFIED_ENUM
		String targetCubeName = relInfo.targetCube.name

		boolean cubeLoaded = relInfo.loadCube(visInfo)
		if (cubeLoaded)
		{
			relInfo.targetTraits.each { String targetFieldName, Map targetTraits ->
				if (CLASS_TRAITS != targetFieldName)
				{
					String nextTargetCubeName = relInfo.getNextTargetCubeName(targetFieldName)
					if (nextTargetCubeName)
					{
						RpmVisualizerRelInfo nextRelInfo = addToStack(visInfo, relInfo, nextTargetCubeName, relInfo.sourceFieldRpmType, targetFieldName)
						if (nextRelInfo && group == UNSPECIFIED_ENUM)
						{
							group = relInfo.getGroupName(visInfo, nextTargetCubeName) + visInfo.groupSuffix
						}
					}
				}
			}
		}

		if (relInfo.sourceCube)
		{
			Long edgeId = visInfo.edgeIdCounter += 1
			visInfo.edges[edgeId] = relInfo.createEdge(edgeId)
		}

		if (!visited.add(targetCubeName + relInfo.availableTargetScope.toString()))
		{
			return
		}

		visInfo.nodes[relInfo.targetId] = relInfo.createNode(visInfo, group)
	}

	private RpmVisualizerRelInfo addToStack(RpmVisualizerInfo visInfo, RpmVisualizerRelInfo relInfo, String nextTargetCubeName, String rpmType, String targetFieldName)
	{
		RpmVisualizerRelInfo nextRelInfo = new RpmVisualizerRelInfo(runtimeClient, appId)
		super.addToStack(visInfo, relInfo, nextRelInfo, nextTargetCubeName)
		NCube nextTargetCube = nextRelInfo.targetCube
		if (nextTargetCube)
		{
			nextRelInfo.populateScopeRelativeToSource(rpmType, targetFieldName, relInfo.availableTargetScope)
			nextRelInfo.sourceFieldName = targetFieldName
			nextRelInfo.sourceFieldRpmType = rpmType
			nextRelInfo.sourceTraits = relInfo.targetTraits
			nextRelInfo.showCellValuesLink = false
		}
		return nextRelInfo
	}

	@Override
	protected NCube isValidStartCube(VisualizerInfo visInfo, String cubeName)
	{
		NCube cube = super.isValidStartCube(visInfo, cubeName)
		if (!cube)
		{
			return null
		}

		if (cubeName.startsWith(RPM_CLASS_DOT))
		{
			if (!cube.getAxis(AXIS_FIELD) || !cube.getAxis(AXIS_TRAIT) )
			{
				visInfo.messages << "Cube ${cubeName} is not a valid rpm class since it does not have both a field axis and a traits axis.".toString()
				return null
			}
		}
		/* TODO: Add ability to start rpm visualization with an rpm enum class
		else if (cubeName.startsWith(RPM_ENUM_DOT))
		{
			if (!cube.getAxis(AXIS_NAME) || !cube.getAxis(AXIS_TRAIT) )
			{
				visInfo.messages << "Cube ${cubeName} is not a valid rpm enum since it does not have both a name axis and a traits axis.".toString()
				return null
			}
		}*/
		else
		{
			visInfo.messages << "Starting cube for visualization must begin with 'rpm.class', ${cubeName} does not.".toString()
			return null
		}
		return cube
	}

	@Override
	protected RpmVisualizerHelper getVisualizerHelper()
	{
		helper =  new RpmVisualizerHelper(runtimeClient, appId)
	}
}