package com.github.TKnudsen.dataAggregation.data.bins;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Logger;

import com.github.TKnudsen.ComplexDataObject.model.io.parsers.objects.Parsers;
import com.github.TKnudsen.ComplexDataObject.model.tools.StatisticsSupport;
import com.github.TKnudsen.ComplexDataObject.model.tools.StringTools;

/**
 * <p>
 * Represents a bin containing numeric values with statistical properties.
 * Leverages Bins utility class for common operations.
 * </p>
 *
 * @since 2013
 */
public class NumbersBin extends Bin {

	private static final Logger LOGGER = Logger.getLogger(NumbersBin.class.getName());

	// Cached statistical data
	private volatile StatisticsSupport dataStatistics;

	// Cached key values for performance
	private volatile Number centroid;
	private volatile Double sumOfSquares;
	private volatile Double averageSquaredDeviation;
	private volatile Integer cachedCount;

	// Configuration
	private static final int DEFAULT_DECIMAL_PLACES = 2;
	private static final double EPSILON = 1e-9;

	/**
	 * Constructs a NumbersBin with numeric elements
	 * 
	 * @param elements        map of element IDs to numeric values
	 * @param aggregationName name of the parent aggregation
	 * @throws IllegalArgumentException if parameters are invalid
	 */
	public NumbersBin(Map<Long, Number> elements, String aggregationName) {
		super(elements, null, aggregationName);

		// Validation delegated to Bins utility
		Bins.validateNumericElements(elements);
	}

	// ==================== TYPE-SAFE ELEMENT ACCESS ====================
	// Now delegated to Bins utility for consistency

	/**
	 * Gets elements as a properly typed map Delegates to Bins utility for type-safe
	 * access
	 * 
	 * @return map of element IDs to numeric values
	 */
	public Map<Long, Number> getNumericElements() {
		return Bins.getNumericElements(this);
	}

	/**
	 * Gets a specific numeric element by ID
	 * 
	 * @param elementId the element ID
	 * @return the numeric value, or null if not found
	 */
	public Number getNumericElement(Long elementId) {
		if (elementId == null)
			return null;

		Object value = getElement(elementId);
		return (value instanceof Number) ? (Number) value : null;
	}

	// ==================== CENTROID ====================

	/**
	 * Gets the centroid (median) of the bin values Thread-safe lazy initialization
	 * 
	 * @return centroid value (never null, may be NaN)
	 */
	public Number getCentroid() {
		if (centroid == null) {
			synchronized (this) {
				if (centroid == null) {
					calculateCentroid();
				}
			}
		}
		return centroid;
	}

	/**
	 * Calculates the centroid as the median of values
	 */
	private void calculateCentroid() {
		StatisticsSupport stats = getDataStatistics();

		if (stats == null || getElementCount() == 0) {
			this.centroid = Double.valueOf(Double.NaN);
			return;
		}

		Number median = stats.getMedian();
		this.centroid = (median != null) ? median : Double.valueOf(Double.NaN);
	}

	/**
	 * Gets element count with caching
	 */
	private int getElementCount() {
		if (cachedCount == null) {
			StatisticsSupport stats = getDataStatistics();
			cachedCount = (stats != null) ? stats.getCount() : 0;
		}
		return cachedCount;
	}

	// ==================== SUM OF SQUARES ====================

	/**
	 * Gets the sum of squared deviations from centroid Formula: sum(xi - centroid)^2
	 * 
	 * @return sum of squared deviations
	 */
	public double getSumOfSquares() {
		if (sumOfSquares == null) {
			synchronized (this) {
				if (sumOfSquares == null) {
					calculateSquaredDeviations();
				}
			}
		}
		return sumOfSquares;
	}

	/**
	 * Gets the average squared deviation from centroid Formula: sum(xi - centroid)^2 /
	 * n
	 * 
	 * @return average squared deviation
	 */
	public double getAverageSquaredDeviation() {
		if (averageSquaredDeviation == null) {
			synchronized (this) {
				if (averageSquaredDeviation == null) {
					calculateSquaredDeviations();
				}
			}
		}
		return averageSquaredDeviation;
	}

	/**
	 * Calculates sum and average of squared deviations
	 */
	private void calculateSquaredDeviations() {
		int count = getElementCount();

		if (count == 0) {
			this.sumOfSquares = 0.0;
			this.averageSquaredDeviation = 0.0;
			return;
		}

		Number centroidValue = getCentroid();
		if (centroidValue == null) {
			this.sumOfSquares = Double.NaN;
			this.averageSquaredDeviation = Double.NaN;
			return;
		}

		double centroidDouble = centroidValue.doubleValue();

		if (Double.isNaN(centroidDouble)) {
			this.sumOfSquares = Double.NaN;
			this.averageSquaredDeviation = Double.NaN;
			return;
		}

		double sum = 0.0;
		int validCount = 0;

		StatisticsSupport stats = getDataStatistics();
		if (stats == null) {
			this.sumOfSquares = Double.NaN;
			this.averageSquaredDeviation = Double.NaN;
			return;
		}

		for (Number value : stats.getValues()) {
			if (value != null && !Double.isNaN(value.doubleValue())) {
				double deviation = value.doubleValue() - centroidDouble;
				sum += deviation * deviation;
				validCount++;
			}
		}

		this.sumOfSquares = sum;
		this.averageSquaredDeviation = (validCount > 0) ? (sum / validCount) : 0.0;

		if (validCount > 0 && (Double.isNaN(sumOfSquares) || Double.isNaN(averageSquaredDeviation))) {
			LOGGER.warning(
					"NaN in squared deviation calculation. Centroid: " + centroidDouble + ", ValidCount: " + validCount);
		}
	}

