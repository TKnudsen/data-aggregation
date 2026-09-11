package com.github.TKnudsen.dataAggregation.data.bins;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.github.TKnudsen.ComplexDataObject.data.interfaces.IDObject;
import com.github.TKnudsen.ComplexDataObject.data.interfaces.ISelfDescription;
import com.github.TKnudsen.ComplexDataObject.model.tools.MathFunctions;

/**
 * <p>
 * Abstract base class for bins containing elements of the same type. A bin
 * represents a group of elements resulting from data aggregation, such as
 * numerical ranges or categorical groupings.
 * </p>
 *
 * @since 2013
 */
public abstract class Bin implements IDObject, ISelfDescription, Comparable<Bin> {

	// Immutable core identity
	private final long id;

	// Aggregation information
	private final String aggregationName;

	// Element storage (defensive copy)
	private final Map<Long, ?> elements;

	// Mutable name (can be lazily computed by subclasses)
	protected String name;

	/**
	 * Constructs a Bin with elements and metadata
	 * 
	 * @param elements        map of element IDs to values
	 * @param name            optional name for the bin (can be null, will be
	 *                        computed if needed)
	 * @param aggregationName name of the parent aggregation (required)
	 * @throws IllegalArgumentException if required parameters are invalid
	 */
	public Bin(Map<Long, ?> elements, String name, String aggregationName) {
		// Validation
		if (aggregationName == null || aggregationName.trim().isEmpty())
			throw new IllegalArgumentException("Aggregation name cannot be null or empty");
		if (elements == null)
			throw new IllegalArgumentException("Elements map cannot be null");

		// Initialize immutable fields
		this.id = MathFunctions.randomLong();
		this.aggregationName = aggregationName;
		this.name = name;

		// Defensive copy to prevent external modification
		this.elements = Collections.unmodifiableMap(new HashMap<>(elements));
	}

	// ==================== IDObject INTERFACE ====================

	@Override
	public long getID() {
		return id;
	}

	// ==================== ISelfDescription INTERFACE ====================

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getDescription() {
		return getName();
	}

	// ==================== AGGREGATION INFO ====================

	/**
	 * Gets the name of the parent aggregation
	 * 
	 * @return aggregation name (never null)
	 */
	public String getAggregationName() {
		return aggregationName;
	}

	// ==================== ELEMENT ACCESS ====================

	/**
	 * Gets all elements in the bin Returns an unmodifiable view to prevent external
	 * modification
	 * 
	 * @return unmodifiable map of element IDs to values
	 */
	public Map<Long, ?> getElements() {
		return elements;
	}

	/**
	 * Gets the number of elements in the bin
	 * 
	 * @return element count
	 */
	public int size() {
		return elements.size();
	}

	/**
	 * Checks if the bin is empty
	 * 
	 * @return true if no elements
	 */
	public boolean isEmpty() {
		return elements.isEmpty();
	}

	/**
	 * Checks if the bin contains an element with the given ID
	 * 
	 * @param elementId the element ID to check
	 * @return true if element exists
	 */
	public boolean containsElement(Long elementId) {
		return elementId != null && elements.containsKey(elementId);
	}

	/**
	 * Checks if the bin contains a specific value Uses equals() for comparison
	 * 
	 * @param value the value to check
	 * @return true if value exists
	 */
	public boolean containsValue(Object value) {
		return elements.containsValue(value);
	}

	/**
	 * Gets the value for a specific element ID
	 * 
	 * @param elementId the element ID
	 * @return the value, or null if not found
	 */
	public Object getElement(Long elementId) {
		return elementId != null ? elements.get(elementId) : null;
	}

	// ==================== EQUALS/HASHCODE ====================

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null || getClass() != obj.getClass())
			return false;

		Bin other = (Bin) obj;

		// Compare by ID (unique identifier)
		return this.id == other.id;
	}

	@Override
	public int hashCode() {
		return Long.hashCode(id);
	}

	// ==================== STRING REPRESENTATION ====================

	@Override
	public String toString() {
		String binName = getName();
		return "Bin[id=" + id + ", name=" + (binName != null ? binName : "unnamed") + ", aggregation=" + aggregationName
				+ ", size=" + size() + "]";
	}

	/**
	 * Creates a summary string with detailed information
	 * 
	 * @return detailed string representation
	 */
	public String toDetailedString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Bin Details:\n");
		sb.append("  ID: ").append(id).append("\n");
		sb.append("  Name: ").append(getName()).append("\n");
		sb.append("  Aggregation: ").append(aggregationName).append("\n");
		sb.append("  Size: ").append(size()).append(" elements\n");
		sb.append("  Type: ").append(getClass().getSimpleName()).append("\n");
		return sb.toString();
	}

	// ==================== UTILITY METHODS ====================
	// Now delegated to Bins utility for consistency and re-usability

	/**
	 * Checks if this bin has the same elements as another bin Compares element IDs
	 * only, not values
	 * 
	 * @param other the other bin
	 * @return true if element IDs match
	 */
	public boolean hasSameElements(Bin other) {
		if (other == null)
			return false;

		if (this.size() != other.size())
			return false;

		return this.elements.keySet().equals(other.elements.keySet());
	}

	/**
	 * Checks if this bin overlaps with another bin Delegates to Bins utility for
	 * consistency
	 * 
	 * @param other the other bin
	 * @return true if bins share at least one element
	 */
	public boolean overlaps(Bin other) {
		return Bins.hasOverlappingElements(this, other);
	}

	/**
	 * Calculates the overlap size with another bin Delegates to Bins utility for
	 * consistency
	 * 
	 * @param other the other bin
	 * @return number of shared elements
	 */
	public int getOverlapSize(Bin other) {
		return Bins.getOverlappingElementIds(this, other).size();
	}

	/**
	 * Calculates Jaccard similarity with another bin Delegates to Bins utility for
	 * consistency Formula: |A intersect B| / |A union B|
	 * 
	 * @param other the other bin
	 * @return Jaccard similarity [0.0, 1.0]
	 */
	public double calculateJaccardSimilarity(Bin other) {
		return Bins.calculateJaccardSimilarity(this, other);
	}

	// ==================== VALIDATION ====================

	/**
	 * Validates the internal state of the bin Can be overridden by subclasses for
	 * additional checks
	 * 
	 * @return true if valid
	 */
	public boolean validate() {
		// Check required fields
		if (aggregationName == null || aggregationName.trim().isEmpty())
			return false;

		if (elements == null)
			return false;

		// Check ID is reasonable (not default value)
		if (id == 0L || id == -1L)
			return false;

		return true;
	}

	// ==================== COMPARABLE INTERFACE ====================

	/**
	 * Compares bins for sorting Default implementation compares by name Subclasses
	 * should override for type-specific comparison
	 * 
	 * @param other the other bin
	 * @return comparison result
	 */
	@Override
	public int compareTo(Bin other) {
		if (other == null)
			return 1;

		// Default: compare by aggregation name, then ID
		int aggComparison = this.aggregationName.compareTo(other.aggregationName);
		if (aggComparison != 0)
			return aggComparison;

		// Fallback to ID for stability
		return Long.compare(this.id, other.id);
	}
}
