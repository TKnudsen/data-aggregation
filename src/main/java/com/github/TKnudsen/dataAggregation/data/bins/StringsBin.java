package com.github.TKnudsen.dataAggregation.data.bins;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * <p>
 * A bin for categorical (string) values with frequency counting and sorted
 * display. Efficiently tracks unique values and their frequencies.
 * </p>
 *
 * @since 2013
 */
public class StringsBin extends Bin {

	private static final Logger LOGGER = Logger.getLogger(StringsBin.class.getName());

	// Cached frequency data
	private volatile Map<String, Integer> frequencyMap;
	private volatile Integer uniqueValueCount;

	// Display configuration
	private static final int DEFAULT_TOP_VALUES_TO_SHOW = 2;
	private static final String NULL_PLACEHOLDER = "<null>";

	/**
	 * Constructs a StringsBin with string elements
	 * 
	 * @param elements        map of element IDs to string values
	 * @param aggregationName name of the parent aggregation
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public StringsBin(Map<Long, String> elements, String aggregationName) {
		super(elements, null, aggregationName);
		// Name will be generated lazily when first accessed
	}

	// ==================== TYPE-SAFE ELEMENT ACCESS ====================
	// Now delegated to Bins utility for consistency

	/**
	 * Gets elements as a properly typed map Delegates to Bins utility for type-safe
	 * access
	 * 
	 * @return map of element IDs to string values
	 */
	public Map<Long, String> getStringElements() {
		return Bins.getStringElements(this);
	}

	/**
	 * Gets a specific string element by ID
	 * 
	 * @param elementId the element ID
	 * @return the string value, or null if not found
	 */
	public String getStringElement(Long elementId) {
		if (elementId == null)
			return null;

		Object value = getElement(elementId);
		return (value instanceof String) ? (String) value : null;
	}

	// ==================== FREQUENCY ANALYSIS ====================

	/**
	 * Gets the frequency map (value -> count) Thread-safe lazy initialization with
	 * caching
	 * 
	 * @return unmodifiable map of values to their frequencies
	 */
	public Map<String, Integer> getFrequencyMap() {
		if (frequencyMap == null) {
			synchronized (this) {
				if (frequencyMap == null) {
					calculateFrequencies();
				}
			}
		}
		return frequencyMap;
	}

	/**
	 * Calculates frequency map from elements Optimized single-pass algorithm
	 */
	private void calculateFrequencies() {
		Map<String, Integer> tempMap = new HashMap<>();
		Map<Long, String> elements = getStringElements();

		if (elements != null && !elements.isEmpty()) {
			for (String value : elements.values()) {
				// Handle null values
				String key = (value != null) ? value : NULL_PLACEHOLDER;
				tempMap.merge(key, 1, Integer::sum);
			}
		}

		this.frequencyMap = Collections.unmodifiableMap(tempMap);
		this.uniqueValueCount = tempMap.size();
	}

	/**
	 * Gets the number of unique/distinct string values
	 * 
	 * @return count of unique values
	 */
	public int getNumberOfDifferentObservations() {
		if (uniqueValueCount == null)
			getFrequencyMap(); // Trigger calculation
		return uniqueValueCount;
	}

	/**
	 * Gets frequency of a specific value
	 * 
	 * @param value the value to count
	 * @return frequency, or 0 if not found
	 */
	public int getFrequency(String value) {
		String key = (value != null) ? value : NULL_PLACEHOLDER;
		return getFrequencyMap().getOrDefault(key, 0);
	}

	/**
	 * Gets the most frequent value (mode)
	 * 
	 * @return the most common value, or null if empty
	 */
	public String getMostFrequentValue() {
		Map<String, Integer> freqMap = getFrequencyMap();

		if (freqMap.isEmpty())
			return null;

		return freqMap.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
	}

	/**
	 * Gets the least frequent value
	 * 
	 * @return the least common value, or null if empty
	 */
	public String getLeastFrequentValue() {
		Map<String, Integer> freqMap = getFrequencyMap();

		if (freqMap.isEmpty())
			return null;

		return freqMap.entrySet().stream().min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
	}

	/**
	 * Gets values sorted by frequency (descending)
	 * 
	 * @return list of values from most to least frequent
	 */
	public List<String> getValuesSortedByFrequency() {
		return getFrequencyMap().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
				.map(Map.Entry::getKey).collect(Collectors.toList());
	}

