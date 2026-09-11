package com.github.TKnudsen.dataAggregation.operation.io;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import com.github.TKnudsen.dataAggregation.operation.aggregation.functions.AggregationCharacterization;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.TKnudsen.ComplexDataObject.model.io.json.ObjectMapperFactory;

/**
 * <p>
 * I/O utilities for loading and saving aggregation configuration files.
 * Handles JSON serialization of AggregationInitialization objects.
 * </p>
 * 
 * <p>
 * <b>File Format:</b> JSON array of objects with fields:
 * </p>
 * <ul>
 * <li>attName: Attribute name</li>
 * <li>aggName: Aggregation name</li>
 * <li>lvl: Aggregation level (int)</li>
 * <li>part: Partitioning strategy (string)</li>
 * <li>outl: Outlier percentage (double)</li>
 * </ul>
 * 
 * <p>
 * <b>Thread Safety:</b> This class is stateless and thread-safe.
 * </p>
 * 
 * @since 2022
 */
public class AttributeAggregationConfigIO {

	private static final Logger LOGGER = Logger.getLogger(AttributeAggregationConfigIO.class.getName());

	// Field names for JSON serialization
	private static final String FIELD_ATTR_NAME = "attName";
	private static final String FIELD_AGG_NAME = "aggName";
	private static final String FIELD_LEVEL = "lvl";
	private static final String FIELD_PARTITIONING = "part";
	private static final String FIELD_OUTLIER = "outl";

	private static final ObjectMapper MAPPER = ObjectMapperFactory.getComplexDataObjectObjectMapper();

	/**
	 * Private constructor to prevent instantiation.
	 */
	private AttributeAggregationConfigIO() {
		throw new AssertionError("Utility class should not be instantiated");
	}

	// ==================== SAVE OPERATIONS ====================

	/**
	 * Saves aggregation configuration to a JSON file.
	 * 
	 * <p>
	 * Creates parent directories if they don't exist.
	 * </p>
	 * 
	 * @param configs  Map of attribute name to aggregation initialization
	 * @param filePath Path to save config file
	 * @throws IOException              if file cannot be written
	 * @throws NullPointerException     if configs or filePath is null
	 * @throws IllegalArgumentException if filePath is empty
	 */
	public static void saveAttributeAggregationConfig(Map<String, AggregationCharacterization> configs, String filePath)
			throws IOException {

		Objects.requireNonNull(configs, "Configs cannot be null");
		Objects.requireNonNull(filePath, "File path cannot be null");

		if (filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("File path cannot be empty");
		}

		LOGGER.info(String.format("Saving aggregation config: %d attributes to %s", configs.size(), filePath));

		// Create parent directories
		Path path = Paths.get(filePath);
		if (path.getParent() != null) {
			Files.createDirectories(path.getParent());
		}

		// Convert to list of maps
		List<Map<String, Object>> jsonData = new ArrayList<>();

		for (AggregationCharacterization init : configs.values()) {
			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put(FIELD_ATTR_NAME, init.getAttributeName());
			entry.put(FIELD_AGG_NAME, init.getAggregationName());
			entry.put(FIELD_LEVEL, init.getAggregationLevel());
			entry.put(FIELD_PARTITIONING, init.getPartitioningStrategy().toString());
			entry.put(FIELD_OUTLIER, init.getOutlierPercentage());
			jsonData.add(entry);
		}

		// Write to file
		try {
			MAPPER.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), jsonData);

