package com.naturaldisasters.disaster;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Chat / action-bar warnings sent to players near a disaster. */
public final class Warnings {
	private Warnings() {
	}

	/** Sent as soon as a disaster is forecast (start of the warning phase). */
	public static void broadcastForecast(Disaster d) {
		for (ServerPlayer p : d.level.players()) {
			double dist = p.position().distanceTo(d.position());
			if (dist > d.warningRadius()) {
				continue;
			}
			boolean siren = d.triggersSirens();
			Component head = Component.literal(siren ? "⚠ " + d.severity.label.toUpperCase() + " " + d.type.label.toUpperCase() + " WARNING"
					: "⚠ " + d.describe() + " forecast")
					.withStyle(d.severity.color, ChatFormatting.BOLD);
			Component detail = Component.literal("  " + (int) dist + " blocks " + DisasterUtil.compass(p.position(), d.position())
					+ " - impact in " + d.secondsUntilImpact() + "s" + (siren ? " - SIRENS ACTIVE" : ""))
					.withStyle(ChatFormatting.GRAY);
			p.sendSystemMessage(head);
			p.sendSystemMessage(detail);
			p.sendSystemMessage(advice(d));
		}
	}

	public static void broadcastImpact(Disaster d, String text) {
		for (ServerPlayer p : d.level.players()) {
			if (p.position().distanceTo(d.position()) <= d.warningRadius()) {
				p.sendSystemMessage(Component.literal(text).withStyle(d.severity.color, ChatFormatting.BOLD), true);
			}
		}
	}

	public static void broadcastEnd(Disaster d) {
		for (ServerPlayer p : d.level.players()) {
			if (p.position().distanceTo(d.position()) <= d.warningRadius()) {
				p.sendSystemMessage(Component.literal(d.type.label + " has passed. Stay alert for aftershocks and debris.")
						.withStyle(ChatFormatting.GREEN), true);
			}
		}
	}

	/** One-line survival advice per disaster type. */
	private static Component advice(Disaster d) {
		String text = switch (d.type) {
			case EARTHQUAKE -> "Get into the open, away from glass and tall structures. Expect a ground rift after strong quakes.";
			case FISSURE -> "The ground will split. Move away from the marked area and do not stand near cave openings.";
			case TORNADO -> "Take shelter underground or in a sturdy room with no windows. Do not stay in the open.";
			case HURRICANE -> "Get indoors under a solid roof. Wind only affects things under open sky.";
			case TSUNAMI -> "Move inland and to high ground immediately - above the wave height.";
			case FLOOD -> "Move to high ground. Water fills valleys and low terrain first.";
		};
		return Component.literal("  " + text).withStyle(ChatFormatting.YELLOW);
	}

	/** Status line shown by detectors. */
	public static Component line(Disaster d, Vec3 viewer) {
		double dist = viewer.distanceTo(d.position());
		String when = d.isActive() ? "ACTIVE NOW" : "impact in " + d.secondsUntilImpact() + "s";
		return Component.literal(d.describe() + " - " + (int) dist + " blocks " + DisasterUtil.compass(viewer, d.position())
				+ " - " + when).withStyle(d.severity.color);
	}
}
