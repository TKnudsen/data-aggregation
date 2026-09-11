package com.github.TKnudsen.dataAggregation.data.contingencyTable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;

import com.github.TKnudsen.ComplexDataObject.data.entry.EntryWithComparableKey;
import com.github.TKnudsen.ComplexDataObject.data.ranking.Ranking;
import com.github.TKnudsen.ComplexDataObject.model.tools.StatisticsSupport;
import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.operation.contingencyTable.ContingencyTableStoreCalculatorService;

/**
 * <p>
 * Thread-safe storage and computation of contingency tables and derived
 * statistics.
 * </p>
 *
 * @since 2016
 */
public class ContingencyTableStore {

	private static final Logger LOGGER = Logger.getLogger(ContingencyTableStore.class.getName());
	private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

	private final Set<Aggregation> aggregations;
	private final ConcurrentHashMap<Aggregation, ConcurrentHashMap<Aggregation, ContingencyTable>> contingencyTables;

	private ContingencyTableStoreCalculatorService calculatorService;

	// Thread-safe caches
	private final ConcurrentHashMap<String, Map<Aggregation, StatisticsSupport>> aggregationStatistics;
	private final ConcurrentHashMap<String, StatisticsSupport> aggregationStatisticsAll;
	private final ConcurrentHashMap<String, Map<Bin, StatisticsSupport>> binStatistics;
	private final ConcurrentHashMap<String, StatisticsSupport> binStatisticsAll;
	private final ConcurrentHashMap<String, Map<String, Ranking<EntryWithComparableKey<Double, Aggregation>>>> attributeRanking;
	private final ConcurrentHashMap<String, Ranking<EntryWithComparableKey<Double, Bin>>> binRanking;

	// Locks
	private final Map<String, ReadWriteLock> measureLocks;
	private final ReadWriteLock aggregationLock = new ReentrantReadWriteLock();

	public ContingencyTableStore(Collection<Aggregation> aggregations) {
		Objects.requireNonNull(aggregations, "Aggregations cannot be null");

		this.aggregations = new HashSet<>(aggregations);
		this.contingencyTables = new ConcurrentHashMap<>();

		this.aggregationStatistics = new ConcurrentHashMap<>();
		this.aggregationStatisticsAll = new ConcurrentHashMap<>();
		this.binStatistics = new ConcurrentHashMap<>();
		this.binStatisticsAll = new ConcurrentHashMap<>();
		this.attributeRanking = new ConcurrentHashMap<>();
		this.binRanking = new ConcurrentHashMap<>();
		this.measureLocks = new ConcurrentHashMap<>();

		calculateContingencyTables();
	}

	// ==================== INITIALIZATION ====================

	private void initContingencyTableStoreCalculatorService() {
		if (calculatorService == null) {
			synchronized (this) {
				if (calculatorService == null) {
					calculatorService = new ContingencyTableStoreCalculatorService(this, THREAD_COUNT);
				}
			}
		}
	}

	public void calculateContingencyTables() {
		initContingencyTableStoreCalculatorService();
		calculatorService.start();
	}

	// ==================== CONTINGENCY TABLE ACCESS ====================

	public boolean containsAggregation(Aggregation aggregation) {
		return contingencyTables.containsKey(aggregation);
	}

	public boolean containsAggregation(String aggregationName) {
		if (aggregationName == null)
			return false;

		for (Aggregation aggregation : aggregations)
			if (aggregation.getName().equals(aggregationName))
				return true;
		return false;
	}

	public Set<Aggregation> getAggregations() {
		aggregationLock.readLock().lock();
		try {
			return new HashSet<>(aggregations);
		} finally {
			aggregationLock.readLock().unlock();
		}
	}

	/**
	 * Gets a contingency table (READ-ONLY lookup). Returns null if table doesn't
	 * exist.
	 * 
	 * Tables should exist for all aggregation pairs after initialization. If null
	 * is returned, this indicates a bug in table management.
	 */
	public ContingencyTable getContingencyTable(Aggregation aggregation1, Aggregation aggregation2) {
		if (aggregation1 == null || aggregation2 == null)
			return null;

		ConcurrentHashMap<Aggregation, ContingencyTable> agg1Tables = contingencyTables.get(aggregation1);

		if (agg1Tables == null) {
			LOGGER.warning("No tables found for aggregation: " + aggregation1.getName());
			return null;
		}

		ContingencyTable table = agg1Tables.get(aggregation2);

		if (table == null) {
			LOGGER.warning("Table not found: [" + aggregation1.getName() + "][" + aggregation2.getName() + "]");
		}

		return table;
	}

