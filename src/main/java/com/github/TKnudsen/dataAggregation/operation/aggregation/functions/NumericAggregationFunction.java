package com.github.TKnudsen.dataAggregation.operation.aggregation.functions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.control.events.BinChangeEvent;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.data.bins.Bins;
import com.github.TKnudsen.dataAggregation.data.bins.NumbersBin;
import com.github.TKnudsen.dataAggregation.operation.optimalBinning.BinSelection;
import com.github.TKnudsen.ComplexDataObject.model.tools.StatisticsSupport;
import com.github.TKnudsen.ComplexDataObject.model.transformations.normalization.LinearNormalizationFunction;
import com.github.TKnudsen.ComplexDataObject.model.transformations.normalization.NormalizationFunction;

/**
 * <p>
 * Memory-optimized aggregation function for numeric data with proper bin
 * ordering.
 * </p>
 *
 * @since 2012
 */
public class NumericAggregationFunction extends AggregationFunction<Number> {

	private static final Logger LOGGER = Logger.getLogger(NumericAggregationFunction.class.getName());

	private double outlierPercentage = 0.00;
	private AggregationCharacterization aggregationInitialization;

	public NumericAggregationFunction(Collection<Number> values, AggregationCharacterization aggregationInitialization) {
		super(values, aggregationInitialization.getAggregationLevel(), aggregationInitialization.getAggregationName());
		this.aggregationInitialization = aggregationInitialization;
		this.outlierPercentage = aggregationInitialization.getOutlierPercentage();

		validateElements();
	}

	public NumericAggregationFunction(Map<Long, Number> elements, AggregationCharacterization aggregationInitialization) {
		super(elements, aggregationInitialization.getAggregationLevel(),
				aggregationInitialization.getAggregationName());
		this.aggregationInitialization = aggregationInitialization;
		this.outlierPercentage = aggregationInitialization.getOutlierPercentage();

		validateElements();
	}

	private void validateElements() {
		for (Map.Entry<Long, Number> entry : elements.entrySet()) {
			Number number = entry.getValue();
			if (number == null) {
				throw new IllegalArgumentException(
						"NumericAggregationFunction: null values are not allowed (ID: " + entry.getKey() + ")");
			}
			if (Double.isInfinite(number.doubleValue())) {
				throw new IllegalArgumentException(
						"NumericAggregationFunction: infinite values are not allowed (ID: " + entry.getKey() + ")");
			}
		}
	}

