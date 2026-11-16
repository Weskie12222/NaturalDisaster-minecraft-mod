package com.naturaldisasters.disaster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Base class for a disaster. Lifecycle:
 * WARNING (detectors + sirens fire) -> ACTIVE (effects run) -> LINGER (sirens keep wailing for a bit) -> removed.
 */
public abstract class Disaster {
	/** Ticks the sirens keep going after the disaster itself has ended (10 seconds). */
	private static final int LINGER_TICKS = 200;

	public final DisasterType type;
	public final Severity severity;
	public final ServerLevel level;
	protected final RandomSource random;
	protected Vec3 pos;

	private final int warnTicks;
	private int age;
	private boolean started;
	private boolean ended;

	protected Disaster(DisasterType type, Severity severity, ServerLevel level, Vec3 pos, int warnTicks) {
		this.type = type;
		this.severity = severity;
		this.level = level;
		this.pos = pos;
		this.warnTicks = warnTicks;
		this.random = level.getRandom();
	}

	/** How many ticks the ACTIVE phase lasts. */
	protected abstract int activeTicks();

	protected abstract void onStart();

	/** Called every tick during the ACTIVE phase. {@code t} is ticks since the impact. */
	protected abstract void tickActive(int t);

	protected abstract void onEnd();

	/** Removes anything temporary (water, etc.). Also called on server shutdown. */
	public void cleanup() {
	}

	/** @return true once the disaster (including linger time) is completely over and can be removed. */
	public final boolean tick() {
		age++;
		if (age < warnTicks) {
			return false;
		}
		if (!started) {
			started = true;
			onStart();
		}
		int t = age - warnTicks;
		if (t < activeTicks()) {
			tickActive(t);
			return false;
		}
		if (!ended) {
			ended = true;
			onEnd();
			cleanup();
		}
		return t >= activeTicks() + LINGER_TICKS;
	}

	public Vec3 position() {
		return pos;
	}

	public boolean isActive() {
		return started && !ended;
	}

	public int secondsUntilImpact() {
		return Math.max(0, (warnTicks - age + 19) / 20);
	}

	/** Radius (blocks) in which players are warned and sirens go off. */
	public double warningRadius() {
		return 160.0 + 96.0 * severity.level;
	}

	public boolean triggersSirens() {
		return severity.level >= type.sirenThreshold.level && !(ended && age - warnTicks >= activeTicks() + LINGER_TICKS);
	}

	public String describe() {
		return severity.label + " " + type.label;
	}
}
