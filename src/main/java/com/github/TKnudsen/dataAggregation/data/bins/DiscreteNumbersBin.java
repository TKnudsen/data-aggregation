package com.github.TKnudsen.dataAggregation.data.bins;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import com.github.TKnudsen.ComplexDataObject.model.tools.StringTools;

/**
 * <p>
 * A bin for discrete numeric values, i.e. a bin whose elements are expected to
 * share (in non-strict mode: mostly share) a single numeric value, as opposed
 * to {@link NumbersBin}, which represents a continuous range.
 * </p>
 *
 * <p>
 * Example: a bin containing [5, 5, 5, 5] for integer values, or
 * [3.14, 3.14, 3.14] for a discrete floating point value.
 * </p>
 *
 * @since 2013
 */
public class DiscreteNumbersBin extends NumbersBin {

	private static final Logger LOGGER = Logger.getLogger(DiscreteNumbersBin.class.getName());

	private static final double VALUE_TOLERANCE = 1e-9;

	private final boolean strictMode;

	private final AtomicReference<Set<Number>> distinctValuesCache = new AtomicReference<>();

	/**
	 * Lenient constructor: does not reject elements holding more than one
	 * distinct value.
	 *
	 * @param elements        map of element IDs to numeric values
	 * @param aggregationName name of the parent aggregation
	 */
	public DiscreteNumbersBin(Map<Long, Number> elements, String aggregationName) {
		this(elements, aggregationName, false);
	}

	/**
	 * @param elements        map of element IDs to numeric values
	 * @param aggregationName name of the parent aggregation
	 * @param strictMode      if true, all elements must share the exact same
	 *                        value
	 * @throws IllegalArgumentException if strictMode is set and the elements
	 *                                  disagree on the value
	 */
	public DiscreteNumbersBin(Map<Long, Number> elements, String aggregationName, boolean strictMode) {
		super(elements, aggregationName);

		this.strictMode = strictMode;

		if (strictMode)
			Bins.validateAllIdenticalValues(elements);
	}

	/**
	 * @return the distinct numeric values held by this bin, ascending
	 */
	public Set<Number> getUniqueValues() {
		Set<Number> cached = distinctValuesCache.get();
		if (cached != null)
			return cached;

		Set<Number> collected = new TreeSet<>((a, b) -> Double.compare(a.doubleValue(), b.doubleValue()));
		Map<Long, ?> elements = getElements();
		if (elements != null)
			for (Object value : elements.values())
				if (value instanceof Number)
					collected.add((Number) value);

		distinctValuesCache.compareAndSet(null, collected);
		return distinctValuesCache.get();
	}

	/**
	 * @return the smallest distinct value in this bin, or NaN if empty
	 */
	public Number getDiscreteValue() {
		Set<Number> values = getUniqueValues();
		return values.isEmpty() ? Double.NaN : values.iterator().next();
	}

	/**
	 * @return true if every element in this bin shares the same value
	 */
	public boolean isTrulyDiscrete() {
		return getUniqueValues().size() <= 1;
	}

	public int getUniqueValueCount() {
		return getUniqueValues().size();
	}

	@Override
	public double getSumOfSquares() {
		return super.getSumOfSquares();
	}

	@Override
	public double getAverageSquaredDeviation() {
		return super.getAverageSquaredDeviation();
	}

	@Override
	public synchronized String getName() {
		if (name == null)
			name = buildDisplayName();
		return name;
	}

	private String buildDisplayName() {
		Set<Number> values = getUniqueValues();

		if (values.isEmpty())
			return "[empty]";

		StringBuilder builder = new StringBuilder();
		for (Number value : values) {
			if (builder.length() > 0)
				builder.append(", ");
			builder.append(formatValue(value));
		}
		return builder.toString();
	}

