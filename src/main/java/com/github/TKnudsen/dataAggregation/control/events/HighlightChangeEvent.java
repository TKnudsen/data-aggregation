package com.github.TKnudsen.dataAggregation.control.events;

import java.beans.PropertyChangeEvent;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import com.github.TKnudsen.dataAggregation.control.DataAggregationHighlightHandler;
import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;

/**
 * <p>
 * Event fired when the highlighting state changes. Contains the current set of
 * highlighted bins and aggregations.
 * </p>
 * 
 * <p>
 * This event is fired by {@link DataAggregationHighlightHandler} whenever the
 * highlighting changes through either the bin or aggregation selection models.
 * </p>
 * 
 * <p>
 * <b>Usage:</b>
 * </p>
 * 
 * <pre>
 * // Listen for highlighting changes
 * highlightHandler.addPropertyChangeListener(new PropertyChangeListener() {
 * 	public void propertyChange(PropertyChangeEvent evt) {
 * 		if (evt instanceof HighlightChangeEvent) {
 * 			HighlightChangeEvent event = (HighlightChangeEvent) evt;
 * 			Set&lt;Bin&gt; highlightedBins = event.getHighlightedBins();
 * 			Set&lt;Aggregation&gt; highlightedAggs = event.getHighlightedAggregations();
 * 			// Update UI...
 * 		}
 * 	}
 * });
 * </pre>
 * 
 * @since 2026
 */
public class HighlightChangeEvent extends PropertyChangeEvent {

	private static final long serialVersionUID = 1L;

	// ==================== EVENT CONSTANTS ====================

	public static final String PROPERTY_NAME = "highlighting";

	// ==================== HIGHLIGHTING STATE ====================

	private final Set<Bin> highlightedBins;
	private final Set<Aggregation> highlightedAggregations;

	// ==================== CHANGE TRACKING ====================

	private final Set<Bin> addedBins;
	private final Set<Bin> removedBins;
	private final Set<Aggregation> addedAggregations;
	private final Set<Aggregation> removedAggregations;

	// ==================== CHANGE TYPE ====================

	/**
	 * Type of highlighting change.
	 */
	public enum ChangeType {
		/** Only bins were changed */
		BIN_ONLY,
		/** Only aggregations were changed */
		AGGREGATION_ONLY,
		/** Both bins and aggregations were changed */
		BOTH,
		/** Everything was cleared */
		CLEARED
	}

	private final ChangeType changeType;

	// ==================== CONSTRUCTORS ====================

	/**
	 * Creates a new highlight change event with detailed change information.
	 * 
	 * @param source                  The source of the event (typically
	 *                                DataAggregationHighlightHandler)
	 * @param highlightedBins         Current set of highlighted bins
	 * @param highlightedAggregations Current set of highlighted aggregations
	 * @param addedBins               Bins that were added in this change
	 * @param removedBins             Bins that were removed in this change
	 * @param addedAggregations       Aggregations that were added in this change
	 * @param removedAggregations     Aggregations that were removed in this change
	 * @throws NullPointerException if any set parameter is null
	 */
	public HighlightChangeEvent(Object source, Set<Bin> highlightedBins, Set<Aggregation> highlightedAggregations,
			Set<Bin> addedBins, Set<Bin> removedBins, Set<Aggregation> addedAggregations,
			Set<Aggregation> removedAggregations) {

		super(source, PROPERTY_NAME, null, null);

		// Validate parameters
		Objects.requireNonNull(highlightedBins, "Highlighted bins cannot be null");
		Objects.requireNonNull(highlightedAggregations, "Highlighted aggregations cannot be null");
		Objects.requireNonNull(addedBins, "Added bins cannot be null");
		Objects.requireNonNull(removedBins, "Removed bins cannot be null");
		Objects.requireNonNull(addedAggregations, "Added aggregations cannot be null");
		Objects.requireNonNull(removedAggregations, "Removed aggregations cannot be null");

		// Store immutable copies
		this.highlightedBins = Collections.unmodifiableSet(highlightedBins);
		this.highlightedAggregations = Collections.unmodifiableSet(highlightedAggregations);
		this.addedBins = Collections.unmodifiableSet(addedBins);
		this.removedBins = Collections.unmodifiableSet(removedBins);
		this.addedAggregations = Collections.unmodifiableSet(addedAggregations);
		this.removedAggregations = Collections.unmodifiableSet(removedAggregations);

		// Determine change type
		this.changeType = determineChangeType();
	}

	/**
	 * Simplified constructor when only current state is known (no delta
	 * information).
	 * 
	 * @param source                  The source of the event
	 * @param highlightedBins         Current set of highlighted bins
	 * @param highlightedAggregations Current set of highlighted aggregations
	 */
	public HighlightChangeEvent(Object source, Set<Bin> highlightedBins, Set<Aggregation> highlightedAggregations) {

		this(source, highlightedBins, highlightedAggregations, Collections.emptySet(), Collections.emptySet(),
				Collections.emptySet(), Collections.emptySet());
	}

	// ==================== CHANGE TYPE DETERMINATION ====================

