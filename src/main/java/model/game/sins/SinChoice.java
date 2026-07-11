package model.game.sins;

import model.game.Sin;

import java.util.List;

/** A choice a sin offers a player at decision time: the sin asking, a prompt, and the ordered options. */
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
