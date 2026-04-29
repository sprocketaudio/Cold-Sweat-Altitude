package net.sprocketgames.coldsweataltitude.shelter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.coldsweataltitude.temperature.AltitudeBand;

public final class ShelterManager
{
    public static final ShelterManager INSTANCE = new ShelterManager();

    private static final double ENCLOSURE_THRESHOLD = 0.90D;
    private static final int[][] CHECK_DIRECTIONS = buildDirections();

    private ShelterManager()
    {
    }

    public double shelterMultiplier(ServerPlayer player, AltitudeBand band)
    {
        if (!band.enableShelterCheck())
        {
            return 1.0D;
        }

        return isEnclosed(player, band.shelterCheckRadius())
            ? Math.max(0.0D, 1.0D - band.shelterReduction())
            : 1.0D;
    }

    private boolean isEnclosed(ServerPlayer player, int shelterCheckRadius)
    {
        int radius = Math.max(1, shelterCheckRadius);
        Level level = player.level();
        BlockPos origin = player.blockPosition().above();
        int closedDirections = 0;

        for (int[] direction : CHECK_DIRECTIONS)
        {
            if (hasClosure(level, origin, radius, direction[0], direction[1], direction[2]))
            {
                closedDirections++;
            }
        }

        return closedDirections / (double) CHECK_DIRECTIONS.length >= ENCLOSURE_THRESHOLD;
    }

    private boolean hasClosure(Level level, BlockPos origin, int radius, int dx, int dy, int dz)
    {
        for (int step = 1; step <= radius; step++)
        {
            BlockPos pos = origin.offset(dx * step, dy * step, dz * step);
            if (isClosureBlock(level, pos))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isClosureBlock(Level level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock)
        {
            return !state.getValue(DoorBlock.OPEN);
        }
        if (state.getBlock() instanceof TrapDoorBlock)
        {
            return !state.getValue(TrapDoorBlock.OPEN);
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }

    private static int[][] buildDirections()
    {
        int[][] directions = new int[26][3];
        int index = 0;
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dy == 0 && dz == 0)
                    {
                        continue;
                    }
                    directions[index++] = new int[] { dx, dy, dz };
                }
            }
        }
        return directions;
    }
}
