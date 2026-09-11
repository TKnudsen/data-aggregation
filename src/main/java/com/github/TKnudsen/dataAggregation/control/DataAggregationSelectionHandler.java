package com.github.TKnudsen.dataAggregation.control;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.ComplexDataObject.data.interfaces.IDObject;

import de.javagl.selection.SelectionEvent;
import de.javagl.selection.SelectionListener;
import de.javagl.selection.SelectionModel;
import de.javagl.selection.SelectionModels;

/**
 * <p>
 * Orchestrates selection state for bins and aggregations. Manages two
 * {@link SelectionModel}s and notifies the {@link AggregationStore} about
 * selection changes to trigger re-computations.
 * </p>
 * 
 * <p>
 * <b>Architecture:</b>
 * </p>
 * <ul>
 * <li>Owns the selection models for bins and aggregations</li>
 * <li>Listens to selection changes and translates them into domain events</li>
 * <li>Fires events to AggregationStore for clustering re-computation</li>
 * <li>Provides read-only access to selection models for UI components</li>
 * </ul>
 * 
 * <p>
 * <b>Usage Pattern:</b>
 * </p>
 * 
 * <pre>
 * // In FrontEnd initialization:
 * DataAggregationSelectionHandler selectionHandler = new DataAggregationSelectionHandler(consumers);
 * 
 * // In panels (read access):
 * selectionHandler.getBinSelectionModel();
 * selectionHandler.getSelection();
 * 
 * // In controllers (write access):
 * selectionHandler.selectBin(clickedBin);
 * selectionHandler.selectAggregations(aggregations);
 * </pre>
 * 
 * @since 2026
 */
public class DataAggregationSelectionHandler {

	private static final Logger LOGGER = Logger.getLogger(DataAggregationSelectionHandler.class.getName());

	// ==================== SELECTION MODELS ====================
	private final SelectionModel<Bin> binSelectionModel;
	private final SelectionModel<Aggregation> aggregationSelectionModel;

	// ============== BACKEND TARGET FOR EVENTS (CLUSTERING) ===============
	private final Consumer<Set<Aggregation>> aggregationSelectionConsumer;
	private final Consumer<Set<Bin>> binSelectionConsumer;

	// ==================== STATE TRACKING ====================
	private boolean suppressEvents = false;

	/**
	 * Creates a new selection handler.
	 * 
	 * @param aggregationStore The aggregation store to notify about selection
	 *                         changes
	 * @throws NullPointerException if aggregationStore is null
	 */
	public DataAggregationSelectionHandler(Consumer<Set<Aggregation>> aggregationSelectionConsumer,
			Consumer<Set<Bin>> binSelectionConsumer) {
		this.aggregationSelectionConsumer = aggregationSelectionConsumer;
		this.binSelectionConsumer = binSelectionConsumer;

		// Create selection models
		this.binSelectionModel = SelectionModels.create();
		this.aggregationSelectionModel = SelectionModels.create();

		// Setup listeners to bridge SelectionModel -> AggregationStore
		setupSelectionListeners();

		LOGGER.info("DataAggregationSelectionHandler initialized");
	}

	/**
	 * Sets up listeners on both selection models to translate SelectionEvents into
	 * domain-specific events for the AggregationStore.
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
					LOGGER.fine(String.format("Bin selection changed: +%d, -%d, total=%d",
							event.getAddedElements().size(), event.getRemovedElements().size(),
							event.getSelectionModel().getSelection().size()));
				}

				binSelectionConsumer.accept(binSelectionModel.getSelection());
			}
		});

		// Listen to aggregation selection changes
		aggregationSelectionModel.addSelectionListener(new SelectionListener<Aggregation>() {
			@Override
			public void selectionChanged(SelectionEvent<Aggregation> event) {
				if (suppressEvents)
					return;

				// Log selection change (only if FINE level is enabled)
				if (LOGGER.isLoggable(Level.FINE)) {
					LOGGER.fine(String.format("Aggregation selection changed: +%d, -%d, total=%d",
							event.getAddedElements().size(), event.getRemovedElements().size(),
							event.getSelectionModel().getSelection().size()));
				}

				aggregationSelectionConsumer.accept(aggregationSelectionModel.getSelection());
			}
		});
	}

	// ==================== PUBLIC ACCESSORS ====================

	/**
	 * Gets the bin selection model for read and write access.
	 * 
	 * @return The bin selection model
	 */
	public SelectionModel<Bin> getBinSelectionModel() {
		return binSelectionModel;
	}

	/**
	 * Gets the aggregation selection model for read and write access.
	 * 
	 * @return The aggregation selection model
	 */
	public SelectionModel<Aggregation> getAggregationSelectionModel() {
		return aggregationSelectionModel;
	}

