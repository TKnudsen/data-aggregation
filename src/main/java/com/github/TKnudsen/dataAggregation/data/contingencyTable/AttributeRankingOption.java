package com.github.TKnudsen.dataAggregation.data.contingencyTable;

/**
 * How to aggregate a set of per-bin-pair significance values into a single
 * ranking score for an attribute pair.
 *
 * @since 2016
 */
public enum AttributeRankingOption {
	MinumumSignificance, MedianSignificance, AverageSignificance, MaxSignificance, SumOfSignificances
}
