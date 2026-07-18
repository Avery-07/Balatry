package client;

import model.cards.DeckCard;
import model.game.Match;
import model.game.MatchPhase;
import model.game.Standings;
import model.game.player.BlindResult;
import model.cards.relics.RelicCard;
import model.game.net.MatchClient;
import model.game.player.PlayerId;
import model.game.player.Round;
import model.game.player.RoundOutcome;
import model.game.player.Run;
import model.game.shop.Shop;

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
        List<RelicView> relicCards,   // the same relics, with what each one asks the caster for
        boolean inShop,
        ShopView shop,             // null outside the shop phase
        boolean hasChosen,         // this seat has made its blind-selection choice
        ResultView lastResult,     // this seat's most recent blind outcome (null before the first)
        List<StandingView> standings,   // every seat, ranked; for the end-of-match summary
        List<OpponentView> opponents
) {

    /** Round-scoped counters, present only while a round is in progress. */
    public record RoundView(int handsRemaining, int discardsRemaining, String score, long roundTarget, boolean canSkip) { }

    /** The only opponent state that crosses the information boundary: identity, points, ranking. */
    public record OpponentView(int seat, String name, long points, int rank) { }

    /**
     * A held relic and its demands. {@code needsSeat} is true only for relics the caster aims by hand; the
     * standings-driven ones pick their own victims, so the UI must not offer a seat for them.
     */
    public record RelicView(String name, String kind, String selector, boolean needsSeat) { }

    /** One seat's line in the final standings; {@code isMe} marks the local seat. */
    public record StandingView(int seat, String name, long points, int rank, boolean isMe) { }

    /** This seat's most recent blind outcome, for the result summary. */
    public record ResultView(String outcome, String score, long target, String bestHand,
                             int handsRemaining, int moneyEarned) { }

    /** Shop contents for the local seat, present only during the shop phase. Prices index-align with the lists. */
    public record ShopView(
            List<String> slots, List<Integer> slotPrices,
            List<String> packs, List<Integer> packPrices,
            List<String> vouchers,
            int rerollCost, int purchasesRemaining) { }

    /** Builds a snapshot from the client's local host. Call on the FX thread. */
    public static MatchSnapshot of(MatchClient client) {
        Match match = client.getLocalHost().getMatch();
        PlayerId me = client.getSeat();
        Run run = match.getRun(me);

        Round r = run.getRound();
        RoundView roundView = (r == null) ? null
                : new RoundView(r.getHandsRemaining(), r.getDiscardsRemaining(),
                                String.valueOf(r.getScore()), r.getTarget(),
                                r.getOutcome() == RoundOutcome.IN_PROGRESS && !r.isActed());

        Standings standings = match.getStandings();
        List<PlayerId> ranking = standings.ranking();

        List<StandingView> table = new ArrayList<>();
        for (PlayerId id : ranking)
            table.add(new StandingView(
                    id.seat(),
                    match.getPlayer(id).name(),
                    standings.getPoints(id),
                    ranking.indexOf(id),
                    id.seat() == me.seat()));

        List<OpponentView> opponents = new ArrayList<>();
        for (PlayerId id : match.getSeats()) {
            if (id.seat() == me.seat()) continue;
            opponents.add(new OpponentView(
                    id.seat(),
                    match.getPlayer(id).name(),
                    standings.getPoints(id),
                    ranking.indexOf(id)));
        }

        boolean hasChosen = match.hasChosenBlind(me);
        BlindResult br = match.getResult(me);
        ResultView lastResult = (br == null) ? null
                : new ResultView(String.valueOf(br.outcome()), String.valueOf(br.score()), br.target(),
                                 String.valueOf(br.bestHand()), br.handsRemaining(), br.moneyEarned());

        boolean inShop = match.getPhase() == MatchPhase.SHOP;
        ShopView shopView = null;
        if (inShop) {
            Shop shop = run.getShop();
            if (shop != null) shopView = buildShop(shop);
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
                relicViews(run.getRelics()),
                inShop,
                shopView,
                hasChosen,
                lastResult,
                table,
                opponents);
    }

    private static ShopView buildShop(Shop shop) {
        List<String> slots = new ArrayList<>();
        List<Integer> slotPrices = new ArrayList<>();
        for (int i = 0; i < shop.getSlotCount(); i++) {
            slots.add(describe(shop.getSlot(i)));
            slotPrices.add(shop.slotPrice(i));
        }
        List<String> packs = new ArrayList<>();
        List<Integer> packPrices = new ArrayList<>();
        for (int i = 0; i < shop.getPackCount(); i++) {
            packs.add(String.valueOf(shop.getPack(i)));
            packPrices.add(shop.packPrice(i));
        }
        List<String> vouchers = new ArrayList<>();
        for (int i = 0; i < shop.getVoucherCount(); i++) {
            vouchers.add(String.valueOf(shop.getVoucher(i)));
        }
        return new ShopView(slots, slotPrices, packs, packPrices, vouchers,
                shop.rerollCost(), shop.purchasesRemaining());
    }

    /** Describes each held relic so the client can ask for exactly the choices it needs. */
    private static List<RelicView> relicViews(List<RelicCard> relics) {
        List<RelicView> out = new ArrayList<>(relics.size());
        for (RelicCard r : relics) {
            var spec = r.getSpec();
            boolean needsSeat = switch (spec.getKind()) {
                case OPPONENT, RIVAL -> true;      // aimed by hand
                case RIVALS, SELF, GLOBAL -> false; // the standings (or nothing) decide
            };
            out.add(new RelicView(spec.getName(), spec.getKind().name(), spec.getSelector().name(), needsSeat));
        }
        return out;
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
