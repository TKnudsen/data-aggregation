package com.github.TKnudsen.dataAggregation.control;

import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.control.events.HighlightChangeEvent;
import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.ComplexDataObject.data.interfaces.IDObject;

import de.javagl.selection.SelectionEvent;
import de.javagl.selection.SelectionListener;
import de.javagl.selection.SelectionModel;
import de.javagl.selection.SelectionModels;

/**
 * <p>
 * Orchestrates the highlighting state for bins and aggregations. Manages two
 * {@link SelectionModel}s and fires {@link HighlightChangeEvent}s when
 * highlighting changes.
 * </p>
 * 
 * <p>
 * <b>Architecture:</b>
 * </p>
 * <ul>
 * <li>Owns the models for bins and aggregations (internal selection
 * models)</li>
 * <li>Accepts highlight changes and translates them</li>
 * <li>Fires {@link HighlightChangeEvent} to registered listeners</li>
 * <li>Provides read-only access to the highlighting models for UI
 * components</li>
 * </ul>
 * 
 * <p>
 * <b>Usage Pattern:</b>
 * </p>
 * 
 * <pre>
 * // In FrontEnd initialization:
 * DataAggregationHighlightHandler highlightHandler = new DataAggregationHighlightHandler();
 * highlightHandler.addPropertyChangeListener(myListener);
 * 
 * // In panels (read access):
 * highlightHandler.getHighlightedBins();
 * 
 * // In controllers (write access):
 * highlightHandler.highlightBin(clickedBin);
 * highlightHandler.highlightAggregations(aggregations);
 * </pre>
 * 
 * @since 2026
 */
public class DataAggregationHighlightHandler {

	private static final Logger LOGGER = Logger.getLogger(DataAggregationHighlightHandler.class.getName());

	// ==================== SELECTION MODELS ====================

	private final SelectionModel<Bin> binSelectionModel;
	private final SelectionModel<Aggregation> aggregationSelectionModel;

	// ==================== LISTENER MANAGEMENT ====================

	private final List<PropertyChangeListener> listeners = new ArrayList<>();

	// ==================== STATE TRACKING ====================

	private boolean suppressEvents = false;

	// ==================== CONSTRUCTOR ====================

	/**
	 * Creates a new highlighting handler.
	 */
	public DataAggregationHighlightHandler() {
		this.binSelectionModel = SelectionModels.create();
		this.aggregationSelectionModel = SelectionModels.create();

		LOGGER.info("DataAggregationHighlightHandler initialized");

		setupSelectionListeners();
	}

	// ==================== LISTENER SETUP ====================

	/**
	 * Sets up listeners on both selection models to translate SelectionEvents into
	 * HighlightChangeEvents for external listeners.
	 */
	private void setupSelectionListeners() {
		// Listen to bin selection changes
		binSelectionModel.addSelectionListener(new SelectionListener<Bin>() {
			@Override
			public void selectionChanged(SelectionEvent<Bin> event) {
				if (suppressEvents) {
					return;
				}

				// Log selection change (only if FINE level is enabled)
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.fine(String.format("Bin highlighting changed: +%d, -%d, total=%d",
							event.getAddedElements().size(), event.getRemovedElements().size(),
							event.getSelectionModel().getSelection().size()));
				}

				// Fire HighlightChangeEvent
				fireHighlightChangeEvent(new HashSet<>(event.getSelectionModel().getSelection()),
						aggregationSelectionModel.getSelection(), new HashSet<>(event.getAddedElements()),
						new HashSet<>(event.getRemovedElements()), Collections.emptySet(), Collections.emptySet());
			}
		});

