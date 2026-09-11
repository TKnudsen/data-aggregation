package com.github.TKnudsen.dataAggregation.data.bins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.github.TKnudsen.ComplexDataObject.model.io.parsers.objects.Parsers;
import com.github.TKnudsen.ComplexDataObject.model.tools.StatisticsSupport;

/**
 * <p>
 * Comprehensive utility for bin operations with functionality extracted from
 * aggregation functions for better separation of concerns.
 * </p>
 *
 * <p>
 * Categories: 1. Merging operations 2. Element access and validation 3. Numeric
 * bin utilities 4. String bin utilities 5. Multi-bin analysis 6. Sorting and
 * filtering 7. Bin comparison and selection
 * </p>
 *
 * @since 2013
 */
public final class Bins {

	private Bins() {
		throw new UnsupportedOperationException("Bins is a utility class");
	}

	// ==================== 1. MERGING OPERATIONS ====================

	/**
	 * Merges a list of bins into a single bin. All bins must be of the same type
	 * and aggregation.
	 * 
	 * @param bins List of bins to merge
	 * @return Merged bin or null if input is null/empty
	 */
	public static <B extends Bin> B merge(List<B> bins) {
		if (bins == null || bins.isEmpty())
			return null;
		if (bins.size() == 1)
			return bins.get(0);
		if (bins.size() == 2)
			return merge(bins.get(0), bins.get(1));

		validateBinCompatibility(bins);
		B referenceBin = bins.get(0);
		String aggregationName = referenceBin.getAggregationName();
		int totalCapacity = calculateTotalCapacity(bins);
		return mergeBinsByType(bins, referenceBin, aggregationName, totalCapacity);
	}

	/**
	 * Merges exactly two bins. Optimized for the most common case.
	 * 
	 * @param bin1 First bin
	 * @param bin2 Second bin
	 * @return Merged bin
	 */
	public static <B extends Bin> B merge(B bin1, B bin2) {
		if (bin1 == null && bin2 == null)
			return null;
		if (bin1 == null)
			return bin2;
		if (bin2 == null)
			return bin1;

		validateBinCompatibility(bin1, bin2);
		String aggregationName = bin1.getAggregationName();
		int totalCapacity = bin1.size() + bin2.size();
		return mergeTwoBinsByType(bin1, bin2, aggregationName, totalCapacity);
	}

	// ==================== 2. ELEMENT ACCESS & VALIDATION ====================

	/**
	 * Type-safe access to numeric elements.
	 * 
	 * @param bin NumbersBin or DiscreteNumbersBin
	 * @return Map of element IDs to numbers
	 */
	@SuppressWarnings("unchecked")
	public static Map<Long, Number> getNumericElements(Bin bin) {
		if (bin == null)
			return Collections.emptyMap();

		if (!(bin instanceof NumbersBin))
			throw new IllegalArgumentException(
					"Bin must be NumbersBin or DiscreteNumbersBin, got: " + bin.getClass().getName());

		return (Map<Long, Number>) bin.getElements();
	}

	/**
	 * Type-safe access to string elements.
	 * 
	 * @param bin StringsBin
	 * @return Map of element IDs to strings
	 */
	@SuppressWarnings("unchecked")
	public static Map<Long, String> getStringElements(Bin bin) {
		if (bin == null)
			return Collections.emptyMap();

		if (!(bin instanceof StringsBin))
			throw new IllegalArgumentException("Bin must be StringsBin, got: " + bin.getClass().getName());

		return (Map<Long, String>) bin.getElements();
	}

	/**
	 * Validates that all elements are numeric and non-null.
	 * 
	 * @param elements Map to validate
	 * @throws IllegalArgumentException if validation fails
	 */
	public static void validateNumericElements(Map<Long, Number> elements) {
		if (elements == null)
			throw new IllegalArgumentException("Elements cannot be null");

		for (Map.Entry<Long, Number> entry : elements.entrySet()) {
			Number value = entry.getValue();

			if (value == null)
				throw new IllegalArgumentException("Numeric element cannot be null for ID: " + entry.getKey());

			if (!(value instanceof Number))
				throw new IllegalArgumentException("Element must be Number for ID: " + entry.getKey());
		}
	}

