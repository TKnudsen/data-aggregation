package com.github.TKnudsen.dataAggregation.operation.contingencyTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregations;
import com.github.TKnudsen.dataAggregation.data.contingencyTable.ContingencyTable;
import com.github.TKnudsen.dataAggregation.data.contingencyTable.ContingencyTableStore;

/**
 * <p>
 * Manages parallel computation of contingency tables using thread pools.
 * Thread-safe executor service for parallel computation of contingency tables.
 * </p>
 *
 * <p>
 * <b>Features:</b>
 * <ul>
 * <li>Progress tracking with atomic counters</li>
 * <li>Named threads for debugging</li>
 * <li>Proper timeout and shutdown handling</li>
 * <li>Explicit pre-computation of all measures</li>
 * <li>Single-table and batch computation modes</li>
 * </ul>
 * </p>
 *
 * @since 2016
 */
public class ContingencyTableStoreCalculatorService {

	private static final Logger LOGGER = Logger.getLogger(ContingencyTableStoreCalculatorService.class.getName());

	// ==================== CONSTANTS ====================

	private static final long DEFAULT_SHUTDOWN_TIMEOUT_MINUTES = 30;
	private static final int PROGRESS_LOG_INTERVAL = 100; // Log every N tables

	// ==================== FIELDS ====================

	private final ContingencyTableStore contingencyTableStore;
	private final int threadCount;
	private final long shutdownTimeoutMinutes;

	// Progress tracking
	private final AtomicInteger completedTasks = new AtomicInteger(0);
	private final AtomicInteger totalTasks = new AtomicInteger(0);

	// Executor (created per batch)
	private volatile ExecutorService executor;

	// ==================== CONSTRUCTORS ====================

	/**
	 * Creates calculator service with default timeout.
	 */
	public ContingencyTableStoreCalculatorService(ContingencyTableStore contingencyTableStore, int threadCount) {
		this(contingencyTableStore, threadCount, DEFAULT_SHUTDOWN_TIMEOUT_MINUTES);
	}

	/**
	 * Creates calculator service with custom timeout.
	 */
	public ContingencyTableStoreCalculatorService(ContingencyTableStore contingencyTableStore, int threadCount,
			long shutdownTimeoutMinutes) {

		if (contingencyTableStore == null)
			throw new IllegalArgumentException("ContingencyTableStore cannot be null");

		if (threadCount <= 0)
			throw new IllegalArgumentException("Thread count must be positive, got: " + threadCount);

		if (contingencyTableStore.getAggregations() == null || contingencyTableStore.getAggregations().isEmpty())
			throw new IllegalArgumentException("ContingencyTableStore must contain aggregations");

		this.contingencyTableStore = contingencyTableStore;
		this.threadCount = Math.max(1, Math.min(threadCount, Runtime.getRuntime().availableProcessors()) - 1);
		this.shutdownTimeoutMinutes = shutdownTimeoutMinutes > 0 ? shutdownTimeoutMinutes
				: DEFAULT_SHUTDOWN_TIMEOUT_MINUTES;
	}

	// ==================== PUBLIC API ====================

	/**
	 * Compute all contingency tables for all aggregation pairs. Creates n x (n-1)
	 * tables where n is the number of aggregations.
	 */
	public void start() {
		long startTime = System.currentTimeMillis();

		Set<Aggregation> aggregations = contingencyTableStore.getAggregations();
		List<Runnable> tasks = new ArrayList<>();

		// Create tasks for all aggregation pairs (excluding self-comparison)
		for (Aggregation agg1 : aggregations)
			for (Aggregation agg2 : aggregations) {
				if (agg1.equals(agg2))
					continue;

				tasks.add(new ContingencyTableCreationWorker(agg1, agg2));
			}

		totalTasks.set(tasks.size());
		completedTasks.set(0);

		LOGGER.info("Creating " + totalTasks.get() + " contingency tables using " + threadCount + " threads");

		executeTasks(tasks, "ContingencyTable-Create");

		long elapsed = System.currentTimeMillis() - startTime;
		LOGGER.info("Completed all " + totalTasks.get() + " tables in " + elapsed + "ms ("
				+ String.format("%.2f", elapsed / (double) totalTasks.get()) + "ms per table)");
	}

