package com.github.TKnudsen.dataAggregation.operation.interestingness;

import java.util.HashMap;
import java.util.Map;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.data.contingencyTable.ContingencyTable;

/**
 * Mutual Information measure calculator.
 * 
 * <p>
 * <b>What it measures:</b> Amount of information one bin provides about another
 * (information-theoretic measure of dependency)
 * </p>
 * 
 * <p>
 * <b>Range:</b> [0, infinity)
 * <ul>
 * <li>0: Complete independence (no information shared)</li>
 * <li>Higher values: Stronger dependency</li>
 * <li>Theoretical maximum depends on bin probabilities and entropy</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Interpretation:</b>
 * <ul>
 * <li>MI = 0 -> Bins are completely independent</li>
 * <li>MI = 0.5 -> Moderate information sharing</li>
 * <li>MI = 2.0 -> Strong information sharing (exact meaning depends on
 * context)</li>
 * <li>Magnitude indicates how much uncertainty about one bin is reduced by
 * knowing the other</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Best for:</b>
 * <ul>
 * <li>Understanding information flow between bins</li>
 * <li>Feature selection in machine learning contexts</li>
 * <li>Detecting any type of dependency (linear or non-linear)</li>
 * <li>Comparing relative information content between bin pairs</li>
 * <li>Information-theoretic analysis</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Limitations:</b>
 * <ul>
 * <li>Not normalized (values not bounded to fixed range, hard to interpret
 * absolute scale)</li>
 * <li>Difficult to compare across different datasets or table sizes</li>
 * <li>Always positive (no indication of direction)</li>
 * <li>Biased toward bins with many elements</li>
 * <li>Requires sufficient sample size for reliable estimates</li>
 * </ul>
 * </p>
 * 
 * <p>
 * <b>Calculation:</b> MI(X;Y) = sum p(x,y) * log(p(x,y) / (p(x)*p(y))) <br>
 * Uses probability distributions derived from the contingency table to measure
 * reduction in uncertainty. Applies minimum threshold of 10/n to avoid
 * unreliable estimates from sparse bins.
 * </p>
 *
 * @since 2026
 */
public class MutualInformationMeasure implements InterestingnessMeasureCalculator {

	@Override
	public MeasureResult compute(ContingencyTable table) {
		int[][] contingencyTable = table.getContingencyTableDirect();
		int sharedElementCount = table.getSharedElementCountDirect();

		int size1 = table.getAggregation1().size();
		int size2 = table.getAggregation2().size();

		// Calculate probability matrix
		double[][] probabilityMatrix = new double[size1][size2];
		for (int i = 0; i < size1; i++) {
			for (int j = 0; j < size2; j++) {
				probabilityMatrix[i][j] = (double) contingencyTable[i][j] / sharedElementCount;

				if (probabilityMatrix[i][j] < 0 || probabilityMatrix[i][j] > 1.0) {
					throw new IllegalStateException(
							"Invalid probability: " + probabilityMatrix[i][j] + " at position [" + i + "," + j + "]");
				}
			}
		}

		// Calculate marginal probabilities
		double[] rowProbs = new double[size1];
		for (int i = 0; i < size1; i++)
			for (int j = 0; j < size2; j++)
				rowProbs[i] += probabilityMatrix[i][j];

		double[] colProbs = new double[size2];
		for (int j = 0; j < size2; j++)
			for (int i = 0; i < size1; i++)
				colProbs[j] += probabilityMatrix[i][j];

		// Calculate mutual information
		Map<Bin, HashMap<Bin, Double>> mi = new HashMap<>();
		double maxMI = Double.NEGATIVE_INFINITY;
		double minMI = Double.POSITIVE_INFINITY;

		double minSizeThreshold = (10.0 / sharedElementCount);

		for (int i = 0; i < size1; i++) {
			Bin b1 = table.getAggregation1().getBins().get(i);
			mi.put(b1, new HashMap<>());

			for (int j = 0; j < size2; j++) {
				Bin b2 = table.getAggregation2().getBins().get(j);

				double pij = probabilityMatrix[i][j];
				double pi = rowProbs[i];
				double pj = colProbs[j];

				double mutualInfo = 0.0;

				if (pij > minSizeThreshold && pi > 0 && pj > 0) {
					mutualInfo = pij * Math.log(pij / (pi * pj));

					if (Double.isNaN(mutualInfo) || Double.isInfinite(mutualInfo)) {
						mutualInfo = 0.0;
					}
				}

				mi.get(b1).put(b2, mutualInfo);
				maxMI = Math.max(maxMI, mutualInfo);
				minMI = Math.min(minMI, mutualInfo);
			}
		}

		return new MeasureResult(mi, minMI, maxMI);
	}

	@Override
	public MeasureDescription getDescription() {
		return new MeasureDescription("Mutual Information",

				"Amount of information one bin provides about another (information-theoretic dependency measure)",

				"[0, infinity)\n" + "  - 0: Complete independence (no information shared)\n"
						+ "  - Higher values: Stronger dependency\n" + "  - Theoretical maximum depends on bin entropy",

				"  - MI = 0    -> Bins are completely independent\n" + "  - MI = 0.1  -> Weak information sharing\n"
						+ "  - MI = 0.5  -> Moderate information sharing\n"
						+ "  - MI = 1.0  -> Strong information sharing\n"
						+ "  - MI = 2.0+ -> Very strong information sharing\n"
						+ "  - Magnitude indicates uncertainty reduction about one bin when knowing the other",

				"  - Understanding information flow between bins\n" + "  - Feature selection in machine learning\n"
						+ "  - Detecting any type of dependency (linear or non-linear)\n"
						+ "  - Comparing relative information content between bin pairs\n"
						+ "  - Information-theoretic analysis and entropy studies\n"
						+ "  - Network analysis and dependency mapping",

				"  - Not normalized (unbounded range makes absolute values hard to interpret)\n"
						+ "  - Difficult to compare across different datasets or table sizes\n"
						+ "  - Always positive (no indication of association direction)\n"
						+ "  - Biased toward bins with many elements\n"
						+ "  - Requires sufficient sample size for reliable estimates\n"
						+ "  - Sensitive to discretization choices",

				"MI(X;Y) = sum p(x,y) x log[p(x,y) / (p(x)xp(y))]\n"
						+ "Uses probability distributions from contingency table\n"
						+ "Applies minimum threshold of 10/n to avoid unreliable sparse estimates",

				false, // hasDirection
				false // isNormalized
		);
	}
}