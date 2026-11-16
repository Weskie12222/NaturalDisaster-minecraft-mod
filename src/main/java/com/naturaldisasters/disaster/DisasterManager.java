package com.naturaldisasters.disaster;

import com.naturaldisasters.item.DetectorItem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Owns every running disaster. Handles natural spawning, detector alerts and the siren alarm query.
 * Disaster state is in memory only: it is cleaned up (temporary water removed) on server stop.
 */
public final class DisasterManager {
	private static final List<Disaster> ACTIVE = new ArrayList<>();
	private static boolean naturalEnabled = true;
	/** Average natural disaster interval per overworld, in seconds. */
	private static final int NATURAL_MEAN_SECONDS = 900;
	private static long tickCount;

	private DisasterManager() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(DisasterManager::tick);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Disaster d : ACTIVE) {
				d.cleanup();
			}
			ACTIVE.clear();
		});
	}

	// ------------------------------------------------------------------ tick

	private static void tick(MinecraftServer server) {
		tickCount++;

		Iterator<Disaster> it = new ArrayList<>(ACTIVE).iterator();
		while (it.hasNext()) {
			Disaster d = it.next();
			if (d.tick()) {
				ACTIVE.remove(d);
			}
		}

		TEST_ALARMS.removeIf(a -> --a.ticksLeft <= 0);

		if (tickCount % 40 == 0) {
			passiveDetectorAlerts(server);
		}
		if (naturalEnabled && tickCount % 20 == 0) {
			naturalSpawn(server);
		}
	}

	// ------------------------------------------------------------------ starting disasters

	/** Starts a disaster around {@code target} (used by commands and natural spawning). */
	public static Disaster spawnNear(DisasterType type, ServerLevel level, Vec3 target, Severity severity) {
		RandomSource r = level.getRandom();
		int warn = type.warningSeconds * 20;
		Disaster d;
		switch (type) {
			case EARTHQUAKE -> {
				Vec3 epicenter = offset(target, r, 10, 60);
				d = new EarthquakeDisaster(level, epicenter, severity);
			}
			case FISSURE -> d = new FissureDisaster(level, offset(target, r, 15, 40), severity, warn);
			case TORNADO -> {
				double ang = r.nextDouble() * Math.PI * 2;
				double dist = 90 + r.nextDouble() * 50;
				Vec3 start = new Vec3(target.x + Math.cos(ang) * dist, target.y, target.z + Math.sin(ang) * dist);
				double heading = Math.atan2(target.z - start.z, target.x - start.x) + (r.nextDouble() - 0.5) * 0.3;
				d = new TornadoDisaster(level, start, heading, severity, warn);
			}
			case HURRICANE -> {
				double ang = r.nextDouble() * Math.PI * 2;
				Vec3 start = new Vec3(target.x + Math.cos(ang) * 150, target.y, target.z + Math.sin(ang) * 150);
				double heading = Math.atan2(target.z - start.z, target.x - start.x);
				d = new HurricaneDisaster(level, start, heading, severity, warn);
			}
			case FLOOD -> d = new FloodDisaster(level, target, severity, warn);
			case TSUNAMI -> d = createTsunami(level, target, severity, warn);
			default -> throw new IllegalStateException();
		}
		register(d);
		return d;
	}

	/** Starts a disaster at an exact position (used by chained events, e.g. quake -> rift/tsunami). */
	public static void spawnLinked(DisasterType type, ServerLevel level, Vec3 at, Severity severity, int warnSeconds) {
		int warn = warnSeconds * 20;
		Disaster d = switch (type) {
			case FISSURE -> new FissureDisaster(level, at, severity, warn);
			case TORNADO -> new TornadoDisaster(level, at, level.getRandom().nextDouble() * Math.PI * 2, severity, warn);
			case TSUNAMI -> createTsunami(level, at, severity, warn);
			default -> null;
		};
		if (d != null) {
			register(d);
		}
	}

	private static Disaster createTsunami(ServerLevel level, Vec3 target, Severity severity, int warn) {
		RandomSource r = level.getRandom();
		Vec3 ocean = findOcean(level, target, 192);
		Vec3 start;
		Vec3 dir;
		if (ocean != null) {
			dir = new Vec3(target.x - ocean.x, 0, target.z - ocean.z).normalize();
			start = ocean.subtract(dir.scale(30));
		} else {
			// No ocean in range (e.g. testing inland): the wave simply comes from a random direction.
			double ang = r.nextDouble() * Math.PI * 2;
			dir = new Vec3(Math.cos(ang), 0, Math.sin(ang));
			start = target.subtract(dir.scale(110));
			start = new Vec3(start.x, target.y, start.z);
		}
		double distance = start.distanceTo(target) + 110 + 20 * severity.level;
		return new TsunamiDisaster(level, start, dir, distance, severity, warn);
	}

	private static void register(Disaster d) {
		ACTIVE.add(d);
		Warnings.broadcastForecast(d);
	}

	private static Vec3 offset(Vec3 base, RandomSource r, double min, double max) {
		double ang = r.nextDouble() * Math.PI * 2;
		double dist = min + r.nextDouble() * (max - min);
		return new Vec3(base.x + Math.cos(ang) * dist, base.y, base.z + Math.sin(ang) * dist);
	}

	// ------------------------------------------------------------------ ocean detection

	/** Finds the closest loaded sea-level water in 16 directions. */
	public static Vec3 findOcean(ServerLevel level, Vec3 from, int maxDist) {
		Vec3 best = null;
		double bestDist = Double.MAX_VALUE;
		int sea = level.getSeaLevel() - 1;
		for (int i = 0; i < 16; i++) {
			double ang = i * Math.PI / 8;
			for (int dist = 8; dist <= maxDist; dist += 8) {
				int x = (int) Math.floor(from.x + Math.cos(ang) * dist);
				int z = (int) Math.floor(from.z + Math.sin(ang) * dist);
				if (!DisasterUtil.loaded(level, x, z)) {
					break;
				}
				if (level.getBlockState(new BlockPos(x, sea, z)).is(Blocks.WATER)) {
					if (dist < bestDist) {
						bestDist = dist;
						best = new Vec3(x, sea + 1, z);
					}
					break;
				}
			}
		}
		return best;
	}

	public static boolean isCoastal(ServerLevel level, Vec3 at, int maxDist) {
		return findOcean(level, at, maxDist) != null;
	}

	// ------------------------------------------------------------------ natural spawning

	private static void naturalSpawn(MinecraftServer server) {
		ServerLevel overworld = server.getLevel(Level.OVERWORLD);
		if (overworld == null || overworld.players().isEmpty()) {
			return;
		}
		RandomSource r = overworld.getRandom();
		if (r.nextInt(NATURAL_MEAN_SECONDS) != 0) {
			return;
		}
		// Do not stack natural disasters on top of a serious one.
		for (Disaster d : ACTIVE) {
			if (d.severity.level >= Severity.STRONG.level) {
				return;
			}
		}
		ServerPlayer target = overworld.players().get(r.nextInt(overworld.players().size()));
		DisasterType[] pool = {
				DisasterType.EARTHQUAKE, DisasterType.EARTHQUAKE,
				DisasterType.TORNADO, DisasterType.TORNADO,
				DisasterType.FLOOD, DisasterType.FLOOD,
				DisasterType.HURRICANE,
				DisasterType.TSUNAMI,
				DisasterType.FISSURE
		};
		DisasterType type = pool[r.nextInt(pool.length)];
		if (type == DisasterType.TSUNAMI && !isCoastal(overworld, target.position(), 192)) {
			type = DisasterType.EARTHQUAKE;
		}
		spawnNear(type, overworld, target.position(), Severity.random(r));
	}

	public static boolean naturalEnabled() {
		return naturalEnabled;
	}

	public static void setNaturalEnabled(boolean enabled) {
		naturalEnabled = enabled;
	}

	// ------------------------------------------------------------------ queries

	public static List<Disaster> active() {
		return List.copyOf(ACTIVE);
	}

	public static void stopAll() {
		for (Disaster d : ACTIVE) {
			d.cleanup();
		}
		ACTIVE.clear();
		TEST_ALARMS.clear();
	}

	private static final class TestAlarm {
		final ServerLevel level;
		final Vec3 center;
		final double radius;
		int ticksLeft;

		TestAlarm(ServerLevel level, Vec3 center, double radius, int ticksLeft) {
			this.level = level;
			this.center = center;
			this.radius = radius;
			this.ticksLeft = ticksLeft;
		}
	}

	private static final List<TestAlarm> TEST_ALARMS = new ArrayList<>();

	/** Manual alarm for /disaster siren: sirens within {@code radius} blocks of {@code center} wail for {@code seconds}. */
	public static void startTestAlarm(ServerLevel level, Vec3 center, double radius, int seconds) {
		TEST_ALARMS.add(new TestAlarm(level, center, radius, seconds * 20));
	}

	/**
	 * Used by sirens: is there a siren-worthy disaster (or a manual test alarm) covering this position?
	 * @return the siren pitch to play (different disasters have different tones), or null if no alarm
	 */
	public static Float alarmPitch(ServerLevel level, BlockPos pos) {
		Vec3 p = Vec3.atCenterOf(pos);
		for (Disaster d : ACTIVE) {
			if (d.level == level && d.triggersSirens() && p.distanceTo(d.position()) <= d.warningRadius()) {
				return d.type.sirenPitch;
			}
		}
		for (TestAlarm a : TEST_ALARMS) {
			if (a.level == level && p.distanceTo(a.center) <= a.radius) {
				return 1.0f;
			}
		}
		return null;
	}

	// ------------------------------------------------------------------ detectors

	/** Right-click readout of a detector. */
	public static void sendReadout(ServerPlayer player, ServerLevel level, String deviceName, Set<DisasterType> types, int range) {
		List<Disaster> found = new ArrayList<>();
		for (Disaster d : ACTIVE) {
			if (d.level == level && types.contains(d.type)
					&& player.position().distanceTo(d.position()) <= range) {
				found.add(d);
			}
		}
		player.sendSystemMessage(Component.literal("[" + deviceName + "] range " + range + " blocks")
				.withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		if (found.isEmpty()) {
			player.sendSystemMessage(Component.literal("  All clear - no activity detected.").withStyle(ChatFormatting.GREEN));
			return;
		}
		for (Disaster d : found) {
			player.sendSystemMessage(Component.literal("  ").append(Warnings.line(d, player.position())));
		}
	}

	/** Every 2 seconds: players carrying a detector get an action-bar alert for the worst matching disaster. */
	private static void passiveDetectorAlerts(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (ServerPlayer player : level.players()) {
				Disaster worst = null;
				for (Disaster d : ACTIVE) {
					if (d.level != level) {
						continue;
					}
					int range = detectorRangeFor(player, d.type);
					if (range <= 0 || player.position().distanceTo(d.position()) > range) {
						continue;
					}
					if (worst == null || d.severity.level > worst.severity.level) {
						worst = d;
					}
				}
				if (worst != null) {
					Component msg = Component.literal("⚠ ").withStyle(worst.severity.color)
							.append(Warnings.line(worst, player.position()));
					player.sendSystemMessage(msg, true);
				}
			}
		}
	}

	/** Best detector range the player carries for this disaster type, or 0 if none. */
	private static int detectorRangeFor(ServerPlayer player, DisasterType type) {
		int best = 0;
		Container inv = player.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack stack = inv.getItem(i);
			if (stack.getItem() instanceof DetectorItem det && det.detects(type)) {
				best = Math.max(best, det.range());
			}
		}
		return best;
	}
}
