package net.sprocketgames.coldsweataltitude;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.sprocketgames.coldsweataltitude.command.AltitudeCommands;
import net.sprocketgames.coldsweataltitude.compat.ColdSweatCompat;
import net.sprocketgames.coldsweataltitude.config.AltitudeConfig;
import net.sprocketgames.coldsweataltitude.network.AltitudeNetwork;
import net.sprocketgames.coldsweataltitude.temperature.AltitudeTemperatureManager;
import org.slf4j.Logger;

@Mod(ColdSweatAltitude.MOD_ID)
public final class ColdSweatAltitude
{
    public static final String MOD_ID = "coldsweat_altitude";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ColdSweatAltitude(IEventBus modEventBus)
    {
        AltitudeConfig.bootstrap();
        ColdSweatCompat.logDependencyStatus();
        modEventBus.addListener(AltitudeNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AltitudeCommands::register);
        NeoForge.EVENT_BUS.addListener(AltitudeTemperatureManager.getInstance()::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(AltitudeTemperatureManager.getInstance()::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(AltitudeTemperatureManager.getInstance()::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(AltitudeTemperatureManager.getInstance()::onLivingDeath);
    }
}
