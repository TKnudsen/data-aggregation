package com.github.TKnudsen.dataAggregation.operation.aggregation.functions;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.control.events.BinChangeEvent;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.data.bins.Bins;

/**
 * <p>
 * Base class for aggregation functions with comprehensive bin utilities
 * delegated to Bins utility class for better separation of concerns.
 * </p>
 *
 * @since 2013
 */
public abstract class AggregationFunction<T> {

	private static final Logger LOGGER = Logger.getLogger(AggregationFunction.class.getName());
	protected int aggregationLevel;
	protected Map<Long, T> elements;
	protected volatile List<Bin> bins = null;
	private final Map<Bin, BinFilterState> filterStatus = new HashMap<>();
	private final List<Bin> filteredBins = new ArrayList<>();
	protected String name;

	private final List<PropertyChangeListener> listeners = new CopyOnWriteArrayList<>();
	private boolean printOut = false;

	// ==================== INNER CLASSES ====================

	private static class BinFilterState {
		boolean isFiltered;
		int originalPosition;

		BinFilterState(boolean isFiltered, int originalPosition) {
			this.isFiltered = isFiltered;
			this.originalPosition = originalPosition;
		}
	}

	private static class BinRestoration {
		Bin bin;
		int position;

		BinRestoration(Bin bin, int position) {
			this.bin = bin;
			this.position = position;
		}
	}

	// ==================== CONSTRUCTORS ====================

	public AggregationFunction(Collection<T> values, int aggregationLevel, String name) {
		if (values == null)
			throw new IllegalArgumentException("Values collection cannot be null");
		if (aggregationLevel < 1)
			throw new IllegalArgumentException("Aggregation level must be at least 1");

		elements = new HashMap<>();
		Long L = 0L;
		for (T t : values)
			elements.put(L++, t);
		this.aggregationLevel = aggregationLevel;
		this.name = name;
	}

	public AggregationFunction(Map<Long, T> entities, int aggregationLevel, String name) {
		if (entities == null)
			throw new IllegalArgumentException("Entities map cannot be null");
		if (aggregationLevel < 1)
			throw new IllegalArgumentException("Aggregation level must be at least 1");

		this.elements = entities;
		this.aggregationLevel = aggregationLevel;
		this.name = name;
	}

	// ==================== ABSTRACT METHODS ====================

	public abstract void calculateAggregation();

	public abstract void splitBin(Bin bin, int targetCount);

	public abstract void mergeBins(List<Bin> bins);

	// ==================== BIN MANAGEMENT ====================

	/**
	 * Change the order of a bin in the bins list
	 */
	public synchronized boolean changeOrder(Bin bin, int targetIndex) {
		if (bins == null || !bins.contains(bin))
			return false;

		if (targetIndex < 0 || targetIndex >= bins.size())
			return false;

		int currentIndex = bins.indexOf(bin);
		if (currentIndex == targetIndex)
			return true;

		bins.remove(currentIndex);
		bins.add(targetIndex, bin);

		notifyListeners(new BinChangeEvent(this, bins));
		return true;
	}

	/**
	 * Filter/un-filter a bin
	 */
	public synchronized void filterBin(Bin bin) {
		if (bin == null || getBins() == null)
			return;

		BinFilterState state = filterStatus.get(bin);

		if (state == null) {
			int position = bins.indexOf(bin);
			if (position == -1)
				return;

			bins.remove(position);
			filteredBins.add(bin);
			filterStatus.put(bin, new BinFilterState(true, position));
		} else {
			if (state.isFiltered) {
				filteredBins.remove(bin);
				int restorePosition = Math.min(state.originalPosition, bins.size());
				bins.add(restorePosition, bin);
				state.isFiltered = false;
			} else {
				int currentPosition = bins.indexOf(bin);
				bins.remove(bin);
				filteredBins.add(bin);
				state.isFiltered = true;
				state.originalPosition = currentPosition;
			}
		}

		notifyListeners(new BinChangeEvent(this, bins));
	}

	/**
	 * Un-filter all bins
	 */
	public synchronized void unfilterAllBins() {
		if (filteredBins.isEmpty())
			return;

		List<BinRestoration> restorations = new ArrayList<>();
		for (Bin bin : new ArrayList<>(filteredBins)) {
			BinFilterState state = filterStatus.get(bin);
			if (state != null && state.isFiltered) {
				restorations.add(new BinRestoration(bin, state.originalPosition));
				state.isFiltered = false;
			}
		}

		restorations.sort((a, b) -> Integer.compare(a.position, b.position));

		filteredBins.clear();
		for (BinRestoration restoration : restorations) {
			int position = Math.min(restoration.position, bins.size());
			bins.add(position, restoration.bin);
		}

		notifyListeners(new BinChangeEvent(this, bins));
	}

