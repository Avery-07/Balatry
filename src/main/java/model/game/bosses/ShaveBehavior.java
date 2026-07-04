package model.game.bosses;

import model.game.Match;
import model.game.player.BlindResult;
import model.game.player.PlayerId;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Shave: the single highest-scoring hand across all participating players is excluded from its owner's
 * settled score — the score the points award reads. The outcome is never changed: a cleared blind stays
 * cleared (no retroactive failure at the barrier). Mirrored seeds make exact cross-seat ties real, not
 * theoretical; every seat whose best hand equals the global maximum is shaved, which is the only symmetric
 * resolution under identical play.
 */
final class ShaveBehavior implements BossBehavior {

    @Override
    public Map<PlayerId, BlindResult> adjustResults(Match match, Map<PlayerId, BlindResult> results,
                                                    Set<PlayerId> participants) {
        BigDecimal max = BigDecimal.ZERO;
        for (Map.Entry<PlayerId, BlindResult> e : results.entrySet())
            if (participants.contains(e.getKey()) && e.getValue().bestHand().compareTo(max) > 0)
                max = e.getValue().bestHand();
        if (max.signum() <= 0) return results;

        Map<PlayerId, BlindResult> adjusted = new LinkedHashMap<>();
        for (Map.Entry<PlayerId, BlindResult> e : results.entrySet()) {
            BlindResult r = e.getValue();
            boolean shaved = participants.contains(e.getKey()) && r.bestHand().compareTo(max) == 0;
            adjusted.put(e.getKey(), shaved ? r.withScore(r.score().subtract(r.bestHand())) : r);
        }
        return adjusted;
    }
}