	private static String formatValue(Number value) {
		if (value == null)
			return "null";

		double raw = value.doubleValue();

		if (Double.isNaN(raw))
			return "NaN";
		if (Double.isInfinite(raw))
			return raw > 0 ? "Infinity" : "-Infinity";
		if (raw == Math.rint(raw))
			return String.valueOf((long) raw);

		return StringTools.truncateDouble(raw, 2);
	}

	@Override
	public int compareTo(Bin other) {
		if (other == null)
			return 1;
		if (!(other instanceof DiscreteNumbersBin))
			return super.compareTo(other);

		double thisValue = this.getDiscreteValue().doubleValue();
		double otherValue = ((DiscreteNumbersBin) other).getDiscreteValue().doubleValue();

		if (Double.isNaN(thisValue) && Double.isNaN(otherValue))
			return 0;
		if (Double.isNaN(thisValue))
			return 1;
		if (Double.isNaN(otherValue))
			return -1;

		return Double.compare(thisValue, otherValue);
	}

	@Override
	public synchronized void clearCache() {
		super.clearCache();
		distinctValuesCache.set(null);
	}

	@Override
	public void warmupCache() {
		super.warmupCache();
		getUniqueValues();
	}

	@Override
	public String toString() {
		Set<Number> values = getUniqueValues();
		return "DiscreteNumbersBin[name=" + getName() + ", size=" + size() + ", uniqueValues=" + values.size()
				+ ", discrete=" + isTrulyDiscrete() + ", value=" + getDiscreteValue() + "]";
	}

	public String toDetailedString() {
		Set<Number> values = getUniqueValues();

		StringBuilder sb = new StringBuilder();
		sb.append("DiscreteNumbersBin Details:\n");
		sb.append("  ID: ").append(getID()).append('\n');
		sb.append("  Name: ").append(getName()).append('\n');
		sb.append("  Aggregation: ").append(getAggregationName()).append('\n');
		sb.append("  Size: ").append(size()).append(" elements\n");
		sb.append("  Unique Values: ").append(values.size()).append('\n');
		sb.append("  Truly Discrete: ").append(isTrulyDiscrete()).append('\n');
		sb.append("  Discrete Value: ").append(getDiscreteValue()).append('\n');
		sb.append("  Strict Mode: ").append(strictMode).append('\n');

		if (values.size() <= 10) {
			sb.append("  All Values: ").append(values).append('\n');
		} else {
			sb.append("  All Values: [").append(values.size()).append(" values, showing first 10]\n");
			int shown = 0;
			for (Number value : values) {
				if (shown++ >= 10)
					break;
				sb.append("    ").append(value).append('\n');
			}
		}

		return sb.toString();
	}

	@Override
	public boolean validate() {
		if (!super.validate())
			return false;

		try {
			Set<Number> values = getUniqueValues();
			if (values == null)
				return false;
			if (strictMode && values.size() > 1)
				return false;

			if (isTrulyDiscrete()) {
				double discrete = getDiscreteValue().doubleValue();
				double centroid = getCentroid().doubleValue();
				if (Math.abs(discrete - centroid) > VALUE_TOLERANCE)
					return false;
			}

			return true;
		} catch (Exception e) {
			LOGGER.warning("Validation failed: " + e.getMessage());
			return false;
		}
	}

	public boolean containsDiscreteValue(Number value) {
		if (value == null)
			return false;

		double target = value.doubleValue();
		for (Number candidate : getUniqueValues())
			if (Math.abs(candidate.doubleValue() - target) < VALUE_TOLERANCE)
				return true;

		return false;
	}

	/**
	 * @return the number of elements in this bin (equals the frequency of the
	 *         discrete value for a truly discrete bin)
	 */
	public int getFrequency() {
		return size();
	}

	/**
	 * @param value the value to count
	 * @return the number of elements matching the given value
	 */
	public int getFrequency(Number value) {
		if (value == null)
			return 0;

		double target = value.doubleValue();
		int count = 0;
		for (Number n : getNumericElements().values())
			if (n != null && Math.abs(n.doubleValue() - target) < VALUE_TOLERANCE)
				count++;

		return count;
	}
}
