package com.github.TKnudsen.dataAggregation.operation.aggregation.characterization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.aggregation.Aggregation;
import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.AggregationCalculationExecutorService;
import com.github.TKnudsen.dataAggregation.operation.aggregation.executors.ComplexDataContainerAdapter;
import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;
import com.github.TKnudsen.ComplexDataObject.data.complexDataObject.ComplexDataContainer;
import com.github.TKnudsen.ComplexDataObject.data.complexDataObject.ComplexDataObject;
import com.github.TKnudsen.ComplexDataObject.model.io.parsers.objects.IObjectParser;

/**
 * Computes {@link AggregationCharacterization}s for the attributes of a
 * {@link ComplexDataContainer}, in parallel across the available processors.
 *
 * @since 2026
 */
public class AggregationCharacterizationBackend {

	private static final Logger LOGGER = Logger.getLogger(AggregationCharacterizationBackend.class.getName());

	private static final int THREAD_COUNT = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);

	// fields that remain static
	private final Collection<Object> values;
	private final Map<String, IObjectParser<?>> parsers;
	private ComplexDataContainer dataContainer;

	// dynamic field
	private AggregationCharacterization aggregationCharacterization;
	private Map<String, AggregationCharacterization> aggregationCharacterizations;

	// internal fields
	private AggregationCalculationExecutorService aggregationCalculationExecutorService;

	// object shall never die
	private List<Aggregation> aggregations = new ArrayList<>();

	public AggregationCharacterizationBackend(Collection<Object> values, Collection<IObjectParser<?>> parsers,
			AggregationCharacterization aggregationInitialization) {
		this.values = values;

		this.parsers = new LinkedHashMap<>();
		int i = 0;
		for (IObjectParser<?> p : parsers)
			this.parsers.put(String.valueOf(i++), p);

		Objects.requireNonNull(aggregationInitialization);

		addAggregationCharacterization(aggregationInitialization);
	}

	public ComplexDataContainer getDataContainer() {
		if (dataContainer == null) {
			List<ComplexDataObject> cdos = new ArrayList<>();

			for (Object o : values) {
				ComplexDataObject cdo = new ComplexDataObject();
				for (String attName : parsers.keySet()) {
					IObjectParser<?> p = parsers.get(attName);
					cdo.add(attName, p.apply(o));
				}

				cdos.add(cdo);
			}

			dataContainer = new ComplexDataContainer(cdos);

			// name, description missing. too bad...
			for (String attribute : dataContainer.getAttributes())
				if (!aggregationCharacterizations.containsKey(attribute))
					aggregationCharacterizations.put(attribute,
							createInternalAggregationInitialization(aggregationCharacterization, attribute));
		}
		return dataContainer;
	}

	public List<Aggregation> getAggregations() {
		if (aggregations.isEmpty()) {
			AggregationCalculationExecutorService executor = new AggregationCalculationExecutorService(
					new ComplexDataContainerAdapter(getDataContainer()), aggregationCharacterizations, THREAD_COUNT);
			try {
				aggregations = executor.start();
			} catch (InterruptedException e) {
				LOGGER.log(Level.SEVERE, "Interrupted while computing aggregation characterizations", e);
				Thread.currentThread().interrupt();
			}
		}

		return aggregations;
	}

	public AggregationCharacterization getAggregationInitialization() {
		return aggregationCharacterization;
	}

	public void addAggregationCharacterization(AggregationCharacterization aggregationInitialization) {
		this.aggregationCharacterization = aggregationInitialization;

		this.aggregationCharacterizations = new LinkedHashMap<>();
		for (String s : parsers.keySet())
			aggregationCharacterizations.put(s, createInternalAggregationInitialization(aggregationInitialization, s));

		// name, description missing. too bad...
		if (dataContainer != null)
			for (String attribute : dataContainer.getAttributeNames())
				if (!aggregationCharacterizations.containsKey(attribute))
					aggregationCharacterizations.put(attribute,
							createInternalAggregationInitialization(aggregationInitialization, attribute));

		this.aggregationCalculationExecutorService = null;
		this.aggregations.clear();
	}

	public Aggregation getAggregation(IObjectParser<?> parser) {
		int i = 0;
		for (String s : parsers.keySet()) {
			if (parsers.get(s).equals(parser))
				for (Aggregation a : getAggregations())
					if (a.getName().equals(s))
						return a;
			i++;
		}

		return null;
	}

	private AggregationCharacterization createInternalAggregationInitialization(AggregationCharacterization outer,
			String name) {
		return new AggregationCharacterization(name, outer.getAggregationLevel(), outer.getPartitioningStrategyEnum(),
				outer.getOutlierPercentage());
	}
}
