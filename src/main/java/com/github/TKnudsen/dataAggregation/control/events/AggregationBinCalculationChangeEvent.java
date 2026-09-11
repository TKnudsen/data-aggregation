package com.github.TKnudsen.dataAggregation.control.events;

import java.beans.PropertyChangeEvent;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;

/**
 * Event fired when an aggregation's bin calculation changes.
 *
 * @since 2026
 */
public class AggregationBinCalculationChangeEvent extends PropertyChangeEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = -7377071415793006880L;

	public AggregationBinCalculationChangeEvent(Object source, Aggregation aggregation) {
		super(source, PropertyChangeEvents.aggregationChanged.name(), null, aggregation);
	}
}