	// ==================== CONVENIENCE METHODS: BIN SELECTION ====================

	/**
	 * Selects a single bin, replacing any previous selection. If the bin was
	 * selected already, it is removed from the selection.
	 * 
	 * @param bin The bin to select
	 */
	public void selectBin(Bin bin) {
		LOGGER.info("Bin selected: " + bin);

		suppressEvents = true;
		clearAggregationSelection();
		suppressEvents = false;

		if (bin == null) {
			clearBinSelection();
			return;
		}

		if (binSelectionModel.isSelected(bin))
			binSelectionModel.removeFromSelection(Collections.singleton(bin));
		else
			binSelectionModel.setSelection(Collections.singleton(bin));
	}

	/**
	 * Adds a bin to the current selection.
	 * 
	 * @param bin The bin to add
	 */
	public void addBinToSelection(Bin bin) {
		suppressEvents = true;
		clearAggregationSelection();
		suppressEvents = false;

		if (bin == null)
			return;

		if (binSelectionModel.isSelected(bin))
			binSelectionModel.removeFromSelection(Collections.singleton(bin));
		else
			binSelectionModel.addToSelection(Collections.singleton(bin));
	}

	/**
	 * Removes a bin from the current selection.
	 * 
	 * @param bin The bin to remove
	 */
	public void removeBinFromSelection(Bin bin) {
		if (bin != null) {
			binSelectionModel.removeFromSelection(bin);
		}
	}

	/**
	 * Selects multiple bins, replacing any previous selection. Only adds unselected
	 * elements but does not remove the ones already selected before.
	 * 
	 * @param bins The bins to select
	 */
	public void selectBins(Collection<Bin> bins) {
		for (Bin bin : bins)
			LOGGER.info("Bin selected: " + bin);

		suppressEvents = true;
		clearAggregationSelection();
		suppressEvents = false;

		if (bins == null || bins.isEmpty()) {
			clearBinSelection();
			return;
		}
		binSelectionModel.setSelection(bins);
	}

	/**
	 * Adds multiple bins to the current selection.
	 * 
	 * @param bins The bins to add
	 */
	public void addBinsToSelection(Collection<Bin> bins) {
		suppressEvents = true;
		clearAggregationSelection();
		suppressEvents = false;

		if (bins != null && !bins.isEmpty()) {
			binSelectionModel.addToSelection(bins);
		}
	}

	/**
	 * Clears the bin selection.
	 */
	public void clearBinSelection() {
		binSelectionModel.clear();
	}

	/**
	 * Checks if a bin is currently selected.
	 * 
	 * @param bin The bin to check
	 * @return true if selected
	 */
	public boolean isBinSelected(Bin bin) {
		return bin != null && binSelectionModel.isSelected(bin);
	}

	/**
	 * Gets the currently selected bins.
	 * 
	 * @return Unmodifiable set of selected bins
	 */
	public Set<Bin> getSelectedBins() {
		return binSelectionModel.getSelection();
	}

	// =============== CONVENIENCE METHODS: AGGREGATION SELECTION ================

	/**
	 * Selects a single aggregation, replacing any previous selection. If the
	 * aggregation was selected already, it is removed from the selection.
	 * 
	 * @param aggregation The aggregation to select
	 */
	public void selectAggregation(Aggregation aggregation) {
		LOGGER.info("Aggregation selected: " + aggregation);

		suppressEvents = true;
		clearBinSelection();
		suppressEvents = false;

		if (aggregation == null) {
			clearAggregationSelection();
			return;
		}

		if (aggregationSelectionModel.isSelected(aggregation))
			aggregationSelectionModel.removeFromSelection(Collections.singleton(aggregation));
		else
			aggregationSelectionModel.setSelection(Collections.singleton(aggregation));
	}

	/**
	 * Adds an aggregation to the current selection.
	 * 
	 * @param aggregation The aggregation to add
	 */
	public void addAggregationToSelection(Aggregation aggregation) {
		suppressEvents = true;
		clearBinSelection();
		suppressEvents = false;

		if (aggregation == null)
			return;

		if (aggregationSelectionModel.isSelected(aggregation))
			aggregationSelectionModel.removeFromSelection(Collections.singleton(aggregation));
		else
			aggregationSelectionModel.addToSelection(Collections.singleton(aggregation));
	}

	/**
	 * Removes an aggregation from the current selection.
	 * 
	 * @param aggregation The aggregation to remove
	 */
	public void removeAggregationFromSelection(Aggregation aggregation) {
		if (aggregation != null) {
			aggregationSelectionModel.removeFromSelection(aggregation);
		}
	}

