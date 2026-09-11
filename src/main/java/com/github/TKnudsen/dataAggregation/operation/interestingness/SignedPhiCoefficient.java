package com.github.TKnudsen.dataAggregation.operation.interestingness;

import java.util.HashMap;
import java.util.Map;

import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.data.contingencyTable.ContingencyTable;

/**
 * Signed (per-bin-pair) association measure based on a 2x2 collapse of the
 * contingency table.
 *
 * <p>
 * This implementation computes, for each bin pair (row bin i, column bin j),
 * the <b>phi coefficient</b> of the corresponding 2x2 table formed by
 * collapsing the original table into:
 * </p>
 *
 * <pre>
 *                Column j     Column != j
 * Row i             a            b
 * Row != i           c            d
 * </pre>
 *
 * <p>
 * The returned value is the signed phi coefficient:
 * </p>
 *
 * <pre>
 *  phi = (a*d - b*c) / sqrt((a+b)(c+d)(a+c)(b+d))
 * </pre>
 *
 * <p>
 * <b>What it measures:</b> For each cell (i,j), whether the co-occurrence of
 * the two bins is higher (+) or lower (-) than expected under independence, and
 * how strongly it deviates.
 * </p>
 *
 * <p>
 * <b>Range:</b> [-1, 1]
 * <ul>
 * <li>-1: Perfect negative association (mutual exclusion in the 2x2
 * collapse)</li>
 * <li>0: No association in the 2x2 collapse</li>
 * <li>+1: Perfect positive association (deterministic co-occurrence in the 2x2
 * collapse)</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Interpretation:</b>
 * <ul>
 * <li>Sign: direction (over-representation if positive, under-representation if
 * negative)</li>
 * <li>Magnitude |phi|: strength of association</li>
 * </ul>
 * Typical "rule of thumb" magnitude bands (context-dependent):
 * <ul>
 * <li>|phi| < 0.10: Negligible</li>
 * <li>0.10 <= |phi| < 0.20: Weak</li>
 * <li>0.20 <= |phi| < 0.40: Moderate</li>
 * <li>0.40 <= |phi| < 0.60: Relatively strong</li>
 * <li>0.60 <= |phi| < 0.80: Strong</li>
 * <li>0.80 <= |phi| <= 1.00: Very strong</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Best for:</b>
 * <ul>
 * <li>Heatmaps / matrix views where you need a per-bin-pair association
 * score</li>
 * <li>Diverging color scales centered at 0 (direction + strength)</li>
 * <li>Detecting over- and under-represented bin pairs relative to
 * marginals</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Limitations:</b>
 * <ul>
 * <li>This measure is a per-pair, 2x2-collapsed association score
 * (bin-vs-rest)</li>
 * <li>Can be noisy for very sparse counts (small marginals)</li>
 * <li>Depends on marginal distributions (as do all chi-square/phi based
 * measures)</li>
 * <li>Effect size interpretation is heuristic and domain-dependent</li>
 * </ul>
 * </p>
 *
 * @since 2026
 */
public class SignedPhiCoefficient implements InterestingnessMeasureCalculator {

