package com.github.TKnudsen.dataAggregation.data.contingencyTable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.operation.contingencyTable.InterestingnessMeasure;
import com.github.TKnudsen.dataAggregation.operation.interestingness.ChiSquareMeasure;
import com.github.TKnudsen.dataAggregation.operation.interestingness.InterestingnessMeasureCalculator;
import com.github.TKnudsen.dataAggregation.operation.interestingness.MeasureDescription;
import com.github.TKnudsen.dataAggregation.operation.interestingness.MeasureResult;
import com.github.TKnudsen.dataAggregation.operation.interestingness.MeasureStatistics;
import com.github.TKnudsen.dataAggregation.operation.interestingness.MutualInformationMeasure;
import com.github.TKnudsen.dataAggregation.operation.interestingness.PhiCoefficientMeasure;
import com.github.TKnudsen.dataAggregation.operation.interestingness.SignedPhiCoefficient;
import com.github.TKnudsen.ComplexDataObject.data.interfaces.IDObject;
import com.github.TKnudsen.ComplexDataObject.model.tools.MathFunctions;

/**
 * Contingency Table for analyzing relationships between two aggregations.
 *
 * @since 2016
 */
public class ContingencyTable implements IDObject {

	private static final Logger LOGGER = Logger.getLogger(ContingencyTable.class.getName());

	private final long ID;
	private final Aggregation aggregation1;
	private final Aggregation aggregation2;

	// Core contingency table cache
	private volatile int[][] contingencyTable;
	private volatile int sharedElementCount = -1;
	private volatile Map<Bin, Integer> sharedObjectIDsOfEachBucket;
	private volatile int contingencyMaxCount = Integer.MIN_VALUE;
	private volatile int contingencyMinCount = Integer.MAX_VALUE;

	// Registry for interestingness measures
	private final Map<String, InterestingnessMeasureCalculator> measureRegistry = new ConcurrentHashMap<>();

	// Cache for computed measures
	private final Map<String, MeasureResult> measureCache = new ConcurrentHashMap<>();

	// Cache for measure statistics
	private final Map<String, MeasureStatistics> statisticsCache = new ConcurrentHashMap<>();

	private boolean printOut = false;

	public ContingencyTable(Aggregation aggregation1, Aggregation aggregation2) {
		if (aggregation1 == null || aggregation2 == null) {
			throw new IllegalArgumentException("Aggregations cannot be null");
		}

		this.aggregation1 = aggregation1;
		this.aggregation2 = aggregation2;
		this.ID = MathFunctions.randomLong();

		// Register default measures
		registerDefaultMeasures();
	}

	private void registerDefaultMeasures() {
		registerMeasure(InterestingnessMeasure.CHI_SQUARE_INVERSE_PROBABILITY.name(), new ChiSquareMeasure());
		registerMeasure(InterestingnessMeasure.MUTUAL_INFORMATION.name(), new MutualInformationMeasure());
		registerMeasure(InterestingnessMeasure.SIGNED_PHI_COEFFICIENT.name(), new SignedPhiCoefficient());
		registerMeasure(InterestingnessMeasure.PHI_COEFFICIENT.name(), new PhiCoefficientMeasure());
	}

	/**
	 * Register a new interestingness measure calculator.
	 */
	public void registerMeasure(String measureName, InterestingnessMeasureCalculator calculator) {
		if (measureName == null || calculator == null) {
			throw new IllegalArgumentException("Measure name and calculator cannot be null");
		}
		measureRegistry.put(measureName, calculator);
		measureCache.remove(measureName);
		statisticsCache.remove(measureName);
	}

	/**
	 * Unregister a measure.
	 */
	public void unregisterMeasure(String measureName) {
		measureRegistry.remove(measureName);
		measureCache.remove(measureName);
		statisticsCache.remove(measureName);
	}

