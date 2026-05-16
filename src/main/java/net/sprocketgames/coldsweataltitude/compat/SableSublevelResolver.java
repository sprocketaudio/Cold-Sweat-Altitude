package net.sprocketgames.coldsweataltitude.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

public final class SableSublevelResolver
{
    public static final SableSublevelResolver INSTANCE = new SableSublevelResolver();

    private Field plotContainerField;
    private Field containerSubLevelsField;
    private Field containerOriginXField;
    private Field containerOriginZField;
    private Field containerLogPlotSizeField;
    private Field containerLogSideLengthField;
    private Field containerAllSubLevelsField;
    private Field trackingSubLevelField;
    private Field collisionInfoField;
    private Field getPlotField;
    private Field getLevelField;
    private Field getPoseField;
    private Field getGlobalBoundsField;
    private Field plotLocalBoundsField;
    private Method plotBoundsContains;
    private Field collisionTrackingSubLevel;
    private Field collisionPreTrackingSubLevel;
    private Field boundsMinXField;
    private Field boundsMinYField;
    private Field boundsMinZField;
    private Field boundsMaxXField;
    private Field boundsMaxYField;
    private Field boundsMaxZField;

    private SableSublevelResolver()
    {
    }

    public SableSublevelContext resolve(Player player)
    {
        if (!(player.level() instanceof ServerLevel serverLevel))
        {
            return null;
        }

        try
        {
            Object container = getServerContainer(serverLevel);
            if (container == null)
            {
                return null;
            }

            Object subLevel = findContainingSubLevel(container, player.position());
            Vec3 plotPosition;

            if (subLevel != null)
            {
                // When the entity is already inside Sable's plot grid, its server position is already plot-space.
                plotPosition = player.position();
            }
            else
            {
                subLevel = findTrackingOrVehicleOrCollisionSubLevel(container, player);
                if (subLevel == null)
                {
                    subLevel = findIntersectingSubLevel(container, player);
                    if (subLevel == null)
                    {
                        return null;
                    }
                }

                Object pose = poseField(subLevel).get(subLevel);
                plotPosition = transformPosition(pose, "transformPositionInverse", player.position());
            }

            if (!isInsidePlotBounds(subLevel, plotPosition))
            {
                return null;
            }

            Object levelObject = levelField(subLevel).get(subLevel);
            if (!(levelObject instanceof Level level))
            {
                return null;
            }

            return new SableSublevelContext(subLevel, level, plotPosition, BlockPos.containing(plotPosition));
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            ColdSweatAltitude.LOGGER.debug("Unable to resolve Sable sublevel context.", exception);
            return null;
        }
    }

    private Object getServerContainer(ServerLevel serverLevel) throws ReflectiveOperationException
    {
        if (plotContainerField == null)
        {
            plotContainerField = serverLevel.getClass().getDeclaredField("sable$plotContainer");
            plotContainerField.setAccessible(true);
        }
        return plotContainerField.get(serverLevel);
    }

    private Object findContainingSubLevel(Object container, Vec3 position) throws ReflectiveOperationException
    {
        BlockPos blockPos = BlockPos.containing(position);
        int chunkX = blockPos.getX() >> 4;
        int chunkZ = blockPos.getZ() >> 4;

        int logPlotSize = (int) containerLogPlotSizeField(container).get(container);
        int originX = (int) containerOriginXField(container).get(container);
        int originZ = (int) containerOriginZField(container).get(container);
        int logSideLength = (int) containerLogSideLengthField(container).get(container);

        int plotX = (chunkX >> logPlotSize) - originX;
        int plotZ = (chunkZ >> logPlotSize) - originZ;
        int sideLength = 1 << logSideLength;
        if (plotX < 0 || plotX >= sideLength || plotZ < 0 || plotZ >= sideLength)
        {
            return null;
        }

        Object[] subLevels = (Object[]) containerSubLevelsField(container).get(container);
        return subLevels[plotX + (plotZ << logSideLength)];
    }

    private Object findTrackingOrVehicleOrCollisionSubLevel(Object container, Player player) throws ReflectiveOperationException
    {
        Object subLevel = trackingSubLevel(player);
        if (subLevel != null)
        {
            return subLevel;
        }

        Entity vehicle = player.getVehicle();
        if (vehicle != null)
        {
            subLevel = findContainingSubLevel(container, vehicle.position());
            if (subLevel != null)
            {
                return subLevel;
            }

            subLevel = trackingSubLevel(vehicle);
            if (subLevel != null)
            {
                return subLevel;
            }
        }

        return findCollisionSubLevel(player);
    }