	/**
	 * Validates that all values are identical (for strict discrete bins).
	 * 
	 * @param elements Map to validate
	 * @throws IllegalArgumentException if values differ
	 */
	public static void validateAllIdenticalValues(Map<Long, Number> elements) {
		if (elements == null || elements.isEmpty())
			return;

		Set<Double> distinctValues = elements.values().stream().map(Number::doubleValue).collect(Collectors.toSet());

		if (distinctValues.size() > 1)
			throw new IllegalArgumentException("All elements must have the same value in strict mode. Found "
					+ distinctValues.size() + " distinct values");
	}

	/**
	 * Gets all unique element IDs across multiple bins.
	 * 
	 * @param bins List of bins
	 * @return Set of all element IDs
	 */
	public static Set<Long> getAllElementIds(List<? extends Bin> bins) {
		Set<Long> allIds = new HashSet<>();

		if (bins != null)
			for (Bin bin : bins)
				if (bin != null && bin.getElements() != null)
					allIds.addAll(bin.getElements().keySet());

		return allIds;
	}

	// ==================== 3. NUMERIC BIN UTILITIES ====================

	/**
	 * Calculates statistics across multiple numeric bins.
	 * 
	 * @param bins List of numeric bins
	 * @return Combined statistics
	 */
	public static StatisticsSupport calculateCombinedStatistics(List<? extends NumbersBin> bins) {
		if (bins == null || bins.isEmpty())
			return new StatisticsSupport(Collections.emptyList());

		List<Double> allValues = new ArrayList<>();

		for (NumbersBin bin : bins)
			if (bin != null && bin.getElements() != null) {
				Collection<Double> values = Parsers.parseDoubles(bin.getElements().values());
				allValues.addAll(values);
			}

		return new StatisticsSupport(allValues);
	}

	/**
	 * Gets the overall range across multiple numeric bins.
	 * 
	 * @param bins List of numeric bins
	 * @return Array [min, max] or [NaN, NaN] if no valid data
	 */
	public static double[] getOverallRange(List<? extends NumbersBin> bins) {
		if (bins == null || bins.isEmpty())
			return new double[] { Double.NaN, Double.NaN };

		double min = Double.POSITIVE_INFINITY;
		double max = Double.NEGATIVE_INFINITY;

		for (NumbersBin bin : bins)
			if (bin != null) {
				double binMin = bin.getMin();
				double binMax = bin.getMax();

				if (!Double.isNaN(binMin))
					min = Math.min(min, binMin);
				if (!Double.isNaN(binMax))
					max = Math.max(max, binMax);
			}

		if (Double.isInfinite(min) || Double.isInfinite(max))
			return new double[] { Double.NaN, Double.NaN };

		return new double[] { min, max };
	}

	/**
	 * Gets the weighted mean across multiple bins.
	 * 
	 * @param bins List of numeric bins
	 * @return Weighted mean or NaN if no valid data
	 */
	public static double getWeightedMean(List<? extends NumbersBin> bins) {
		if (bins == null || bins.isEmpty())
			return Double.NaN;

		double weightedSum = 0.0;
		int totalCount = 0;

		for (NumbersBin bin : bins)
			if (bin != null && bin.size() > 0) {
				double mean = bin.getMean();
				if (!Double.isNaN(mean)) {
					weightedSum += mean * bin.size();
					totalCount += bin.size();
				}
			}

		return totalCount > 0 ? weightedSum / totalCount : Double.NaN;
	}

	/**
	 * Gets all unique discrete values across multiple discrete bins.
	 * 
	 * @param bins List of discrete bins
	 * @return Set of all unique values
	 */
	public static Set<Number> getAllDiscreteValues(List<DiscreteNumbersBin> bins) {
		Set<Number> allValues = new HashSet<>();

		if (bins != null)
			for (DiscreteNumbersBin bin : bins)
				if (bin != null)
					allValues.addAll(bin.getUniqueValues());

		return allValues;
	}

	/**
	 * Checks if all bins are truly discrete (single value).
	 * 
	 * @param bins List of discrete bins
	 * @return true if all bins contain only one unique value
	 */
	public static boolean areAllTrulyDiscrete(List<DiscreteNumbersBin> bins) {
		if (bins == null || bins.isEmpty())
			return false;

		for (DiscreteNumbersBin bin : bins)
			if (bin != null && !bin.isTrulyDiscrete())
				return false;

		return true;
	}