	@Override
	public MeasureResult compute(ContingencyTable table) {
		int[][] ct = table.getContingencyTableDirect();
		int r = table.getAggregation1().size();
		int c = table.getAggregation2().size();

		Map<Bin, HashMap<Bin, Double>> phiMap = new HashMap<>();

		// Total n
		int n = 0;
		for (int i = 0; i < r; i++) {
			for (int j = 0; j < c; j++) {
				n += ct[i][j];
			}
		}

		// Edge case: empty table
		if (n == 0) {
			for (int i = 0; i < r; i++) {
				Bin b1 = table.getAggregation1().getBins().get(i);
				HashMap<Bin, Double> inner = new HashMap<>();
				for (int j = 0; j < c; j++) {
					Bin b2 = table.getAggregation2().getBins().get(j);
					inner.put(b2, 0.0);
				}
				phiMap.put(b1, inner);
			}
			return new MeasureResult(phiMap, 0.0, 0.0);
		}

		// Row totals and column totals (computed from ct to ensure consistency)
		int[] rowTotals = new int[r];
		int[] colTotals = new int[c];

		for (int i = 0; i < r; i++) {
			int sum = 0;
			for (int j = 0; j < c; j++)
				sum += ct[i][j];
			rowTotals[i] = sum;
		}

		for (int j = 0; j < c; j++) {
			int sum = 0;
			for (int i = 0; i < r; i++)
				sum += ct[i][j];
			colTotals[j] = sum;
		}

		// Track symmetric bounds for diverging color scales
		double maxAbsPhi = 0.0;

		// Per-pair signed association via 2x2 collapse: phi in [-1, +1]
		for (int i = 0; i < r; i++) {
			Bin b1 = table.getAggregation1().getBins().get(i);
			HashMap<Bin, Double> inner = new HashMap<>();
			phiMap.put(b1, inner);

			for (int j = 0; j < c; j++) {
				Bin b2 = table.getAggregation2().getBins().get(j);

				double a = ct[i][j];
				double b = rowTotals[i] - a;
				double cc = colTotals[j] - a;
				double d = n - a - b - cc;

				// Guard against inconsistent marginals
				if (b < 0 || cc < 0 || d < 0) {
					inner.put(b2, 0.0);
					continue;
				}

				// denom = (a+b)(c+d)(a+c)(b+d)
				double denom = (a + b) * (cc + d) * (a + cc) * (b + d);

				double phi = 0.0;
				if (denom > 0.0) {
					double num = a * d - b * cc; // signed numerator
					phi = num / Math.sqrt(denom); // [-1, +1] in theory

					// Numerical guards
					if (Double.isNaN(phi) || Double.isInfinite(phi))
						phi = 0.0;
					if (phi > 1.0)
						phi = 1.0;
					if (phi < -1.0)
						phi = -1.0;
				}

				inner.put(b2, phi);

				double absPhi = Math.abs(phi);
				if (absPhi > maxAbsPhi)
					maxAbsPhi = absPhi;
			}
		}

		// If everything is 0, keep symmetric bounds at 0
		if (maxAbsPhi == 0.0) {
			return new MeasureResult(phiMap, 0.0, 0.0);
		}

		// Symmetric min/max so 0 is centered in the colormap
		return new MeasureResult(phiMap, -maxAbsPhi, +maxAbsPhi);
	}

	@Override
	public MeasureDescription getDescription() {
		return new MeasureDescription("Signed Phi (2x2 collapsed association)",

				"Per-bin-pair signed association based on the phi coefficient of a 2x2 collapse (bin vs rest)",

				"[-1, 1]\n" + "  - -1: Perfect negative association (mutual exclusion in the 2x2 collapse)\n"
						+ "  -  0: No association\n"
						+ "  - +1: Perfect positive association (deterministic co-occurrence in the 2x2 collapse)",

				"  - Sign indicates direction:\n"
						+ "      - Positive: over-represented co-occurrence (more than expected)\n"
						+ "      - Negative: under-represented co-occurrence (less than expected)\n"
						+ "  - Magnitude |phi| indicates strength (rule-of-thumb, context-dependent):\n"
						+ "      - |phi| < 0.10: Negligible\n" + "      - 0.10-0.20: Weak\n"
						+ "      - 0.20-0.40: Moderate\n" + "      - 0.40-0.60: Relatively strong\n"
						+ "      - 0.60-0.80: Strong\n" + "      - 0.80-1.00: Very strong",

				"  - Heatmaps / matrix views requiring per-bin-pair direction and strength\n"
						+ "  - Diverging color scales centered at 0\n"
						+ "  - Detecting over- and under-represented bin pairs relative to marginals\n"
						+ "  - Exploratory analysis of contingency tables (cell-level association patterns)",

				"  - This is a per-pair score via 2x2 collapse\n"
						+ "  - Can be noisy for sparse bins (small marginals)\n"
						+ "  - Sensitive to marginal distributions\n"
						+ "  - Effect size bands are heuristic and domain-dependent",

				"Per cell (i,j), collapse the full table into a 2x2 table:\n" + "  a = O(i,j)\n"
						+ "  b = rowTotal(i) - a\n" + "  c = colTotal(j) - a\n" + "  d = n - a - b - c\n\n"
						+ "phi(i,j) = (a*d - b*c) / sqrt[(a+b)(c+d)(a+c)(b+d)]\n" + "Returned value is phi(i,j) in [-1, +1].",

				true, // hasDirection
				true // isNormalized
		);
	}
}