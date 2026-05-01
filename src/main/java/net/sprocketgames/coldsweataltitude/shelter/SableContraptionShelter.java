package net.sprocketgames.coldsweataltitude.shelter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class SableContraptionShelter
{
    static final SableContraptionShelter INSTANCE = new SableContraptionShelter();

    private final boolean available;
    private final Class<?> entityMovementExtensionClass;
    private final Method getTrackingSubLevel;
    private final Method getCollisionInfo;
    private final Method getLastTrackingSubLevelId;
    private final Method getContainer;
    private final Method getContainerSubLevel;
    private final Method getLogicalPose;
    private final Method transformPositionInverse;
    private final Method getPlot;
    private final Method getEmbeddedLevelAccessor;
    private final Method getPlotChunkMin;
    private final Method getPlotChunkHolder;
    private final Method getChunkFromHolder;
    private final Field collisionTrackingSubLevel;
    private final Field collisionPreTrackingSubLevel;

    private static final double[][] CHECK_DIRECTIONS = buildDirections();
    private static final int[][] FLOOD_DIRECTIONS = {
        { 1, 0, 0 },
        { -1, 0, 0 },
        { 0, 1, 0 },
        { 0, -1, 0 },
        { 0, 0, 1 },
        { 0, 0, -1 }
    };

    private SableContraptionShelter()
    {
        ReflectionData data = ReflectionData.load();
        available = data != null;
        entityMovementExtensionClass = data == null ? null : data.entityMovementExtensionClass();
        getTrackingSubLevel = data == null ? null : data.getTrackingSubLevel();
        getCollisionInfo = data == null ? null : data.getCollisionInfo();
        getLastTrackingSubLevelId = data == null ? null : data.getLastTrackingSubLevelId();
        getContainer = data == null ? null : data.getContainer();
        getContainerSubLevel = data == null ? null : data.getContainerSubLevel();
        getLogicalPose = data == null ? null : data.getLogicalPose();
        transformPositionInverse = data == null ? null : data.transformPositionInverse();
        getPlot = data == null ? null : data.getPlot();
        getEmbeddedLevelAccessor = data == null ? null : data.getEmbeddedLevelAccessor();
        getPlotChunkMin = data == null ? null : data.getPlotChunkMin();
        getPlotChunkHolder = data == null ? null : data.getPlotChunkHolder();
        getChunkFromHolder = data == null ? null : data.getChunkFromHolder();
        collisionTrackingSubLevel = data == null ? null : data.collisionTrackingSubLevel();
        collisionPreTrackingSubLevel = data == null ? null : data.collisionPreTrackingSubLevel();
    }

    double enclosure(ServerPlayer player, int radius)
    {
        if (!available || !entityMovementExtensionClass.isInstance(player))
        {
            return 0.0D;
        }

        try
        {
            Object subLevel = findPlayerSubLevel(player);
            if (subLevel == null)
            {
                return 0.0D;
            }

            Object plot = getPlot.invoke(subLevel);
            Object accessor = getEmbeddedLevelAccessor.invoke(plot);
            if (!(accessor instanceof BlockGetter blockGetter))
            {
                return 0.0D;
            }

            Object pose = getLogicalPose.invoke(subLevel);
            Vec3 localPosition = (Vec3) transformPositionInverse.invoke(pose, new Vec3(player.getX(), player.getY() + 1.0D, player.getZ()));
            BlockPos origin = toLocalBlockPos(plot, BlockPos.containing(localPosition.x, localPosition.y, localPosition.z));
            return enclosure(plot, blockGetter, origin, radius);
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException exception)
        {
            ColdSweatAltitude.LOGGER.debug("Unable to evaluate Sable contraption shelter.", exception);
            return 0.0D;
        }
    }

    String diagnostic(ServerPlayer player, int radius)
    {
        if (!available)
        {
            return "unavailable";
        }
        if (!entityMovementExtensionClass.isInstance(player))
        {
            return "not_extended";
        }

        try
        {
            Object subLevel = findPlayerSubLevel(player);
            if (subLevel == null)
            {
                return "no_sublevel";
            }

            Object plot = getPlot.invoke(subLevel);
            Object accessor = getEmbeddedLevelAccessor.invoke(plot);
            if (!(accessor instanceof BlockGetter blockGetter))
            {
                return "no_block_getter";
            }

            Object pose = getLogicalPose.invoke(subLevel);
            Vec3 localPosition = (Vec3) transformPositionInverse.invoke(pose, new Vec3(player.getX(), player.getY() + 1.0D, player.getZ()));
            BlockPos plotOrigin = BlockPos.containing(localPosition.x, localPosition.y, localPosition.z);
            BlockPos origin = toLocalBlockPos(plot, plotOrigin);
            double enclosure = enclosure(plot, blockGetter, origin, radius);
            return "plotOrigin=" + plotOrigin.toShortString() + ", localOrigin=" + origin.toShortString()
                + ", enclosure=" + Math.round(enclosure * 100.0D) + "%";
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException exception)
        {
            ColdSweatAltitude.LOGGER.debug("Unable to evaluate Sable shelter diagnostics.", exception);
            return "error=" + exception.getClass().getSimpleName();
        }
    }

    private double enclosure(Object plot, BlockGetter level, BlockPos origin, int radius)
        throws ReflectiveOperationException
    {
        if (isEnclosedVolume(plot, level, origin, radius))
        {
            return 1.0D;
        }
        return directionalEnclosure(plot, level, origin, radius);
    }

    private double directionalEnclosure(Object plot, BlockGetter level, BlockPos origin, int radius)
        throws ReflectiveOperationException
    {
        int closedDirections = 0;

        for (double[] direction : CHECK_DIRECTIONS)
        {
            if (hasClosure(plot, level, origin, radius, direction[0], direction[1], direction[2]))
            {
                closedDirections++;
            }
        }

        return closedDirections / (double) CHECK_DIRECTIONS.length;
    }

    private boolean isEnclosedVolume(Object plot, BlockGetter level, BlockPos origin, int radius)
        throws ReflectiveOperationException
    {
        if (isClosureBlock(plot, level, origin))
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
                if (visited.contains(next) || isClosureBlock(plot, level, next))
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

    private boolean hasClosure(Object plot, BlockGetter level, BlockPos origin, int radius, double dx, double dy, double dz)
        throws ReflectiveOperationException
    {
        double originX = origin.getX() + 0.5D;
        double originY = origin.getY() + 0.5D;
        double originZ = origin.getZ() + 0.5D;

        for (double step = 0.75D; step <= radius; step += 0.5D)
        {
            BlockPos pos = BlockPos.containing(originX + dx * step, originY + dy * step, originZ + dz * step);
            if (isClosureBlock(plot, level, pos))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isClosureBlock(Object plot, BlockGetter level, BlockPos pos)
        throws ReflectiveOperationException
    {
        BlockState state = getBlockState(plot, level, pos);
        if (state.is(Blocks.AIR))
        {
            return false;
        }
        if (state.getBlock() instanceof DoorBlock)
        {
            return !state.getValue(DoorBlock.OPEN);
        }
        if (state.getBlock() instanceof TrapDoorBlock)
        {
            return !state.getValue(TrapDoorBlock.OPEN);
        }

        return !state.isAir();
    }

    private BlockState getBlockState(Object plot, BlockGetter level, BlockPos pos) throws ReflectiveOperationException
    {
        Object chunkHolder = getPlotChunkHolder.invoke(plot, new ChunkPos(pos));
        if (chunkHolder == null)
        {
            return Blocks.AIR.defaultBlockState();
        }

        Object chunk = getChunkFromHolder.invoke(chunkHolder);
        if (chunk instanceof LevelChunk levelChunk)
        {
            try
            {
                return levelChunk.getBlockState(pos);
            }
            catch (RuntimeException exception)
            {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return Blocks.AIR.defaultBlockState();
    }

    private BlockPos toLocalBlockPos(Object plot, BlockPos plotPos) throws ReflectiveOperationException
    {
        Object chunkMin = getPlotChunkMin.invoke(plot);
        if (chunkMin instanceof ChunkPos min)
        {
            return new BlockPos(plotPos.getX() - min.getMinBlockX(), plotPos.getY(), plotPos.getZ() - min.getMinBlockZ());
        }
        return plotPos;
    }

    private Object findPlayerSubLevel(ServerPlayer player) throws ReflectiveOperationException
    {
        Object subLevel = getTrackingSubLevel.invoke(player);
        if (subLevel != null)
        {
            return subLevel;
        }

        Object collisionInfo = getCollisionInfo.invoke(player);
        if (collisionInfo != null)
        {
            subLevel = collisionTrackingSubLevel.get(collisionInfo);
            if (subLevel != null)
            {
                return subLevel;
            }

            subLevel = collisionPreTrackingSubLevel.get(collisionInfo);
            if (subLevel != null)
            {
                return subLevel;
            }
        }

        Object lastTrackingId = getLastTrackingSubLevelId.invoke(player);
        if (lastTrackingId instanceof UUID uuid)
        {
            Object container = getContainer.invoke(null, (ServerLevel) player.level());
            return getContainerSubLevel.invoke(container, uuid);
        }

        return null;
    }

    private record ReflectionData(
        Class<?> entityMovementExtensionClass,
        Method getTrackingSubLevel,
        Method getCollisionInfo,
        Method getLastTrackingSubLevelId,
        Method getContainer,
        Method getContainerSubLevel,
        Method getLogicalPose,
        Method transformPositionInverse,
        Method getPlot,
        Method getEmbeddedLevelAccessor,
        Method getPlotChunkMin,
        Method getPlotChunkHolder,
        Method getChunkFromHolder,
        Field collisionTrackingSubLevel,
        Field collisionPreTrackingSubLevel)
    {
        static ReflectionData load()
        {
            try
            {
                Class<?> entityMovementExtensionClass = Class.forName("dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension");
                Class<?> subLevelContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                Class<?> levelPlotClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.LevelPlot");
                Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3dc");
                Class<?> plotChunkHolderClass = Class.forName("dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder");
                Class<?> collisionInfoClass = Class.forName("dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision$CollisionInfo");

                return new ReflectionData(
                    entityMovementExtensionClass,
                    entityMovementExtensionClass.getMethod("sable$getTrackingSubLevel"),
                    entityMovementExtensionClass.getMethod("sable$getCollisionInfo"),
                    entityMovementExtensionClass.getMethod("sable$getLastTrackingSubLevelID"),
                    subLevelContainerClass.getMethod("getContainer", ServerLevel.class),
                    subLevelContainerClass.getMethod("getSubLevel", UUID.class),
                    subLevelClass.getMethod("logicalPose"),
                    poseClass.getMethod("transformPositionInverse", Vec3.class),
                    subLevelClass.getMethod("getPlot"),
                    levelPlotClass.getMethod("getEmbeddedLevelAccessor"),
                    levelPlotClass.getMethod("getChunkMin"),
                    levelPlotClass.getMethod("getChunkHolder", ChunkPos.class),
                    plotChunkHolderClass.getMethod("getChunk"),
                    collisionInfoClass.getField("trackingSubLevel"),
                    collisionInfoClass.getField("preTrackingSubLevel"));
            }
            catch (ReflectiveOperationException | LinkageError exception)
            {
                return null;
            }
        }
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
