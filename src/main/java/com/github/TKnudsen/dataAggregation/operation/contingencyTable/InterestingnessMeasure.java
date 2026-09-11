package com.github.TKnudsen.dataAggregation.operation.contingencyTable;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The interestingness measures available for ranking contingency table
 * entries.
 *
 * @since 2026
 */
public enum InterestingnessMeasure {
	CHI_SQUARE_INVERSE_PROBABILITY, MUTUAL_INFORMATION, SIGNED_PHI_COEFFICIENT, PHI_COEFFICIENT;

	public static List<InterestingnessMeasure> interestingnessMeasures() {
		return Arrays.asList(InterestingnessMeasure.values());
	}

	public static List<String> interestingnessMeasureNames() {
		return Arrays.stream(InterestingnessMeasure.values()).map(Enum::name).collect(Collectors.toList());
	}

}
