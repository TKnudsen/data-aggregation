package com.github.TKnudsen.dataAggregation.operation.interestingness;

/**
 * Description metadata for an interestingness measure.
 *
 * @since 2026
 */
public class MeasureDescription {
	private final String name;
	private final String shortDescription;
	private final String rangeDescription;
	private final String interpretationGuide;
	private final String bestUsedFor;
	private final String limitations;
	private final String calculationFormula;
	private final boolean hasDirection;
	private final boolean isNormalized;

	public MeasureDescription(String name, String shortDescription, String rangeDescription, String interpretationGuide,
			String bestUsedFor, String limitations, String calculationFormula, boolean hasDirection,
			boolean isNormalized) {
		this.name = name;
		this.shortDescription = shortDescription;
		this.rangeDescription = rangeDescription;
		this.interpretationGuide = interpretationGuide;
		this.bestUsedFor = bestUsedFor;
		this.limitations = limitations;
		this.calculationFormula = calculationFormula;
		this.hasDirection = hasDirection;
		this.isNormalized = isNormalized;
	}

	// Getters
	public String getName() {
		return name;
	}

	public String getShortDescription() {
		return shortDescription;
	}

	public String getRangeDescription() {
		return rangeDescription;
	}

	public String getInterpretationGuide() {
		return interpretationGuide;
	}

	public String getBestUsedFor() {
		return bestUsedFor;
	}

	public String getLimitations() {
		return limitations;
	}

	public String getCalculationFormula() {
		return calculationFormula;
	}

	public boolean hasDirection() {
		return hasDirection;
	}

	public boolean isNormalized() {
		return isNormalized;
	}

	/**
	 * Get a formatted string representation of the description.
	 */
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("=== ").append(name).append(" ===\n\n");
		sb.append("Description: ").append(shortDescription).append("\n\n");
		sb.append("Range: ").append(rangeDescription).append("\n\n");
		sb.append("Interpretation:\n").append(interpretationGuide).append("\n\n");
		sb.append("Best Used For:\n").append(bestUsedFor).append("\n\n");
		sb.append("Limitations:\n").append(limitations).append("\n\n");
		sb.append("Calculation: ").append(calculationFormula).append("\n\n");
		sb.append("Properties:\n");
		sb.append("  - Has Direction: ").append(hasDirection ? "Yes" : "No").append("\n");
		sb.append("  - Normalized: ").append(isNormalized ? "Yes" : "No").append("\n");
		return sb.toString();
	}

	/**
	 * Get a compact single-line summary.
	 */
	public String toCompactString() {
		return String.format("%s: %s (Range: %s, Direction: %s, Normalized: %s)", name, shortDescription,
				rangeDescription, hasDirection ? "Yes" : "No", isNormalized ? "Yes" : "No");
	}
}