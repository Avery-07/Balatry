package client;

import model.cards.DeckCard;
import model.game.Match;
import model.game.MatchPhase;
import model.game.Standings;
import model.game.player.BlindResult;
import model.cards.relics.RelicCard;
import model.cards.Card;
import model.cards.packs.BoosterPack;
import model.cards.jokers.JokerCard;
import model.cards.consumables.ConsumableCard;
import model.cards.vouchers.Voucher;
import model.modifiers.Sticker;
import model.cards.packs.PackOpening;
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
        List<String> pendingPacks,      // bought or granted packs waiting to be opened
        PackView openPack,              // the pack currently being picked from, or null
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

    /**
     * An open booster pack. {@code options} keeps its slot indices — an already-taken option stays in place as
     * null so the remaining indices never shift under the player mid-pick.
     */
    public record PackView(String pack, List<PackOptionView> options, int picksLeft) { }

    /** One offer in an open pack; {@code relic} is non-null when taking it casts a relic on the spot. */
    public record PackOptionView(String label, boolean taken, RelicView relic) { }

    /** One seat's line in the final standings; {@code isMe} marks the local seat. */
    public record StandingView(int seat, String name, long points, int rank, boolean isMe) { }

    /** This seat's most recent blind outcome, for the result summary. */
    public record ResultView(String outcome, String score, long target, String bestHand,
                             int handsRemaining, int moneyEarned) { }

    /** Shop contents for the local seat, present only during the shop phase. Prices index-align with the lists. */
    /**
     * One buyable or holdable item. {@code label} is the human name, {@code detail} the hover text — the model
     * stores no rules prose, so detail carries the structured facts it does know (kind, rarity, cost, edition).
     */
    public record ItemView(String label, String detail, int price, boolean sold) { }

    public record ShopView(
            List<ItemView> slots,
            List<ItemView> packs,
            List<ItemView> vouchers,
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

        List<String> pendingPacks = new ArrayList<>();
        for (BoosterPack p : run.getPendingPacks()) pendingPacks.add(describe(p));
        PackView openPack = packView(run.getCurrentOpening());

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
                pendingPacks,
                openPack,
                table,
                opponents);
    }

    /**
     * Reads the shop defensively: a bought slot is left empty rather than removed, and the price accessors reject
     * empty slots outright, so a sold entry is rendered in place as {@code (sold)}. Keeping the empty slots in the
     * list matters — buy indices address positions, so collapsing them would silently retarget the next purchase.
     */
    private static ShopView buildShop(Shop shop) {
        List<ItemView> slots = new ArrayList<>();
        for (int i = 0; i < shop.getSlotCount(); i++) {
            Card card = shop.getSlot(i);
            slots.add(card == null ? sold() : new ItemView(describe(card), detail(card), shop.slotPrice(i), false));
        }
        List<ItemView> packs = new ArrayList<>();
        for (int i = 0; i < shop.getPackCount(); i++) {
            BoosterPack pack = shop.getPack(i);
            packs.add(pack == null ? sold() : new ItemView(describe(pack), detail(pack), shop.packPrice(i), false));
        }
        List<ItemView> vouchers = new ArrayList<>();
        for (int i = 0; i < shop.getVoucherCount(); i++) {
            Object voucher = shop.getVoucher(i);
            vouchers.add(voucher == null
                    ? new ItemView("(redeemed)", "already taken", 0, true)
                    : new ItemView(describe(voucher), detail(voucher),
                                   voucher instanceof Card c ? c.getShopValue() : 0, false));
        }
        return new ShopView(slots, packs, vouchers, shop.rerollCost(), shop.purchasesRemaining());
    }

    /** Describes each held relic so the client can ask for exactly the choices it needs. */
    /** Appends the marks that change how a card plays: its edition and any stickers. */
    private static String decorate(Card card, String name) {
        StringBuilder sb = new StringBuilder(name);
        if (card.getEdition() != null) sb.append('[').append(card.getEdition()).append(']');
        if (card.isDebuffed()) sb.append("{DEBUFFED}");
        return sb.toString();
    }

    private static List<RelicView> relicViews(List<RelicCard> relics) {
        List<RelicView> out = new ArrayList<>(relics.size());
        for (RelicCard r : relics) out.add(relicView(r));
        return out;
    }

    /** What one relic demands of its caster: a seat only when it is aimed by hand, plus its selector. */
    private static RelicView relicView(RelicCard relic) {
        var spec = relic.getSpec();
        boolean needsSeat = switch (spec.getKind()) {
            case OPPONENT, RIVAL -> true;       // aimed by hand
            case RIVALS, SELF, GLOBAL -> false; // the standings (or nothing) decide
        };
        return new RelicView(spec.getName(), spec.getKind().name(), spec.getSelector().name(), needsSeat);
    }

    /** The open pack, preserving option indices so a taken slot does not shift the ones after it. */
    private static PackView packView(PackOpening opening) {
        if (opening == null) return null;
        List<PackOptionView> options = new ArrayList<>();
        for (Card option : opening.getOptions()) {
            if (option == null) options.add(new PackOptionView("(taken)", true, null));
            else options.add(new PackOptionView(describe(option), false,
                    option instanceof RelicCard relic ? relicView(relic) : null));
        }
        return new PackView(describe(opening.getPack()), options, opening.getPicksLeft());
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
    private static ItemView sold() { return new ItemView("(sold)", "already bought", 0, true); }

    /**
     * Hover text. The model holds no rules prose — a joker's behaviour is a lambda, not a sentence — so this
     * reports the facts it can actually answer for: what the card is, its rarity or type, its price, and any
     * edition or stickers riding on it. Real rules text would have to come from the catalogue document.
     */
    private static String detail(Object o) {
        StringBuilder d = new StringBuilder();
        if (o instanceof JokerCard j) {
            d.append("Joker \u2014 ").append(j.getSpec().getRarity()).append(", base $").append(j.getSpec().getCost());
        } else if (o instanceof ConsumableCard c) {
            d.append(c.getSpec().getType()).append(" \u2014 base $").append(c.getSpec().getCost());
        } else if (o instanceof RelicCard r) {
            d.append("Relic \u2014 ").append(r.getSpec().getKind()).append(", base $").append(r.getSpec().getCost());
            if (r.getSpec().getSelector() != model.cards.relics.RelicSelector.NONE)
                d.append(", choose a ").append(r.getSpec().getSelector().name().toLowerCase().replace('_', ' '));
        } else if (o instanceof Voucher v) {
            d.append("Voucher \u2014 base $").append(v.getSpec().getCost());
        } else if (o instanceof BoosterPack p) {
            d.append("Booster pack \u2014 ").append(p.baseOptionCount()).append(" options, ")
             .append(p.basePickCount()).append(" pick(s)");
        } else if (o instanceof DeckCard c) {
            d.append("Playing card \u2014 ").append(c.getRank()).append(" of ").append(c.getSuit());
        } else {
            d.append(String.valueOf(o));
        }
        if (o instanceof Card card) {
            if (card.getEdition() != null) d.append("  \u00b7 ").append(card.getEdition());
            for (Sticker sticker : card.getStickers().keySet()) d.append("  \u00b7 ").append(sticker);
        }
        return d.toString();
    }

    private static String describe(Object o) {
        if (o instanceof BoosterPack p)   // no toString on the model type; render it as SIZE KIND
            return p.size() + " " + p.kind();
        if (o instanceof JokerCard j)       return decorate(j, j.getSpec().getName());
        if (o instanceof ConsumableCard c)  return decorate(c, c.getSpec().getName());
        if (o instanceof RelicCard r)       return decorate(r, r.getSpec().getName());
        if (o instanceof Voucher v)         return decorate(v, v.getSpec().getName());
        if (o instanceof DeckCard c) {
            StringBuilder sb = new StringBuilder(c.getRank().name()).append('-').append(c.getSuit().name());
            if (c.getEnhancement() != null) sb.append('[').append(c.getEnhancement()).append(']');
            if (c.getSeal() != null) sb.append('{').append(c.getSeal()).append('}');
            return sb.toString();
        }
        return String.valueOf(o);
    }
}
