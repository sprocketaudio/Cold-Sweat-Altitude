package net.sprocketgames.coldsweataltitude.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.sprocketgames.coldsweataltitude.config.AltitudeConfig;
import net.sprocketgames.coldsweataltitude.player.PlayerAltitudeState;
import net.sprocketgames.coldsweataltitude.shelter.ShelterManager;
import net.sprocketgames.coldsweataltitude.temperature.AltitudeBand;
import net.sprocketgames.coldsweataltitude.temperature.AltitudeTemperatureManager;

import java.util.Comparator;
import java.util.Optional;

public final class AltitudeCommands
{
    private AltitudeCommands()
    {
    }

    public static void register(RegisterCommandsEvent event)
    {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("coldsweat_altitude")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("status").executes(context -> status(context.getSource())))
            .then(Commands.literal("reload").executes(context -> reload(context.getSource())))
            .then(Commands.literal("list").executes(context -> list(context.getSource())));

        event.getDispatcher().register(root);
    }

    private static int status(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        AltitudeTemperatureManager manager = AltitudeTemperatureManager.getInstance();
        PlayerAltitudeState state = manager.refreshState(player);
        Optional<AltitudeBand> activeBand = manager.findMatchingBand(player);

        String bandId = activeBand.map(AltitudeBand::id).orElse("none");
        double modifier = activeBand.map(band -> band.effectiveModifier(state.protectionMultiplier(), state.shelterMultiplier())).orElse(0.0D);
        String shelterDetails = activeBand
            .map(band -> ", WorldShelter=" + Math.round(ShelterManager.INSTANCE.worldShelterEnclosure(player, band) * 100.0D) + "%"
                + ", SableShelter=" + Math.round(ShelterManager.INSTANCE.sableShelterEnclosure(player, band) * 100.0D) + "%"
                + ", Sable=" + ShelterManager.INSTANCE.sableDiagnostic(player, band))
            .orElse("");

        source.sendSuccess(() -> Component.literal(
            "Dimension=" + player.level().dimension().location()
                + ", Y=" + player.getBlockY()
                + ", Band=" + bandId
                + ", TempModifier=" + modifier
                + ", ProtectionReduction=" + (1.0D - state.protectionMultiplier())
                + ", ShelterReduction=" + (1.0D - state.shelterMultiplier())
                + ", Shelter=" + Math.round(state.shelterEnclosure() * 100.0D) + "%"
                + shelterDetails
                + ", TicksInBand=" + state.ticksInBand()),
            false);
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandSourceStack source)
    {
        int count = AltitudeConfig.reload().size();
        source.sendSuccess(() -> Component.literal("Reloaded Cold Sweat: Altitude config with " + count + " valid bands."), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandSourceStack source)
    {
        if (AltitudeConfig.getBands().isEmpty())
        {
            source.sendFailure(Component.literal("No valid altitude bands are currently loaded."));
            return 0;
        }

        for (AltitudeBand band : AltitudeConfig.getBands().stream().sorted(Comparator.comparingInt(AltitudeBand::priority).reversed()).toList())
        {
            source.sendSuccess(() -> Component.literal(
                band.id() + " | priority=" + band.priority()
                    + " | minY=" + band.minY()
                    + " | maxY=" + (band.maxY() == null ? "open" : band.maxY())
                    + " | mode=" + band.modifierMode()
                    + " | temp=" + band.temperatureModifier()),
                false);
        }
        return Command.SINGLE_SUCCESS;
    }
}
