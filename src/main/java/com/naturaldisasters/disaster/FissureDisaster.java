package com.naturaldisasters.disaster;

import com.naturaldisasters.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A long crack tears open in the ground. For each column the rift looks for a cave below the surface:
 * if there is one, the crack is dug all the way down into it (so the world "finds" caves); if not,
 * it only leaves a shallow V-shaped scar.
 */
public class FissureDisaster extends Disaster {
	private final double dirX;
	private final double dirZ;
	private final int length;
	private final int halfWidth;
	private final int maxSearchDepth;
	private final int shallowDepth;
	private final Vec3 start;
	private int carvedSteps;

	public FissureDisaster(ServerLevel level, Vec3 center, Severity severity, int warnTicks) {
		super(DisasterType.FISSURE, severity, level, center, warnTicks);
		double ang = random.nextDouble() * Math.PI * 2;
		this.dirX = Math.cos(ang);
		this.dirZ = Math.sin(ang);
		this.length = 32 + 28 * severity.level;
		this.halfWidth = 1 + severity.level / 2;
		this.maxSearchDepth = 28 + 14 * severity.level;
		this.shallowDepth = 6 + 3 * severity.level;
		this.start = new Vec3(center.x - dirX * length / 2.0, center.y, center.z - dirZ * length / 2.0);
	}

	@Override
	protected int activeTicks() {
		// Two blocks of crack per tick.
		return length / 2 + 40;
	}

	@Override
	protected void onStart() {
		Warnings.broadcastImpact(this, "The ground splits open!");
	}

	@Override
	protected void tickActive(int t) {
		if (carvedSteps >= length) {
			return;
		}
		for (int i = 0; i < 2 && carvedSteps < length; i++) {
			carveSlice(carvedSteps++);
		}
		if (t % 12 == 0) {
			double mx = start.x + dirX * carvedSteps;
			double mz = start.z + dirZ * carvedSteps;
			level.playSound(null, mx, level.getSeaLevel(), mz, ModSounds.RUMBLE, SoundSource.AMBIENT, 3.0f, 0.6f);
		}
	}

	private void carveSlice(int step) {
		double cx = start.x + dirX * step + (random.nextDouble() - 0.5) * 0.8;
		double cz = start.z + dirZ * step + (random.nextDouble() - 0.5) * 0.8;
		// Perpendicular axis for the crack width.
		double px = -dirZ;
		double pz = dirX;
		// The crack narrows toward both ends.
		double taper = Math.min(1.0, Math.min(step, length - step) / 8.0);
		int w = Math.max(0, (int) Math.round(halfWidth * (0.4 + 0.6 * taper)));

		for (int lateral = -w; lateral <= w; lateral++) {
			int x = (int) Math.floor(cx + px * lateral);
			int z = (int) Math.floor(cz + pz * lateral);
			if (!DisasterUtil.loaded(level, x, z)) {
				continue;
			}
			carveColumn(x, z, w == 0 ? 0 : Math.abs(lateral) / (double) w);
		}
	}

	/** @param edge 0 at the crack's center line, 1 at its edge (edges are shallower). */
	private void carveColumn(int x, int z, double edge) {
		int surface = DisasterUtil.surfaceY(level, x, z) - 1;
		int minY = level.getMinY() + 6;

		int caveY = findCaveBelow(x, z, surface, minY);
		int bottom;
		if (caveY != Integer.MIN_VALUE) {
			// Open into the cave: dig down to its floor level so the opening is walkable/fallable.
			bottom = caveY;
		} else {
			bottom = surface - (int) Math.max(2, shallowDepth * (1.0 - edge * 0.6));
		}
		bottom = Math.max(bottom, minY);

		for (int y = surface; y >= bottom; y--) {
			BlockPos bp = new BlockPos(x, y, z);
			BlockState state = level.getBlockState(bp);
			if (state.isAir() || state.is(Blocks.BEDROCK) || state.hasBlockEntity()) {
				continue;
			}
			float hardness = state.getDestroySpeed(level, bp);
			if (hardness < 0) {
				continue;
			}
			if (y >= surface - 1 && random.nextInt(4) == 0) {
				DisasterUtil.dust(level, bp, state, 3);
			}
			level.setBlock(bp, Blocks.AIR.defaultBlockState(), 3);
		}
	}

	/**
	 * Scans down from the surface for an underground air pocket (a cave).
	 * @return Y of the first cave air block, or {@link Integer#MIN_VALUE} if none within range.
	 */
	private int findCaveBelow(int x, int z, int surface, int minY) {
		boolean seenSolid = false;
		int limit = Math.max(minY, surface - maxSearchDepth);
		for (int y = surface; y >= limit; y--) {
			BlockState s = level.getBlockState(new BlockPos(x, y, z));
			if (s.isAir() || !s.getFluidState().isEmpty()) {
				if (seenSolid && surface - y >= 4) {
					return y - 1;
				}
			} else {
				seenSolid = true;
			}
		}
		return Integer.MIN_VALUE;
	}

	@Override
	protected void onEnd() {
		Warnings.broadcastEnd(this);
	}
}
