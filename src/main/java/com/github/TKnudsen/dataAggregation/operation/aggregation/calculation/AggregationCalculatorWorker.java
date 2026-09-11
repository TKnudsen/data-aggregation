package com.github.TKnudsen.dataAggregation.operation.aggregation.calculation;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;

/**
 * <p>
 * Thread-safe worker that calculates an Aggregation for a single attribute.
 * </p>
 *
 * <p>
 * <b>Thread Safety:</b> Uses BlockingQueue for thread-safe result collection.
 * Results are added atomically without requiring external synchronization.
 * </p>
 * 
 * <p>
 * <b>Error Handling:</b> Converts invalid numeric values to NaN and logs
 * warnings. Non-convertible values are tracked and reported. Handles Feature<?>
 * objects by extracting their underlying values.
 * </p>
 * 
 * <p>
 * <b>Type Detection:</b> For numeric attributes, automatically detects discrete
 * vs continuous distributions using statistical analysis.
 * </p>
 * 
 * @since 2016
 */
public class AggregationCalculatorWorker implements Runnable {

	private static final Logger LOGGER = Logger.getLogger(AggregationCalculatorWorker.class.getName());

	// ==================== CONSTANTS ====================

	/**
	 * Threshold for determining if numeric data is discrete (default: 5%). If fewer
	 * than 5% of values are unique, data is considered discrete.
	 */
	private static final double DISCRETE_THRESHOLD = 0.05;

	/**
	 * String value treated as "Not Available" / missing data.
	 */
	private static final String NA_VALUE = "N/A";

	/**
	 * Maximum number of individual conversion errors to report (prevents log spam).
	 */
	private static final int MAX_ERROR_REPORTS = 5;

	// ==================== FIELDS ====================

	// Input parameters (immutable after construction)
	private final String attribute;
	private final Map<Long, Object> values;
	private final boolean numeric;
	private final AggregationCharacterization aggregationInitialization;

	// Thread-safe output
	private final BlockingQueue<Aggregation> resultQueue;

	private boolean printOut = false;

	// ==================== CONSTRUCTOR ====================

	/**
	 * Creates a new aggregation calculator worker.
	 * 
	 * @param attribute                 The attribute name
	 * @param entities                  Map of entity IDs to values (not modified by
	 *                                  this worker)
	 * @param numeric                   Whether the attribute is numeric
	 * @param aggregationInitialization Aggregation configuration
	 * @param resultQueue               Thread-safe queue for results
	 * @throws NullPointerException if any required parameter is null
	 */
	public AggregationCalculatorWorker(String attribute, Map<Long, Object> values, boolean numeric,
			AggregationCharacterization aggregationInitialization, BlockingQueue<Aggregation> resultQueue,
			boolean printOut) {

		this.attribute = Objects.requireNonNull(attribute, "Attribute cannot be null");
		this.values = Objects.requireNonNull(values, "Entities map cannot be null");
		this.numeric = numeric;
		this.aggregationInitialization = Objects.requireNonNull(aggregationInitialization,
				"AggregationInitialization cannot be null");
		this.resultQueue = Objects.requireNonNull(resultQueue, "Result queue cannot be null");
		this.printOut = printOut;
	}

	// ==================== RUNNABLE IMPLEMENTATION ====================

	@Override
	public void run() {
		try {
			Aggregation result = AggregationCalculator.calculate(attribute, values, numeric, aggregationInitialization,
					printOut);
			resultQueue.offer(result);
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error calculating aggregation for '" + attribute + "'", e);
		}
	}

	// ==================== GETTERS ====================

	/**
	 * Get the attribute name this worker is processing.
	 */
	public String getAttribute() {
		return attribute;
	}

	/**
	 * Check if this worker is processing numeric data.
	 */
	public boolean isNumeric() {
		return numeric;
	}

	/**
	 * Get the number of entities this worker is processing.
	 */
	public int getEntityCount() {
		return values.size();
	}

	public boolean isPrintOut() {
		return printOut;
	}

	public void setPrintOut(boolean printOut) {
		this.printOut = printOut;
	}
}