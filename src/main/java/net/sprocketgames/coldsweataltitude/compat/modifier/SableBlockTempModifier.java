package net.sprocketgames.coldsweataltitude.compat.modifier;

import com.momosoftworks.coldsweat.api.registry.BlockTempRegistry;
import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import com.momosoftworks.coldsweat.api.temperature.block_temp.ConfiguredBlockTemp;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.config.ConfigSettings;
import com.momosoftworks.coldsweat.data.codec.configuration.BlockTempData;
import com.momosoftworks.coldsweat.util.math.CSMath;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelContext;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelResolver;
import net.sprocketgames.coldsweataltitude.compat.blocktemp.AeronauticsHeatSourceBlockTemp;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Cold Sweat's BlockTempModifier adapted to scan Sable's local level.  Keep
 * this calculation in lockstep with upstream: the order, ray blocking, fade,
 * grouped caps, and single matching rule per block are all significant.
 */
public final class SableBlockTempModifier extends TempModifier
{
    private static final double LOG_FACTOR = 0.52D;

    private final Map<Long, ChunkAccess> chunks = new LinkedHashMap<>(16, 0.75F, true);
    private final Map<BlockTemp, Double> blockTempTotals = new HashMap<>(16);
    private final Map<TagKey<BlockTempData>, Double> groupTotals = new HashMap<>(8);
    private final Long2ObjectOpenHashMap<BlockState> stateCache = new Long2ObjectOpenHashMap<>(3000);

    @Override
    protected Function<Double, Double> calculate(LivingEntity entity, Temperature.Trait trait)
    {
        if (!(entity instanceof Player player) || trait != Temperature.Trait.WORLD)
        {
            return temp -> temp;
        }

        SableSublevelContext context = SableSublevelResolver.INSTANCE.resolve(player);
        if (context == null)
        {
            return temp -> temp;
        }

        blockTempTotals.clear();
        groupTotals.clear();
        stateCache.clear();
        // A Sable sub-level can be destroyed and recreated at the same local
        // coordinates when a contraption is reassembled.  Retaining chunks
        // across calculations can therefore scan the old, now-empty level.
        chunks.clear();

        Level level = context.level();
        BlockPos entityPos = context.localBlockPos();
        Vec3 localPosition = context.localPosition();
        AABB localBox = player.getBoundingBox().move(localPosition.subtract(player.position()));
        int range = Math.max(ConfigSettings.BLOCK_RANGE.get(), AeronauticsHeatSourceBlockTemp.scanRange());
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();

        ChunkAccess chunk = null;
        long chunkPos = 0L;
        for (int x = -range; x < range; x++)
        {
            int chunkX = (entityPos.getX() + x) >> 4;
            for (int z = -range; z < range; z++)
            {
                int chunkZ = (entityPos.getZ() + z) >> 4;
                long nextChunkPos = ChunkPos.asLong(chunkX, chunkZ);
                if (chunk == null || nextChunkPos != chunkPos)
                {
                    chunkPos = nextChunkPos;
                    chunk = chunks.get(chunkPos);
                    if (chunk == null)
                    {
                        chunk = WorldHelper.getChunk(level, new ChunkPos(chunkPos));
                        chunks.put(chunkPos, chunk);
                    }
                    if (chunk == null)
                    {
                        continue;
                    }
                }

                for (int y = -range; y < range; y++)
                {
                    blockPos.set(entityPos.getX() + x, entityPos.getY() + y, entityPos.getZ() + z);
                    BlockState state = cachedState(chunk, blockPos);
                    if (state.isAir())
                    {
                        continue;
                    }

                    Collection<BlockTemp> blockTemps = BlockTempRegistry.getBlockTempsFor(state);
                    if (blockTemps.isEmpty() || (blockTemps.size() == 1 && blockTemps.contains(BlockTempRegistry.DEFAULT_BLOCK_TEMP))
                        || !areAnyBlockTempsInRange(blockTemps))
                    {
                        continue;
                    }

                    Vec3 sourcePos = Vec3.atCenterOf(blockPos);
                    Vec3 closestPlayerPos = closestPoint(localBox, sourcePos);
                    int blockers = countBlockers(level, chunk, blockPos, sourcePos, closestPlayerPos);
                    double distance = CSMath.getDistance(closestPlayerPos, sourcePos);

                    for (BlockTemp blockTemp : blockTemps)
                    {
                        if (!blockTemp.isValid(level, blockPos, state))
                        {
                            continue;
                        }

                        double temperature = blockTemp.getTemperature(level, player, state, blockPos, distance);
                        if (temperature == 0.0D)
                        {
                            continue;
                        }

                        double contribution = blockTemp.fade()
                            ? CSMath.blend(temperature, 0.0D, distance, 0.5D, blockTemp.range())
                            : temperature;
                        addContribution(blockTemp, contribution, blockers);
                        // This matches Cold Sweat: one valid BlockTemp rule per physical block.
                        break;
                    }
                }
            }
        }

        while (chunks.size() >= 16)
        {
            chunks.remove(chunks.keySet().iterator().next());
        }

        Map<BlockTemp, Double> totals = new HashMap<>(blockTempTotals);
        return temp -> {
            double result = temp;
            for (Map.Entry<BlockTemp, Double> entry : totals.entrySet())
            {
                BlockTemp blockTemp = entry.getKey();
                if (CSMath.betweenInclusive(result, blockTemp.minTemperature(), blockTemp.maxTemperature()))
                {
                    result = CSMath.clamp(result + entry.getValue(), blockTemp.minTemperature(), blockTemp.maxTemperature());
                }
            }
            return result;
        };
    }

