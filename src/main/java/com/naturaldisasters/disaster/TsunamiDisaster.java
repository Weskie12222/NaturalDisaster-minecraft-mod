package com.naturaldisasters.disaster;

import com.naturaldisasters.registry.ModSounds;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A wall of water travels in a straight line. The water is made of temporary source blocks that are
 * removed again a few seconds later, so the terrain recovers (blocks the wave smashed stay smashed).
 */
public class TsunamiDisaster extends Disaster {
	/** Max water blocks placed per tick, to protect server TPS on extreme waves. */
	private static final int PLACE_BUDGET = 1800;
	private static final int WATER_LIFETIME = 50;

	private record Cell(BlockPos pos, int expiresAt) {
	}

	private final Vec3 start;
	private final Vec3 dir;
	private final double lateralHalf;
	private final double waveHeight;
	private final double speed; // blocks per tick
	private final double totalDistance;

	private double travelled;
	private int nextSlice;
	private int tickCounter;
	private final Set<Long> visited = new HashSet<>();
	private final Map<Long, Integer> baseCache = new HashMap<>();
	private final ArrayDeque<Cell> cells = new ArrayDeque<>();

	public TsunamiDisaster(ServerLevel level, Vec3 start, Vec3 direction, double distance, Severity severity, int warnTicks) {
		super(DisasterType.TSUNAMI, severity, level, start, warnTicks);
		this.start = start;
		this.dir = new Vec3(direction.x, 0, direction.z).normalize();
		this.totalDistance = distance;
		this.lateralHalf = 22.0 + 14.0 * severity.level;
		this.waveHeight = 2.0 + 3.0 * severity.level;
		this.speed = 0.35 + 0.07 * severity.level;
	}

	@Override
	protected int activeTicks() {
		return (int) (totalDistance / speed) + WATER_LIFETIME + 20;
	}

	@Override
	protected void onStart() {
		Warnings.broadcastImpact(this, "The tsunami wave is arriving - get to high ground!");
	}

	@Override
	protected void tickActive(int t) {
		tickCounter = t;
		if (travelled < totalDistance) {
			travelled += speed;
			pos = start.add(dir.scale(travelled));
			int budget = PLACE_BUDGET;
			while (nextSlice <= travelled && budget > 0) {
				budget -= placeSlice(nextSlice++);
			}
			if (t % 25 == 0) {
				level.playSound(null, pos.x, level.getSeaLevel() + 2, pos.z, ModSounds.WAVE, SoundSource.AMBIENT,
						4.0f + severity.level, 0.55f);
			}
		}
		expireWater(false);
		pushEntities();
	}

	/** Places one cross-section of the wave. @return number of blocks placed. */
	private int placeSlice(int slice) {
		double fx = start.x + dir.x * slice;
		double fz = start.z + dir.z * slice;
		double nx = -dir.z;
		double nz = dir.x;

		// Wave shape: ramps up, then loses height as it runs inland.
		double progress = slice / totalDistance;
		double ramp = Math.min(1.0, slice / 14.0);
		double h = waveHeight * ramp * Math.pow(Math.max(0.0, 1.0 - progress), 0.6);
		int hInt = (int) Math.round(h);
		if (hInt <= 0) {
			return 0;
		}

		int placed = 0;
		int half = (int) lateralHalf;
		float breakHardness = DisasterUtil.maxHardness(severity);
		for (int j = -half; j <= half; j++) {
			// Wave is a bit lower at its far edges.
			double edge = 1.0 - 0.45 * Math.pow(Math.abs(j) / (double) Math.max(1, half), 2);
			int colHeight = Math.max(1, (int) Math.round(hInt * edge));

			int x = (int) Math.floor(fx + nx * j);
			int z = (int) Math.floor(fz + nz * j);
			long key = BlockPos.asLong(x, 0, z);
			if (!visited.add(key) || !DisasterUtil.loaded(level, x, z)) {
				continue;
			}
			int base = baseCache.computeIfAbsent(key, k -> DisasterUtil.surfaceY(level, x, z));
			for (int dy = 0; dy < colHeight; dy++) {
				BlockPos bp = new BlockPos(x, base + dy, z);
				BlockState state = level.getBlockState(bp);
				if (state.is(Blocks.WATER) && state.getFluidState().isSource()) {
					continue;
				}
				if (!DisasterUtil.replaceable(state)) {
					if (DisasterUtil.destructible(level, bp, state, breakHardness)) {
						level.destroyBlock(bp, false);
					} else {
						break; // solid obstacle: the wave goes around/over it
					}
				}
				level.setBlock(bp, Blocks.WATER.defaultBlockState(), 2);
				cells.add(new Cell(bp, tickCounter + WATER_LIFETIME));
				placed++;
			}
		}
		return placed;
	}

	private void expireWater(boolean all) {
		int removed = 0;
		while (!cells.isEmpty() && (all || cells.peekFirst().expiresAt() <= tickCounter) && (all || removed < 3000)) {
			Cell c = cells.pollFirst();
			if (level.getBlockState(c.pos()).is(Blocks.WATER)) {
				level.setBlock(c.pos(), Blocks.AIR.defaultBlockState(), 2);
			}
			removed++;
		}
	}

	private void pushEntities() {
		double reach = lateralHalf + 4;
		AABB box = new AABB(pos.x - reach, level.getSeaLevel() - 8, pos.z - reach,
				pos.x + reach, level.getSeaLevel() + 40, pos.z + reach);
		double push = 0.05 + 0.03 * severity.level;
		for (Entity e : level.getEntitiesOfClass(Entity.class, box)) {
			if (!DisasterUtil.isVulnerable(e) || !e.isInWater()) {
				continue;
			}
			double along = (e.getX() - pos.x) * dir.x + (e.getZ() - pos.z) * dir.z;
			// Only things in or just behind the front are shoved inland.
			if (along < -WATER_LIFETIME * speed || along > 6) {
				continue;
			}
			DisasterUtil.push(e, dir.x * push, 0.0, dir.z * push);
		}
	}

	@Override
	public void cleanup() {
		expireWater(true);
	}

	@Override
	protected void onEnd() {
		Warnings.broadcastEnd(this);
	}
}
