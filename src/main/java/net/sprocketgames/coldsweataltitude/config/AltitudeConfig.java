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
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AltitudeConfig
{
    public static final String FILE_NAME = "coldsweat_altitude-server.toml";
    public static final int MAX_SHELTER_CHECK_RADIUS = 16;
    public static final double MAX_AERONAUTICS_HEAT_RANGE = 16.0D;

    private static final AeronauticsHeatSettings DEFAULT_AERONAUTICS_HEAT_SETTINGS =
        new AeronauticsHeatSettings(0.14D, 7.0D, 0.12D, 8.0D);

    private static volatile List<AltitudeBand> bands = List.of();
    private static volatile AeronauticsHeatSettings aeronauticsHeatSettings = DEFAULT_AERONAUTICS_HEAT_SETTINGS;
    private static volatile boolean debugLogging;
    private static volatile BandGradientMode bandGradientMode = BandGradientMode.BOUNDARY;

    public enum BandGradientMode { NONE, LINEAR, BOUNDARY }

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
            boolean configChanged = clampTopLevelConfigValues(config);
            debugLogging = booleanValue(config, "debugLogging", false);
            bandGradientMode = enumValue(config, "bandGradientMode", BandGradientMode.NONE);
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

                    configChanged |= clampBandConfigValues(bandConfig);

                    Optional<AltitudeBandConfig> parsedConfig = AltitudeBandConfig.fromConfig(bandConfig, message -> ColdSweatAltitude.LOGGER.warn(message));
                    if (parsedConfig.isEmpty())
                    {
                        continue;
                    }

                    AltitudeBand.fromConfig(parsedConfig.get(), message -> ColdSweatAltitude.LOGGER.warn(message))
                        .ifPresent(loadedBands::add);
                }
            }

            if (configChanged)
            {
                config.save();
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

    public static boolean debugLogging()
    {
        return debugLogging;
    }

    public static BandGradientMode bandGradientMode() { return bandGradientMode; }

    public static Path configPath()
    {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    private static void ensureExists()
    {
        Path path = configPath();
        if (Files.exists(path))
        {
            migrateExistingConfig(path);
            return;
        }

        try
        {
            Files.createDirectories(path.getParent());
            Files.writeString(path, documentedDefaultConfig());
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to create default altitude config at " + path, exception);
        }
    }

    /**
     * Adds new optional settings without parsing and reserializing a user's
     * TOML. NightConfig does not preserve key order during a rewrite, whereas
     * this migration only inserts the missing lines beside their related
     * settings and otherwise leaves the file byte-for-byte intact.
     */
    private static void migrateExistingConfig(Path path)
    {
        try
        {
            String original = Files.readString(path);
            String newline = original.contains("\r\n") ? "\r\n" : "\n";
            String migrated = normalizeMigrationComments(original, newline);
            migrated = migrateTopLevelSettings(migrated, newline);

            if (!migrated.equals(original))
            {
                Files.writeString(path, migrated);
            }
        }
        catch (IOException exception)
        {
            ColdSweatAltitude.LOGGER.warn("Unable to migrate new settings into {}.", FILE_NAME, exception);
        }
    }

    private static String normalizeMigrationComments(String config, String newline)
    {
        String oldHelp = "# ADD adds the modifier to Cold Sweat's world temperature. MULTIPLY scales it (1.0 = no change)." + newline
            + "# Gradients begin at temperatureModifier (minY) and end at gradientEndModifier (maxY)." + newline
            + "# gradientCurve: NONE keeps a fixed modifier; LINEAR changes evenly; SMOOTH eases at both ends." + newline
            + "# Gradients require a finite maxY." + newline;
        String normalized = config.replace(oldHelp + newline, "").replace(oldHelp, "");
        normalized = normalized.replace(
            "# Modifier at maxY when a gradient is enabled. Ignored while gradientCurve is NONE.",
            "# Modifier at maxY; used by LINEAR or SMOOTH.");
        normalized = normalized.replace(
            "# NONE keeps a fixed modifier; LINEAR changes evenly; SMOOTH eases at both ends. Gradients require maxY.",
            "# NONE=fixed; LINEAR=even; SMOOTH=eased.");
        normalized = normalized.replace(
            "# Modifier at maxY; used by LINEAR or SMOOTH.",
            "# Modifier reached at maxY. Example: 0.0 to -0.08 gets colder as Y rises.");
        normalized = normalized.replace(
            "# NONE=fixed; LINEAR=even; SMOOTH=eased.",
            "# NONE stays fixed; LINEAR ramps evenly; SMOOTH ramps gently near both ends.");
        normalized = normalized.replaceAll("(?m)^# (Modifier at maxY.*|Value at maxY.*|Modifier reached at maxY.*|NONE=.*|NONE stays fixed.*)\\R", "");
        normalized = normalized.replaceAll("(?m)^gradient(EndModifier|Curve)\\s*=.*\\R", "");
        normalized = Pattern.compile(
                "(?ms)^\\s*onEnterMessage\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\")\\R"
                    + "(?:(?!^\\s*\\[\\[bands\\]\\]).)*?"
                    + "(^\\s*actionbarMessage\\s*=\\s*)\"\"")
            .matcher(normalized)
            .replaceAll("$2$1");
        normalized = normalized.replaceAll(
            "(?m)^# Use plain text:\\R# onEnterMessage =.*\\R# Use a translation key instead:\\R# onEnterMessage =.*\\RonEnterMessage =.*\\R", "");
        normalized = normalized.replaceAll("(?m)^# onEnterMessage =.*\\R", "");
        normalized = normalized.replaceAll("(?m)^onEnterMessage\\s*=.*\\R?", "");
        normalized = normalized.replace(
            "# Minimum ticks between this band's messages.",
            "# How long this band's action-bar message remains visible, in ticks.");
        normalized = normalized.replaceAll(
            "(?m)^(\\s*)messageCooldownTicks(\\s*=)",
            "$1actionbarDisplayTicks$2");
        String misplacedDebug = "# Aeronautics heat-source tuning applies to normal world blocks and Sable ship interiors." + newline
            + "# Enable extra diagnostic log entries for altitude and shelter transitions." + newline
            + "debugLogging = false" + newline + newline;
        return normalized.replace(misplacedDebug,
            "# Enable extra diagnostic log entries for altitude and shelter transitions." + newline
                + "debugLogging = false" + newline + newline
                + "# Aeronautics heat-source tuning applies to normal world blocks and Sable ship interiors." + newline);
    }

    private static String migrateTopLevelSettings(String config, String newline)
    {
        String debugValue = settingValue(config, "debugLogging", "false");
        String gradientValue = settingValue(config, "bandGradientMode",
            settingValue(config, "bandInterpolationMode", "\"BOUNDARY\""));

        String cleaned = config
            .replaceAll("(?m)^# Enable extra diagnostic log entries for altitude and shelter transitions\\.\\R", "")
            .replaceAll("(?m)^# Controls how temperature changes between altitude bands\\.\\R", "")
            .replaceAll("(?m)^# NONE=abrupt; LINEAR=.*\\R", "")
            .replaceAll("(?m)^(debugLogging|bandInterpolationMode|bandGradientMode)\\s*=.*\\R?", "");

        String addition = "# Enable extra diagnostic log entries for altitude and shelter transitions." + newline
            + "debugLogging = " + debugValue + newline + newline
            + "# Controls how temperature changes between altitude bands." + newline
            + "# NONE=abrupt; LINEAR=across the whole band; BOUNDARY=only near each band edge." + newline
            + "bandGradientMode = " + gradientValue + newline + newline;
        Matcher heatSection = Pattern.compile("(?m)^# Aeronautics heat-source tuning applies.*(?:\\R)?").matcher(cleaned);
        if (heatSection.find())
        {
            return cleaned.substring(0, heatSection.start()) + addition + cleaned.substring(heatSection.start());
        }

        Matcher heatSettings = Pattern.compile("(?m)^aeronauticsBurnerHeat\\s*=").matcher(cleaned);
        if (heatSettings.find())
        {
            return cleaned.substring(0, heatSettings.start()) + addition + cleaned.substring(heatSettings.start());
        }

        int firstBand = cleaned.indexOf("[[bands]]");
        return firstBand >= 0
            ? cleaned.substring(0, firstBand) + addition + cleaned.substring(firstBand)
            : cleaned + newline + addition;
    }

    private static String settingValue(String config, String key, String fallback)
    {
        Matcher setting = Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=\\s*(.*)$").matcher(config);
        return setting.find() ? setting.group(1) : fallback;
    }

    private static AeronauticsHeatSettings loadAeronauticsHeatSettings(CommentedConfig config)
    {
        return new AeronauticsHeatSettings(
            doubleValue(config, "aeronauticsBurnerHeat", DEFAULT_AERONAUTICS_HEAT_SETTINGS.burnerHeat()),
            clampedRange(config, "aeronauticsBurnerRange", DEFAULT_AERONAUTICS_HEAT_SETTINGS.burnerRange()),
            doubleValue(config, "aeronauticsSteamVentHeat", DEFAULT_AERONAUTICS_HEAT_SETTINGS.steamVentHeat()),
            clampedRange(config, "aeronauticsSteamVentRange", DEFAULT_AERONAUTICS_HEAT_SETTINGS.steamVentRange()));
    }

    private static boolean clampTopLevelConfigValues(CommentedConfig config)
    {
        boolean changed = false;
        changed |= clampDoubleConfigValue(config, "aeronauticsBurnerRange", MAX_AERONAUTICS_HEAT_RANGE);
        changed |= clampDoubleConfigValue(config, "aeronauticsSteamVentRange", MAX_AERONAUTICS_HEAT_RANGE);
        return changed;
    }

    private static boolean clampBandConfigValues(CommentedConfig bandConfig)
    {
        Object value = bandConfig.get("shelterCheckRadius");
        if (!(value instanceof Number number) || number.intValue() <= MAX_SHELTER_CHECK_RADIUS)
        {
            return false;
        }

        String id = Optional.ofNullable(bandConfig.get("id"))
            .map(Object::toString)
            .orElse("<unknown>");
        bandConfig.set("shelterCheckRadius", MAX_SHELTER_CHECK_RADIUS);
        ColdSweatAltitude.LOGGER.warn(
            "Altitude band '{}' has shelterCheckRadius={} which is above the safe maximum {}. Rewriting it to {} to avoid expensive shelter scans.",
            id,
            number.intValue(),
            MAX_SHELTER_CHECK_RADIUS,
            MAX_SHELTER_CHECK_RADIUS);
        return true;
    }

    private static boolean clampDoubleConfigValue(CommentedConfig config, String key, double max)
    {
        Object value = config.get(key);
        if (!(value instanceof Number number) || number.doubleValue() <= max)
        {
            return false;
        }

        config.set(key, max);
        ColdSweatAltitude.LOGGER.warn(
            "{}={} is above the safe maximum {}. Rewriting it to {} to avoid expensive heat-source scans.",
            key,
            number.doubleValue(),
            max,
            max);
        return true;
    }

    private static double clampedRange(CommentedConfig config, String key, double fallback)
    {
        double value = doubleValue(config, key, fallback);
        if (value <= MAX_AERONAUTICS_HEAT_RANGE)
        {
            return value;
        }

        ColdSweatAltitude.LOGGER.warn(
            "{}={} is above the safe maximum {}. Clamping to {} to avoid expensive heat-source scans.",
            key,
            value,
            MAX_AERONAUTICS_HEAT_RANGE,
            MAX_AERONAUTICS_HEAT_RANGE);
        return MAX_AERONAUTICS_HEAT_RANGE;
    }

    private static double doubleValue(CommentedConfig config, String key, double fallback)
    {
        Object value = config.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static <T extends Enum<T>> T enumValue(CommentedConfig config, String key, T fallback)
    {
        Object value = config.get(key);
        if (!(value instanceof String string)) return fallback;
        try { return Enum.valueOf(fallback.getDeclaringClass(), string.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private static String documentedDefaultConfig()
    {
        Map<String, String> comments = Map.ofEntries(
            Map.entry("aeronauticsBurnerHeat", "Heat added by an Aeronautics burner."),
            Map.entry("aeronauticsBurnerRange", "Maximum block distance at which a burner contributes heat."),
            Map.entry("aeronauticsSteamVentHeat", "Heat added by an Aeronautics steam vent."),
            Map.entry("aeronauticsSteamVentRange", "Maximum block distance at which a steam vent contributes heat."),
            Map.entry("id", "Unique name used by commands, messages, and saved player state."),
            Map.entry("enabled", "Turn this altitude band on or off."),
            Map.entry("dimensions", "Dimensions this band applies to."),
            Map.entry("dimensionMode", "WHITELIST applies only to listed dimensions; BLACKLIST excludes them."),
            Map.entry("minY", "Inclusive lower Y level for this band."),
            Map.entry("maxY", "Inclusive upper Y level; omit it for an open-ended band."),
            Map.entry("temperatureModifier", "Value at this band's minY."),
            Map.entry("modifierMode", "ADD adds temperature; MULTIPLY scales it (1.0 = unchanged)."),
            Map.entry("priority", "Higher priority wins when multiple bands match."),
            Map.entry("actionbarDisplayTicks", "How long this band's action-bar message remains visible, in ticks."),
            Map.entry("protectionTag", "Item tag whose equipped pieces reduce this band's temperature effect."),
            Map.entry("requiredPieces", "Minimum tagged armour pieces needed before protection applies."),
            Map.entry("protectionReductionPerPiece", "Reduction supplied by each qualifying armour piece."),
            Map.entry("fullProtectionPieces", "Number of qualifying pieces required for full protection."),
            Map.entry("enableShelterCheck", "Whether enclosed cover reduces this band's temperature effect."),
            Map.entry("shelterCheckRadius", "Block radius used when checking nearby cover."),
            Map.entry("shelterReduction", "Maximum effect reduction granted by full shelter."));

        StringBuilder documented = new StringBuilder();
        String[] lines = defaultConfig().split("\\R", -1);
        for (int index = 0; index < lines.length; index++)
        {
            String line = lines[index];
            String trimmed = line.trim();
            if (trimmed.startsWith("gradientEndModifier") || trimmed.startsWith("gradientCurve")
                || trimmed.startsWith("# Modifier at maxY") || trimmed.startsWith("# NONE "))
            {
                continue;
            }
            if (trimmed.startsWith("#") && index + 1 < lines.length)
            {
                String next = lines[index + 1].trim();
                String nextKey = next.contains("=") ? next.substring(0, next.indexOf('=')) .trim() : "";
                if (comments.containsKey(nextKey))
                {
                    continue;
                }
            }
            if (trimmed.equals("[[bands]]"))
            {
                documented.append("# Altitude band definition.\n");
            }
            else if (!trimmed.startsWith("#") && trimmed.contains("="))
            {
                String key = trimmed.substring(0, trimmed.indexOf('=')) .trim();
                String comment = comments.get(key);
                if (comment != null)
                {
                    documented.append("# ").append(comment).append('\n');
                }
            }
            documented.append(line).append('\n');
        }
        return documented.toString();
    }

    private static boolean booleanValue(CommentedConfig config, String key, boolean fallback)
    {
        Object value = config.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static String defaultConfig()
    {
        return """
            # Cold Sweat: Altitude server config
            # Temperature values are applied through Cold Sweat's WORLD temperature modifiers.
            # Band id is a user-defined stable name used by commands, logs, messages, and runtime state.
            # Leave maxY unset to make a band open-ended upward.
            # These defaults are intended for a mostly vanilla-style overworld height range.
            # Aeronautics heat-source tuning applies to both normal world blocks and Sable ship interiors.
            # Performance guard: shelterCheckRadius and Aeronautics heat ranges above 16 are rewritten to 16 when loaded.
            # Enable extra diagnostic log entries for altitude and shelter transitions.
            debugLogging = false
            # Controls how temperature changes between altitude bands.
            # NONE=abrupt; LINEAR=across the whole band; BOUNDARY=only near each band edge.
            bandGradientMode = "BOUNDARY"

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
            maxY = 0
            temperatureModifier = 0.08
            modifierMode = "ADD"
            priority = 10
            # Use plain text:
            # actionbarMessage = "Deep cave warmth"
            # Use a translation key instead:
            # actionbarMessage = "coldsweat_altitude.band.deep_caves.actionbar"
            actionbarMessage = "The cave air feels warmer here."
            actionbarDisplayTicks = 100
            protectionTag = ""
            requiredPieces = 0
            protectionReductionPerPiece = 0.0
            fullProtectionPieces = 4
            enableShelterCheck = true
            shelterCheckRadius = 4
            shelterReduction = 0.20

            [[bands]]
            id = "underground"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = 1
            maxY = 62
            temperatureModifier = 0.03
            modifierMode = "ADD"
            priority = 5
            # Use plain text:
            # actionbarMessage = "Underground air"
            # Use a translation key instead:
            # actionbarMessage = "coldsweat_altitude.band.underground.actionbar"
            actionbarMessage = "The underground air is calm."
            actionbarDisplayTicks = 100
            protectionTag = ""
            requiredPieces = 0
            protectionReductionPerPiece = 0.0
            fullProtectionPieces = 4
            enableShelterCheck = true
            shelterCheckRadius = 4
            shelterReduction = 0.25

            [[bands]]
            id = "surface"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = 63
            maxY = 127
            temperatureModifier = 0.0
            modifierMode = "ADD"
            priority = 0
            # Use plain text:
            # actionbarMessage = "Surface conditions"
            # Use a translation key instead:
            # actionbarMessage = "coldsweat_altitude.band.surface.actionbar"
            actionbarMessage = "You return to the surface."
            actionbarDisplayTicks = 100
            protectionTag = ""
            requiredPieces = 0
            protectionReductionPerPiece = 0.0
            fullProtectionPieces = 4
            enableShelterCheck = true
            shelterCheckRadius = 4
            shelterReduction = 0.20

            [[bands]]
            id = "high_peaks"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = 128
            maxY = 191
            temperatureModifier = -0.08
            modifierMode = "ADD"
            priority = 15
            # Use plain text:
            # actionbarMessage = "High altitude chill"
            # Use a translation key instead:
            # actionbarMessage = "coldsweat_altitude.band.high_peaks.actionbar"
            actionbarMessage = "The mountain air grows colder."
            actionbarDisplayTicks = 100
            protectionTag = ""
            requiredPieces = 0
            protectionReductionPerPiece = 0.0
            fullProtectionPieces = 4
            enableShelterCheck = true
            shelterCheckRadius = 4
            shelterReduction = 0.35

            [[bands]]
            id = "low_sky"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = 192
            maxY = 255
            temperatureModifier = -0.20
            modifierMode = "ADD"
            priority = 20
            # Use plain text:
            # actionbarMessage = "Altitude chill"
            # Use a translation key instead:
            # actionbarMessage = "coldsweat_altitude.band.low_sky.actionbar"
            actionbarMessage = "The air grows colder at this altitude."
            actionbarDisplayTicks = 100
            protectionTag = ""
            requiredPieces = 0
            protectionReductionPerPiece = 0.0
            fullProtectionPieces = 4
            enableShelterCheck = true
            shelterCheckRadius = 4
            shelterReduction = 0.50

            [[bands]]
            id = "extreme_sky"
            enabled = true
            dimensions = ["minecraft:overworld"]
            dimensionMode = "WHITELIST"
            minY = 256
            maxY = 4096
            temperatureModifier = -0.45
            modifierMode = "ADD"
            priority = 30
            # Use plain text:
            # actionbarMessage = "Extreme altitude exposure"
            # Use a translation key instead:
            # actionbarMessage = "coldsweat_altitude.band.extreme_sky.actionbar"
            actionbarMessage = "The air grows thin and bitter."
            actionbarDisplayTicks = 100
            protectionTag = ""
            requiredPieces = 0
            protectionReductionPerPiece = 0.0
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
