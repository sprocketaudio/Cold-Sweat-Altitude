package net.sprocketgames.coldsweataltitude.client;

public final class ShelterHudState
{
    private static final long FADE_IN_DURATION_MILLIS = 500L;
    private static final long HIGHLIGHT_DURATION_MILLIS = 5_000L;
    private static final long FADE_DURATION_MILLIS = 1_000L;
    private static final float RESTING_OPACITY = 0.35F;
    private static final double CHANGE_EPSILON = 0.005D;

    private static double shelterEnclosure;
    private static long lastChangeMillis;

    private ShelterHudState()
    {
    }

    public static void update(double enclosure)
    {
        double clamped = Math.max(0.0D, Math.min(1.0D, enclosure));
        if (Math.abs(clamped - shelterEnclosure) >= CHANGE_EPSILON)
        {
            long now = System.currentTimeMillis();
            // A new value while already highlighted should extend the full
            // visibility period, not visibly restart the fade-in animation.
            lastChangeMillis = hudOpacity() >= 0.99F
                ? now - FADE_IN_DURATION_MILLIS
                : now;
        }
        shelterEnclosure = clamped;
    }

    public static double shelterEnclosure()
    {
        return shelterEnclosure;
    }

    public static float hudOpacity()
    {
        long elapsed = System.currentTimeMillis() - lastChangeMillis;
        if (elapsed <= FADE_IN_DURATION_MILLIS)
        {
            float fadeProgress = elapsed / (float) FADE_IN_DURATION_MILLIS;
            return RESTING_OPACITY + (1.0F - RESTING_OPACITY) * fadeProgress;
        }
        if (elapsed <= FADE_IN_DURATION_MILLIS + HIGHLIGHT_DURATION_MILLIS)
        {
            return 1.0F;
        }
        if (elapsed >= FADE_IN_DURATION_MILLIS + HIGHLIGHT_DURATION_MILLIS + FADE_DURATION_MILLIS)
        {
            return RESTING_OPACITY;
        }

        float fadeProgress = (elapsed - FADE_IN_DURATION_MILLIS - HIGHLIGHT_DURATION_MILLIS) / (float) FADE_DURATION_MILLIS;
        return 1.0F + (RESTING_OPACITY - 1.0F) * fadeProgress;
    }
}
