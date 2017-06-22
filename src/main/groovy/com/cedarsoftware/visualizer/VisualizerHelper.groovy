package com.cedarsoftware.visualizer

import com.cedarsoftware.ncube.ApplicationID
import com.cedarsoftware.ncube.NCubeRuntimeClient
import com.cedarsoftware.ncube.exception.CoordinateNotFoundException
import com.cedarsoftware.ncube.exception.InvalidCoordinateException
import groovy.transform.CompileStatic

import static com.cedarsoftware.ncube.NCubeAppContext.getNcubeRuntime
import static com.cedarsoftware.visualizer.VisualizerConstants.DOUBLE_BREAK

/**
 * Provides helper methods to handle exceptions occurring during the execution
 * of n-cube cells for the purpose of producing a visualization.
 */

@CompileStatic
class VisualizerHelper
{
	protected static ApplicationID appId
	
	VisualizerHelper(NCubeRuntimeClient runtimeClient, ApplicationID appId)
	{
		this.appId = appId
	}
	
	protected static boolean handleUnboundScope(VisualizerInfo visInfo, VisualizerRelInfo relInfo, List<MapEntry> unboundAxesList)
	{
		boolean hasUnboundScopeToInclude
		if (relInfo.targetId == visInfo.selectedNodeId && unboundAxesList)
		{
			unboundAxesList.each { MapEntry unboundAxis ->
				String cubeName = unboundAxis.key as String
				MapEntry axisEntry = unboundAxis.value as MapEntry
				String scopeKey = axisEntry.key as String
				if (!relInfo.isDerivedScopeKey(visInfo, scopeKey))
				{
					relInfo.setLoadAgain(visInfo, scopeKey)
					relInfo.addNodeScope(cubeName, scopeKey, true)
					hasUnboundScopeToInclude = true
				}
			}
		}
		return hasUnboundScopeToInclude
	}

	protected static StringBuilder handleCoordinateNotFoundException(CoordinateNotFoundException e, VisualizerInfo visInfo, VisualizerRelInfo relInfo)
	{
		StringBuilder sb = new StringBuilder()
		String cubeName = e.cubeName
		String scopeKey = e.axisName
		if (cubeName && scopeKey)
		{
			if (relInfo.targetId == visInfo.selectedNodeId)
			{
				relInfo.setLoadAgain(visInfo, scopeKey)
				relInfo.addNodeScope(cubeName, scopeKey, false, false, e.coordinate)
			}
			return sb
		}
		else
		{
			sb.append("Unable to handle CoordinateNotFoundException ${DOUBLE_BREAK}")
			return sb.append(handleException(e as Exception))
		}
	}

	protected static StringBuilder handleInvalidCoordinateException(InvalidCoordinateException e, VisualizerInfo visInfo, VisualizerRelInfo relInfo, Set mandatoryScopeKeys)
	{
		StringBuilder sb = new StringBuilder()
		Set<String> missingScopeKeys = findMissingScope(relInfo.availableTargetScope, e.requiredKeys, mandatoryScopeKeys)
		if (missingScopeKeys)
		{
			if (relInfo.targetId == visInfo.selectedNodeId)
			{
				missingScopeKeys.each { String scopeKey ->
					relInfo.setLoadAgain(visInfo, scopeKey)
					relInfo.addNodeScope(e.cubeName, scopeKey)
				}
			}
			return sb
		}
		else
		{
			sb = new StringBuilder("Unable to handle InvalidCoordinateException ${DOUBLE_BREAK}")
			return sb.append(handleException(e as Exception))
		}
	}

	protected static String handleException(Throwable e)
	{
		Throwable t = getDeepestException(e)
		return getExceptionMessage(t, e)
	}

	protected static Throwable getDeepestException(Throwable e)
	{
		while (e.cause != null)
		{
			e = e.cause
		}
		return e
	}

	private static Set<String> findMissingScope(Map<String, Object> scope, Set<String> requiredKeys, Set mandatoryScopeKeys)
	{
		return requiredKeys.findAll { String scopeKey ->
			!mandatoryScopeKeys.contains(scopeKey) && (scope == null || !scope.containsKey(scopeKey))
		}
	}

	protected static String getExceptionMessage(Throwable t, Throwable e)
	{
		"""\
<b>Message:</b> ${DOUBLE_BREAK}${e.message}${DOUBLE_BREAK}<b>Root cause: </b>\
${DOUBLE_BREAK}${t.toString()}${DOUBLE_BREAK}<b>Stack trace: </b>${DOUBLE_BREAK}${ncubeRuntime.getTestCauses(t)}"""
	}
}