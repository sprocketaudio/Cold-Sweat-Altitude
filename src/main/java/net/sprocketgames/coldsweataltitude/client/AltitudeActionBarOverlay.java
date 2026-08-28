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
import net.sprocketgames.coldsweataltitude.util.UiText;

@EventBusSubscriber(modid = ColdSweatAltitude.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class AltitudeActionBarOverlay
{
    private static final ResourceLocation LAYER_ID = ResourceLocation.fromNamespaceAndPath(ColdSweatAltitude.MOD_ID, "altitude_action_bar");

    private AltitudeActionBarOverlay()
    {
    }

    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event)
    {
        event.registerAbove(VanillaGuiLayers.HOTBAR, LAYER_ID, AltitudeActionBarOverlay::render);
    }

    private static void render(GuiGraphics guiGraphics, net.minecraft.client.DeltaTracker deltaTracker)
    {
        float opacity = AltitudeActionBarState.opacity();
        Minecraft minecraft = Minecraft.getInstance();
        int alpha = Math.round(opacity * 255.0F);
        // Font forces colours with a near-zero alpha to opaque, which causes a
        // bright flash at the end of a fade. Skip those final invisible frames.
        if (alpha < 4 || minecraft.options.hideGui || minecraft.player == null || minecraft.font == null)
        {
            return;
        }

        String text = UiText.fromConfigMessage(AltitudeActionBarState.message()).getString();
        int x = (guiGraphics.guiWidth() - minecraft.font.width(text)) / 2;
        int y = guiGraphics.guiHeight() - 68;
        int color = (alpha << 24)
            | UiText.temperatureColor(AltitudeActionBarState.temperatureDelta());
        guiGraphics.drawString(minecraft.font, text, x, y, color, true);
    }
}
