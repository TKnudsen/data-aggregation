package com.github.TKnudsen.dataAggregation.data.aggregation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;

/**
 * Static utility methods for working with {@link Aggregation} instances.
 *
 * @since 2013
 */
public class Aggregations {

	private static final Logger LOGGER = Logger.getLogger(Aggregations.class.getName());

	public static int getElementsSize(Aggregation aggregation) {
		return aggregation.getIndex().size();
	}

	public static boolean containsBin(Aggregation aggregation, Bin bin) {
		if (aggregation == null || bin == null)
			return false;

		for (Bin b : aggregation)
			if (b.equals(bin))
				return true;

		return false;
	}

	public static Aggregation getAggregation(Bin bin, Collection<Aggregation> aggregations, boolean considerFiltered) {
		for (Aggregation aggregation : aggregations) {
			if (considerFiltered)
				if (aggregation.isFiltered(bin))
					return aggregation;
				else {
				}

			if (aggregation.getBins().contains(bin))
				return aggregation;
			else {
			}
		}

		return null;
	}

	/**
	 * 
	 * @param aggregation
	 * @param considerBinFilterStatus true means filtered bins are ignored
	 * @return
	 */
	public static List<List<Long>> getMapping(Aggregation aggregation, boolean considerBinFilterStatus) {
		List<List<Long>> mapping = new ArrayList<>();

		for (Bin bin : aggregation) {
			if (considerBinFilterStatus && aggregation.isFiltered(bin))
				continue;

			List<Long> entityIds = new ArrayList<>(bin.getElements().keySet());
			mapping.add(entityIds);
		}

		return mapping;
	}

	// ==================== BASIC EQUALITY ====================

	/**
	 * Checks if two aggregations have identical bin structures.
	 * 
	 * <p>
	 * Two aggregations have equal bins if they have the same number of bins and
	 * each bin contains the same entity IDs in the same order.
	 * </p>
	 * 
	 * <p>
	 * <b>Use Cases:</b>
	 * </p>
	 * <ul>
	 * <li>Verifying aggregation recalculation didn't change structure</li>
	 * <li>Testing aggregation functions</li>
	 * <li>Comparing aggregation strategies</li>
	 * </ul>
	 * 
	 * <p>
	 * <b>Example:</b>
	 * </p>
	 * 
	 * <pre>
	 * if (AggregationComparator.equalBins(agg1, agg2)) {
	 * 	System.out.println("Aggregations have identical bin structures");
	 * }
	 * </pre>
	 * 
	 * @param aggregation1 First aggregation
	 * @param aggregation2 Second aggregation
	 * @return true if bins are identical (same order, same content)
	 */
	public static boolean equalBins(Aggregation aggregation1, Aggregation aggregation2) {
		// Null checks
		if (aggregation1 == null && aggregation2 == null) {
			return true;
		}
		if (aggregation1 == null || aggregation2 == null) {
			return false;
		}

		// Identity check
		if (aggregation1 == aggregation2) {
			return true;
		}

		List<Bin> bins1 = aggregation1.getBins();
		List<Bin> bins2 = aggregation2.getBins();

		// Check bin count
		if (bins1.size() != bins2.size()) {
			if (LOGGER.isLoggable(Level.FINE))
				LOGGER.fine(String.format("Bin count mismatch: %s has %d bins, %s has %d bins",
						aggregation1.getName(), bins1.size(), aggregation2.getName(), bins2.size()));
			return false;
		}

		// Compare each bin's entity IDs
		for (int i = 0; i < bins1.size(); i++) {
			Bin bin1 = bins1.get(i);
			Bin bin2 = bins2.get(i);

			Set<Long> entities1 = bin1.getElements().keySet();
			Set<Long> entities2 = bin2.getElements().keySet();

			if (!entities1.equals(entities2)) {
				System.out
						.println(String.format("Aggregations: Bin %d differs: '%s' (%d entities) vs '%s' (%d entities)",
								i, bin1.getName(), entities1.size(), bin2.getName(), entities2.size()));
				return false;
			}
		}

		return true;
	}

