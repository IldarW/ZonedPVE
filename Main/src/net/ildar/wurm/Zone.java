package net.ildar.wurm;

import java.awt.*;

public enum Zone {
    /**
     * Always PvE
     */
    PVE(new Color(0, 255, 0)),

    /**
     * Always PvP
     */
    PVP(new Color(255, 0, 0)),

    /**
     * PvE with scheduled PvP
     */
    TimedPvp(new Color(255, 255, 0)),

    /**
     * PvP with PvE at deeds
     */
    PveOnDeeds(new Color(255, 128, 128)),

    /**
     * PvP with PvE at deeds. Scheduled PvP at deeds.
     */
    TimedPveOnDeeds(new Color(255, 128, 0));

    private int color;

    Zone(Color color) {
        this.color = color.getRGB();
    }

    public int getColor() {
        return this.color;
    }
}
