package net.sprocketgames.coldsweataltitude.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.momosoftworks.coldsweat.api.util.Temperature;
import net.sprocketgames.coldsweataltitude.compat.HeatDiagnostics;
import net.sprocketgames.coldsweataltitude.config.AltitudeConfig;
import net.sprocketgames.coldsweataltitude.network.AltitudeNetwork;
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
        AltitudeTemperatureManager.CoordinateDiagnostic coordinates = manager.coordinateDiagnostic(player);
        PlayerAltitudeState state = manager.refreshState(player);
        AltitudeNetwork.sendShelterStatus(player, state.shelterEnclosure());
        Optional<AltitudeBand> activeBand = manager.findMatchingBand(player);
        HeatDiagnostics.Report heat = HeatDiagnostics.collect(player);

        Component bandId = activeBand.map(band -> text(band.id()))
            .orElse(Component.translatable("coldsweat_altitude.value.none"));
        double rawModifier = activeBand.map(band -> manager.rawModifier(player, band)).orElse(0.0D);
        double modifier = activeBand.map(band -> band.effectiveModifier(rawModifier, state.protectionMultiplier(), state.shelterMultiplier())).orElse(0.0D);
        Component worldShelter = activeBand
            .map(band -> text(Math.round(ShelterManager.INSTANCE.worldShelterEnclosure(player, band) * 100.0D) + "%"))
            .orElse(Component.translatable("coldsweat_altitude.value.not_applicable"));
        Component sableShelter = activeBand
            .map(band -> text(Math.round(ShelterManager.INSTANCE.sableShelterEnclosure(player, band) * 100.0D) + "%"))
            .orElse(Component.translatable("coldsweat_altitude.value.not_applicable"));
        Component sableDiagnostic = activeBand
            .map(band -> text(ShelterManager.INSTANCE.sableDiagnostic(player, band)))
            .orElse(Component.translatable("coldsweat_altitude.value.not_applicable"));

        sendStatus(source, ChatFormatting.AQUA, "commands.coldsweat_altitude.status.title.altitude", "commands.coldsweat_altitude.status.altitude",
            bandId,
            text(player.level().dimension().location()),
            text(coordinates.playerDisplay()),
            text(coordinates.sableWorldDisplay()),
            text(coordinates.sableLocalDisplay()),
            text(state.ticksInBand()));
        sendStatus(source, ChatFormatting.GOLD, "commands.coldsweat_altitude.status.title.modifiers", "commands.coldsweat_altitude.status.modifiers",
            text(formatDouble(rawModifier)),
            text(formatDouble(modifier)),
            text(formatDouble(1.0D - state.protectionMultiplier())),
            text(formatDouble(1.0D - state.shelterMultiplier())),
            text(Math.round(state.shelterEnclosure() * 100.0D)),
            worldShelter,
            sableShelter,
            sableDiagnostic);
        sendStatus(source, ChatFormatting.LIGHT_PURPLE, "commands.coldsweat_altitude.status.title.temperature", "commands.coldsweat_altitude.status.temperature",
            text(formatDouble(Temperature.get(player, Temperature.Trait.WORLD))),
            text(formatDouble(Temperature.get(player, Temperature.Trait.BODY))),
            text(formatDouble(Temperature.get(player, Temperature.Trait.CORE))));
        sendStatus(source, ChatFormatting.YELLOW, "commands.coldsweat_altitude.status.title.sources", "commands.coldsweat_altitude.status.sources",
            formatHeatSources(heat));
        sendStatus(source, ChatFormatting.RED, "commands.coldsweat_altitude.status.title.hearths", "commands.coldsweat_altitude.status.hearths",
            formatHearths(heat));
        return Command.SINGLE_SUCCESS;
    }

    private static int reload(CommandSourceStack source)
    {
        int count = AltitudeConfig.reload().size();
        source.sendSuccess(() -> Component.translatable("commands.coldsweat_altitude.reload.success", text(count)), true);
        return Command.SINGLE_SUCCESS;
    }

    private static void sendStatus(CommandSourceStack source, ChatFormatting accent, String titleKey, String bodyKey, Object... arguments)
    {
        source.sendSuccess(() -> Component.translatable(titleKey).withStyle(accent)
            .append(Component.literal(" | ").withStyle(ChatFormatting.DARK_GRAY))
            .append(Component.translatable(bodyKey, arguments).withStyle(ChatFormatting.GRAY)), false);
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
                text(band.id()),
                text(band.priority()),
                text(band.minY()),
                band.maxY() == null ? Component.translatable("coldsweat_altitude.value.open") : text(band.maxY()),
                text(band.modifierMode()),
                text(band.temperatureModifier())),
                false);
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Component formatHeatSources(HeatDiagnostics.Report heat)
    {
        if (heat.entries().isEmpty())
        {
            return Component.translatable("commands.coldsweat_altitude.status.heat_sources.none", text(heat.scanRange()));
        }

        return joinComponents(heat.entries().stream()
            .limit(5)
            .map(entry -> Component.translatable(
                "commands.coldsweat_altitude.status.heat_sources.entry",
                text(entry.context()),
                text(entry.blockId()),
                text(entry.pos().toShortString()),
                text(formatDouble(entry.distance())),
                text(entry.matchingRules())))
            .toList());
    }

    private static Component formatHearths(HeatDiagnostics.Report heat)
    {
        if (heat.error() != null)
        {
            return text("error(" + heat.error() + ")");
        }
        if (heat.hearths().isEmpty())
        {
            return Component.translatable("commands.coldsweat_altitude.status.hearths.none");
        }

        return joinComponents(heat.hearths().stream()
            .limit(3)
            .map(hearth -> Component.translatable(
                "commands.coldsweat_altitude.status.hearths.entry",
                text(hearth.context()),
                text(hearth.pos().toShortString()),
                text(formatDouble(hearth.distance())),
                text(hearth.heatingOn()),
                text(hearth.coolingOn()),
                text(hearth.hotFuel()),
                text(hearth.coldFuel()),
                text(hearth.usingHotFuel()),
                text(hearth.usingColdFuel()),
                text(hearth.heatingLevel()),
                text(hearth.coolingLevel()),
                text(hearth.maxRange()),
                text(hearth.affectingPlayer())))
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

    private static Component text(Object value)
    {
        return Component.literal(String.valueOf(value)).withStyle(ChatFormatting.WHITE);
    }
}
