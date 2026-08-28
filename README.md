# Cold Sweat: Altitude

Cold Sweat: Altitude is a configurable addon for [Cold Sweat](https://www.curseforge.com/minecraft/mc-mods/cold-sweat) that adds temperature changes based on altitude.

Create warm cave layers, neutral surface conditions, freezing mountain peaks, or dangerous upper atmospheres entirely through config.

Requires Cold Sweat 2.4.1 or newer.

## Features

- Fully configurable altitude bands with min/max Y ranges
- Smooth temperature transitions between bands
- Additive or multiplicative temperature modifiers
- Dimension whitelist and blacklist support
- Priority-based band resolution
- Shelter-based reduction of altitude effects
- Item-tag-based protection for pack makers
- Coloured action-bar feedback when changing altitude bands
- Create: Aeronautics / Sable ship shelter and heat-source compatibility
- Runtime commands for inspection and debugging

## Smooth altitude gradients

Choose how temperature changes between altitude bands:

- `NONE` - temperature changes instantly at each band boundary.
- `LINEAR` - temperature changes gradually across the whole band.
- `BOUNDARY` - temperature remains stable through most of the band and transitions smoothly near its edges.

## Action-bar feedback

Entering a new altitude band displays a coloured action-bar message:

- Warm bands tint red
- Neutral bands remain white
- Cold bands tint blue

Use `actionbarDisplayTicks` to control how long each message remains visible.

## Create: Aeronautics / Sable support

When Create: Aeronautics and Sable are installed, Cold Sweat: Altitude supports altitude, shelter, and heat sources on assembled ships.

Ship interiors can use shelter protection, while Aeronautics burners and steam vents contribute heat in both normal world spaces and Sable contraptions.

## Configuration

Config location:

`config/coldsweat_altitude-server.toml`

Reload the config while a world is running:

`/coldsweat_altitude reload`

### Basic band example

```
[[bands]]
id = "high_mountains"
enabled = true
dimensions = ["minecraft:overworld"]
dimensionMode = "WHITELIST"
minY = 128
maxY = 191
temperatureModifier = -0.08
modifierMode = "ADD"
priority = 10
actionbarMessage = "The mountain air grows colder."
actionbarDisplayTicks = 100
enableShelterCheck = true
shelterCheckRadius = 4
shelterReduction = 0.35
```

## Commands

`/coldsweat_altitude status`

Shows the active altitude band, modifiers, shelter value, protection, Sable coordinates, and nearby thermal sources.

`/coldsweat_altitude reload`

Reloads the altitude config without restarting the world.

This addon is not affiliated with or endorsed by Cold Sweat or its authors.
