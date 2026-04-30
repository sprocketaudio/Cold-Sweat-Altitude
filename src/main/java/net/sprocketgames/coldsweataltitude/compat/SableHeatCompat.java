package net.sprocketgames.coldsweataltitude.compat;

import com.momosoftworks.coldsweat.api.event.core.init.GatherDefaultTempModifiersEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.placement.Matcher;
import com.momosoftworks.coldsweat.api.util.placement.Placement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;
import net.sprocketgames.coldsweataltitude.compat.modifier.SableBlockTempModifier;
import net.sprocketgames.coldsweataltitude.compat.modifier.SableHearthModifier;

public final class SableHeatCompat
{
    private static final ResourceLocation SABLE_BLOCK_TEMP_ID =
        ResourceLocation.fromNamespaceAndPath(ColdSweatAltitude.MOD_ID, "sable_block_temp");
    private static final ResourceLocation SABLE_HEARTH_ID =
        ResourceLocation.fromNamespaceAndPath(ColdSweatAltitude.MOD_ID, "sable_hearth");

    private SableHeatCompat()
    {
    }

    public static void register()
    {
        NeoForge.EVENT_BUS.register(new EventHandler());
        ColdSweatAltitude.LOGGER.info("Registering Sable heat-source compat modifiers (block temps + hearth).");
    }

    private static final class EventHandler
    {
        @SubscribeEvent
        public void onTempModifierRegister(TempModifierRegisterEvent event)
        {
            event.register(SABLE_BLOCK_TEMP_ID, SableBlockTempModifier::new);
            event.register(SABLE_HEARTH_ID, SableHearthModifier::new);
        }

        @SubscribeEvent
        public void onGatherDefaultModifiers(GatherDefaultTempModifiersEvent event)
        {
            if (!(event.getEntity() instanceof Player) || event.getTrait() != Temperature.Trait.WORLD)
            {
                return;
            }

            add(event, new SableBlockTempModifier());
            add(event, new SableHearthModifier());
        }

        private void add(GatherDefaultTempModifiersEvent event, TempModifier modifier)
        {
            event.addModifier(modifier.tickRate(20), Matcher.SAME_CLASS, Placement.LAST);
        }
    }
}
