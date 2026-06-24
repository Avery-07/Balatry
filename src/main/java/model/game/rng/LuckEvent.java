package model.game.rng;

import model.game.player.PlayerStats;

/**
 * The counter-keyed luck events. Each is salted by a per-player occurrence counter (see
 * {@link PlayerStats}), so the N-th occurrence resolves identically for every player on the seed.
 * Each event owns its own {@link RngSource} so the counter alone is a collision-free salt.
 */
public enum LuckEvent {
    GLASS_BREAK(RngSource.GLASS_BREAK),
    LUCKY_MULT(RngSource.LUCKY_MULT),
    LUCKY_MONEY(RngSource.LUCKY_MONEY),
    WHEEL_OF_FORTUNE(RngSource.WHEEL_OF_FORTUNE);

    private final RngSource source;

    LuckEvent(RngSource source) { this.source = source; }

    /** The dedicated keyed stream this event draws from. */
    public RngSource getSource() { return source; }
}