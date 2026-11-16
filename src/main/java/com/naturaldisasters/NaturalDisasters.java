package com.naturaldisasters;

import com.naturaldisasters.disaster.DisasterCommands;
import com.naturaldisasters.disaster.DisasterManager;
import com.naturaldisasters.registry.ModBlocks;
import com.naturaldisasters.registry.ModItems;
import com.naturaldisasters.registry.ModSounds;
import com.naturaldisasters.registry.ModTabs;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;

public class NaturalDisasters implements ModInitializer {
	public static final String MOD_ID = "naturaldisasters";

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModSounds.initialize();
		ModBlocks.initialize();
		ModItems.initialize();
		ModTabs.initialize();
		DisasterCommands.initialize();
		DisasterManager.initialize();
	}
}
