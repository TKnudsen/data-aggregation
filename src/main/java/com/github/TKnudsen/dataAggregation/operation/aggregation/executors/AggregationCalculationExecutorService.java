package com.github.TKnudsen.dataAggregation.operation.aggregation.executors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.operation.aggregation.calculation.AggregationCalculatorWorker;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;

/**
 * Thread-safe executor service for parallel aggregation calculation.
 * 
 * <p>
 * Features:
 * <ul>
 * <li>Thread-safe result collection using BlockingQueue</li>
 * <li>Configurable timeout</li>
 * <li>Error tracking and reporting</li>
 * <li>Proper executor shutdown</li>
 * <li>Generic data container support via DataContainerAdapter</li>
 * </ul>
 * </p>
 *
 * @since 2016
 */
public class AggregationCalculationExecutorService {

	private static final Logger LOGGER = Logger.getLogger(AggregationCalculationExecutorService.class.getName());

	// Default configuration
	private static final int DEFAULT_THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
	private static final long DEFAULT_TIMEOUT_MINUTES = 30;

	// Input configuration
	private final DataContainerAdapter dataContainer;
	private final Map<String, AggregationCharacterization> aggregationInitializations;
	private final int threadCount;
	private final long timeoutMinutes;

	// Thread-safe result collection
	private final BlockingQueue<Aggregation> resultQueue;
	private final AtomicInteger errorCount;

	// Executor
	private ExecutorService executor;

	/**
	 * Creates executor service with default timeout.
	 */
	public AggregationCalculationExecutorService(DataContainerAdapter dataContainer,
			Map<String, AggregationCharacterization> aggregationInitializations, int threadCount) {
		this(dataContainer, aggregationInitializations, threadCount, DEFAULT_TIMEOUT_MINUTES);
	}

	/**
	 * Creates executor service with custom timeout.
	 */
	public AggregationCalculationExecutorService(DataContainerAdapter dataContainer,
			Map<String, AggregationCharacterization> aggregationInitializations, int threadCount, long timeoutMinutes) {

		this.dataContainer = Objects.requireNonNull(dataContainer, "DataContainer cannot be null");
		this.aggregationInitializations = Objects.requireNonNull(aggregationInitializations,
				"AggregationInitializations cannot be null");
		this.threadCount = threadCount > 0 ? threadCount : DEFAULT_THREAD_COUNT;
		this.timeoutMinutes = timeoutMinutes > 0 ? timeoutMinutes : DEFAULT_TIMEOUT_MINUTES;

		this.resultQueue = new LinkedBlockingQueue<>();
		this.errorCount = new AtomicInteger(0);
	}

	/**
	 * Start aggregation calculation on all attributes/features.
	 * 
	 * @return List of successfully created aggregations
	 * @throws InterruptedException            if interrupted while waiting for
	 *                                         completion
	 * @throws AggregationCalculationException if calculation fails
	 */
	public List<Aggregation> start() throws InterruptedException {
		long startTime = System.currentTimeMillis();

		LOGGER.info("Starting with " + threadCount + " threads...");

		try {
			// Create executor
			executor = Executors.newFixedThreadPool(threadCount, r -> {
				Thread t = new Thread(r, "AggregationCalculation-worker");
				t.setDaemon(true);
				return t;
			});

			// Submit workers for each attribute/feature
			List<String> attributes = dataContainer.getAttributes();
			int submittedTasks = 0;

			for (String attribute : attributes) {
				if (attribute == null) {
					LOGGER.warning("Skipping null attribute");
					continue;
				}

				AggregationCharacterization init = aggregationInitializations.get(attribute);
				if (init == null) {
					LOGGER.warning("No initialization for attribute: " + attribute);
					continue;
				}

				try {
					Map<Long, Object> values = dataContainer.getAttributeValues(attribute);
					boolean numeric = dataContainer.isNumeric(attribute);

					AggregationCalculatorWorker worker = new AggregationCalculatorWorker(attribute, values, numeric,
							init, resultQueue, false);

					executor.execute(worker);
					submittedTasks++;

				} catch (Exception e) {
					LOGGER.warning("Error creating worker for attribute '" + attribute + "': " + e.getMessage());
					errorCount.incrementAndGet();
				}
			}

			LOGGER.info("Submitted " + submittedTasks + " tasks");

			// Shutdown and wait for completion
			executor.shutdown();
			boolean completed = executor.awaitTermination(timeoutMinutes, TimeUnit.MINUTES);

			if (!completed) {
				LOGGER.warning("Timeout after " + timeoutMinutes + " minutes!");
				executor.shutdownNow();
				throw new AggregationCalculationException(
						"Aggregation calculation timed out after " + timeoutMinutes + " minutes");
			}

			// Collect results
			List<Aggregation> aggregations = new ArrayList<>(resultQueue);

			// Validate results
			int emptyBinCount = 0;
			for (Aggregation agg : aggregations) {
				if (agg.getBins() == null || agg.getBins().size() == 0) {
					LOGGER.warning("Aggregation '" + agg.getName() + "' has 0 bins!");
					emptyBinCount++;
				}
			}

			long elapsed = System.currentTimeMillis() - startTime;

			LOGGER.info("Completed in " + elapsed + "ms" + " - Submitted tasks: " + submittedTasks
					+ ", Successful aggregations: " + aggregations.size() + ", Empty bin aggregations: "
					+ emptyBinCount + ", Errors: " + errorCount.get());

			return aggregations;

		} finally {
			// Ensure executor is always shutdown
			if (executor != null && !executor.isShutdown()) {
				executor.shutdownNow();
			}
		}
	}

	/**
	 * Get current error count.
	 */
	public int getErrorCount() {
		return errorCount.get();
	}

	/**
	 * Get configured thread count.
	 */
	public int getThreadCount() {
		return threadCount;
	}

	/**
	 * Check if executor is still running.
	 */
	public boolean isRunning() {
		return executor != null && !executor.isTerminated();
	}

	/**
	 * Force shutdown of executor (interrupts running tasks).
	 */
	public void forceShutdown() {
		if (executor != null && !executor.isShutdown()) {
			LOGGER.warning("Force shutdown requested");
			executor.shutdownNow();
		}
	}

	// ==================== ADAPTER INTERFACE ====================

	/**
	 * Adapter interface to abstract different data container types.
	 * 
	 * <p>
	 * Allows the executor service to work with any data container type by providing
	 * a common interface for accessing attributes/features.
	 * </p>
	 */
	public interface DataContainerAdapter {
		/**
		 * Get all attribute/feature names. Unsorted.
		 */
		List<String> getAttributes();

		/**
		 * Get all attribute/feature names. Sorted.
		 */
		List<String> getAttributesSorted();

		/**
		 * Get values for a specific attribute/feature.
		 * 
		 * @param attribute The attribute/feature name
		 * @return Map of entity ID to value
		 */
		Map<Long, Object> getAttributeValues(String attribute);

		/**
		 * Check if attribute/feature is numeric.
		 * 
		 * @param attribute The attribute/feature name
		 * @return true if numeric, false otherwise
		 */
		boolean isNumeric(String attribute);
	}

	// ==================== EXCEPTION ====================

	/**
	 * Exception thrown when aggregation calculation fails.
	 */
	public static class AggregationCalculationException extends RuntimeException {
		public AggregationCalculationException(String message) {
			super(message);
		}

		public AggregationCalculationException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}