package com.github.TKnudsen.dataAggregation.test.complexDataObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.AggregationCalculationExecutorService;
import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.ComplexDataContainerAdapter;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.NumericAggregationFunction.PartitioningStrategyEnum;
import com.github.TKnudsen.ComplexDataObject.data.complexDataObject.ComplexDataContainer;
import com.github.TKnudsen.ComplexDataObject.data.complexDataObject.ComplexDataObject;
import com.github.TKnudsen.ComplexDataObject.data.dataFactory.DataSets;

/**
 * Demo/tester for parallel bin calculation over a {@link ComplexDataContainer}.
 *
 * @since 2016
 */
public class ComplexDataObjectAttributesThreadedBinningTester {

	private static int initialAggregationLevel = 6;
	private static PartitioningStrategyEnum partitioningStrategyDefault = PartitioningStrategyEnum.FREQUENCY_UNIFORM;

	private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

	public static void main(String[] args) {

		List<ComplexDataObject> createTitanicDataSet = DataSets.titanicDataSet();

		ComplexDataContainer dataContainer = new ComplexDataContainer(createTitanicDataSet);

		Map<String, AggregationCharacterization> aggregationInitialization = new HashMap<String, AggregationCharacterization>();

		for (String s : dataContainer.getAttributeNames())
			aggregationInitialization.put(s,
					new AggregationCharacterization(s, initialAggregationLevel, partitioningStrategyDefault));
		// example for explicit AggregationInitialization
		aggregationInitialization.put("FARE",
				new AggregationCharacterization("FARE", 13, PartitioningStrategyEnum.DOMAIN_UNIFORM));

		List<Aggregation> aggregations = new ArrayList<>();
		AggregationCalculationExecutorService executor = new AggregationCalculationExecutorService(
				new ComplexDataContainerAdapter(dataContainer), aggregationInitialization, THREAD_COUNT);
		try {
			aggregations = executor.start();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		System.out.println();
		System.out.println("HERE COMES THE BINNING RESULT:");
		System.out.println();

		for (Aggregation aggregation : aggregations)
			System.out.println(aggregation);
	}
}