	public boolean isFiltered(Bin bin) {
		BinFilterState state = filterStatus.get(bin);
		return state != null && state.isFiltered;
	}

	public List<Bin> getFilteredBins() {
		return Collections.unmodifiableList(filteredBins);
	}

	// ==================== BIN UTILITIES (DELEGATED TO Bins) ====================

	/**
	 * Gets total number of elements across all bins
	 */
	public int getTotalBinElementCount() {
		List<Bin> currentBins = getBinsRaw();
		return currentBins != null ? Bins.getTotalElementCount(currentBins) : 0;
	}

	/**
	 * Gets bins sorted by size
	 */
	public List<Bin> getBinsSortedBySize(boolean descending) {
		List<Bin> currentBins = getBinsRaw();
		return currentBins != null ? Bins.sortBySize(currentBins, descending) : new ArrayList<>();
	}

	/**
	 * Filters bins by minimum size
	 */
	public List<Bin> getBinsWithMinSize(int minSize) {
		List<Bin> currentBins = getBinsRaw();
		return currentBins != null ? Bins.filterByMinSize(currentBins, minSize) : new ArrayList<>();
	}

	/**
	 * Groups bins by their aggregation name
	 */
	public Map<String, List<Bin>> groupBinsByAggregation() {
		List<Bin> currentBins = getBinsRaw();
		return currentBins != null ? Bins.groupByAggregation(currentBins) : new HashMap<>();
	}

	/**
	 * Groups bins by their type
	 */
	public Map<Class<?>, List<Bin>> groupBinsByType() {
		List<Bin> currentBins = getBinsRaw();
		return currentBins != null ? Bins.groupByType(currentBins) : new HashMap<>();
	}

	/**
	 * Finds the smallest bin
	 */
	public Bin findSmallestBin() {
		List<Bin> currentBins = getBinsRaw();
		return currentBins != null ? Bins.findSmallestBin(currentBins) : null;
	}

	/**
	 * Finds the largest bin
	 */
	public Bin findLargestBin() {
		List<Bin> currentBins = getBinsRaw();
		return currentBins != null ? Bins.findLargestBin(currentBins) : null;
	}

	/**
	 * Checks if two bins can be safely merged
	 */
	public boolean canMergeBins(Bin bin1, Bin bin2) {
		return Bins.canMerge(bin1, bin2);
	}

	/**
	 * Checks if bins have overlapping elements
	 */
	public boolean hasOverlappingBins(Bin bin1, Bin bin2) {
		return Bins.hasOverlappingElements(bin1, bin2);
	}

	// ==================== CORE FUNCTIONALITY ====================

	protected void refresh() {
		if (bins != null)
			this.aggregationLevel = bins.size();
	}

	public List<Bin> getBins() {
		if (bins == null) {
			synchronized (this) {
				if (bins == null) {
					calculateAggregation();
				}
			}
		}
		return bins == null ? null : Collections.unmodifiableList(bins);
	}

	public List<Bin> getBinsRaw() {
		return bins == null ? null : Collections.unmodifiableList(bins);
	}

	public String getDescription() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getSimpleName()).append(". Bins:");

		if (bins != null) {
			for (Bin bin : bins) {
				sb.append(" ").append(bin.getName());
			}
		}

		return sb.toString();
	}

	public int getAggregationLevel() {
		return aggregationLevel;
	}

	public synchronized void setAggregationLevel(int aggregationLevel) {
		if (aggregationLevel < 1)
			throw new IllegalArgumentException("Aggregation level must be at least 1");

		if (this.aggregationLevel != aggregationLevel) {
			this.bins = null;
			this.aggregationLevel = aggregationLevel;
		}
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return getName() + ". " + getDescription();
	}

	// ==================== LISTENER MANAGEMENT ====================

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		if (listener == null)
			return;
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	public boolean removePropertyChangeListener(PropertyChangeListener listener) {
		return listeners.remove(listener);
	}

	public void clearPropertyChangeListeners() {
		listeners.clear();
	}

	public int getListenerCount() {
		return listeners.size();
	}

	protected void notifyListeners(PropertyChangeEvent event) {
		for (PropertyChangeListener listener : listeners) {
			try {
				listener.propertyChange(event);
			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Error notifying listener", e);
			}
		}
	}

	public boolean isPrintOut() {
		return printOut;
	}

	public void setPrintOut(boolean printOut) {
		this.printOut = printOut;
	}
}