	@Override
	public void calculateAggregation() {
		long startTime = System.currentTimeMillis();

		int aggregationsRemaining = aggregationLevel;

		Map<Long, Number> binOfRemainings = null;
		if (containsNaNs()) {
			aggregationsRemaining = Math.max(0, aggregationsRemaining - 1);
			binOfRemainings = new HashMap<>();
		}

		bins = new ArrayList<>();

		int validCount = 0;
		for (Map.Entry<Long, Number> entry : elements.entrySet()) {
			Number value = entry.getValue();
			if (value != null && !Double.isNaN(value.doubleValue())) {
				validCount++;
			}
		}

		double[] values = new double[validCount];
		Long[] keys = new Long[validCount];
		int idx = 0;

		for (Map.Entry<Long, Number> entry : elements.entrySet()) {
			Number value = entry.getValue();
			if (value != null && !Double.isNaN(value.doubleValue())) {
				values[idx] = value.doubleValue();
				keys[idx] = entry.getKey();
				idx++;
			} else if (binOfRemainings != null) {
				binOfRemainings.put(entry.getKey(), value);
			}
		}

		if (values.length == 0) {
			NumbersBin bin = new NumbersBin(elements, "NaN");
			bins.add(bin);
			LOGGER.info("NumericAggregationFunction: All values are NaN, created single NaN bin");
			return;
		}

		quickSortWithKeys(values, keys, 0, values.length - 1);

		StatisticsSupport stats = new StatisticsSupport(convertToList(values));
		double min = stats.getMin();
		double max = stats.getMax();

		if (outlierPercentage > 0.00) {
			min = stats.getPercentile((int) (outlierPercentage * 100));
			max = stats.getPercentile((int) (100 - outlierPercentage * 100));

			for (int i = 0; i < values.length; i++) {
				values[i] = Math.min(max, Math.max(values[i], min));
			}
			stats = new StatisticsSupport(convertToList(values));
		}

		NormalizationFunction normalizationFunction = new LinearNormalizationFunction(min, max);
		normalizationFunction.setLimitToBounds(true);

		aggregationsRemaining = Math.min(aggregationsRemaining, stats.getCountUniqueObservations());

		Map<Integer, Map<Long, Number>> pointsHash = new HashMap<>();
		for (int i = 0; i < aggregationsRemaining; i++) {
			pointsHash.put(i, new HashMap<>());
		}

		switch (aggregationInitialization.getPartitioningStrategyEnum()) {
		case DOMAIN_UNIFORM:
			assignToDomainUniformBins(values, keys, normalizationFunction, aggregationsRemaining, pointsHash);
			break;

		case FREQUENCY_UNIFORM:
			assignToFrequencyUniformBins(values, keys, aggregationsRemaining, pointsHash);
			break;

		case GOODNESS_OF_FIT:
			assignToGoodnessOfFitBins(values, keys, stats, aggregationsRemaining, pointsHash);
			break;

		default:
			throw new IllegalStateException(
					"Unknown partitioning strategy: " + aggregationInitialization.getPartitioningStrategyEnum());
		}

		List<NumbersBin> createdBins = new ArrayList<>();
		for (int i = 0; i < aggregationsRemaining; i++) {
			Map<Long, Number> binElements = pointsHash.get(i);
			if (!binElements.isEmpty()) {
				NumbersBin bin = new NumbersBin(binElements, name);
				createdBins.add(bin);
			}
		}

		List<NumbersBin> sortedBins = Bins.sortByMinValue(createdBins, false);
		bins.addAll(sortedBins);

		if (binOfRemainings != null && !binOfRemainings.isEmpty()) {
			NumbersBin nanBin = new NumbersBin(binOfRemainings, name);
			bins.add(nanBin);
		}

		if (isPrintOut()) {
			long elapsed = System.currentTimeMillis() - startTime;
			LOGGER.info("NumericAggregationFunction.calculateAggregation took " + elapsed + "ms for " + elements.size()
					+ " elements, created " + bins.size() + " bins");
		}
	}

	private void assignToDomainUniformBins(double[] values, Long[] keys, NormalizationFunction normalizationFunction,
			int aggregationsRemaining, Map<Integer, Map<Long, Number>> pointsHash) {

		for (int i = 0; i < values.length; i++) {
			if (Double.isNaN(values[i]))
				continue;

			double relative = normalizationFunction.apply(values[i]).doubleValue();
			relative *= aggregationsRemaining;

			int binIndex;
			if (relative == aggregationsRemaining) {
				binIndex = aggregationsRemaining - 1;
			} else {
				binIndex = (int) Math.floor(relative);
			}

			binIndex = Math.max(0, Math.min(binIndex, aggregationsRemaining - 1));
			pointsHash.get(binIndex).put(keys[i], values[i]);
		}
	}

	private void assignToFrequencyUniformBins(double[] values, Long[] keys, int aggregationsRemaining,
			Map<Integer, Map<Long, Number>> pointsHash) {

		int[] binAssignments = splitFrequencyBasedIterative(values, aggregationsRemaining);

		for (int i = 0; i < values.length; i++) {
			pointsHash.get(binAssignments[i]).put(keys[i], values[i]);
		}
	}

	private void assignToGoodnessOfFitBins(double[] values, Long[] keys, StatisticsSupport stats,
			int aggregationsRemaining, Map<Integer, Map<Long, Number>> pointsHash) {

		double[] optimalIntervals;
		if (aggregationsRemaining > 1) {
			BinSelection bs = new BinSelection(stats, aggregationsRemaining);
			optimalIntervals = bs.getOptimalIntervals();
		} else {
			optimalIntervals = new double[] { stats.getMin(), stats.getMax() };
		}

		for (int i = 0; i < values.length; i++) {
			double d = values[i];
			if (Double.isNaN(d))
				continue;

			for (int j = 0; j < optimalIntervals.length - 1; j++) {
				if (d >= optimalIntervals[j] && d < optimalIntervals[j + 1]) {
					pointsHash.get(j).put(keys[i], d);
					break;
				} else if (j == aggregationsRemaining - 1 && d == optimalIntervals[j + 1]) {
					pointsHash.get(j).put(keys[i], d);
					break;
				}
			}
		}
	}

