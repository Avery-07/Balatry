package model.client;

import client.MatchSnapshot;
import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.game.Match;
import model.game.MatchConfig;
import model.game.MatchPhase;
import model.game.player.PlayerId;
import model.game.player.Run;
import model.game.scoring.HandType;
import model.game.shop.Shop;

import java.util.ArrayList;
import java.util.List;

/**
 * Harness for {@link MatchSnapshot} — the model→view projection the client renders. It drives a real match
 * through each phase and asserts the snapshot's shape, guarding the layer the model harnesses never touch. The
 * shop case in particular replays the purchase that once threw {@code slot is empty}: after a buy, the sold slot
 * must project as a spent (null) entry rather than crash {@code buildShop}.
 */
public final class SnapshotTests {

    private static int failures = 0;

    public static void main(String[] args) {
        selectionSnapshot();
        blindSnapshot();
        hudDataSnapshot();
        shopPurchaseSnapshot();
        packOpeningSnapshot();
        jokerBadgeSnapshot();
        readinessSnapshot();
        jokerStateSnapshot();
        editionLabelSnapshot();
        stickerLabelSnapshot();
        deckAndLevelsSnapshot();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** SELECTION: all three blinds are shown, the boss row carries its name+effect, only the current is actionable. */
    private static void selectionSnapshot() {
        Match m = Match.create(7L, List.of("A", "B"), MatchConfig.defaults().withBlindSelection(true));
        m.start();
        MatchSnapshot s = MatchSnapshot.of(m, m.getSeats().get(0));

        check("phase SELECTION", s.phase() == MatchPhase.SELECTION);
        checkInt("three blinds shown", s.blinds().size(), 3);
        MatchSnapshot.BlindOption small = s.blinds().get(0), big = s.blinds().get(1), boss = s.blinds().get(2);
        check("small is the current blind", small.current());
        check("big is not current", !big.current());
        check("boss is not current", !boss.current());
        check("only the current blind carries a skip tag",
                small.skipTag() != null && big.skipTag() == null && boss.skipTag() == null);
        check("boss row names its boss and effect", boss.bossName() != null && boss.bossEffect() != null);
        check("non-boss rows carry no boss", small.bossName() == null && big.bossName() == null);
        checkLong("small reward $3", small.reward(), 3);
        checkLong("big reward $4", big.reward(), 4);
        checkLong("boss reward $5", boss.reward(), 5);
        checkLong("small target 300 at ante 1", small.target(), 300);
        checkInt("round number 1 at ante 1 small", s.roundNumber(), 1);
        check("hud shows base hands outside a round", s.hands() > 0);
        checkLong("hud money is the $4 opener", s.money(), Match.STARTING_MONEY);
    }

    /** BLIND: the hand projects as structured cards, and the HUD counts mirror the live round. */
    private static void blindSnapshot() {
        Match m = Match.create(8L, List.of("A", "B"));   // no blind selection: deals straight into BLIND
        m.start();
        MatchSnapshot s = MatchSnapshot.of(m, m.getSeats().get(0));

        check("phase BLIND", s.phase() == MatchPhase.BLIND);
        check("a hand was dealt", !s.hand().isEmpty());
        for (MatchSnapshot.HandCardView c : s.hand()) {
            check("rank ordinal in range", c.rank() >= 0 && c.rank() < Rank.values().length);
            check("suit ordinal in range", c.suit() >= 0 && c.suit() < Suit.values().length);
            check("card carries a label", c.label() != null && !c.label().isEmpty());
        }
        check("round view present during a blind", s.round() != null);
        checkInt("hud hands mirror the round", s.hands(), s.round().handsRemaining());
        checkInt("hud discards mirror the round", s.discards(), s.round().discardsRemaining());
    }

    /** HUD data (Phase 2): slot counters, the deck pile, and the chips×mult readout going non-zero after a play. */
    private static void hudDataSnapshot() {
        Match m = Match.create(21L, List.of("A", "B"));   // standard 52-card deck, straight into BLIND
        PlayerId a = m.getSeats().get(0);
        m.start();

        MatchSnapshot pre = MatchSnapshot.of(m, a);
        checkInt("deck total is the standard 52", pre.deckTotal(), 52);
        checkInt("deck pile is 44 after the deal", pre.deckRemaining(), 44);
        checkInt("no joker slots used yet", pre.jokerSlotsUsed(), 0);
        checkInt("joker slot capacity is 5", pre.jokerSlotsMax(), 5);
        checkInt("no consumable slots used yet", pre.consumableSlotsUsed(), 0);
        checkInt("consumable capacity is 2", pre.consumableSlotsMax(), 2);
        check("chips×mult reads 0 before any hand", pre.chips().equals("0") && pre.mult().equals("0"));

        Run run = m.getRun(a);
        int n = Math.min(5, run.getRound().getHand().size());
        run.getRound().play(new ArrayList<>(run.getRound().getHand().subList(0, n)));

        MatchSnapshot post = MatchSnapshot.of(m, a);
        check("chips are non-zero after a play", !post.chips().equals("0"));
        check("mult is non-zero after a play", !post.mult().equals("0"));
        check("deck pile shrank as cards were redrawn", post.deckRemaining() < pre.deckRemaining());
    }

    /** SHOP: the offer projects, and a purchase leaves a spent (null) slot without crashing the projection. */
    private static void shopPurchaseSnapshot() {
        Match m = Match.create(9L, List.of("A", "B"));
        PlayerId a = m.getSeats().get(0);
        for (PlayerId id : m.getSeats()) stackToWin(m.getRun(id));
        m.start();
        for (PlayerId id : m.getSeats()) {                 // clear the blind on both seats, then settle to the shop
            Run run = m.getRun(id);
            int n = Math.min(5, run.getRound().getHand().size());
            run.getRound().play(new ArrayList<>(run.getRound().getHand().subList(0, n)));
            run.getRound().finish();
        }
        m.toShop();
        check("phase SHOP", m.getPhase() == MatchPhase.SHOP);

        Run run = m.getRun(a);
        run.addMoney(50);   // afford whatever the first acquirable slot costs
        MatchSnapshot before = MatchSnapshot.of(m, a);
        check("shop present in the snapshot", before.shop() != null);
        check("slots offered", !before.shop().slots().isEmpty());

        Shop shop = run.getShop();
        int bought = -1;
        for (int i = 0; i < shop.getSlotCount(); i++)
            if (shop.getSlot(i) != null && run.canAcquire(shop.getSlot(i))) { shop.buy(i); bought = i; break; }
        check("a slot was bought", bought >= 0);

        MatchSnapshot after = MatchSnapshot.of(m, a);       // must not throw on the emptied slot
        check("bought slot now reads spent (null)", after.shop().slots().get(bought) == null);
        int offered = 0;
        for (MatchSnapshot.ShopItem it : after.shop().slots()) if (it != null) offered++;
        checkInt("exactly one slot became spent", offered, before.shop().slots().size() - 1);
    }

    /** PACK OPENING: buying a pack projects the opening (name, budget, options); spending the budget clears it. */
    private static void packOpeningSnapshot() {
        Match m = Match.create(31L, List.of("A", "B"));
        PlayerId a = m.getSeats().get(0);
        for (PlayerId id : m.getSeats()) stackToWin(m.getRun(id));
        m.start();
        for (PlayerId id : m.getSeats()) {
            Run run = m.getRun(id);
            int n = Math.min(5, run.getRound().getHand().size());
            run.getRound().play(new ArrayList<>(run.getRound().getHand().subList(0, n)));
            run.getRound().finish();
        }
        m.toShop();

        Run run = m.getRun(a);
        run.addMoney(50);
        var shop = run.getShop();
        int packIdx = -1;
        for (int i = 0; i < shop.getPackCount(); i++) if (shop.getPack(i) != null) { packIdx = i; break; }
        check("the shop offers a pack", packIdx >= 0);

        // Buy + open, mirroring the model's BuyPack action.
        run.grantPack(shop.buyPack(packIdx));
        run.beginOpening(run.openPendingPack(run.getPendingPacks().size() - 1));

        MatchSnapshot s = MatchSnapshot.of(m, a);
        check("the snapshot exposes the open pack", s.opening() != null);
        check("the pack names itself", s.opening() != null && !s.opening().packName().isEmpty());
        check("the pack has a positive pick budget", s.opening() != null && s.opening().picksLeft() >= 1);
        check("the pack offers options", s.opening() != null && !s.opening().options().isEmpty());

        // Spending the whole budget clears the opening.
        var op = run.getCurrentOpening();
        while (op.getPicksLeft() > 0) {
            int idx = -1;
            for (int i = 0; i < op.getOptions().size(); i++) if (op.getOptions().get(i) != null) { idx = i; break; }
            if (idx < 0) break;
            op.pick(idx);
        }
        if (op.getPicksLeft() == 0) run.clearOpening();
        check("the opening clears once its budget is spent", MatchSnapshot.of(m, a).opening() == null);
    }

    /** Eight cycling-suit Aces and every hand type leveled far up: one hand clears any early blind. */
    /** The top-bar joker views: name, the edition/sticker badge (Sticky shows its live toll), and the grey-out. */
    private static void jokerBadgeSnapshot() {
        Match m = Match.create(33L, List.of("A", "B"));
        PlayerId a = m.getSeats().get(0);
        Run run = m.getRun(a);

        var plain = model.items.jokers.Jokers.JOKER.make();
        var laden = model.items.jokers.Jokers.GREEDY_JOKER.make();
        laden.apply(model.modifiers.Edition.FOIL);
        laden.apply(model.modifiers.Sticker.STICKY);
        laden.tickStickers();   // the toll grows a round, so the badge must show the *current* cost
        var dead = model.items.jokers.Jokers.LUSTY_JOKER.make();
        dead.apply(model.modifiers.Sticker.DEBUFFED);
        run.acquire(plain);
        run.acquire(laden);
        run.acquire(dead);
        m.start();

        MatchSnapshot s = MatchSnapshot.of(m, a);
        checkInt("all three jokers project", s.jokers().size(), 3);
        check("a plain joker has no badge", s.jokers().get(0).badge().isEmpty());
        check("the badge names the edition", s.jokers().get(1).badge().contains("Foil"));
        check("the badge shows Sticky's live toll", s.jokers().get(1).badge().contains("Sticky $4"));
        check("a live joker is not greyed", !s.jokers().get(0).debuffed());
        check("a debuffed joker is greyed", s.jokers().get(2).debuffed());
    }

    /**
     * The lockstep barrier a player can see: once a seat signals ready to leave RESULT/SHOP, the snapshot says
     * so — that is what lets the button become "waiting for others" instead of appearing dead.
     */
    private static void readinessSnapshot() {
        var host = model.game.host.MatchHost.create(77L, List.of("A", "B"));
        host.start();
        Match m = host.getMatch();
        PlayerId a = m.getSeats().get(0), b = m.getSeats().get(1);

        // Drive both seats to the result screen.
        int guard = 0;
        while (m.getPhase() != MatchPhase.RESULT && guard++ < 50) {
            if (m.getPhase() == MatchPhase.SELECTION) {
                for (PlayerId id : m.getSeats()) host.submit(new model.game.actions.Action.PlayBlind(id));
            } else if (m.getPhase() == MatchPhase.BLIND) {
                for (PlayerId id : m.getSeats()) host.submit(new model.game.actions.Action.FinishRound(id));
            }
        }
        check("the table reached the result screen", m.getPhase() == MatchPhase.RESULT);

        MatchSnapshot before = MatchSnapshot.of(m, a);
        check("nobody is ready yet", !before.isReady());
        checkInt("and the tally agrees", before.readyCount(), 0);
        checkInt("both seats are active", before.activeSeats(), 2);

        host.submit(new model.game.actions.Action.ReadyForNext(a));
        MatchSnapshot mine = MatchSnapshot.of(m, a);
        check("my signal is visible to me", mine.isReady());
        checkInt("the tally counts it", mine.readyCount(), 1);
        check("the phase has not moved — we wait on the other seat", m.getPhase() == MatchPhase.RESULT);

        MatchSnapshot theirs = MatchSnapshot.of(m, b);
        check("the other seat is not ready", !theirs.isReady());
        checkInt("but sees the same tally", theirs.readyCount(), 1);

        host.submit(new model.game.actions.Action.ReadyForNext(b));
        check("the barrier crosses once everyone signals", m.getPhase() != MatchPhase.RESULT);
        check("and readiness resets behind it", !MatchSnapshot.of(m, a).isReady());
    }

    /** The joker's live variable reaches the tooltip: named picks, accumulated bonuses, and the raw fallback. */
    private static void jokerStateSnapshot() {
        Match m = Match.create(55L, List.of("A", "B"));
        PlayerId a = m.getSeats().get(0);
        Run run = m.getRun(a);

        // A hidden pick renders as words the player can act on (Mail-In Rebate's counter is a Rank ordinal).
        var rebate = model.items.jokers.Jokers.MAIL_IN_REBATE.make();
        // An accumulator renders as the bonus it is worth (Green Joker's counter is +Mult).
        var green = model.items.jokers.Jokers.GREEN_JOKER.make();
        run.acquire(rebate);
        run.acquire(green);
        m.start();
        // Set after start: the round's ON_ROUND_START re-rolls Rebate's pick, and the test needs a known one.
        rebate.setCounter(model.items.DeckCard.Rank.KING.ordinal());
        green.setCounter(7);

        MatchSnapshot s = MatchSnapshot.of(m, a);
        check("a hidden pick names itself", s.jokers().get(0).state().contains("King"));
        check("an accumulator shows its bonus", s.jokers().get(1).state().contains("+7 Mult"));

        // A spec with no state renderer: non-zero counters fall back to a bare value, zero shows nothing.
        var anon = model.items.jokers.JokerSpec.named("Anon", model.items.jokers.Rarity.COMMON).build();
        var card = new model.items.jokers.JokerCard(anon);
        check("no counter, no state", anon.stateOf(card) == null);
        card.setCounter(3);
        check("an unnamed counter still surfaces", "Value: 3".equals(anon.stateOf(card)));
    }

    /** An edition on a hand card reaches its label, where the client's tooltip reads it. */
    private static void editionLabelSnapshot() {
        Match m = Match.create(66L, List.of("A", "B"));
        PlayerId a = m.getSeats().get(0);
        m.start();
        Run run = m.getRun(a);
        run.getRound().getHand().get(0).apply(model.modifiers.Edition.FOIL);

        MatchSnapshot s = MatchSnapshot.of(m, a);
        check("the edition reaches the hand label", s.hand().get(0).label().contains("<FOIL>"));
        check("plain cards stay plain", !s.hand().get(1).label().contains("<"));
    }

    /** A sticker on a playing card reaches its hand label too (so the hover tooltip can show it, like jokers/items). */
    private static void stickerLabelSnapshot() {
        Match m = Match.create(67L, List.of("A", "B"));
        PlayerId a = m.getSeats().get(0);
        m.start();
        Run run = m.getRun(a);
        run.getRound().getHand().get(0).apply(model.modifiers.Sticker.ETERNAL);

        MatchSnapshot s = MatchSnapshot.of(m, a);
        check("the sticker reaches the hand label", s.hand().get(0).label().contains("(Eternal)"));
        check("cards without stickers carry none", !s.hand().get(1).label().contains("("));
    }

    /** The deck-hover view and the play-preview table: spent cards grey out, hand levels price a play. */
    private static void deckAndLevelsSnapshot() {
        Match m = Match.create(44L, List.of("A", "B"));
        PlayerId a = m.getSeats().get(0);
        m.start();   // straight into a round: 8 dealt, 44 in the pile

        Run run = m.getRun(a);
        MatchSnapshot fresh = MatchSnapshot.of(m, a);
        checkInt("the whole deck projects", fresh.deckCards().size(), 52);
        // Live means "still drawable": the 8 dealt cards are in hand, not in the pile, so they grey immediately.
        long liveFresh = fresh.deckCards().stream().filter(MatchSnapshot.DeckCardView::live).count();
        checkInt("only the draw pile is live", (int) liveFresh, run.getRound().getDrawPile().size());

        int handBefore = run.getRound().getHand().size();
        int played = Math.min(5, handBefore);
        run.getRound().play(new ArrayList<>(run.getRound().getHand().subList(0, played)));

        MatchSnapshot after = MatchSnapshot.of(m, a);
        long liveAfter = after.deckCards().stream().filter(MatchSnapshot.DeckCardView::live).count();
        checkInt("the live set tracks the shrinking pile", (int) liveAfter, run.getRound().getDrawPile().size());
        check("playing consumed draws", liveAfter < liveFresh);

        // Destroy a card outright: the deck view must lose the entry entirely, not grey it.
        int deckBefore = after.deckCards().size();
        run.destroyDeckCards(List.of(run.getDeck().get(0)));
        checkInt("a destroyed card vanishes from the view",
                MatchSnapshot.of(m, a).deckCards().size(), deckBefore - 1);

        checkInt("every hand type is priced", after.handLevels().size(), HandType.values().length);
        MatchSnapshot.HandLevelView pair = after.handLevels().stream()
                .filter(h -> h.type().equals("PAIR")).findFirst().orElseThrow();
        check("PAIR at level 1 prices 10 x 2", pair.level() == 1 && pair.chips() == 10 && pair.mult() == 2);

        // The pack shelf's label is String.valueOf(pack), which used to print an object hash — the display name
        // is what the snapshot (and the pack-opening header) actually ships.
        var pack = new model.items.packs.BoosterPack(
                model.items.packs.PackKind.ARCANA, model.items.packs.PackSize.MEGA);
        check("packs name themselves", String.valueOf(pack).equals("Mega Arcana Pack"));
        var plain = new model.items.packs.BoosterPack(
                model.items.packs.PackKind.CELESTIAL, model.items.packs.PackSize.NORMAL);
        check("a normal pack has no size prefix", String.valueOf(plain).equals("Celestial Pack"));
    }

    private static void stackToWin(Run run) {
        run.resetDeck(new ArrayList<>());
        Suit[] suits = Suit.values();
        for (int i = 0; i < 8; i++) run.addCardToDeck(new DeckCard(Rank.ACE, suits[i % suits.length]));
        for (HandType type : HandType.values())
            for (int i = 0; i < 100; i++) run.getHandLevels().levelUp(type);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) { check(label + " (" + actual + ")", actual == expected); }
    private static void checkLong(String label, long actual, long expected) { check(label + " (" + actual + ")", actual == expected); }
}
