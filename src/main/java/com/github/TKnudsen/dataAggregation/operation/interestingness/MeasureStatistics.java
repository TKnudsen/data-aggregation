package com.github.TKnudsen.dataAggregation.operation.interestingness;

/**
 * Statistics about a measure.
 *
 * @since 2026
 */
public class MeasureStatistics {
	public final double minValue;
	public final double maxValue;

	public MeasureStatistics(double minValue, double maxValue) {
		this.minValue = minValue;
		this.maxValue = maxValue;
	}
}
