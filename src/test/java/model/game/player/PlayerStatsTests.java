package model.game.player;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.consumables.ConsumableType;
import model.items.consumables.Planets;
import model.items.consumables.Tarots;
import model.game.scoring.HandType;

import java.util.List;

/** Run-as-main harness for {@link PlayerStats}: hand-type history, discards, consumable usage, economy counters, and resets. */
public final class PlayerStatsTests {

    private static int failures = 0;

    public static void main(String[] args) {
        handTypeTracking();
        recentWindowAndMostPlayed();
        discardTracking();
        consumableTracking();
        economyCounters();
        roundReset();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    // --- hand-type counts (run + round), Supernova / Card Sharp shapes ---
    private static void handTypeTracking() {
        PlayerStats s = new PlayerStats();
        s.recordHandPlayed(HandType.PAIR);
        s.recordHandPlayed(HandType.PAIR);
        s.recordHandPlayed(HandType.FLUSH);

        check("PAIR run count", s.getHandPlays(HandType.PAIR), 2);
        check("FLUSH run count", s.getHandPlays(HandType.FLUSH), 1);
        check("HIGH_CARD never played", s.getHandPlays(HandType.HIGH_CARD), 0);
        check("total hands played", s.getTotalHandsPlayed(), 3);
        check("PAIR played this round (Card Sharp sees >1)", s.getHandPlaysThisRound(HandType.PAIR), 2);
    }

    // --- recent window (Obelisk) + most-played tie-break (Black Hole / Satellite / Telescope) ---
    private static void recentWindowAndMostPlayed() {
        PlayerStats s = new PlayerStats();
        s.recordHandPlayed(HandType.PAIR);
        s.recordHandPlayed(HandType.TWO_PAIR);
        s.recordHandPlayed(HandType.FLUSH);

        check("last hand is FLUSH", s.getLastHandType() == HandType.FLUSH);
        List<HandType> recent = s.getRecentHands(2);
        check("recent[0] = FLUSH (most recent first)", recent.get(0) == HandType.FLUSH);
        check("recent[1] = TWO_PAIR", recent.get(1) == HandType.TWO_PAIR);
        check("recent window respects n", recent.size(), 2);

        // tie between PAIR(2) and FLUSH(2): higher-ranking hand (FLUSH) wins
        PlayerStats t = new PlayerStats();
        t.recordHandPlayed(HandType.PAIR);
        t.recordHandPlayed(HandType.FLUSH);
        t.recordHandPlayed(HandType.PAIR);
        t.recordHandPlayed(HandType.FLUSH);
        check("most-played tie breaks toward higher hand", t.getMostPlayedHand() == HandType.FLUSH);

        check("most-played is null before any play", new PlayerStats().getMostPlayedHand() == null);
    }

    // --- discards: run total (Yorick) + per-rank this round (Hit the Road) ---
    private static void discardTracking() {
        PlayerStats s = new PlayerStats();
        s.recordDiscard(List.of(card(Rank.JACK), card(Rank.JACK), card(Rank.THREE)));
        s.recordDiscard(List.of(card(Rank.JACK)));

        check("cards discarded this run", s.getCardsDiscarded(), 4);
        check("discard actions this run", s.getDiscardsUsed(), 2);
        check("Jacks discarded this round", s.getDiscardedThisRound(Rank.JACK), 3);
        check("Threes discarded this round", s.getDiscardedThisRound(Rank.THREE), 1);
        check("cards discarded this round", s.getCardsDiscardedThisRound(), 4);
    }

    // --- consumables: per-type count (Constellation / Fortune Teller) + unique (Satellite) ---
    private static void consumableTracking() {
        PlayerStats s = new PlayerStats();
        s.recordConsumableUsed(Planets.MARS.spec());
        s.recordConsumableUsed(Planets.MARS.spec());      // same planet twice
        s.recordConsumableUsed(Planets.VENUS.spec());
        s.recordConsumableUsed(Tarots.THE_FOOL.spec());

        check("planets used", s.getConsumablesUsed(ConsumableType.PLANET), 3);
        check("tarots used (Fool counts)", s.getConsumablesUsed(ConsumableType.TAROT), 1);
        check("unique planets used", s.getUniqueConsumablesUsed(ConsumableType.PLANET), 2);
        check("total consumables used", s.getTotalConsumablesUsed(), 4);
    }

    // --- economy / card events + Campfire reset ---
    private static void economyCounters() {
        PlayerStats s = new PlayerStats();
        s.recordReroll(); s.recordReroll();
        s.recordPurchase();
        s.recordCardAdded();
        s.recordCardsDestroyed(2);
        s.recordGlassDestroyed(1);   // also counts toward total destroyed
        s.recordBlindSkipped();
        s.recordTargeted();
        s.recordCardSold(); s.recordCardSold();

        check("rerolls", s.getRerolls(), 2);
        check("purchases", s.getPurchases(), 1);
        check("cards added", s.getCardsAdded(), 1);
        check("glass destroyed", s.getGlassDestroyed(), 1);
        check("total destroyed (generic + glass)", s.getCardsDestroyed(), 3);
        check("blinds skipped", s.getBlindsSkipped(), 1);
        check("times targeted", s.getTimesTargeted(), 1);
        check("cards sold", s.getCardsSold(), 2);

        s.resetCardsSold();
        check("cards sold reset (Campfire)", s.getCardsSold(), 0);
        check("rerolls survive sold-reset", s.getRerolls(), 2);
    }

    // --- beginRound clears round-scoped tallies but keeps run history ---
    private static void roundReset() {
        PlayerStats s = new PlayerStats();
        s.recordHandPlayed(HandType.PAIR);
        s.recordDiscard(List.of(card(Rank.JACK)));

        s.beginRound();

        check("round hand-plays cleared", s.getHandPlaysThisRound(HandType.PAIR), 0);
        check("round Jack-discards cleared", s.getDiscardedThisRound(Rank.JACK), 0);
        check("round discard count cleared", s.getCardsDiscardedThisRound(), 0);
        check("run hand-plays survive round reset", s.getHandPlays(HandType.PAIR), 1);
        check("run discard count survives round reset", s.getCardsDiscarded(), 1);
    }

    // --- helpers ---
    private static DeckCard card(Rank rank) { return new DeckCard(rank, Suit.SPADES); }

    private static void check(String label, int actual, int expected) {
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.printf("%-44s %s  (got %d, expected %d)%n", label, ok ? "PASS" : "FAIL", actual, expected);
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-44s %s%n", label, ok ? "PASS" : "FAIL");
    }
}
