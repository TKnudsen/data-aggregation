package com.github.TKnudsen.dataAggregation.operation.aggregation.executors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.AggregationCalculationExecutorService.DataContainerAdapter;
import com.github.TKnudsen.ComplexDataObject.data.features.mixedData.MixedDataFeatureContainer;

/**
 * Adapter for MixedDataFeatureContainer.
 *
 * @since 2016
 */
public class MixedDataFeatureContainerAdapter implements DataContainerAdapter {

	private final MixedDataFeatureContainer container;

	public MixedDataFeatureContainerAdapter(MixedDataFeatureContainer container) {
		if (container == null)
			throw new IllegalArgumentException("Container cannot be null");
		this.container = container;
	}

	@Override
	public Map<Long, Object> getAttributeValues(String attribute) {
		return container.getFeatureValues(attribute);
	}

	@Override
	public boolean isNumeric(String attribute) {
		return container.isNumeric(attribute);
	}

	@Override
	public List<String> getAttributes() {
		return new ArrayList<>(container.getFeatureNames());
	}

	@Override
	public List<String> getAttributesSorted() {
		return new ArrayList<>(container.getFeatureNames());
	}
}