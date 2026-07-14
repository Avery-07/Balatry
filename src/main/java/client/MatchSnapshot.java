package client;

import model.cards.DeckCard;
import model.game.Match;
import model.game.MatchPhase;
import model.game.Standings;
import model.game.net.MatchClient;
import model.game.player.PlayerId;
import model.game.player.Round;
import model.game.player.Run;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable, seat-relative view of the match, built from the client's local host. This is the
 * <em>information boundary</em>: it exposes the local seat's full private state (hand, board, money, shop) but
 * only points and ranking for opponents, honoring the hidden-information design.
 *
 * <p>Two honesty caveats worth keeping in view:
 * <ul>
 *   <li>The transport is lockstep — the local model actually holds every opponent's private state, so this
 *       filtering is a <em>policy</em>, not enforcement. A server-authoritative variant that pushes per-seat
 *       filtered frames would make it a real boundary; until then, this class simply declines to read what it
 *       shouldn't show.</li>
 *   <li>Fields are captured as display strings (via {@link String#valueOf}) rather than live model objects, so
 *       a snapshot is a frozen, render-safe value that never dereferences the mutating model after it is built.
 *       {@link #of} must be called on the FX thread (see {@code MatchViewModel}), so it reads a settled model.</li>
 * </ul>
 */
public record MatchSnapshot(
        int seat,
        String name,
        MatchPhase phase,
        int ante,
        int anteCount,
        String blind,
        long target,
        String activeSin,
        String boss,
        String skipTag,
        long money,
        RoundView round,           // null outside a round
        List<String> hand,
        List<String> jokers,
        List<String> consumables,
        List<String> relics,
        boolean inShop,
        List<OpponentView> opponents
) {

    /** Round-scoped counters, present only while a round is in progress. */
    public record RoundView(int handsRemaining, int discardsRemaining, String score, long roundTarget) { }

    /** The only opponent state that crosses the information boundary: identity, points, ranking. */
    public record OpponentView(int seat, String name, long points, int rank) { }

    /** Builds a snapshot from the client's local host. Call on the FX thread. */
    public static MatchSnapshot of(MatchClient client) {
        Match match = client.getLocalHost().getMatch();
        PlayerId me = client.getSeat();
        Run run = match.getRun(me);

        Round r = run.getRound();
        RoundView roundView = (r == null) ? null
                : new RoundView(r.getHandsRemaining(), r.getDiscardsRemaining(),
                                String.valueOf(r.getScore()), r.getTarget());

        Standings standings = match.getStandings();
        List<PlayerId> ranking = standings.ranking();

        List<OpponentView> opponents = new ArrayList<>();
        for (PlayerId id : match.getSeats()) {
            if (id.seat() == me.seat()) continue;
            opponents.add(new OpponentView(
                    id.seat(),
                    match.getPlayer(id).name(),
                    standings.getPoints(id),
                    ranking.indexOf(id)));
        }

        return new MatchSnapshot(
                me.seat(),
                match.getPlayer(me).name(),
                match.getPhase(),
                match.getAnte(),
                match.getAnteCount(),
                String.valueOf(match.getBlind()),
                match.getCurrentTarget(),
                String.valueOf(match.getActiveSin()),
                match.getCurrentBoss() == null ? null : String.valueOf(match.getCurrentBoss()),
                String.valueOf(match.getCurrentTag()),
                run.getMoney(),
                roundView,
                display(run.getHeld()),
                display(run.getJokers()),
                display(run.getConsumables()),
                display(run.getRelics()),
                match.getPhase() == MatchPhase.SHOP,
                opponents);
    }

    private static List<String> display(List<?> items) {
        List<String> out = new ArrayList<>(items.size());
        for (Object o : items) out.add(describe(o));
        return out;
    }

    /**
     * A readable label for one item. {@link DeckCard}s render as {@code RANK-SUIT} with any enhancement/seal
     * appended; everything else falls back to {@link String#valueOf}. Card types other than {@code DeckCard}
     * (jokers, consumables, relics) rely on their own {@code toString} for now — worth tightening once we can
     * see their real output in the debug view.
     */
    private static String describe(Object o) {
        if (o instanceof DeckCard c) {
            StringBuilder sb = new StringBuilder(c.getRank().name()).append('-').append(c.getSuit().name());
            if (c.getEnhancement() != null) sb.append('[').append(c.getEnhancement()).append(']');
            if (c.getSeal() != null) sb.append('{').append(c.getSeal()).append('}');
            return sb.toString();
        }
        return String.valueOf(o);
    }
}
