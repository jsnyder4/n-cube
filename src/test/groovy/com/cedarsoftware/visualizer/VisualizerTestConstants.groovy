package com.cedarsoftware.visualizer

import groovy.transform.CompileStatic

/**
 * Provides constants for the visualizer
 */

@CompileStatic
interface VisualizerTestConstants
{
	final String ADDITIONAL_SCOPE_REQUIRED_FOR = 'Additional scope required for '
	final String REQUIRED_SCOPE_VALUE_NOT_FOUND_FOR = 'Required scope value not found for '
	final String UNABLE_TO_LOAD = 'Unable to load '
	final String ADDITIONAL_SCOPE_REQUIRED = 'Additional scope is required.'
	final String IS_NOT_VALID_FOR = 'is not valid for'
	final String DEFAULTS_WERE_USED = 'Defaults were used for some scope keys.'
	final String SELECT_OR_ENTER_VALUE = 'Select or enter value...'
	final String ENTER_VALUE = 'Enter value...'
	final String DEFAULT = 'Default'
	final String NONE = 'none'
	final String DETAILS_LABEL_SCOPE = 'Scope'
	final String DETAILS_LABEL_AVAILABLE_SCOPE = 'Available scope'
	final String DETAILS_LABEL_AXES = 'Axes'
	final String DETAILS_LABEL_CELL_VALUES = 'Cell values'
	final String DETAILS_LABEL_EXPAND_ALL = 'Expand all'
	final String DETAILS_LABEL_COLLAPSE_ALL = 'Collapse all'
	final String DETAILS_LABEL_NON_EXECUTED_VALUE = 'Non-executed value:'
	final String DETAILS_LABEL_EXECUTED_VALUE = 'Executed value:'
	final String DETAILS_LABEL_EXCEPTION = 'Exception:'
	final String DETAILS_LABEL_MESSAGE = 'Message:'
	final String DETAILS_LABEL_ROOT_CAUSE = 'Root cause:'
	final String DETAILS_LABEL_STACK_TRACE = 'Stack trace:'
	final String DETAILS_TITLE_EXPAND_ALL = 'Expand all cell details'
	final String DETAILS_TITLE_COLLAPSE_ALL = 'Collapse all cell details'
	final String DETAILS_TITLE_EXECUTED_CELL = 'Executed cell'
	final String DETAILS_TITLE_MISSING_OR_INVALID_COORDINATE = 'The cell was executed with a missing or invalid coordinate'
	final String DETAILS_TITLE_ERROR_DURING_EXECUTION = 'An error occurred during the execution of the cell'
}