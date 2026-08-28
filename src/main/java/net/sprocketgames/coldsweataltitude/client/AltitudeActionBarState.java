package net.sprocketgames.coldsweataltitude.client;

public final class AltitudeActionBarState
{
    private static final long FADE_IN_MILLIS = 250L;
    private static final long FADE_OUT_MILLIS = 500L;

    private static String message = "";
    private static double temperatureDelta;
    private static long shownAtMillis = Long.MIN_VALUE;
    private static long holdMillis = 5_000L;

    private AltitudeActionBarState()
    {
    }

    public static void update(String nextMessage, double nextTemperatureDelta, int displayTicks)
    {
        long now = System.currentTimeMillis();
        float currentOpacity = opacity(now);
        boolean alreadyVisible = !message.isBlank() && currentOpacity > 0.0F;
        if (alreadyVisible && message.equals(nextMessage)
            && Double.compare(temperatureDelta, nextTemperatureDelta) == 0)
        {
            return;
        }
        message = nextMessage;
        temperatureDelta = nextTemperatureDelta;
        holdMillis = Math.max(1, displayTicks) * 50L;
        // Do not restart the fade when moving through bands quickly. Update
        // the text in place and restart its visible hold period instead.
        shownAtMillis = alreadyVisible ? now - FADE_IN_MILLIS : now;
    }

    public static String message()
    {
        return message;
    }

    public static double temperatureDelta()
    {
        return temperatureDelta;
    }

    public static float opacity()
    {
        return opacity(System.currentTimeMillis());
    }

    private static float opacity(long now)
    {
        long elapsed = now - shownAtMillis;
        if (elapsed < 0 || message.isBlank())
        {
            return 0.0F;
        }
        if (elapsed < FADE_IN_MILLIS)
        {
            return elapsed / (float) FADE_IN_MILLIS;
        }
        if (elapsed < FADE_IN_MILLIS + holdMillis)
        {
            return 1.0F;
        }
        if (elapsed >= FADE_IN_MILLIS + holdMillis + FADE_OUT_MILLIS)
        {
            return 0.0F;
        }
        return 1.0F - ((elapsed - FADE_IN_MILLIS - holdMillis) / (float) FADE_OUT_MILLIS);
    }
}