	/**
	 * Selects multiple aggregations, replacing any previous selection. Only adds
	 * unselected elements but does not remove the ones already selected before.
	 * 
	 * @param aggregations The aggregations to select
	 */
	public void selectAggregations(Collection<Aggregation> aggregations) {
		for (Aggregation aggregation : aggregations)
			LOGGER.info("Aggregation selected: " + aggregation);

		suppressEvents = true;
		clearBinSelection();
		suppressEvents = false;

		if (aggregations == null || aggregations.isEmpty()) {
			clearAggregationSelection();
			return;
		}
		aggregationSelectionModel.setSelection(aggregations);
	}

	/**
	 * Adds multiple aggregations to the current selection.
	 * 
	 * @param aggregations The aggregations to add
	 */
	public void addAggregationsToSelection(Collection<Aggregation> aggregations) {
		suppressEvents = true;
		clearBinSelection();
		suppressEvents = false;

		if (aggregations != null && !aggregations.isEmpty()) {
			aggregationSelectionModel.addToSelection(aggregations);
		}
	}

	/**
	 * Clears the aggregation selection.
	 */
	public void clearAggregationSelection() {
		aggregationSelectionModel.clear();
	}

	/**
	 * Checks if an aggregation is currently selected.
	 * 
	 * @param aggregation The aggregation to check
	 * @return true if selected
	 */
	public boolean isAggregationSelected(Aggregation aggregation) {
		return aggregation != null && aggregationSelectionModel.isSelected(aggregation);
	}

	/**
	 * Gets the currently selected aggregations.
	 * 
	 * @return Unmodifiable set of selected aggregations
	 */
	public Set<Aggregation> getSelectedAggregations() {
		return aggregationSelectionModel.getSelection();
	}

	// ==================== BULK OPERATIONS ====================

	/**
	 * Clears both bin and aggregation selections.
	 */
	public void clearAllSelections() {
		binSelectionModel.clear();
		aggregationSelectionModel.clear();
	}

	/**
	 * Executes a selection operation without firing events. Useful for batch
	 * updates to prevent multiple re-computations.
	 * 
	 * @param operation The operation to execute
	 */
	public void executeSilently(Runnable operation) {
		boolean wasSupressed = suppressEvents;
		suppressEvents = true;
		try {
			operation.run();
		} finally {
			suppressEvents = wasSupressed;
		}
	}

	// ============== CONVENIENCE METHODS: IDOBJECT SELECTION ===============

	/**
	 * Checks if an IDObject (can be aggregation, or bin, or other) is currently
	 * selected.
	 * 
	 * @param object The object to check
	 * @return true if selected
	 */
	public boolean isSelected(IDObject object) {
		if (object instanceof Aggregation)
			return isAggregationSelected((Aggregation) object);
		else if (object instanceof Bin)
			return isBinSelected((Bin) object);
		return false;
	}

	// ==================== STATISTICS ====================

	/**
	 * Gets the total number of selected items (bins + aggregations).
	 * 
	 * @return Total selection count
	 */
	public int getTotalSelectionCount() {
		return binSelectionModel.getSelection().size() + aggregationSelectionModel.getSelection().size();
	}

	/**
	 * Checks if there is any selection.
	 * 
	 * @return true if at least one bin or aggregation is selected
	 */
	public boolean hasSelection() {
		return !binSelectionModel.getSelection().isEmpty() || !aggregationSelectionModel.getSelection().isEmpty();
	}

	/**
	 * Prints current selection state to console (for debugging).
	 */
	public void printSelectionState() {
		System.err.println("+================================================================");
		System.err.println("| SELECTION STATE");
		System.err.println("+================================================================");
		System.err.printf("| Bins selected:          %d%n", binSelectionModel.getSelection().size());
		System.err.printf("| Aggregations selected:  %d%n", aggregationSelectionModel.getSelection().size());
		System.err.println("+----------------------------------------------------------------");

		if (!binSelectionModel.getSelection().isEmpty()) {
			System.err.println("| Selected Bins:");
			for (Bin bin : binSelectionModel.getSelection()) {
				System.err.printf("|   - %s (ID: %d)%n", bin.getName(), bin.getID());
			}
		}

		if (!aggregationSelectionModel.getSelection().isEmpty()) {
			System.err.println("| Selected Aggregations:");
			for (Aggregation agg : aggregationSelectionModel.getSelection()) {
				System.err.printf("|   - %s (ID: %d)%n", agg.getName(), agg.getID());
			}
		}

		System.err.println("+================================================================");
	}
}