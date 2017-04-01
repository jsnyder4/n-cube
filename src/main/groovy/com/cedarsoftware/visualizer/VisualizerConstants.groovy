package com.cedarsoftware.visualizer

import com.cedarsoftware.util.SafeSimpleDateFormat
import groovy.transform.CompileStatic

/**
 * Provides constants for the visualizer
 */

@CompileStatic
interface VisualizerConstants
{
	final String SPACE = '&nbsp;'
	final String BREAK = '<br>'
	final String DOUBLE_BREAK = "${BREAK}${BREAK}"

	final String UNSPECIFIED = 'UNSPECIFIED'
	final String NCUBE = 'NCUBE'
	final String RULE_NCUBE = 'RULE_NCUBE'

	final String HTTP = 'http:'
	final String HTTPS = 'https:'
	final String FILE = 'file:'

	final String DEFAULT = 'Default'
	final String JSON_FILE_PREFIX = 'config/'
	final String JSON_FILE_SUFFIX = '.json'
	final String VISUALIZER_CONFIG_CUBE_NAME = 'VisualizerConfig'
	final String VISUALIZER_CONFIG_NETWORK_OVERRIDES_CUBE_NAME = 'VisualizerConfig.NetworkOverrides'
	final String CONFIG_ITEM = 'configItem'
	final String CONFIG_NETWORK_OVERRIDES_BASIC = 'networkOverridesBasic'
	final String CONFIG_NETWORK_OVERRIDES_FULL = 'networkOverridesFull'
	final String CONFIG_ALL_GROUPS = 'allGroups'
	final String CONFIG_ALL_TYPES = 'allTypes'
	final String CONFIG_GROUP_SUFFIX = 'groupSuffix'
	final String CUBE_TYPE = 'cubeType'
	final String CUBE_TYPE_DEFAULT = null

	final String DETAILS_CLASS_CELL_VALUES = 'cellValues'
	final String DETAILS_CLASS_WORD_WRAP = 'wordwrap'
	final String DETAILS_CLASS_SCOPE_CLICK = 'scopeClick'
	final String DETAILS_CLASS_SCOPE_INPUT = 'scopeInput'
	final String DETAILS_CLASS_EXCEPTION = 'Exception'
	final String DETAILS_CLASS_EXPAND_ALL = 'expandAll'
	final String DETAILS_CLASS_COLLAPSE_ALL = 'collapseAll'
	final String DETAILS_CLASS_EXECUTED_CELL = 'executedCell'
	final String DETAILS_CLASS_FORM_CONTROL = 'form-control'
	final String DETAILS_CLASS_TOP_NODE = 'topNode'
	final String DETAILS_CLASS_MISSING_VALUE = 'missingValue'
	final String DETAILS_CLASS_DEFAULT_VALUE = 'defaultValue'
}