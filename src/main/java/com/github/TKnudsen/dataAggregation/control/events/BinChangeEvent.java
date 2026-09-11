package com.github.TKnudsen.dataAggregation.control.events;

import java.beans.PropertyChangeEvent;
import java.util.List;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;

/**
 * Event fired when the set of bins changes.
 *
 * @since 2026
 */
public class BinChangeEvent extends PropertyChangeEvent {

	/**
	 * 
	 */
	private static final long serialVersionUID = 5715674112574119637L;

	public BinChangeEvent(Object source, List<Bin> bins) {
		super(source, PropertyChangeEvents.aggregationChanged.name(), null, bins);
	}

	@SuppressWarnings("unchecked")
	public List<Bin> getBins() {
		return (List<Bin>) getNewValue();
	}
}
