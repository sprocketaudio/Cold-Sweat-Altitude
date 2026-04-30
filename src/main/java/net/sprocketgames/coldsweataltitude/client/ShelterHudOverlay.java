package net.sprocketgames.coldsweataltitude.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;

@EventBusSubscriber(modid = ColdSweatAltitude.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ShelterHudOverlay
{
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(ColdSweatAltitude.MOD_ID, "shelter_hud");
    private static final double MIN_VISIBLE_ENCLOSURE = 0.01D;

    private ShelterHudOverlay()
    {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event)
    {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER_ID, ShelterHudOverlay::render);
    }

    private static void render(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker)
    {
        double enclosure = ShelterHudState.shelterEnclosure();
        if (enclosure < MIN_VISIBLE_ENCLOSURE)
        {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null || minecraft.font == null)
        {
            return;
        }

        int percent = Math.max(1, Math.min(100, (int) Math.round(enclosure * 100.0D)));
        String label = "\u2302 " + percent + "%";
        int width = minecraft.font.width(label);
        int x = guiGraphics.guiWidth() - width - 8;
        int y = guiGraphics.guiHeight() - 72;
        int color = shelterColor(percent);

        guiGraphics.fill(x - 3, y - 3, x + width + 4, y + 11, 0x99000000);
        guiGraphics.drawString(minecraft.font, label, x, y, color, true);
    }

    private static int shelterColor(int percent)
    {
        if (percent >= 90)
        {
            return 0xFF9EE6FF;
        }
        if (percent >= 45)
        {
            return 0xFFFFD166;
        }
        return 0xFFFF8A5B;
    }
}
