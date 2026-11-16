package com.naturaldisasters.disaster;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Shared helpers used by all disasters. */
public final class DisasterUtil {
	private DisasterUtil() {
	}

	/** Y of the first free block above the terrain (water surface counts as terrain). */
	public static int surfaceY(ServerLevel level, int x, int z) {
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
	}

	public static boolean loaded(ServerLevel level, int x, int z) {
		return level.hasChunkAt(new BlockPos(x, level.getSeaLevel(), z));
	}

	/**
	 * Hardness limit for what a disaster of the given severity can wreck.
	 * minor ~ plants/torches, moderate ~ glass/dirt/leaves, strong ~ stone/wood slabs, extreme ~ planks/logs/bricks.
	 */
	public static float maxHardness(Severity severity) {
		return 0.15f + 0.9f * (severity.level - 1);
	}

	/** True if the block can be destroyed by a disaster of this strength. Never touches block entities (chests etc.). */
	public static boolean destructible(ServerLevel level, BlockPos pos, BlockState state, float maxHardness) {
		if (state.isAir() || state.hasBlockEntity() || !state.getFluidState().isEmpty()) {
			return false;
		}
		float hardness = state.getDestroySpeed(level, pos);
		return hardness >= 0.0f && hardness <= maxHardness && !state.is(Blocks.BEDROCK);
	}

	public static boolean replaceable(BlockState state) {
		return state.isAir() || state.canBeReplaced();
	}

	/** Gives an entity a velocity change and makes sure players actually receive it. */
	public static void push(Entity entity, double dx, double dy, double dz) {
		entity.setDeltaMovement(entity.getDeltaMovement().add(dx, dy, dz));
		entity.hurtMarked = true;
	}

	public static void setMotion(Entity entity, Vec3 motion) {
		entity.setDeltaMovement(motion);
		entity.hurtMarked = true;
	}

	public static boolean isVulnerable(Entity entity) {
		if (entity instanceof ServerPlayer player) {
			return !player.isCreative() && !player.isSpectator();
		}
		return true;
	}

	public static void dust(ServerLevel level, BlockPos pos, BlockState state, int count) {
		if (state.isAir()) {
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
				pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, count, 0.4, 0.3, 0.4, 0.05);
	}

	public static String compass(Vec3 from, Vec3 to) {
		double dx = to.x - from.x;
		double dz = to.z - from.z;
		// +X is east, +Z is south in Minecraft.
		double deg = Math.toDegrees(Math.atan2(dz, dx));
		if (deg < 0) {
			deg += 360.0;
		}
		String[] names = {"E", "SE", "S", "SW", "W", "NW", "N", "NE"};
		return names[(int) Math.round(deg / 45.0) % 8];
	}
}
