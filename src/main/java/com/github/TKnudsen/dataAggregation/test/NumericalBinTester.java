package com.github.TKnudsen.dataAggregation.test;

import java.util.HashMap;
import java.util.Map;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.data.bins.NumbersBin;

/**
 * <p>
 * Demo/tester for {@link NumbersBin}.
 * </p>
 *
 * @since 2015
 */

public class NumericalBinTester {

	public static void main(String[] args) {

		Map<Long, Number> elements = new HashMap<>();

		elements.put(1L, 2);
		elements.put(2L, 1.1);
		elements.put(3L, Double.NaN);
		elements.put(4L, 6.6666666666666666666);

		Bin bin = new NumbersBin(elements, "Some name");

		System.out.println(bin);

	}
}