	/**
	 * Gets values sorted alphabetically
	 * 
	 * @return sorted list of unique values
	 */
	public List<String> getValuesSortedAlphabetically() {
		List<String> values = new java.util.ArrayList<>(getFrequencyMap().keySet());
		Collections.sort(values);
		return values;
	}

	/**
	 * Gets top N most frequent values
	 * 
	 * @param n number of values to return
	 * @return list of top N values
	 */
	public List<String> getTopValues(int n) {
		if (n < 0)
			throw new IllegalArgumentException("N must be non-negative");

		List<String> sorted = getValuesSortedByFrequency();
		return sorted.subList(0, Math.min(n, sorted.size()));
	}

	// ==================== NAME GENERATION ====================

	@Override
	public String getName() {
		if (name == null) {
			synchronized (this) {
				if (name == null) {
					name = generateName();
				}
			}
		}
		return name;
	}

	/**
	 * Generates name based on most frequent values
	 */
	private String generateName() {
		Map<String, Integer> freqMap = getFrequencyMap();
		int uniqueCount = freqMap.size();

		// Handle empty
		if (uniqueCount == 0)
			return "[0]";

		// Single value - simple format
		if (uniqueCount == 1)
			return freqMap.keySet().iterator().next();

		// Multiple values - show top N by frequency
		List<String> topValues = getTopValues(DEFAULT_TOP_VALUES_TO_SHOW);
		return formatNameWithTopValues(topValues, uniqueCount);
	}

	/**
	 * Formats name with top values
	 */
	private String formatNameWithTopValues(List<String> topValues, int totalCount) {
		StringBuilder sb = new StringBuilder("[");

		for (int i = 0; i < topValues.size(); i++) {
			if (i > 0)
				sb.append(", ");
			sb.append(topValues.get(i));
		}

		// Add ellipsis if more values exist
		if (totalCount > topValues.size())
			sb.append(", ...");

		sb.append("]");
		return sb.toString();
	}

	/**
	 * Generates a detailed name with frequencies
	 * 
	 * @param maxValues maximum values to show
	 * @return detailed name string
	 */
	public String getNameWithFrequencies(int maxValues) {
		if (maxValues < 1)
			throw new IllegalArgumentException("maxValues must be at least 1");

		Map<String, Integer> freqMap = getFrequencyMap();

		if (freqMap.isEmpty())
			return "[0]";

		List<String> topValues = getTopValues(maxValues);
		StringBuilder sb = new StringBuilder("[");

		for (int i = 0; i < topValues.size(); i++) {
			if (i > 0)
				sb.append(", ");

			String value = topValues.get(i);
			int count = freqMap.get(value);
			sb.append(value).append("(").append(count).append(")");
		}

		if (freqMap.size() > topValues.size())
			sb.append(", ...");

		sb.append("]");
		return sb.toString();
	}

	// ==================== COMPARISON ====================

	@Override
	public int compareTo(Bin other) {
		if (other == null)
			return 1;

		if (!(other instanceof StringsBin))
			return super.compareTo(other);

		StringsBin otherBin = (StringsBin) other;

		// Compare by aggregation name
		int aggComparison = getAggregationName().compareTo(otherBin.getAggregationName());
		if (aggComparison != 0)
			return aggComparison;

		// For StringsBin, compare by most frequent value instead of name
		String thisValue = this.getMostFrequentValue();
		String otherValue = otherBin.getMostFrequentValue();

		if (thisValue == null && otherValue == null)
			return Long.compare(this.getID(), otherBin.getID());
		if (thisValue == null)
			return 1;
		if (otherValue == null)
			return -1;

		int valueComparison = thisValue.compareTo(otherValue);
		if (valueComparison != 0)
			return valueComparison;

		// Stable sort by ID
		return Long.compare(this.getID(), otherBin.getID());
	}

	// ==================== UTILITY METHODS ====================

	/**
	 * Checks if this bin contains a specific string value
	 * 
	 * @param value the value to check
	 * @return true if the value exists
	 */
	public boolean containsStringValue(String value) {
		String key = (value != null) ? value : NULL_PLACEHOLDER;
		return getFrequencyMap().containsKey(key);
	}