	public void setContingencyTable(Aggregation aggregation1, Aggregation aggregation2, ContingencyTable ct) {
		if (aggregation1 == null || aggregation2 == null || ct == null)
			return;

		contingencyTables.putIfAbsent(aggregation1, new ConcurrentHashMap<>());
		contingencyTables.get(aggregation1).put(aggregation2, ct);

		invalidateStatisticsForAggregation(aggregation1);
		invalidateStatisticsForAggregation(aggregation2);
	}

	public Map<Aggregation, Map<Aggregation, ContingencyTable>> getContingencyTables() {
		Map<Aggregation, Map<Aggregation, ContingencyTable>> copy = new HashMap<>();

		for (Map.Entry<Aggregation, ConcurrentHashMap<Aggregation, ContingencyTable>> entry : contingencyTables
				.entrySet()) {
			copy.put(entry.getKey(), new HashMap<>(entry.getValue()));
		}

		return copy;
	}

	// ==================== AGGREGATION MANAGEMENT ====================

	public void addAggregation(Aggregation aggregation) {
		Objects.requireNonNull(aggregation, "Aggregation cannot be null");

		aggregationLock.writeLock().lock();
		try {

			LOGGER.info(
					"Adding aggregation '" + aggregation.getName() + "' (current count: " + aggregations.size() + ")");

			Aggregation existing = null;
			for (Aggregation agg : aggregations)
				if (agg.getName().equals(aggregation.getName()))
					existing = agg;

			if (existing != null)
				removeAggregation(existing);

			aggregations.add(aggregation);

			computeTablesForNewAggregation(aggregation);

			LOGGER.info("Successfully added '" + aggregation.getName() + "'");
		} finally {
			aggregationLock.writeLock().unlock();
		}
	}

	private void computeTablesForNewAggregation(Aggregation newAggregation) {
		initContingencyTableStoreCalculatorService();

		int tableCount = 0;

		for (Aggregation existing : aggregations) {
			if (existing.equals(newAggregation))
				continue;

			ContingencyTable table1 = new ContingencyTable(newAggregation, existing);
			table1.computeContingencyTable();
			setContingencyTable(newAggregation, existing, table1);
			tableCount++;

			ContingencyTable table2 = new ContingencyTable(existing, newAggregation);
			table2.computeContingencyTable();
			setContingencyTable(existing, newAggregation, table2);
			tableCount++;
		}

		ContingencyTable selfTable = new ContingencyTable(newAggregation, newAggregation);
		selfTable.computeContingencyTable();
		setContingencyTable(newAggregation, newAggregation, selfTable);
		tableCount++;

		LOGGER.info("Computed " + tableCount + " new contingency tables");
		resetAllStatistics();
	}

	public void removeAggregation(Aggregation aggregation) {
		if (aggregation == null)
			return;

		aggregationLock.writeLock().lock();
		try {
			boolean removed = aggregations.remove(aggregation);

			if (!removed) {
				LOGGER.warning("Aggregation not found for removal: " + aggregation.getName());
				return;
			}

			LOGGER.info("Removing aggregation '" + aggregation.getName() + "'");

			contingencyTables.remove(aggregation);

			for (ConcurrentHashMap<Aggregation, ContingencyTable> rowMap : contingencyTables.values())
				rowMap.remove(aggregation);

			invalidateStatisticsForAggregation(aggregation);

			LOGGER.info("Successfully removed '" + aggregation.getName() + "'");
		} finally {
			aggregationLock.writeLock().unlock();
		}
	}

	public void refreshTablesContaining(Aggregation aggregation) {
		if (aggregation == null)
			return;

		initContingencyTableStoreCalculatorService();
		calculatorService.refreshTablesContaining(aggregation);
		invalidateStatisticsForAggregation(aggregation);
	}

	public int size() {
		aggregationLock.readLock().lock();
		try {
			return aggregations.size();
		} finally {
			aggregationLock.readLock().unlock();
		}
	}

	// ==================== STATISTICS INVALIDATION ====================

	private void invalidateStatisticsForAggregation(Aggregation aggregation) {
		for (Map<Aggregation, StatisticsSupport> stats : aggregationStatistics.values())
			stats.remove(aggregation);

		binStatistics.clear();
		aggregationStatisticsAll.clear();
		binStatisticsAll.clear();
		attributeRanking.clear();
		binRanking.clear();
	}

