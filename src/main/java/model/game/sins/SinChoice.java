package model.game.sins;

import model.game.Sin;

import java.util.List;

/**
 * A choice a sin offers a player at decision time: the sin asking, a prompt, and the ordered options. The
 * {@link SinChoiceProvider} answers with an index into {@link #options()}; the requesting {@link SinModifier}
 * maps that index to meaning (e.g. Pride maps option 2 to a x2 multiplier). Generic by design so every
 * choice-driven sin (Pride's multiplier now, Wrath's 1-of-4 cards later) uses the same provider seam.
 */
public record SinChoice(Sin sin, String prompt, List<String> options) {

    public SinChoice {
        if (sin == null) throw new IllegalArgumentException("sin required");
        if (options == null || options.isEmpty())
            throw new IllegalArgumentException("a choice needs at least one option");
        options = List.copyOf(options);
    }

    /** Number of options offered. */
    public int size() { return options.size(); }
}