	/**
	 * Checks if this is a pure categorical bin (all unique values)
	 * 
	 * @return true if every element has a unique value
	 */
	public boolean isPureCategorical() {
		return getNumberOfDifferentObservations() == size();
	}

	/**
	 * Checks if this is a single-value bin (all elements identical)
	 * 
	 * @return true if only one unique value
	 */
	public boolean isSingleValue() {
		return getNumberOfDifferentObservations() == 1;
	}

	/**
	 * Gets the diversity ratio (unique values / total elements)
	 * 
	 * @return diversity ratio [0.0, 1.0]
	 */
	public double getDiversityRatio() {
		int total = size();
		if (total == 0)
			return 0.0;

		return (double) getNumberOfDifferentObservations() / total;
	}

	/**
	 * Calculates entropy (Shannon entropy) of the categorical distribution
	 * 
	 * @return entropy value
	 */
	public double getEntropy() {
		Map<String, Integer> freqMap = getFrequencyMap();
		int total = size();

		if (total == 0)
			return 0.0;

		double entropy = 0.0;
		for (int count : freqMap.values()) {
			if (count > 0) {
				double probability = (double) count / total;
				entropy -= probability * Math.log(probability) / Math.log(2);
			}
		}

		return entropy;
	}

	// ==================== CACHE MANAGEMENT ====================

	/**
	 * Clears all cached calculations
	 */
	public synchronized void clearCache() {
		frequencyMap = null;
		uniqueValueCount = null;
		name = null;
	}

	/**
	 * Warms up the cache by calculating all values
	 */
	public void warmupCache() {
		getFrequencyMap();
		getName();
	}

	// ==================== STRING REPRESENTATION ====================

	@Override
	public String toString() {
		return "StringsBin[" + "name=" + getName() + ", size=" + size() + ", unique="
				+ getNumberOfDifferentObservations() + ", diversity=" + String.format("%.2f", getDiversityRatio())
				+ "]";
	}

	/**
	 * Creates a detailed string with frequency information
	 */
	public String toDetailedString() {
		StringBuilder sb = new StringBuilder();
		sb.append("StringsBin Details:\n");
		sb.append("  ID: ").append(getID()).append("\n");
		sb.append("  Name: ").append(getName()).append("\n");
		sb.append("  Aggregation: ").append(getAggregationName()).append("\n");
		sb.append("  Total Elements: ").append(size()).append("\n");
		sb.append("  Unique Values: ").append(getNumberOfDifferentObservations()).append("\n");
		sb.append("  Diversity Ratio: ").append(String.format("%.3f", getDiversityRatio())).append("\n");
		sb.append("  Entropy: ").append(String.format("%.3f", getEntropy())).append("\n");
		sb.append("  Most Frequent: ").append(getMostFrequentValue()).append("\n");
		sb.append("  Least Frequent: ").append(getLeastFrequentValue()).append("\n");

		// Show frequency distribution
		Map<String, Integer> freqMap = getFrequencyMap();
		if (freqMap.size() <= 20) {
			sb.append("  Frequency Distribution:\n");
			List<Map.Entry<String, Integer>> sorted = freqMap.entrySet().stream()
					.sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).collect(Collectors.toList());

			for (Map.Entry<String, Integer> entry : sorted) {
				sb.append("    ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
			}
		} else {
			sb.append("  Top 20 Values by Frequency:\n");
			List<String> top20 = getTopValues(20);
			for (String value : top20) {
				sb.append("    ").append(value).append(": ").append(freqMap.get(value)).append("\n");
			}
		}

		return sb.toString();
	}

	// ==================== VALIDATION ====================

	@Override
	public boolean validate() {
		if (!super.validate())
			return false;

		try {
			// Check frequency map is calculable
			Map<String, Integer> freqMap = getFrequencyMap();
			if (freqMap == null)
				return false;

			// Check unique count matches
			int uniqueCount = getNumberOfDifferentObservations();
			if (uniqueCount != freqMap.size())
				return false;

			// Check frequencies sum to total size
			int totalFrequency = freqMap.values().stream().mapToInt(Integer::intValue).sum();

			if (totalFrequency != size())
				return false;

			// All frequencies should be positive
			if (freqMap.values().stream().anyMatch(count -> count <= 0))
				return false;

			return true;

		} catch (Exception e) {
			LOGGER.warning("Validation failed: " + e.getMessage());
			return false;
		}
	}
}
