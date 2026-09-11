package com.github.TKnudsen.dataAggregation.operation.interestingness;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.data.contingencyTable.ContingencyTable;
import com.github.TKnudsen.statistics.ChiSquare;

/**
 * Chi-Square statistical significance measure calculator.
 * 
 * <p>
 * <b>What it measures:</b> Statistical significance of deviation from
 * independence
 * </p>
 * 
 * <p>
 * <b>Range:</b> Unbounded (can be positive or negative)
 * <ul>
 * <li>Negative values indicate under-representation (observed &lt;
 * expected)</li>
 * <li>Positive values indicate over-representation (observed &gt;
 * expected)</li>
 * <li>Values closer to 0: statistically significant deviation</li>
 * <li>Values closer to +/-1: not statistically significant</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Interpretation:</b>
 * <ul>
 * <li>p-value = 0.001 -> Very significant over-representation</li>
 * <li>p-value = -0.001 -> Very significant under-representation</li>
 * <li>p-value = 0.5 -> Not statistically significant</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Best for:</b>
 * <ul>
 * <li>Hypothesis testing (is there a statistically significant
 * relationship?)</li>
 * <li>Identifying bins that deviate significantly from what you'd expect by
 * chance</li>
 * <li>When you care about statistical confidence more than effect size</li>
 * <li>Determining direction of deviation (over vs under-representation)</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Limitations:</b>
 * <ul>
 * <li>Heavily influenced by sample size (large samples -> small p-values even
 * for weak associations)</li>
 * <li>Doesn't directly measure strength of association</li>
 * <li>Not comparable across different table sizes</li>
 * <li>High sensitivity to sample size can be misleading</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Calculation:</b> Uses chi-square test statistic with 1 degree of freedom,
 * comparing observed vs expected frequencies under independence assumption.
 * Sign indicates direction of association relative to expected values.
 * </p>
 *
 * @since 2026
 */
public class ChiSquareMeasure implements InterestingnessMeasureCalculator {

	private static final Logger LOGGER = Logger.getLogger(ChiSquareMeasure.class.getName());

	@Override
	public MeasureResult compute(ContingencyTable table) {
		int[][] contingencyTable = table.getContingencyTableDirect();
		int sharedElementCount = table.getSharedElementCountDirect();
		Map<Bin, Integer> sharedObjectIDsOfEachBucket = table.getSharedObjectIDsOfEachBucketDirect();

		Map<Bin, HashMap<Bin, Double>> chi = new HashMap<>();
		double maxPValue = Double.NEGATIVE_INFINITY;
		double minPValue = Double.POSITIVE_INFINITY;

		int size1 = table.getAggregation1().size();
		int size2 = table.getAggregation2().size();

		for (int i = 0; i < size1; i++) {
			Bin b1 = table.getAggregation1().getBins().get(i);
			chi.put(b1, new HashMap<>());

			Integer bin1Total = sharedObjectIDsOfEachBucket.get(b1);
			if (bin1Total == null || bin1Total == 0) {
				continue;
			}

			for (int j = 0; j < size2; j++) {
				Bin b2 = table.getAggregation2().getBins().get(j);
				Integer bin2Total = sharedObjectIDsOfEachBucket.get(b2);

				if (bin2Total == null || bin2Total == 0) {
					chi.get(b1).put(b2, 0.0);
					continue;
				}

				double observedCount = contingencyTable[i][j];
				double expectedCount = ((double) bin1Total / sharedElementCount) * bin2Total;

				if (expectedCount < Math.max(5, sharedElementCount * 0.001)) {
					chi.get(b1).put(b2, 0.0);
					continue;
				}

				double observedOther = 0;
				for (int k = 0; k < size2; k++) {
					if (k != j) {
						observedOther += contingencyTable[i][k];
					}
				}

				double expectedOther = ((double) bin1Total / sharedElementCount) * (sharedElementCount - bin2Total);

				double chiSquareStat = 0;

				if (expectedCount > 0) {
					double term1 = (observedCount - expectedCount);
					chiSquareStat += (term1 * term1) / expectedCount;
				}

				if (expectedOther > 0) {
					double term2 = (observedOther - expectedOther);
					chiSquareStat += (term2 * term2) / expectedOther;
				}

				if (Double.isNaN(chiSquareStat) || Double.isInfinite(chiSquareStat)) {
					LOGGER.warning("Invalid chi-square value for bins " + b1 + " and " + b2);
					chi.get(b1).put(b2, 0.0);
					continue;
				}

				double pValue = ChiSquare.chiSquareDistribution(1, chiSquareStat);

				if (observedCount < expectedCount)
					pValue = (pValue == 0.0) ? 0.0 : -pValue;

				chi.get(b1).put(b2, pValue);
				maxPValue = Math.max(maxPValue, pValue);
				minPValue = Math.min(minPValue, pValue);
			}
		}

		return new MeasureResult(chi, minPValue, maxPValue);
	}

	@Override
	public MeasureDescription getDescription() {
		return new MeasureDescription("Chi-Square (p-value based)",

				"Statistical significance of deviation from independence",

				"Unbounded (positive and negative)\n"
						+ "  - Negative values: under-representation (observed < expected)\n"
						+ "  - Positive values: over-representation (observed > expected)\n"
						+ "  - Values closer to 0: statistically significant\n"
						+ "  - Values closer to +/-1: not statistically significant",

				"  - p = 0.001  -> Very significant over-representation\n"
						+ "  - p = -0.001 -> Very significant under-representation\n"
						+ "  - p = 0.5    -> Not statistically significant\n"
						+ "  - p < 0.05   -> Generally considered significant (positive association)\n"
						+ "  - p < -0.05  -> Generally considered significant (negative association)",

				"  - Hypothesis testing (is there a statistically significant relationship?)\n"
						+ "  - Identifying bins that deviate significantly from chance\n"
						+ "  - When statistical confidence is more important than effect size\n"
						+ "  - Determining direction of deviation (over vs under-representation)\n"
						+ "  - Quality control and anomaly detection",

				"  - Heavily influenced by sample size (large n -> small p-values even for weak associations)\n"
						+ "  - Doesn't directly measure strength of association\n"
						+ "  - Not comparable across different table sizes\n"
						+ "  - Can be misleading with very large datasets\n"
						+ "  - Requires minimum expected cell counts (typically >=5)",

				"chi^2 = sum[(observed - expected)^2 / expected]\n" + "p-value from chi-square distribution with df=1\n"
						+ "Sign indicates direction relative to expected values",

				true, // hasDirection
				false // isNormalized
		);
	}
}