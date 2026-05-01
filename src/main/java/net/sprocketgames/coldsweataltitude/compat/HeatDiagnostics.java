package net.sprocketgames.coldsweataltitude.compat;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import net.sprocketgames.coldsweataltitude.compat.blocktemp.AeronauticsHeatSourceBlockTemp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

public final class HeatDiagnostics
{
    private HeatDiagnostics()
    {
    }

    public static Report collect(ServerPlayer player)
    {
        int scanRange = AeronauticsHeatSourceBlockTemp.scanRange();
        List<Entry> entries = new ArrayList<>();
        List<HearthEntry> hearths = new ArrayList<>();

        scan("World", player.level(), player, player.position(), player.blockPosition(), scanRange, entries);
        scanHearths("World", player.level(), player.getBoundingBox(), player.position(), player.blockPosition(), hearths);

        SableSublevelContext context = SableSublevelResolver.INSTANCE.resolve(player);
        if (context != null)
        {
            scan("Sable", context.level(), player, context.localPosition(), context.localBlockPos(), scanRange, entries);
            Vec3 delta = context.localPosition().subtract(player.position());
            AABB localBox = player.getBoundingBox().move(delta);
            localBox = localBox.setMaxY(Math.max(localBox.maxY, localBox.minY + 2.0D));
            scanHearths("Sable", context.level(), localBox, context.localPosition(), context.localBlockPos(), hearths);
        }

        entries.sort(Comparator.comparingDouble((Entry entry) -> Math.abs(entry.value())).reversed());
        hearths.sort(Comparator.comparingDouble(HearthEntry::distance));
        double total = entries.stream().mapToDouble(Entry::value).sum();
        return new Report(scanRange, total, entries, hearths);
    }

    private static void scan(String context,
                             Level level,
                             ServerPlayer player,
                             Vec3 sourcePosition,
                             BlockPos center,
                             int scanRange,
                             List<Entry> entries)
    {
        for (BlockPos scannedPos : BlockPos.betweenClosed(center.offset(-scanRange, -scanRange, -scanRange),
            center.offset(scanRange, scanRange, scanRange)))
        {
            BlockPos pos = scannedPos.immutable();
            BlockState state = level.getBlockState(pos);
            Collection<BlockTemp> blockTemps = BlockTempRegistry.getBlockTempsFor(state);
            if (blockTemps.isEmpty())
            {
                continue;
            }

            double distance = Vec3.atCenterOf(pos).subtract(sourcePosition).length();
            for (BlockTemp blockTemp : blockTemps)
            {
                if (!blockTemp.isValid(level, pos, state))
                {
                    continue;
                }

                double value = blockTemp.getTemperature(level, player, state, pos, distance);
                if (Math.abs(value) < 0.0001D)
                {
                    continue;
                }

                entries.add(new Entry(
                    context,
                    BuiltInRegistries.BLOCK.getKey(state.getBlock()),
                    pos,
                    distance,
                    value,
                    blockTemp.getClass().getSimpleName()));
            }
        }
    }

    private static void scanHearths(String context,
                                    Level level,
                                    AABB localBox,
                                    Vec3 sourcePosition,
                                    BlockPos center,
                                    List<HearthEntry> hearths)
    {
        int searchRadius = 24;
        for (BlockPos scannedPos : BlockPos.betweenClosed(center.offset(-searchRadius, -searchRadius, -searchRadius),
            center.offset(searchRadius, searchRadius, searchRadius)))
        {
            BlockPos pos = scannedPos.immutable();
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof HearthBlockEntity hearth))
            {
                continue;
            }

            double distance = Vec3.atCenterOf(pos).distanceTo(sourcePosition);
            if (distance > hearth.getMaxRange())
            {
                continue;
            }

            hearths.add(new HearthEntry(
                context,
                pos,
                distance,
                hearth.isHeatingOn(),
                hearth.getHotFuel(),
                hearth.isUsingHotFuel(),
                hearth.getHeatingLevel(),
                hearth.getMaxRange(),
                hearth.isAffectingPos(occupiedPositions(localBox))));
        }
    }

    private static List<BlockPos> occupiedPositions(AABB box)
    {
        int minX = (int) Math.floor(box.minX);
        int minY = (int) Math.floor(box.minY);
        int minZ = (int) Math.floor(box.minZ);
        int maxX = (int) Math.floor(box.maxX);
        int maxY = (int) Math.floor(box.maxY);
        int maxZ = (int) Math.floor(box.maxZ);

        List<BlockPos> positions = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
        {
            for (int y = minY; y <= maxY; y++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    positions.add(new BlockPos(x, y, z));
                    if (positions.size() > 128)
                    {
                        return positions;
                    }
                }
            }
        }
        return positions;
    }

    public record Report(
        int scanRange,
        double total,
        List<Entry> entries,
        List<HearthEntry> hearths)
    {
    }

    public record Entry(
        String context,
        ResourceLocation blockId,
        BlockPos pos,
        double distance,
        double value,
        String source)
    {
    }

    public record HearthEntry(
        String context,
        BlockPos pos,
        double distance,
        boolean heatingOn,
        int hotFuel,
        boolean usingHotFuel,
        int heatingLevel,
        int maxRange,
        boolean affectingPlayer)
    {
    }
}
