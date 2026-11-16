package com.naturaldisasters.registry;

import com.naturaldisasters.NaturalDisasters;
import com.naturaldisasters.block.SirenBlock;
import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static final Block SIREN = register(
			"siren",
			SirenBlock::new,
			BlockBehaviour.Properties.of()
					.strength(3.0f, 6.0f)
					.sound(SoundType.METAL)
					.requiresCorrectToolForDrops()
					.lightLevel(state -> state.getValue(SirenBlock.LIT) ? 10 : 0)
	);

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties props) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, NaturalDisasters.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(props.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, NaturalDisasters.id(name));
		BlockItem item = new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey));
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		return block;
	}

	public static void initialize() {
	}

	private ModBlocks() {
	}
}
