package com.naturaldisasters.disaster;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * Weather and summoning go through vanilla commands (run as the server) so this code does not depend
 * on internal weather APIs, which change between Minecraft versions.
 */
public final class Weather {
	private Weather() {
	}

	/** @param kind "rain", "thunder" or "clear" */
	public static void set(ServerLevel level, String kind, int seconds) {
		run(level, "weather " + kind + " " + seconds + "s");
	}

	public static void run(ServerLevel level, String command) {
		MinecraftServer server = level.getServer();
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(level), command);
	}
}
