package model.game.tags;

import model.items.DeckCard;
import model.items.DeckCard.Rank;
import model.items.DeckCard.Suit;
import model.items.jokers.Jokers;
import model.items.packs.PackKind;
import model.items.packs.PackSize;
import model.game.player.Run;
import model.game.scoring.HandType;

import java.util.ArrayList;
import java.util.List;

/** Run-as-main harness for the skip-tag catalog: immediate economy and permanent-upgrade effects, pack grants into the pending area, the null-safety of stat-driven tags on a fresh run, and the pending timings accumulating untouched. */
public final class TagTests {

    private static int failures = 0;

    public static void main(String[] args) {
        economyTags();
        permanentTags();
        packTags();
        pendingTags();
        doubleTag();

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void economyTags() {
        Run run = new Run(1L);
        run.addMoney(10);
        run.grantTag(SkipTag.ECONOMY_TAG);
        checkInt("Economy doubles money", run.getMoney(), 20);
        run.addMoney(80);   // 100 held
        run.grantTag(SkipTag.ECONOMY_TAG);
        checkInt("Economy gain caps at $40", run.getMoney(), 140);

        Run stats = new Run(2L);
        for (int i = 0; i < 12; i++) stats.addCardToDeck(new DeckCard(Rank.ACE, Suit.SPADES));
        var round = stats.beginRound(1);
        round.play(new ArrayList<>(round.getHand().subList(0, 5)));
        round.play(new ArrayList<>(round.getHand().subList(0, 5)));
        round.finish();                                   // 2 hands played, 3 discards unused
        stats.endRound(model.game.Blind.SMALL);
        stats.grantTag(SkipTag.HANDY_TAG);
        stats.grantTag(SkipTag.GARBAGE_TAG);
        int handy = 2 * stats.getStats().getTotalHandsPlayed();
        int garbage = 2 * stats.getStats().getUnusedDiscards();
        check("Handy pays $2 per played hand (" + handy + ")", handy == 4);
        check("Garbage pays $2 per unused settled discard (" + garbage + ")", garbage == 6);

        Run speed = new Run(3L);
        speed.getStats().recordBlindSkipped();
        speed.getStats().recordBlindSkipped();
        speed.grantTag(SkipTag.SPEED_TAG);
        checkInt("Speed pays $10 per skipped blind", speed.getMoney(), 20);
    }

    private static void permanentTags() {
        Run run = new Run(4L);
        int hand = run.getHandSize();
        int slots = run.getConsumableSlots();
        run.grantTag(SkipTag.JUGGLE_TAG);
        run.grantTag(SkipTag.INVENTORY_TAG);
        checkInt("Juggle grants +1 hand size permanently", run.getHandSize(), hand + 1);
        checkInt("Inventory grants +1 consumable slot", run.getConsumableSlots(), slots + 1);

        // Orbital: +3 levels on the most played hand; a no-op before any hand exists.
        Run fresh = new Run(5L);
        fresh.grantTag(SkipTag.ORBITAL_TAG);
        check("Orbital is a no-op with no hands played", fresh.getStats().getMostPlayedHand() == null);
        for (int i = 0; i < 8; i++) fresh.addCardToDeck(new DeckCard(Rank.ACE, Suit.SPADES));
        var round = fresh.beginRound(1);
        round.play(new ArrayList<>(round.getHand().subList(0, 5)));
        round.finish();
        fresh.endRound(model.game.Blind.SMALL);
        HandType most = fresh.getStats().getMostPlayedHand();
        int level = fresh.getHandLevels().levelOf(most);
        fresh.grantTag(SkipTag.ORBITAL_TAG);
        checkInt("Orbital upgrades the most played hand by 3", fresh.getHandLevels().levelOf(most), level + 3);

        // Top-up: up to 3 commons, capped by board room.
        Run topUp = new Run(6L);
        topUp.grantTag(SkipTag.TOP_UP_TAG);
        checkInt("Top-up creates 3 common jokers", topUp.board().size(), 3);
        topUp.board().add(Jokers.JOKER.make());   // 4/5
        topUp.grantTag(SkipTag.TOP_UP_TAG);
        checkInt("Top-up respects board room", topUp.board().size(), 5);
    }

    private static void packTags() {
        Run run = new Run(7L);
        run.grantTag(SkipTag.MYTH_TAG);
        run.grantTag(SkipTag.SPECTRAL_TAG);
        checkInt("pack tags land in the pending area", run.getPendingPacks().size(), 2);
        var myth = run.getPendingPacks().get(0);
        check("Myth Tag grants a Mega Myth Pack", myth.kind() == PackKind.MYTH && myth.size() == PackSize.MEGA);
        var spectral = run.getPendingPacks().get(1);
        check("Spectral Tag grants a normal Spectral Pack",
                spectral.kind() == PackKind.SPECTRAL && spectral.size() == PackSize.NORMAL);
    }

    private static void pendingTags() {
        Run run = new Run(8L);
        run.grantTag(SkipTag.COUPON_TAG);
        run.grantTag(SkipTag.INVESTMENT_TAG);
        run.grantTag(SkipTag.DOUBLE_TAG);
        check("pending timings accumulate untouched",
                run.getPendingTags().equals(List.of(SkipTag.COUPON_TAG, SkipTag.INVESTMENT_TAG, SkipTag.DOUBLE_TAG)));
        checkInt("tags gained counts every timing", run.getStats().getTagsGained(), 3);
        check("a pending tag can be consumed once", run.consumePendingTag(SkipTag.INVESTMENT_TAG)
                && !run.consumePendingTag(SkipTag.INVESTMENT_TAG));
    }

    /** Double Tag: every pending copy duplicates the next selected tag (Double excluded), with its own timing. */
    private static void doubleTag() {
        // One pending Double + an IMMEDIATE grant -> the effect resolves twice ($10 -> $20 -> $40).
        Run run = new Run(9L);
        run.addMoney(10);
        run.grantTag(SkipTag.DOUBLE_TAG);
        run.grantTag(SkipTag.ECONOMY_TAG);
        checkInt("Double resolves an immediate tag twice", run.getMoney(), 40);
        check("Double consumed by the grant", run.getPendingTags().isEmpty());

        // Two pending Doubles + a pending-timing grant -> three copies, all pending with the tag's own timing.
        Run stacked = new Run(10L);
        stacked.grantTag(SkipTag.DOUBLE_TAG);
        stacked.grantTag(SkipTag.DOUBLE_TAG);
        stacked.grantTag(SkipTag.VOUCHER_TAG);
        checkInt("two Doubles -> three pending copies", stacked.getPendingTags().size(), 3);
        check("all copies are the selected tag",
                stacked.getPendingTags().stream().allMatch(t -> t == SkipTag.VOUCHER_TAG));
        checkInt("each copy counts as a gained tag", stacked.getStats().getTagsGained(), 5);

        // A Double never copies another Double: granting a second leaves both pending.
        Run selfless = new Run(11L);
        selfless.grantTag(SkipTag.DOUBLE_TAG);
        selfless.grantTag(SkipTag.DOUBLE_TAG);
        check("Double does not copy itself",
                selfless.getPendingTags().equals(List.of(SkipTag.DOUBLE_TAG, SkipTag.DOUBLE_TAG)));
    }

    private static void check(String label, boolean ok) {
        if (!ok) failures++;
        System.out.printf("%-52s %s%n", label, ok ? "PASS" : "FAIL");
    }

    private static void checkInt(String label, int actual, int expected) {
        check(label + " (" + actual + ")", actual == expected);
    }
}
