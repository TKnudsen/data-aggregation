package com.github.TKnudsen.dataAggregation.control.events;

/**
 * Property-change event names used across the control/events package. Some
 * entries predate this library's current scope (carried over from a shared
 * event-name enum) and are unused here.
 *
 * @since 2013
 */
public enum PropertyChangeEvents {
	selectedStatusChanged, SynchStateChanged, fontChanged, quantileChanged, graphsChanged, clusterGraphChanged,
	layoutChanged, layoutCoordinatesRequest, aggregationChanged, bucketChanged, edgeRankingChanged, filterStatusChanged,
	clusterCountChanged, aggregationSelectionChanged, bucketSelectionChanged, guidanceChanged
}
