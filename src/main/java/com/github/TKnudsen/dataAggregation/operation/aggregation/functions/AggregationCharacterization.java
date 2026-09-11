package com.github.TKnudsen.dataAggregation.operation.aggregation.functions;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.NumericAggregationFunction.PartitioningStrategyEnum;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * <p>
 * Immutable configuration for aggregation characterization. Defines how an
 * attribute should be aggregated including bin count, partitioning strategy,
 * and outlier handling.
 * </p>
 * 
 * <p>
 * <b>Thread Safety:</b> This class is fully immutable and thread-safe. All
 * fields are final and defensive copies are returned where applicable.
 * </p>
 * 
 * <p>
 * <b>Serialization:</b> Supports JSON serialization via Jackson. The
 * partitioning strategy is stored as a String internally to ensure
 * compatibility with various serialization frameworks.
 * </p>
 * 
 * <p>
 * <b>Builder Pattern:</b> Use withXXX() methods to create modified copies:
 * </p>
 * 
 * <pre>
 * AggregationCharacterization config = new AggregationCharacterization("age", 5, DOMAIN_UNIFORM);
 * AggregationCharacterization modified = config.withOutlierPercentage(0.05);
 * </pre>
 * 
 * @since 2015
 */
public final class AggregationCharacterization implements Serializable {

	private static final long serialVersionUID = 1L;
	private static final Logger LOGGER = Logger.getLogger(AggregationCharacterization.class.getName());

	// ==================== CONSTANTS ====================

	/** Minimum allowed aggregation level. */
	public static final int MIN_AGGREGATION_LEVEL = 1;

	/** Maximum recommended aggregation level. */
	public static final int MAX_AGGREGATION_LEVEL = 1000;

	/** Minimum outlier percentage (0% = no outlier removal). */
	public static final double MIN_OUTLIER_PERCENTAGE = 0.0;

	/** Maximum outlier percentage (100% = remove all). */
	public static final double MAX_OUTLIER_PERCENTAGE = 1.0;

	// ==================== FIELDS ====================

	@JsonProperty("attributeName")
	private final String attributeName;

	@JsonProperty("aggregationName")
	private final String aggregationName;

	@JsonProperty("aggregationLevel")
	private final int aggregationLevel;

	/**
	 * Stored as String (not enum) for serialization compatibility. Use
	 * getPartitioningStrategyEnum() to retrieve as enum.
	 */
	@JsonProperty("partitioningStrategy")
	private final String partitioningStrategy;

	@JsonProperty("outlierPercentage")
	private final double outlierPercentage;

	// ==================== CONSTRUCTORS ====================

	/**
	 * Creates aggregation characterization with minimal parameters.
	 * 
	 * <p>
	 * Uses attribute name as aggregation name and 0% outlier removal.
	 * </p>
	 * 
	 * @param attributeName        Attribute to aggregate
	 * @param aggregationLevel     Number of bins/levels
	 * @param partitioningStrategy Partitioning strategy
	 */
	public AggregationCharacterization(String attributeName, int aggregationLevel,
			PartitioningStrategyEnum partitioningStrategy) {
		this(attributeName, null, aggregationLevel, partitioningStrategy, MIN_OUTLIER_PERCENTAGE);
	}

	/**
	 * Creates aggregation characterization with string strategy name.
	 * 
	 * <p>
	 * Useful for deserialization. Uses attribute name as aggregation name.
	 * </p>
	 * 
	 * @param attributeName        Attribute to aggregate
	 * @param aggregationLevel     Number of bins/levels
	 * @param partitioningStrategy Strategy name
	 */
	public AggregationCharacterization(String attributeName, int aggregationLevel, String partitioningStrategy) {
		this(attributeName, null, aggregationLevel, partitioningStrategy, MIN_OUTLIER_PERCENTAGE);
	}

	/**
	 * Creates aggregation characterization with outlier removal.
	 * 
	 * @param attributeName        Attribute to aggregate
	 * @param aggregationLevel     Number of bins/levels
	 * @param partitioningStrategy Partitioning strategy
	 * @param outlierPercentage    Outlier removal ratio [0.0, 1.0]
	 */
	public AggregationCharacterization(String attributeName, int aggregationLevel,
			PartitioningStrategyEnum partitioningStrategy, double outlierPercentage) {
		this(attributeName, null, aggregationLevel, partitioningStrategy, outlierPercentage);
	}

