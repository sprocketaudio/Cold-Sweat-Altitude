package net.sprocketgames.coldsweataltitude.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;

public record ShelterStatusPayload(double shelterEnclosure) implements CustomPacketPayload
{
    public static final CustomPacketPayload.Type<ShelterStatusPayload> TYPE = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(ColdSweatAltitude.MOD_ID, "shelter_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShelterStatusPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE.cast(),
        ShelterStatusPayload::shelterEnclosure,
        ShelterStatusPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type()
    {
        return TYPE;
    }
}