	/**
	 * Determines the type of change based on what was added/removed.
	 */
	private ChangeType determineChangeType() {
		boolean binsChanged = !addedBins.isEmpty() || !removedBins.isEmpty();
		boolean aggsChanged = !addedAggregations.isEmpty() || !removedAggregations.isEmpty();

		// Check if everything was cleared
		if (highlightedBins.isEmpty() && highlightedAggregations.isEmpty() && (binsChanged || aggsChanged)) {
			return ChangeType.CLEARED;
		}

		if (binsChanged && aggsChanged) {
			return ChangeType.BOTH;
		} else if (binsChanged) {
			return ChangeType.BIN_ONLY;
		} else if (aggsChanged) {
			return ChangeType.AGGREGATION_ONLY;
		} else {
			// No change (shouldn't normally happen)
			return ChangeType.BOTH;
		}
	}

	// ==================== GETTERS: CURRENT STATE ====================

	/**
	 * Gets the current set of highlighted bins.
	 * 
	 * @return Unmodifiable set of highlighted bins (never null, may be empty)
	 */
	public Set<Bin> getHighlightedBins() {
		return highlightedBins;
	}

	/**
	 * Gets the current set of highlighted aggregations.
	 * 
	 * @return Unmodifiable set of highlighted aggregations (never null, may be
	 *         empty)
	 */
	public Set<Aggregation> getHighlightedAggregations() {
		return highlightedAggregations;
	}

	// ==================== GETTERS: CHANGE DELTAS ====================

	/**
	 * Gets the bins that were added in this change.
	 * 
	 * @return Unmodifiable set of added bins (never null, may be empty)
	 */
	public Set<Bin> getAddedBins() {
		return addedBins;
	}

	/**
	 * Gets the bins that were removed in this change.
	 * 
	 * @return Unmodifiable set of removed bins (never null, may be empty)
	 */
	public Set<Bin> getRemovedBins() {
		return removedBins;
	}

	/**
	 * Gets the aggregations that were added in this change.
	 * 
	 * @return Unmodifiable set of added aggregations (never null, may be empty)
	 */
	public Set<Aggregation> getAddedAggregations() {
		return addedAggregations;
	}

	/**
	 * Gets the aggregations that were removed in this change.
	 * 
	 * @return Unmodifiable set of removed aggregations (never null, may be empty)
	 */
	public Set<Aggregation> getRemovedAggregations() {
		return removedAggregations;
	}

	// ==================== GETTERS: METADATA ====================

	/**
	 * Gets the type of change that occurred.
	 * 
	 * @return The change type
	 */
	public ChangeType getChangeType() {
		return changeType;
	}

	/**
	 * Checks if any bins are currently highlighted.
	 * 
	 * @return true if at least one bin is highlighted
	 */
	public boolean hasBinsHighlighted() {
		return !highlightedBins.isEmpty();
	}

	/**
	 * Checks if any aggregations are currently highlighted.
	 * 
	 * @return true if at least one aggregation is highlighted
	 */
	public boolean hasAggregationsHighlighted() {
		return !highlightedAggregations.isEmpty();
	}

	/**
	 * Checks if anything is highlighted.
	 * 
	 * @return true if at least one bin or aggregation is highlighted
	 */
	public boolean hasHighlighting() {
		return hasBinsHighlighted() || hasAggregationsHighlighted();
	}

	/**
	 * Gets the total number of highlighted items (bins + aggregations).
	 * 
	 * @return Total count
	 */
	public int getTotalHighlightedCount() {
		return highlightedBins.size() + highlightedAggregations.size();
	}

	// ==================== STRING REPRESENTATION ====================

	@Override
	public String toString() {
		return String.format("HighlightChangeEvent[type=%s, bins=%d (+%d/-%d), aggs=%d (+%d/-%d)]", changeType,
				highlightedBins.size(), addedBins.size(), removedBins.size(), highlightedAggregations.size(),
				addedAggregations.size(), removedAggregations.size());
	}

	/**
	 * Creates a detailed string representation for debugging.
	 * 
	 * @return Detailed string with all highlighted items
	 */
	public String toDetailedString() {
		StringBuilder sb = new StringBuilder();
		sb.append("HighlightChangeEvent:\n");
		sb.append("  Type: ").append(changeType).append("\n");
		sb.append("  Highlighted Bins (").append(highlightedBins.size()).append("):");

		if (highlightedBins.isEmpty()) {
			sb.append(" none\n");
		} else {
			sb.append("\n");
			for (Bin bin : highlightedBins) {
				sb.append("    - ").append(bin.getName()).append(" (ID: ").append(bin.getID()).append(")\n");
			}
		}

		sb.append("  Highlighted Aggregations (").append(highlightedAggregations.size()).append("):");

		if (highlightedAggregations.isEmpty()) {
			sb.append(" none\n");
		} else {
			sb.append("\n");
			for (Aggregation agg : highlightedAggregations) {
				sb.append("    - ").append(agg.getName()).append(" (ID: ").append(agg.getID()).append(")\n");
			}
		}

		return sb.toString();
	}
}