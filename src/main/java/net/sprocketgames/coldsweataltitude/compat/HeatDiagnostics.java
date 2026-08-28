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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class HeatDiagnostics
{
    private static final Method HEARTH_AREA_CONTAINS_POS = findHearthAreaMethod("areaContainsPos");
    private static final Method HEARTH_IS_AFFECTING_POS = findHearthAreaMethod("isAffectingPos");

    private HeatDiagnostics()
    {
    }

    public static Report collect(ServerPlayer player)
    {
        try
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

            entries = combineMatchingRules(entries);
            entries.sort(Comparator.comparingDouble((Entry entry) -> Math.abs(entry.value())).reversed());
            hearths.sort(Comparator.comparingDouble(HearthEntry::distance));
            double total = entries.stream().mapToDouble(Entry::value).sum();
            return new Report(scanRange, total, entries, hearths, null);
        }
        catch (RuntimeException exception)
        {
            return new Report(AeronauticsHeatSourceBlockTemp.scanRange(), 0.0D, List.of(), List.of(),
                exception.getClass().getSimpleName() + ": " + String.valueOf(exception.getMessage()));
        }
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
                    blockTemp.getClass().getSimpleName(),
                    1));
            }
        }
    }

    private static List<Entry> combineMatchingRules(List<Entry> entries)
    {
        Map<SourceKey, Entry> combined = new LinkedHashMap<>();
        for (Entry entry : entries)
        {
            SourceKey key = new SourceKey(entry.context(), entry.blockId(), entry.pos());
            Entry previous = combined.get(key);
            if (previous == null)
            {
                combined.put(key, entry);
                continue;
            }
            combined.put(key, new Entry(previous.context(), previous.blockId(), previous.pos(), previous.distance(),
                previous.value(), previous.source(), previous.matchingRules() + 1));
        }
        return new ArrayList<>(combined.values());
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
                hearth.isCoolingOn(),
                hearth.getHotFuel(),
                hearth.getColdFuel(),
                hearth.isUsingHotFuel(),
                hearth.isUsingColdFuel(),
                hearth.getHeatingLevel(),
                hearth.getCoolingLevel(),
                hearth.getMaxRange(),
                hearthAffectsPositions(hearth, occupiedPositions(localBox))));
        }
    }

    private static boolean hearthAffectsPositions(HearthBlockEntity hearth, List<BlockPos> positions)
    {
        if (tryHearthAreaMethod(hearth, HEARTH_AREA_CONTAINS_POS, positions))
        {
            return true;
        }
        return tryHearthAreaMethod(hearth, HEARTH_IS_AFFECTING_POS, positions);
    }

    private static boolean tryHearthAreaMethod(HearthBlockEntity hearth, Method method, List<BlockPos> positions)
    {
        if (method == null)
        {
            return false;
        }
        try
        {
            Object result = method.invoke(hearth, positions);
            return result instanceof Boolean value && value;
        }
        catch (ReflectiveOperationException | RuntimeException ignored)
        {
            return false;
        }
    }

    private static Method findHearthAreaMethod(String name)
    {
        try
        {
            Method method = HearthBlockEntity.class.getMethod(name, List.class);
            method.setAccessible(true);
            return method;
        }
        catch (ReflectiveOperationException | RuntimeException ignored)
        {
            return null;
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
        List<HearthEntry> hearths,
        String error)
    {
    }

    public record Entry(
        String context,
        ResourceLocation blockId,
        BlockPos pos,
        double distance,
        double value,
        String source,
        int matchingRules)
    {
    }

    private record SourceKey(String context, ResourceLocation blockId, BlockPos pos)
    {
    }

    public record HearthEntry(
        String context,
        BlockPos pos,
        double distance,
        boolean heatingOn,
        boolean coolingOn,
        int hotFuel,
        int coldFuel,
        boolean usingHotFuel,
        boolean usingColdFuel,
        int heatingLevel,
        int coolingLevel,
        int maxRange,
        boolean affectingPlayer)
    {
    }
}
