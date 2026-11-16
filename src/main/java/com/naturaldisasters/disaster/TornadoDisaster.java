package com.naturaldisasters.disaster;

import com.naturaldisasters.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A moving funnel. It sucks entities in and flings them upward, rips loose blocks off the ground
 * (as falling blocks that land somewhere else) and wanders across the landscape.
 */
public class TornadoDisaster extends Disaster {
	private final double radius;
	private final double height;
	private double heading; // radians
	private final double speed; // blocks per tick
	private double spin;

	public TornadoDisaster(ServerLevel level, Vec3 start, double headingRadians, Severity severity, int warnTicks) {
		super(DisasterType.TORNADO, severity, level, start, warnTicks);
		this.heading = headingRadians;
		this.radius = 3.0 + 2.2 * severity.level;
		this.height = 26.0 + 12.0 * severity.level;
		this.speed = 0.12 + 0.04 * severity.level;
	}

	/** Rough Enhanced-Fujita-style rating for the readout. */
	@Override
	public String describe() {
		String[] ef = {"EF0-1", "EF2", "EF3-4", "EF5"};
		return severity.label + " Tornado (" + ef[severity.level - 1] + ")";
	}

	@Override
	protected int activeTicks() {
		return (40 + 20 * severity.level) * 20;
	}

	@Override
	protected void onStart() {
		Warnings.broadcastImpact(this, "A tornado has touched down!");
	}

	@Override
	protected void tickActive(int t) {
		move();
		spin += 0.45;

		funnelParticles();
		if (t % 30 == 0) {
			level.playSound(null, pos.x, pos.y + 6, pos.z, ModSounds.ROAR, SoundSource.WEATHER,
					3.0f + severity.level, 0.7f + random.nextFloat() * 0.2f);
		}
		if (t % 2 == 0) {
			ripBlocks();
		}
		affectEntities(t);
	}

	private void move() {
		heading += (random.nextDouble() - 0.5) * 0.10;
		double nx = pos.x + Math.cos(heading) * speed;
		double nz = pos.z + Math.sin(heading) * speed;
		if (!DisasterUtil.loaded(level, (int) Math.floor(nx), (int) Math.floor(nz))) {
			// Turn around instead of leaving the loaded area.
			heading += Math.PI;
			return;
		}
		double ny = DisasterUtil.surfaceY(level, (int) Math.floor(nx), (int) Math.floor(nz));
		pos = new Vec3(nx, ny, nz);
	}

	private void funnelParticles() {
		// A handful of rings per tick, rotating, wider toward the top. (Particles render within ~32 blocks.)
		int rings = 6 + 2 * severity.level;
		for (int i = 0; i < rings; i++) {
			double h = random.nextDouble() * height;
			double r = 0.8 + (radius * 1.4) * (h / height);
			double a = spin + h * 0.35 + random.nextDouble() * 0.6;
			double x = pos.x + Math.cos(a) * r;
			double z = pos.z + Math.sin(a) * r;
			level.sendParticles(ParticleTypes.CLOUD, x, pos.y + h, z, 1, 0.1, 0.1, 0.1, 0.01);
			if (i % 3 == 0) {
				level.sendParticles(ParticleTypes.LARGE_SMOKE, x, pos.y + h, z, 1, 0.2, 0.2, 0.2, 0.0);
			}
		}
	}

	private void ripBlocks() {
		int attempts = 2 + 2 * severity.level;
		float maxHardness = DisasterUtil.maxHardness(severity);
		for (int i = 0; i < attempts; i++) {
			double ang = random.nextDouble() * Math.PI * 2;
			double dist = Math.sqrt(random.nextDouble()) * radius * 1.3;
			int x = (int) Math.floor(pos.x + Math.cos(ang) * dist);
			int z = (int) Math.floor(pos.z + Math.sin(ang) * dist);
			if (!DisasterUtil.loaded(level, x, z)) {
				continue;
			}
			// Look at the top few blocks of the terrain/buildings in this column.
			int top = DisasterUtil.surfaceY(level, x, z) - 1;
			BlockPos bp = new BlockPos(x, top - random.nextInt(2), z);
			BlockState state = level.getBlockState(bp);
			if (!DisasterUtil.destructible(level, bp, state, maxHardness)) {
				continue;
			}
			FallingBlockEntity flying = FallingBlockEntity.fall(level, bp, state);
			double tangent = ang + Math.PI / 2;
			DisasterUtil.setMotion(flying, new Vec3(
					Math.cos(tangent) * 0.5 + (random.nextDouble() - 0.5) * 0.2,
					0.7 + random.nextDouble() * 0.6,
					Math.sin(tangent) * 0.5 + (random.nextDouble() - 0.5) * 0.2));
		}
	}

	private void affectEntities(int t) {
		double influence = radius * 2.4;
		AABB box = new AABB(pos.x - influence, pos.y - 4, pos.z - influence,
				pos.x + influence, pos.y + height, pos.z + influence);
		double pull = 0.025 + 0.015 * severity.level;
		double lift = 0.05 + 0.035 * severity.level;
		double spinPower = 0.05 + 0.03 * severity.level;

		for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
			if (!DisasterUtil.isVulnerable(e)) {
				continue;
			}
			double dx = pos.x - e.getX();
			double dz = pos.z - e.getZ();
			double dist = Math.sqrt(dx * dx + dz * dz);
			if (dist > influence || dist < 0.01) {
				continue;
			}
			double nx = dx / dist;
			double nz = dz / dist;
			double closeness = 1.0 - dist / influence;

			Vec3 motion = e.getDeltaMovement();
			double mx = motion.x + (nx * pull + -nz * spinPower) * (0.4 + closeness);
			double mz = motion.z + (nz * pull + nx * spinPower) * (0.4 + closeness);
			double my = dist < radius * 1.5 ? Math.min(motion.y + lift, 0.9 + 0.1 * severity.level) : motion.y;
			DisasterUtil.setMotion(e, new Vec3(mx, my, mz));
			e.fallDistance = 0; // the tornado carries you; the landing is what hurts.

			if (t % 10 == 0 && dist < radius && e instanceof LivingEntity living) {
				living.hurtServer(level, level.damageSources().generic(), 1.0f + severity.level);
			}
		}
	}

	@Override
	protected void onEnd() {
		Warnings.broadcastEnd(this);
	}
}
