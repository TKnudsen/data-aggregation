package com.github.TKnudsen.dataAggregation.test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.AggregationCalculationExecutorService;
import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.MixedDataFeatureContainerAdapter;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.NumericAggregationFunction.PartitioningStrategyEnum;
import com.github.TKnudsen.dataAggregation.operation.distanceMeasures.BinMatchingDistanceMeasure;
import com.github.TKnudsen.ComplexDataObject.data.features.FeatureType;
import com.github.TKnudsen.ComplexDataObject.data.features.mixedData.MixedDataFeature;
import com.github.TKnudsen.ComplexDataObject.data.features.mixedData.MixedDataFeatureContainer;
import com.github.TKnudsen.ComplexDataObject.data.features.mixedData.MixedDataFeatureVector;
import com.github.TKnudsen.ComplexDataObject.model.distanceMeasure.IDistanceMeasure;

/**
 * <p>
 * Demo/tester for {@link BinMatchingDistanceMeasure}.
 * </p>
 *
 * @since 2016
 */

public class BinMatchingDistanceMeasureTester {

	private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

	public static void main(String[] args) {

		// create data
		int dataCount = 100;

		List<MixedDataFeatureVector> objects = new ArrayList<>();

		for (int i = 0; i < dataCount; i++) {
			List<MixedDataFeature> entries = new ArrayList<>();

			double rand = Math.random();
			if (rand < 0.25)
				entries.add(new MixedDataFeature("Categorical", "A", FeatureType.STRING));
			else if (rand < 0.8)
				entries.add(new MixedDataFeature("Categorical", "B", FeatureType.STRING));
			else
				entries.add(new MixedDataFeature("Categorical", "C", FeatureType.STRING));

			rand = Math.random();
			if (rand < 0.4)
				entries.add(new MixedDataFeature("Boolean", true, FeatureType.BOOLEAN));
			else
				entries.add(new MixedDataFeature("Boolean", false, FeatureType.BOOLEAN));

			rand = Math.random();
			entries.add(new MixedDataFeature("Numerical1", rand * 10.0, FeatureType.DOUBLE));

			rand = Math.random();
			entries.add(new MixedDataFeature("Numerical2", rand * 5.0, FeatureType.DOUBLE));

			MixedDataFeatureVector o = new MixedDataFeatureVector(entries);
			objects.add(o);
		}

		MixedDataFeatureContainer dataContainer = new MixedDataFeatureContainer(objects);

		// initialize aggregation initialization
		Map<String, AggregationCharacterization> aggregationInitialization = new HashMap<String, AggregationCharacterization>();

		for (String s : dataContainer.getFeatureNames())
			aggregationInitialization.put(s,
					new AggregationCharacterization(s, 6, PartitioningStrategyEnum.FREQUENCY_UNIFORM));

		// test aggregations
		List<Aggregation> aggregations = new ArrayList<>();
		AggregationCalculationExecutorService executor = new AggregationCalculationExecutorService(
				new MixedDataFeatureContainerAdapter(dataContainer), aggregationInitialization, THREAD_COUNT);
		try {
			aggregations = executor.start();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		System.out.println(aggregations);

		// create DistanceMeasure
		IDistanceMeasure<MixedDataFeatureVector> distanceMeasure = new BinMatchingDistanceMeasure(dataContainer);

		for (int i = 0; i < 5; i++)
			for (int j = 0; j < 5; j++) {
				MixedDataFeatureVector o1 = objects.get((int) (Math.random() * dataCount));
				MixedDataFeatureVector o2 = objects.get((int) (Math.random() * dataCount));
				System.out.println("Distance between o1: " + o1 + " and o2: " + o2 + " is "
						+ distanceMeasure.getDistance(o1, o2) + ".");
			}
	}
}
