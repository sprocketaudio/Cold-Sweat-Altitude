package net.sprocketgames.coldsweataltitude.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;

import java.lang.reflect.Method;
import java.util.List;

public final class SableSublevelResolver
{
    public static final SableSublevelResolver INSTANCE = new SableSublevelResolver();

    private final boolean available;
    private final Method getContainer;
    private final Method getAllSubLevels;
    private final Method getTrackingSubLevel;
    private final Method getPlotPosition;
    private final Method logicalPose;
    private final Method transformPosition;
    private final Method transformPositionInverse;
    private final Method boundingBox;
    private final Method boundingBoxContains;
    private final Method getLevel;

    private SableSublevelResolver()
    {
        ReflectionData data = ReflectionData.load();
        available = data != null;
        getContainer = data == null ? null : data.getContainer();
        getAllSubLevels = data == null ? null : data.getAllSubLevels();
        getTrackingSubLevel = data == null ? null : data.getTrackingSubLevel();
        getPlotPosition = data == null ? null : data.getPlotPosition();
        logicalPose = data == null ? null : data.logicalPose();
        transformPosition = data == null ? null : data.transformPosition();
        transformPositionInverse = data == null ? null : data.transformPositionInverse();
        boundingBox = data == null ? null : data.boundingBox();
        boundingBoxContains = data == null ? null : data.boundingBoxContains();
        getLevel = data == null ? null : data.getLevel();
    }

    public SableSublevelContext resolve(Player player)
    {
        if (!available || !(player.level() instanceof ServerLevel serverLevel))
        {
            return null;
        }

        try
        {
            Object container = getContainer.invoke(null, serverLevel);
            if (container == null)
            {
                return null;
            }

            Vec3 plotPosition = tryGetPlotPosition(player);
            Object subLevel = getTrackingSubLevel.invoke(player);

            if (subLevel == null && plotPosition != null)
            {
                subLevel = findBestMatchingSubLevelByLocal(container, plotPosition, player.position());
            }
            if (subLevel == null)
            {
                subLevel = findContainingSubLevelByGlobalBounds(container, player.position());
            }
            if (subLevel == null)
            {
                return null;
            }

            Vec3 localPosition = plotPosition;
            if (localPosition == null)
            {
                Object pose = logicalPose.invoke(subLevel);
                Object transformed = transformPositionInverse.invoke(pose, player.position());
                if (!(transformed instanceof Vec3 vec3))
                {
                    return null;
                }
                localPosition = vec3;
            }

            Object levelObject = getLevel.invoke(subLevel);
            if (!(levelObject instanceof Level level))
            {
                return null;
            }

            return new SableSublevelContext(level, localPosition, BlockPos.containing(localPosition));
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            ColdSweatAltitude.LOGGER.debug("Unable to resolve Sable sublevel context.", exception);
            return null;
        }
    }

    private Object findContainingSubLevelByGlobalBounds(Object container, Vec3 globalPosition) throws ReflectiveOperationException
    {
        Object subLevels = getAllSubLevels.invoke(container);
        if (!(subLevels instanceof List<?> list))
        {
            return null;
        }

        for (Object subLevel : list)
        {
            Object box = boundingBox.invoke(subLevel);
            if (box != null && (Boolean) boundingBoxContains.invoke(box, globalPosition.x, globalPosition.y, globalPosition.z))
            {
                return subLevel;
            }
        }
        return null;
    }

    private Object findBestMatchingSubLevelByLocal(Object container, Vec3 localPosition, Vec3 globalPosition) throws ReflectiveOperationException
    {
        Object subLevels = getAllSubLevels.invoke(container);
        if (!(subLevels instanceof List<?> list))
        {
            return null;
        }

        Object bestMatch = null;
        double bestDistance = Double.MAX_VALUE;

        for (Object subLevel : list)
        {
            Object pose = logicalPose.invoke(subLevel);
            Object transformed = transformPosition.invoke(pose, localPosition);
            if (!(transformed instanceof Vec3 transformedVec))
            {
                continue;
            }

            double distance = transformedVec.distanceToSqr(globalPosition);
            if (distance < bestDistance)
            {
                bestDistance = distance;
                bestMatch = subLevel;
            }
        }

        return bestDistance < 16.0D ? bestMatch : null;
    }

    private Vec3 tryGetPlotPosition(Player player)
    {
        try
        {
            Object result = getPlotPosition.invoke(player);
            return result instanceof Vec3 vec3 ? vec3 : null;
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            return null;
        }
    }

    private record ReflectionData(
        Method getContainer,
        Method getAllSubLevels,
        Method getTrackingSubLevel,
        Method getPlotPosition,
        Method logicalPose,
        Method transformPosition,
        Method transformPositionInverse,
        Method boundingBox,
        Method boundingBoxContains,
        Method getLevel)
    {
        static ReflectionData load()
        {
            try
            {
                Class<?> playerClass = Class.forName("net.minecraft.world.entity.player.Player");
                Class<?> serverLevelClass = Class.forName("net.minecraft.server.level.ServerLevel");
                Class<?> subLevelContainerClass = Class.forName("dev.ryanhcode.sable.api.sublevel.SubLevelContainer");
                Class<?> subLevelClass = Class.forName("dev.ryanhcode.sable.sublevel.SubLevel");
                Class<?> poseClass = Class.forName("dev.ryanhcode.sable.companion.math.Pose3d");
                Class<?> boundingBoxClass = Class.forName("dev.ryanhcode.sable.companion.math.BoundingBox3dc");

                return new ReflectionData(
                    subLevelContainerClass.getMethod("getContainer", serverLevelClass),
                    subLevelContainerClass.getMethod("getAllSubLevels"),
                    playerClass.getMethod("sable$getTrackingSubLevel"),
                    playerClass.getMethod("sable$getPlotPosition"),
                    subLevelClass.getMethod("logicalPose"),
                    poseClass.getMethod("transformPosition", Vec3.class),
                    poseClass.getMethod("transformPositionInverse", Vec3.class),
                    subLevelClass.getMethod("boundingBox"),
                    boundingBoxClass.getMethod("contains", double.class, double.class, double.class),
                    subLevelClass.getMethod("getLevel"));
            }
            catch (ReflectiveOperationException | LinkageError exception)
            {
                return null;
            }
        }
    }
}
