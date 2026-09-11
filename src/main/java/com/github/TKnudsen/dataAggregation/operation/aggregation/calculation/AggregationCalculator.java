package com.github.TKnudsen.dataAggregation.operation.aggregation.calculation;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.DiscreteNumericAggregationFunction;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.NumericAggregationFunction;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.StringAggregationFunction;
import com.github.TKnudsen.ComplexDataObject.data.features.Feature;
import com.github.TKnudsen.ComplexDataObject.model.io.parsers.objects.Parsers;
import com.github.TKnudsen.ComplexDataObject.model.tools.StatisticsSupport;

/**
 * Pure calculation class for creating aggregations. No threading concerns, just
 * business logic.
 * 
 * <p>
 * <b>Features:</b>
 * </p>
 * <ul>
 * <li>Automatic detection of discrete vs continuous numeric data</li>
 * <li>Robust type conversion with multiple fallback strategies</li>
 * <li>Comprehensive error handling and reporting</li>
 * <li>Support for Feature<?> wrapper objects</li>
 * </ul>
 *
 * @since 2016
 */
public final class AggregationCalculator {

	private static final Logger LOGGER = Logger.getLogger(AggregationCalculator.class.getName());

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

	// ==================== CONSTRUCTOR ====================

	/**
	 * Private constructor - utility class cannot be instantiated.
	 */
	private AggregationCalculator() {
		throw new UnsupportedOperationException("AggregationCalculator is a utility class");
	}

	// ==================== PUBLIC API ====================

	/**
	 * Calculate aggregation with default logging (enabled).
	 * 
	 * @param attribute Attribute name
	 * @param values    Entity ID to value map
	 * @param numeric   Whether attribute is numeric
	 * @param init      Aggregation configuration
	 * @return Calculated aggregation, or null if calculation fails
	 */
	public static Aggregation calculate(String attribute, Map<Long, Object> values, boolean numeric,
			AggregationCharacterization init) {
		return calculate(attribute, values, numeric, init, true);
	}

	/**
	 * Calculate a single aggregation from attribute values.
	 * 
	 * @param attribute Attribute name
	 * @param values    Entity ID to value map
	 * @param numeric   Whether attribute is numeric
	 * @param init      Aggregation configuration
	 * @param printOut  Whether to print info messages (errors always print)
	 * @return Calculated aggregation, or null if calculation fails
	 */
	public static Aggregation calculate(String attribute, Map<Long, Object> values, boolean numeric,
			AggregationCharacterization init, boolean printOut) {

		// Validation
		Objects.requireNonNull(attribute, "Attribute cannot be null");
		Objects.requireNonNull(values, "Values cannot be null");
		Objects.requireNonNull(init, "Initialization cannot be null");

		if (attribute.trim().isEmpty())
			throw new IllegalArgumentException("Attribute name cannot be empty");

		long startTime = System.currentTimeMillis();

		Aggregation aggregation = null;

		try {
			// Check for empty input
			if (values.isEmpty()) {
				LOGGER.warning("Attribute '" + attribute + "' has no values - skipping");
				return null;
			}

			// Create appropriate aggregation
			aggregation = numeric ? createNumericAggregation(attribute, values, init, printOut)
					: createStringAggregation(attribute, values, init, printOut);

			// Log success
			if (aggregation != null && printOut) {
				long elapsed = System.currentTimeMillis() - startTime;
				int totalBins = aggregation.getBins().size();
				int totalElements = aggregation.AggregationFunction().getTotalBinElementCount();
				int invalidElements = values.size() - totalElements;
				String functionType = aggregation.AggregationFunction().getClass().getSimpleName();

				System.out.println("AggregationCalculator: Completed '" + attribute + "' - " + values.size()
						+ " values, " + invalidElements + " invalid, " + totalBins + " bins (" + functionType + ") in "
						+ elapsed + "ms");
			}

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Unexpected error processing '" + attribute + "'", e);
		}

		return aggregation;
	}

	// ==================== NUMERIC AGGREGATION ====================

	/**
	 * Create numeric aggregation, choosing between discrete and continuous based on
	 * data distribution.
	 */
	private static Aggregation createNumericAggregation(String attribute, Map<Long, Object> values,
			AggregationCharacterization init, boolean printOut) {

		ConversionResult<Number> result = convertToNumeric(attribute, values, printOut);

		// Check for empty result
		if (result.values.isEmpty()) {
			LOGGER.warning(
					"No valid numeric values for '" + attribute + "' (" + result.errorCount + " conversion errors)");
			return null;
		}

		// Check for all-NaN case
		long validCount = result.values.values().stream().filter(n -> Double.isFinite(n.doubleValue())).count();

		if (validCount == 0) {
			LOGGER.warning(
					"All numeric values for '" + attribute + "' are NaN/Infinite - cannot create aggregation");
			return null;
		}

		// Determine if data is discrete or continuous
		StatisticsSupport stats = new StatisticsSupport(result.values.values());

		NumericAggregationFunction aggregationFunction;

		if (stats.isLikelyDiscrete(DISCRETE_THRESHOLD)) {
			if (printOut) {
				System.out.println("AggregationCalculator: '" + attribute + "' -> DISCRETE numeric ("
						+ result.values.size() + " valid, " + result.errorCount + " errors)");
			}
			aggregationFunction = new DiscreteNumericAggregationFunction(result.values, init);
		} else {
			if (printOut) {
				System.out.println("AggregationCalculator: '" + attribute + "' -> CONTINUOUS numeric ("
						+ result.values.size() + " valid, " + result.errorCount + " errors)");
			}
			aggregationFunction = new NumericAggregationFunction(result.values, init);
		}

		return new Aggregation(aggregationFunction);
	}

