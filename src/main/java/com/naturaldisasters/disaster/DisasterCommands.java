package com.naturaldisasters.disaster;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Arrays;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permissions;

/**
 * /disaster start <type> [severity]   - start a disaster around you
 * /disaster stop                      - end every disaster and remove temporary water
 * /disaster list                      - show running disasters
 * /disaster natural <true|false>      - toggle natural random disasters
 * /disaster siren [seconds] [radius]  - sound every siren near you (test)
 */
public final class DisasterCommands {
	private DisasterCommands() {
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("disaster")
				.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
				.then(Commands.literal("start")
						.then(Commands.argument("type", StringArgumentType.word())
								.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
										Arrays.stream(DisasterType.values()).map(DisasterType::id), b))
								.executes(ctx -> start(ctx, Severity.STRONG))
								.then(Commands.argument("severity", StringArgumentType.word())
										.suggests((ctx, b) -> SharedSuggestionProvider.suggest(
												Arrays.stream(Severity.values()).map(Severity::id), b))
										.executes(ctx -> {
											Severity s = Severity.parse(StringArgumentType.getString(ctx, "severity"));
											if (s == null) {
												ctx.getSource().sendFailure(Component.literal(
														"Severity must be minor, moderate, strong or extreme."));
												return 0;
											}
											return start(ctx, s);
										}))))
				.then(Commands.literal("stop").executes(ctx -> {
					DisasterManager.stopAll();
					ctx.getSource().sendSuccess(() -> Component.literal("All disasters stopped."), true);
					return 1;
				}))
				.then(Commands.literal("list").executes(ctx -> {
					var list = DisasterManager.active();
					if (list.isEmpty()) {
						ctx.getSource().sendSuccess(() -> Component.literal("No active disasters."), false);
						return 0;
					}
					for (Disaster d : list) {
						ctx.getSource().sendSuccess(() -> Warnings.line(d, ctx.getSource().getPosition()), false);
					}
					return list.size();
				}))
				.then(Commands.literal("natural")
						.then(Commands.argument("enabled", BoolArgumentType.bool()).executes(ctx -> {
							boolean on = BoolArgumentType.getBool(ctx, "enabled");
							DisasterManager.setNaturalEnabled(on);
							ctx.getSource().sendSuccess(() -> Component.literal(
									"Natural disasters " + (on ? "enabled" : "disabled") + "."), true);
							return 1;
						})))
				.then(Commands.literal("siren")
						.executes(ctx -> siren(ctx, 20, 256))
						.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 300))
								.executes(ctx -> siren(ctx, IntegerArgumentType.getInteger(ctx, "seconds"), 256))
								.then(Commands.argument("radius", IntegerArgumentType.integer(8, 1024))
										.executes(ctx -> siren(ctx,
												IntegerArgumentType.getInteger(ctx, "seconds"),
												IntegerArgumentType.getInteger(ctx, "radius")))))));
	}

	private static int start(CommandContext<CommandSourceStack> ctx, Severity severity) {
		CommandSourceStack source = ctx.getSource();
		DisasterType type = DisasterType.parse(StringArgumentType.getString(ctx, "type"));
		if (type == null) {
			source.sendFailure(Component.literal("Unknown disaster. Types: earthquake, fissure, tornado, hurricane, tsunami, flood."));
			return 0;
		}
		Disaster d = DisasterManager.spawnNear(type, source.getLevel(), source.getPosition(), severity);
		source.sendSuccess(() -> Component.literal("Started " + d.describe() + " (warning: "
				+ d.secondsUntilImpact() + "s)."), true);
		return 1;
	}

	private static int siren(CommandContext<CommandSourceStack> ctx, int seconds, int radius) {
		CommandSourceStack source = ctx.getSource();
		DisasterManager.startTestAlarm(source.getLevel(), source.getPosition(), radius, seconds);
		source.sendSuccess(() -> Component.literal("Sirens within " + radius + " blocks will sound for " + seconds + "s."), true);
		return 1;
	}
}
