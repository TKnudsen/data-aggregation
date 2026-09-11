package com.github.TKnudsen.dataAggregation.operation.aggregation.functions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.control.events.BinChangeEvent;
import com.github.TKnudsen.dataAggregation.data.bins.Bin;
import com.github.TKnudsen.dataAggregation.data.bins.Bins;
import com.github.TKnudsen.dataAggregation.data.bins.StringsBin;

/**
 * <p>
 * Aggregation function for string-valued attributes. Uses
 * Map&lt;String, LongList&gt; histograms (IDs only) and merges by concatenating
 * ID lists (no repeated Map.putAll).
 * </p>
 *
 * @since 2013
 */
public class StringAggregationFunction extends AggregationFunction<String> {

	private static final Logger LOGGER = Logger.getLogger(StringAggregationFunction.class.getName());

	private static final String OTHER_LABEL = "OTHER";

	public StringAggregationFunction(Map<Long, String> elements, int aggregationLevel, String name) {
		super(elements, aggregationLevel, name);
	}

	// ==================== PRIMITIVE LONG LIST ====================

	private static final class LongList {
		private long[] data;
		private int size;

		LongList(int initialCapacity) {
			this.data = new long[Math.max(4, initialCapacity)];
		}

		void add(long v) {
			int s = size;
			if (s == data.length) {
				long[] nd = new long[s + (s >> 1) + 1];
				System.arraycopy(data, 0, nd, 0, s);
				data = nd;
			}
			data[s] = v;
			size = s + 1;
		}

		void addAll(LongList other) {
			if (other == null || other.size == 0)
				return;
			ensureCapacity(size + other.size);
			System.arraycopy(other.data, 0, this.data, this.size, other.size);
			this.size += other.size;
		}

		int size() {
			return size;
		}

		long get(int i) {
			return data[i];
		}

		private void ensureCapacity(int desired) {
			if (desired <= data.length)
				return;
			int newCap = data.length;
			while (newCap < desired) {
				newCap = newCap + (newCap >> 1) + 1;
			}
			long[] nd = new long[newCap];
			System.arraycopy(data, 0, nd, 0, size);
			data = nd;
		}
	}

	private static final class BinEntry implements Comparable<BinEntry> {
		final String key;
		final LongList ids;

		BinEntry(String key, LongList ids) {
			this.key = key;
			this.ids = ids;
		}

		@Override
		public int compareTo(BinEntry other) {
			int c = Integer.compare(this.ids.size(), other.ids.size());
			if (c != 0)
				return c;
			return this.key.compareTo(other.key);
		}
	}

	private static Map<Long, String> materialize(String value, LongList ids) {
		final int n = ids.size();
		final Map<Long, String> map = new HashMap<>((int) (n / 0.75f) + 1);
		for (int i = 0; i < n; i++)
			map.put(ids.get(i), value);

		return map;
	}

	// ==================== AGGREGATION ====================

	@Override
	public void calculateAggregation() {
		final long startTime = System.currentTimeMillis();

		bins = new ArrayList<>(Math.min(aggregationLevel, elements.size()));

		final Map<String, LongList> histogram = new HashMap<>();

		for (Map.Entry<Long, String> e : elements.entrySet()) {
			final long id = e.getKey();
			final String raw = e.getValue();
			final String value = (raw == null) ? "" : raw;

			histogram.computeIfAbsent(value, k -> new LongList(4)).add(id);
		}

		if (histogram.size() <= aggregationLevel)
			for (Map.Entry<String, LongList> entry : histogram.entrySet())
				bins.add(new StringsBin(materialize(entry.getKey(), entry.getValue()), name));
		else
			mergeSmallestBins(histogram, aggregationLevel);

		if (isPrintOut())
			System.out.println("StringAggregationFunction.calculateAggregation took "
					+ (System.currentTimeMillis() - startTime) + " ms for " + elements.size() + " elements");
	}

	private void mergeSmallestBins(Map<String, LongList> histogram, int targetLevel) {
		final PriorityQueue<BinEntry> pq = new PriorityQueue<>(histogram.size());

		for (Map.Entry<String, LongList> e : histogram.entrySet())
			pq.offer(new BinEntry(e.getKey(), e.getValue()));

		final LongList remainings = new LongList(16);

		while (pq.size() > targetLevel - 1) {
			final BinEntry smallest = pq.poll();
			remainings.addAll(smallest.ids);
		}

		while (!pq.isEmpty()) {
			final BinEntry entry = pq.poll();
			bins.add(new StringsBin(materialize(entry.key, entry.ids), name));
		}

		if (remainings.size() > 0)
			bins.add(new StringsBin(materialize(OTHER_LABEL, remainings), name));

	}

	// ==================== SPLIT ====================

