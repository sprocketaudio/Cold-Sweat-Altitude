package net.sprocketgames.coldsweataltitude.protection;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import net.sprocketgames.coldsweataltitude.temperature.AltitudeBand;

public final class AltitudeProtectionManager
{
    public static final AltitudeProtectionManager INSTANCE = new AltitudeProtectionManager();

    private AltitudeProtectionManager()
    {
    }

    public double protectionMultiplier(Player player, AltitudeBand band)
    {
        TagKey<Item> protectionTag = band.protectionTag();
        if (protectionTag == null)
        {
            return 1.0D;
        }

        int protectedPieces = 0;
        for (ItemStack armorPiece : player.getInventory().armor)
        {
            if (!armorPiece.isEmpty() && armorPiece.is(protectionTag))
            {
                protectedPieces++;
            }
        }

        if (protectedPieces < band.requiredPieces())
        {
            return 1.0D;
        }

        if (band.fullProtectionPieces() > 0 && protectedPieces >= band.fullProtectionPieces())
        {
            return 0.0D;
        }

        double reduction = protectedPieces * band.protectionReductionPerPiece();
        return 1.0D - Mth.clamp(reduction, 0.0D, 1.0D);
    }

    public static TagKey<Item> parseTag(String id)
    {
        ResourceLocation resourceLocation = ResourceLocation.tryParse(id);
        return resourceLocation == null ? null : TagKey.create(net.minecraft.core.registries.Registries.ITEM, resourceLocation);
    }
}
