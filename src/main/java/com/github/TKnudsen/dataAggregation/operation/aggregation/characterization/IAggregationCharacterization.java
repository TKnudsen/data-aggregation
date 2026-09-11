package com.github.TKnudsen.dataAggregation.operation.aggregation.characterization;

import java.util.Collection;

import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;

/**
 * Something that can provide an {@link AggregationCharacterization}, either
 * as a fixed configuration or derived from a set of values.
 *
 * @since 2026
 */
public interface IAggregationCharacterization {

	public AggregationCharacterization getAggregationCharacterization();

	public AggregationCharacterization getAggregationCharacterization(Collection<Object> values, String attributeName);
}