    private Object findIntersectingSubLevel(Object container, Player player) throws ReflectiveOperationException
    {
        Object allSubLevelsObject = containerAllSubLevelsField(container).get(container);
        if (!(allSubLevelsObject instanceof Iterable<?> allSubLevels))
        {
            return null;
        }

        AABB playerBox = player.getBoundingBox().inflate(0.1D);
        Vec3 playerCenter = player.position();

        Object intersecting = null;
        for (Object subLevel : allSubLevels)
        {
            if (subLevel == null)
            {
                continue;
            }

            if (!globalBoundsIntersects(subLevel, playerBox))
            {
                continue;
            }

            Vec3 plotPosition = transformPosition(poseField(subLevel).get(subLevel), "transformPositionInverse", playerCenter);
            if (!isInsidePlotBounds(subLevel, plotPosition))
            {
                continue;
            }

            intersecting = subLevel;
            break;
        }
        return intersecting;
    }

    private Object trackingSubLevel(Entity entity) throws ReflectiveOperationException
    {
        return trackingSubLevelField(entity).get(entity);
    }

    private Object findCollisionSubLevel(Player player) throws ReflectiveOperationException
    {
        Object collisionInfo = collisionInfoField(player).get(player);
        if (collisionInfo == null)
        {
            return null;
        }

        if (collisionTrackingSubLevel == null)
        {
            collisionTrackingSubLevel = collisionInfo.getClass().getField("trackingSubLevel");
        }
        Object subLevel = collisionTrackingSubLevel.get(collisionInfo);
        if (subLevel != null)
        {
            return subLevel;
        }

        if (collisionPreTrackingSubLevel == null)
        {
            collisionPreTrackingSubLevel = collisionInfo.getClass().getField("preTrackingSubLevel");
        }
        return collisionPreTrackingSubLevel.get(collisionInfo);
    }

    private boolean isInsidePlotBounds(Object subLevel, Vec3 plotPosition) throws ReflectiveOperationException
    {
        if (plotPosition == null)
        {
            return false;
        }

        Object plot = plotField(subLevel).get(subLevel);
        if (plot == null)
        {
            return false;
        }

        Object bounds = plotLocalBoundsField(plot).get(plot);
        if (bounds == null)
        {
            return false;
        }

        if (plotBoundsContains == null || plotBoundsContains.getDeclaringClass() != bounds.getClass())
        {
            plotBoundsContains = Objects.requireNonNull(
                findMethod(bounds.getClass(), "contains", int.class, int.class, int.class),
                "Missing bounds contains method");
        }

        BlockPos pos = BlockPos.containing(plotPosition);
        return Boolean.TRUE.equals(plotBoundsContains.invoke(bounds, pos.getX(), pos.getY(), pos.getZ()));
    }

    private Vec3 transformPosition(Object pose, String methodName, Vec3 position) throws ReflectiveOperationException
    {
        Method vec3Method = findMethod(pose.getClass(), methodName, Vec3.class);
        if (vec3Method != null)
        {
            Object transformed = vec3Method.invoke(pose, position);
            if (transformed instanceof Vec3 vec3)
            {
                return vec3;
            }
        }

        Method vectorMethod = findVectorTransformMethod(pose.getClass(), methodName);
        if (vectorMethod == null)
        {
            throw new NoSuchMethodException(pose.getClass().getName() + "." + methodName);
        }

        Object transformed = vectorMethod.invoke(pose, new Vector3d(position.x, position.y, position.z));
        if (transformed instanceof Vector3dc vector)
        {
            return new Vec3(vector.x(), vector.y(), vector.z());
        }
        throw new NoSuchMethodException(pose.getClass().getName() + "." + methodName + " returned " + transformed);
    }

    private Method findVectorTransformMethod(Class<?> owner, String name)
    {
        for (Method candidate : owner.getMethods())
        {
            if (!candidate.getName().equals(name) || candidate.getParameterCount() != 1)
            {
                continue;
            }

            Class<?> parameterType = candidate.getParameterTypes()[0];
            if (Vector3dc.class.isAssignableFrom(parameterType) || parameterType.isAssignableFrom(Vector3d.class))
            {
                return candidate;
            }
        }
        return null;
    }

    private Method findMethod(Class<?> owner, String name, Class<?>... parameterTypes)
    {
        Class<?> current = owner;
        while (current != null)
        {
            try
            {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            }
            catch (NoSuchMethodException ignored)
            {
                current = current.getSuperclass();
            }
        }

        for (Class<?> iface : owner.getInterfaces())
        {
            try
            {
                Method method = iface.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            }
            catch (NoSuchMethodException ignored)
            {
            }
        }

        return null;
    }

    private Field containerSubLevelsField(Object container) throws NoSuchFieldException
    {
        if (containerSubLevelsField == null)
        {
            containerSubLevelsField = findField(container.getClass(), "subLevels");
        }
        return containerSubLevelsField;
    }

    private Field containerOriginXField(Object container) throws NoSuchFieldException
    {
        if (containerOriginXField == null)
        {
            containerOriginXField = findField(container.getClass(), "originX");
        }
        return containerOriginXField;
    }

    private Field containerOriginZField(Object container) throws NoSuchFieldException
    {
        if (containerOriginZField == null)
        {
            containerOriginZField = findField(container.getClass(), "originZ");
        }
        return containerOriginZField;
    }

