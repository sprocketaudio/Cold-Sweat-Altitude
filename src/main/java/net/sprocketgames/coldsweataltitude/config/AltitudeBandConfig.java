package net.sprocketgames.coldsweataltitude.config;

import com.electronwill.nightconfig.core.CommentedConfig;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;

public record AltitudeBandConfig(
    String id,
    boolean enabled,
    List<String> dimensions,
    DimensionMode dimensionMode,
    int minY,
    Integer maxY,
    double temperatureModifier,
    ModifierMode modifierMode,
    int priority,
    String onEnterMessage,
    String actionbarMessage,
    int messageCooldownTicks,
    String protectionTag,
    int requiredPieces,
    double protectionReductionPerPiece,
    int fullProtectionPieces,
    boolean enableShelterCheck,
    int shelterCheckRadius,
    double shelterReduction)
{
    public enum ModifierMode
    {
        ADD,
        MULTIPLY
    }

    public enum DimensionMode
    {
        WHITELIST,
        BLACKLIST
    }

    public static Optional<AltitudeBandConfig> fromConfig(CommentedConfig config, Consumer<String> warningSink)
    {
        String id = stringValue(config, "id", "").trim();
        if (id.isEmpty())
        {
            warningSink.accept("Skipping altitude band with missing id.");
            return Optional.empty();
        }

        int minY = intValue(config, "minY", 0);
        Integer maxY = optionalIntValue(config, "maxY");
        if (maxY != null && maxY < minY)
        {
            warningSink.accept("Skipping altitude band '" + id + "' because maxY is lower than minY.");
            return Optional.empty();
        }

        List<String> dimensions = stringList(config, "dimensions");

        return Optional.of(new AltitudeBandConfig(
            id,
            booleanValue(config, "enabled", true),
            dimensions,
            enumValue(config, "dimensionMode", DimensionMode.WHITELIST, warningSink, id),
            minY,
            maxY,
            doubleValue(config, "temperatureModifier", 0.0D),
            enumValue(config, "modifierMode", ModifierMode.ADD, warningSink, id),
            intValue(config, "priority", 0),
            stringValue(config, "onEnterMessage", ""),
            stringValue(config, "actionbarMessage", ""),
            intValue(config, "messageCooldownTicks", 100),
            stringValue(config, "protectionTag", ""),
            intValue(config, "requiredPieces", 0),
            doubleValue(config, "protectionReductionPerPiece", 0.0D),
            intValue(config, "fullProtectionPieces", 4),
            booleanValue(config, "enableShelterCheck", false),
            intValue(config, "shelterCheckRadius", 2),
            doubleValue(config, "shelterReduction", 0.0D)));
    }

    private static boolean booleanValue(CommentedConfig config, String key, boolean fallback)
    {
        Object value = config.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static double doubleValue(CommentedConfig config, String key, double fallback)
    {
        Object value = config.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static int intValue(CommentedConfig config, String key, int fallback)
    {
        Object value = config.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static Integer optionalIntValue(CommentedConfig config, String key)
    {
        Object value = config.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static String stringValue(CommentedConfig config, String key, String fallback)
    {
        Object value = config.get(key);
        return value instanceof String string ? string : fallback;
    }

    private static List<String> stringList(CommentedConfig config, String key)
    {
        Object value = config.get(key);
        if (!(value instanceof List<?> rawList))
        {
            return List.of();
        }

        return rawList.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .filter(string -> !string.isBlank())
            .toList();
    }

    private static <T extends Enum<T>> T enumValue(CommentedConfig config, String key, T fallback, Consumer<String> warningSink, String id)
    {
        Object value = config.get(key);
        if (!(value instanceof String string))
        {
            return fallback;
        }

        try
        {
            return Enum.valueOf(fallback.getDeclaringClass(), string.toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception)
        {
            warningSink.accept("Invalid " + key + " value '" + string + "' in altitude band '" + id + "'. Using " + fallback + ".");
            return fallback;
        }
    }
}
