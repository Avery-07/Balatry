package model.game;

import model.game.player.PlayerId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The cumulative competition standings of a match: total points per seat plus the most recent round's award.
 * Owned and updated by {@link Match} at the settlement barrier; everything here is read-only to the outside.
 * By default the players' only information about each other is their ranking and point totals — this object
 * is exactly that surface.
 */
public final class Standings {

    private final Map<PlayerId, Long> totals = new LinkedHashMap<>();   // seat order
    private Map<PlayerId, Long> lastAward = Map.of();

    Standings(Collection<PlayerId> seats) {
        for (PlayerId id : seats) totals.put(id, 0L);
    }

    /** Records one settled round's award (already sin-adjusted); seats absent from {@code award} earned nothing. */
    void record(Map<PlayerId, Long> award) {
        lastAward = Map.copyOf(award);
        for (Map.Entry<PlayerId, Long> e : award.entrySet())
            totals.merge(e.getKey(), e.getValue(), Long::sum);
    }

    /** This seat's cumulative points. */
    public long getPoints(PlayerId id) { return totals.getOrDefault(id, 0L); }

    /** Cumulative points by seat, in seat order. */
    public Map<PlayerId, Long> getTotals() { return Map.copyOf(totals); }

    /** The most recent round's award by seat (seats that earned nothing are absent), empty before the first settlement. */
    public Map<PlayerId, Long> getLastAward() { return lastAward; }

    /** Seats ordered by points, highest first; ties keep seat order (stable). */
    public List<PlayerId> ranking() {
        List<PlayerId> order = new ArrayList<>(totals.keySet());
        order.sort((a, b) -> Long.compare(totals.get(b), totals.get(a)));
        return order;
    }
}
