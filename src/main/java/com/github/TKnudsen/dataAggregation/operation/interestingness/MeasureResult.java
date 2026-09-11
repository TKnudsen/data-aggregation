package com.github.TKnudsen.dataAggregation.operation.interestingness;

import java.util.HashMap;
import java.util.Map;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;

/**
 * Result of a measure computation.
 *
 * @since 2026
 */
public class MeasureResult {
	private final Map<Bin, HashMap<Bin, Double>> values;
	private final double minValue;
	private final double maxValue;

	public MeasureResult() {
		this.values = new HashMap<Bin, HashMap<Bin, Double>>();
		this.minValue = Double.POSITIVE_INFINITY;
		this.maxValue = Double.NEGATIVE_INFINITY;
	}

	public MeasureResult(Map<Bin, HashMap<Bin, Double>> values, double minValue, double maxValue) {
		this.values = values;
		this.minValue = minValue;
		this.maxValue = maxValue;
	}

	public Map<Bin, HashMap<Bin, Double>> getValues() {
		return values;
	}

	public double getMinValue() {
		return minValue;
	}

	public double getMaxValue() {
		return maxValue;
	}

}