	private int[] splitFrequencyBasedIterative(double[] sortedValues, int targetBins) {
		int[] binAssignments = new int[sortedValues.length];

		if (targetBins <= 1) {
			return binAssignments;
		}

		int[] binStarts = new int[targetBins];
		int[] binEnds = new int[targetBins];
		binStarts[0] = 0;
		binEnds[0] = sortedValues.length - 1;
		int currentBinCount = 1;

		while (currentBinCount < targetBins) {
			int largestBinIdx = -1;
			int largestBinSize = 0;

			for (int i = 0; i < currentBinCount; i++) {
				int size = binEnds[i] - binStarts[i] + 1;
				if (size > largestBinSize && canSplit(sortedValues, binStarts[i], binEnds[i])) {
					largestBinSize = size;
					largestBinIdx = i;
				}
			}

			if (largestBinIdx == -1) {
				break;
			}

			int start = binStarts[largestBinIdx];
			int end = binEnds[largestBinIdx];
			int splitPoint = findSplitPoint(sortedValues, start, end);

			binStarts[currentBinCount] = splitPoint;
			binEnds[currentBinCount] = end;

			binEnds[largestBinIdx] = splitPoint - 1;

			currentBinCount++;
		}

		for (int bin = 0; bin < currentBinCount; bin++) {
			for (int i = binStarts[bin]; i <= binEnds[bin]; i++) {
				binAssignments[i] = bin;
			}
		}

		return binAssignments;
	}

	private boolean canSplit(double[] values, int start, int end) {
		if (end - start < 1)
			return false;

		double first = values[start];
		for (int i = start + 1; i <= end; i++) {
			if (values[i] != first) {
				return true;
			}
		}
		return false;
	}

	private int findSplitPoint(double[] sortedValues, int start, int end) {
		int medianIdx = (start + end) / 2;
		double medianValue = sortedValues[medianIdx];

		int splitPoint = medianIdx;
		while (splitPoint < end && sortedValues[splitPoint] == medianValue) {
			splitPoint++;
		}

		if (splitPoint == end && sortedValues[end] == medianValue) {
			splitPoint = medianIdx;
			while (splitPoint > start && sortedValues[splitPoint - 1] == medianValue) {
				splitPoint--;
			}
		}

		return splitPoint;
	}

	private void quickSortWithKeys(double[] values, Long[] keys, int low, int high) {
		if (high - low > 10000) {
			quickSortIterative(values, keys, low, high);
		} else {
			quickSortRecursive(values, keys, low, high);
		}
	}

	private void quickSortRecursive(double[] values, Long[] keys, int low, int high) {
		while (low < high) {
			if (high - low < 10) {
				insertionSort(values, keys, low, high);
				return;
			}

			int pi = partitionWithMedian(values, keys, low, high);

			if (pi - low < high - pi) {
				quickSortRecursive(values, keys, low, pi - 1);
				low = pi + 1;
			} else {
				quickSortRecursive(values, keys, pi + 1, high);
				high = pi - 1;
			}
		}
	}

	private void quickSortIterative(double[] values, Long[] keys, int low, int high) {
		int[] stack = new int[high - low + 1];
		int top = -1;

		stack[++top] = low;
		stack[++top] = high;

		while (top >= 0) {
			high = stack[top--];
			low = stack[top--];

			if (high - low < 10) {
				insertionSort(values, keys, low, high);
				continue;
			}

			int pi = partitionWithMedian(values, keys, low, high);

			if (pi - 1 > low) {
				stack[++top] = low;
				stack[++top] = pi - 1;
			}

			if (pi + 1 < high) {
				stack[++top] = pi + 1;
				stack[++top] = high;
			}
		}
	}

	private void insertionSort(double[] values, Long[] keys, int low, int high) {
		for (int i = low + 1; i <= high; i++) {
			double valueKey = values[i];
			Long keyKey = keys[i];
			int j = i - 1;

			while (j >= low && values[j] > valueKey) {
				values[j + 1] = values[j];
				keys[j + 1] = keys[j];
				j--;
			}
			values[j + 1] = valueKey;
			keys[j + 1] = keyKey;
		}
	}