	private void resetAllStatistics() {
		aggregationStatistics.clear();
		aggregationStatisticsAll.clear();
		binStatistics.clear();
		binStatisticsAll.clear();
		attributeRanking.clear();
		binRanking.clear();
	}

	// ==================== STATISTICS CALCULATION (FIXED) ====================

	/**
	 * Calculate statistics for a specific measure with proper synchronization.
	 * 
	 * <p>
	 * <b>Thread Safety:</b> Uses per-measure locks to ensure only one thread
	 * calculates statistics for a given measure at a time, and results are only
	 * published atomically when fully computed.
	 * </p>
	 */
	private void calculateStatistics(String measureName) {
		Objects.requireNonNull(measureName, "Measure name cannot be null");
		if (measureName.isEmpty())
			throw new IllegalArgumentException("Measure name cannot be empty");

		ReadWriteLock lock = measureLocks.computeIfAbsent(measureName, k -> new ReentrantReadWriteLock());

		lock.writeLock().lock();
		try {
			// Check if already calculated INSIDE the lock
			if (isStatisticsCalculated(measureName)) {
				return;
			}

			LOGGER.info("Calculating statistics for measure: " + measureName);
			long startTime = System.currentTimeMillis();

			// Calculate everything LOCALLY first (don't publish until complete)
			Map<Aggregation, StatisticsSupport> aggStats = new ConcurrentHashMap<>();
			Map<Bin, StatisticsSupport> binStats = new ConcurrentHashMap<>();
			List<Double> allAggValues = new ArrayList<>();
			List<Double> allBinValues = new ArrayList<>();

			aggregationLock.readLock().lock();
			try {
				for (Aggregation a1 : aggregations) {
					List<Double> valuesAgg = new ArrayList<>();
					Map<Bin, List<Double>> valuesBin = new HashMap<>();

					for (Aggregation a2 : aggregations) {
						if (a1.equals(a2))
							continue;

						ContingencyTable table = getContingencyTable(a1, a2);
						if (table == null) {
							throw new IllegalArgumentException("ContingencyTable not found for aggregations");
						}

						for (Bin b1 : a1) {
							for (Bin b2 : a2) {
								double value = table.getInterestingness(b1, b2, measureName);

								if (Double.isFinite(value) && value >= 0) {
									valuesAgg.add(value);
									valuesBin.computeIfAbsent(b1, k -> new ArrayList<>()).add(value);
								}
							}
						}
					}

					if (!valuesAgg.isEmpty()) {
						aggStats.put(a1, new StatisticsSupport(valuesAgg));
						allAggValues.addAll(valuesAgg);
					}

					for (Bin b : a1) {
						List<Double> binValues = valuesBin.get(b);
						if (binValues != null && !binValues.isEmpty()) {
							binStats.put(b, new StatisticsSupport(binValues));
							allBinValues.addAll(binValues);
						}
					}
				}
			} finally {
				aggregationLock.readLock().unlock();
			}

			// ATOMICALLY publish ALL results at once
			aggregationStatistics.put(measureName, aggStats);
			if (!allAggValues.isEmpty()) {
				aggregationStatisticsAll.put(measureName, new StatisticsSupport(allAggValues));
			}

			binStatistics.put(measureName, binStats);
			if (!allBinValues.isEmpty()) {
				binStatisticsAll.put(measureName, new StatisticsSupport(allBinValues));
			}

			long elapsed = System.currentTimeMillis() - startTime;
			LOGGER.info("Statistics calculated for " + aggStats.size() + " aggregations, " + binStats.size()
					+ " bins in " + elapsed + "ms");

		} finally {
			lock.writeLock().unlock();
		}
	}

	/**
	 * Checks if statistics are fully calculated for a measure. Must be called under
	 * lock.
	 */
	private boolean isStatisticsCalculated(String measureName) {
		if (!aggregationStatistics.containsKey(measureName))
			return false;

		Map<Aggregation, StatisticsSupport> aggStats = aggregationStatistics.get(measureName);
		if (aggStats == null || aggStats.isEmpty())
			return false;

		// Check that we have stats for all aggregations
		if (aggStats.size() != aggregations.size())
			return false;

		// Also check bin statistics exist
		if (!binStatistics.containsKey(measureName))
			return false;

		if (!binStatisticsAll.containsKey(measureName))
			return false;

		return true;
	}

