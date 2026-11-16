package com.naturaldisasters.registry;

import com.naturaldisasters.NaturalDisasters;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * Custom sound events. They are defined in assets/naturaldisasters/sounds.json as redirects to
 * vanilla sound events (type "event"), so the mod ships no audio files of its own.
 */
public final class ModSounds {
	public static final SoundEvent SIREN = register("siren", 192f);
	public static final SoundEvent RUMBLE = register("rumble", 96f);
	public static final SoundEvent ROAR = register("roar", 128f);
	public static final SoundEvent WAVE = register("wave", 128f);

	private static SoundEvent register(String name, float range) {
		Identifier id = NaturalDisasters.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createFixedRangeEvent(id, range));
	}

	public static void initialize() {
	}

	private ModSounds() {
	}
}