    private BlockState cachedState(ChunkAccess chunk, BlockPos pos)
    {
        long key = pos.asLong();
        BlockState state = stateCache.get(key);
        if (state == null)
        {
            LevelChunkSection section = WorldHelper.getChunkSection(chunk, pos.getY());
            state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
            stateCache.put(key, state);
        }
        return state;
    }

    private int countBlockers(Level level, ChunkAccess chunk, BlockPos sourceBlock, Vec3 sourcePos, Vec3 closestPlayerPos)
    {
        int[] blockers = new int[1];
        Vec3 ray = sourcePos.subtract(closestPlayerPos);
        Direction direction = Direction.getNearest(ray.x, ray.y, ray.z);
        WorldHelper.forBlocksInRay(closestPlayerPos, sourcePos, level, chunk, stateCache, (rayState, rayPos) -> {
            if (!rayPos.equals(sourceBlock) && WorldHelper.isSpreadBlocked(level, rayState, rayPos, direction, direction))
            {
                blockers[0]++;
            }
        }, 3);
        return blockers[0];
    }

    private void addContribution(BlockTemp blockTemp, double contribution, int blockers)
    {
        double current = blockTempTotals.getOrDefault(blockTemp, 0.0D);
        double groupTotal = getGroupTotal(blockTemp);
        double groupDelta = groupTotal - current;
        double updated;
        if (blockTemp.logarithmic())
        {
            double next = Math.pow(Math.pow(current, 1.0D / LOG_FACTOR) + contribution, LOG_FACTOR);
            updated = current + (next - current) / (blockers + 1.0D);
        }
        else
        {
            updated = current + contribution / (blockers + 1.0D);
        }
        updated = CSMath.clamp(updated, blockTemp.minEffect() + groupDelta, blockTemp.maxEffect() - groupDelta);
        blockTempTotals.put(blockTemp, updated);
        updateGroupTotal(blockTemp, updated - current);
    }

    private boolean areAnyBlockTempsInRange(Collection<BlockTemp> blockTemps)
    {
        for (BlockTemp blockTemp : blockTemps)
        {
            if (!blockTempTotals.containsKey(blockTemp)
                || CSMath.betweenInclusive(getGroupTotal(blockTemp), blockTemp.minEffect(), blockTemp.maxEffect()))
            {
                return true;
            }
        }
        return false;
    }

    private double getGroupTotal(BlockTemp blockTemp)
    {
        if (!(blockTemp instanceof ConfiguredBlockTemp configured) || configured.getData().effectGroup().isEmpty())
        {
            return blockTempTotals.getOrDefault(blockTemp, 0.0D);
        }
        return groupTotals.getOrDefault(configured.getData().effectGroup().get(), 0.0D);
    }

    private void updateGroupTotal(BlockTemp blockTemp, double delta)
    {
        if (blockTemp instanceof ConfiguredBlockTemp configured && configured.getData().effectGroup().isPresent())
        {
            groupTotals.merge(configured.getData().effectGroup().get(), delta, Double::sum);
        }
    }

    private static Vec3 closestPoint(AABB box, Vec3 point)
    {
        return new Vec3(clamp(point.x, box.minX, box.maxX), clamp(point.y, box.minY, box.maxY), clamp(point.z, box.minZ, box.maxZ));
    }

    private static double clamp(double value, double min, double max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