	// ==================== RANKINGS CALCULATION ====================

	private void calculateRankings(String measureName, String rankingOptions) {
		Objects.requireNonNull(measureName, "Measure name cannot be null");
		Objects.requireNonNull(rankingOptions, "Ranking options cannot be null");

		ReadWriteLock lock = measureLocks.computeIfAbsent(measureName, k -> new ReentrantReadWriteLock());

		lock.writeLock().lock();
		try {
			if (!aggregationStatistics.containsKey(measureName)) {
				calculateStatistics(measureName);
			}

			if (attributeRanking.containsKey(measureName)
					&& attributeRanking.get(measureName).containsKey(rankingOptions)) {

				Ranking<EntryWithComparableKey<Double, Aggregation>> existing = attributeRanking.get(measureName)
						.get(rankingOptions);

				if (existing != null && existing.size() == aggregations.size())
					return;
			}

			LOGGER.info("Calculating rankings for measure: " + measureName);

			attributeRanking.putIfAbsent(measureName, new ConcurrentHashMap<>());
			Ranking<EntryWithComparableKey<Double, Aggregation>> aggRanking = new Ranking<>();

			aggregationLock.readLock().lock();
			try {
				for (Aggregation aggregation : aggregations) {
					StatisticsSupport stats = getAttributeStatistics(aggregation, measureName);
					double score = getAttributeRankingScore(stats, rankingOptions);
					aggRanking.add(new EntryWithComparableKey<>(score, aggregation));
				}
			} finally {
				aggregationLock.readLock().unlock();
			}

			attributeRanking.get(measureName).put(rankingOptions, aggRanking);

			Ranking<EntryWithComparableKey<Double, Bin>> bRanking = new Ranking<>();
			Map<Bin, StatisticsSupport> binStatsMap = getBinStatistics(measureName);

			for (Map.Entry<Bin, StatisticsSupport> entry : binStatsMap.entrySet()) {
				double score = entry.getValue().getMax();
				bRanking.add(new EntryWithComparableKey<>(score, entry.getKey()));
			}

			binRanking.put(measureName, bRanking);

		} finally {
			lock.writeLock().unlock();
		}
	}

	private static double getAttributeRankingScore(StatisticsSupport dataStatistics, String option) {

		if (dataStatistics == null)
			return 0.0;

		try {
			AttributeRankingOption value = AttributeRankingOption.valueOf(option);

			switch (value) {
			case MinumumSignificance:
				return dataStatistics.getMin();
			case AverageSignificance:
				return dataStatistics.getMean();
			case MaxSignificance:
				return dataStatistics.getMax();
			case MedianSignificance:
				return dataStatistics.getMedian();
			case SumOfSignificances:
				return dataStatistics.getSum();
			default:
				LOGGER.warning("ContingencyTableStore.getAttributeRankingScore: unable to interpret ranking option:"
						+ option + ", returning 0.0");
				return 0.0;
			}
		} catch (Exception e) {
			LOGGER.warning("ContingencyTableStore.getAttributeRankingScore: unable to convert string " + option
					+ " into AttributeRankingOption");
			return 0.0;
		}
	}

	// ==================== PUBLIC STATISTICS API (FIXED) ====================

	/**
	 * Gets attribute statistics with proper synchronization to prevent reading
	 * partially-computed data.
	 */
	public StatisticsSupport getAttributeStatistics(Aggregation aggregation, String measureName) {
		if (aggregation == null || measureName == null)
			return null;

		ReadWriteLock lock = measureLocks.computeIfAbsent(measureName, k -> new ReentrantReadWriteLock());

		// Use read lock to safely check and read
		lock.readLock().lock();
		try {
			if (!isStatisticsCalculated(measureName)) {
				// Release read lock before acquiring write lock
				lock.readLock().unlock();

				// Acquire write lock to calculate
				lock.writeLock().lock();
				try {
					// Double-check after acquiring write lock
					if (!isStatisticsCalculated(measureName)) {
						calculateStatistics(measureName);
					}

					// Downgrade to read lock
					lock.readLock().lock();
				} finally {
					lock.writeLock().unlock();
				}
			}

			Map<Aggregation, StatisticsSupport> stats = aggregationStatistics.get(measureName);
			return (stats != null) ? stats.get(aggregation) : null;

		} finally {
			lock.readLock().unlock();
		}
	}

