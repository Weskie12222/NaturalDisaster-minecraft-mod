# Natural Disasters (Fabric, Minecraft 26.2)

Earthquakes, tornadoes, tsunamis, floods, hurricanes and ground rifts that tear open into caves,
plus sirens and detector tools that warn you before they hit.

## Build

Requirements: **JDK 25**, internet access for Gradle (Fabric Maven).

```
./gradlew build          # jar ends up in build/libs/naturaldisasters-1.0.0.jar
./gradlew runServer      # or runClient, to test
```

Toolchain: Minecraft 26.2 (unobfuscated, official Mojang names), Fabric Loader 0.19.3,
Loom 1.17 (`net.fabricmc.fabric-loom`), Gradle 9.5.1, Fabric API 0.161.0+26.2.
Players need Fabric Loader >= 0.19.3 and Fabric API.

## Disasters

| Disaster | What it does | Warning lead time |
|---|---|---|
| Earthquake | Shakes players/mobs, breaks fragile blocks. Strong+ can leave a rift; strong+ near a coast triggers a tsunami | 25 s |
| Ground Rift | A long crack opens. It scans each column for a cave below: if found it digs down into it (finding caves), otherwise leaves a shallow scar | 20 s |
| Tornado | Moving funnel that lifts entities, rips blocks off as flying debris, damages what it catches | 60 s |
| Hurricane | Thunderstorm, gale wind on anything under open sky, lightning, debris; extreme ones spawn tornadoes | 120 s |
| Tsunami | Wall of temporary water travelling inland from the nearest ocean, pushes entities, smashes weak blocks | 90 s |
| Flood | Rain and rising water that fills valleys and low ground, then recedes | 90 s |

Severity: **Minor, Moderate, Strong, Extreme.** Strength scales radius, duration, damage and what blocks can be destroyed.

## Warning system

- **Warning Siren** (block): wails automatically when a Strong/Extreme disaster (Moderate+ for tsunamis)
  is forecast or active within range. Different disasters use different siren pitches.
  It also sounds when given a redstone signal. Place several around your base.
- **Seismograph**: earthquakes and rifts (400 blocks)
- **Tsunami Detector**: tsunamis (600 blocks)
- **Storm Radar**: tornadoes and hurricanes (512 blocks)
- **Flood Gauge**: floods (320 blocks)

Carry a detector anywhere in your inventory for automatic action-bar alerts (distance, direction, seconds to impact).
Right-click for a full readout in chat. Players near a forecast also get chat warnings with survival advice.

Everything is craftable (see `data/naturaldisasters/recipe/`) and in the "Natural Disasters" creative tab.

## Commands (need game-master permission)

```
/disaster start <earthquake|fissure|tornado|hurricane|tsunami|flood> [minor|moderate|strong|extreme]
/disaster stop
/disaster list
/disaster natural <true|false>     # random natural disasters, ~one per 15 min per overworld, default on
/disaster siren [seconds] [radius] # test all sirens near you
```

## Notes and limits

- Disaster state is kept in memory. Temporary water is removed when the server stops cleanly;
  after a crash a few water blocks could remain.
- Tornado/hurricane particles are only rendered by the client within ~32 blocks. Sound and warnings carry further.
- Siren/rumble/roar/wave sounds are redirects to vanilla sound events (`sounds.json`), so there are no audio files to ship.
- Tuning knobs live at the top of each disaster class (radius, speed, durations) and in `DisasterManager` (`NATURAL_MEAN_SECONDS`).
