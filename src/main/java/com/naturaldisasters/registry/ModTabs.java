package com.naturaldisasters.registry;

import com.naturaldisasters.NaturalDisasters;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModTabs {
	public static final ResourceKey<CreativeModeTab> KEY =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, NaturalDisasters.id("natural_disasters"));

	public static final CreativeModeTab TAB = FabricCreativeModeTab.builder()
			.icon(() -> new ItemStack(ModBlocks.SIREN))
			.title(Component.translatable("creativeTab.naturaldisasters"))
			.displayItems((params, output) -> {
				output.accept(ModBlocks.SIREN);
				output.accept(ModItems.SEISMOGRAPH);
				output.accept(ModItems.TSUNAMI_DETECTOR);
				output.accept(ModItems.STORM_RADAR);
				output.accept(ModItems.FLOOD_GAUGE);
			})
			.build();

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, TAB);
	}

	private ModTabs() {
	}
}