	public Map<Aggregation, StatisticsSupport> getAttributeStatistics(String measureName) {
		if (measureName == null)
			return new HashMap<>();

		ReadWriteLock lock = measureLocks.computeIfAbsent(measureName, k -> new ReentrantReadWriteLock());

		lock.readLock().lock();
		try {
			if (!isStatisticsCalculated(measureName)) {
				lock.readLock().unlock();

				lock.writeLock().lock();
				try {
					if (!isStatisticsCalculated(measureName)) {
						calculateStatistics(measureName);
					}
					lock.readLock().lock();
				} finally {
					lock.writeLock().unlock();
				}
			}

			Map<Aggregation, StatisticsSupport> stats = aggregationStatistics.get(measureName);
			return (stats != null) ? new HashMap<>(stats) : new HashMap<>();

		} finally {
			lock.readLock().unlock();
		}
	}

	public StatisticsSupport getBinStatistics(Bin bin, String measureName) {
		if (bin == null || measureName == null)
			return null;

		ReadWriteLock lock = measureLocks.computeIfAbsent(measureName, k -> new ReentrantReadWriteLock());

		lock.readLock().lock();
		try {
			if (!isStatisticsCalculated(measureName)) {
				lock.readLock().unlock();

				lock.writeLock().lock();
				try {
					if (!isStatisticsCalculated(measureName)) {
						calculateStatistics(measureName);
					}
					lock.readLock().lock();
				} finally {
					lock.writeLock().unlock();
				}
			}

			Map<Bin, StatisticsSupport> stats = binStatistics.get(measureName);
			return (stats != null) ? stats.get(bin) : null;

		} finally {
			lock.readLock().unlock();
		}
	}

	public Map<Bin, StatisticsSupport> getBinStatistics(String measureName) {
		if (measureName == null)
			return new HashMap<>();

		ReadWriteLock lock = measureLocks.computeIfAbsent(measureName, k -> new ReentrantReadWriteLock());

		lock.readLock().lock();
		try {
			if (!isStatisticsCalculated(measureName)) {
				lock.readLock().unlock();

				lock.writeLock().lock();
				try {
					if (!isStatisticsCalculated(measureName)) {
						calculateStatistics(measureName);
					}
					lock.readLock().lock();
				} finally {
					lock.writeLock().unlock();
				}
			}

			Map<Bin, StatisticsSupport> stats = binStatistics.get(measureName);
			return (stats != null) ? new HashMap<>(stats) : new HashMap<>();

		} finally {
			lock.readLock().unlock();
		}
	}

	public StatisticsSupport getBinsStatistics(String measureName) {
		if (measureName == null)
			return null;

		ReadWriteLock lock = measureLocks.computeIfAbsent(measureName, k -> new ReentrantReadWriteLock());

		lock.readLock().lock();
		try {
			if (!isStatisticsCalculated(measureName)) {
				lock.readLock().unlock();

				lock.writeLock().lock();
				try {
					if (!isStatisticsCalculated(measureName)) {
						calculateStatistics(measureName);
					}
					lock.readLock().lock();
				} finally {
					lock.writeLock().unlock();
				}
			}

			return binStatisticsAll.get(measureName);

		} finally {
			lock.readLock().unlock();
		}
	}

	// ==================== PUBLIC RANKINGS API ====================

	public Ranking<EntryWithComparableKey<Double, Aggregation>> getAttributeRanking(String measureName,
			String rankingOption) {

		if (measureName == null || rankingOption == null)
			return new Ranking<>();

		if (!attributeRanking.containsKey(measureName) || !attributeRanking.get(measureName).containsKey(rankingOption))
			calculateRankings(measureName, rankingOption);

		Ranking<EntryWithComparableKey<Double, Aggregation>> ranking = attributeRanking.get(measureName)
				.get(rankingOption);

		return (ranking != null) ? ranking : new Ranking<>();
	}

	public Ranking<EntryWithComparableKey<Double, Bin>> getBinRanking(String measureName) {
		if (measureName == null)
			return new Ranking<>();

		if (!binRanking.containsKey(measureName))
			calculateRankings(measureName, AttributeRankingOption.MaxSignificance.name());

		Ranking<EntryWithComparableKey<Double, Bin>> ranking = binRanking.get(measureName);
		return (ranking != null) ? ranking : new Ranking<>();
	}
}