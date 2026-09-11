package com.github.TKnudsen.dataAggregation.operation.aggregation.executors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.AggregationCalculationExecutorService.DataContainerAdapter;
import com.github.TKnudsen.ComplexDataObject.data.DataContainers;
import com.github.TKnudsen.ComplexDataObject.data.complexDataObject.ComplexDataContainer;

/**
 * Adapter for ComplexDataContainer.
 *
 * @since 2016
 */
public class ComplexDataContainerAdapter implements DataContainerAdapter {

	private final ComplexDataContainer container;

	public ComplexDataContainerAdapter(ComplexDataContainer container) {
		if (container == null)
			throw new IllegalArgumentException("Container cannot be null");
		this.container = container;
	}

	@Override
	public Map<Long, Object> getAttributeValues(String attribute) {
		return DataContainers.getAttributeValues(container, attribute);
	}

	@Override
	public boolean isNumeric(String attribute) {
		return container.isNumeric(attribute);
	}

	@Override
	public List<String> getAttributes() {
		return new ArrayList<String>(container.getAttributes());
	}

	@Override
	public List<String> getAttributesSorted() {
		return new ArrayList<String>(container.getAttributesSorted());
	}
}