	/**
	 * Creates aggregation characterization with custom aggregation name.
	 * 
	 * @param attributeName        Attribute to aggregate
	 * @param aggregationName      Display name (null = use attribute name)
	 * @param aggregationLevel     Number of bins/levels
	 * @param partitioningStrategy Partitioning strategy
	 * @param outlierPercentage    Outlier removal ratio [0.0, 1.0]
	 */
	public AggregationCharacterization(String attributeName, String aggregationName, int aggregationLevel,
			PartitioningStrategyEnum partitioningStrategy, double outlierPercentage) {
		this(attributeName, aggregationName, aggregationLevel, partitioningStrategy.name(), outlierPercentage);
	}

	/**
	 * Primary constructor with full validation.
	 * 
	 * <p>
	 * This is the constructor used by Jackson for JSON deserialization.
	 * </p>
	 * 
	 * @param attributeName        Name of the attribute (required)
	 * @param aggregationName      Display name (null = use attribute name)
	 * @param aggregationLevel     Number of bins/levels (>= 1)
	 * @param partitioningStrategy Strategy name (must be valid enum)
	 * @param outlierPercentage    Outlier ratio [0.0, 1.0]
	 * @throws IllegalArgumentException if any parameter is invalid
	 */
	@JsonCreator
	public AggregationCharacterization(@JsonProperty("attributeName") String attributeName,
			@JsonProperty("aggregationName") String aggregationName,
			@JsonProperty("aggregationLevel") int aggregationLevel,
			@JsonProperty("partitioningStrategy") String partitioningStrategy,
			@JsonProperty("outlierPercentage") double outlierPercentage) {

		// Validate attribute name
		Objects.requireNonNull(attributeName, "Attribute name cannot be null");
		if (attributeName.trim().isEmpty()) {
			throw new IllegalArgumentException("Attribute name cannot be empty");
		}
		this.attributeName = attributeName.trim();

		// Set aggregation name (default to attribute name)
		if (aggregationName == null || aggregationName.trim().isEmpty()) {
			this.aggregationName = this.attributeName;
		} else {
			this.aggregationName = aggregationName.trim();
		}

		// Validate aggregation level
		if (aggregationLevel < MIN_AGGREGATION_LEVEL) {
			throw new IllegalArgumentException(
					String.format("Aggregation level must be >= %d, got: %d", MIN_AGGREGATION_LEVEL, aggregationLevel));
		}
		if (aggregationLevel > MAX_AGGREGATION_LEVEL) {
			LOGGER.warning(String.format("Aggregation level %d exceeds recommended maximum %d", aggregationLevel,
					MAX_AGGREGATION_LEVEL));
		}
		this.aggregationLevel = aggregationLevel;

		// Validate partitioning strategy
		Objects.requireNonNull(partitioningStrategy, "Partitioning strategy cannot be null");
		validatePartitioningStrategy(partitioningStrategy);
		this.partitioningStrategy = partitioningStrategy;

		// Validate outlier percentage
		if (outlierPercentage < MIN_OUTLIER_PERCENTAGE || outlierPercentage > MAX_OUTLIER_PERCENTAGE) {
			throw new IllegalArgumentException(String.format("Outlier percentage must be in [%.2f, %.2f], got: %.2f",
					MIN_OUTLIER_PERCENTAGE, MAX_OUTLIER_PERCENTAGE, outlierPercentage));
		}
		this.outlierPercentage = outlierPercentage;

		LOGGER.fine(String.format("Created AggregationCharacterization: %s (level=%d, strategy=%s, outliers=%.2f%%)",
				attributeName, aggregationLevel, partitioningStrategy, outlierPercentage * 100));
	}

	// ==================== FACTORY METHODS ====================

	/**
	 * Creates a default configuration with uniform domain partitioning.
	 * 
	 * @param attributeName    Attribute to aggregate
	 * @param aggregationLevel Number of bins
	 * @return New configuration
	 */
	public static AggregationCharacterization createDefault(String attributeName, int aggregationLevel) {
		return new AggregationCharacterization(attributeName, aggregationLevel,
				PartitioningStrategyEnum.DOMAIN_UNIFORM);
	}

