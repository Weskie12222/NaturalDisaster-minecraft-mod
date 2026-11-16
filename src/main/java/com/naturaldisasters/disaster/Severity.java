package com.naturaldisasters.disaster;

import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.util.RandomSource;

public enum Severity {
	MINOR("Minor", 1, ChatFormatting.YELLOW),
	MODERATE("Moderate", 2, ChatFormatting.GOLD),
	STRONG("Strong", 3, ChatFormatting.RED),
	EXTREME("Extreme", 4, ChatFormatting.DARK_RED);

	public final String label;
	public final int level;
	public final ChatFormatting color;

	Severity(String label, int level, ChatFormatting color) {
		this.label = label;
		this.level = level;
		this.color = color;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static Severity parse(String s) {
		for (Severity v : values()) {
			if (v.id().equals(s.toLowerCase(Locale.ROOT))) {
				return v;
			}
		}
		return null;
	}

	/** Natural-spawn weights: minor 40%, moderate 30%, strong 20%, extreme 10%. */
	public static Severity random(RandomSource random) {
		int roll = random.nextInt(100);
		if (roll < 40) {
			return MINOR;
		}
		if (roll < 70) {
			return MODERATE;
		}
		if (roll < 90) {
			return STRONG;
		}
		return EXTREME;
	}
}