	private int partitionWithMedian(double[] values, Long[] keys, int low, int high) {
		int mid = low + (high - low) / 2;

		if (values[mid] < values[low]) {
			swap(values, keys, low, mid);
		}
		if (values[high] < values[low]) {
			swap(values, keys, low, high);
		}
		if (values[high] < values[mid]) {
			swap(values, keys, mid, high);
		}

		swap(values, keys, mid, high - 1);
		double pivot = values[high - 1];

		int i = low;
		int j = high - 1;

		while (true) {
			while (values[++i] < pivot) {
				if (i >= high - 1)
					break;
			}
			while (values[--j] > pivot) {
				if (j <= low)
					break;
			}
			if (i >= j)
				break;
			swap(values, keys, i, j);
		}

		swap(values, keys, i, high - 1);
		return i;
	}

	private void swap(double[] values, Long[] keys, int i, int j) {
		double tempV = values[i];
		values[i] = values[j];
		values[j] = tempV;

		Long tempK = keys[i];
		keys[i] = keys[j];
		keys[j] = tempK;
	}

	private List<Number> convertToList(double[] values) {
		List<Number> list = new ArrayList<>(values.length);
		for (double v : values) {
			list.add(v);
		}
		return list;
	}

	private boolean containsNaNs() {
		for (Map.Entry<Long, Number> entry : elements.entrySet()) {
			Number value = entry.getValue();
			if (value == null || Double.isNaN(value.doubleValue())) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void splitBin(Bin bin, int targetCount) {
		if (bins == null) {
			throw new NullPointerException(toString() + ": splitBin() bins are null");
		}
		if (!bins.contains(bin)) {
			throw new IllegalArgumentException(toString() + ": splitBin() Aggregation does not contain bin");
		}

		int index = bins.indexOf(bin);

		Map<Long, Number> numericElements = Bins.getNumericElements(bin);
		int validCount = 0;

		for (Map.Entry<Long, Number> entry : numericElements.entrySet()) {
			Number number = entry.getValue();
			if (!Double.isNaN(number.doubleValue())) {
				validCount++;
			}
		}

		double[] values = new double[validCount];
		Long[] keys = new Long[validCount];
		int idx = 0;

		for (Map.Entry<Long, Number> entry : numericElements.entrySet()) {
			Number number = entry.getValue();
			if (!Double.isNaN(number.doubleValue())) {
				values[idx] = number.doubleValue();
				keys[idx] = entry.getKey();
				idx++;
			}
		}

		quickSortWithKeys(values, keys, 0, values.length - 1);

		int[] binAssignments = splitFrequencyBasedIterative(values, targetCount);

		List<Map<Long, Number>> points = new ArrayList<>();
		for (int i = 0; i < targetCount; i++) {
			points.add(new HashMap<>());
		}

		for (int i = 0; i < values.length; i++) {
			points.get(binAssignments[i]).put(keys[i], values[i]);
		}

		bins.remove(index);

		List<NumbersBin> newBins = new ArrayList<>();
		for (Map<Long, Number> point : points) {
			if (!point.isEmpty()) {
				newBins.add(new NumbersBin(point, name));
			}
		}

		List<NumbersBin> sortedBins = Bins.sortByMinValue(newBins, false);

		for (int i = 0; i < sortedBins.size(); i++) {
			bins.add(index + i, sortedBins.get(i));
		}

		refresh();
		notifyListeners(new BinChangeEvent(this, bins));
	}

	@Override
	public void mergeBins(List<Bin> bins) {
		throw new UnsupportedOperationException("NumericAggregationFunction: mergeBins not supported");
	}

	public enum PartitioningStrategyEnum {
		DOMAIN_UNIFORM, FREQUENCY_UNIFORM, GOODNESS_OF_FIT,
	}

	public AggregationCharacterization getAggregationInitialization() {
		return aggregationInitialization;
	}

	public double getOutlierPercentage() {
		return outlierPercentage;
	}

	public void setOutlierPercentage(double outlierPercentage) {
		this.outlierPercentage = outlierPercentage;
	}
}
