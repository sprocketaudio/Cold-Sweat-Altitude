package net.sprocketgames.coldsweataltitude.temperature;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.sprocketgames.coldsweataltitude.config.AltitudeBandConfig;
import net.sprocketgames.coldsweataltitude.protection.AltitudeProtectionManager;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public record AltitudeBand(
    String id,
    boolean enabled,
    Set<ResourceLocation> dimensions,
    AltitudeBandConfig.DimensionMode dimensionMode,
    int minY,
    Integer maxY,
    double temperatureModifier,
    AltitudeBandConfig.ModifierMode modifierMode,
    int priority,
    String onEnterMessage,
    String actionbarMessage,
    int messageCooldownTicks,
    TagKey<Item> protectionTag,
    int requiredPieces,
    double protectionReductionPerPiece,
    int fullProtectionPieces,
    boolean enableShelterCheck,
    int shelterCheckRadius,
    double shelterReduction)
{
    public static Optional<AltitudeBand> fromConfig(AltitudeBandConfig config, Consumer<String> warningSink)
    {
        Set<ResourceLocation> dimensionIds = new HashSet<>();
        for (String dimension : config.dimensions())
        {
            ResourceLocation id = ResourceLocation.tryParse(dimension);
            if (id == null)
            {
                warningSink.accept("Skipping invalid dimension id '" + dimension + "' in altitude band '" + config.id() + "'.");
                continue;
            }
            dimensionIds.add(id);
        }

        TagKey<Item> protectionTag = config.protectionTag().isBlank()
            ? null
            : AltitudeProtectionManager.parseTag(config.protectionTag());
        if (!config.protectionTag().isBlank() && protectionTag == null)
        {
            warningSink.accept("Invalid protection tag '" + config.protectionTag() + "' in altitude band '" + config.id() + "'. Tag-based protection will be disabled for this band.");
        }

        return Optional.of(new AltitudeBand(
            config.id(),
            config.enabled(),
            Set.copyOf(dimensionIds),
            config.dimensionMode(),
            config.minY(),
            config.maxY(),
            config.temperatureModifier(),
            config.modifierMode(),
            config.priority(),
            config.onEnterMessage(),
            config.actionbarMessage(),
            config.messageCooldownTicks(),
            protectionTag,
            Math.max(0, config.requiredPieces()),
            Mth.clamp(config.protectionReductionPerPiece(), 0.0D, 1.0D),
            Math.max(0, config.fullProtectionPieces()),
            config.enableShelterCheck(),
            Math.max(1, config.shelterCheckRadius()),
            Mth.clamp(config.shelterReduction(), 0.0D, 1.0D)));
    }

    public boolean matches(ResourceLocation dimensionId, int y)
    {
        if (!enabled)
        {
            return false;
        }

        boolean yMatches = y >= minY && (maxY == null || y <= maxY);
        if (!yMatches)
        {
            return false;
        }

        if (dimensions.isEmpty())
        {
            return true;
        }

        boolean listed = dimensions.contains(dimensionId);
        return dimensionMode == AltitudeBandConfig.DimensionMode.WHITELIST ? listed : !listed;
    }

    public double effectiveModifier(double protectionMultiplier, double shelterMultiplier)
    {
        double exposureMultiplier = protectionMultiplier * shelterMultiplier;
        if (modifierMode == AltitudeBandConfig.ModifierMode.MULTIPLY)
        {
            return 1.0D + ((temperatureModifier - 1.0D) * exposureMultiplier);
        }
        return temperatureModifier * exposureMultiplier;
    }
}
