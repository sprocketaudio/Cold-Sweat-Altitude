package net.sprocketgames.coldsweataltitude.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import net.neoforged.fml.loading.FMLPaths;
import net.sprocketgames.coldsweataltitude.ColdSweatAltitude;
import net.sprocketgames.coldsweataltitude.temperature.AltitudeBand;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AltitudeConfig
{
    public static final String FILE_NAME = "coldsweat_altitude-server.toml";
    private static final AeronauticsHeatSettings DEFAULT_AERONAUTICS_HEAT_SETTINGS =
        new AeronauticsHeatSettings(0.14D, 7.0D, 0.12D, 8.0D);

    private static volatile List<AltitudeBand> bands = List.of();
    private static volatile AeronauticsHeatSettings aeronauticsHeatSettings = DEFAULT_AERONAUTICS_HEAT_SETTINGS;

    private AltitudeConfig()
    {
    }

    public static void bootstrap()
    {
        ensureExists();
        reload();
    }

    public static List<AltitudeBand> reload()
    {
        ensureExists();
        List<AltitudeBand> loadedBands = new ArrayList<>();

        try (CommentedFileConfig config = CommentedFileConfig.builder(configPath())
            .sync()
            .autosave()
            .writingMode(WritingMode.REPLACE)
            .build())
        {
            config.load();
            aeronauticsHeatSettings = loadAeronauticsHeatSettings(config);
            Object rawBands = config.get("bands");
            if (rawBands instanceof List<?> entries)
            {
                for (Object entry : entries)
                {
                    if (!(entry instanceof CommentedConfig bandConfig))
                    {
                        ColdSweatAltitude.LOGGER.warn("Skipping malformed band entry in {}.", FILE_NAME);
                        continue;
                    }

                    Optional<AltitudeBandConfig> parsedConfig = AltitudeBandConfig.fromConfig(bandConfig, message -> ColdSweatAltitude.LOGGER.warn(message));
                    if (parsedConfig.isEmpty())
                    {
                        continue;
                    }

                    AltitudeBand.fromConfig(parsedConfig.get(), message -> ColdSweatAltitude.LOGGER.warn(message))
                        .ifPresent(loadedBands::add);
                }
            }
        }
        catch (Exception exception)
        {
            ColdSweatAltitude.LOGGER.error("Failed to load {}. Keeping previous band set.", FILE_NAME, exception);
            return bands;
        }

        bands = loadedBands.stream()
            .sorted((left, right) -> Integer.compare(right.priority(), left.priority()))
            .toList();
        ColdSweatAltitude.LOGGER.info("Loaded {} altitude band definitions.", bands.size());
        return bands;
    }

    public static List<AltitudeBand> getBands()
    {
        return bands;
    }

    public static AeronauticsHeatSettings getAeronauticsHeatSettings()
    {
        return aeronauticsHeatSettings;
    }

    public static Path configPath()
    {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static void ensureExists()
    {
        Path path = configPath();
        if (Files.exists(path))
        {
            return;
        }

        try
        {
            Files.createDirectories(path.getParent());
            Files.writeString(path, defaultConfig());
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to create default altitude config at " + path, exception);
        }
    }

    private static AeronauticsHeatSettings loadAeronauticsHeatSettings(CommentedConfig config)
    {
        return new AeronauticsHeatSettings(
            doubleValue(config, "aeronauticsBurnerHeat", DEFAULT_AERONAUTICS_HEAT_SETTINGS.burnerHeat()),
            doubleValue(config, "aeronauticsBurnerRange", DEFAULT_AERONAUTICS_HEAT_SETTINGS.burnerRange()),
            doubleValue(config, "aeronauticsSteamVentHeat", DEFAULT_AERONAUTICS_HEAT_SETTINGS.steamVentHeat()),
            doubleValue(config, "aeronauticsSteamVentRange", DEFAULT_AERONAUTICS_HEAT_SETTINGS.steamVentRange()));
    }

    private static double doubleValue(CommentedConfig config, String key, double fallback)
    {
        Object value = config.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static String defaultConfig()
    {
        return """
            # Cold Sweat: Altitude server config
            # Temperature values are applied through Cold Sweat's WORLD temperature modifiers.
            # Band id is a user-defined stable name used by commands, logs, messages, and runtime state.
            # Leave maxY unset to make a band open-ended upward.
            # Aeronautics heat-source tuning applies to both normal world blocks and Sable ship interiors.

            aeronauticsBurnerHeat = 0.14
            aeronauticsBurnerRange = 7.0
            aeronauticsSteamVentHeat = 0.12
            aeronauticsSteamVentRange = 8.0

            [[bands]]
            id = "deep_caves"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = -64
            maxY = 30
            temperatureModifier = 0.10
            modifierMode = "ADD"
            priority = 10
            onEnterMessage = ""
            actionbarMessage = ""
            messageCooldownTicks = 100
            protectionTag = ""
            requiredPieces = 0
            protectionReductionPerPiece = 0.0
            fullProtectionPieces = 4
            enableShelterCheck = false
            shelterCheckRadius = 4
            shelterReduction = 0.0

            [[bands]]
            id = "surface"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = 31
            maxY = 160
            temperatureModifier = 0.0
            modifierMode = "ADD"
            priority = 0
            onEnterMessage = ""
            actionbarMessage = ""
            messageCooldownTicks = 100
            protectionTag = ""
            requiredPieces = 0
            protectionReductionPerPiece = 0.0
            fullProtectionPieces = 4
            enableShelterCheck = false
            shelterCheckRadius = 4
            shelterReduction = 0.0

            [[bands]]
            id = "low_sky"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = 161
            maxY = 300
            temperatureModifier = -0.05
            modifierMode = "ADD"
            priority = 20
            onEnterMessage = ""
            actionbarMessage = ""
            messageCooldownTicks = 100
            protectionTag = "coldsweat_altitude:cold_altitude_protection"
            requiredPieces = 4
            protectionReductionPerPiece = 0.25
            fullProtectionPieces = 4
            enableShelterCheck = false
            shelterCheckRadius = 4
            shelterReduction = 0.0

            [[bands]]
            id = "high_sky"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = 301
            maxY = 600
            temperatureModifier = -0.35
            modifierMode = "ADD"
            priority = 30
            onEnterMessage = ""
            actionbarMessage = ""
            messageCooldownTicks = 100
            protectionTag = "coldsweat_altitude:cold_altitude_protection"
            requiredPieces = 4
            protectionReductionPerPiece = 0.25
            fullProtectionPieces = 4
            enableShelterCheck = true
            shelterCheckRadius = 4
            shelterReduction = 0.75

            [[bands]]
            id = "extreme_sky"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = 601
            maxY = 4096
            temperatureModifier = -0.75
            modifierMode = "ADD"
            priority = 40
            onEnterMessage = "The air grows thin and bitter."
            actionbarMessage = "Extreme altitude exposure"
            messageCooldownTicks = 100
            protectionTag = "coldsweat_altitude:cold_altitude_protection"
            requiredPieces = 4
            protectionReductionPerPiece = 0.25
            fullProtectionPieces = 4
            enableShelterCheck = true
            shelterCheckRadius = 4
            shelterReduction = 0.75
            """;
    }

    public record AeronauticsHeatSettings(
        double burnerHeat,
        double burnerRange,
        double steamVentHeat,
        double steamVentRange)
    {
    }
}
