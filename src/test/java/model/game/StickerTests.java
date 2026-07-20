package model.game;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.jokers.JokerCard;
import model.items.jokers.Jokers;
import model.game.player.Run;
import model.game.player.Stickers;
import model.modifiers.Sticker;
import model.modifiers.StickerState;

import java.util.ArrayList;
import java.util.List;

/**
 * Run-as-main harness for the drawback stickers — the four new ones (Floating, Delayed, Fragile, Sticky) and the
 * stake pools that hand them out. These are what the Red, Blue and Gold stakes actually <em>are</em>, so the
 * pools' cumulative shape is asserted here alongside each sticker's behaviour.
 */
public final class StickerTests {

    private static int failures = 0;

    public static void main(String[] args) {
        stakePools();
        stickerState();
        delayed();
        fragile();
        floating();
        sticky();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    /** A stake's sticker pool is cumulative, exactly like its numeric effects. */
    private static void stakePools() {
        check("White rolls no stickers", Stickers.poolFor(Stake.WHITE).isEmpty());

        List<Sticker> red = Stickers.poolFor(Stake.RED);
        check("Red rolls Floating and Sticky",
                red.contains(Sticker.FLOATING) && red.contains(Sticker.STICKY) && red.size() == 2);

        // Green adds no stickers of its own, but sits above Red, so it inherits Red's pair.
        check("Green inherits Red's pair", Stickers.poolFor(Stake.GREEN).equals(red));

        List<Sticker> blue = Stickers.poolFor(Stake.BLUE);
        check("Blue adds Fragile and Eternal", blue.contains(Sticker.FRAGILE) && blue.contains(Sticker.ETERNAL));
        check("Blue keeps Red's pair", blue.contains(Sticker.FLOATING) && blue.contains(Sticker.STICKY));

        List<Sticker> gold = Stickers.poolFor(Stake.GOLD);
        check("Gold adds Rental and Perishable", gold.contains(Sticker.RENTAL) && gold.contains(Sticker.PERISHABLE));
        checkInt("Gold rolls from all six", gold.size(), 6);
    }

    /** Perishable counts down; Sticky counts up. Both ride the same per-round tick. */
    private static void stickerState() {
        JokerCard perishable = Jokers.JOKER.make();
        perishable.apply(Sticker.PERISHABLE);
        checkInt("Perishable starts its countdown",
                perishable.getStickerState(Sticker.PERISHABLE).roundsRemaining(), StickerState.PERISHABLE_ROUNDS);

        JokerCard sticky = Jokers.JOKER.make();
        sticky.apply(Sticker.STICKY);
        checkInt("Sticky opens at its base toll", sticky.getStickySellCost(), StickerState.STICKY_BASE_COST);
        sticky.tickStickers();
        checkInt("a round raises the toll", sticky.getStickySellCost(),
                StickerState.STICKY_BASE_COST + StickerState.STICKY_COST_PER_ROUND);
        sticky.tickStickers();
        checkInt("and again", sticky.getStickySellCost(),
                StickerState.STICKY_BASE_COST + 2 * StickerState.STICKY_COST_PER_ROUND);

        check("a plain joker owes nothing to sell", Jokers.JOKER.make().getStickySellCost() == 0);
    }

    /** Delayed sits out the round's first hand, then wakes — and never leaks into the next round. */
    private static void delayed() {
        Run run = stockedRun(5L);
        JokerCard j = Jokers.JOKER.make();
        j.apply(Sticker.DELAYED);
        run.acquire(j);

        var round = run.beginRound(50);
        check("Delayed is silent as the round opens", j.isDebuffed());
        check("the silence is transient, not a sticker", j.isSuppressed() && !j.hasSticker(Sticker.DEBUFFED));

        playFive(round);
        check("Delayed wakes after the first hand", !j.isDebuffed());

        // A round that ends while a Delayed joker is silenced must not carry that silence forward.
        Run second = stockedRun(6L);
        JokerCard k = Jokers.JOKER.make();
        k.apply(Sticker.DELAYED);
        second.acquire(k);
        var lost = second.beginRound(1_000_000);
        check("silenced at the open", k.isDebuffed());
        lost.finish();                     // resolves as a loss, mid-silence
        second.endRound(Blind.SMALL);
        check("the silence is cleared by settlement", !k.isDebuffed());
    }

    /** Fragile sours on a hand that limps in under a tenth of the target. */
    private static void fragile() {
        Run weak = stockedRun(7L);
        JokerCard j = Jokers.JOKER.make();
        j.apply(Sticker.FRAGILE);
        weak.acquire(j);
        var round = weak.beginRound(1_000_000);   // nothing this hand plays will reach a tenth of this
        check("Fragile starts intact", !j.isDebuffed());
        playFive(round);
        check("a feeble hand debuffs Fragile", j.hasSticker(Sticker.DEBUFFED));

        Run strong = stockedRun(7L);
        JokerCard k = Jokers.JOKER.make();
        k.apply(Sticker.FRAGILE);
        strong.acquire(k);
        playFive(strong.beginRound(1));           // any hand clears a tenth of a target of 1
        check("a solid hand leaves Fragile alone", !k.isDebuffed());
    }

    /** Floating drifts each hand, deterministically — the same seed moves it the same way. */
    private static void floating() {
        check("the same seed drifts identically", floatOrder(11L).equals(floatOrder(11L)));
        check("a different seed drifts differently", !floatOrder(11L).equals(floatOrder(12L)));

        // The board keeps every joker; drifting is a reorder, never a loss.
        checkInt("the board keeps all its jokers", floatOrder(11L).size(), 4);
        check("the drifting joker is still there", floatOrder(11L).contains("F"));
    }

    /** Sticky charges to sell, and refuses the sale outright when the seat cannot pay. */
    private static void sticky() {
        Run rich = stockedRun(9L);
        JokerCard j = Jokers.JOKER.make();
        j.apply(Sticker.STICKY);
        rich.acquire(j);
        rich.addMoney(20);
        int before = rich.getMoney();
        int value = rich.sellJoker(0);
        checkInt("selling a Sticky joker charges its toll", rich.getMoney(),
                before + value - StickerState.STICKY_BASE_COST);

        Run broke = stockedRun(10L);
        JokerCard k = Jokers.JOKER.make();
        k.apply(Sticker.STICKY);
        broke.acquire(k);
        checkThrows("a seat that cannot pay the toll cannot sell", () -> broke.sellJoker(0));
        checkInt("and the joker stays on the board", broke.getJokers().size(), 1);

        // Eternal still wins: it is refused before any toll is considered.
        Run eternal = stockedRun(11L);
        JokerCard e = Jokers.JOKER.make();
        e.apply(Sticker.ETERNAL);
        e.apply(Sticker.STICKY);
        eternal.acquire(e);
        eternal.addMoney(50);
        int held = eternal.getMoney();
        checkThrows("an Eternal joker still cannot be sold", () -> eternal.sellJoker(0));
        checkInt("a refused sale charges nothing", eternal.getMoney(), held);
    }

    // --- helpers ---

    /** The board order after two hands, with the Floating joker marked "F" — the thing a drift reorders. */
    private static List<String> floatOrder(long seed) {
        Run run = stockedRun(seed);
        JokerCard floating = Jokers.JOKER.make();
        floating.apply(Sticker.FLOATING);
        run.acquire(Jokers.GREEDY_JOKER.make());
        run.acquire(floating);
        run.acquire(Jokers.LUSTY_JOKER.make());
        run.acquire(Jokers.WRATHFUL_JOKER.make());

        var round = run.beginRound(50);
        playFive(round);
        if (round.getHandsRemaining() > 0 && round.getHand().size() >= 5) playFive(round);

        List<String> order = new ArrayList<>();
        for (JokerCard j : run.getJokers()) order.add(j == floating ? "F" : j.getSpec().getName());
        return order;
    }

    /** A run with enough cards to keep dealing five-card hands. */
    private static Run stockedRun(long seed) {
        Run run = new Run(seed);
        for (int i = 0; i < 40; i++)
            run.addCardToDeck(new DeckCard(Rank.values()[i % Rank.values().length], Suit.values()[i % 4]));
        return run;
    }

    private static void playFive(model.game.player.Round round) {
        round.play(new ArrayList<>(round.getHand().subList(0, Math.min(5, round.getHand().size()))));
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }

    private static void checkThrows(String label, Runnable r) {
        try { r.run(); check(label, false); }
        catch (RuntimeException e) { check(label, true); }
    }
}
