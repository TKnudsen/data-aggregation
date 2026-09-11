package com.github.TKnudsen.dataAggregation.data.aggregation;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.control.events.AggregationBinCalculationChangeEvent;
import com.github.TKnudsen.dataAggregation.control.events.BinChangeEvent;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationFunction;
import com.github.TKnudsen.ComplexDataObject.data.interfaces.IDObject;
import com.github.TKnudsen.ComplexDataObject.data.interfaces.ISelfDescription;
import com.github.TKnudsen.ComplexDataObject.model.tools.MathFunctions;
import com.github.TKnudsen.ComplexDataObject.model.tools.StatisticsSupport;

/**
 * <p>
 * Thread-safe representation of a data aggregation with bins and associated
 * metadata. All public methods are thread-safe and use lazy initialization
 * with proper double-checked locking.
 * </p>
 *
 * @since 2013
 */
public class Aggregation
		implements IDObject, ISelfDescription, PropertyChangeListener, Comparable<Aggregation>, Iterable<Bin> {

	private static final Logger LOGGER = Logger.getLogger(Aggregation.class.getName());

	private final long ID;
	private final String name;
	private final AggregationFunction<?> aggregationFunction;

	private volatile int cachedHashCode = 0;
	private final AtomicBoolean isCalculatingBins = new AtomicBoolean(false);

	private volatile List<Bin> bins;
	private volatile ConcurrentHashMap<Long, Bin> mappingLongBin;
	private volatile List<String> labeling;
	private volatile Map<Long, Bin> index;
	private volatile Map<Integer, StatisticsSupport> binStatistics;

	private final List<PropertyChangeListener> listeners = new ArrayList<>();
	private final ReadWriteLock listenerLock = new ReentrantReadWriteLock();
	private final ReadWriteLock stateLock = new ReentrantReadWriteLock();

	/**
	 * Creates a new aggregation based on the provided aggregation function.
	 * Immediately calculates bins and dependent caches.
	 * 
	 * @param aggregationFunction The function defining how data is aggregated into
	 *                            bins
	 * @throws NullPointerException if aggregationFunction is null
	 */
	public Aggregation(AggregationFunction<?> aggregationFunction) {
		Objects.requireNonNull(aggregationFunction, "AggregationFunction cannot be null");

		this.ID = MathFunctions.randomLong();
		this.aggregationFunction = aggregationFunction;
		this.name = aggregationFunction.getName();
		this.binStatistics = new ConcurrentHashMap<>();

		aggregationFunction.addPropertyChangeListener(this);

		stateLock.writeLock().lock();
		try {
			calculateBinsAndDependenciesInternal();
		} finally {
			stateLock.writeLock().unlock();
		}
	}

	@Override
	public void propertyChange(PropertyChangeEvent event) {
		cachedHashCode = 0;

		if (LOGGER.isLoggable(Level.FINE))
			LOGGER.fine("Aggregation " + name + ": handling event " + event.getClass().getSimpleName());

		if (event instanceof AggregationBinCalculationChangeEvent) {
			handleAggregationChange();
		} else if (event instanceof BinChangeEvent) {
			handleBinChange((BinChangeEvent) event);
		}
	}

	/**
	 * Handles aggregation-level changes by recalculating bins and all dependent
	 * caches atomically. Prevents recursive calls using atomic guard.
	 */
	private void handleAggregationChange() {
		if (isCalculatingBins.get()) {
			if (LOGGER.isLoggable(Level.FINE))
				LOGGER.fine("Aggregation " + name + ": Skipping recursive calculateBins()");
			return;
		}

		calculateBins();
	}

	/**
	 * Handles bin-level changes by invalidating only bin-dependent caches (not
	 * mapping which is bin-independent) and notifying listeners.
	 * 
	 * @param event The bin change event
	 */
	private void handleBinChange(BinChangeEvent event) {
		stateLock.writeLock().lock();
		try {
			labeling = null;
		} finally {
			stateLock.writeLock().unlock();
		}

		notifyListeners(event);
	}

	/**
	 * Notifies all registered listeners of a property change event. Creates a
	 * defensive copy of the listener list to avoid concurrent modification issues.
	 * 
	 * @param event The event to propagate to listeners
	 */
	private void notifyListeners(PropertyChangeEvent event) {
		List<PropertyChangeListener> listenersCopy;

		listenerLock.readLock().lock();
		try {
			listenersCopy = new ArrayList<>(listeners);
		} finally {
			listenerLock.readLock().unlock();
		}

		for (PropertyChangeListener listener : listenersCopy) {
			listener.propertyChange(event);
		}
	}

	/**
	 * Registers a property change listener to receive notifications when this
	 * aggregation changes. Duplicate registrations are ignored.
	 * 
	 * @param listener The listener to add
	 */
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		if (listener == null)
			return;

		listenerLock.writeLock().lock();
		try {
			if (!listeners.contains(listener)) {
				listeners.add(listener);
			}
		} finally {
			listenerLock.writeLock().unlock();
		}
	}

	/**
	 * Removes a property change listener. If the listener is not registered, this
	 * method has no effect.
	 * 
	 * @param listener The listener to remove
	 * @return true if the listener was removed, false if not found
	 */
	public boolean removePropertyChangeListener(PropertyChangeListener listener) {
		if (listener == null)
			return false;

		listenerLock.writeLock().lock();
		try {
			return listeners.remove(listener);
		} finally {
			listenerLock.writeLock().unlock();
		}
	}

	/**
	 * Returns a defensive copy of all registered property change listeners.
	 * 
	 * @return List of listeners (modifications to this list do not affect the
	 *         aggregation)
	 */
	public List<PropertyChangeListener> getPropertyChangeListeners() {
		listenerLock.readLock().lock();
		try {
			return new ArrayList<>(listeners);
		} finally {
			listenerLock.readLock().unlock();
		}
	}

	/**
	 * Returns the bins of this aggregation. Never returns null.
	 * 
	 * @return List of bins
	 */
	public List<Bin> getBins() {
		List<Bin> result = bins;
		if (result != null) {
			return result;
		}

		stateLock.writeLock().lock();
		try {
			result = bins;
			if (result == null) {
				calculateBinsAndDependenciesInternal();
				result = bins;
			}
		} finally {
			stateLock.writeLock().unlock();
		}

		return result;
	}

	/**
	 * Recalculates bins and all dependent caches atomically, then notifies
	 * listeners. Protected against recursive calls using an atomic guard.
	 */
	public void calculateBins() {
		if (!isCalculatingBins.compareAndSet(false, true)) {
			if (LOGGER.isLoggable(Level.FINE))
				LOGGER.fine("Aggregation " + name + ": Already calculating bins, skipping");
			return;
		}

		try {
			stateLock.writeLock().lock();
			try {
				calculateBinsAndDependenciesInternal();
			} finally {
				stateLock.writeLock().unlock();
			}

			notifyListeners(new AggregationBinCalculationChangeEvent(this, this));

		} finally {
			isCalculatingBins.set(false);
		}
	}

	/**
	 * Calculate bins and all dependent caches atomically under one write lock. This
	 * prevents other threads from seeing partial state (bins valid but mapping
	 * null) which causes deadlocks.
	 * 
	 * Must be called under write lock.
	 */
	private void calculateBinsAndDependenciesInternal() {
		bins = aggregationFunction.getBins();

		List<List<Long>> newMapping = new ArrayList<>();
		ConcurrentHashMap<Long, Bin> newMappingLongBin = new ConcurrentHashMap<>();
		Map<Long, Bin> newIndex = new HashMap<>();
		List<String> newLabeling = new ArrayList<>();

		for (Bin bin : bins) {
			List<Long> entityIds = new ArrayList<>(bin.getElements().keySet());
			newMapping.add(entityIds);

			newLabeling.add(bin.getName());

			for (Long entityId : entityIds) {
				newMappingLongBin.put(entityId, bin);
				newIndex.put(entityId, bin);
			}
		}

		this.mappingLongBin = newMappingLongBin;
		this.index = newIndex;
		this.labeling = newLabeling;
	}

	/**
	 * Returns the number of bins in this aggregation.
	 * 
	 * @return Bin count
	 */
	public int size() {
		return getBins().size();
	}

	/**
	 * Returns the mapping from entity ID to bin. Never returns null after
	 * initialization.
	 * 
	 * @return Map from entity ID to containing bin
	 */
	public ConcurrentHashMap<Long, Bin> getMappingLongBin() {
		ConcurrentHashMap<Long, Bin> result = mappingLongBin;
		if (result != null) {
			return result;
		}

		stateLock.writeLock().lock();
		try {
			result = mappingLongBin;
			if (result == null) {
				calculateBinsAndDependenciesInternal();
				result = mappingLongBin;
			}
		} finally {
			stateLock.writeLock().unlock();
		}

		return result;
	}

	/**
	 * Returns the index mapping entity IDs to bins. Never returns null after
	 * initialization.
	 * 
	 * @return Map from entity ID to containing bin
	 */
	public Map<Long, Bin> getIndex() {
		Map<Long, Bin> result = index;
		if (result != null) {
			return result;
		}

		stateLock.writeLock().lock();
		try {
			result = index;
			if (result == null) {
				calculateBinsAndDependenciesInternal();
				result = index;
			}
		} finally {
			stateLock.writeLock().unlock();
		}

		return result;
	}

	/**
	 * Returns the labels of all bins in order. Never returns null after
	 * initialization.
	 * 
	 * @return List of bin names
	 */
	public List<String> getLabeling() {
		List<String> result = labeling;
		if (result != null) {
			return result;
		}

		stateLock.writeLock().lock();
		try {
			result = labeling;
			if (result == null) {
				result = new ArrayList<>();
				for (Bin bin : getBins()) {
					result.add(bin.getName());
				}
				this.labeling = result;
			}
		} finally {
			stateLock.writeLock().unlock();
		}

		return result;
	}

	/**
	 * Returns statistics about bin sizes (distribution of entity counts across
	 * bins). Cached per bin count configuration.
	 * 
	 * @return Statistics support object for bin sizes
	 */
	public StatisticsSupport getBinStatistics() {
		int currentBinCount = getBins().size();

		StatisticsSupport stats = binStatistics.get(currentBinCount);
		if (stats != null) {
			return stats;
		}

		List<Double> binSizes = new ArrayList<>();
		for (Bin bin : getBins()) {
			binSizes.add((double) bin.size());
		}

		stats = new StatisticsSupport(binSizes);
		binStatistics.putIfAbsent(currentBinCount, stats);

		return binStatistics.get(currentBinCount);
	}

	/**
	 * Splits a bin into multiple bins with the specified target count.
	 * 
	 * @param bin         The bin to split
	 * @param targetCount Desired number of resulting bins
	 */
	public void splitBin(Bin bin, int targetCount) {
		if (getBins().contains(bin)) {
			stateLock.writeLock().lock();
			try {
				aggregationFunction.splitBin(bin, targetCount);
				binStatistics.remove(bins != null ? bins.size() : 0);
			} finally {
				stateLock.writeLock().unlock();
			}
		}
	}

	/**
	 * Changes the order position of a bin.
	 * 
	 * @param bin         The bin to reorder
	 * @param targetIndex The desired index position
	 */
	public void changeOrder(Bin bin, int targetIndex) {
		stateLock.writeLock().lock();
		try {
			aggregationFunction.changeOrder(bin, targetIndex);
			binStatistics.remove(bins != null ? bins.size() : 0);
		} finally {
			stateLock.writeLock().unlock();
		}
	}

	/**
	 * Merges multiple bins into a single bin.
	 * 
	 * @param binsToMerge List of bins to merge
	 */
	public void mergeBins(List<Bin> binsToMerge) {
		stateLock.writeLock().lock();
		try {
			aggregationFunction.mergeBins(binsToMerge);
			binStatistics.remove(bins != null ? bins.size() : 0);
		} finally {
			stateLock.writeLock().unlock();
		}
	}

	/**
	 * Filters out a bin from the aggregation.
	 * 
	 * @param bin The bin to filter
	 */
	public void filterBin(Bin bin) {
		stateLock.writeLock().lock();
		try {
			aggregationFunction.filterBin(bin);
			binStatistics.remove(bins != null ? bins.size() : 0);
		} finally {
			stateLock.writeLock().unlock();
		}
	}

	/**
	 * Checks if a bin is currently filtered.
	 * 
	 * @param bin The bin to check
	 * @return true if the bin is filtered
	 */
	public boolean isFiltered(Bin bin) {
		return aggregationFunction.isFiltered(bin);
	}

	/**
	 * Returns all currently filtered bins.
	 * 
	 * @return List of filtered bins
	 */
	public List<Bin> getFilteredBins() {
		return aggregationFunction.getFilteredBins();
	}

	/**
	 * Returns the underlying aggregation function.
	 * 
	 * @return The aggregation function
	 */
	public AggregationFunction<?> AggregationFunction() {
		return aggregationFunction;
	}

	/**
	 * Returns the current aggregation level.
	 * 
	 * @return Aggregation level
	 */
	public int getAggregationLevel() {
		return aggregationFunction.getAggregationLevel();
	}

	/**
	 * Sets the aggregation level.
	 * 
	 * @param aggregationLevel The new aggregation level
	 */
	public void setAggregationLevel(int aggregationLevel) {
		this.aggregationFunction.setAggregationLevel(aggregationLevel);
	}

	@Override
	public long getID() {
		return ID;
	}

	/**
	 * Returns the name of this aggregation.
	 * 
	 * @return Aggregation name
	 */
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return aggregationFunction.getDescription();
	}

	/**
	 * Compares aggregations by name for ordering.
	 * 
	 * @param other The aggregation to compare to
	 * @return Negative, zero, or positive based on name comparison
	 */
	@Override
	public int compareTo(Aggregation other) {
		if (other == null)
			return -1;
		return this.getName().compareTo(other.getName());
	}

	@Override
	public String toString() {
		return aggregationFunction.toString();
	}

	/**
	 * Returns a hash code based on the immutable ID.
	 * 
	 * <p>
	 * Hash must be stable for use in HashSet/HashMap. Uses only the immutable ID
	 * field, never bins or other mutable state.
	 * </p>
	 * 
	 * @return Hash code based on ID
	 */
	@Override
	public int hashCode() {
		return Long.hashCode(ID);
	}

	/**
	 * Compares aggregations for equality based on their unique IDs.
	 * 
	 * @param obj The object to compare with
	 * @return true if the aggregations have the same ID
	 */
	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		Aggregation other = (Aggregation) obj;
		return this.ID == other.ID;
	}

	/**
	 * Checks if a map of entities contains only numeric values.
	 * 
	 * @param entities Map of entity IDs to values
	 * @return true if all values are numeric
	 */
	public static boolean isNumeric(Map<Long, Object> entities) {
		return testNumeric(entities);
	}

	/**
	 * Tests if all values in an entity map are numeric.
	 * 
	 * @param entities Map of entity IDs to values
	 * @return true if all non-null values can be parsed as doubles
	 */
	public static boolean testNumeric(Map<Long, Object> entities) {
		if (entities == null || entities.isEmpty())
			return false;

		for (Object value : entities.values()) {
			if (value == null)
				continue;

			if (!isDouble(String.valueOf(value))) {
				return false;
			}
		}

		return true;
	}

	/**
	 * Checks if a string represents a valid double value.
	 * 
	 * @param str String to check
	 * @return true if the string can be parsed as a double
	 */
	public static boolean isDouble(String str) {
		if (str == null || str.trim().isEmpty())
			return false;

		try {
			Double.parseDouble(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Checks if a string represents a valid integer value.
	 * 
	 * @param str String to check
	 * @return true if the string can be parsed as an integer
	 */
	public static boolean isInteger(String str) {
		if (str == null || str.trim().isEmpty())
			return false;

		try {
			Integer.parseInt(str);
			return true;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * Returns an iterator over the bins in this aggregation. The iterator is a
	 * snapshot at the time of the call and will not reflect subsequent changes to
	 * the aggregation.
	 * 
	 * <p>
	 * <b>Thread-Safety:</b> This method is thread-safe. The returned iterator
	 * iterates over a defensive copy of the bin list, preventing
	 * ConcurrentModificationException.
	 * 
	 * @return An iterator over bins (never null)
	 */
	@Override
	public Iterator<Bin> iterator() {
		// Get bins with proper thread-safety (triggering lazy initialization)
		List<Bin> currentBins = getBins();

		// Return iterator over defensive copy to prevent
		// ConcurrentModificationException
		return new ArrayList<>(currentBins).iterator();
	}

	/**
	 * Returns a spliterator over bins with appropriate characteristics.
	 * 
	 * @return A spliterator with SIZED, SUBSIZED, and IMMUTABLE characteristics
	 */
	@Override
	public Spliterator<Bin> spliterator() {
		List<Bin> currentBins = getBins();
		// Create defensive copy and return spliterator
		return new ArrayList<>(currentBins).spliterator();
	}
}
