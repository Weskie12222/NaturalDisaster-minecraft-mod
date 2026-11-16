package com.naturaldisasters.disaster;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Heavy rain and rising water. Water climbs one layer at a time across a disc around the center,
 * flooding valleys and low ground (hills stay dry), then recedes layer by layer.
 */
public class FloodDisaster extends Disaster {
	private static final int LAYER_INTERVAL = 90; // ticks between rising/receding by one block
	private static final int COLUMNS_PER_TICK = 1500;

	private record Column(int x, int z, int base) {
	}

	private final int radius;
	private final int maxRise;
	private int datumY;
	private final List<Column> columns = new ArrayList<>();
	private final Deque<List<BlockPos>> layers = new ArrayDeque<>();

	// Layer under construction.
	private int currentRise = 0;
	private int cursor = 0;
	private List<BlockPos> building;
	private boolean receding;

	public FloodDisaster(ServerLevel level, Vec3 center, Severity severity, int warnTicks) {
		super(DisasterType.FLOOD, severity, level, center, warnTicks);
		this.radius = 20 + 11 * severity.level;
		this.maxRise = 1 + severity.level; // 2..5 blocks
	}

	@Override
	protected int activeTicks() {
		return LAYER_INTERVAL * maxRise * 2 + 20 * (20 + 15 * severity.level);
	}

	@Override
	protected void onStart() {
		Weather.set(level, "rain", activeTicks() / 20);
		int cx = (int) Math.floor(pos.x);
		int cz = (int) Math.floor(pos.z);
		datumY = DisasterUtil.surfaceY(level, cx, cz) - 1;
		for (int dx = -radius; dx <= radius; dx++) {
			for (int dz = -radius; dz <= radius; dz++) {
				if (dx * dx + dz * dz > radius * radius) {
					continue;
				}
				int x = cx + dx;
				int z = cz + dz;
				if (DisasterUtil.loaded(level, x, z)) {
					columns.add(new Column(x, z, DisasterUtil.surfaceY(level, x, z)));
				}
			}
		}
		Warnings.broadcastImpact(this, "Floodwaters are rising!");
	}

	@Override
	protected void tickActive(int t) {
		int riseTicks = LAYER_INTERVAL * maxRise;
		int holdEnd = riseTicks + 20 * (20 + 15 * severity.level);

		if (t < riseTicks) {
			advanceBuilding(t);
		} else if (t >= holdEnd && !layers.isEmpty()) {
			receding = true;
			if ((t - holdEnd) % LAYER_INTERVAL == 0) {
				removeTopLayer();
			}
		}
	}

	private void advanceBuilding(int t) {
		if (building == null) {
			if (t % LAYER_INTERVAL != 0 || currentRise >= maxRise) {
				return;
			}
			currentRise++;
			cursor = 0;
			building = new ArrayList<>();
		}
		int end = Math.min(columns.size(), cursor + COLUMNS_PER_TICK);
		int y = datumY + currentRise;
		for (; cursor < end; cursor++) {
			Column c = columns.get(cursor);
			if (c.base() > y) {
				continue; // this ground is above the water line: stays dry
			}
			BlockPos bp = new BlockPos(c.x(), y, c.z());
			BlockState state = level.getBlockState(bp);
			if (DisasterUtil.replaceable(state) && !state.is(Blocks.WATER)) {
				level.setBlock(bp, Blocks.WATER.defaultBlockState(), 2);
				building.add(bp);
			}
		}
		if (cursor >= columns.size()) {
			layers.addLast(building);
			building = null;
		}
	}

	private void removeTopLayer() {
		List<BlockPos> top = layers.pollLast();
		if (top == null) {
			return;
		}
		for (BlockPos bp : top) {
			if (level.getBlockState(bp).is(Blocks.WATER)) {
				level.setBlock(bp, Blocks.AIR.defaultBlockState(), 2);
			}
		}
	}

	@Override
	public void cleanup() {
		if (building != null) {
			layers.addLast(building);
			building = null;
		}
		while (!layers.isEmpty()) {
			removeTopLayer();
		}
	}

	@Override
	protected void onEnd() {
		Warnings.broadcastEnd(this);
	}
}