	/**
	 * Checks if two aggregations have equivalent bin structures regardless of
	 * order.
	 * 
	 * <p>
	 * Two aggregations are equivalent if they partition entities into the same
	 * groups, even if those groups appear in different orders or have different
	 * names.
	 * </p>
	 * 
	 * <p>
	 * <b>Example:</b>
	 * </p>
	 * 
	 * <pre>
	 * // agg1: [bin1: {1,2,3}, bin2: {4,5}]
	 * // agg2: [binA: {4,5}, binB: {1,2,3}]
	 * boolean equal = AggregationComparator.equalBinsUnordered(agg1, agg2); // true
	 * </pre>
	 * 
	 * @param aggregation1 First aggregation
	 * @param aggregation2 Second aggregation
	 * @return true if bin groupings are equivalent (order-independent)
	 */
	public static boolean equalBinsUnordered(Aggregation aggregation1, Aggregation aggregation2) {
		// Null checks
		if (aggregation1 == null && aggregation2 == null) {
			return true;
		}
		if (aggregation1 == null || aggregation2 == null) {
			return false;
		}

		// Identity check
		if (aggregation1 == aggregation2) {
			return true;
		}

		List<Bin> bins1 = aggregation1.getBins();
		List<Bin> bins2 = aggregation2.getBins();

		// Check bin count
		if (bins1.size() != bins2.size()) {
			return false;
		}

		// Build sets of entity ID sets for each aggregation
		Set<Set<Long>> entitySets1 = new HashSet<>();
		for (Bin bin : bins1) {
			entitySets1.add(new HashSet<>(bin.getElements().keySet()));
		}

		Set<Set<Long>> entitySets2 = new HashSet<>();
		for (Bin bin : bins2) {
			entitySets2.add(new HashSet<>(bin.getElements().keySet()));
		}

		return entitySets1.equals(entitySets2);
	}

	/**
	 * Checks if two aggregations have strictly equal bins (including names).
	 * 
	 * <p>
	 * Two aggregations have strictly equal bins if:
	 * </p>
	 * <ul>
	 * <li>They have the same number of bins</li>
	 * <li>Each bin has the same name (in order)</li>
	 * <li>Each bin contains the same entity IDs</li>
	 * </ul>
	 * 
	 * @param aggregation1 First aggregation
	 * @param aggregation2 Second aggregation
	 * @return true if bins are strictly equal
	 */
	public static boolean equalBinsStrict(Aggregation aggregation1, Aggregation aggregation2) {
		// Null checks
		if (aggregation1 == null && aggregation2 == null) {
			return true;
		}
		if (aggregation1 == null || aggregation2 == null) {
			return false;
		}

		// Identity check
		if (aggregation1 == aggregation2) {
			return true;
		}

		List<Bin> bins1 = aggregation1.getBins();
		List<Bin> bins2 = aggregation2.getBins();

		// Check bin count
		if (bins1.size() != bins2.size()) {
			return false;
		}

		// Compare each bin strictly (name + content)
		for (int i = 0; i < bins1.size(); i++) {
			Bin bin1 = bins1.get(i);
			Bin bin2 = bins2.get(i);

			// Compare bin names
			if (!Objects.equals(bin1.getName(), bin2.getName())) {
				return false;
			}

			// Compare entity ID sets
			Set<Long> entities1 = bin1.getElements().keySet();
			Set<Long> entities2 = bin2.getElements().keySet();

			if (!entities1.equals(entities2)) {
				return false;
			}
		}

		return true;
	}

	// ==================== SIMILARITY METRICS ====================

	/**
	 * Computes Jaccard similarity between two aggregation bin structures.
	 * 
	 * <p>
	 * Returns a value between 0.0 (completely different) and 1.0 (identical). Based
	 * on the proportion of entity pairs that are grouped together in both
	 * aggregations.
	 * </p>
	 * 
	 * <p>
	 * <b>Algorithm:</b> Jaccard similarity of entity pair sets
	 * </p>
	 * 
	 * <pre>
	 * similarity = |intersection| / |union|
	 * </pre>
	 * 
	 * <p>
	 * <b>Example:</b>
	 * </p>
	 * 
	 * <pre>
	 * double sim = AggregationComparator.binSimilarity(agg1, agg2);
	 * System.out.printf("Bins are %.1f%% similar\n", sim * 100);
	 * </pre>
	 * 
	 * @param aggregation1 First aggregation
	 * @param aggregation2 Second aggregation
	 * @return Similarity score [0.0, 1.0]
	 */
	public static double binSimilarity(Aggregation aggregation1, Aggregation aggregation2) {
		// Null checks
		if (aggregation1 == null || aggregation2 == null) {
			return 0.0;
		}

		// Identity check
		if (aggregation1 == aggregation2) {
			return 1.0;
		}

		List<Bin> bins1 = aggregation1.getBins();
		List<Bin> bins2 = aggregation2.getBins();

		// Build entity pair sets (entities that appear together in a bin)
		Set<String> pairs1 = buildEntityPairSet(bins1);
		Set<String> pairs2 = buildEntityPairSet(bins2);

		// Handle empty cases
		if (pairs1.isEmpty() && pairs2.isEmpty()) {
			return 1.0;
		}

		// Compute Jaccard similarity
		Set<String> intersection = new HashSet<>(pairs1);
		intersection.retainAll(pairs2);

		Set<String> union = new HashSet<>(pairs1);
		union.addAll(pairs2);

		double similarity = union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();

		if (LOGGER.isLoggable(Level.FINE))
			LOGGER.fine(String.format("Bin similarity: %s vs %s = %.3f (intersection=%d, union=%d)",
					aggregation1.getName(), aggregation2.getName(), similarity, intersection.size(), union.size()));

		return similarity;
	}