	/**
	 * Compute the contingency table (unchanged from your version).
	 */
	public void computeContingencyTable() {
		if (contingencyTable != null)
			return;

		synchronized (this) {
			if (contingencyTable != null) {
				return;
			}

			long startTime = System.currentTimeMillis();

			int size1 = aggregation1.size();
			int size2 = aggregation2.size();

			if (size1 == 0)
				LOGGER.warning("aggregation1 size is zero for " + aggregation1.getName());
			if (size2 == 0)
				LOGGER.warning("aggregation2 size is zero for " + aggregation2.getName());

			int[][] table = new int[size1][size2];
			Map<Bin, Integer> binCounts = new HashMap<>();
			int totalShared = 0;
			int maxCount = Integer.MIN_VALUE;
			int minCount = Integer.MAX_VALUE;

			Map<Long, Integer> elementToBin2Index = new HashMap<>();
			for (int j = 0; j < size2; j++) {
				Bin bin = aggregation2.getBins().get(j);
				for (Long id : bin.getElements().keySet())
					elementToBin2Index.put(id, j);
			}

			for (int i = 0; i < size1; i++) {
				Bin bin1 = aggregation1.getBins().get(i);
				int bin1Count = 0;

				for (Long id : bin1.getElements().keySet()) {
					Integer j = elementToBin2Index.get(id);
					if (j != null) {
						table[i][j]++;
						bin1Count++;
						totalShared++;
						maxCount = Math.max(maxCount, table[i][j]);
					}
				}

				binCounts.put(bin1, bin1Count);
			}

			for (int j = 0; j < size2; j++) {
				Bin bin2 = aggregation2.getBins().get(j);
				int bin2Count = 0;

				for (int i = 0; i < size1; i++) {
					bin2Count += table[i][j];
					if (table[i][j] > 0)
						minCount = Math.min(minCount, table[i][j]);
				}

				binCounts.put(bin2, bin2Count);
			}

			if (totalShared == 0) {
				minCount = 0;
				maxCount = 0;
			}

			this.sharedObjectIDsOfEachBucket = binCounts;
			this.sharedElementCount = totalShared;
			this.contingencyMaxCount = maxCount;
			this.contingencyMinCount = minCount;
			this.contingencyTable = table;

			long elapsed = System.currentTimeMillis() - startTime;
			if (printOut)
				System.out.println("ContingencyTable.computeContingencyTable() took " + elapsed + "ms for "
						+ totalShared + " shared elements");
		}
	}

	/**
	 * Compute a specific measure by name.
	 */
	public void computeMeasure(String measureName) {
		if (!measureRegistry.containsKey(measureName)) {
			throw new IllegalArgumentException("Unknown measure: " + measureName);
		}

		// Check if already computed
		if (measureCache.containsKey(measureName)) {
			return;
		}

		synchronized (measureCache) {
			if (measureCache.containsKey(measureName)) {
				return;
			}

			// Ensure contingency table is computed
			computeContingencyTable();

			if (sharedElementCount == 0) {
				LOGGER.warning("No shared elements, cannot compute " + measureName);
				measureCache.put(measureName, new MeasureResult());
				return;
			}

			// Delegate to the specific calculator
			InterestingnessMeasureCalculator calculator = measureRegistry.get(measureName);
			MeasureResult result = calculator.compute(this);

			measureCache.put(measureName, result);
		}
	}

	/**
	 * Get interestingness value between two bins for a specific measure.
	 */
	public double getInterestingness(Bin bucket1, Bin bucket2, String measureName) {
		if (bucket1 == null || bucket2 == null || measureName == null) {
			return Double.NaN;
		}

		computeMeasure(measureName);

		MeasureResult result = measureCache.get(measureName);
		if (result == null || result.getValues() == null) {
			return Double.NaN;
		}

		if (result.getValues().containsKey(bucket1) && result.getValues().get(bucket1).containsKey(bucket2)) {
			return result.getValues().get(bucket1).get(bucket2);
		}

		return Double.NaN;
	}

