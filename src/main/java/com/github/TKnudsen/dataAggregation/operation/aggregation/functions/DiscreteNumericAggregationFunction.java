package com.github.TKnudsen.dataAggregation.operation.aggregation.functions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.data.bins.Bins;
import com.github.TKnudsen.dataAggregation.data.bins.DiscreteNumbersBin;

/**
 * <p>
 * Aggregation function for numeric attributes with only a small number of
 * distinct values. One bin is created per distinct value -- independent of
 * the chosen partitioning strategy and aggregation level -- and bins are then
 * merged down to the target level, if necessary. Outlier handling is not
 * applied.
 * </p>
 *
 * @since 2012
 */
public class DiscreteNumericAggregationFunction extends NumericAggregationFunction {

	public DiscreteNumericAggregationFunction(Map<Long, Number> elements,
			AggregationCharacterization aggregationInitialization) {
		super(elements, aggregationInitialization);
	}

	@Override
	public void calculateAggregation() {
		long startTime = System.currentTimeMillis();

		bins = new ArrayList<>(groupByValue(elements));

		if (bins.size() > aggregationLevel)
			collapseToAggregationLevel();

		if (isPrintOut())
			System.out.println("DiscreteNumericAggregationFunction.calculateAggregation took "
					+ (System.currentTimeMillis() - startTime) + " ms for " + elements.size() + " elements");
	}

	/**
	 * Groups the given elements by exact numeric value and wraps each group in a
	 * {@link DiscreteNumbersBin}, ordered by ascending value.
	 */
	private List<DiscreteNumbersBin> groupByValue(Map<Long, Number> elements) {
		TreeMap<Number, Map<Long, Number>> byValue = new TreeMap<>(
				(a, b) -> Double.compare(a.doubleValue(), b.doubleValue()));

		for (Map.Entry<Long, Number> entry : elements.entrySet())
			byValue.computeIfAbsent(entry.getValue(), v -> new LinkedHashMap<>()).put(entry.getKey(), entry.getValue());

		List<DiscreteNumbersBin> result = new ArrayList<>(byValue.size());
		for (Map<Long, Number> group : byValue.values())
			result.add(new DiscreteNumbersBin(group, name));

		return result;
	}

	/**
	 * Repeatedly merges the smallest neighboring pair of bins (via the
	 * {@link Bins} utility) until at most {@code aggregationLevel} bins remain.
	 */
	private void collapseToAggregationLevel() {
		while (bins.size() > aggregationLevel && bins.size() > 1) {
			int mergeAt = Bins.findSmallestNeighboringPair(bins);
			if (mergeAt == -1)
				break;

			Bin merged = Bins.merge(bins.get(mergeAt), bins.get(mergeAt + 1));
			bins.set(mergeAt, merged);
			bins.remove(mergeAt + 1);
		}
	}
}
