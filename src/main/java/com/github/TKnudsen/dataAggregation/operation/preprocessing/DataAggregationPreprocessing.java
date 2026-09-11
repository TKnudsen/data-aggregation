package com.github.TKnudsen.dataAggregation.operation.preprocessing;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.github.TKnudsen.ComplexDataObject.data.complexDataObject.ComplexDataObject;
import com.github.TKnudsen.ComplexDataObject.data.entry.EntryWithComparableKey;
import com.github.TKnudsen.ComplexDataObject.model.processors.SamplingDataProcessor;

/**
 * Helper class for filtering and sampling data.
 * 
 * <p>
 * Provides utilities for applying filters and random sampling to
 * ComplexDataObject collections.
 * </p>
 *
 * @since 2026
 */
public class DataAggregationPreprocessing {

	/**
	 * Apply filters to data (exclusion-based).
	 * 
	 * @param data    Input data
	 * @param filters Filters to apply (objects matching ANY filter are excluded)
	 * @return Filtered data
	 */
	public static List<ComplexDataObject> applyFilters(List<ComplexDataObject> data,
			List<EntryWithComparableKey<String, Object>> filters) {

		if (data == null)
			throw new IllegalArgumentException("Data cannot be null");

		if (filters == null || filters.isEmpty())
			return new ArrayList<>(data);

		// Validate filters first
		for (EntryWithComparableKey<String, Object> filter : filters) {
			Objects.requireNonNull(filter, "Filter cannot be null");
			Objects.requireNonNull(filter.getKey(), "Filter attribute cannot be null");
			Objects.requireNonNull(filter.getValue(), "Filter attribute value cannot be null");
		}

		// Apply filters
		List<ComplexDataObject> filtered = new ArrayList<>();

		for (ComplexDataObject cdo : data)
			if (!shouldExclude(cdo, filters))
				filtered.add(cdo);

		return filtered;
	}

	/**
	 * Check if object should be excluded based on filters.
	 * 
	 * @param cdo     Object to check
	 * @param filters Filters to apply
	 * @return true if object matches ANY filter (should be excluded)
	 */
	private static boolean shouldExclude(ComplexDataObject cdo, List<EntryWithComparableKey<String, Object>> filters) {

		for (EntryWithComparableKey<String, Object> filter : filters) {
			Object attributeValue = cdo.getAttribute(filter.getKey());
			
			if (filter.getValue().equals(attributeValue))
				return true; // Matches filter, exclude
		}

		return false; // Doesn't match any filter, keep
	}

	/**
	 * Apply random sampling to data (order-preserving).
	 * 
	 * @param data       Input data
	 * @param targetSize Target size after sampling
	 * @return Sampled data
	 */
	public static List<ComplexDataObject> applySampling(List<ComplexDataObject> data, int targetSize) {

		if (data == null)
			throw new IllegalArgumentException("Data cannot be null");

		if (targetSize <= 0)
			throw new IllegalArgumentException("Target size must be positive");

		// No sampling needed if already small enough
		if (data.size() <= targetSize)
			return new ArrayList<>(data);

		// Apply sampling
		SamplingDataProcessor<ComplexDataObject> sampler = new SamplingDataProcessor<>(targetSize);

		List<ComplexDataObject> mutableList = new ArrayList<>(data);
		sampler.process(mutableList);

		return mutableList;
	}

	/**
	 * Sample a generic collection to target size.
	 * 
	 * @param <T>        Type of elements
	 * @param collection Collection to sample
	 * @param targetSize Target size
	 * @return Sampled collection
	 */
	public static <T> Collection<T> sampleCollection(Collection<T> collection, int targetSize) {

		if (collection == null)
			throw new IllegalArgumentException("Collection cannot be null");

		if (targetSize <= 0)
			throw new IllegalArgumentException("Target size must be positive");

		// No sampling needed
		if (collection.size() <= targetSize)
			return new ArrayList<>(collection);

		// Convert to list and sample
		SamplingDataProcessor<T> sampler = new SamplingDataProcessor<>(targetSize);
		List<T> list = new ArrayList<>(collection);
		sampler.process(list);

		return list;
	}

	/**
	 * Create an exclusion filter for a specific attribute value.
	 * 
	 * @param attribute Attribute name
	 * @param value     Value to exclude
	 * @return Filter entry
	 */
	public static EntryWithComparableKey<String, Object> createExclusionFilter(String attribute, Object value) {

		Objects.requireNonNull(attribute, "Attribute cannot be null");
		Objects.requireNonNull(value, "Value cannot be null");

		return new EntryWithComparableKey<>(attribute, value);
	}

	/**
	 * Create multiple exclusion filters.
	 * 
	 * @param filters Map of attribute -> value pairs to exclude
	 * @return List of filter entries
	 */
	public static List<EntryWithComparableKey<String, Object>> createExclusionFilters(
			java.util.Map<String, Object> filters) {

		if (filters == null)
			return new ArrayList<>();

		List<EntryWithComparableKey<String, Object>> filterList = new ArrayList<>();

		for (java.util.Map.Entry<String, Object> entry : filters.entrySet())
			filterList.add(createExclusionFilter(entry.getKey(), entry.getValue()));

		return filterList;
	}
}