	/**
	 * Get statistics for a specific measure.
	 */
	public MeasureStatistics getMeasureStatistics(String measureName) {
		// Check statistics cache first
		MeasureStatistics cached = statisticsCache.get(measureName);
		if (cached != null)
			return cached;

		computeMeasure(measureName);
		MeasureResult result = measureCache.get(measureName);

		if (result == null)
			return null;

		// Create and cache the statistics object
		MeasureStatistics stats = new MeasureStatistics(result.getMinValue(), result.getMaxValue());
		statisticsCache.put(measureName, stats);

		return stats;
	}

	/**
	 * Reset all cached calculations.
	 */
	public synchronized void reset() {
		this.contingencyTable = null;
		this.sharedElementCount = -1;
		this.sharedObjectIDsOfEachBucket = null;
		this.contingencyMaxCount = Integer.MIN_VALUE;
		this.contingencyMinCount = Integer.MAX_VALUE;

		this.measureCache.clear();
		this.statisticsCache.clear();
	}

	/**
	 * Get description of a specific measure.
	 * 
	 * @param measureName Name of the measure
	 * @return MeasureDescription or null if measure not found
	 */
	public MeasureDescription getMeasureDescription(String measureName) {
		InterestingnessMeasureCalculator calculator = measureRegistry.get(measureName);
		return (calculator != null) ? calculator.getDescription() : null;
	}

	/**
	 * Get descriptions of all registered measures.
	 * 
	 * @return Map of measure names to their descriptions
	 */
	public Map<String, MeasureDescription> getAllMeasureDescriptions() {
		Map<String, MeasureDescription> descriptions = new HashMap<>();
		for (Map.Entry<String, InterestingnessMeasureCalculator> entry : measureRegistry.entrySet()) {
			descriptions.put(entry.getKey(), entry.getValue().getDescription());
		}
		return descriptions;
	}

	/**
	 * Print information about all available measures to console.
	 */
	public void printAvailableMeasures() {
		System.out.println("=== Available Interestingness Measures ===\n");
		for (Map.Entry<String, InterestingnessMeasureCalculator> entry : measureRegistry.entrySet()) {
			MeasureDescription desc = entry.getValue().getDescription();
			System.out.println(desc.toCompactString());
			System.out.println();
		}
	}

	/**
	 * Print detailed information about a specific measure.
	 * 
	 * @param measureName Name of the measure
	 */
	public void printMeasureInfo(String measureName) {
		MeasureDescription desc = getMeasureDescription(measureName);
		if (desc != null) {
			System.out.println(desc.toString());
		} else {
			System.out.println("Measure not found: " + measureName);
		}
	}

	// Getters (unchanged)

	public int[][] getContingencyTable() {
		computeContingencyTable();
		return contingencyTable;
	}

	public int getSharedElementCount() {
		computeContingencyTable();
		return sharedElementCount;
	}

	public Map<Bin, Integer> getSharedObjectIDsOfEachBucket() {
		computeContingencyTable();
		return sharedObjectIDsOfEachBucket;
	}

	public int getContingencyMaxCount() {
		computeContingencyTable();
		return contingencyMaxCount;
	}

	public int getContingencyMinCount() {
		computeContingencyTable();
		return contingencyMinCount;
	}

	@Override
	public long getID() {
		return ID;
	}

	public Aggregation getAggregation1() {
		return aggregation1;
	}

	public Aggregation getAggregation2() {
		return aggregation2;
	}

	/**
	 * Package-private accessors for measure calculators
	 */
	public int[][] getContingencyTableDirect() {
		return contingencyTable;
	}

	public int getSharedElementCountDirect() {
		return sharedElementCount;
	}

	public Map<Bin, Integer> getSharedObjectIDsOfEachBucketDirect() {
		return sharedObjectIDsOfEachBucket;
	}

	public boolean isPrintOut() {
		return printOut;
	}

	public void setPrintOut(boolean printOut) {
		this.printOut = printOut;
	}
}