	/**
	 * Builds a set of entity pair strings for Jaccard similarity calculation.
	 */
	private static Set<String> buildEntityPairSet(List<Bin> bins) {
		Set<String> pairs = new HashSet<>();

		for (Bin bin : bins) {
			List<Long> entities = new ArrayList<>(bin.getElements().keySet());

			// Generate all pairs within this bin
			for (int i = 0; i < entities.size(); i++) {
				for (int j = i + 1; j < entities.size(); j++) {
					long e1 = Math.min(entities.get(i), entities.get(j));
					long e2 = Math.max(entities.get(i), entities.get(j));
					pairs.add(e1 + "," + e2);
				}
			}
		}

		return pairs;
	}

	/**
	 * Computes Adjusted Rand Index (ARI) between two aggregations.
	 * 
	 * <p>
	 * The Adjusted Rand Index measures the similarity of two clusterings, adjusted
	 * for chance. Returns a value between -1.0 (completely different) and 1.0
	 * (identical), with 0.0 indicating random clustering.
	 * </p>
	 * 
	 * <p>
	 * <b>Interpretation:</b>
	 * </p>
	 * <ul>
	 * <li>1.0: Perfect agreement</li>
	 * <li>0.0: Random agreement (no better than chance)</li>
	 * <li>Negative: Less agreement than expected by chance</li>
	 * </ul>
	 * 
	 * @param aggregation1 First aggregation
	 * @param aggregation2 Second aggregation
	 * @return Adjusted Rand Index [-1.0, 1.0]
	 */
	public static double adjustedRandIndex(Aggregation aggregation1, Aggregation aggregation2) {
		if (aggregation1 == null || aggregation2 == null) {
			return -1.0;
		}

		if (aggregation1 == aggregation2) {
			return 1.0;
		}

		// Build entity-to-cluster mappings
		Map<Long, Integer> clustering1 = buildEntityClusterMap(aggregation1.getBins());
		Map<Long, Integer> clustering2 = buildEntityClusterMap(aggregation2.getBins());

		// Get common entities
		Set<Long> commonEntities = new HashSet<>(clustering1.keySet());
		commonEntities.retainAll(clustering2.keySet());

		if (commonEntities.isEmpty()) {
			return 0.0;
		}

		// Build contingency table
		Map<String, Integer> contingency = new HashMap<>();
		for (Long entity : commonEntities) {
			int cluster1 = clustering1.get(entity);
			int cluster2 = clustering2.get(entity);
			String key = cluster1 + "," + cluster2;
			contingency.merge(key, 1, Integer::sum);
		}

		// Calculate ARI components
		int n = commonEntities.size();

		// Sum of combinations in contingency table
		int sumComb = 0;
		for (int count : contingency.values()) {
			sumComb += comb2(count);
		}

		// Sum of combinations in each clustering
		Map<Integer, Integer> counts1 = new HashMap<>();
		Map<Integer, Integer> counts2 = new HashMap<>();

		for (Long entity : commonEntities) {
			counts1.merge(clustering1.get(entity), 1, Integer::sum);
			counts2.merge(clustering2.get(entity), 1, Integer::sum);
		}

		int sumComb1 = 0;
		for (int count : counts1.values()) {
			sumComb1 += comb2(count);
		}

		int sumComb2 = 0;
		for (int count : counts2.values()) {
			sumComb2 += comb2(count);
		}

		// Calculate ARI
		double expectedIndex = (double) (sumComb1 * sumComb2) / comb2(n);
		double maxIndex = (double) (sumComb1 + sumComb2) / 2.0;
		double index = sumComb;

		if (maxIndex - expectedIndex == 0) {
			return 1.0;
		}

		double ari = (index - expectedIndex) / (maxIndex - expectedIndex);

		if (LOGGER.isLoggable(Level.FINE))
			LOGGER.fine(String.format("Adjusted Rand Index: %s vs %s = %.3f", aggregation1.getName(),
					aggregation2.getName(), ari));

		return ari;
	}

