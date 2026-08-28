package net.sprocketgames.coldsweataltitude.temperature;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;
import net.sprocketgames.coldsweataltitude.compat.ColdSweatCompat;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelContext;
import net.sprocketgames.coldsweataltitude.compat.SableSublevelResolver;
import net.sprocketgames.coldsweataltitude.config.AltitudeBandConfig;
import net.sprocketgames.coldsweataltitude.config.AltitudeConfig;
import net.sprocketgames.coldsweataltitude.network.AltitudeNetwork;
import net.sprocketgames.coldsweataltitude.player.PlayerAltitudeState;
import net.sprocketgames.coldsweataltitude.protection.AltitudeProtectionManager;
import net.sprocketgames.coldsweataltitude.shelter.ShelterManager;
import net.sprocketgames.coldsweataltitude.util.UiText;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AltitudeTemperatureManager
{
    // Sable transform math can leave a stationary player a few thousandths of
    // a block below an integer after assembly. Treat that as the intended
    // block Y so a player standing at a band boundary does not flicker between
    // bands (and between their shelter settings).
    private static final double ALTITUDE_Y_EPSILON = 0.1D;
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
        int y = altitudeY(player);

        return AltitudeConfig.getBands().stream()
            .filter(band -> band.matches(dimensionId, y))
            .findFirst();
    }

    /**
     * Exposes Sable's coordinate transform for diagnostics only. Altitude band
     * matching deliberately continues to use the player's server position.
     */
    public CoordinateDiagnostic coordinateDiagnostic(ServerPlayer player)
    {
        SableSublevelContext context = SableSublevelResolver.INSTANCE.resolve(player);
        if (context == null)
        {
            return new CoordinateDiagnostic(player.position(), null, null);
        }

        return new CoordinateDiagnostic(
            player.position(),
            context.worldPosition(),
            context.localPosition());
    }

    public record CoordinateDiagnostic(net.minecraft.world.phys.Vec3 playerPos,
                                       net.minecraft.world.phys.Vec3 sableWorldPos,
                                       net.minecraft.world.phys.Vec3 sableLocalPos)
    {
        public String playerDisplay()
        {
            return format(playerPos);
        }

        public String sableWorldDisplay()
        {
            return sableWorldPos == null ? "n/a" : format(sableWorldPos);
        }

        public String sableLocalDisplay()
        {
            return sableLocalPos == null ? "n/a" : format(sableLocalPos);
        }

        private static String format(net.minecraft.world.phys.Vec3 pos)
        {
            return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f", pos.x, pos.y, pos.z);
        }
    }

    public PlayerAltitudeState refreshState(ServerPlayer player)
    {
        Optional<AltitudeBand> matchingBand = findMatchingBand(player);
        AltitudeBand band = matchingBand.orElse(null);

        double protectionMultiplier = band == null
            ? 1.0D
            : AltitudeProtectionManager.INSTANCE.protectionMultiplier(player, band);
        double shelterEnclosure = band == null
            ? 0.0D
            : ShelterManager.INSTANCE.shelterEnclosure(player, band);
        double shelterMultiplier = band == null
            ? 1.0D
            : ShelterManager.INSTANCE.shelterMultiplier(band, shelterEnclosure);

        PlayerAltitudeState state = playerStates.computeIfAbsent(player.getUUID(), ignored -> new PlayerAltitudeState());
        state.refresh(band, band == null ? 0.0D : rawModifier(player, band), UPDATE_INTERVAL_TICKS, protectionMultiplier, shelterMultiplier, shelterEnclosure);
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

        if (serverPlayer.isSpectator() || !serverPlayer.isAlive())
        {
            clear(serverPlayer);
            return;
        }

        Optional<AltitudeBand> activeBand = findMatchingBand(serverPlayer);
        PlayerAltitudeState state = refreshState(serverPlayer);
        if (activeBand.isEmpty())
        {
            if (AltitudeConfig.debugLogging())
            {
                ColdSweatAltitude.LOGGER.debug("Altitude update for {}: no matching band at raw Y={}, sending shelter 0%.",
                    serverPlayer.getGameProfile().getName(), formatY(serverPlayer.getY()));
            }
            clear(serverPlayer);
            return;
        }

        AltitudeBand band = activeBand.get();
        if (AltitudeConfig.debugLogging() && state.bandChanged())
        {
            ColdSweatAltitude.LOGGER.debug("Altitude update for {}: band={} at raw Y={}, shelter={}%, sending HUD update.",
                serverPlayer.getGameProfile().getName(), band.id(), formatY(serverPlayer.getY()),
                Math.round(state.shelterEnclosure() * 100.0D));
        }
        ColdSweatCompat.applyAltitudeModifier(serverPlayer, band.id(), state.finalModifier(), band.modifierMode());
        AltitudeNetwork.sendShelterStatus(serverPlayer, state.shelterEnclosure());
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
        AltitudeNetwork.sendShelterStatus(player, 0.0D);
    }

    private void sendWarnings(ServerPlayer player, AltitudeBand band, PlayerAltitudeState state)
    {
        long gameTime = player.level().getGameTime();
        if (!state.bandChanged())
        {
            return;
        }

        if (!band.actionbarMessage().isBlank())
        {
            double rawModifier = rawModifier(player, band);
            double temperatureDelta = band.modifierMode() == AltitudeBandConfig.ModifierMode.MULTIPLY
                ? rawModifier - 1.0D
                : rawModifier;
            AltitudeNetwork.sendAltitudeActionBar(
                player, band.actionbarMessage(), temperatureDelta, band.actionbarDisplayTicks());
        }
        state.lastMessageTick(gameTime);
    }

    private static String formatY(double y)
    {
        return String.format(java.util.Locale.ROOT, "%.6f", y);
    }

    private static int altitudeY(ServerPlayer player)
    {
        return (int) Math.floor(player.getY() + ALTITUDE_Y_EPSILON);
    }

    public double rawModifier(ServerPlayer player, AltitudeBand band)
    {
        AltitudeConfig.BandGradientMode mode = AltitudeConfig.bandGradientMode();
        if (mode == AltitudeConfig.BandGradientMode.NONE)
        {
            return band.temperatureModifier();
        }

        ResourceLocation dimensionId = player.level().dimension().location();
        int y = altitudeY(player);
        if (mode == AltitudeConfig.BandGradientMode.LINEAR)
        {
            if (band.maxY() == null)
            {
                return band.temperatureModifier();
            }
            AltitudeBand next = adjacentBand(dimensionId, band.maxY() + 1);
            return band.modifierAtY(y, next == null ? band.temperatureModifier() : next.temperatureModifier());
        }

        AltitudeBand previous = previousBand(dimensionId, band.minY() - 1);
        int lowerEdge = boundaryEdge(previous, band);
        if (previous != null && y < band.minY() + lowerEdge)
        {
            double midpoint = (previous.temperatureModifier() + band.temperatureModifier()) / 2.0D;
            return net.minecraft.util.Mth.lerp((y - band.minY()) / (double) lowerEdge, midpoint, band.temperatureModifier());
        }

        AltitudeBand next = band.maxY() == null ? null : adjacentBand(dimensionId, band.maxY() + 1);
        int upperEdge = boundaryEdge(band, next);
        if (next != null && y > band.maxY() - upperEdge)
        {
            double midpoint = (band.temperatureModifier() + next.temperatureModifier()) / 2.0D;
            return net.minecraft.util.Mth.lerp((y - (band.maxY() - upperEdge)) / (double) upperEdge, band.temperatureModifier(), midpoint);
        }
        return band.temperatureModifier();
    }

    private static int boundaryEdge(AltitudeBand first, AltitudeBand second)
    {
        if (first == null || second == null)
        {
            return 0;
        }
        return Math.max(1, Math.min(bandHeight(first), bandHeight(second)) / 4);
    }

    private static int bandHeight(AltitudeBand band)
    {
        return band.maxY() == null ? Integer.MAX_VALUE : band.maxY() - band.minY() + 1;
    }

    private static AltitudeBand adjacentBand(ResourceLocation dimensionId, int y)
    {
        return AltitudeConfig.getBands().stream()
            .filter(candidate -> candidate.minY() == y && candidate.matches(dimensionId, y))
            .findFirst().orElse(null);
    }

    private static AltitudeBand previousBand(ResourceLocation dimensionId, int y)
    {
        return AltitudeConfig.getBands().stream()
            .filter(candidate -> candidate.maxY() != null && candidate.maxY() == y && candidate.matches(dimensionId, y))
            .findFirst().orElse(null);
    }
}
