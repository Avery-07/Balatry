package model.game.player;

import model.game.scoring.HandType;

/** Run-as-main harness verifying {@link HandLevels} base values and leveling math. */
public final class HandLevelsTests {

    private static int failures = 0;

    public static void main(String[] args) {
        HandLevels levels = new HandLevels();

        // Every hand starts at level 1 with its declared base.
        check("high card L1 chips", levels.chipsFor(HandType.HIGH_CARD), 5);
        check("high card L1 mult", levels.multFor(HandType.HIGH_CARD), 1);
        check("pair L1 chips", levels.chipsFor(HandType.PAIR), 10);
        check("pair L1 mult", levels.multFor(HandType.PAIR), 2);
        check("flush five L1 chips", levels.chipsFor(HandType.FLUSH_FIVE), 160);
        check("flush five L1 mult", levels.multFor(HandType.FLUSH_FIVE), 16);
        check("default level is 1", levels.levelOf(HandType.STRAIGHT), 1);

        // One Planet on Pair: level 2 -> base + 1 * per-level gain.
        check("levelUp returns new level", levels.levelUp(HandType.PAIR), 2);
        check("pair L2 chips (10 + 15)", levels.chipsFor(HandType.PAIR), 25);
        check("pair L2 mult (2 + 1)", levels.multFor(HandType.PAIR), 3);

        // Three Planets on Flush: level 4 -> base + 3 * per-level gain.
        levels.levelUp(HandType.FLUSH);
        levels.levelUp(HandType.FLUSH);
        levels.levelUp(HandType.FLUSH);
        check("flush L4 level", levels.levelOf(HandType.FLUSH), 4);
        check("flush L4 chips (35 + 3*15)", levels.chipsFor(HandType.FLUSH), 80);
        check("flush L4 mult (4 + 3*2)", levels.multFor(HandType.FLUSH), 10);

        // Leveling one hand leaves others untouched.
        check("straight untouched", levels.levelOf(HandType.STRAIGHT), 1);

        System.out.println(failures == 0 ? "\nALL PASS" : "\n" + failures + " FAILURE(S)");
        if (failures != 0) System.exit(1);
    }

    private static void check(String label, long actual, long expected) {
        boolean ok = actual == expected;
        if (!ok) failures++;
        System.out.printf("%-38s %s  (got %d, expected %d)%n", label, ok ? "PASS" : "FAIL", actual, expected);
    }
}