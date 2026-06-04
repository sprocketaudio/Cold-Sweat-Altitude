package net.sprocketgames.coldsweataltitude.compat.modifier;

import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.common.blockentity.HearthBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelContext;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class SableHearthModifier extends TempModifier
{
    private static final int HEARTH_SEARCH_RADIUS = 24;
    private static final Method HEARTH_AREA_CONTAINS_POS = findHearthAreaMethod("areaContainsPos");
    private static final Method HEARTH_IS_AFFECTING_POS = findHearthAreaMethod("isAffectingPos");

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

        Level level = context.level();
        Vec3 localPosition = context.localPosition();
        BlockPos center = context.localBlockPos();
        Vec3 delta = localPosition.subtract(player.position());
        AABB localBox = player.getBoundingBox().move(delta);
        localBox = localBox.setMaxY(Math.max(localBox.maxY, localBox.minY + 2.0D));
        List<BlockPos> occupiedPositions = occupiedPositions(localBox);

        for (BlockPos pos : BlockPos.betweenClosed(
            center.offset(-HEARTH_SEARCH_RADIUS, -HEARTH_SEARCH_RADIUS, -HEARTH_SEARCH_RADIUS),
            center.offset(HEARTH_SEARCH_RADIUS, HEARTH_SEARCH_RADIUS, HEARTH_SEARCH_RADIUS)))
        {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (!(blockEntity instanceof HearthBlockEntity hearth))
            {
                continue;
            }

            int maxRange = hearth.getMaxRange();
            double distanceSquared = Vec3.atCenterOf(pos).distanceToSqr(localBox.getCenter());
            if (distanceSquared > (double) maxRange * maxRange)
            {
                continue;
            }
            if (!hearthAffectsPositions(hearth, occupiedPositions))
            {
                continue;
            }

            tryInsulate(hearth, player);
        }

        return temp -> temp;
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

    private static boolean tryInsulate(HearthBlockEntity hearth, LivingEntity entity)
    {
        try
        {
            Method method = HearthBlockEntity.class.getDeclaredMethod("insulateEntity", LivingEntity.class);
            method.setAccessible(true);
            Object result = method.invoke(hearth, entity);
            return result instanceof Boolean value && value;
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            return false;
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
}
