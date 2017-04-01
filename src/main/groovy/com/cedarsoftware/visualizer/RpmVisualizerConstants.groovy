package com.cedarsoftware.visualizer

import groovy.transform.CompileStatic

/**
 * Provides constants for the visualizer
 */

@CompileStatic
interface RpmVisualizerConstants extends VisualizerConstants
{
	final String RPM_CLASS = 'rpm.class'
	final String RPM_ENUM = 'rpm.enum'
	final String RPM_CLASS_DOT = 'rpm.class.'
	final String RPM_SCOPE_CLASS_DOT = 'rpm.scope.class.'
	final String RPM_ENUM_DOT = 'rpm.enum.'
	final String CLASS_TRAITS = 'CLASS_TRAITS'
	final String DOT_CLASS_TRAITS = '.classTraits'
	final String R_RPM_TYPE = 'r:rpmType'
	final String R_EXISTS = 'r:exists'
	final String V_ENUM = 'v:enum'
	final String R_SCOPED_NAME = 'r:scopedName'
	final String V_MIN = 'v:min'
	final String V_MAX = 'v:max'
	final String SOURCE_FIELD_NAME = 'sourceFieldName'
	final String AXIS_FIELD = 'field'
	final String AXIS_NAME = 'name'
	final String AXIS_TRAIT = 'trait'
	final String V_MIN_CARDINALITY = '0'
	final String V_MAX_CARDINALITY = '999999'

	final String SOURCE_SCOPE_KEY_PREFIX = 'source'
	final String SYSTEM_SCOPE_KEY_PREFIX = "_"
	final String POLICY_CONTROL_DATE = 'policyControlDate'
	final String QUOTE_DATE = 'quoteDate'
	final String EFFECTIVE_VERSION = SYSTEM_SCOPE_KEY_PREFIX + "effectiveVersion"
	final Set<String> MANDATORY_SCOPE_KEYS = [AXIS_FIELD, AXIS_NAME, AXIS_TRAIT] as Set

	final String CUBE_TYPE_RPM = 'rpm'
	final String TYPES_TO_ADD_CUBE_NAME = 'VisualizerTypesToAdd'
	final String SOURCE_TYPE = 'sourceType'
	final String TARGET_TYPE = 'targetType'
	final String UNSPECIFIED_ENUM = 'UNSPECIFIED_ENUM'
}