	/**
	 * Extracts the minimum value from a numeric bin.
	 * 
	 * @param bin NumbersBin to analyze
	 * @return Minimum value or POSITIVE_INFINITY if empty
	 */
	public static double getMinValue(NumbersBin bin) {
		if (bin == null)
			return Double.POSITIVE_INFINITY;

		return bin.getMin();
	}

	/**
	 * Extracts the maximum value from a numeric bin.
	 * 
	 * @param bin NumbersBin to analyze
	 * @return Maximum value or NEGATIVE_INFINITY if empty
	 */
	public static double getMaxValue(NumbersBin bin) {
		if (bin == null)
			return Double.NEGATIVE_INFINITY;

		return bin.getMax();
	}

	/**
	 * Extracts the range (max - min) from a numeric bin.
	 * 
	 * @param bin NumbersBin to analyze
	 * @return Range or NaN if bin is empty
	 */
	public static double getRange(NumbersBin bin) {
		if (bin == null)
			return Double.NaN;

		double min = bin.getMin();
		double max = bin.getMax();

		if (Double.isNaN(min) || Double.isNaN(max))
			return Double.NaN;

		return max - min;
	}

	// ==================== 4. STRING BIN UTILITIES ====================

	/**
	 * Aggregates frequency maps across multiple string bins.
	 * 
	 * @param bins List of string bins
	 * @return Combined frequency map
	 */
	public static Map<String, Integer> aggregateFrequencies(List<StringsBin> bins) {
		Map<String, Integer> combined = new HashMap<>();

		if (bins != null)
			for (StringsBin bin : bins)
				if (bin != null) {
					Map<String, Integer> binFreqs = bin.getFrequencyMap();
					for (Map.Entry<String, Integer> entry : binFreqs.entrySet()) {
						combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
					}
				}

		return combined;
	}

	/**
	 * Gets all unique string values across multiple bins.
	 * 
	 * @param bins List of string bins
	 * @return Set of all unique strings
	 */
	public static Set<String> getAllUniqueStrings(List<StringsBin> bins) {
		Set<String> allValues = new HashSet<>();

		if (bins != null)
			for (StringsBin bin : bins)
				if (bin != null)
					allValues.addAll(bin.getFrequencyMap().keySet());

		return allValues;
	}

	/**
	 * Gets the most frequent value across all bins.
	 * 
	 * @param bins List of string bins
	 * @return Most frequent string or null if no data
	 */
	public static String getMostFrequentValueAcrossBins(List<StringsBin> bins) {
		Map<String, Integer> aggregated = aggregateFrequencies(bins);

		if (aggregated.isEmpty())
			return null;

		return aggregated.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
	}

	/**
	 * Calculates combined diversity ratio across bins.
	 * 
	 * @param bins List of string bins
	 * @return Diversity ratio (unique count / total count)
	 */
	public static double getCombinedDiversityRatio(List<StringsBin> bins) {
		if (bins == null || bins.isEmpty())
			return 0.0;

		int uniqueCount = getAllUniqueStrings(bins).size();
		int totalElements = getTotalElementCount(bins);

		return totalElements > 0 ? (double) uniqueCount / totalElements : 0.0;
	}

	/**
	 * Calculates combined entropy across bins.
	 * 
	 * @param bins List of string bins
	 * @return Shannon entropy
	 */
	public static double getCombinedEntropy(List<StringsBin> bins) {
		Map<String, Integer> aggregated = aggregateFrequencies(bins);
		int total = aggregated.values().stream().mapToInt(Integer::intValue).sum();

		if (total == 0)
			return 0.0;

		double entropy = 0.0;
		for (int count : aggregated.values())
			if (count > 0) {
				double probability = (double) count / total;
				entropy -= probability * Math.log(probability) / Math.log(2);
			}

		return entropy;
	}

	// ==================== 5. MULTI-BIN ANALYSIS ====================

	/**
	 * Checks if two bins can be merged.
	 * 
	 * @param bin1 First bin
	 * @param bin2 Second bin
	 * @return true if bins are compatible for merging
	 */
	public static boolean canMerge(Bin bin1, Bin bin2) {
		if (bin1 == null || bin2 == null)
			return false;

		if (!bin1.getClass().equals(bin2.getClass()))
			return false;

		return Objects.equals(bin1.getAggregationName(), bin2.getAggregationName());
	}

