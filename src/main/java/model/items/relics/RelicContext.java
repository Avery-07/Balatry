package model.items.relics;

import model.game.Match;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.rng.RngSource;

import java.util.random.RandomGenerator;

/** Everything a {@link RelicEffect} needs at resolution: the owning {@link Match}, the caster's {@link Run} and seat, the targeted {@link Run} and seat (both {@code null} for self/global relics), and the caster's {@link RelicTarget} selection. */
public record RelicContext(Match match, Run source, PlayerId sourceId,
                           Run target, PlayerId targetId, RelicTarget selection) {

    /** A keyed stream for this resolution, salted by the caster's next {@link RngSource#RELIC_EFFECT} occurrence. */
    public RandomGenerator random() {
        return source.getRng().streamFor(RngSource.RELIC_EFFECT, source.nextSalt(RngSource.RELIC_EFFECT));
    }
}
