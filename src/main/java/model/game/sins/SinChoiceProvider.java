package model.game.sins;

import model.game.player.Run;

/**
 * Resolves the synchronous player decisions some sins need (Pride's multiplier, later Wrath's card pick) that a
 * headless model cannot produce on its own. Injected into the {@link model.game.Match} like
 * {@link model.game.SinSelector}: a real client supplies UI-backed choices, while tests and headless runs use a
 * deterministic policy. Returns an index into {@link SinChoice#options()}; the calling {@link SinModifier} clamps
 * defensively, so an out-of-range answer degrades to the first option rather than throwing.
 */
@FunctionalInterface
public interface SinChoiceProvider {

    /** The chosen index into {@code request.options()} for {@code run}. */
    int choose(Run run, SinChoice request);

    /** Deterministic default: always the first option (for Pride, the no-gamble x1). Used in headless/test contexts. */
    SinChoiceProvider FIRST = (run, request) -> 0;
}
