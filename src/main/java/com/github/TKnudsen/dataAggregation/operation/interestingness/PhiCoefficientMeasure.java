package com.github.TKnudsen.dataAggregation.operation.interestingness;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.data.contingencyTable.ContingencyTable;

/**
 * Phi coefficient measure calculator. The Phi coefficient is a measure of
 * association for two binary variables. For larger contingency tables, it's
 * generalized but may exceed [-1, 1] range.
 * 
 * <p>
 * <b>What it measures:</b> Direction and strength of association between two
 * categorical variables (correlation-like measure for categorical data)
 * </p>
 * 
 * <p>
 * <b>Range:</b> [-1, 1]
 * <ul>
 * <li>-1: Perfect negative association (mutually exclusive)</li>
 * <li>0: No association (independence)</li>
 * <li>+1: Perfect positive association (perfect co-occurrence)</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Interpretation:</b>
 * <ul>
 * <li>phi = -0.8 -> Strong negative association (bins are mutually exclusive)</li>
 * <li>phi = -0.3 -> Weak negative association (bins tend not to co-occur)</li>
 * <li>phi = 0.0 -> No association (bins are independent)</li>
 * <li>phi = +0.3 -> Weak positive association (bins sometimes co-occur)</li>
 * <li>phi = +0.6 -> Moderate positive association (bins often co-occur)</li>
 * <li>phi = +0.9 -> Strong positive association (bins almost always co-occur)</li>
 * <li>Negative values: inverse relationship (presence of bin1 -> absence of
 * bin2)</li>
 * <li>Positive values: direct relationship (presence of bin1 -> presence of
 * bin2)</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Best for:</b>
 * <ul>
 * <li>Understanding direction of relationships between bins</li>
 * <li>2x2 contingency tables (most meaningful and interpretable)</li>
 * <li>When you need correlation-like interpretation for categorical data</li>
 * <li>Detecting inverse relationships or mutual exclusivity</li>
 * <li>Identifying co-occurring vs. anti-correlated bins</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Limitations:</b>
 * <ul>
 * <li>For tables larger than 2x2, can theoretically exceed [-1, 1] range
 * (clamped in implementation)</li>
 * <li>"Bin vs rest" approach for larger tables is a simplification</li>
 * <li>Less standardized interpretation for non-2x2 tables</li>
 * <li>Can be sensitive to marginal distributions in unbalanced tables</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Calculation:</b> For bin pairs, creates a 2x2 subtable (bin vs. rest):
 * <br>
 * phi = (ad - bc) / sqrt((a+b)(c+d)(a+c)(b+d)) <br>
 * where a = count in both bins, b = bin1 only, c = bin2 only, d = neither bin.
 * <br>
 * This "bin vs rest" approach allows computing directional association for each
 * bin pair in larger contingency tables.
 * </p>
 *
 * @since 2026
 */
public class PhiCoefficientMeasure implements InterestingnessMeasureCalculator {

	private static final Logger LOGGER = Logger.getLogger(PhiCoefficientMeasure.class.getName());

