package com.naturaldisasters.disaster;

import java.util.Locale;

public enum DisasterType {
	// name, warning lead time in seconds, minimum severity that sets off sirens, siren pitch
	EARTHQUAKE("Earthquake", 25, Severity.STRONG, 1.35f),
	FISSURE("Ground Rift", 20, Severity.STRONG, 1.2f),
	TORNADO("Tornado", 60, Severity.STRONG, 1.0f),
	HURRICANE("Hurricane", 120, Severity.STRONG, 0.85f),
	TSUNAMI("Tsunami", 90, Severity.MODERATE, 0.65f),
	FLOOD("Flood", 90, Severity.STRONG, 0.95f);

	public final String label;
	public final int warningSeconds;
	public final Severity sirenThreshold;
	public final float sirenPitch;

	DisasterType(String label, int warningSeconds, Severity sirenThreshold, float sirenPitch) {
		this.label = label;
		this.warningSeconds = warningSeconds;
		this.sirenThreshold = sirenThreshold;
		this.sirenPitch = sirenPitch;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static DisasterType parse(String s) {
		for (DisasterType t : values()) {
			if (t.id().equals(s.toLowerCase(Locale.ROOT))) {
				return t;
			}
		}
		return null;
	}
}
