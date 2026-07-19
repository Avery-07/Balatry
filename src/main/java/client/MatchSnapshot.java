package client;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.jokers.JokerCard;
import model.cards.packs.BoosterPack;
import model.cards.vouchers.Voucher;
import model.game.Blind;
import model.game.BlindTargets;
import model.game.BossBlind;
import model.game.Match;
import model.game.MatchPhase;
import model.game.Standings;
import model.game.player.BlindResult;
import model.cards.consumables.ConsumableCard;
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
 *   <li>Fields are captured as display values (often via {@link String#valueOf}) rather than live model objects,
 *       so a snapshot is a frozen, render-safe value that never dereferences the mutating model after it is
 *       built. {@link #of} must be called on the FX thread (see {@code MatchViewModel}), so it reads a settled
 *       model.</li>
 * </ul>
 */
public record MatchSnapshot(
        int seat,
        String name,
        MatchPhase phase,
        int ante,
        int anteCount,
        int roundNumber,           // cumulative blind count across the match (Balatro's "Round")
        String blind,
        long target,
        String activeSin,
        String boss,
        String skipTag,
        long money,
        int hands,                 // hands available now: the round's remaining, or the base outside a round
        int discards,              // discards available now: the round's remaining, or the base outside a round
        int jokerSlotsUsed,        // top-of-screen joker counter (used / max)
        int jokerSlotsMax,
        int consumableSlotsUsed,   // top-of-screen consumable counter (used / max; relics share this pool)
        int consumableSlotsMax,
        int deckRemaining,         // deck pile: cards left to draw (draw pile during a round, full deck otherwise)
        int deckTotal,             // deck pile: total cards in the deck
        String chips,              // blue chips of the last play this round ("0" before any hand) — the chips×mult readout
        String mult,               // red mult of the last play this round ("0" before any hand)
        RoundView round,           // null outside a round
        List<HandCardView> hand,   // structured so the client can sort by rank or suit without parsing labels
        List<String> jokers,
        List<ItemView> inventory,  // consumables and relics as one indexed area; relics carry their casting demands
        List<BlindOption> blinds,  // the ante's three blinds, for the selection screen (empty outside SELECTION)
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

    /** One card in hand: a stable {@code id} (for cross-frame animation) plus rank/suit ordinals for sorting. */
    public record HandCardView(int id, String label, int rank, int suit) { }

    /**
     * One of the ante's three blinds, as the selection screen shows it: its type, chip target and cash reward,
     * and — for the boss — its name and effect (locked at ante start, so it is known even on the small blind).
     * {@code current} marks the blind actually being selected; {@code skipTag} is the tag skipping it would grant
     * (only the current blind can be skipped, so it is null on the others).
     */
    public record BlindOption(String type, long target, int reward,
                              String bossName, String bossEffect,
                              boolean current, String skipTag) { }

    /**
     * One held inventory item — a consumable or a relic — as the client sees it. Relics and consumables share a
     * single indexed area, so {@code isRelic} plus {@code modelIndex} (the item's position within its own model
     * list) is how a gesture routes to the right verb. The casting fields are meaningful only for relics: {@code
     * kind} names who it lands on, {@code selector} what choice it needs ("NONE" for consumables), and {@code
     * needsSeat} is true only for relics the caster aims by hand.
     */
    public record ItemView(String label, boolean isRelic, int modelIndex,
                           String kind, String selector, boolean needsSeat) { }

    /** One seat's line in the final standings; {@code isMe} marks the local seat. */
    public record StandingView(int seat, String name, long points, int rank, boolean isMe) { }

    /** This seat's most recent blind outcome, for the result summary. */
    public record ResultView(String outcome, String score, long target, String bestHand,
                             int handsRemaining, int moneyEarned) { }

    /** One purchasable shop card/pack: its name, price, and the hover text (name, category, cost). */
    public record ShopItem(String label, int price, String tooltip) { }

    /** One offered voucher: name, price, hover text, and whether it can be redeemed now (one per ante). */
    public record VoucherItem(String label, int price, String tooltip, boolean redeemable) { }

    /** Shop contents for the local seat, present only during the shop phase. */
    public record ShopView(
            List<ShopItem> slots,
            List<ShopItem> packs,
            List<VoucherItem> vouchers,
            int rerollCost, int purchasesRemaining) { }

    /** Builds a snapshot from the client's local host. Call on the FX thread. */
    public static MatchSnapshot of(MatchClient client) {
        return of(client.getLocalHost().getMatch(), client.getSeat());
    }

    /** Builds a seat-relative snapshot directly from a match; the test seam behind {@link #of(MatchClient)}. */
    public static MatchSnapshot of(Match match, PlayerId me) {
        Run run = match.getRun(me);

        Round r = run.getRound();
        RoundView roundView = (r == null) ? null
                : new RoundView(r.getHandsRemaining(), r.getDiscardsRemaining(),
                                String.valueOf(r.getScore()), r.getTarget(),
                                r.getOutcome() == RoundOutcome.IN_PROGRESS && !r.isActed());

        // HUD hands/discards are shown in every phase: the round's live counts during a blind, the base otherwise.
        int hands    = (r != null) ? r.getHandsRemaining()    : run.getBaseHands();
        int discards = (r != null) ? r.getDiscardsRemaining() : run.getBaseDiscards();

        // Slot counters (top of screen) and the deck pile (right edge).
        int deckTotal     = run.getDeck().size();
        int deckRemaining = (r != null) ? r.getDrawPile().size() : deckTotal;
        // Blue chips × red mult of the last play this round; "0" before any hand and reset each round.
        String chips = (r != null) ? r.getLastChips().toBigInteger().toString() : "0";
        String mult  = (r != null) ? r.getLastMult().toBigInteger().toString()  : "0";

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
            if (shop != null) shopView = buildShop(shop, run);
        }

        return new MatchSnapshot(
                me.seat(),
                match.getPlayer(me).name(),
                match.getPhase(),
                match.getAnte(),
                match.getAnteCount(),
                match.getRoundNumber(),
                String.valueOf(match.getBlind()),
                match.getCurrentTarget(),
                String.valueOf(match.getActiveSin()),
                match.getCurrentBoss() == null ? null : String.valueOf(match.getCurrentBoss()),
                String.valueOf(match.getCurrentTag()),
                run.getMoney(),
                hands,
                discards,
                run.usedJokerSlots(),
                run.getJokerSlots(),
                run.usedConsumableSlots(),
                run.getConsumableSlots(),
                deckRemaining,
                deckTotal,
                chips,
                mult,
                roundView,
                hand(run),
                display(run.getJokers()),
                inventory(run),
                blindOptions(match),
                inShop,
                shopView,
                hasChosen,
                lastResult,
                table,
                opponents);
    }

    /** The seat's hand as structured cards, in model order (index i here is index i in the round's hand). */
    private static List<HandCardView> hand(Run run) {
        List<HandCardView> out = new ArrayList<>();
        for (DeckCard c : run.getHeld())
            out.add(new HandCardView(c.id(), describe(c), c.getRank().ordinal(), c.getSuit().ordinal()));
        return out;
    }

    /** The ante's three blinds for the selection screen; empty unless the match is in SELECTION. */
    private static List<BlindOption> blindOptions(Match match) {
        if (match.getPhase() != MatchPhase.SELECTION) return List.of();
        List<BlindOption> out = new ArrayList<>();
        BossBlind anteBoss = match.getAnteBoss();
        for (Blind b : Blind.values()) {
            long tgt = BlindTargets.target(match.getAnte(), b);
            String bossName = null, bossEffect = null;
            if (b == Blind.BOSS && anteBoss != null) {
                tgt = tgt * anteBoss.targetMultiplier();
                bossName = anteBoss.displayName();
                bossEffect = anteBoss.effect();
            }
            boolean current = b == match.getBlind();
            String tag = current ? String.valueOf(match.getCurrentTag()) : null;
            out.add(new BlindOption(blindType(b), tgt, b.getReward(), bossName, bossEffect, current, tag));
        }
        return out;
    }

    private static String blindType(Blind b) {
        return switch (b) {
            case SMALL -> "Small Blind";
            case BIG   -> "Big Blind";
            case BOSS  -> "Boss Blind";
        };
    }

    private static ShopView buildShop(Shop shop, Run run) {
        // Bought cards/packs and redeemed vouchers leave a null slot; we keep it as a null entry so tile indices
        // stay aligned with the model's slot indices (the client renders nulls as spent, unbuyable tiles).
        List<ShopItem> slots = new ArrayList<>();
        for (int i = 0; i < shop.getSlotCount(); i++) {
            Card c = shop.getSlot(i);
            if (c == null) { slots.add(null); continue; }
            int price = shop.slotPrice(i);
            slots.add(new ShopItem(nameOf(c), price, tooltip(nameOf(c), descriptionOf(c), categoryOf(c), price)));
        }
        List<ShopItem> packs = new ArrayList<>();
        for (int i = 0; i < shop.getPackCount(); i++) {
            BoosterPack p = shop.getPack(i);
            if (p == null) { packs.add(null); continue; }
            int price = shop.packPrice(i);
            String label = String.valueOf(p);
            packs.add(new ShopItem(label, price, tooltip(label, "", "Booster Pack", price)));
        }
        List<VoucherItem> vouchers = new ArrayList<>();
        for (int i = 0; i < shop.getVoucherCount(); i++) {
            Voucher v = shop.getVoucher(i);
            if (v == null) { vouchers.add(null); continue; }
            int price = v.getSpec().getCost();
            String label = v.getSpec().getName();
            vouchers.add(new VoucherItem(label, price, tooltip(label, v.getSpec().getDescription(), "Voucher", price), run.canRedeem(v)));
        }
        return new ShopView(slots, packs, vouchers, shop.rerollCost(), shop.purchasesRemaining());
    }

    /** Hover text: name, the authored effect description when present, then the category and cost line. */
    private static String tooltip(String name, String description, String category, int price) {
        StringBuilder sb = new StringBuilder(name);
        if (description != null && !description.isEmpty()) sb.append('\n').append(description);
        return sb.append('\n').append(category).append(" · $").append(price).toString();
    }

    /** The purchasable card's display name (spec name for jokers/consumables/relics; toString otherwise). */
    private static String nameOf(Card c) {
        if (c instanceof JokerCard j)         return j.getSpec().getName();
        if (c instanceof ConsumableCard con)  return con.getSpec().getName();
        if (c instanceof RelicCard rel)       return rel.getSpec().getName();
        return String.valueOf(c);
    }

    /** The purchasable card's authored effect description, or empty if none has been written yet. */
    private static String descriptionOf(Card c) {
        if (c instanceof JokerCard j)         return j.getSpec().getDescription();
        if (c instanceof ConsumableCard con)  return con.getSpec().getDescription();
        if (c instanceof RelicCard rel)       return rel.getSpec().getDescription();
        return "";
    }

    /** A short category label for a shop card, for the hover text. */
    private static String categoryOf(Card c) {
        if (c instanceof JokerCard j)         return "Joker · " + j.getSpec().getRarity();
        if (c instanceof ConsumableCard con)  return String.valueOf(con.getSpec().getType());
        if (c instanceof RelicCard rel)       return "Relic · " + rel.getSpec().getKind();
        return "Card";
    }

    /**
     * The seat's held items as one indexed area: consumables first, then relics, each tagged with its own model
     * index so a gesture can route to {@code useConsumable}/{@code sellConsumable} or {@code useRelic}/{@code
     * sellRelic}. Relics carry the demands the client needs to prompt for (who they hit, what choice they need).
     */
    private static List<ItemView> inventory(Run run) {
        List<ItemView> out = new ArrayList<>();
        List<ConsumableCard> consumables = run.getConsumables();
        for (int i = 0; i < consumables.size(); i++)
            out.add(new ItemView(describe(consumables.get(i)), false, i, null, "NONE", false));
        List<RelicCard> relics = run.getRelics();
        for (int i = 0; i < relics.size(); i++) {
            var spec = relics.get(i).getSpec();
            boolean needsSeat = switch (spec.getKind()) {
                case OPPONENT, RIVAL -> true;      // aimed by hand
                case RIVALS, SELF, GLOBAL -> false; // the standings (or nothing) decide
            };
            out.add(new ItemView(spec.getName(), true, i, spec.getKind().name(), spec.getSelector().name(), needsSeat));
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