    private Field containerLogPlotSizeField(Object container) throws NoSuchFieldException
    {
        if (containerLogPlotSizeField == null)
        {
            containerLogPlotSizeField = findField(container.getClass(), "logPlotSize");
        }
        return containerLogPlotSizeField;
    }

    private Field containerLogSideLengthField(Object container) throws NoSuchFieldException
    {
        if (containerLogSideLengthField == null)
        {
            containerLogSideLengthField = findField(container.getClass(), "logSideLength");
        }
        return containerLogSideLengthField;
    }

    private Field containerAllSubLevelsField(Object container) throws NoSuchFieldException
    {
        if (containerAllSubLevelsField == null)
        {
            containerAllSubLevelsField = findField(container.getClass(), "allSubLevels");
        }
        return containerAllSubLevelsField;
    }

    private Field trackingSubLevelField(Entity entity) throws NoSuchFieldException
    {
        if (trackingSubLevelField == null)
        {
            trackingSubLevelField = findField(entity.getClass(), "sable$trackingSubLevel");
        }
        return trackingSubLevelField;
    }

    private Field collisionInfoField(Player player) throws NoSuchFieldException
    {
        if (collisionInfoField == null)
        {
            collisionInfoField = findField(player.getClass(), "sable$collisionInfo");
        }
        return collisionInfoField;
    }

    private Field poseField(Object subLevel) throws NoSuchFieldException
    {
        if (getPoseField == null)
        {
            getPoseField = findField(subLevel.getClass(), "pose");
        }
        return getPoseField;
    }

    private Field globalBoundsField(Object subLevel) throws NoSuchFieldException
    {
        if (getGlobalBoundsField == null)
        {
            getGlobalBoundsField = findField(subLevel.getClass(), "globalBounds");
        }
        return getGlobalBoundsField;
    }

    private Field levelField(Object subLevel) throws NoSuchFieldException
    {
        if (getLevelField == null)
        {
            getLevelField = findField(subLevel.getClass(), "level");
        }
        return getLevelField;
    }

    private Field plotField(Object subLevel) throws NoSuchFieldException
    {
        if (getPlotField == null)
        {
            getPlotField = findField(subLevel.getClass(), "plot");
        }
        return getPlotField;
    }

    private Field plotLocalBoundsField(Object plot) throws NoSuchFieldException
    {
        if (plotLocalBoundsField == null)
        {
            plotLocalBoundsField = findField(plot.getClass(), "localBounds");
        }
        return plotLocalBoundsField;
    }

    private boolean globalBoundsIntersects(Object subLevel, AABB playerBox) throws ReflectiveOperationException
    {
        Object bounds = globalBoundsField(subLevel).get(subLevel);
        if (bounds == null)
        {
            return false;
        }

        double minX = ((Number) minXField(bounds).get(bounds)).doubleValue();
        double minY = ((Number) minYField(bounds).get(bounds)).doubleValue();
        double minZ = ((Number) minZField(bounds).get(bounds)).doubleValue();
        double maxX = ((Number) maxXField(bounds).get(bounds)).doubleValue();
        double maxY = ((Number) maxYField(bounds).get(bounds)).doubleValue();
        double maxZ = ((Number) maxZField(bounds).get(bounds)).doubleValue();

        return playerBox.maxX > minX && playerBox.minX < maxX
            && playerBox.maxY > minY && playerBox.minY < maxY
            && playerBox.maxZ > minZ && playerBox.minZ < maxZ;
    }

    private Field minXField(Object bounds) throws NoSuchFieldException
    {
        if (boundsMinXField == null)
        {
            boundsMinXField = findField(bounds.getClass(), "minX");
        }
        return boundsMinXField;
    }

    private Field minYField(Object bounds) throws NoSuchFieldException
    {
        if (boundsMinYField == null)
        {
            boundsMinYField = findField(bounds.getClass(), "minY");
        }
        return boundsMinYField;
    }

    private Field minZField(Object bounds) throws NoSuchFieldException
    {
        if (boundsMinZField == null)
        {
            boundsMinZField = findField(bounds.getClass(), "minZ");
        }
        return boundsMinZField;
    }

    private Field maxXField(Object bounds) throws NoSuchFieldException
    {
        if (boundsMaxXField == null)
        {
            boundsMaxXField = findField(bounds.getClass(), "maxX");
        }
        return boundsMaxXField;
    }

    private Field maxYField(Object bounds) throws NoSuchFieldException
    {
        if (boundsMaxYField == null)
        {
            boundsMaxYField = findField(bounds.getClass(), "maxY");
        }
        return boundsMaxYField;
    }

    private Field maxZField(Object bounds) throws NoSuchFieldException
    {
        if (boundsMaxZField == null)
        {
            boundsMaxZField = findField(bounds.getClass(), "maxZ");
        }
        return boundsMaxZField;
    }

    private Field findField(Class<?> owner, String name) throws NoSuchFieldException
    {
        Class<?> current = owner;
        while (current != null)
        {
            try
            {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            }
            catch (NoSuchFieldException ignored)
            {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(owner.getName() + "." + name);
    }
}
