package net.sprocketgames.coldsweataltitude.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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

        Object bandId = activeBand.<Object>map(AltitudeBand::id).orElse(Component.translatable("coldsweat_altitude.value.none"));
        double rawModifier = activeBand.map(AltitudeBand::temperatureModifier).orElse(0.0D);
        double modifier = activeBand.map(band -> band.effectiveModifier(state.protectionMultiplier(), state.shelterMultiplier())).orElse(0.0D);
        Object numericNet = activeBand
            .<Object>map(band -> band.modifierMode() == AltitudeBandConfig.ModifierMode.ADD
                ? formatDouble(modifier + heat.total())
                : Component.translatable("coldsweat_altitude.value.not_applicable_multiply"))
            .orElse(formatDouble(heat.total()));
        Object worldShelter = activeBand
            .<Object>map(band -> Math.round(ShelterManager.INSTANCE.worldShelterEnclosure(player, band) * 100.0D) + "%")
            .orElse(Component.translatable("coldsweat_altitude.value.not_applicable"));
        Object sableShelter = activeBand
            .<Object>map(band -> Math.round(ShelterManager.INSTANCE.sableShelterEnclosure(player, band) * 100.0D) + "%")
            .orElse(Component.translatable("coldsweat_altitude.value.not_applicable"));
        Object sableDiagnostic = activeBand
            .<Object>map(band -> ShelterManager.INSTANCE.sableDiagnostic(player, band))
            .orElse(Component.translatable("coldsweat_altitude.value.not_applicable"));

        source.sendSuccess(() -> Component.translatable(
            "commands.coldsweat_altitude.status.line1",
            player.level().dimension().location(),
            player.getBlockY(),
            bandId,
            formatDouble(rawModifier),
            formatDouble(modifier),
            formatDouble(1.0D - state.protectionMultiplier()),
            formatDouble(1.0D - state.shelterMultiplier()),
            Math.round(state.shelterEnclosure() * 100.0D),
            worldShelter,
            sableShelter,
            sableDiagnostic,
            state.ticksInBand()),
            false);
        source.sendSuccess(() -> Component.translatable(
            "commands.coldsweat_altitude.status.line2",
            heat.scanRange(),
            formatDouble(heat.total()),
            numericNet,
            formatDouble(Temperature.get(player, Temperature.Trait.WORLD)),
            formatDouble(Temperature.get(player, Temperature.Trait.BODY)),
            formatDouble(Temperature.get(player, Temperature.Trait.CORE)),
            formatHeatSources(heat),
            formatHearths(heat)),
            false);
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandSourceStack source)
    {
        int count = AltitudeConfig.reload().size();
        source.sendSuccess(() -> Component.translatable("commands.coldsweat_altitude.reload.success", count), true);
        return Command.SINGLE_SUCCESS;
    }

    private static int list(CommandSourceStack source)
    {
        if (AltitudeConfig.getBands().isEmpty())
        {
            source.sendFailure(Component.translatable("commands.coldsweat_altitude.list.empty"));
            return 0;
        }

        for (AltitudeBand band : AltitudeConfig.getBands().stream().sorted(Comparator.comparingInt(AltitudeBand::priority).reversed()).toList())
        {
            source.sendSuccess(() -> Component.translatable(
                "commands.coldsweat_altitude.list.entry",
                band.id(),
                band.priority(),
                band.minY(),
                band.maxY() == null ? Component.translatable("coldsweat_altitude.value.open") : band.maxY(),
                band.modifierMode(),
                band.temperatureModifier()),
                false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Component formatHeatSources(HeatDiagnostics.Report heat)
    {
        if (heat.entries().isEmpty())
        {
            return Component.translatable("commands.coldsweat_altitude.status.heat_sources.none", heat.scanRange());
        }

        return joinComponents(heat.entries().stream()
            .limit(5)
            .map(entry -> Component.translatable(
                "commands.coldsweat_altitude.status.heat_sources.entry",
                entry.context(),
                entry.blockId(),
                entry.pos().toShortString(),
                formatDouble(entry.distance()),
                formatDouble(entry.value())))
            .toList());
    }

    private static Component formatHearths(HeatDiagnostics.Report heat)
    {
        if (heat.hearths().isEmpty())
        {
            return Component.translatable("commands.coldsweat_altitude.status.hearths.none");
        }

        return joinComponents(heat.hearths().stream()
            .limit(3)
            .map(hearth -> Component.translatable(
                "commands.coldsweat_altitude.status.hearths.entry",
                hearth.context(),
                hearth.pos().toShortString(),
                formatDouble(hearth.distance()),
                hearth.heatingOn(),
                hearth.hotFuel(),
                hearth.usingHotFuel(),
                hearth.heatingLevel(),
                hearth.maxRange(),
                hearth.affectingPlayer()))
            .toList());
    }

    private static Component joinComponents(java.util.List<? extends Component> components)
    {
        MutableComponent joined = Component.empty();
        for (int index = 0; index < components.size(); index++)
        {
            if (index > 0)
            {
                joined.append(Component.literal(", "));
            }
            joined.append(components.get(index));
        }
        return joined;
    }

    private static String formatDouble(double value)
    {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
