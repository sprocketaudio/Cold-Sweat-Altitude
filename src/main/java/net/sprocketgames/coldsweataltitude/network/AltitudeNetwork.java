package net.sprocketgames.coldsweataltitude.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.sprocketgames.coldsweataltitude.client.AltitudeActionBarState;
import net.sprocketgames.coldsweataltitude.client.ShelterHudState;

public final class AltitudeNetwork
{
    private AltitudeNetwork()
    {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event)
    {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
            ShelterStatusPayload.TYPE,
            ShelterStatusPayload.STREAM_CODEC,
            (payload, context) -> ShelterHudState.update(payload.shelterEnclosure()));
        registrar.playToClient(
            AltitudeActionBarPayload.TYPE,
            AltitudeActionBarPayload.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(
                () -> AltitudeActionBarState.update(payload.message(), payload.temperatureDelta(), payload.displayTicks())));
    }

    public static void sendShelterStatus(ServerPlayer player, double shelterEnclosure)
    {
        double clamped = Math.max(0.0D, Math.min(1.0D, shelterEnclosure));
        PacketDistributor.sendToPlayer(player, new ShelterStatusPayload(clamped));
    }

    public static void sendAltitudeActionBar(ServerPlayer player, String message, double temperatureDelta, int displayTicks)
    {
        PacketDistributor.sendToPlayer(player, new AltitudeActionBarPayload(message, temperatureDelta, displayTicks));
    }
}