	/**
	 * Creates a configuration for categorical data.
	 * 
	 * <p>
	 * Uses DOMAIN_UNIFORM strategy with no outlier removal.
	 * </p>
	 * 
	 * @param attributeName Attribute to aggregate
	 * @param cardinality   Number of unique categories
	 * @return New configuration
	 */
	public static AggregationCharacterization forCategorical(String attributeName, int cardinality) {
		return new AggregationCharacterization(attributeName, cardinality, PartitioningStrategyEnum.DOMAIN_UNIFORM,
				MIN_OUTLIER_PERCENTAGE);
	}

	/**
	 * Creates a configuration for numeric data with outlier removal.
	 * 
	 * @param attributeName     Attribute to aggregate
	 * @param aggregationLevel  Number of bins
	 * @param outlierPercentage Outlier removal ratio
	 * @return New configuration
	 */
	public static AggregationCharacterization forNumeric(String attributeName, int aggregationLevel,
			double outlierPercentage) {
		return new AggregationCharacterization(attributeName, aggregationLevel, PartitioningStrategyEnum.DOMAIN_UNIFORM,
				outlierPercentage);
	}

	// ==================== GETTERS ====================

	/**
	 * Gets the attribute name.
	 * 
	 * @return Attribute name (never null)
	 */
	public String getAttributeName() {
		return attributeName;
	}

	/**
	 * Gets the aggregation display name.
	 * 
	 * @return Aggregation name (never null)
	 */
	public String getAggregationName() {
		return aggregationName;
	}

	/**
	 * Gets the aggregation level (bin count).
	 * 
	 * @return Aggregation level (>= 1)
	 */
	public int getAggregationLevel() {
		return aggregationLevel;
	}

	/**
	 * Gets the partitioning strategy as enum.
	 * 
	 * @return Partitioning strategy enum (never null)
	 * @throws IllegalStateException if stored string is invalid (should never
	 *                               happen)
	 */
	public PartitioningStrategyEnum getPartitioningStrategyEnum() {
		try {
			return PartitioningStrategyEnum.valueOf(partitioningStrategy);
		} catch (IllegalArgumentException e) {
			// This should never happen due to constructor validation
			throw new IllegalStateException(
					String.format("Internal error: Invalid partitioning strategy: '%s'", partitioningStrategy), e);
		}
	}

	/**
	 * Gets the partitioning strategy as string (for serialization).
	 * 
	 * @return Strategy name (never null)
	 */
	public String getPartitioningStrategy() {
		return partitioningStrategy;
	}

	/**
	 * Gets the outlier removal percentage.
	 * 
	 * @return Outlier percentage [0.0, 1.0]
	 */
	public double getOutlierPercentage() {
		return outlierPercentage;
	}

	/**
	 * Checks if outlier removal is enabled.
	 * 
	 * @return true if outlierPercentage > 0
	 */
	public boolean hasOutlierRemoval() {
		return outlierPercentage > MIN_OUTLIER_PERCENTAGE;
	}

	// ==================== BUILDER PATTERN ====================

	/**
	 * Creates a copy with modified outlier percentage.
	 * 
	 * <p>
	 * Since this class is immutable, returns a new instance.
	 * </p>
	 * 
	 * @param outlierPercentage New outlier percentage [0.0, 1.0]
	 * @return New instance with updated outlier percentage
	 */
	public AggregationCharacterization withOutlierPercentage(double outlierPercentage) {
		return new AggregationCharacterization(this.attributeName, this.aggregationName, this.aggregationLevel,
				this.partitioningStrategy, outlierPercentage);
	}

	/**
	 * Creates a copy with modified aggregation level.
	 * 
	 * @param aggregationLevel New aggregation level (>= 1)
	 * @return New instance with updated level
	 */
	public AggregationCharacterization withAggregationLevel(int aggregationLevel) {
		return new AggregationCharacterization(this.attributeName, this.aggregationName, aggregationLevel,
				this.partitioningStrategy, this.outlierPercentage);
	}

	/**
	 * Creates a copy with modified partitioning strategy.
	 * 
	 * @param strategy New partitioning strategy
	 * @return New instance with updated strategy
	 */
	public AggregationCharacterization withPartitioningStrategy(PartitioningStrategyEnum strategy) {
		return new AggregationCharacterization(this.attributeName, this.aggregationName, this.aggregationLevel,
				strategy, this.outlierPercentage);
	}

