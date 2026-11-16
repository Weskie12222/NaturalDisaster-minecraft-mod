package com.naturaldisasters.item;

import com.naturaldisasters.disaster.DisasterManager;
import com.naturaldisasters.disaster.DisasterType;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A handheld detector. Anywhere in the inventory it sends an action-bar alert when a matching disaster
 * is forecast or active within its range; right-clicking prints a full readout in chat.
 */
public class DetectorItem extends Item {
	private final String deviceName;
	private final Set<DisasterType> detects;
	private final int range;

	public DetectorItem(Properties properties, String deviceName, Set<DisasterType> detects, int range) {
		super(properties);
		this.deviceName = deviceName;
		this.detects = detects;
		this.range = range;
	}

	public boolean detects(DisasterType type) {
		return detects.contains(type);
	}

	public int range() {
		return range;
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer) {
			DisasterManager.sendReadout(serverPlayer, serverLevel, deviceName, detects, range);
		}
		return InteractionResult.SUCCESS;
	}
}