	@Override
	public void splitBin(Bin bucket, int targetCount) {
		if (bins == null)
			throw new NullPointerException(toString() + " : splitBucket() bins are null.");
		if (!bins.contains(bucket))
			throw new IllegalArgumentException(
					toString() + " : splitBucket() Aggregation does not contain such a bucket.");

		final Map<String, LongList> histogram = new HashMap<>();

		@SuppressWarnings("unchecked")
		final Map<Long, String> bucketElements = (Map<Long, String>) bucket.getElements();

		for (Long idObj : bucketElements.keySet()) {
			String value = elements.get(idObj);
			if (value == null)
				value = "";
			histogram.computeIfAbsent(value, k -> new LongList(4)).add(idObj);
		}

		if (histogram.size() <= 1) {
			LOGGER.warning("splitBucket(): only one unique value. Cannot split.");
			return;
		}

		final int actualTargetCount = Math.min(targetCount, histogram.size());
		final int bucketIndex = bins.indexOf(bucket);
		bins.remove(bucketIndex);

		if (actualTargetCount >= histogram.size()) {
			final List<StringsBin> newBins = new ArrayList<>(histogram.size());
			for (Map.Entry<String, LongList> e : histogram.entrySet()) {
				newBins.add(new StringsBin(materialize(e.getKey(), e.getValue()), name));
			}

			// Use Bins utility for sorting
			newBins.sort((a, b) -> Integer.compare(b.size(), a.size()));

			for (int i = newBins.size() - 1; i >= 0; i--)
				bins.add(bucketIndex, newBins.get(i));

		} else {
			final PriorityQueue<BinEntry> pq = new PriorityQueue<>(histogram.size());
			for (Map.Entry<String, LongList> e : histogram.entrySet())
				pq.offer(new BinEntry(e.getKey(), e.getValue()));

			final LongList merged = new LongList(16);
			while (pq.size() > actualTargetCount - 1)
				merged.addAll(pq.poll().ids);

			final List<StringsBin> newBins = new ArrayList<>(actualTargetCount);

			while (!pq.isEmpty()) {
				BinEntry e = pq.poll();
				newBins.add(new StringsBin(materialize(e.key, e.ids), name));
			}

			if (merged.size() > 0)
				newBins.add(new StringsBin(materialize(OTHER_LABEL, merged), name));

			newBins.sort((a, b) -> Integer.compare(b.size(), a.size()));

			for (int i = newBins.size() - 1; i >= 0; i--)
				bins.add(bucketIndex, newBins.get(i));

		}

		refresh();
		notifyListeners(new BinChangeEvent(this, bins));
	}

	// ==================== MERGE - USING Bins UTILITY ====================

	@Override
	public void mergeBins(List<Bin> bucketsToMerge) {
		if (bucketsToMerge == null || bucketsToMerge.size() < 2) {
			LOGGER.warning("mergeBins: need at least 2 bins to merge");
			return;
		}

		// Validation using Bins utility
		for (Bin bin : bucketsToMerge)
			if (!bins.contains(bin)) {
				LOGGER.warning("mergeBins: bin not found in aggregation");
				return;
			}

		if (!Bins.canMerge(bucketsToMerge)) {
			LOGGER.warning("mergeBins: bins are incompatible");
			return;
		}

		final int firstIndex = bins.indexOf(bucketsToMerge.get(0));

		// Remove all bins to merge
		for (Bin bin : bucketsToMerge)
			bins.remove(bin);

		// Use Bins utility to merge
		@SuppressWarnings("unchecked")
		List<StringsBin> stringBinsToMerge = (List<StringsBin>) (List<?>) bucketsToMerge;

		StringsBin mergedBin = Bins.merge(stringBinsToMerge);

		bins.add(firstIndex, mergedBin);

		refresh();
		notifyListeners(new BinChangeEvent(this, bins));
	}

	// ==================== UTILITY METHODS ====================

	/**
	 * Gets aggregate frequency map across all bins
	 */
	public Map<String, Integer> getAggregateFrequencies() {
		if (bins == null || bins.isEmpty())
			return new HashMap<>();

		List<StringsBin> stringBins = new ArrayList<>();
		for (Bin bin : bins)
			if (bin instanceof StringsBin)
				stringBins.add((StringsBin) bin);

		return Bins.aggregateFrequencies(stringBins);
	}

	/**
	 * Gets all unique string values across all bins
	 */
	public java.util.Set<String> getAllUniqueValues() {
		if (bins == null || bins.isEmpty())
			return new java.util.HashSet<>();

		List<StringsBin> stringBins = new ArrayList<>();
		for (Bin bin : bins)
			if (bin instanceof StringsBin)
				stringBins.add((StringsBin) bin);

		return Bins.getAllUniqueStrings(stringBins);
	}
}