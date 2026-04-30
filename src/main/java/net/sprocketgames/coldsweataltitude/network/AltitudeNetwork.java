package net.sprocketgames.coldsweataltitude.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
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
    }

    public static void sendShelterStatus(ServerPlayer player, double shelterEnclosure)
    {
        double clamped = Math.max(0.0D, Math.min(1.0D, shelterEnclosure));
        PacketDistributor.sendToPlayer(player, new ShelterStatusPayload(clamped));
    }
}
