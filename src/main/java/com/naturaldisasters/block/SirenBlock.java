package com.naturaldisasters.block;

import com.mojang.serialization.MapCodec;
import com.naturaldisasters.disaster.DisasterManager;
import com.naturaldisasters.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * Warning siren. It wails when a Strong/Extreme disaster (Moderate for tsunamis) is forecast or active
 * within range, and also whenever it receives a redstone signal. The block polls itself with scheduled
 * ticks, which are saved with the chunk, so no block entity or registry of sirens is needed.
 */
public class SirenBlock extends Block {
	public static final MapCodec<SirenBlock> CODEC = simpleCodec(SirenBlock::new);
	public static final BooleanProperty LIT = BlockStateProperties.LIT;

	private static final int POLL_TICKS = 10;
	/** The horn sample is ~4 seconds; start a new blast every 4 seconds. */
	private static final int BLAST_PERIOD = 80;

	public SirenBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(LIT, false));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(LIT);
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		level.scheduleTick(pos, this, POLL_TICKS);
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		Float pitch = DisasterManager.alarmPitch(level, pos);
		boolean powered = level.hasNeighborSignal(pos);
		boolean alarm = pitch != null || powered;

		if (alarm != state.getValue(LIT)) {
			level.setBlock(pos, state.setValue(LIT, alarm), 3);
		}
		if (alarm) {
			if (level.getGameTime() % BLAST_PERIOD < POLL_TICKS) {
				level.playSound(null, pos, ModSounds.SIREN, SoundSource.BLOCKS, 6.0f, pitch != null ? pitch : 1.0f);
			}
			level.sendParticles(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5,
					1, 0.3, 0.2, 0.3, 1.0);
		}
		level.scheduleTick(pos, this, POLL_TICKS);
	}
}
