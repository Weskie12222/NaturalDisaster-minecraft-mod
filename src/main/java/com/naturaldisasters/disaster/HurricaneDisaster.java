package com.naturaldisasters.disaster;

import com.naturaldisasters.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A huge slow storm: thunderstorm weather, constant gale-force wind on anything under open sky,
 * flying debris (leaves, glass, torches), lightning, and at higher strengths tornadoes spawning inside it.
 */
public class HurricaneDisaster extends Disaster {
	private final double radius;
	private final double windX;
	private final double windZ;
	private final double drift;
	private final double heading;

	public HurricaneDisaster(ServerLevel level, Vec3 center, double headingRadians, Severity severity, int warnTicks) {
		super(DisasterType.HURRICANE, severity, level, center, warnTicks);
		this.radius = 90.0 + 30.0 * severity.level;
		this.heading = headingRadians;
		this.windX = Math.cos(headingRadians + 0.6);
		this.windZ = Math.sin(headingRadians + 0.6);
		this.drift = 0.04;
	}

	@Override
	public double warningRadius() {
		return radius + 160.0;
	}

	@Override
	protected int activeTicks() {
		return (60 + 30 * severity.level) * 20;
	}

	@Override
	protected void onStart() {
		Weather.set(level, "thunder", activeTicks() / 20 + 30);
		Warnings.broadcastImpact(this, "The hurricane is making landfall! Seek shelter!");
	}

	@Override
	protected void tickActive(int t) {
		pos = new Vec3(pos.x + Math.cos(heading) * drift, pos.y, pos.z + Math.sin(heading) * drift);

		if (t % 40 == 0) {
			for (ServerPlayer p : level.players()) {
				if (p.position().distanceToSqr(pos) < radius * radius) {
					level.playSound(null, p.getX(), p.getY(), p.getZ(), ModSounds.ROAR, SoundSource.WEATHER,
							1.5f + 0.5f * severity.level, 0.5f + random.nextFloat() * 0.2f);
				}
			}
		}

		windOnEntities();
		debris(t);

		if (severity.level >= Severity.STRONG.level && t % 20 == 0) {
			lightning();
		}
		// Extreme hurricanes spawn short-lived tornadoes inside the storm.
		if (severity == Severity.EXTREME && t > 200 && t % 600 == 0) {
			spawnEmbeddedTornado();
		}
	}

	private void windOnEntities() {
		AABB box = new AABB(pos.x - radius, level.getMinY(), pos.z - radius,
				pos.x + radius, level.getMaxY(), pos.z + radius);
		double gust = 0.012 + 0.012 * severity.level;
		gust *= 0.7 + 0.6 * random.nextDouble();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box)) {
			if (!DisasterUtil.isVulnerable(e) || e.isInWater()) {
				continue;
			}
			BlockPos bp = e.blockPosition();
			if (!level.canSeeSky(bp)) {
				continue; // sheltered
			}
			DisasterUtil.push(e, windX * gust, e.onGround() ? 0.0 : 0.002, windZ * gust);
			if (severity == Severity.EXTREME && e.onGround() && random.nextInt(40) == 0) {
				DisasterUtil.push(e, windX * 0.5, 0.35, windZ * 0.5); // knocked off your feet
			}
		}
	}

	private void debris(int t) {
		if (t % 2 != 0) {
			return;
		}
		float maxHardness = DisasterUtil.maxHardness(severity) * 0.6f;
		int attempts = 2 + severity.level;
		for (int i = 0; i < attempts; i++) {
			double ang = random.nextDouble() * Math.PI * 2;
			double dist = Math.sqrt(random.nextDouble()) * radius;
			int x = (int) Math.floor(pos.x + Math.cos(ang) * dist);
			int z = (int) Math.floor(pos.z + Math.sin(ang) * dist);
			if (!DisasterUtil.loaded(level, x, z)) {
				continue;
			}
			BlockPos bp = new BlockPos(x, DisasterUtil.surfaceY(level, x, z) - 1, z);
			BlockState state = level.getBlockState(bp);
			if (DisasterUtil.destructible(level, bp, state, maxHardness)) {
				DisasterUtil.dust(level, bp, state, 4);
				level.destroyBlock(bp, false);
			}
		}
		// Wind streaks near players.
		for (ServerPlayer p : level.players()) {
			if (p.position().distanceToSqr(pos) < radius * radius) {
				level.sendParticles(ParticleTypes.CLOUD, p.getX() - windX * 6, p.getY() + 1.5, p.getZ() - windZ * 6,
						3, 3, 2, 3, 0.4);
			}
		}
	}

	private void lightning() {
		if (level.players().isEmpty()) {
			return;
		}
		ServerPlayer p = level.players().get(random.nextInt(level.players().size()));
		if (p.position().distanceToSqr(pos) > radius * radius || random.nextInt(6) != 0) {
			return;
		}
		int x = (int) (p.getX() + (random.nextDouble() - 0.5) * 70);
		int z = (int) (p.getZ() + (random.nextDouble() - 0.5) * 70);
		if (!DisasterUtil.loaded(level, x, z)) {
			return;
		}
		int y = DisasterUtil.surfaceY(level, x, z);
		Weather.run(level, "summon minecraft:lightning_bolt " + x + " " + y + " " + z);
	}

	private void spawnEmbeddedTornado() {
		double ang = random.nextDouble() * Math.PI * 2;
		double dist = 20 + random.nextDouble() * (radius - 20);
		Vec3 at = new Vec3(pos.x + Math.cos(ang) * dist, pos.y, pos.z + Math.sin(ang) * dist);
		DisasterManager.spawnLinked(DisasterType.TORNADO, level, at, Severity.MODERATE, 0);
	}

	@Override
	protected void onEnd() {
		Warnings.broadcastEnd(this);
	}
}
