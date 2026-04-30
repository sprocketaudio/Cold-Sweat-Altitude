
# Cold Sweat: Altitude

Cold Sweat: Altitude is a NeoForge 1.21.1 addon for Cold Sweat focused on configurable altitude-based temperature progression.

The goal is simple: let modpacks define temperature bands by Y level so caves, mountains, upper sky layers, and custom dimensions can all behave differently without pack-specific code. The addon is intended to sit on top of Cold Sweat's existing biome, dimension, block, and equipment systems rather than replace them.

The current feature set includes scaled shelter, in-game HUD feedback, and Create: Aeronautics / Sable ship support for both shelter and heat sources.

This repository contains the addon implementation:

- Required dependency: `cold_sweat` 2.4+
- Target platform: NeoForge `21.1.181+` on Minecraft `1.21.1`

Gameplay support includes:

- Configurable altitude bands with per-dimension filtering
- Additive or multiplicative temperature modifiers
- Optional warning messages layered on top of Cold Sweat
- Tag-based protection support
- Scaled shelter reduction
- Shelter HUD feedback when partial or full shelter is detected
- Shelter detection support for Create: Aeronautics ships using Sable contraptions
- Cold Sweat block-temperature support inside Create: Aeronautics ships using Sable contraptions
- Direct Aeronautics heat-source support for `Hot Air Burner` and `Steam Vent`
- Configurable Aeronautics burner and steam-vent heat/range tuning in `coldsweat_altitude-server.toml`
- Cold Sweat hearth support inside ship interiors for packs that use hearth blocks on ships
- Admin commands for status, reload, and band listing
