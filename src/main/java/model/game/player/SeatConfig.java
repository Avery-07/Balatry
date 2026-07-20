package model.game.player;

import model.game.Stake;

import java.util.ArrayList;
import java.util.List;

/**
 * One seat's opening choices: display name, {@link Sleeve} and {@link Stake}. The deck is deliberately absent —
 * it is a table-wide choice living in {@code MatchConfig}, while these two are per-seat.
 *
 * <p>Every client replays the match locally, so all seats must agree on every seat's config or the replays
 * diverge. Treat this record as part of the match's wire-visible setup, not as local preference.
 */
public record SeatConfig(String name, Sleeve sleeve, Stake stake) {

    public SeatConfig {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name required");
        if (sleeve == null) sleeve = Sleeve.STANDARD;
        if (stake == null)  stake = Stake.WHITE;
    }

    /** A seat with the default sleeve and stake. */
    public static SeatConfig of(String name) { return new SeatConfig(name, Sleeve.STANDARD, Stake.WHITE); }

    /** Default seats for a list of names — the shape older callers and tests expect. */
    public static List<SeatConfig> defaults(List<String> names) {
        List<SeatConfig> out = new ArrayList<>(names.size());
        for (String n : names) out.add(of(n));
        return out;
    }
}
