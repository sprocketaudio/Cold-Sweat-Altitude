package net.sprocketgames.coldsweataltitude.shelter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.coldsweataltitude.temperature.AltitudeBand;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

public final class ShelterManager
{
    public static final ShelterManager INSTANCE = new ShelterManager();

    private static final double ENCLOSURE_THRESHOLD = 0.90D;
    private static final double[][] CHECK_DIRECTIONS = buildDirections();
    private static final int[][] FLOOD_DIRECTIONS = {
        { 1, 0, 0 },
        { -1, 0, 0 },
        { 0, 1, 0 },
        { 0, -1, 0 },
        { 0, 0, 1 },
        { 0, 0, -1 }
    };

    private ShelterManager()
    {
    }

    public double shelterMultiplier(ServerPlayer player, AltitudeBand band)
    {
        if (!band.enableShelterCheck())
        {
            return 1.0D;
        }

        double enclosure = shelterEnclosure(player, band);
        return shelterMultiplier(band, enclosure);
    }

    public double shelterMultiplier(AltitudeBand band, double enclosure)
    {
        double shelterScale = Math.min(1.0D, enclosure / ENCLOSURE_THRESHOLD);
        double effectiveReduction = band.shelterReduction() * shelterScale;
        return Math.max(0.0D, 1.0D - effectiveReduction);
    }

    public double shelterEnclosure(ServerPlayer player, AltitudeBand band)
    {
        if (!band.enableShelterCheck())
        {
            return 0.0D;
        }
        return enclosure(player, band.shelterCheckRadius());
    }

    public double worldShelterEnclosure(ServerPlayer player, AltitudeBand band)
    {
        if (!band.enableShelterCheck())
        {
            return 0.0D;
        }
        return enclosureInWorld(player, Math.max(1, band.shelterCheckRadius()));
    }

    public double sableShelterEnclosure(ServerPlayer player, AltitudeBand band)
    {
        if (!band.enableShelterCheck())
        {
            return 0.0D;
        }
        return SableContraptionShelter.INSTANCE.enclosure(player, Math.max(1, band.shelterCheckRadius()));
    }

    public String sableDiagnostic(ServerPlayer player, AltitudeBand band)
    {
        if (!band.enableShelterCheck())
        {
            return "disabled";
        }
        return SableContraptionShelter.INSTANCE.diagnostic(player, Math.max(1, band.shelterCheckRadius()));
    }

    private double enclosure(ServerPlayer player, int shelterCheckRadius)
    {
        int radius = Math.max(1, shelterCheckRadius);
        return Math.max(
            enclosureInWorld(player, radius),
            SableContraptionShelter.INSTANCE.enclosure(player, radius));
    }

    private double enclosureInWorld(ServerPlayer player, int radius)
    {
        Level level = player.level();
        BlockPos origin = player.blockPosition().above();
        return enclosure(level, origin, radius);
    }

    static double enclosure(BlockGetter level, BlockPos origin, int radius)
    {
        if (isEnclosedVolume(level, origin, radius))
        {
            return 1.0D;
        }
        return directionalEnclosure(level, origin, radius);
    }

    private static double directionalEnclosure(BlockGetter level, BlockPos origin, int radius)
    {
        int closedDirections = 0;

        for (double[] direction : CHECK_DIRECTIONS)
        {
            if (hasClosure(level, origin, radius, direction[0], direction[1], direction[2]))
            {
                closedDirections++;
            }
        }

        return closedDirections / (double) CHECK_DIRECTIONS.length;
    }

    private static boolean isEnclosedVolume(BlockGetter level, BlockPos origin, int radius)
    {
        if (isClosureBlock(level, origin))
        {
            return false;
        }

        ArrayDeque<BlockPos> open = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        open.add(origin);
        visited.add(origin);

        while (!open.isEmpty())
        {
            BlockPos current = open.removeFirst();
            if (isAtScanLimit(origin, current, radius))
            {
                return false;
            }

            for (int[] direction : FLOOD_DIRECTIONS)
            {
                BlockPos next = current.offset(direction[0], direction[1], direction[2]);
                if (visited.contains(next) || isClosureBlock(level, next))
                {
                    continue;
                }

                visited.add(next);
                open.add(next);
            }
        }

        return true;
    }

    private static boolean isAtScanLimit(BlockPos origin, BlockPos current, int radius)
    {
        return Math.abs(current.getX() - origin.getX()) >= radius
            || Math.abs(current.getY() - origin.getY()) >= radius
            || Math.abs(current.getZ() - origin.getZ()) >= radius;
    }

    private static boolean hasClosure(BlockGetter level, BlockPos origin, int radius, double dx, double dy, double dz)
    {
        double originX = origin.getX() + 0.5D;
        double originY = origin.getY() + 0.5D;
        double originZ = origin.getZ() + 0.5D;

        for (double step = 0.75D; step <= radius; step += 0.5D)
        {
            BlockPos pos = BlockPos.containing(originX + dx * step, originY + dy * step, originZ + dz * step);
            if (isClosureBlock(level, pos))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean isClosureBlock(BlockGetter level, BlockPos pos)
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

    private static double[][] buildDirections()
    {
        int resolution = 4;
        java.util.List<double[]> directions = new java.util.ArrayList<>();
        for (int dx = -resolution; dx <= resolution; dx++)
        {
            for (int dy = -resolution; dy <= resolution; dy++)
            {
                for (int dz = -resolution; dz <= resolution; dz++)
                {
                    if (dx == 0 && dy == 0 && dz == 0)
                    {
                        continue;
                    }
                    if (Math.max(Math.max(Math.abs(dx), Math.abs(dy)), Math.abs(dz)) != resolution)
                    {
                        continue;
                    }

                    double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    directions.add(new double[] { dx / length, dy / length, dz / length });
                }
            }
        }
        return directions.toArray(double[][]::new);
    }
}
