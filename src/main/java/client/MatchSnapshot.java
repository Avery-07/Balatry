package client;

import model.items.Card;
import model.items.DeckCard;
import model.items.jokers.JokerCard;
import model.items.packs.BoosterPack;
import model.items.packs.PackOpening;
import model.items.vouchers.Voucher;
import model.game.Blind;
import model.game.BlindTargets;
import model.game.BossBlind;
import model.game.Match;
import model.game.MatchPhase;
import model.game.Stake;
import model.game.Standings;
import model.game.player.BlindResult;
import model.items.consumables.ConsumableCard;
import model.items.relics.RelicCard;
import model.game.net.MatchClient;
import model.game.player.PlayerId;
import model.game.player.Round;
import model.game.player.RoundOutcome;
import model.game.scoring.HandType;
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
        String deckType,           // the table's shared starting deck (display name)
        String sleeve,             // this seat's sleeve (display name); per-seat, opponents' are not shown
        String stake,              // this seat's difficulty (display name); per-seat, so targets below are its own
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
        List<DeckCardView> deckCards,   // the whole deck, spent cards flagged — the deck-pile hover view
        List<HandLevelView> handLevels, // every hand type at this seat's current level, for the play preview
        EvalFlags evalFlags,            // the hand-shaping joker traits, so the client's preview classifies like the model
        List<VoucherView> vouchers,     // vouchers this seat has redeemed this run, for the Run Info overlay
        List<ScoreEventView> lastPlay,  // the last play's scoring timeline, for the scoring animation
        List<JokerView> jokers,
        List<ItemView> inventory,  // consumables and relics as one indexed area; relics carry their casting demands
        List<BlindOption> blinds,  // the ante's three blinds, for the selection screen (empty outside SELECTION)
        boolean inShop,
        ShopView shop,             // null outside the shop phase
        PackOpeningView opening,   // a booster pack being picked from, or null
        List<PendingPackView> pendingPacks,   // granted, unopened packs (skip tags, Wrath) awaiting an Open at the barrier
        boolean hasChosen,         // this seat has made its blind-selection choice
        boolean isReady,           // this seat has signalled ready to leave RESULT/SHOP — waiting on the others
        int readyCount,            // how many still-playing seats have signalled ready
        int activeSeats,           // how many seats are still playing (the barrier's denominator)
        int blindDoneCount,        // still-playing seats whose round has resolved (the blind barrier's progress tally)
        ResultView lastResult,     // this seat's most recent blind outcome (null before the first)
        List<StandingView> standings,   // every seat, ranked; for the end-of-match summary
        List<OpponentView> opponents
) {

    /**
     * Round-scoped state, present whenever a round exists (including one already resolved while the table waits at
     * the blind barrier). {@code outcome} is a {@code RoundOutcome} name — IN_PROGRESS, WON, LOST or SKIPPED — so
     * the client can tell "still playing" from "done, waiting on others" and a skip from a played-out blind.
     */
    public record RoundView(int handsRemaining, int discardsRemaining, String score, long roundTarget,
                            boolean canSkip, String outcome) {
        /** Whether this round is finished (the seat is waiting at the blind barrier rather than still playing). */
        public boolean done()    { return !"IN_PROGRESS".equals(outcome); }
        /** Whether this round was skipped for its tag (as opposed to played to a win or loss). */
        public boolean skipped() { return "SKIPPED".equals(outcome); }
    }

    /** The only opponent state that crosses the information boundary: identity, points, ranking. */
    public record OpponentView(int seat, String name, long points, int rank) { }

    /**
     * One card in hand: a stable {@code id} (for cross-frame animation) plus rank/suit ordinals for sorting.
     * A card the boss dealt {@code faceDown} is masked at the boundary — rank and suit are {@code -1} and the
     * label is blank — so no tooltip, sort or preview can leak what its owner is not allowed to see.
     */
    public record HandCardView(int id, String label, int rank, int suit, int enhancement, int seal, boolean faceDown) { }

    /**
     * One card of the full deck, for the deck-pile hover: rank/suit ordinals and whether it is still {@code live}
     * (in the draw pile or the hand). During a round a spent card greys out; outside a round everything is live.
     */
    public record DeckCardView(int rank, int suit, int enhancement, int seal, boolean live) { }

    /**
     * One poker hand at this seat's current level — what a play of that type is worth right now, plus {@code plays}:
     * how many times this seat has played it this run (the Run Info "×N" usage column).
     */
    public record HandLevelView(String type, int level, long chips, long mult, int plays) { }

    /** The hand-shaping joker traits owned by this seat, so the client's play preview classifies a selection exactly as the model would. */
    public record EvalFlags(boolean fourFingers, boolean shortcut, boolean smeared, boolean splash, boolean dyscalculia) { }

    /** One voucher this seat has redeemed this run, as the Run Info list shows it: name and effect text. */
    public record VoucherView(String name, String description) { }

    /**
     * One beat of the last play's scoring, in the order the engine performed it — the client replays these as
     * the trigger/effect animation: the source pops, a small square shows what it did, and the readouts count to
     * {@code chipsAfter}/{@code multAfter}. Those running totals come straight from the model, so the animation
     * cannot drift from the score that was actually banked.
     *
     * <p>{@code sourceId} matches a {@link HandCardView#id()} or {@link JokerView#id()}; it is {@code -1} for
     * beats no card owns (the hand's base value, the Plasma balance). {@code kind} is a
     * {@code ScoringEvent.Kind} name: BASE, CHIPS, MULT, XMULT, MONEY, RETRIGGER, DESTROYED, BALANCE.
     */
    public record ScoreEventView(int sourceId, String sourceName, String kind,
                                 String amount, String chipsAfter, String multAfter) { }

    /**
     * One held joker as the top bar shows it: its name, the authored effect text (for the hover tooltip), a short
     * {@code badge} naming its edition and stickers ("Foil · Sticky $4", empty when it has neither), and whether
     * it is currently doing nothing. The badge exists because stickers are drawbacks the player agreed to — they
     * have to stay visible after the purchase, not just in the shop.
     */
    public record JokerView(int id, String name, String description, String state, String badge, boolean debuffed) { }

    /**
     * One of the ante's three blinds, as the selection screen shows it: its type, chip target and cash reward,
     * and — for the boss — its name and effect (locked at ante start, so it is known even on the small blind).
     * {@code current} marks the blind actually being selected; {@code skipTag} is the display name of the tag
     * skipping it would grant (null on the others, since only the current blind can be skipped) and {@code
     * skipTagDesc} is that tag's effect text, for the Skip button's hover tooltip.
     */
    public record BlindOption(String type, long target, int reward,
                              String bossName, String bossEffect,
                              boolean current, String skipTag, String skipTagDesc) { }

    /**
     * One held inventory item — a consumable or a relic — as the client sees it. Relics and consumables share a
     * single indexed area, so {@code isRelic} plus {@code modelIndex} (the item's position within its own model
     * list) is how a gesture routes to the right verb. The casting fields are meaningful only for relics: {@code
     * kind} names who it lands on, {@code selector} what choice it needs ("NONE" for consumables), and {@code
     * needsSeat} is true only for relics the caster aims by hand.
     */
    public record ItemView(int id, String label, String description, String badge, boolean isRelic, int modelIndex,
                           String kind, String selector, boolean needsSeat, int minTargets) { }

    /** One seat's line in the final standings; {@code isMe} marks the local seat, {@code departed} a player who left. */
    public record StandingView(int seat, String name, long points, int rank, boolean isMe, boolean departed) { }

    /** The local seat's own standings line — the result/finish screens all want exactly this row. */
    public StandingView myStanding() {
        for (StandingView v : standings) if (v.isMe()) return v;
        return new StandingView(seat, name, 0, 0, true, false);   // unreachable in a real match; safe default
    }

    /** This seat's most recent blind outcome, for the result summary. */
    public record ResultView(String outcome, String score, long target, String bestHand,
                             int handsRemaining, int moneyEarned) { }

    /**
     * One purchasable shop card/pack: its name, price, the hover text (name, category, cost), and a short
     * {@code badge} naming its edition and stickers — empty for a plain card, but a Sticky or Perishable roll is
     * a real drawback, so the buyer must see it on the tile <em>before</em> paying.
     */
    public record ShopItem(int id, String label, int price, String tooltip, String badge) { }

    /** One offered voucher: name, price, hover text, and whether it can be redeemed now (one per ante). */
    public record VoucherItem(int id, String label, int price, String tooltip, boolean redeemable) { }

    /** A booster pack being opened: its name, the remaining pick budget, and each option's label (null = taken). */
    public record PackOpeningView(String packName, int picksLeft, List<PackOption> options) { }

    /** A granted, unopened booster pack (a skip tag's free pack, or Wrath's) the seat opens at the blind barrier; {@code index} into the run's pending list. */
    public record PendingPackView(String label, int index) { }

    /**
     * One offered option in an open pack, everything the client needs to pick it (every pick is used at once):
     * its display {@code label} (null once taken); {@code minTargets}, how many hand cards a targeted consumable
     * needs; and for a relic, {@code isRelic} plus its casting demands ({@code selector} — what it aims at, like a
     * held relic's, and {@code needsSeat}) so the pick can derive the same target from the selection. {@code
     * description} is the card's authored effect text, for the hover tooltip; {@code id} is the card's stable id,
     * so the client can ride each option on the same retained, draggable row the shop tiles use ({@code -1} once taken).
     */
    public record PackOption(int id, String label, String description, int minTargets, boolean isRelic, String selector, boolean needsSeat) { }

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
                                r.getOutcome() == RoundOutcome.IN_PROGRESS && !r.isActed(),
                                String.valueOf(r.getOutcome()));

        // The blind barrier's tally: how many still-playing seats have finished their round (won, lost or skipped),
        // so a seat waiting at the barrier can see progress — the analog of readyCount for the RESULT/SHOP barriers.
        int blindDoneCount = 0;
        if (match.getPhase() == MatchPhase.BLIND)
            for (PlayerId id : match.getActiveSeats()) {
                Round rr = match.getRun(id).getRound();
                if (rr != null && rr.getOutcome() != RoundOutcome.IN_PROGRESS) blindDoneCount++;
            }

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
                    id.seat() == me.seat(),
                    match.hasDeparted(id)));

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
                match.getCurrentTarget(me),   // this seat's own stake-scaled target
                String.valueOf(match.getActiveSin()),
                match.getCurrentBoss() == null ? null : String.valueOf(match.getCurrentBoss()),
                String.valueOf(match.getCurrentTag()),
                match.getDeckType().displayName(),
                run.getSleeve().displayName(),
                run.getStake().displayName(),
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
                deckCards(run),
                handLevels(run),
                new EvalFlags(
                        run.hasActiveTrait(model.items.jokers.JokerTrait.FOUR_FINGERS),
                        run.hasActiveTrait(model.items.jokers.JokerTrait.SHORTCUT),
                        run.hasActiveTrait(model.items.jokers.JokerTrait.SMEARED),
                        run.hasActiveTrait(model.items.jokers.JokerTrait.SPLASH),
                        run.hasActiveTrait(model.items.jokers.JokerTrait.DYSCALCULIA)),
                vouchers(run),
                scoreEvents(run),
                jokerViews(run),
                inventory(run),
                blindOptions(match, run.getStake()),
                inShop,
                shopView,
                packOpening(run),
                pendingPacks(run),
                hasChosen,
                match.isReady(me),
                match.readyCount(),
                match.getActiveSeats().size(),
                blindDoneCount,
                lastResult,
                table,
                opponents);
    }

    /**
     * The seat's hand as structured cards, in model order. During a blind this is the live hand; while a pack is
     * open outside a round it is the temporary hand dealt for aiming a targeted pick — so the client renders and
     * selects from it the same way (index i here is index i in {@link Run#getSelectionHand()}).
     */
    private static List<HandCardView> hand(Run run) {
        Round round = run.getRound();
        List<HandCardView> out = new ArrayList<>();
        for (DeckCard c : run.getSelectionHand()) {
            if (round != null && round.isFaceDown(c))
                out.add(new HandCardView(c.id(), "", -1, -1, -1, -1, true));
            else
                out.add(new HandCardView(c.id(), describe(c), c.getRank().ordinal(), c.getSuit().ordinal(),
                        c.getEnhancement() == null ? -1 : c.getEnhancement().ordinal(),
                        c.getSeal() == null ? -1 : c.getSeal().ordinal(), false));
        }
        return out;
    }

    /**
     * The whole deck for the pile's hover view — the deck as it exists <em>now</em>, so destroyed cards are
     * simply absent and created ones appear. During a round a card is {@code live} only while it is still in the
     * draw pile; a card in hand, played, or discarded greys out (the pile view answers "what can I still draw",
     * and a held card cannot be drawn). Outside a round the next deal could bring anything, so everything is live.
     */
    private static List<DeckCardView> deckCards(Run run) {
        Round r = run.getRound();
        java.util.Set<Integer> liveIds = null;
        if (r != null) {
            liveIds = new java.util.HashSet<>();
            for (DeckCard c : r.getDrawPile()) liveIds.add(c.id());
        }
        List<DeckCardView> out = new ArrayList<>();
        for (DeckCard c : run.getDeck())
            out.add(new DeckCardView(c.getRank().ordinal(), c.getSuit().ordinal(),
                    c.getEnhancement() == null ? -1 : c.getEnhancement().ordinal(),
                    c.getSeal() == null ? -1 : c.getSeal().ordinal(),
                    liveIds == null || liveIds.contains(c.id())));
        return out;
    }

    /**
     * The last play's scoring timeline as display values. Empty outside a round and before the round's first
     * hand; the client keys a replay off it changing.
     */
    private static List<ScoreEventView> scoreEvents(Run run) {
        Round r = run.getRound();
        if (r == null) return List.of();
        List<ScoreEventView> out = new ArrayList<>();
        for (model.game.scoring.ScoringEvent e : r.getLastEvents())
            out.add(new ScoreEventView(e.sourceId(), e.sourceName(), e.kind().name(),
                    e.amount().stripTrailingZeros().toPlainString(),
                    e.chipsAfter().toBigInteger().toString(),
                    e.multAfter().stripTrailingZeros().toPlainString()));
        return out;
    }

    /** Every poker hand at this seat's current level — the play preview's pricing and Run Info's level/usage list. */
    private static List<HandLevelView> handLevels(Run run) {
        List<HandLevelView> out = new ArrayList<>();
        for (HandType t : HandType.values())
            out.add(new HandLevelView(t.name(), run.getHandLevels().levelOf(t),
                    run.getHandLevels().chipsFor(t), run.getHandLevels().multFor(t),
                    run.getStats().getHandPlays(t)));
        return out;
    }

    /** The vouchers this seat has redeemed this run, for the Run Info overlay. */
    private static List<VoucherView> vouchers(Run run) {
        List<VoucherView> out = new ArrayList<>();
        for (model.items.vouchers.VoucherSpec v : run.getStats().getRedeemedVouchers())
            out.add(new VoucherView(v.getName(), v.getDescription()));
        return out;
    }

    /**
     * The ante's three blinds for the selection screen; empty unless the match is in SELECTION. Targets are shown
     * at {@code stake} — the viewing seat's own — since stakes are per-seat and scale what it must score.
     */
    private static List<BlindOption> blindOptions(Match match, Stake stake) {
        // Shown while selecting, and kept through the blind so a seat that skipped can still see the tiles behind
        // its "waiting for others" popup. The current-blind marking and tag are unchanged across the transition.
        if (match.getPhase() != MatchPhase.SELECTION && match.getPhase() != MatchPhase.BLIND) return List.of();
        List<BlindOption> out = new ArrayList<>();
        BossBlind anteBoss = match.getAnteBoss();
        for (Blind b : Blind.values()) {
            long tgt = BlindTargets.target(match.getAnte(), b, stake);
            String bossName = null, bossEffect = null;
            if (b == Blind.BOSS && anteBoss != null) {
                tgt = tgt * anteBoss.targetMultiplier();
                bossName = anteBoss.displayName();
                bossEffect = anteBoss.effect();
            }
            boolean current = b == match.getBlind();
            model.game.tags.SkipTag skip = current ? match.getCurrentTag() : null;
            String tag = skip == null ? null : skip.getDisplayName();
            String tagDesc = skip == null ? null : skip.getDescription();
            out.add(new BlindOption(blindType(b), tgt, b.getReward(), bossName, bossEffect, current, tag, tagDesc));
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

    /** The pack the seat is currently picking from, or null. Taken options appear as null entries. */
    private static PackOpeningView packOpening(Run run) {
        PackOpening o = run.getCurrentOpening();
        if (o == null) return null;
        List<PackOption> options = new ArrayList<>();
        for (Card c : o.getOptions()) {
            if (c == null) { options.add(new PackOption(-1, null, "", 0, false, "NONE", false)); continue; }
            String label = c instanceof DeckCard d ? describe(d) : nameOf(c);
            String desc = descriptionOf(c);
            if (c instanceof model.items.relics.RelicCard rc) {
                var spec = rc.getSpec();
                boolean needsSeat = switch (spec.getKind()) {
                    case OPPONENT -> true;
                    case RANDOM_RIVAL, RIVALS, SELF, GLOBAL -> false;
                };
                options.add(new PackOption(c.id(), label, desc, 0, true, spec.getSelector().name(), needsSeat));
            } else {
                int minTargets = c instanceof model.items.consumables.ConsumableCard cc ? cc.getSpec().getMinTargets() : 0;
                options.add(new PackOption(c.id(), label, desc, minTargets, false, "NONE", false));
            }
        }
        return new PackOpeningView(String.valueOf(o.getPack()), o.getPicksLeft(), options);
    }

    /** Granted-but-unopened packs, in pending order, so the client can offer an Open at the blind barrier. */
    private static List<PendingPackView> pendingPacks(Run run) {
        var packs = run.getPendingPacks();
        List<PendingPackView> out = new ArrayList<>(packs.size());
        for (int i = 0; i < packs.size(); i++) out.add(new PendingPackView(String.valueOf(packs.get(i)), i));
        return out;
    }

    private static ShopView buildShop(Shop shop, Run run) {
        // Bought cards/packs and redeemed vouchers leave a null slot; we keep it as a null entry so tile indices
        // stay aligned with the model's slot indices (the client renders nulls as spent, unbuyable tiles).
        List<ShopItem> slots = new ArrayList<>();
        for (int i = 0; i < shop.getSlotCount(); i++) {
            Card c = shop.getSlot(i);
            if (c == null) { slots.add(null); continue; }
            int price = shop.slotPrice(i);
            String badge = badgeOf(c);
            slots.add(new ShopItem(c.id(), nameOf(c), price,
                    tooltip(nameOf(c), descriptionOf(c), categoryOf(c) + (badge.isEmpty() ? "" : " · " + badge), price),
                    badge));
        }
        List<ShopItem> packs = new ArrayList<>();
        for (int i = 0; i < shop.getPackCount(); i++) {
            BoosterPack p = shop.getPack(i);
            if (p == null) { packs.add(null); continue; }
            int price = shop.packPrice(i);
            String label = String.valueOf(p);
            packs.add(new ShopItem(p.id(), label, price, tooltip(label, "", "Booster Pack", price), ""));
        }
        List<VoucherItem> vouchers = new ArrayList<>();
        for (int i = 0; i < shop.getVoucherCount(); i++) {
            Voucher v = shop.getVoucher(i);
            if (v == null) { vouchers.add(null); continue; }
            int price = v.getSpec().getCost();
            String label = v.getSpec().getName();
            vouchers.add(new VoucherItem(v.id(), label, price, tooltip(label, v.getSpec().getDescription(), "Voucher", price), run.canRedeem(v)));
        }
        return new ShopView(slots, packs, vouchers, shop.rerollCost(), shop.purchasesRemaining());
    }

    /**
     * The visible drawbacks-and-shine summary for one card: its edition and each sticker, dot-separated —
     * "Foil · Sticky $4" — or empty for a plain card. Sticky shows its current sell toll since that is the
     * number the player is actually agreeing to.
     */
    private static String badgeOf(Card c) {
        List<String> parts = new ArrayList<>();
        if (c.getEdition() != null) parts.add(title(c.getEdition().name()));
        String stickers = stickerText(c);
        if (!stickers.isEmpty()) parts.add(stickers);
        return String.join(" · ", parts);
    }

    /** A card's applied stickers as display text ("Eternal · Sticky $5"), empty when it has none. Any card may carry them. */
    private static String stickerText(Card c) {
        List<String> parts = new ArrayList<>();
        for (var sticker : c.getStickers().keySet()) {
            if (sticker == model.modifiers.Sticker.STICKY) parts.add("Sticky $" + c.getStickySellCost());
            else parts.add(title(sticker.name()));
        }
        return String.join(" · ", parts);
    }

    /** {@code PERISHABLE} → {@code Perishable}; delegates to the client's one title-caser. */
    private static String title(String enumName) { return client.game.Fmt.title(enumName); }

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
        for (int i = 0; i < consumables.size(); i++) {
            var card = consumables.get(i);
            var spec = card.getSpec();
            out.add(new ItemView(card.id(), spec.getName(), spec.getDescription(), badgeOf(card), false, i, null, "NONE", false,
                    spec.getMinTargets()));
        }
        List<RelicCard> relics = run.getRelics();
        for (int i = 0; i < relics.size(); i++) {
            var card = relics.get(i);
            var spec = card.getSpec();
            boolean needsSeat = switch (spec.getKind()) {
                case OPPONENT -> true;             // a freely-chosen seat (no current relic uses this)
                case RANDOM_RIVAL, RIVALS, SELF, GLOBAL -> false; // random / standings-driven / no target
            };
            out.add(new ItemView(card.id(), spec.getName(), spec.getDescription(), badgeOf(card), true, i,
                    spec.getKind().name(), spec.getSelector().name(), needsSeat, 0));
        }
        return out;
    }

    /** The board as the top bar shows it: each joker's name, effect text, live state, badge, and liveness. */
    private static List<JokerView> jokerViews(Run run) {
        // The read-only view a joker's state descriptor reads its current effect from. Built once: the snapshot
        // runs on the FX thread over a settled model, so this is the safe moment to evaluate the descriptors.
        model.game.player.JokerInfo info = model.game.player.JokerInfo.of(run);
        List<JokerView> out = new ArrayList<>();
        for (JokerCard j : run.getJokers()) {
            String state = j.getSpec().stateOf(j, info);
            out.add(new JokerView(j.id(), j.getSpec().getName(), j.getSpec().getDescription(),
                    state == null ? "" : state, badgeOf(j), j.isDebuffed()));
        }
        return out;
    }

    /**
     * A readable label for one item. {@link DeckCard}s render as {@code RANK-SUIT} with any enhancement/seal/
     * edition appended ({@code [GOLD]{RED}<FOIL>}); everything else falls back to {@link String#valueOf}. Card
     * types other than {@code DeckCard} (jokers, consumables, relics) rely on their own {@code toString} for now
     * — worth tightening once we can see their real output in the debug view.
     */
    private static String describe(Object o) {
        if (o instanceof DeckCard c) {
            StringBuilder sb = new StringBuilder(c.getRank().name()).append('-').append(c.getSuit().name());
            if (c.getEnhancement() != null) sb.append('[').append(c.getEnhancement()).append(']');
            if (c.getSeal() != null) sb.append('{').append(c.getSeal()).append('}');
            if (c.getEdition() != null) sb.append('<').append(c.getEdition()).append('>');
            String stickers = stickerText(c);   // already display-formatted; Fmt.cardTip shows it verbatim
            if (!stickers.isEmpty()) sb.append('(').append(stickers).append(')');
            return sb.toString();
        }
        return String.valueOf(o);
    }
}
