package model.game.player;

import model.items.Card;
import model.items.jokers.JokerCard;
import model.game.Stake;
import model.game.rng.Rng;
import model.game.rng.RngSource;
import model.modifiers.Sticker;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * The round-scoped behaviour of the drawback stickers, and the stake-driven roll that hands them out. Kept in one
 * place because these effects fire from four different moments — round start, before each hand, after each hand,
 * and end of round — and scattering them across {@link Round} and {@link RoundSettlement} would make the set
 * impossible to read as a whole.
 *
 * <p>Everything random here is keyed, so a replay produces the same stickers on the same cards and moves the same
 * Floating jokers to the same places.
 */
public final class Stickers {

    /** A hand scoring under this fraction of the blind's target sours a {@link Sticker#FRAGILE} joker. */
    private static final int FRAGILE_THRESHOLD_PERCENT = 10;

    /** One in this many shop cards carries a sticker, at stakes that hand them out at all. */
    private static final int ROLL_ONE_IN = 4;

    private Stickers() { }

    // --- the stake roll ----------------------------------------------------

    /**
     * The stickers {@code stake} may put on a shop card. Cumulative like everything else about a stake: Gold
     * rolls from its own pair plus every pair below it.
     */
    public static List<Sticker> poolFor(Stake stake) {
        List<Sticker> pool = new ArrayList<>();
        if (stake == null) return pool;
        if (stake.includes(Stake.RED))  { pool.add(Sticker.FLOATING); pool.add(Sticker.STICKY); }
        if (stake.includes(Stake.BLUE)) { pool.add(Sticker.FRAGILE);  pool.add(Sticker.ETERNAL); }
        if (stake.includes(Stake.GOLD)) { pool.add(Sticker.RENTAL);   pool.add(Sticker.PERISHABLE); }
        return pool;
    }

    /**
     * Maybe stickers a freshly-rolled shop card, by the seat's stake. Only jokers are eligible — the drawbacks
     * are all written in terms of a joker sitting on a board. {@code salt} must identify the slot uniquely so the
     * roll is stable across replays.
     */
    public static void rollOnto(Card card, Stake stake, Rng rng, long salt) {
        if (!(card instanceof JokerCard) || rng == null) return;
        List<Sticker> pool = poolFor(stake);
        if (pool.isEmpty()) return;
        if (!rng.chance(RngSource.CARD_STICKER, salt, 1, ROLL_ONE_IN)) return;
        card.apply(pool.get(rng.nextInt(RngSource.CARD_STICKER, Rng.combine(salt, 1), pool.size())));
    }

    // --- round-scoped behaviour --------------------------------------------

    /** Round start: a Delayed joker is silenced until the round's first hand is behind it. */
    public static void beginRound(Run run) {
        for (JokerCard j : run.getJokers()) if (j.hasSticker(Sticker.DELAYED)) j.setSuppressed(true);
    }

    /**
     * Before a hand is scored: every Floating joker drifts to a new board position. Keyed by the hand's index so
     * the drift is part of the replay rather than a source of divergence.
     */
    public static void beforeHand(Run run, int handIndex) {
        Board board = run.board();
        for (int i = 0; i < board.size(); i++) {
            if (!board.get(i).hasSticker(Sticker.FLOATING)) continue;
            int to = run.getRng().nextInt(RngSource.STICKER_FLOAT, Rng.combine(handIndex, i), board.size());
            if (to != i) board.move(i, to);
        }
    }

    /**
     * After a hand is scored: Delayed jokers wake up (the first hand has now been played), and a hand that
     * limped in under a tenth of the target sours every Fragile joker on the board.
     */
    public static void afterHand(Run run, BigDecimal handScore, long target) {
        for (JokerCard j : run.getJokers()) if (j.hasSticker(Sticker.DELAYED)) j.setSuppressed(false);

        if (target <= 0) return;
        BigDecimal floor = BigDecimal.valueOf(target)
                .multiply(BigDecimal.valueOf(FRAGILE_THRESHOLD_PERCENT))
                .divide(BigDecimal.valueOf(100));
        if (handScore.compareTo(floor) >= 0) return;
        for (JokerCard j : run.getJokers()) if (j.hasSticker(Sticker.FRAGILE)) j.apply(Sticker.DEBUFFED);
    }

    /** Round end: clear any lingering suppression, so a seat never carries a silenced joker into the next round. */
    public static void endRound(Run run) {
        for (JokerCard j : run.getJokers()) j.setSuppressed(false);
    }
}
