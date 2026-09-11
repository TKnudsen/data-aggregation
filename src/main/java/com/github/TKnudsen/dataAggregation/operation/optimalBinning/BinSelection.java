package com.github.TKnudsen.dataAggregation.operation.optimalBinning;

import com.github.TKnudsen.ComplexDataObject.model.tools.StatisticsSupport;

/**
 * <p>
 * Optimal histogram bin-width selection.
 * </p>
 *
 * <p>
 * Port of Hideaki Shimazaki's optimal histogram bin-width selection algorithm
 * (2006), Department of Physics, Kyoto University, Kyoto 606-8502, Japan.
 * </p>
 *
 * @since 2012
 */
public class BinSelection {

	private double data_[];
	// static double K[]; //The number of events within a bins
	private double K_OPT[];
	private double BINS[];
	private double BIN_MAX = 0;
	private double BIN_MIN = 0;
	// private int BINS_LEN;
	private double BIN_OPT;

	private int NS[];
	private int N_MAX = 1;
	private int N_MIN = 1;
	private int N_OPT;

	private double dataMin;
	private double dataMax;
	// private double N;
	private double C[];

	public BinSelection(StatisticsSupport dataStatistics, int maxBins) {

		this.dataMin = dataStatistics.getMin();
		this.dataMax = dataStatistics.getMax();

		data_ = dataStatistics.getValues();

		NS = new int[maxBins - 1];
		N_MIN = Integer.MAX_VALUE;
		N_MAX = Integer.MIN_VALUE;
		for (int i = 0; i < maxBins - 1; i++) {
			NS[i] = maxBins - i;
			// NS[i] = N;
			N_MAX = Math.max(N_MAX, NS[i]);
			N_MIN = Math.min(N_MIN, NS[i]);
		}

		BINS = new double[maxBins - 1];
		BIN_MAX = Double.NEGATIVE_INFINITY;
		BIN_MIN = Double.POSITIVE_INFINITY;
		for (int i = 0; i < BINS.length; i++) {
			BINS[i] = (dataMax - dataMin) / NS[i];
			// BINS[i] = (double) bins;
			BIN_MAX = Math.max(BIN_MAX, BINS[i]);
			BIN_MIN = Math.min(BIN_MIN, BINS[i]);
		}

		C = new double[maxBins - 1];

		CostFunctionArray();

		int optiBinSizeIndex = getOptimalBinSize();
		BIN_OPT = BINS[optiBinSizeIndex];
		N_OPT = NS[optiBinSizeIndex];

		// K_OPT = new double[N_OPT];
		// double[] EDGE_OPT = new double[N_OPT + 1];
		// for (int i = 0; i < K_OPT.length; i++) {
		// K_OPT[i] = OutputK(i, N_OPT);
		// EDGE_OPT[i] = dataMin + BIN_OPT * i;
		// }
		//
		// EDGE_OPT[K_OPT.length] = dataMax;
	}

	private double OutputK(int i, int N) {
		K_OPT = eventCount(N, 0);
		return K_OPT[i];
	}

	public int getOptimalBinSize() { // Output an optimal bins size
		if (C.length == 0)
			return 0;
		double C_MIN = C[0];
		int IDX = 0;
		for (int i = 0; i < BINS.length; i++) {
			C_MIN = Math.min(C_MIN, C[i]);
			if (C_MIN == C[i]) {
				IDX = i;
			}
		}
		return IDX;
	}

	public double[] getOptimalIntervals() {

		K_OPT = new double[N_OPT];
		double[] optimalIntervalBorders = new double[N_OPT + 1];

		for (int i = 0; i < K_OPT.length; i++) {
			K_OPT[i] = OutputK(i, N_OPT);
			optimalIntervalBorders[i] = dataMin + BIN_OPT * i;
		}

		optimalIntervalBorders[K_OPT.length] = dataMax;

		return optimalIntervalBorders;
	}

	private void CostFunctionArray() {
		C = new double[BINS.length];
		for (int i = 0; i < BINS.length; i++) {
			double C_BUF = 0;
			double d = 10;
			for (int j = 0; j < d + 1; j++) {
				C_BUF = C_BUF + costFunction(NS[i], BINS[i] * (double) (j - d / 2) / d);
			}
			C[i] = C_BUF / (d + 1); // CostFunction(BINS[i]);
		}
	}

	private double costFunction(int N, double INIT) {
		double K[] = eventCount(N, INIT);

		double Kbar = 0;
		for (int i = 0; i < N; i++) {
			Kbar = Kbar + K[i] / (double) N;
		}

		double V = 0;
		for (int i = 0; i < N; i++) {
			V = V + Math.pow(K[i] - Kbar, 2) / (double) N;
		}

		double BIN = (dataMax - dataMin) / (double) N;

		double c;
		c = (2 * Kbar - V) / Math.pow(BIN, 2);
		return c;
	}

	private double[] eventCount(int N, double INIT) {
		double BIN = (dataMax - dataMin) / (double) N;

		double K[] = new double[N];
		for (int i = 0; i < N; i++) {
			K[i] = 0;
			for (int j = 0; j < data_.length; j++) {
				if (data_[j] >= INIT + dataMin + BIN * i && data_[j] < INIT + dataMin + BIN * (i + 1)) {
					K[i] = K[i] + 1;
				}
			}
		}
		K[N - 1] = K[N - 1] + 1;
		return K;
	}
}