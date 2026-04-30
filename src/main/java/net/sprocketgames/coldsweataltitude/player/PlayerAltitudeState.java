package net.sprocketgames.coldsweataltitude.player;

import net.sprocketgames.coldsweataltitude.temperature.AltitudeBand;

import java.util.Objects;

public final class PlayerAltitudeState
{
    private String activeBand;
    private boolean bandChanged;
    private int ticksInBand;
    private long lastMessageTick = Long.MIN_VALUE;
    private double protectionMultiplier = 1.0D;
    private double shelterMultiplier = 1.0D;
    private double shelterEnclosure;
    private double finalModifier;

    public void refresh(AltitudeBand band, int elapsedTicks, double protectionMultiplier, double shelterMultiplier, double shelterEnclosure)
    {
        String nextBand = band == null ? null : band.id();
        bandChanged = !Objects.equals(activeBand, nextBand);
        activeBand = nextBand;
        ticksInBand = bandChanged ? 0 : ticksInBand + elapsedTicks;

        this.protectionMultiplier = protectionMultiplier;
        this.shelterMultiplier = shelterMultiplier;
        this.shelterEnclosure = shelterEnclosure;
        this.finalModifier = band == null ? 0.0D : band.effectiveModifier(protectionMultiplier, shelterMultiplier);

        if (activeBand == null)
        {
            lastMessageTick = Long.MIN_VALUE;
        }
    }

    public String activeBand()
    {
        return activeBand;
    }

    public boolean bandChanged()
    {
        return bandChanged;
    }

    public int ticksInBand()
    {
        return ticksInBand;
    }

    public long lastMessageTick()
    {
        return lastMessageTick;
    }

    public void lastMessageTick(long lastMessageTick)
    {
        this.lastMessageTick = lastMessageTick;
    }

    public double protectionMultiplier()
    {
        return protectionMultiplier;
    }

    public double shelterMultiplier()
    {
        return shelterMultiplier;
    }

    public double shelterEnclosure()
    {
        return shelterEnclosure;
    }

    public double finalModifier()
    {
        return finalModifier;
    }
}
