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