	/**
	 * Creates a copy with modified aggregation name.
	 * 
	 * @param aggregationName New aggregation name
	 * @return New instance with updated name
	 */
	public AggregationCharacterization withAggregationName(String aggregationName) {
		return new AggregationCharacterization(this.attributeName, aggregationName, this.aggregationLevel,
				this.partitioningStrategy, this.outlierPercentage);
	}

	// ==================== VALIDATION ====================

	/**
	 * Validates that this configuration is compatible with an attribute type.
	 * 
	 * @param isNumeric Whether the attribute is numeric
	 * @throws IllegalStateException if configuration is incompatible with attribute
	 *                               type
	 */
	public void validateForAttributeType(boolean isNumeric) {
		if (!isNumeric) {
			// Categorical attributes
			if (hasOutlierRemoval()) {
				LOGGER.warning(String.format(
						"Outlier removal (%.2f%%) specified for non-numeric attribute '%s' - will be ignored",
						outlierPercentage * 100, attributeName));
			}
		}
	}

	/**
	 * Validates the partitioning strategy string.
	 * 
	 * @param strategy Strategy string to validate
	 * @throws IllegalArgumentException if strategy is invalid
	 */
	private static void validatePartitioningStrategy(String strategy) {
		try {
			PartitioningStrategyEnum.valueOf(strategy);
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(String.format("Invalid partitioning strategy: '%s'. Valid values: %s",
					strategy, Arrays.toString(PartitioningStrategyEnum.values())), e);
		}
	}

	// ==================== OBJECT METHODS ====================

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof AggregationCharacterization)) {
			return false;
		}

		AggregationCharacterization other = (AggregationCharacterization) obj;
		return aggregationLevel == other.aggregationLevel
				&& Double.compare(other.outlierPercentage, outlierPercentage) == 0
				&& Objects.equals(attributeName, other.attributeName)
				&& Objects.equals(aggregationName, other.aggregationName)
				&& Objects.equals(partitioningStrategy, other.partitioningStrategy);
	}

	@Override
	public int hashCode() {
		return Objects.hash(attributeName, aggregationName, aggregationLevel, partitioningStrategy, outlierPercentage);
	}

	@Override
	public String toString() {
		return String.format(
				"AggregationCharacterization{attribute='%s', name='%s', level=%d, strategy=%s, outliers=%.2f%%}",
				attributeName, aggregationName, aggregationLevel, partitioningStrategy, outlierPercentage * 100);
	}

	// ==================== UTILITY METHODS ====================

	/**
	 * Creates a human-readable summary of this configuration.
	 * 
	 * @return Summary string
	 */
	public String toSummaryString() {
		StringBuilder sb = new StringBuilder();
		sb.append(String.format("Attribute: %s\n", attributeName));
		sb.append(String.format("  Aggregation Name: %s\n", aggregationName));
		sb.append(String.format("  Level: %d bins\n", aggregationLevel));
		sb.append(String.format("  Strategy: %s\n", partitioningStrategy));

		if (hasOutlierRemoval()) {
			sb.append(String.format("  Outlier Removal: %.2f%%\n", outlierPercentage * 100));
		} else {
			sb.append("  Outlier Removal: None\n");
		}

		return sb.toString();
	}

	/**
	 * Checks if this configuration is equivalent to another (ignoring aggregation
	 * name).
	 * 
	 * @param other Other configuration
	 * @return true if functionally equivalent
	 */
	public boolean isEquivalentTo(AggregationCharacterization other) {
		if (other == null) {
			return false;
		}

		return aggregationLevel == other.aggregationLevel
				&& Double.compare(other.outlierPercentage, outlierPercentage) == 0
				&& Objects.equals(attributeName, other.attributeName)
				&& Objects.equals(partitioningStrategy, other.partitioningStrategy);
	}

	/**
	 * Creates a copy of this configuration.
	 * 
	 * @return New instance with same values
	 */
	public AggregationCharacterization copy() {
		return new AggregationCharacterization(this.attributeName, this.aggregationName, this.aggregationLevel,
				this.partitioningStrategy, this.outlierPercentage);
	}
}