	/**
	 * Checks if a list of bins can be merged.
	 * 
	 * @param bins List of bins
	 * @return true if all bins are compatible
	 */
	public static <B extends Bin> boolean canMerge(List<B> bins) {
		if (bins == null || bins.isEmpty())
			return false;

		if (bins.size() == 1)
			return true;

		try {
			validateBinCompatibility(bins);
			return true;
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * Checks if bins have overlapping element IDs.
	 * 
	 * @param bin1 First bin
	 * @param bin2 Second bin
	 * @return true if any element IDs overlap
	 */
	public static boolean hasOverlappingElements(Bin bin1, Bin bin2) {
		if (bin1 == null || bin2 == null)
			return false;

		Map<Long, ?> elements1 = bin1.getElements();
		Map<Long, ?> elements2 = bin2.getElements();

		Map<Long, ?> smaller = elements1.size() <= elements2.size() ? elements1 : elements2;
		Map<Long, ?> larger = elements1.size() > elements2.size() ? elements1 : elements2;

		for (Long id : smaller.keySet())
			if (larger.containsKey(id))
				return true;

		return false;
	}

	/**
	 * Gets the set of overlapping element IDs.
	 * 
	 * @param bin1 First bin
	 * @param bin2 Second bin
	 * @return Set of overlapping IDs
	 */
	public static Set<Long> getOverlappingElementIds(Bin bin1, Bin bin2) {
		if (bin1 == null || bin2 == null)
			return Collections.emptySet();

		Set<Long> intersection = new HashSet<>(bin1.getElements().keySet());
		intersection.retainAll(bin2.getElements().keySet());
		return intersection;
	}

	/**
	 * Counts total elements across multiple bins.
	 * 
	 * @param bins List of bins
	 * @return Total element count
	 */
	public static <B extends Bin> int getTotalElementCount(List<B> bins) {
		if (bins == null || bins.isEmpty())
			return 0;

		int total = 0;
		for (B bin : bins)
			if (bin != null)
				total += bin.size();

		return total;
	}

	/**
	 * Groups bins by their aggregation name.
	 * 
	 * @param bins List of bins
	 * @return Map from aggregation name to bins
	 */
	public static Map<String, List<Bin>> groupByAggregation(List<? extends Bin> bins) {
		if (bins == null || bins.isEmpty())
			return new HashMap<>();

		return bins.stream().filter(Objects::nonNull).collect(Collectors.groupingBy(Bin::getAggregationName));
	}

	/**
	 * Groups bins by their type.
	 * 
	 * @param bins List of bins
	 * @return Map from bin class to bins
	 */
	public static Map<Class<?>, List<Bin>> groupByType(List<? extends Bin> bins) {
		if (bins == null || bins.isEmpty())
			return new HashMap<>();

		return bins.stream().filter(Objects::nonNull).collect(Collectors.groupingBy(Bin::getClass));
	}

	/**
	 * Gets the most common bin type in a list.
	 * 
	 * @param bins List of bins
	 * @return Most common bin class or null
	 */
	public static <B extends Bin> Class<?> getMostCommonBinType(List<B> bins) {
		if (bins == null || bins.isEmpty())
			return null;

		Map<Class<?>, Integer> typeCounts = new HashMap<>();

		for (B bin : bins) {
			if (bin != null) {
				Class<?> type = bin.getClass();
				typeCounts.put(type, typeCounts.getOrDefault(type, 0) + 1);
			}
		}

		return typeCounts.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
	}

	/**
	 * Calculates Jaccard similarity between two bins based on element IDs.
	 * 
	 * @param bin1 First bin
	 * @param bin2 Second bin
	 * @return Jaccard similarity [0.0, 1.0]
	 */
	public static double calculateJaccardSimilarity(Bin bin1, Bin bin2) {
		if (bin1 == null || bin2 == null || bin1.isEmpty() || bin2.isEmpty())
			return 0.0;

		Set<Long> ids1 = bin1.getElements().keySet();
		Set<Long> ids2 = bin2.getElements().keySet();

		Set<Long> intersection = new HashSet<>(ids1);
		intersection.retainAll(ids2);

		Set<Long> union = new HashSet<>(ids1);
		union.addAll(ids2);

		return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
	}

	// ==================== 6. SORTING AND FILTERING ====================

	/**
	 * Filters bins by minimum size.
	 * 
	 * @param bins    List of bins
	 * @param minSize Minimum size threshold
	 * @return Filtered list
	 */
	public static <B extends Bin> List<B> filterByMinSize(List<B> bins, int minSize) {
		if (bins == null)
			return new ArrayList<>();

		return bins.stream().filter(bin -> bin != null && bin.size() >= minSize).collect(Collectors.toList());
	}

	/**
	 * Gets bins sorted by size.
	 * 
	 * @param bins       List of bins
	 * @param descending Sort order
	 * @return Sorted list
	 */
	public static <B extends Bin> List<B> sortBySize(List<B> bins, boolean descending) {
		if (bins == null)
			return new ArrayList<>();

		List<B> sorted = new ArrayList<>(bins);
		sorted.sort(createBinComparator((b1, b2) -> Integer.compare(b1.size(), b2.size()), descending));

		return sorted;
	}

	/**
	 * Sorts numeric bins by centroid value.
	 * 
	 * @param bins       List of numeric bins
	 * @param descending Sort order
	 * @return Sorted list
	 */
	public static List<NumbersBin> sortByCentroid(List<NumbersBin> bins, boolean descending) {
		if (bins == null)
			return new ArrayList<>();

		List<NumbersBin> sorted = new ArrayList<>(bins);
		sorted.sort(createBinComparator(
				(b1, b2) -> Double.compare(b1.getCentroid().doubleValue(), b2.getCentroid().doubleValue()),
				descending));

		return sorted;
	}

	/**
	 * Sorts numeric bins by mean value.
	 * 
	 * @param bins       List of numeric bins
	 * @param descending Sort order
	 * @return Sorted list
	 */
	public static List<NumbersBin> sortByMean(List<NumbersBin> bins, boolean descending) {
		if (bins == null)
			return new ArrayList<>();

		List<NumbersBin> sorted = new ArrayList<>(bins);
		sorted.sort(createBinComparator((b1, b2) -> Double.compare(b1.getMean(), b2.getMean()), descending));

		return sorted;
	}

	/**
	 * Sorts numeric bins by minimum value (for semantic ordering).
	 * 
	 * @param bins       List of numeric bins
	 * @param descending Sort order (false for ascending, typical use case)
	 * @return Sorted list
	 */
	public static List<NumbersBin> sortByMinValue(List<NumbersBin> bins, boolean descending) {
		if (bins == null)
			return new ArrayList<>();

		List<NumbersBin> sorted = new ArrayList<>(bins);
		sorted.sort(createBinComparator((b1, b2) -> Double.compare(getMinValue(b1), getMinValue(b2)), descending));

		return sorted;
	}

	/**
	 * Sorts numeric bins by maximum value.
	 * 
	 * @param bins       List of numeric bins
	 * @param descending Sort order
	 * @return Sorted list
	 */
	public static List<NumbersBin> sortByMaxValue(List<NumbersBin> bins, boolean descending) {
		if (bins == null)
			return new ArrayList<>();

		List<NumbersBin> sorted = new ArrayList<>(bins);
		sorted.sort(createBinComparator((b1, b2) -> Double.compare(getMaxValue(b1), getMaxValue(b2)), descending));

		return sorted;
	}

	/**
	 * Sorts numeric bins by range (max - min).
	 * 
	 * @param bins       List of numeric bins
	 * @param descending Sort order
	 * @return Sorted list
	 */
	public static List<NumbersBin> sortByRange(List<NumbersBin> bins, boolean descending) {
		if (bins == null)
			return new ArrayList<>();

		List<NumbersBin> sorted = new ArrayList<>(bins);
		sorted.sort(createBinComparator((b1, b2) -> Double.compare(getRange(b1), getRange(b2)), descending));

		return sorted;
	}

	// ==================== 7. BIN COMPARISON AND SELECTION ====================

	/**
	 * Finds the bin with the minimum size.
	 * 
	 * @param bins List of bins
	 * @return Smallest bin or null
	 */
	public static <B extends Bin> B findSmallestBin(List<B> bins) {
		if (bins == null || bins.isEmpty())
			return null;

		return bins.stream().filter(Objects::nonNull).min((b1, b2) -> Integer.compare(b1.size(), b2.size()))
				.orElse(null);
	}

	/**
	 * Finds the bin with the maximum size.
	 * 
	 * @param bins List of bins
	 * @return Largest bin or null
	 */
	public static <B extends Bin> B findLargestBin(List<B> bins) {
		if (bins == null || bins.isEmpty())
			return null;

		return bins.stream().filter(Objects::nonNull).max((b1, b2) -> Integer.compare(b1.size(), b2.size()))
				.orElse(null);
	}

	/**
	 * Finds the index of the smallest neighboring pair. Useful for efficient
	 * merging strategies.
	 * 
	 * @param bins List of bins
	 * @return Index of first bin in smallest pair, or -1 if not enough bins
	 */
	public static <B extends Bin> int findSmallestNeighboringPair(List<B> bins) {
		if (bins == null || bins.size() < 2)
			return -1;

		int minIndex = 0;
		int minSize = bins.get(0).size() + bins.get(1).size();

		for (int i = 1; i < bins.size() - 1; i++) {
			int combinedSize = bins.get(i).size() + bins.get(i + 1).size();
			if (combinedSize < minSize) {
				minIndex = i;
				minSize = combinedSize;
			}
		}

		return minIndex;
	}

	/**
	 * Checks if bins list contains only one unique bin type.
	 * 
	 * @param bins List of bins
	 * @return true if all bins have the same type
	 */
	public static boolean hasUniformType(List<? extends Bin> bins) {
		if (bins == null || bins.isEmpty())
			return true;

		Class<?> firstType = null;
		for (Bin bin : bins) {
			if (bin != null) {
				if (firstType == null) {
					firstType = bin.getClass();
				} else if (!bin.getClass().equals(firstType)) {
					return false;
				}
			}
		}

		return true;
	}

	// ==================== PRIVATE HELPER METHODS ====================

	/**
	 * Creates a null-safe comparator with optional reverse order.
	 * 
	 * @param baseComparator Base comparison logic
	 * @param descending     Reverse order flag
	 * @return Comparator that handles nulls and direction
	 */
	private static <T> Comparator<T> createBinComparator(Comparator<T> baseComparator, boolean descending) {
		return (b1, b2) -> {
			if (b1 == null && b2 == null)
				return 0;
			if (b1 == null)
				return descending ? 1 : -1;
			if (b2 == null)
				return descending ? -1 : 1;

			int comparison = baseComparator.compare(b1, b2);
			return descending ? -comparison : comparison;
		};
	}

	@SuppressWarnings("unchecked")
	private static <B extends Bin> B mergeBinsByType(List<B> bins, B referenceBin, String aggregationName,
			int totalCapacity) {

		if (referenceBin instanceof DiscreteNumbersBin) {
			Map<Long, Number> merged = mergeNumericElements(bins, totalCapacity);
			return (B) new DiscreteNumbersBin(merged, aggregationName);
		}
		if (referenceBin instanceof NumbersBin) {
			Map<Long, Number> merged = mergeNumericElements(bins, totalCapacity);
			return (B) new NumbersBin(merged, aggregationName);
		}
		if (referenceBin instanceof StringsBin) {
			Map<Long, String> merged = mergeStringElements(bins, totalCapacity);
			return (B) new StringsBin(merged, aggregationName);
		}

		throw new IllegalArgumentException("Unsupported bin type: " + referenceBin.getClass().getName());
	}

	@SuppressWarnings("unchecked")
	private static <B extends Bin> B mergeTwoBinsByType(B bin1, B bin2, String aggregationName, int totalCapacity) {

		if (bin1 instanceof DiscreteNumbersBin) {
			Map<Long, Number> merged = mergeTwoNumericMaps((Map<Long, Number>) bin1.getElements(),
					(Map<Long, Number>) bin2.getElements(), totalCapacity);
			return (B) new DiscreteNumbersBin(merged, aggregationName);
		}
		if (bin1 instanceof NumbersBin) {
			Map<Long, Number> merged = mergeTwoNumericMaps((Map<Long, Number>) bin1.getElements(),
					(Map<Long, Number>) bin2.getElements(), totalCapacity);
			return (B) new NumbersBin(merged, aggregationName);
		}
		if (bin1 instanceof StringsBin) {
			Map<Long, String> merged = mergeTwoStringMaps((Map<Long, String>) bin1.getElements(),
					(Map<Long, String>) bin2.getElements(), totalCapacity);
			return (B) new StringsBin(merged, aggregationName);
		}

		throw new IllegalArgumentException("Unsupported bin type: " + bin1.getClass().getName());
	}

	@SuppressWarnings("unchecked")
	private static <B extends Bin> Map<Long, Number> mergeNumericElements(List<B> bins, int initialCapacity) {

		Map<Long, Number> merged = new HashMap<>(initialCapacity);

		for (B bin : bins) {
			Map<Long, Number> elements = (Map<Long, Number>) bin.getElements();
			for (Long id : elements.keySet()) {
				if (merged.containsKey(id))
					throw new IllegalArgumentException("Duplicate element ID: " + id);
			}
			merged.putAll(elements);
		}

		return merged;
	}

	@SuppressWarnings("unchecked")
	private static <B extends Bin> Map<Long, String> mergeStringElements(List<B> bins, int initialCapacity) {

		Map<Long, String> merged = new HashMap<>(initialCapacity);

		for (B bin : bins) {
			Map<Long, String> elements = (Map<Long, String>) bin.getElements();
			for (Long id : elements.keySet()) {
				if (merged.containsKey(id))
					throw new IllegalArgumentException("Duplicate element ID: " + id);
			}
			merged.putAll(elements);
		}

		return merged;
	}

	private static Map<Long, Number> mergeTwoNumericMaps(Map<Long, Number> map1, Map<Long, Number> map2,
			int initialCapacity) {

		Map<Long, Number> merged = new HashMap<>(initialCapacity);
		merged.putAll(map1);

		for (Map.Entry<Long, Number> entry : map2.entrySet()) {
			if (merged.containsKey(entry.getKey()))
				throw new IllegalArgumentException("Duplicate element ID: " + entry.getKey());
			merged.put(entry.getKey(), entry.getValue());
		}

		return merged;
	}

	private static Map<Long, String> mergeTwoStringMaps(Map<Long, String> map1, Map<Long, String> map2,
			int initialCapacity) {

		Map<Long, String> merged = new HashMap<>(initialCapacity);
		merged.putAll(map1);

		for (Map.Entry<Long, String> entry : map2.entrySet()) {
			if (merged.containsKey(entry.getKey()))
				throw new IllegalArgumentException("Duplicate element ID: " + entry.getKey());
			merged.put(entry.getKey(), entry.getValue());
		}

		return merged;
	}

	private static <B extends Bin> void validateBinCompatibility(List<B> bins) {
		if (bins == null || bins.isEmpty())
			return;

		B referenceBin = bins.get(0);
		if (referenceBin == null)
			throw new IllegalArgumentException("Reference bin is null");

		Class<?> referenceClass = referenceBin.getClass();
		String referenceAggregation = referenceBin.getAggregationName();

		for (int i = 1; i < bins.size(); i++) {
			B bin = bins.get(i);

			if (bin == null)
				throw new IllegalArgumentException("Null bin at index " + i);

			if (!bin.getClass().equals(referenceClass))
				throw new IllegalArgumentException("Type mismatch at index " + i + ": " + referenceClass.getSimpleName()
						+ " vs " + bin.getClass().getSimpleName());

			if (!Objects.equals(bin.getAggregationName(), referenceAggregation))
				throw new IllegalArgumentException("Aggregation mismatch at index " + i);
		}
	}

	private static <B extends Bin> void validateBinCompatibility(B bin1, B bin2) {
		if (!bin1.getClass().equals(bin2.getClass()))
			throw new IllegalArgumentException(
					"Type mismatch: " + bin1.getClass().getSimpleName() + " vs " + bin2.getClass().getSimpleName());

		if (!Objects.equals(bin1.getAggregationName(), bin2.getAggregationName()))
			throw new IllegalArgumentException("Aggregation name mismatch");
	}

	private static <B extends Bin> int calculateTotalCapacity(List<B> bins) {
		int total = 0;
		for (B bin : bins)
			if (bin != null)
				total += bin.size();

		return total;
	}
}
