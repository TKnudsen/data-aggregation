package com.github.TKnudsen.dataAggregation.operation.interestingness;

import com.github.TKnudsen.dataAggregation.data.contingencyTable.ContingencyTable;

/**
 * Interface for plugg-able interestingness measure calculators.
 *
 * @since 2026
 */
public interface InterestingnessMeasureCalculator {
	/**
	 * Compute the measure for all bin pairs.
	 * 
	 * @param table The contingency table with pre-computed base statistics
	 * @return Result containing values and statistics
	 */
	MeasureResult compute(ContingencyTable table);

	MeasureDescription getDescription();
}