	// ==================== STATISTICS ====================

	/**
	 * Gets statistical support for the bin values Thread-safe with double-checked
	 * locking
	 * 
	 * @return statistics support object (never null)
	 */
	public StatisticsSupport getDataStatistics() {
		if (dataStatistics == null) {
			synchronized (this) {
				if (dataStatistics == null) {
					calculateDataStatistics();
				}
			}
		}
		return dataStatistics;
	}

	/**
	 * Calculates statistics from element values
	 */
	private void calculateDataStatistics() {
		Map<Long, ?> elements = getElements();

		if (elements == null || elements.isEmpty()) {
			this.dataStatistics = new StatisticsSupport(new ArrayList<Double>(0));
			this.cachedCount = 0;
			return;
		}

		Collection<Double> values = Parsers.parseDoubles(elements.values());
		this.dataStatistics = new StatisticsSupport(values);
		this.cachedCount = values.size();
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
	 * Generates name based on min-max range
	 */
	private String generateName() {
		int count = getElementCount();

		if (count == 0)
			return "[empty]";

		StatisticsSupport stats = getDataStatistics();
		Double min = stats.getMin();
		Double max = stats.getMax();

		if (min == null || max == null)
			return "[invalid]";

		String minStr = formatNumber(min);
		String maxStr = formatNumber(max);

		return minStr.equals(maxStr) ? "[" + minStr + "]" : "[" + minStr + "-" + maxStr + "]";
	}

	/**
	 * Formats a number for display
	 */
	private String formatNumber(double value) {
		if (Double.isNaN(value))
			return "NaN";
		if (Double.isInfinite(value))
			return value > 0 ? "Infinity" : "-Infinity";

		return StringTools.truncateDouble(value, DEFAULT_DECIMAL_PLACES);
	}

	// ==================== COMPARISON ====================

	@Override
	public int compareTo(Bin other) {
		if (other == null)
			return 1;

		if (!(other instanceof NumbersBin))
			return getClass().getName().compareTo(other.getClass().getName());

		NumbersBin otherBin = (NumbersBin) other;

		Number thisCentroid = this.getCentroid();
		Number otherCentroid = otherBin.getCentroid();

		if (thisCentroid == null && otherCentroid == null)
			return 0;
		if (thisCentroid == null)
			return 1;
		if (otherCentroid == null)
			return -1;

		double thisValue = thisCentroid.doubleValue();
		double otherValue = otherCentroid.doubleValue();

		boolean thisNaN = Double.isNaN(thisValue);
		boolean otherNaN = Double.isNaN(otherValue);

		if (thisNaN && otherNaN)
			return 0;
		if (thisNaN)
			return 1;
		if (otherNaN)
			return -1;

		return Double.compare(thisValue, otherValue);
	}

	// ==================== EQUALS/HASHCODE ====================

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (!(obj instanceof NumbersBin))
			return false;

		NumbersBin other = (NumbersBin) obj;

		Number thisCentroid = this.getCentroid();
		Number otherCentroid = other.getCentroid();

		if (thisCentroid == null && otherCentroid == null)
			return true;
		if (thisCentroid == null || otherCentroid == null)
			return false;

		double thisValue = thisCentroid.doubleValue();
		double otherValue = otherCentroid.doubleValue();

		if (Double.isNaN(thisValue) && Double.isNaN(otherValue))
			return true;
		if (Double.isNaN(thisValue) || Double.isNaN(otherValue))
			return false;

		return Math.abs(thisValue - otherValue) < EPSILON;
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();

		Number centroidValue = getCentroid();
		if (centroidValue != null && !Double.isNaN(centroidValue.doubleValue())) {
			double rounded = Math.round(centroidValue.doubleValue() / EPSILON) * EPSILON;
			long bits = Double.doubleToLongBits(rounded);
			result = 31 * result + (int) (bits ^ (bits >>> 32));
		}

		return result;
	}

	// ==================== STATISTICAL UTILITY METHODS ====================

	/**
	 * Gets the range (max - min) of values
	 * 
	 * @return range, or NaN if invalid
	 */
	public double getRange() {
		StatisticsSupport stats = getDataStatistics();
		Double min = stats.getMin();
		Double max = stats.getMax();

		if (min == null || max == null)
			return Double.NaN;

		return max - min;
	}

	/**
	 * Gets the standard deviation
	 * 
	 * @return standard deviation
	 */
	public double getStandardDeviation() {
		return getDataStatistics().getStandardDeviation();
	}

