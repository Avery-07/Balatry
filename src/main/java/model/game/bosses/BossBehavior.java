package model.game.bosses;

import model.game.Match;
import model.game.player.BlindResult;
import model.game.player.PlayerId;
import model.game.player.Run;

import java.util.Map;
import java.util.Set;

/** Match-level behaviour for boss blinds whose effect is resolved across players rather than within one run — the seam {@link model.game.BossBlind}'s declarative flags cannot express. */
public interface BossBehavior {

    /** The no-op behaviour, active outside boss rounds and for bosses with no cross-player component. */
    BossBehavior NONE = new BossBehavior() {};

    /** Called once when the boss blind is dealt, after every seat's round has begun. */
    default void onBossBegin(Match match) {}

    /** The shared discard pool this seat draws from instead of its own counter, or {@code null} if this boss imposes none or the seat is not (or no longer) participating. */
    default SharedDiscardPool sharedDiscards(Run run) { return null; }

    /** Adjusts the settled results at the barrier, before sin settlement and the points award — the only moment all seats' results exist simultaneously. */
    default Map<PlayerId, BlindResult> adjustResults(Match match, Map<PlayerId, BlindResult> results,
                                                     Set<PlayerId> participants) {
        return results;
    }

    /** Called once at the barrier after results are adjusted; undo any state applied at {@link #onBossBegin}. */
    default void onBossEnd(Match match) {}
}