	/**
	 * Builds entity-to-cluster-index mapping.
	 */
	private static Map<Long, Integer> buildEntityClusterMap(List<Bin> bins) {
		Map<Long, Integer> map = new HashMap<>();

		for (int i = 0; i < bins.size(); i++) {
			for (Long entityId : bins.get(i).getElements().keySet()) {
				map.put(entityId, i);
			}
		}

		return map;
	}

	/**
	 * Calculates binomial coefficient C(n, 2) = n * (n-1) / 2.
	 */
	private static int comb2(int n) {
		if (n < 2) {
			return 0;
		}
		return n * (n - 1) / 2;
	}

	// ==================== DETAILED COMPARISON ====================

	/**
	 * Result of detailed bin comparison.
	 */
	public static class BinComparisonResult {
		public final boolean equal;
		public final String reason;
		public final List<String> differences;

		public BinComparisonResult(boolean equal, String reason, List<String> differences) {
			this.equal = equal;
			this.reason = reason;
			this.differences = differences != null ? differences : new ArrayList<>();
		}

		public boolean isEqual() {
			return equal;
		}

		public String getReason() {
			return reason;
		}

		public List<String> getDifferences() {
			return new ArrayList<>(differences);
		}

		@Override
		public String toString() {
			if (equal) {
				return "Bins are equal";
			}

			StringBuilder sb = new StringBuilder();
			sb.append("Bins are NOT equal: ").append(reason).append("\n");

			for (String diff : differences) {
				sb.append("  - ").append(diff).append("\n");
			}

			return sb.toString();
		}
	}

	/**
	 * Performs detailed comparison of two aggregations with difference reporting.
	 * 
	 * <p>
	 * Provides comprehensive comparison including:
	 * </p>
	 * <ul>
	 * <li>Bin count differences</li>
	 * <li>Entity count differences per bin</li>
	 * <li>Missing/extra entities per bin</li>
	 * <li>Bin name differences</li>
	 * </ul>
	 * 
	 * <p>
	 * <b>Example:</b>
	 * </p>
	 * 
	 * <pre>
	 * BinComparisonResult result = AggregationComparator.compareBins(agg1, agg2);
	 * if (!result.isEqual()) {
	 * 	System.out.println(result); // Prints all differences
	 * }
	 * </pre>
	 * 
	 * @param aggregation1 First aggregation
	 * @param aggregation2 Second aggregation
	 * @return Detailed comparison result
	 */
	public static BinComparisonResult compareBins(Aggregation aggregation1, Aggregation aggregation2) {
		// Null checks
		if (aggregation1 == null && aggregation2 == null) {
			return new BinComparisonResult(true, "Both aggregations are null", null);
		}
		if (aggregation1 == null) {
			return new BinComparisonResult(false, "First aggregation is null", null);
		}
		if (aggregation2 == null) {
			return new BinComparisonResult(false, "Second aggregation is null", null);
		}

		// Identity check
		if (aggregation1 == aggregation2) {
			return new BinComparisonResult(true, "Same instance", null);
		}

		List<String> differences = new ArrayList<>();
		List<Bin> bins1 = aggregation1.getBins();
		List<Bin> bins2 = aggregation2.getBins();

		// Check bin count
		if (bins1.size() != bins2.size()) {
			differences.add(String.format("Different bin counts: %s has %d bins, %s has %d bins",
					aggregation1.getName(), bins1.size(), aggregation2.getName(), bins2.size()));
			return new BinComparisonResult(false, "Bin count mismatch", differences);
		}

		// Compare each bin
		boolean allEqual = true;

		for (int i = 0; i < bins1.size(); i++) {
			Bin bin1 = bins1.get(i);
			Bin bin2 = bins2.get(i);

			// Compare names
			if (!Objects.equals(bin1.getName(), bin2.getName())) {
				differences.add(String.format("Bin %d name differs: '%s' vs '%s'", i, bin1.getName(), bin2.getName()));
				allEqual = false;
			}

			// Compare entity counts
			Set<Long> entities1 = bin1.getElements().keySet();
			Set<Long> entities2 = bin2.getElements().keySet();

			if (entities1.size() != entities2.size()) {
				differences.add(String.format("Bin %d (%s) entity count differs: %d vs %d", i, bin1.getName(),
						entities1.size(), entities2.size()));
				allEqual = false;
			}

			// Compare entity IDs
			if (!entities1.equals(entities2)) {
				Set<Long> onlyIn1 = new HashSet<>(entities1);
				onlyIn1.removeAll(entities2);

				Set<Long> onlyIn2 = new HashSet<>(entities2);
				onlyIn2.removeAll(entities1);

				if (!onlyIn1.isEmpty()) {
					differences.add(String.format("Bin %d (%s) in %s has %d extra entities%s", i, bin1.getName(),
							aggregation1.getName(), onlyIn1.size(), onlyIn1.size() <= 10 ? ": " + onlyIn1 : ""));
				}

				if (!onlyIn2.isEmpty()) {
					differences.add(String.format("Bin %d (%s) in %s has %d extra entities%s", i, bin2.getName(),
							aggregation2.getName(), onlyIn2.size(), onlyIn2.size() <= 10 ? ": " + onlyIn2 : ""));
				}

				allEqual = false;
			}
		}

		if (allEqual) {
			return new BinComparisonResult(true, "All bins equal", null);
		} else {
			return new BinComparisonResult(false, "Bins differ in content", differences);
		}
	}