	/**
	 * Gets the mean (average)
	 * 
	 * @return mean value
	 */
	public double getMean() {
		return getDataStatistics().getMean();
	}

	/**
	 * Gets the minimum value
	 * 
	 * @return minimum value, or NaN if invalid
	 */
	public double getMin() {
		Double min = getDataStatistics().getMin();
		return (min != null) ? min : Double.NaN;
	}

	/**
	 * Gets the maximum value
	 * 
	 * @return maximum value, or NaN if invalid
	 */
	public double getMax() {
		Double max = getDataStatistics().getMax();
		return (max != null) ? max : Double.NaN;
	}

	/**
	 * Gets the variance
	 * 
	 * @return variance
	 */
	public double getVariance() {
		return getDataStatistics().getVariance();
	}

	/**
	 * Gets a percentile value
	 * 
	 * @param percentile percentile to calculate (0-100)
	 * @return percentile value, or NaN if invalid
	 */
	public double getPercentile(double percentile) {
		if (percentile < 0 || percentile > 100)
			throw new IllegalArgumentException("Percentile must be between 0 and 100");

		StatisticsSupport stats = getDataStatistics();
		if (stats == null || getElementCount() == 0)
			return Double.NaN;

		// Common percentiles
		if (percentile == 25)
			return stats.getPercentile(25);
		if (percentile == 50)
			return stats.getMedian();
		if (percentile == 75)
			return stats.getPercentile(75);

		// General percentile
		return stats.getPercentile(percentile);
	}

	/**
	 * Gets interquartile range (Q3 - Q1)
	 * 
	 * @return IQR value
	 */
	public double getInterquartileRange() {
		StatisticsSupport stats = getDataStatistics();
		return stats.getPercentile(75) - stats.getPercentile(25);
	}

	// ==================== CACHE MANAGEMENT ====================

	/**
	 * Clears all cached calculations
	 */
	public synchronized void clearCache() {
		dataStatistics = null;
		centroid = null;
		sumOfSquares = null;
		averageSquaredDeviation = null;
		cachedCount = null;
		name = null;
	}

	/**
	 * Warms up the cache by calculating all values
	 */
	public void warmupCache() {
		getDataStatistics();
		getCentroid();
		getSumOfSquares();
		getAverageSquaredDeviation();
		getName();
	}

	// ==================== STRING REPRESENTATION ====================

	@Override
	public String toString() {
		Number centroidValue = getCentroid();
		String centroidStr = (centroidValue != null) ? formatNumber(centroidValue.doubleValue()) : "null";

		return "NumbersBin[name=" + getName() + ", size=" + size() + ", centroid=" + centroidStr + ", range="
				+ formatNumber(getRange()) + "]";
	}

	/**
	 * Creates a detailed string with all statistics
	 * 
	 * @return detailed representation
	 */
	public String toDetailedString() {
		StringBuilder sb = new StringBuilder();
		sb.append("NumbersBin Details:\n");
		sb.append("  ID: ").append(getID()).append("\n");
		sb.append("  Name: ").append(getName()).append("\n");
		sb.append("  Aggregation: ").append(getAggregationName()).append("\n");
		sb.append("  Size: ").append(size()).append(" elements\n");
		sb.append("  Min: ").append(formatNumber(getMin())).append("\n");
		sb.append("  Max: ").append(formatNumber(getMax())).append("\n");
		sb.append("  Mean: ").append(formatNumber(getMean())).append("\n");
		sb.append("  Median: ").append(formatNumber(getCentroid().doubleValue())).append("\n");
		sb.append("  Std Dev: ").append(formatNumber(getStandardDeviation())).append("\n");
		sb.append("  Variance: ").append(formatNumber(getVariance())).append("\n");
		sb.append("  Range: ").append(formatNumber(getRange())).append("\n");
		sb.append("  Q1: ").append(formatNumber(getDataStatistics().getPercentile(25))).append("\n");
		sb.append("  Q3: ").append(formatNumber(getDataStatistics().getPercentile(75))).append("\n");
		sb.append("  IQR: ").append(formatNumber(getInterquartileRange())).append("\n");
		return sb.toString();
	}

	// ==================== VALIDATION ====================

	@Override
	public boolean validate() {
		if (!super.validate())
			return false;

		try {
			StatisticsSupport stats = getDataStatistics();
			if (stats == null)
				return false;

			Number cent = getCentroid();
			if (cent == null)
				return false;

			double sumSq = getSumOfSquares();
			double avgSq = getAverageSquaredDeviation();

			if (Double.isNaN(sumSq) != Double.isNaN(avgSq))
				return false;

			if (getElementCount() > 0 && !Double.isNaN(sumSq)) {
				double recalculated = sumSq / getElementCount();
				if (Math.abs(recalculated - avgSq) > EPSILON)
					return false;
			}

			return true;

		} catch (Exception e) {
			LOGGER.warning("Validation failed: " + e.getMessage());
			return false;
		}
	}
}
