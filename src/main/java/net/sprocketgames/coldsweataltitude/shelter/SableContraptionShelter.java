package net.sprocketgames.coldsweataltitude.shelter;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelContext;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelResolver;

import java.lang.reflect.Method;
import java.util.function.Predicate;

final class SableContraptionShelter
{
    static final SableContraptionShelter INSTANCE = new SableContraptionShelter();

    private Method getPlot;
    private Method getPlotBoundingBox;
    private Method plotBoundsContains;

    private SableContraptionShelter()
    {
    }

    double enclosure(ServerPlayer player, int radius)
    {
        try
        {
            SableSublevelContext context = SableSublevelResolver.INSTANCE.resolve(player);
            if (context == null || context.subLevelHandle() == null)
            {
                return 0.0D;
            }

            Object plot = plot(context.subLevelHandle());
            Vec3 plotPosition = context.localPosition().add(0.0D, 1.0D, 0.0D);
            BlockPos origin = BlockPos.containing(plotPosition.x, plotPosition.y, plotPosition.z);
            return ShelterManager.enclosure(player.level(), origin, radius, plotBounds(plot));
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException exception)
        {
            ColdSweatAltitude.LOGGER.debug("Unable to evaluate Sable contraption shelter.", exception);
            return 0.0D;
        }
    }

    String diagnostic(ServerPlayer player, int radius)
    {
        try
        {
            SableSublevelContext context = SableSublevelResolver.INSTANCE.resolve(player);
            if (context == null || context.subLevelHandle() == null)
            {
                return "no_sublevel";
            }

            Object plot = plot(context.subLevelHandle());
            Vec3 plotPosition = context.localPosition().add(0.0D, 1.0D, 0.0D);
            BlockPos origin = BlockPos.containing(plotPosition.x, plotPosition.y, plotPosition.z);
            double enclosure = ShelterManager.enclosure(player.level(), origin, radius, plotBounds(plot));
            return "localOrigin=" + origin.toShortString() + ", enclosure=" + Math.round(enclosure * 100.0D) + "%";
        }
        catch (ReflectiveOperationException | LinkageError | RuntimeException exception)
        {
            ColdSweatAltitude.LOGGER.debug("Unable to evaluate Sable shelter diagnostics.", exception);
            return "error=" + exception.getClass().getSimpleName();
        }
    }

    private Object plot(Object subLevel) throws ReflectiveOperationException
    {
        if (getPlot == null)
        {
            getPlot = subLevel.getClass().getMethod("getPlot");
        }
        return getPlot.invoke(subLevel);
    }

    private Predicate<BlockPos> plotBounds(Object plot) throws ReflectiveOperationException
    {
        if (getPlotBoundingBox == null)
        {
            getPlotBoundingBox = plot.getClass().getMethod("getBoundingBox");
        }

        Object bounds = getPlotBoundingBox.invoke(plot);
        if (bounds == null)
        {
            return pos -> false;
        }

        if (plotBoundsContains == null || plotBoundsContains.getDeclaringClass() != bounds.getClass())
        {
            plotBoundsContains = bounds.getClass().getMethod("contains", int.class, int.class, int.class);
        }

        return pos -> {
            try
            {
                return Boolean.TRUE.equals(plotBoundsContains.invoke(bounds, pos.getX(), pos.getY(), pos.getZ()));
            }
            catch (ReflectiveOperationException exception)
            {
                return false;
            }
        };
    }
}
