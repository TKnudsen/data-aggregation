package com.github.TKnudsen.dataAggregation.operation.distanceMeasures;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.ComplexDataObject.data.features.mixedData.MixedDataFeatureContainer;
import com.github.TKnudsen.ComplexDataObject.data.features.mixedData.MixedDataFeatureVector;
import com.github.TKnudsen.ComplexDataObject.model.distanceMeasure.IDistanceMeasure;
import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.AggregationCalculationExecutorService;
import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.MixedDataFeatureContainerAdapter;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.NumericAggregationFunction.PartitioningStrategyEnum;

/**
 * <p>
 * Counts the number of non-matching attribute-bins for two given
 * MixedDataObjects. The two objects need to be contained in the given
 * MixedDataContainer.
 * </p>
 *
 * @since 2016
 */

public class BinMatchingDistanceMeasure implements IDistanceMeasure<MixedDataFeatureVector> {

	private static final Logger LOGGER = Logger.getLogger(BinMatchingDistanceMeasure.class.getName());

	/**
	 * 
	 */
	private static final long serialVersionUID = 2641881687204151358L;

	private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

	private MixedDataFeatureContainer dataContainer;

	private List<Aggregation> aggregations;

	public BinMatchingDistanceMeasure(MixedDataFeatureContainer dataContainer) {
		this.dataContainer = dataContainer;

		initialize();
	}

	private void initialize() {
		// initialize aggregation initialization
		Map<String, AggregationCharacterization> aggregationInitialization = new HashMap<String, AggregationCharacterization>();

		for (String s : dataContainer.getFeatureNames())
			aggregationInitialization.put(s,
					new AggregationCharacterization(s, 6, PartitioningStrategyEnum.FREQUENCY_UNIFORM));

		// test aggregations
//		aggregations = new ArrayList<>();
//		AggregationCalculationExecutorServiceMixedData aggregationCalculationExecutorService = new AggregationCalculationExecutorServiceMixedData(
//				dataContainer, aggregationInitialization, aggregations, 16);
//		aggregationCalculationExecutorService.start();

		AggregationCalculationExecutorService executor = new AggregationCalculationExecutorService(
				new MixedDataFeatureContainerAdapter(dataContainer), aggregationInitialization, THREAD_COUNT);
		try {
			aggregations = executor.start();
		} catch (InterruptedException e) {
			LOGGER.log(Level.SEVERE, "Interrupted while computing aggregations", e);
			Thread.currentThread().interrupt();
		}

		if (LOGGER.isLoggable(Level.FINE))
			LOGGER.fine(String.valueOf(aggregations));
	}

	@Override
	public double getDistance(MixedDataFeatureVector o1, MixedDataFeatureVector o2) {
		if (!dataContainer.contains(o1) || !dataContainer.contains(o2))
			return Math.max(Math.max(o1.getDimensions(), o2.getDimensions()), aggregations.size());

		int dist = aggregations.size();

		for (Aggregation aggregation : aggregations) {
			if (aggregation == null || aggregation.getBins() == null)
				continue;
			for (Bin bin : aggregation) {
				if (bin.containsElement(o1.getID()) && bin.containsElement(o2.getID())) {
					dist -= 1;
					break;
				}
			}
		}

		if (dist < 0)
			LOGGER.warning("getDistance(): something went wrong...");

		return dist;
	}

	@Override
	public String getName() {
		return "BinMatchingDistanceMeasure";
	}

	@Override
	public String getDescription() {
		return "BinMatchingDistanceMeasure";
	}

	@Override
	public double applyAsDouble(MixedDataFeatureVector t, MixedDataFeatureVector u) {
		return getDistance(t, u);
	}
}
