package net.sprocketgames.coldsweataltitude.util;

import net.minecraft.network.chat.Component;

public final class UiText
{
    private UiText()
    {
    }

    public static Component fromConfigMessage(String value)
    {
        String trimmed = value.trim();
        if (trimmed.isEmpty())
        {
            return Component.empty();
        }
        if (trimmed.startsWith("translate:"))
        {
            return Component.translatable(trimmed.substring("translate:".length()));
        }
        if (!containsWhitespace(trimmed) && trimmed.contains("."))
        {
            return Component.translatable(trimmed);
        }
        return Component.literal(value);
    }

    /** Returns a readable warm/neutral/cold text colour for an altitude modifier. */
    public static int temperatureColor(double temperatureDelta)
    {
        if (temperatureDelta == 0.0D)
        {
            return 0xFFFFFF;
        }

        int color;
        if (temperatureDelta > 0.0D)
        {
            color = interpolateColor(0xFFFFFF, 0xFF4B3E, Math.min(1.0D, temperatureDelta / 0.08D));
        }
        else
        {
            color = interpolateColor(0xFFFFFF, 0x3D75FF, Math.min(1.0D, -temperatureDelta / 0.45D));
        }
        return color;
    }

    private static int interpolateColor(int start, int end, double progress)
    {
        int red = interpolateChannel(start >> 16, end >> 16, progress);
        int green = interpolateChannel((start >> 8) & 0xFF, (end >> 8) & 0xFF, progress);
        int blue = interpolateChannel(start & 0xFF, end & 0xFF, progress);
        return (red << 16) | (green << 8) | blue;
    }

    private static int interpolateChannel(int start, int end, double progress)
    {
        return (int) Math.round(start + ((end - start) * progress));
    }

    private static boolean containsWhitespace(String value)
    {
        for (int index = 0; index < value.length(); index++)
        {
            if (Character.isWhitespace(value.charAt(index)))
            {
                return true;
            }
        }
        return false;
    }
}
