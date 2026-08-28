package net.sprocketgames.coldsweataltitude.compat;

import com.momosoftworks.coldsweat.api.event.core.init.GatherDefaultTempModifiersEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.BlockTempRegisterEvent;
import com.momosoftworks.coldsweat.api.event.core.registry.TempModifierRegisterEvent;
import com.momosoftworks.coldsweat.api.temperature.modifier.TempModifier;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.placement.Matcher;
import com.momosoftworks.coldsweat.api.util.placement.Placement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;
import net.sprocketgames.coldsweataltitude.compat.blocktemp.AeronauticsHeatSourceBlockTemp;
import net.sprocketgames.coldsweataltitude.compat.modifier.SableBlockTempModifier;
import net.sprocketgames.coldsweataltitude.compat.modifier.SableHearthModifier;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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
        ColdSweatAltitude.LOGGER.info("Registering Sable heat-source compat block temperatures and Sable-only temp modifiers.");
    }

    private static final class EventHandler
    {
        private final Map<UUID, Level> playerSublevels = new HashMap<>();

        @SubscribeEvent
        public void onTempModifierRegister(TempModifierRegisterEvent event)
        {
            event.register(SABLE_BLOCK_TEMP_ID, SableBlockTempModifier::new);
            event.register(SABLE_HEARTH_ID, SableHearthModifier::new);
        }

        @SubscribeEvent
        public void onBlockTempRegister(BlockTempRegisterEvent event)
        {
            registerBlockTemp(event,
                AeronauticsHeatSourceBlockTemp.HeatSourceType.ADJUSTABLE_BURNER,
                AeronauticsHeatSourceBlockTemp.getAdjustableBurnerBlock());
            registerBlockTemp(event,
                AeronauticsHeatSourceBlockTemp.HeatSourceType.STEAM_VENT,
                AeronauticsHeatSourceBlockTemp.getSteamVentBlock());
        }

        @SubscribeEvent
        public void onGatherDefaultModifiers(GatherDefaultTempModifiersEvent event)
        {
            // Cold Sweat 2.4.1's sublevel helper still only implements Valkyrien transforms in source.
            // Keep Altitude's custom modifiers active for Sable until upstream native support covers it.
            if (!(event.getEntity() instanceof Player) || event.getTrait() != Temperature.Trait.WORLD)
            {
                return;
            }

            add(event, new SableBlockTempModifier());
            add(event, new SableHearthModifier());
        }

        @SubscribeEvent
        public void onPlayerTick(PlayerTickEvent.Post event)
        {
            if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide())
            {
                return;
            }

            SableSublevelContext context = SableSublevelResolver.INSTANCE.resolve(player);
            Level currentSublevel = context == null ? null : context.level();
            Level previousSublevel = playerSublevels.get(player.getUUID());
            if (previousSublevel == currentSublevel)
            {
                return;
            }

            if (currentSublevel == null)
            {
                playerSublevels.remove(player.getUUID());
            }
            else
            {
                playerSublevels.put(player.getUUID(), currentSublevel);
            }

            // Sable modifiers normally update every 20 ticks.  Refresh them
            // immediately when a contraption is assembled, disassembled, or
            // recreated, so the old and new block scanners never leave a
            // visible temperature gap during the handover.
            Temperature.forEachModifier(player, Temperature.Trait.WORLD, modifier -> {
                if (modifier instanceof SableBlockTempModifier || modifier instanceof SableHearthModifier)
                {
                    modifier.update(0.0D, player, Temperature.Trait.WORLD);
                }
            });
            Temperature.updateTemperature(player);
        }

        private void add(GatherDefaultTempModifiersEvent event, TempModifier modifier)
        {
            event.addModifier(modifier.tickRate(20), Matcher.SAME_CLASS, Placement.LAST);
        }

        private void registerBlockTemp(BlockTempRegisterEvent event,
                                       AeronauticsHeatSourceBlockTemp.HeatSourceType type,
                                       net.minecraft.world.level.block.Block block)
        {
            if (block != null)
            {
                event.register(new AeronauticsHeatSourceBlockTemp(type, block));
            }
        }
    }
}
