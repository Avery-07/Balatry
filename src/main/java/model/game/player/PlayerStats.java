package model.game.player;

import model.game.rng.LuckEvent;

/** Per-player counters that live beside a {@link Run}; currently the per-event luck occurrence counts. */
public final class PlayerStats {

    private int glassBreak;       // Glass shatter rolls
    private int luckyMult;        // Lucky +mult rolls
    private int luckyMoney;       // Lucky +money rolls
    private int wheelOfFortune;   // Wheel of Fortune edition rolls
    private int starNegative;     // The Star negative-edition rolls
    private int moonEdition;      // The Moon edition rolls
    private int sunEdition;       // The Sun edition rolls

    /** Returns the salt for the next roll of {@code event} (its 0-based occurrence index), then advances that counter. */
    public long nextSalt(LuckEvent event) {
        return switch (event) {
            case GLASS_BREAK      -> glassBreak++;
            case LUCKY_MULT       -> luckyMult++;
            case LUCKY_MONEY      -> luckyMoney++;
            case WHEEL_OF_FORTUNE -> wheelOfFortune++;
            case STAR_NEGATIVE    -> starNegative++;
            case MOON_EDITION     -> moonEdition++;
            case SUN_EDITION      -> sunEdition++;
        };
    }

    /** How many times {@code event} has fired so far. Does not advance the counter. */
    public int getCount(LuckEvent event) {
        return switch (event) {
            case GLASS_BREAK      -> glassBreak;
            case LUCKY_MULT       -> luckyMult;
            case LUCKY_MONEY      -> luckyMoney;
            case WHEEL_OF_FORTUNE -> wheelOfFortune;
            case STAR_NEGATIVE    -> starNegative;
            case MOON_EDITION     -> moonEdition;
            case SUN_EDITION      -> sunEdition;
        };
    }
}