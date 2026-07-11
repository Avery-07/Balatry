package model.game.sins;

import model.game.player.Run;

/** Resolves the synchronous player decisions some sins need (Pride's multiplier, later Wrath's card pick) that a headless model cannot produce on its own. */
@FunctionalInterface
public interface SinChoiceProvider {

    /** The chosen index into {@code request.options()} for {@code run}. */
    int choose(Run run, SinChoice request);

    /** Deterministic default: always the first option (for Pride, the no-gamble x1). Used in headless/test contexts. */
    SinChoiceProvider FIRST = (run, request) -> 0;
}
