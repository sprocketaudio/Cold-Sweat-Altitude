package net.sprocketgames.coldsweataltitude.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.sprocketgames.coldsweataltitude.compat.HeatDiagnostics;
import net.sprocketgames.coldsweataltitude.config.AltitudeConfig;
import net.sprocketgames.coldsweataltitude.config.AltitudeBandConfig;
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
        HeatDiagnostics.Report heat = HeatDiagnostics.collect(player);

        String bandId = activeBand.map(AltitudeBand::id).orElse("none");
        double rawModifier = activeBand.map(AltitudeBand::temperatureModifier).orElse(0.0D);
        double modifier = activeBand.map(band -> band.effectiveModifier(state.protectionMultiplier(), state.shelterMultiplier())).orElse(0.0D);
        String numericNet = activeBand
            .map(band -> band.modifierMode() == AltitudeBandConfig.ModifierMode.ADD
                ? formatDouble(modifier + heat.total())
                : "n/a-multiply")
            .orElse(formatDouble(heat.total()));
        String shelterDetails = activeBand
            .map(band -> ", WorldShelter=" + Math.round(ShelterManager.INSTANCE.worldShelterEnclosure(player, band) * 100.0D) + "%"
                + ", SableShelter=" + Math.round(ShelterManager.INSTANCE.sableShelterEnclosure(player, band) * 100.0D) + "%"
                + ", Sable=" + ShelterManager.INSTANCE.sableDiagnostic(player, band))
            .orElse("");

        source.sendSuccess(() -> Component.literal(
            "Dimension=" + player.level().dimension().location()
                + ", Y=" + player.getBlockY()
                + ", Band=" + bandId
                + ", AltitudeRaw=" + formatDouble(rawModifier)
                + ", AltitudeApplied=" + formatDouble(modifier)
                + ", ProtectionReduction=" + (1.0D - state.protectionMultiplier())
                + ", ShelterReduction=" + (1.0D - state.shelterMultiplier())
                + ", Shelter=" + Math.round(state.shelterEnclosure() * 100.0D) + "%"
                + shelterDetails
                + ", TicksInBand=" + state.ticksInBand()),
            false);
        source.sendSuccess(() -> Component.literal(
            "HeatScanRange=" + heat.scanRange()
                + ", HeatTotal=" + formatDouble(heat.total())
                + ", OurNet=" + numericNet
                + ", CSWorld=" + formatDouble(Temperature.get(player, Temperature.Trait.WORLD))
                + ", CSBody=" + formatDouble(Temperature.get(player, Temperature.Trait.BODY))
                + ", CSCore=" + formatDouble(Temperature.get(player, Temperature.Trait.CORE))
                + ", HeatSources=" + formatHeatSources(heat)
                + ", Hearths=" + formatHearths(heat)),
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

    private static String formatHeatSources(HeatDiagnostics.Report heat)
    {
        if (heat.entries().isEmpty())
        {
            return "none(scan=" + heat.scanRange() + ")";
        }

        return heat.entries().stream()
            .limit(5)
            .map(entry -> entry.context()
                + ":" + entry.blockId()
                + "@" + entry.pos().toShortString()
                + " d=" + formatDouble(entry.distance())
                + " heat=" + formatDouble(entry.value()))
            .toList()
            .toString();
    }

    private static String formatHearths(HeatDiagnostics.Report heat)
    {
        if (heat.hearths().isEmpty())
        {
            return "none";
        }

        return heat.hearths().stream()
            .limit(3)
            .map(hearth -> hearth.context()
                + "@" + hearth.pos().toShortString()
                + " d=" + formatDouble(hearth.distance())
                + " heatingOn=" + hearth.heatingOn()
                + " hotFuel=" + hearth.hotFuel()
                + " usingHotFuel=" + hearth.usingHotFuel()
                + " heatingLevel=" + hearth.heatingLevel()
                + " maxRange=" + hearth.maxRange()
                + " affecting=" + hearth.affectingPlayer())
            .toList()
            .toString();
    }

    private static String formatDouble(double value)
    {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
