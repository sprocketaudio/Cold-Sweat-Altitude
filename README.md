
# Cold Sweat: Altitude

Cold Sweat: Altitude is a NeoForge 1.21.1 addon for Cold Sweat focused on configurable altitude-based temperature progression.

The goal is simple: let modpacks define temperature bands by Y level so caves, mountains, upper sky layers, and custom dimensions can all behave differently without pack-specific code. The addon is intended to sit on top of Cold Sweat's existing biome, dimension, block, and equipment systems rather than replace them.

This repository contains the addon implementation:

- Package namespace: `net.sprocketgames.coldsweataltitude`
- Mod ID: `coldsweat_altitude`
- Required dependency: `cold_sweat` 2.4+
- Target platform: NeoForge `21.1.181+` on Minecraft `1.21.1`

Planned gameplay support includes:

- Configurable altitude bands with per-dimension filtering
- Additive or multiplicative temperature modifiers
- Optional warning messages layered on top of Cold Sweat
- Tag-based protection support
- Simple shelter reduction
- Admin commands for status, reload, and band listing