	@Override
	public MeasureResult compute(ContingencyTable table) {
		int[][] contingencyTable = table.getContingencyTableDirect();
		int sharedElementCount = table.getSharedElementCountDirect();
		Map<Bin, Integer> sharedObjectIDsOfEachBucket = table.getSharedObjectIDsOfEachBucketDirect();

		Map<Bin, HashMap<Bin, Double>> phiCoefficients = new HashMap<>();
		double maxValue = Double.NEGATIVE_INFINITY;
		double minValue = Double.POSITIVE_INFINITY;

		int size1 = table.getAggregation1().size();
		int size2 = table.getAggregation2().size();

		// Calculate row and column totals
		int[] rowTotals = new int[size1];
		int[] colTotals = new int[size2];

		for (int i = 0; i < size1; i++) {
			Bin b1 = table.getAggregation1().getBins().get(i);
			Integer total = sharedObjectIDsOfEachBucket.get(b1);
			rowTotals[i] = (total != null) ? total : 0;
		}

		for (int j = 0; j < size2; j++) {
			Bin b2 = table.getAggregation2().getBins().get(j);
			Integer total = sharedObjectIDsOfEachBucket.get(b2);
			colTotals[j] = (total != null) ? total : 0;
		}

		// Calculate Phi coefficient for each bin pair
		for (int i = 0; i < size1; i++) {
			Bin b1 = table.getAggregation1().getBins().get(i);
			phiCoefficients.put(b1, new HashMap<>());

			if (rowTotals[i] == 0) {
				// If row has no observations, phi is 0 for all columns
				for (int j = 0; j < size2; j++) {
					Bin b2 = table.getAggregation2().getBins().get(j);
					phiCoefficients.get(b1).put(b2, 0.0);
				}
				continue;
			}

			for (int j = 0; j < size2; j++) {
				Bin b2 = table.getAggregation2().getBins().get(j);

				if (colTotals[j] == 0) {
					phiCoefficients.get(b1).put(b2, 0.0);
					continue;
				}

				// Create a 2x2 table: bin1&bin2 vs. rest
				//
				// bin2 not-bin2
				// bin1 a b
				// not-bin1 c d

				double a = contingencyTable[i][j]; // bin1 AND bin2
				double b = rowTotals[i] - a; // bin1 AND NOT bin2
				double c = colTotals[j] - a; // NOT bin1 AND bin2
				double d = sharedElementCount - a - b - c; // NOT bin1 AND NOT bin2

				// Sanity check
				if (d < 0) {
					LOGGER.warning("Invalid 2x2 table for bins " + b1 + " and " + b2);
					phiCoefficients.get(b1).put(b2, 0.0);
					continue;
				}

				// Calculate phi coefficient: phi = (ad - bc) / sqrt((a+b)(c+d)(a+c)(b+d))
				double numerator = (a * d) - (b * c);

				double denominator = Math.sqrt((a + b) * (c + d) * (a + c) * (b + d));

				double phi;
				if (denominator == 0.0 || Double.isNaN(denominator)) {
					// Perfect independence or invalid case
					phi = 0.0;
				} else {
					phi = numerator / denominator;
				}

				// Clamp to [-1, 1] range (due to numerical precision issues)
				phi = Math.max(-1.0, Math.min(1.0, phi));

				if (Double.isNaN(phi) || Double.isInfinite(phi)) {
					LOGGER.warning("Invalid phi value for bins " + b1 + " and " + b2);
					phiCoefficients.get(b1).put(b2, 0.0);
					continue;
				}

				phiCoefficients.get(b1).put(b2, phi);
				maxValue = Math.max(maxValue, phi);
				minValue = Math.min(minValue, phi);
			}
		}

		// If no valid values were computed
		if (minValue == Double.POSITIVE_INFINITY) {
			minValue = 0.0;
			maxValue = 0.0;
		}

		return new MeasureResult(phiCoefficients, minValue, maxValue);
	}

	@Override
	public MeasureDescription getDescription() {
		return new MeasureDescription("Phi Coefficient",

				"Direction and strength of association for categorical variables (correlation-like measure)",

				"[-1, 1]\n" + "  - -1: Perfect negative association (mutually exclusive)\n"
						+ "  -  0: No association (independence)\n"
						+ "  - +1: Perfect positive association (perfect co-occurrence)",

				"  - phi = -0.9  -> Strong negative association (bins almost never co-occur)\n"
						+ "  - phi = -0.5  -> Moderate negative association\n"
						+ "  - phi = -0.2  -> Weak negative association\n"
						+ "  - phi =  0.0  -> No association (independence)\n"
						+ "  - phi = +0.2  -> Weak positive association\n"
						+ "  - phi = +0.5  -> Moderate positive association\n"
						+ "  - phi = +0.9  -> Strong positive association (bins almost always co-occur)\n"
						+ "  - Negative: inverse relationship (bin1 present -> bin2 absent)\n"
						+ "  - Positive: direct relationship (bin1 present -> bin2 present)",

				"  - Understanding direction of relationships between bins\n"
						+ "  - 2x2 contingency tables (most meaningful)\n"
						+ "  - When you need correlation-like interpretation for categorical data\n"
						+ "  - Detecting inverse relationships or mutual exclusivity\n"
						+ "  - Identifying co-occurring vs. anti-correlated bins\n"
						+ "  - Pattern discovery in categorical data",

				"  - For tables >2x2, can theoretically exceed [-1,1] (clamped in implementation)\n"
						+ "  - 'Bin vs rest' approach for larger tables is a simplification\n"
						+ "  - Less standardized interpretation for non-2x2 tables\n"
						+ "  - Can be sensitive to marginal distributions in unbalanced tables\n"
						+ "  - Assumes nominal categorical data",

				"For bin pairs, creates 2x2 subtable (bin vs. rest):\n" + "phi = (ad - bc) / sqrt[(a+b)(c+d)(a+c)(b+d)]\n"
						+ "where a=both bins, b=bin1 only, c=bin2 only, d=neither\n"
						+ "Allows directional association for each pair in larger tables",

				true, // hasDirection
				true // isNormalized
		);
	}
}