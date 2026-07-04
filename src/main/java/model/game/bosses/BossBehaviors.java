package model.game.bosses;

import model.game.BossBlind;

/** Maps a {@link BossBlind} to its Match-level behaviour; returns a fresh instance so behaviours may hold round state. */
public final class BossBehaviors {

    private BossBehaviors() {}

    /** The behaviour for {@code boss}, or {@link BossBehavior#NONE} for bosses with no cross-player component. */
    public static BossBehavior behaviorFor(BossBlind boss) {
        if (boss == null) return BossBehavior.NONE;
        return switch (boss) {
            case THE_HIVEMIND  -> new HivemindBehavior();
            case THE_COMMONS   -> new CommonsBehavior();
            case THE_BANDWAGON -> new BandwagonBehavior();
            case THE_SHAVE     -> new ShaveBehavior();
            default            -> BossBehavior.NONE;
        };
    }
}
