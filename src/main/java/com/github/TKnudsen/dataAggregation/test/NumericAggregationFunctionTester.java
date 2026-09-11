package com.github.TKnudsen.dataAggregation.test;

import java.util.HashMap;
import java.util.Map;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.NumericAggregationFunction;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.NumericAggregationFunction.PartitioningStrategyEnum;

/**
 * <p>
 * Demo/tester for {@link NumericAggregationFunction}.
 * </p>
 *
 * @since 2015
 */

public class NumericAggregationFunctionTester {

	public static void main(String[] args) {

		Map<Long, Number> elements = new HashMap<>();

		elements.put(1L, 2);
		elements.put(2L, 1.1);
		elements.put(3L, Double.NaN);
		elements.put(4L, 6.6666666666666666666);
		elements.put(5L, Double.MIN_VALUE);
		elements.put(6L, 4);
		elements.put(7L, 7.77);

		AggregationCharacterization initialization = new AggregationCharacterization("Some numeric attribute", 4,
				PartitioningStrategyEnum.FREQUENCY_UNIFORM);

		NumericAggregationFunction function = new NumericAggregationFunction(elements, initialization);
		function.setOutlierPercentage(0.2); // mitigates the Double.MinValue outlier

		for (Bin bin : function.getBins())
			System.out.println(bin);
	}

}
