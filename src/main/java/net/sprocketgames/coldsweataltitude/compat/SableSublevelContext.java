package net.sprocketgames.coldsweataltitude.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public record SableSublevelContext(
    Object subLevelHandle,
    Level level,
    Vec3 localPosition,
    BlockPos localBlockPos,
    Vec3 worldPosition)
{
}
