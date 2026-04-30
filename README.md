
# Cold Sweat: Altitude

Cold Sweat: Altitude is a NeoForge 1.21.1 addon for Cold Sweat focused on configurable altitude-based temperature progression.

The goal is simple: let modpacks define temperature bands by Y level so caves, mountains, upper sky layers, and custom dimensions can all behave differently without pack-specific code. The addon is intended to sit on top of Cold Sweat's existing biome, dimension, block, and equipment systems rather than replace them.

As of `0.3.0`, the addon also supports scaled shelter detection inside enclosed Create: Aeronautics ships running on Sable contraptions, along with Cold Sweat heat-source support inside those moving ship interiors.

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
- Cold Sweat block-temperature and hearth-style heat-source support inside Create: Aeronautics ships using Sable contraptions
- Admin commands for status, reload, and band listing