		// Listen to aggregation selection changes
		aggregationSelectionModel.addSelectionListener(new SelectionListener<Aggregation>() {
			@Override
			public void selectionChanged(SelectionEvent<Aggregation> event) {
				if (suppressEvents) {
					return;
				}

				// Log selection change (only if FINE level is enabled)
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.fine(String.format("Aggregation highlighting changed: +%d, -%d, total=%d",
							event.getAddedElements().size(), event.getRemovedElements().size(),
							event.getSelectionModel().getSelection().size()));
				}

				// Fire HighlightChangeEvent
				fireHighlightChangeEvent(binSelectionModel.getSelection(),
						new HashSet<>(event.getSelectionModel().getSelection()), Collections.emptySet(),
						Collections.emptySet(), new HashSet<>(event.getAddedElements()),
						new HashSet<>(event.getRemovedElements()));
			}
		});
	}

	// ==================== LISTENER MANAGEMENT ====================

	/**
	 * Adds a property change listener to receive HighlightChangeEvents.
	 * 
	 * @param listener The listener to add
	 */
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		if (listener == null) {
			LOGGER.warning("Cannot add null listener");
			return;
		}

		synchronized (listeners) {
			if (!listeners.contains(listener)) {
				listeners.add(listener);
				LOGGER.fine("Listener added: " + listener.getClass().getSimpleName());
			}
		}
	}

	/**
	 * Removes a property change listener.
	 * 
	 * @param listener The listener to remove
	 * @return true if the listener was removed
	 */
	public boolean removePropertyChangeListener(PropertyChangeListener listener) {
		if (listener == null) {
			return false;
		}

		synchronized (listeners) {
			boolean removed = listeners.remove(listener);
			if (removed) {
				LOGGER.fine("Listener removed: " + listener.getClass().getSimpleName());
			}
			return removed;
		}
	}

	/**
	 * Gets all registered listeners (defensive copy).
	 * 
	 * @return List of listeners
	 */
	public List<PropertyChangeListener> getPropertyChangeListeners() {
		synchronized (listeners) {
			return new ArrayList<>(listeners);
		}
	}

	// ==================== EVENT FIRING ====================

	/**
	 * Fires a HighlightChangeEvent to all registered listeners.
	 * 
	 * @param highlightedBins         Current highlighted bins
	 * @param highlightedAggregations Current highlighted aggregations
	 * @param addedBins               Added bins
	 * @param removedBins             Removed bins
	 * @param addedAggregations       Added aggregations
	 * @param removedAggregations     Removed aggregations
	 */
	private void fireHighlightChangeEvent(Set<Bin> highlightedBins, Set<Aggregation> highlightedAggregations,
			Set<Bin> addedBins, Set<Bin> removedBins, Set<Aggregation> addedAggregations,
			Set<Aggregation> removedAggregations) {

		HighlightChangeEvent event = new HighlightChangeEvent(this, highlightedBins, highlightedAggregations, addedBins,
				removedBins, addedAggregations, removedAggregations);

		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine("Firing: " + event);
		}

		// Create defensive copy to avoid ConcurrentModificationException
		List<PropertyChangeListener> listenersCopy;
		synchronized (listeners) {
			listenersCopy = new ArrayList<>(listeners);
		}

		// Notify all listeners
		for (PropertyChangeListener listener : listenersCopy) {
			try {
				listener.propertyChange(event);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error notifying listener", e);
			}
		}
	}

	// ================= CONVENIENCE METHODS: BIN HIGHLIGHTING =================

	/**
	 * Highlights a single bin, replacing any previous highlight states.
	 * 
	 * @param bin The bin to highlight
	 */
	public void highlightBin(Bin bin) {
		suppressEvents = true;
		clearAggregationHighlighting();
		suppressEvents = false;

		if (bin == null) {
			clearBinHighlighting();
			return;
		}

		binSelectionModel.setSelection(Collections.singleton(bin));
	}

	/**
	 * Highlight multiple bins, replacing any previous highlighting.
	 * 
	 * @param bins The bins to highlight
	 */
	public void highlightBins(Collection<Bin> bins) {
		suppressEvents = true;
		clearAggregationHighlighting();
		suppressEvents = false;

		if (bins == null || bins.isEmpty()) {
			clearBinHighlighting();
			return;
		}

		binSelectionModel.setSelection(bins);
	}

	/**
	 * Clears the bin highlighting.
	 */
	public void clearBinHighlighting() {
		binSelectionModel.clear();
	}

	/**
	 * Checks if a bin is currently highlighted.
	 * 
	 * @param bin The bin to check
	 * @return true if highlighted
	 */
	public boolean isBinHighlighted(Bin bin) {
		return bin != null && binSelectionModel.isSelected(bin);
	}

	/**
	 * Gets the currently highlighted bins.
	 * 
	 * @return Unmodifiable set of highlighted bins
	 */
	public Set<Bin> getHighlightedBins() {
		return binSelectionModel.getSelection();
	}

	// =============== CONVENIENCE METHODS: AGGREGATION HIGHLIGHTING
	// ================

	/**
	 * Highlights a single aggregation, replacing any previous aggregation
	 * highlighting. Also highlights its corresponding bins.
	 * 
	 * @param aggregation The aggregation to highlight
	 */
	public void highlightAggregation(Aggregation aggregation) {
		suppressEvents = true;
		clearBinHighlighting();
		suppressEvents = false;

		if (aggregation == null) {
			clearAggregationHighlighting();
			return;
		}

		aggregationSelectionModel.setSelection(Collections.singleton(aggregation));

		suppressEvents = true;
		binSelectionModel.setSelection(aggregation.getBins());
		suppressEvents = false;
	}

	/**
	 * Highlights multiple aggregations, replacing any previous highlighting. Also
	 * highlights the corresponding bins.
	 * 
	 * @param aggregations The aggregations to highlight
	 */
	public void highlightAggregations(Collection<Aggregation> aggregations) {
		suppressEvents = true;
		clearBinHighlighting();
		suppressEvents = false;

		if (aggregations == null || aggregations.isEmpty()) {
			clearAllHighlightings();
			return;
		}

		aggregationSelectionModel.setSelection(aggregations);

		suppressEvents = true;
		List<Bin> bins = new ArrayList<>();
		for (Aggregation aggregation : aggregations) {
			bins.addAll(aggregation.getBins());
		}
		binSelectionModel.setSelection(bins);
		suppressEvents = false;
	}

	/**
	 * Clears the aggregation highlighting.
	 */
	public void clearAggregationHighlighting() {
		aggregationSelectionModel.clear();
	}

	/**
	 * Checks if an aggregation is currently highlighted.
	 * 
	 * @param aggregation The aggregation to check
	 * @return true if highlighted
	 */
	public boolean isAggregationHighlighted(Aggregation aggregation) {
		return aggregation != null && aggregationSelectionModel.isSelected(aggregation);
	}

	/**
	 * Gets the currently highlighted aggregations.
	 * 
	 * @return Unmodifiable set of highlighted aggregations
	 */
	public Set<Aggregation> getHighlightedAggregations() {
		return aggregationSelectionModel.getSelection();
	}

	// ==================== BULK OPERATIONS ====================

	/**
	 * Clears both bin and aggregation highlights.
	 */
	public void clearAllHighlightings() {
		// Clear both models (will trigger events automatically)
		binSelectionModel.clear();
		aggregationSelectionModel.clear();
	}

	/**
	 * Executes a highlighting operation without firing events. Useful for batch
	 * updates to prevent multiple re-computations.
	 * 
	 * @param operation The operation to execute
	 */
	public void executeSilently(Runnable operation) {
		boolean wasSuppressed = suppressEvents;
		suppressEvents = true;
		try {
			operation.run();
		} finally {
			suppressEvents = wasSuppressed;
		}
	}

	// ============== CONVENIENCE METHODS: IDOBJECT HIGHLIGHTING ===============

	/**
	 * Checks if an IDObject (can be aggregation or bin) is currently highlighted.
	 * 
	 * @param object The object to check
	 * @return true if highlighted
	 */
	public boolean isHighlighted(IDObject object) {
		if (object instanceof Aggregation) {
			return isAggregationHighlighted((Aggregation) object);
		} else if (object instanceof Bin) {
			return isBinHighlighted((Bin) object);
		}
		return false;
	}

	/**
	 * Highlights IDObjects (can be aggregations, bins, or both).
	 * 
	 * @param objects The objects to highlight
	 */
	public void highlight(Collection<IDObject> objects) {
		suppressEvents = true;
		clearAllHighlightings();
		suppressEvents = false;

		if (objects == null || objects.isEmpty()) {
			return;
		}

		List<Aggregation> aggregations = new ArrayList<>();
		List<Bin> bins = new ArrayList<>();

		for (IDObject object : objects) {
			if (object instanceof Aggregation) {
				aggregations.add((Aggregation) object);
			} else if (object instanceof Bin) {
				bins.add((Bin) object);
			}
		}

		if (!aggregations.isEmpty()) {
			highlightAggregations(aggregations);
		}

		if (!bins.isEmpty()) {
			if (!aggregations.isEmpty()) {
				binSelectionModel.addToSelection(bins);
			} else {
				highlightBins(bins);
			}
		}
	}

	// ==================== STATISTICS ====================

	/**
	 * Gets the total number of highlighted items (bins + aggregations).
	 * 
	 * @return Total highlighted count
	 */
	public int getTotalHighlightedCount() {
		return binSelectionModel.getSelection().size() + aggregationSelectionModel.getSelection().size();
	}

	/**
	 * Checks if there is any highlighting.
	 * 
	 * @return true if at least one bin or aggregation is highlighted
	 */
	public boolean hasHighlighting() {
		return !binSelectionModel.getSelection().isEmpty() || !aggregationSelectionModel.getSelection().isEmpty();
	}

	/**
	 * Prints current highlighting state to console (for debugging).
	 */
	public void printHighlightingState() {
		System.err.println("+================================================================");
		System.err.println("| HIGHLIGHTING STATE");
		System.err.println("+================================================================");
		System.err.printf("| Bins highlighted:          %d%n", binSelectionModel.getSelection().size());
		System.err.printf("| Aggregations highlighted:  %d%n", aggregationSelectionModel.getSelection().size());
		System.err.println("+----------------------------------------------------------------");

		if (!binSelectionModel.getSelection().isEmpty()) {
			System.err.println("| Highlighted Bins:");
			for (Bin bin : binSelectionModel.getSelection()) {
				System.err.printf("|   - %s (ID: %d)%n", bin.getName(), bin.getID());
			}
		}

		if (!aggregationSelectionModel.getSelection().isEmpty()) {
			System.err.println("| Highlighted Aggregations:");
			for (Aggregation agg : aggregationSelectionModel.getSelection()) {
				System.err.printf("|   - %s (ID: %d)%n", agg.getName(), agg.getID());
			}
		}

		System.err.println("+================================================================");
	}
}