	/**
	 * Refresh all contingency tables involving a specific aggregation. Called when
	 * an aggregation has been modified.
	 * 
	 * @param aggregation The aggregation that changed
	 */
	public void refreshTablesContaining(Aggregation aggregation) {
		if (aggregation == null)
			throw new IllegalArgumentException("Aggregation cannot be null");

		// If no tables exist yet, create them all
		if (contingencyTableStore.size() == 0) {
			start();
			return;
		}

		if (!contingencyTableStore.getAggregations().contains(aggregation))
			throw new IllegalArgumentException("Aggregation not in store: " + aggregation.getName());

		long startTime = System.currentTimeMillis();
		List<Runnable> tasks = new ArrayList<>();

		// Create refresh tasks for all tables involving this aggregation
		for (Aggregation otherAgg : contingencyTableStore.getAggregations()) {
			if (Aggregations.equalBinsUnordered(otherAgg, aggregation))
				continue;

			// Refresh table [other][aggregation]
			ContingencyTable table1 = contingencyTableStore.getContingencyTable(otherAgg, aggregation);
			if (table1 != null) {
				tasks.add(new ContingencyTableRefreshWorker(table1, otherAgg, aggregation));
			} else {
				LOGGER.warning(
						"Table [" + otherAgg.getName() + "][" + aggregation.getName() + "] not found, creating it");
				tasks.add(new ContingencyTableCreationWorker(otherAgg, aggregation));
			}

			// Refresh table [aggregation][other]
			ContingencyTable table2 = contingencyTableStore.getContingencyTable(aggregation, otherAgg);
			if (table2 != null) {
				tasks.add(new ContingencyTableRefreshWorker(table2, aggregation, otherAgg));
			} else {
				LOGGER.warning(
						"Table [" + aggregation.getName() + "][" + otherAgg.getName() + "] not found, creating it");
				tasks.add(new ContingencyTableCreationWorker(aggregation, otherAgg));
			}
		}

		totalTasks.set(tasks.size());
		completedTasks.set(0);

		LOGGER.info("Refreshing " + totalTasks.get() + " tables involving " + aggregation.getName());

		executeTasks(tasks, "ContingencyTable-Refresh");

		long elapsed = System.currentTimeMillis() - startTime;
		LOGGER.info("Refreshed " + totalTasks.get() + " tables in " + elapsed + "ms");
	}

	/**
	 * Calculate a single contingency table on-demand (synchronous).
	 * 
	 * @param aggregation1 First aggregation
	 * @param aggregation2 Second aggregation
	 */
	public void calculateContingencyTable(Aggregation aggregation1, Aggregation aggregation2) {
		if (aggregation1 == null || aggregation2 == null)
			throw new IllegalArgumentException("Aggregations cannot be null");

		// Execute synchronously without thread pool overhead
		ContingencyTableCreationWorker worker = new ContingencyTableCreationWorker(aggregation1, aggregation2);
		worker.run();
	}

	/**
	 * Force shutdown of the executor service.
	 */
	public void shutdown() {
		shutdownExecutor();
	}

	// ==================== PROGRESS TRACKING ====================

	/**
	 * Get computation progress (0.0 to 1.0).
	 */
	public double getProgress() {
		int total = totalTasks.get();
		if (total == 0)
			return 0.0;

		return (double) completedTasks.get() / total;
	}

	/**
	 * Get number of completed tasks.
	 */
	public int getCompletedTasks() {
		return completedTasks.get();
	}

	/**
	 * Get total number of tasks.
	 */
	public int getTotalTasks() {
		return totalTasks.get();
	}

	/**
	 * Check if computation is currently running.
	 */
	public boolean isRunning() {
		ExecutorService exec = executor;
		return exec != null && !exec.isTerminated();
	}

	// ==================== INTERNAL EXECUTION ====================

	/**
	 * Execute a batch of tasks using a thread pool.
	 */
	private void executeTasks(List<Runnable> tasks, String threadNamePrefix) {
		if (tasks.isEmpty()) {
			LOGGER.info("No tasks to execute");
			return;
		}

		// Create executor with named threads
		executor = Executors.newFixedThreadPool(threadCount, new NamedThreadFactory(threadNamePrefix));

		try {
			// Submit all tasks
			for (Runnable task : tasks)
				executor.execute(task);

			// Shutdown and wait for completion
			shutdownExecutor();

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error during execution", e);

			// Force shutdown on error
			if (executor != null)
				executor.shutdownNow();

		} finally {
			executor = null;
		}
	}

	/**
	 * Shutdown executor with proper timeout handling.
	 */
	private void shutdownExecutor() {
		ExecutorService exec = executor;
		if (exec == null || exec.isShutdown()) {
			return;
		}

		try {
			// Initiate shutdown
			exec.shutdown();

			// Wait for tasks to complete
			if (!exec.awaitTermination(shutdownTimeoutMinutes, TimeUnit.MINUTES)) {
				LOGGER.warning("Timeout after " + shutdownTimeoutMinutes + " minutes. Forcing shutdown...");

				// Force shutdown
				List<Runnable> notExecuted = exec.shutdownNow();
				if (!notExecuted.isEmpty())
					LOGGER.warning(notExecuted.size() + " tasks were not executed");

				// Wait briefly for forced shutdown
				if (!exec.awaitTermination(1, TimeUnit.MINUTES))
					LOGGER.warning("Executor did not terminate after forced shutdown");

			}

		} catch (InterruptedException e) {
			LOGGER.warning("Interrupted during shutdown");
			Thread.currentThread().interrupt();
			exec.shutdownNow();
		}
	}

