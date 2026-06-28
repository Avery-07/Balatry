package model.game.player;

import model.cards.Card;
import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.JokerCard;
import model.cards.jokers.JokerSpec;
import model.cards.jokers.Rarity;
import model.game.Match;
import model.game.player.PlayerId;
import model.modifiers.Edition;

import java.util.ArrayList;
import java.util.List;

/** Run-as-main harness for {@link Shop}: mirroring, buy/sell, slot limits + NEGATIVE, reroll, and Match open/close. */
public final class ShopTests {

    private static int failures = 0;

    private static final JokerSpec SPEC = JokerSpec.named("Stub", Rarity.COMMON).build();
    private static final ShopPool JOKER_POOL = stream -> new JokerCard(SPEC, 4);
    private static final ShopPool CARD_POOL = stream -> { DeckCard d = new DeckCard(Rank.ACE, Suit.SPADES); d.setShopValue(1); return d; };
    private static final ShopPool NEG_JOKER_POOL = stream -> { JokerCard j = new JokerCard(SPEC, 4); j.apply(Edition.NEGATIVE); return j; };

    public static void main(String[] args) {
        // --- same seed -> identical offerings (placeholder pool) ---
        Shop sa = new Run(7L).openShop();
        Shop sb = new Run(7L).openShop();
        boolean mirrored = sa.getSlotCount() == sb.getSlotCount();
        for (int i = 0; mirrored && i < sa.getSlotCount(); i++) mirrored = sameItem(sa.getSlot(i), sb.getSlot(i));
        check("same seed -> mirrored shop", mirrored);

        Shop sc = new Run(8L).openShop();
        boolean differs = false;
        for (int i = 0; i < sa.getSlotCount(); i++) if (!sameItem(sa.getSlot(i), sc.getSlot(i))) differs = true;
        check("different seed -> different shop", differs);

        // --- buy a joker: charged, routed to inventory, slot cleared ---
        Run r1 = new Run(1L); r1.addMoney(10);
        Shop s1 = new Shop(r1, 0, 2, JOKER_POOL);
        checkInt("two slots", s1.getSlotCount(), 2);
        Card bought = s1.buy(0);
        check("bought a joker", bought instanceof JokerCard);
        checkInt("joker in inventory", r1.getJokers().size(), 1);
        checkInt("money 10 -> 6", r1.getMoney(), 6);
        check("slot cleared", s1.getSlot(0) == null);

        // --- slot limit blocks a normal joker; NEGATIVE bypasses it ---
        Run r2 = new Run(2L); r2.addMoney(100);
        for (int i = 0; i < 5; i++) r2.acquire(new JokerCard(SPEC, 4));
        checkInt("5 joker slots used", r2.usedJokerSlots(), 5);
        check("cannot add a normal joker", !r2.canAddJoker(new JokerCard(SPEC, 4)));
        checkThrows("buy blocked when full", () -> new Shop(r2, 0, 1, JOKER_POOL).buy(0));
        Card neg = new Shop(r2, 0, 1, NEG_JOKER_POOL).buy(0);
        check("negative joker bought", neg instanceof JokerCard);
        checkInt("inventory grew to 6", r2.getJokers().size(), 6);
        checkInt("used slots still 5", r2.usedJokerSlots(), 5);

        // --- reroll cost escalates and is affordability-gated ---
        Run r3 = new Run(3L); r3.addMoney(20);
        Shop s3 = new Shop(r3, 0, 2, JOKER_POOL);
        checkInt("first reroll costs 5", s3.rerollCost(), 5);
        s3.reroll();
        checkInt("money 20 -> 15", r3.getMoney(), 15);
        checkInt("next reroll costs 6", s3.rerollCost(), 6);
        s3.reroll();
        checkInt("money 15 -> 9", r3.getMoney(), 9);
        checkInt("third reroll costs 7", s3.rerollCost(), 7);
        r3.addMoney(-9);
        checkThrows("reroll blocked when broke", s3::reroll);

        // --- sell returns half the shop value and frees the slot ---
        Run r4 = new Run(4L); r4.addMoney(10);
        new Shop(r4, 0, 1, JOKER_POOL).buy(0);   // -4 -> 6
        int got = r4.sellJoker(0);
        checkInt("sell value 2", got, 2);
        checkInt("money 6 -> 8", r4.getMoney(), 8);
        checkInt("inventory empty", r4.getJokers().size(), 0);

        // --- cannot buy without money ---
        checkThrows("buy blocked when broke", () -> new Shop(new Run(5L), 0, 1, JOKER_POOL).buy(0));

        // --- a bought playing card goes to the deck ---
        Run r6 = new Run(6L); r6.addMoney(5);
        Shop s6 = new Shop(r6, 0, 1, CARD_POOL);
        Card card = s6.buy(0);
        check("bought a deck card", card instanceof DeckCard);
        checkInt("deck grew by 1", r6.getDeck().size(), 1);
        checkInt("money 5 -> 4", r6.getMoney(), 4);

        // --- Match opens a shop per seat at the barrier and closes it on leaving ---
        Match match = Match.create(50L, List.of("A", "B"));
        match.start();
        for (PlayerId id : match.getSeats()) exhaust(match, id);
        match.toShop();
        boolean opened = true;
        for (PlayerId id : match.getSeats())
            opened &= match.getRun(id).getShop() != null && match.getRun(id).getShop().getSlotCount() == 3;
        check("shop opened per seat", opened);
        match.nextBlind();
        boolean closed = true;
        for (PlayerId id : match.getSeats()) closed &= match.getRun(id).getShop() == null;
        check("shop closed on next blind", closed);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void exhaust(Match match, PlayerId id) {
        while (match.getRun(id).getRound().getOutcome() == RoundOutcome.IN_PROGRESS) {
            List<DeckCard> one = new ArrayList<>(match.getRun(id).getRound().getHand().subList(0, 1));
            match.getRun(id).getRound().play(one);
        }
    }

    private static boolean sameItem(Card x, Card y) {
        if (x == null || y == null) return x == y;
        if (x.getClass() != y.getClass() || x.getShopValue() != y.getShopValue()) return false;
        if (x instanceof DeckCard dx && y instanceof DeckCard dy) return dx.getRank() == dy.getRank() && dx.getSuit() == dy.getSuit();
        return true;
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-36s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable action) {
        boolean threw = false;
        try { action.run(); } catch (RuntimeException e) { threw = true; }
        check(label, threw);
    }
}