	// ==================== STATISTICS ====================

	/**
	 * Computes statistics about bin size differences between two aggregations.
	 * 
	 * @param aggregation1 First aggregation
	 * @param aggregation2 Second aggregation
	 * @return Map of statistic names to values
	 */
	public static Map<String, Double> computeBinSizeStatistics(Aggregation aggregation1, Aggregation aggregation2) {

		Map<String, Double> stats = new HashMap<>();

		if (aggregation1 == null || aggregation2 == null) {
			return stats;
		}

		List<Bin> bins1 = aggregation1.getBins();
		List<Bin> bins2 = aggregation2.getBins();

		// Total entity counts
		int total1 = bins1.stream().mapToInt(Bin::size).sum();
		int total2 = bins2.stream().mapToInt(Bin::size).sum();

		stats.put("totalEntities1", (double) total1);
		stats.put("totalEntities2", (double) total2);
		stats.put("totalDifference", (double) Math.abs(total1 - total2));

		// Bin counts
		stats.put("binCount1", (double) bins1.size());
		stats.put("binCount2", (double) bins2.size());

		// Average bin sizes
		stats.put("avgBinSize1", bins1.isEmpty() ? 0.0 : (double) total1 / bins1.size());
		stats.put("avgBinSize2", bins2.isEmpty() ? 0.0 : (double) total2 / bins2.size());

		return stats;
	}

	/**
	 * Prints a detailed comparison report to the logger.
	 * 
	 * @param aggregation1 First aggregation
	 * @param aggregation2 Second aggregation
	 */
	public static void printComparisonReport(Aggregation aggregation1, Aggregation aggregation2) {
		if (aggregation1 == null || aggregation2 == null) {
			return;
		}

		StringBuilder report = new StringBuilder();
		report.append("\n=== Aggregation Comparison Report ===\n");
		report.append(String.format("Comparing: %s vs %s\n", aggregation1.getName(), aggregation2.getName()));

		// Equality checks
		boolean equal = equalBins(aggregation1, aggregation2);
		boolean equalUnordered = equalBinsUnordered(aggregation1, aggregation2);
		boolean equalStrict = equalBinsStrict(aggregation1, aggregation2);

		report.append(String.format("\nEquality (ordered): %b\n", equal));
		report.append(String.format("Equality (unordered): %b\n", equalUnordered));
		report.append(String.format("Equality (strict): %b\n", equalStrict));

		// Similarity metrics
		double similarity = binSimilarity(aggregation1, aggregation2);
		double ari = adjustedRandIndex(aggregation1, aggregation2);

		report.append(String.format("\nJaccard Similarity: %.3f (%.1f%%)\n", similarity, similarity * 100));
		report.append(String.format("Adjusted Rand Index: %.3f\n", ari));

		// Statistics
		Map<String, Double> stats = computeBinSizeStatistics(aggregation1, aggregation2);
		report.append("\nBin Statistics:\n");
		report.append(String.format("  %s: %d bins, %d entities (avg %.1f per bin)\n", aggregation1.getName(),
				stats.get("binCount1").intValue(), stats.get("totalEntities1").intValue(), stats.get("avgBinSize1")));
		report.append(String.format("  %s: %d bins, %d entities (avg %.1f per bin)\n", aggregation2.getName(),
				stats.get("binCount2").intValue(), stats.get("totalEntities2").intValue(), stats.get("avgBinSize2")));

		// Detailed differences
		if (!equal) {
			BinComparisonResult result = compareBins(aggregation1, aggregation2);
			report.append("\nDetailed Differences:\n");
			for (String diff : result.getDifferences()) {
				report.append("  - ").append(diff).append("\n");
			}
		}

		report.append("=====================================\n");

		LOGGER.info(report.toString());
	}
}
