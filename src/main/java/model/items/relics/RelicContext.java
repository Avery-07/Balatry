package model.items.relics;

import model.game.Match;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.rng.RngSource;

import java.util.random.RandomGenerator;

/** Everything a {@link RelicEffect} needs at resolution: the owning {@link Match}, the caster's {@link Run} and seat, the targeted {@link Run} and seat (both {@code null} for self/global relics), the caster's {@link RelicTarget} selection, and {@code relicCode} — this relic's stable identity, so its rolls stay isolated to it. */
public record RelicContext(Match match, Run source, PlayerId sourceId,
                           Run target, PlayerId targetId, RelicTarget selection, int relicCode) {

    /** A stream isolated to this relic: keyed by {@link RngSource#RELIC} plus the relic's own code, advancing only its own counter. */
    public RandomGenerator random() {
        return source.getRng().streamFor(RngSource.RELIC, source.nextSalt(RngSource.RELIC, relicCode));
    }
}