			LOGGER.fine(String.format("Config saved successfully: %s", filePath));

		} catch (IOException e) {
			LOGGER.severe(String.format("Failed to save config to %s: %s", filePath, e.getMessage()));
			throw e;
		}
	}

	/**
	 * Saves configuration with automatic parent directory creation.
	 * 
	 * @param configs  Configuration map
	 * @param filePath Target file path
	 * @return true if save succeeded, false otherwise
	 */
	public static boolean saveAttributeAggregationConfigSafe(Map<String, AggregationCharacterization> configs,
			String filePath) {

		try {
			saveAttributeAggregationConfig(configs, filePath);
			return true;
		} catch (Exception e) {
			LOGGER.warning(String.format("Safe save failed for %s: %s", filePath, e.getMessage()));
			return false;
		}
	}

	// ==================== LOAD OPERATIONS ====================

	/**
	 * Loads aggregation configuration from a JSON file.
	 * 
	 * @param filePath Path to config file
	 * @return Map of attribute name to aggregation initialization
	 * @throws IOException          if file cannot be read or parsed
	 * @throws NullPointerException if filePath is null
	 */
	public static Map<String, AggregationCharacterization> loadAttributeAggregationConfig(String filePath)
			throws IOException {

		Objects.requireNonNull(filePath, "File path cannot be null");

		Path path = Paths.get(filePath);

		if (!Files.exists(path)) {
			throw new IOException(String.format("Config file not found: %s", filePath));
		}

		if (!Files.isRegularFile(path)) {
			throw new IOException(String.format("Path is not a file: %s", filePath));
		}

		LOGGER.info(String.format("Loading aggregation config from: %s", filePath));

		try {
			// Read JSON array
			TypeReference<List<Map<String, Object>>> typeRef = new TypeReference<List<Map<String, Object>>>() {
			};

			List<Map<String, Object>> jsonData = MAPPER.readValue(new File(filePath), typeRef);

			// Convert to config map
			Map<String, AggregationCharacterization> configs = new LinkedHashMap<>();

			for (Map<String, Object> entry : jsonData) {
				try {
					AggregationCharacterization init = parseConfigEntry(entry);
					configs.put(init.getAttributeName(), init);

				} catch (Exception e) {
					LOGGER.warning(String.format("Skipping invalid config entry: %s", e.getMessage()));
				}
			}

			LOGGER.fine(String.format("Loaded %d aggregation configs from %s", configs.size(), filePath));

			return configs;

		} catch (IOException e) {
			LOGGER.severe(String.format("Failed to load config from %s: %s", filePath, e.getMessage()));
			throw e;
		}
	}

	/**
	 * Loads configuration, returning null if file doesn't exist.
	 * 
	 * @param filePath Path to config file
	 * @return Configuration map, or null if file not found
	 */
	public static Map<String, AggregationCharacterization> loadAttributeAggregationConfigSafe(String filePath) {

		try {
			return loadAttributeAggregationConfig(filePath);
		} catch (IOException e) {
			LOGGER.fine(String.format("Config not found or invalid: %s", filePath));
			return null;
		}
	}

	/**
	 * Checks if a config file exists and is valid.
	 * 
	 * @param filePath Path to check
	 * @return true if file exists and is readable
	 */
	public static boolean configExists(String filePath) {
		if (filePath == null) {
			return false;
		}

		Path path = Paths.get(filePath);
		return Files.exists(path) && Files.isRegularFile(path) && Files.isReadable(path);
	}

	// ==================== HELPER METHODS ====================

	/**
	 * Parses a single config entry from JSON map.
	 * 
	 * @param entry JSON map entry
	 * @return AggregationInitialization object
	 * @throws IllegalArgumentException if entry is invalid
	 */
	private static AggregationCharacterization parseConfigEntry(Map<String, Object> entry) {
		// Extract and validate fields
		String attrName = getRequiredString(entry, FIELD_ATTR_NAME);
		String aggName = getRequiredString(entry, FIELD_AGG_NAME);
		int level = getRequiredInt(entry, FIELD_LEVEL);
		String partitioning = getRequiredString(entry, FIELD_PARTITIONING);
		double outlier = getRequiredDouble(entry, FIELD_OUTLIER);

		return new AggregationCharacterization(attrName, aggName, level, partitioning, outlier);
	}

	/**
	 * Gets required string field from map.
	 */
	private static String getRequiredString(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null) {
			throw new IllegalArgumentException(String.format("Missing required field: %s", key));
		}
		return value.toString();
	}

	/**
	 * Gets required int field from map.
	 */
	private static int getRequiredInt(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null) {
			throw new IllegalArgumentException(String.format("Missing required field: %s", key));
		}

		if (value instanceof Number) {
			return ((Number) value).intValue();
		}

		try {
			return Integer.parseInt(value.toString());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(String.format("Invalid integer value for %s: %s", key, value));
		}
	}

	/**
	 * Gets required double field from map.
	 */
	private static double getRequiredDouble(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null) {
			throw new IllegalArgumentException(String.format("Missing required field: %s", key));
		}

		if (value instanceof Number) {
			return ((Number) value).doubleValue();
		}

		try {
			return Double.parseDouble(value.toString());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(String.format("Invalid double value for %s: %s", key, value));
		}
	}

	// ==================== UTILITY METHODS ====================

	/**
	 * Generates default config file path from data file path.
	 * 
	 * @param dataFilePath Path to data file
	 * @return Suggested config file path
	 */
	public static String getDefaultConfigPath(String dataFilePath) {
		Objects.requireNonNull(dataFilePath, "Data file path cannot be null");

		// Remove extension
		String baseName = dataFilePath;
		int lastDot = baseName.lastIndexOf('.');
		if (lastDot > 0) {
			baseName = baseName.substring(0, lastDot);
		}

		return baseName + " aggregationConfig.json";
	}

	/**
	 * Validates a configuration map.
	 * 
	 * @param configs Configuration to validate
	 * @return true if valid
	 */
	public static boolean validateConfig(Map<String, AggregationCharacterization> configs) {
		if (configs == null || configs.isEmpty()) {
			return false;
		}

		for (Map.Entry<String, AggregationCharacterization> entry : configs.entrySet()) {
			String key = entry.getKey();
			AggregationCharacterization init = entry.getValue();

			if (init == null) {
				LOGGER.warning(String.format("Null initialization for key: %s", key));
				return false;
			}

			if (!key.equals(init.getAttributeName())) {
				LOGGER.warning(String.format("Key mismatch: key=%s, attrName=%s", key, init.getAttributeName()));
				return false;
			}
		}

		return true;
	}
}