	/**
	 * Convert raw entity values to numeric, handling type conversions and errors.
	 */
	private static ConversionResult<Number> convertToNumeric(String attribute, Map<Long, Object> values,
			boolean printOut) {

		Map<Long, Number> numericEntities = new HashMap<>((int) ((values.size() * 4) / 3) + 1 // Optimize capacity
		);

		int conversionErrors = 0;
		int nanCount = 0;

		for (Map.Entry<Long, Object> entry : values.entrySet()) {
			Long id = entry.getKey();
			Object value = entry.getValue();

			NumberConversionResult conversionResult = convertToNumber(value);

			if (conversionResult.isValid()) {
				numericEntities.put(id, conversionResult.value);

				if (Double.isNaN(conversionResult.value.doubleValue())) {
					nanCount++;
				}
			} else {
				conversionErrors++;

				// Report first few errors to help debugging
				if (conversionErrors <= MAX_ERROR_REPORTS) {
					String valueStr = (value == null) ? "null"
							: "'" + value + "' (type: " + value.getClass().getSimpleName() + ")";
					LOGGER.warning("'" + attribute + "' entity " + id + " has non-numeric value: " + valueStr);
				}
			}
		}

		// Summary report
		if (conversionErrors > MAX_ERROR_REPORTS)
			LOGGER.warning(
					"... and " + (conversionErrors - MAX_ERROR_REPORTS) + " more errors for '" + attribute + "'");

		if (nanCount > 0 && printOut)
			System.out.println("AggregationCalculator: '" + attribute + "' contains " + nanCount + " NaN values");

		return new ConversionResult<>(numericEntities, conversionErrors);
	}

	/**
	 * Attempt to convert a value to Number, trying multiple strategies.
	 */
	private static NumberConversionResult convertToNumber(Object value) {
		// Handle null
		if (value == null)
			return NumberConversionResult.valid(Double.NaN);

		// Handle "N/A"
		if (NA_VALUE.equals(value.toString()))
			return NumberConversionResult.valid(Double.NaN);

		// Handle Feature<?> objects - extract underlying value
		if (value instanceof Feature<?>) {
			Object featureValue = ((Feature<?>) value).getFeatureValue();
			Number parsed = Parsers.parseDouble(featureValue);
			return (parsed != null) ? NumberConversionResult.valid(parsed) : NumberConversionResult.invalid();
		}

		// Already a Number - return as-is (valid)
		if (value instanceof Number)
			return NumberConversionResult.valid((Number) value);

		// Try parsing as number
		Number parsed = Parsers.parseDouble(value);
		return (parsed != null) ? NumberConversionResult.valid(parsed) : NumberConversionResult.invalid();
	}

	// ==================== STRING AGGREGATION ====================

	/**
	 * Create string aggregation.
	 */
	private static Aggregation createStringAggregation(String attribute, Map<Long, Object> values,
			AggregationCharacterization init, boolean printOut) {

		ConversionResult<String> result = convertToString(values);

		if (result.values.isEmpty()) {
			LOGGER.warning(
					"No valid string values for '" + attribute + "' (" + result.errorCount + " null/invalid)");
			return null;
		}

		if (printOut)
			System.out.println("AggregationCalculator: '" + attribute + "' -> STRING (" + result.values.size()
					+ " valid, " + result.errorCount + " null/invalid)");

		StringAggregationFunction aggregationFunction = new StringAggregationFunction(result.values,
				init.getAggregationLevel(), init.getAggregationName());

		return new Aggregation(aggregationFunction);
	}

	/**
	 * Convert raw entity values to strings, filtering out nulls and handling
	 * Features.
	 */
	private static ConversionResult<String> convertToString(Map<Long, Object> values) {
		Map<Long, String> stringEntities = new HashMap<>((int) ((values.size() * 4) / 3) + 1);

		int nullOrInvalidCount = 0;

		for (Map.Entry<Long, Object> entry : values.entrySet()) {
			Long id = entry.getKey();
			Object value = entry.getValue();

			// Handle null
			if (value == null) {
				nullOrInvalidCount++;
				continue;
			}

			// Handle Feature<?> objects - extract underlying value
			if (value instanceof Feature<?>) {
				Object featureValue = ((Feature<?>) value).getFeatureValue();

				if (featureValue == null) {
					nullOrInvalidCount++;
					continue;
				}

				value = featureValue;
			}

			// Convert to string
			String stringValue = (value instanceof String) ? (String) value : value.toString();

			// Filter out empty strings
			if (stringValue.isEmpty()) {
				nullOrInvalidCount++;
				continue;
			}

			stringEntities.put(id, stringValue);
		}

		return new ConversionResult<>(stringEntities, nullOrInvalidCount);
	}

	// ==================== HELPER CLASSES ====================

	/**
	 * Result of a type conversion operation.
	 */
	private static class ConversionResult<T> {
		final Map<Long, T> values;
		final int errorCount;

		ConversionResult(Map<Long, T> values, int errorCount) {
			this.values = values;
			this.errorCount = errorCount;
		}
	}

	/**
	 * Result of attempting to convert a value to Number.
	 */
	private static class NumberConversionResult {
		final Number value;
		final boolean valid;

		private NumberConversionResult(Number value, boolean valid) {
			this.value = value;
			this.valid = valid;
		}

		static NumberConversionResult valid(Number value) {
			return new NumberConversionResult(value, true);
		}

		static NumberConversionResult invalid() {
			return new NumberConversionResult(null, false);
		}

		boolean isValid() {
			return valid;
		}
	}
}