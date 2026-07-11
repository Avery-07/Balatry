package model.game;

import model.game.player.BlindResult;
import model.game.player.PlayerId;

import java.util.Map;

/** Policy for converting one settled blind's results into competition points, injected via {@link MatchConfig} like every other match policy — the round-value curve and the distribution rule are tuning knobs, so they live behind this seam rather than in the engine. */
@FunctionalInterface
public interface PointsPolicy {

    /** The base points each seat earns for this settled blind. */
    Map<PlayerId, Long> award(int ante, Blind blind, Map<PlayerId, BlindResult> results);

    /** The default policy: chip-proportional split of a linearly growing round reward. */
    PointsPolicy PROPORTIONAL = new ProportionalPointsPolicy();
}
