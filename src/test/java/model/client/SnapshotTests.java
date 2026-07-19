package model.client;

import client.MatchSnapshot;
import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
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

    /** Eight cycling-suit Aces and every hand type leveled far up: one hand clears any early blind. */
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
