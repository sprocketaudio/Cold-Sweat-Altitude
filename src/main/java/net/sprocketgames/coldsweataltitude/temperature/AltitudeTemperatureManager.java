package net.sprocketgames.coldsweataltitude.temperature;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.sprocketgames.coldsweataltitude.compat.ColdSweatCompat;
import net.sprocketgames.coldsweataltitude.config.AltitudeConfig;
import net.sprocketgames.coldsweataltitude.player.PlayerAltitudeState;
import net.sprocketgames.coldsweataltitude.protection.AltitudeProtectionManager;
import net.sprocketgames.coldsweataltitude.shelter.ShelterManager;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AltitudeTemperatureManager
{
    public static final int UPDATE_INTERVAL_TICKS = 40;

    private static final AltitudeTemperatureManager INSTANCE = new AltitudeTemperatureManager();

    private final Map<UUID, PlayerAltitudeState> playerStates = new ConcurrentHashMap<>();

    private AltitudeTemperatureManager()
    {
    }

    public static AltitudeTemperatureManager getInstance()
    {
        return INSTANCE;
    }

    public Optional<AltitudeBand> findMatchingBand(ServerPlayer player)
    {
        ResourceLocation dimensionId = player.level().dimension().location();
        int y = player.getBlockY();

        return AltitudeConfig.getBands().stream()
            .filter(band -> band.matches(dimensionId, y))
            .findFirst();
    }

    public PlayerAltitudeState refreshState(ServerPlayer player)
    {
        Optional<AltitudeBand> matchingBand = findMatchingBand(player);
        AltitudeBand band = matchingBand.orElse(null);

        double protectionMultiplier = band == null
            ? 1.0D
            : AltitudeProtectionManager.INSTANCE.protectionMultiplier(player, band);
        double shelterMultiplier = band == null
            ? 1.0D
            : ShelterManager.INSTANCE.shelterMultiplier(player, band);

        PlayerAltitudeState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerAltitudeState());
        state.refresh(band, UPDATE_INTERVAL_TICKS, protectionMultiplier, shelterMultiplier);
        return state;
    }

    public void onPlayerTick(PlayerTickEvent.Post event)
    {
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer) || serverPlayer.level().isClientSide())
        {
            return;
        }

        if (serverPlayer.tickCount % UPDATE_INTERVAL_TICKS != 0)
        {
            return;
        }

        if (serverPlayer.isCreative() || serverPlayer.isSpectator() || !serverPlayer.isAlive())
        {
            clear(serverPlayer);
            return;
        }

        Optional<AltitudeBand> activeBand = findMatchingBand(serverPlayer);
        PlayerAltitudeState state = refreshState(serverPlayer);
        if (activeBand.isEmpty())
        {
            clear(serverPlayer);
            return;
        }

        AltitudeBand band = activeBand.get();
        ColdSweatCompat.applyAltitudeModifier(serverPlayer, band.id(), state.finalModifier(), band.modifierMode());
        sendWarnings(serverPlayer, band, state);
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            clear(player);
        }
    }

    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            clear(player);
        }
    }

    public void onLivingDeath(LivingDeathEvent event)
    {
        Entity entity = event.getEntity();
        if (entity instanceof ServerPlayer player)
        {
            clear(player);
        }
    }

    public void clear(ServerPlayer player)
    {
        playerStates.remove(player.getUUID());
        ColdSweatCompat.removeAltitudeModifier(player);
    }

    private void sendWarnings(ServerPlayer player, AltitudeBand band, PlayerAltitudeState state)
    {
        long gameTime = player.level().getGameTime();
        if (state.bandChanged() && !band.onEnterMessage().isBlank())
        {
            player.sendSystemMessage(Component.literal(band.onEnterMessage()));
            state.lastMessageTick(gameTime);
            return;
        }

        if (band.actionbarMessage().isBlank())
        {
            return;
        }

        int cooldown = Math.max(1, band.messageCooldownTicks());
        if (gameTime - state.lastMessageTick() >= cooldown)
        {
            player.displayClientMessage(Component.literal(band.actionbarMessage()), true);
            state.lastMessageTick(gameTime);
        }
    }
}
