package net.sprocketgames.coldsweataltitude.compat.blocktemp;

import com.momosoftworks.coldsweat.api.temperature.block_temp.BlockTemp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.sprocketgames.coldsweataltitude.config.AltitudeConfig;

import java.lang.reflect.Method;

public final class AeronauticsHeatSourceBlockTemp extends BlockTemp
{
    private static final ResourceLocation ADJUSTABLE_BURNER_ID =
        ResourceLocation.fromNamespaceAndPath("aeronautics", "adjustable_burner");
    private static final ResourceLocation STEAM_VENT_ID =
        ResourceLocation.fromNamespaceAndPath("aeronautics", "steam_vent");

    private final HeatSourceType type;

    public AeronauticsHeatSourceBlockTemp(HeatSourceType type, Block... blocks)
    {
        super(blocks);
        this.type = type;
    }

    public static Block getAdjustableBurnerBlock()
    {
        return BuiltInRegistries.BLOCK.getOptional(ADJUSTABLE_BURNER_ID).orElse(null);
    }

    public static Block getSteamVentBlock()
    {
        return BuiltInRegistries.BLOCK.getOptional(STEAM_VENT_ID).orElse(null);
    }

    @Override
    public double getTemperature(Level level, LivingEntity entity, BlockState state, BlockPos pos, double distance)
    {
        if (!hasBlock(state.getBlock()))
        {
            return 0.0D;
        }

        return switch (type)
        {
            case ADJUSTABLE_BURNER -> burnerTemperature(level, state, pos, distance);
            case STEAM_VENT -> steamVentTemperature(level, state, pos, distance);
        };
    }

    private double burnerTemperature(Level level, BlockState state, BlockPos pos, double distance)
    {
        if (!isBurnerActive(level, state, pos))
        {
            return 0.0D;
        }

        AltitudeConfig.AeronauticsHeatSettings settings = AltitudeConfig.getAeronauticsHeatSettings();
        return falloff(distance, settings.burnerRange(), settings.burnerHeat());
    }

    private double steamVentTemperature(Level level, BlockState state, BlockPos pos, double distance)
    {
        if (!isSteamVentActive(level, state, pos))
        {
            return 0.0D;
        }

        AltitudeConfig.AeronauticsHeatSettings settings = AltitudeConfig.getAeronauticsHeatSettings();
        return falloff(distance, settings.steamVentRange(), settings.steamVentHeat());
    }

    private boolean isBurnerActive(Level level, BlockState state, BlockPos pos)
    {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null)
        {
            Boolean canOutput = invokeBoolean(blockEntity, "canOutputGas");
            if (canOutput != null)
            {
                return canOutput;
            }
        }
        return getBooleanProperty(state, "powered") || getEnumPropertyName(state, "variant") != null;
    }

    private boolean isSteamVentActive(Level level, BlockState state, BlockPos pos)
    {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity != null)
        {
            Boolean canOutput = invokeBoolean(blockEntity, "canOutputGas");
            if (canOutput != null)
            {
                return canOutput;
            }
        }
        return getBooleanProperty(state, "powered");
    }

    private static Boolean invokeBoolean(Object target, String methodName)
    {
        try
        {
            Method method = target.getClass().getMethod(methodName);
            Object result = method.invoke(target);
            return result instanceof Boolean value ? value : null;
        }
        catch (ReflectiveOperationException | RuntimeException exception)
        {
            return null;
        }
    }

    private static boolean getBooleanProperty(BlockState state, String propertyName)
    {
        return state.getProperties().stream()
            .filter(property -> property.getName().equals(propertyName))
            .findFirst()
            .map(property -> state.getValue(property))
            .filter(Boolean.class::isInstance)
            .map(Boolean.class::cast)
            .orElse(false);
    }

    private static String getEnumPropertyName(BlockState state, String propertyName)
    {
        return state.getProperties().stream()
            .filter(property -> property.getName().equals(propertyName))
            .findFirst()
            .map(property -> state.getValue(property))
            .map(Object::toString)
            .orElse(null);
    }

    private static double falloff(double distance, double range, double peak)
    {
        if (distance >= range)
        {
            return 0.0D;
        }

        double scale = 1.0D - distance / range;
        return Math.max(0.0D, peak * scale);
    }

    public enum HeatSourceType
    {
        ADJUSTABLE_BURNER,
        STEAM_VENT
    }
}