	// ==================== WORKER CLASSES ====================

	/**
	 * Worker for creating a new contingency table with all measures computed.
	 */
	private class ContingencyTableCreationWorker implements Runnable {
		private final Aggregation aggregation1;
		private final Aggregation aggregation2;

		ContingencyTableCreationWorker(Aggregation aggregation1, Aggregation aggregation2) {
			if (aggregation1 == null || aggregation2 == null)
				throw new IllegalArgumentException("Aggregations cannot be null");

			this.aggregation1 = aggregation1;
			this.aggregation2 = aggregation2;
		}

		@Override
		public void run() {
			try {
				// Create contingency table
				ContingencyTable table = new ContingencyTable(aggregation1, aggregation2);

				// Pre-compute ALL measures for this table
				table.computeContingencyTable();

				// Compute all registered measures
				// (The new extensible design allows dynamic measures)
				for (String measureName : InterestingnessMeasure.interestingnessMeasureNames()) {
					try {
						table.computeMeasure(measureName);
					} catch (Exception e) {
						LOGGER.warning("Failed to compute measure '" + measureName + "' for [" + aggregation1.getName()
								+ "][" + aggregation2.getName() + "]: " + e.getMessage());
					}
				}

				// Store in the contingency table store
				contingencyTableStore.setContingencyTable(aggregation1, aggregation2, table);

				// Update progress
				int completed = completedTasks.incrementAndGet();
				if (completed % PROGRESS_LOG_INTERVAL == 0 || completed == totalTasks.get())
					LOGGER.info(completed + "/" + totalTasks.get() + " (" + String.format("%.1f%%", getProgress() * 100)
							+ ")");

			} catch (Exception e) {
				LOGGER.log(Level.SEVERE,
						"Error creating contingency table [" + aggregation1.getName() + "][" + aggregation2.getName() + "]",
						e);
			}
		}
	}

	/**
	 * Worker for refreshing an existing contingency table.
	 */
	private class ContingencyTableRefreshWorker implements Runnable {
		private final ContingencyTable table;
		private final Aggregation aggregation1;
		private final Aggregation aggregation2;

		ContingencyTableRefreshWorker(ContingencyTable table, Aggregation agg1, Aggregation agg2) {
			if (table == null)
				throw new IllegalArgumentException("ContingencyTable cannot be null");

			this.table = table;
			this.aggregation1 = agg1;
			this.aggregation2 = agg2;
		}

		@Override
		public void run() {
			try {
				// Reset and recompute all statistics
				table.reset();
				table.computeContingencyTable();

				// Recompute all registered measures
				for (String measureName : InterestingnessMeasure.interestingnessMeasureNames()) {
					try {
						table.computeMeasure(measureName);
					} catch (Exception e) {
						LOGGER.warning("Failed to compute measure '" + measureName + "' during refresh: " + e.getMessage());
					}
				}

				// Update progress
				int completed = completedTasks.incrementAndGet();
				if (completed % (PROGRESS_LOG_INTERVAL / 10) == 0 || completed == totalTasks.get())
					LOGGER.info("Refresh progress: " + completed + "/" + totalTasks.get() + " ("
							+ String.format("%.1f%%", getProgress() * 100) + ")");

			} catch (Exception e) {
				String name1 = aggregation1 != null ? aggregation1.getName() : "unknown";
				String name2 = aggregation2 != null ? aggregation2.getName() : "unknown";
				LOGGER.log(Level.SEVERE, "Error refreshing table [" + name1 + "][" + name2 + "]", e);
			}
		}
	}

	// ==================== THREAD FACTORY ====================

	/**
	 * Named thread factory for easier debugging.
	 */
	private static class NamedThreadFactory implements ThreadFactory {
		private final AtomicInteger threadNumber = new AtomicInteger(1);
		private final String namePrefix;

		NamedThreadFactory(String namePrefix) {
			this.namePrefix = namePrefix;
		}

		@Override
		public Thread newThread(Runnable r) {
			Thread thread = new Thread(r, namePrefix + "-" + threadNumber.getAndIncrement());
			thread.setDaemon(false); // Not daemon - we want these to complete
			return thread;
		}
	}

	// ==================== GETTERS ====================

	public ContingencyTableStore getContingencyTableStore() {
		return contingencyTableStore;
	}

	public int getThreadCount() {
		return threadCount;
	}

	public long getShutdownTimeoutMinutes() {
		return shutdownTimeoutMinutes;
	}
}