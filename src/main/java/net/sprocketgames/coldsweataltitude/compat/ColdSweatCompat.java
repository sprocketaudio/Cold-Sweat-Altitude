package net.sprocketgames.coldsweataltitude.compat;

import com.momosoftworks.coldsweat.api.temperature.modifier.SimpleTempModifier;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.placement.Placement;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;
import net.sprocketgames.coldsweataltitude.config.AltitudeBandConfig;

public final class ColdSweatCompat
{
    public static final String COLD_SWEAT_MOD_ID = "cold_sweat";
    private static final String ALTITUDE_MARKER = "ColdSweatAltitude";
    private static final String BAND_ID = "BandId";

    private ColdSweatCompat()
    {
    }

    public static boolean isLoaded()
    {
        return ModList.get().isLoaded(COLD_SWEAT_MOD_ID);
    }

    public static void logDependencyStatus()
    {
        if (isLoaded())
        {
            ColdSweatAltitude.LOGGER.info("Cold Sweat dependency detected. Altitude temperature bands are active.");
        }
        else
        {
            ColdSweatAltitude.LOGGER.error("Cold Sweat is not loaded, but this mod declares it as a required dependency.");
        }
    }

    public static void applyAltitudeModifier(ServerPlayer player, String bandId, double modifier, AltitudeBandConfig.ModifierMode mode)
    {
        if (isNeutral(modifier, mode))
        {
            removeAltitudeModifier(player);
            Temperature.updateTemperature(player);
            return;
        }

        SimpleTempModifier.Operation operation = mode == AltitudeBandConfig.ModifierMode.MULTIPLY
            ? SimpleTempModifier.Operation.MULTIPLY
            : SimpleTempModifier.Operation.ADD;

        if (Temperature.getModifier(player, Temperature.Trait.WORLD, ColdSweatCompat::isAltitudeModifier)
            .orElse(null) instanceof SimpleTempModifier existingModifier)
        {
            existingModifier.setTemperature(modifier);
            existingModifier.setOperation(operation);
            existingModifier.tickRate(1);
            existingModifier.getNBT().putString(BAND_ID, bandId);
            Temperature.updateModifiers(player);
            Temperature.updateTemperature(player);
            return;
        }

        SimpleTempModifier tempModifier = new SimpleTempModifier(modifier, operation).tickRate(1);
        tempModifier.getNBT().putBoolean(ALTITUDE_MARKER, true);
        tempModifier.getNBT().putString(BAND_ID, bandId);

        Temperature.addModifier(player, tempModifier, Temperature.Trait.WORLD, Placement.LAST);
        Temperature.updateModifiers(player);
        Temperature.updateTemperature(player);
    }

    public static void removeAltitudeModifier(ServerPlayer player)
    {
        Temperature.removeModifiers(player, Temperature.Trait.WORLD, ColdSweatCompat::isAltitudeModifier);
        Temperature.updateTemperature(player);
    }

    private static boolean isAltitudeModifier(TempModifier modifier)
    {
        return modifier instanceof SimpleTempModifier && modifier.getNBT().getBoolean(ALTITUDE_MARKER);
    }

    private static boolean isNeutral(double modifier, AltitudeBandConfig.ModifierMode mode)
    {
        double neutralValue = mode == AltitudeBandConfig.ModifierMode.MULTIPLY ? 1.0D : 0.0D;
        return Math.abs(modifier - neutralValue) < 0.000001D;
    }
}
