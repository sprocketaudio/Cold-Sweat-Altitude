package net.sprocketgames.coldsweataltitude.client;

public final class ShelterHudState
{
    private static final long STALE_AFTER_MILLIS = 5_000L;

    private static double shelterEnclosure;
    private static long lastUpdateMillis;

    private ShelterHudState()
    {
    }

    public static void update(double enclosure)
    {
        shelterEnclosure = Math.max(0.0D, Math.min(1.0D, enclosure));
        lastUpdateMillis = System.currentTimeMillis();
    }

    public static double shelterEnclosure()
    {
        if (System.currentTimeMillis() - lastUpdateMillis > STALE_AFTER_MILLIS)
        {
            return 0.0D;
        }
        return shelterEnclosure;
    }
}
