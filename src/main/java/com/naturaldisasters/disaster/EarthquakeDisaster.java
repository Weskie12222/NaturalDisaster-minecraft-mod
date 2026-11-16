package com.naturaldisasters.disaster;

import com.naturaldisasters.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The ground shakes: players and mobs are jolted around, fragile blocks break, and strong quakes
 * can tear a ground rift and (near the coast) trigger a tsunami.
 */
public class EarthquakeDisaster extends Disaster {
	private final double radius;
	private final double magnitude;

	public EarthquakeDisaster(ServerLevel level, Vec3 epicenter, Severity severity) {
		super(DisasterType.EARTHQUAKE, severity, level, epicenter, DisasterType.EARTHQUAKE.warningSeconds * 20);
		this.radius = 40.0 + 40.0 * severity.level;
		// Richter-style number purely for display: 3.x minor ... 8.x extreme.
		this.magnitude = 3.0 + severity.level * 1.2 + random.nextDouble() * 0.9;
	}

	public double magnitude() {
		return magnitude;
	}

	@Override
	public String describe() {
		return severity.label + " Earthquake (M" + String.format("%.1f", magnitude) + ")";
	}

	@Override
	protected int activeTicks() {
		return (8 + 4 * severity.level) * 20;
	}

	@Override
	protected void onStart() {
		Warnings.broadcastImpact(this, "The ground begins to shake!");
	}

	@Override
	protected void tickActive(int t) {
		AABB box = new AABB(pos.x - radius, level.getMinY(), pos.z - radius,
				pos.x + radius, level.getMaxY(), pos.z + radius);

		// Rumble sound at every nearby player.
		if (t % 16 == 0) {
			for (ServerPlayer p : level.players()) {
				if (p.position().distanceToSqr(pos) < radius * radius * 4) {
					level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.RUMBLE, SoundSource.AMBIENT,
							1.0f + 0.4f * severity.level, 0.8f + random.nextFloat() * 0.3f);
				}
			}
		}

		// Jolt everything standing on the ground.
		if (t % 4 == 0) {
			double power = 0.05 + 0.045 * severity.level;
			for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
				if (!e.onGround() || !DisasterUtil.isVulnerable(e)) {
					continue;
				}
				// Stronger the closer to the epicenter.
				double falloff = 1.0 - Math.min(1.0, e.position().distanceTo(pos) / (radius * 1.2));
				if (falloff <= 0) {
					continue;
				}
				DisasterUtil.push(e,
						(random.nextDouble() - 0.5) * 2 * power * falloff,
						0.12 * falloff + 0.03 * severity.level * falloff,
						(random.nextDouble() - 0.5) * 2 * power * falloff);
			}
		}

		// Break exposed blocks near the surface.
		int attempts = 3 * severity.level;
		float maxHardness = DisasterUtil.maxHardness(severity);
		for (int i = 0; i < attempts; i++) {
			double ang = random.nextDouble() * Math.PI * 2;
			double dist = Math.sqrt(random.nextDouble()) * radius;
			int x = (int) Math.floor(pos.x + Math.cos(ang) * dist);
			int z = (int) Math.floor(pos.z + Math.sin(ang) * dist);
			if (!DisasterUtil.loaded(level, x, z)) {
				continue;
			}
			int y = DisasterUtil.surfaceY(level, x, z) - 1 - random.nextInt(3);
			BlockPos bp = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(bp);
			if (DisasterUtil.destructible(level, bp, state, maxHardness)) {
				DisasterUtil.dust(level, bp, state, 6);
				level.destroyBlock(bp, false);
			}
		}

	}

	@Override
	protected void onEnd() {
		Warnings.broadcastEnd(this);
		// Strong quakes can leave a rift behind.
		boolean rift = severity == Severity.EXTREME || (severity == Severity.STRONG && random.nextFloat() < 0.5f);
		if (rift) {
			DisasterManager.spawnLinked(DisasterType.FISSURE, level, pos, severity, 0);
		}
		// A strong offshore quake sends a tsunami toward the coast.
		if (severity.level >= Severity.STRONG.level && DisasterManager.isCoastal(level, pos, 96)) {
			DisasterManager.spawnLinked(DisasterType.TSUNAMI, level, pos, severity, 45);
		}
	}
}
