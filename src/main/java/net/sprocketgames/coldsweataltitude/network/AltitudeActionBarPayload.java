package net.sprocketgames.coldsweataltitude.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;

public record AltitudeActionBarPayload(String message, double temperatureDelta, int displayTicks) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<AltitudeActionBarPayload> TYPE = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(ColdSweatAltitude.MOD_ID, "altitude_action_bar"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AltitudeActionBarPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        AltitudeActionBarPayload::message,
        ByteBufCodecs.DOUBLE.cast(),
        AltitudeActionBarPayload::temperatureDelta,
        ByteBufCodecs.VAR_INT,
        AltitudeActionBarPayload::displayTicks,
        AltitudeActionBarPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
