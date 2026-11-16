package com.naturaldisasters.registry;

import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.disaster.DisasterType;
import com.naturaldisasters.item.DetectorItem;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	/** Detects earthquakes and ground rifts. */
	public static final Item SEISMOGRAPH = register("seismograph",
			new DetectorItem(props("seismograph"), "Seismograph",
					Set.of(DisasterType.EARTHQUAKE, DisasterType.FISSURE), 400));

	/** Detects tsunamis (including those triggered by offshore earthquakes). */
	public static final Item TSUNAMI_DETECTOR = register("tsunami_detector",
			new DetectorItem(props("tsunami_detector"), "Tsunami Detector",
					Set.of(DisasterType.TSUNAMI), 600));

	/** Detects tornadoes and hurricanes. */
	public static final Item STORM_RADAR = register("storm_radar",
			new DetectorItem(props("storm_radar"), "Storm Radar",
					Set.of(DisasterType.TORNADO, DisasterType.HURRICANE), 512));

	/** Detects floods. */
	public static final Item FLOOD_GAUGE = register("flood_gauge",
			new DetectorItem(props("flood_gauge"), "Flood Gauge",
					Set.of(DisasterType.FLOOD), 320));

	private static Item.Properties props(String name) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NaturalDisasters.id(name));
		return new Item.Properties().stacksTo(1).setId(key);
	}

	private static Item register(String name, Item item) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, NaturalDisasters.id(name));
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void initialize() {
	}

	private ModItems() {
	}
}
