package model.game.player;

import model.items.DeckCard;
import model.items.consumables.ConsumableCard;
import model.items.consumables.Tarots;
import model.items.jokers.JokerCard;
import model.game.Blind;
import model.game.BossBlind;
import model.game.scoring.Trigger;
import model.game.tags.SkipTag;
import model.modifiers.Enhancement;

import java.math.BigDecimal;

/** Resolves a finished round: end-of-round card effects and the cash-out economy, producing a {@link BlindResult}. */
public final class RoundSettlement {

    private static final int GOLD_CARD_BONUS = 3;   // $ per Gold card held at end of round
    private static final int HAND_BONUS = 1;        // $ per remaining hand
    private static final int INTEREST_PER = 5;      // $1 of interest per $5 held

    // The Frugal sleeve's replacement economy: paid per unused hand and discard, with no interest at all.
    private static final int FRUGAL_PER_HAND = 2, FRUGAL_PER_DISCARD = 1;

    /** Settles {@code round} against {@code blind}; cash-out applies only when the blind was cleared. */
    public BlindResult settle(Run run, Round round, Blind blind) {
        int entryMoney = run.getMoney();
        Stickers.endRound(run);   // unconditional: a lost round must not carry a Delayed silence into the next

        if (round.getOutcome() == RoundOutcome.WON) {
            cashOut(run, round, blind);
            maintainStickers(run);
        }
        // A failed blind awards nothing; the run continues (no elimination). [policy decision to confirm]

        if (round.getOutcome() == RoundOutcome.SKIPPED)   // a skip forfeits everything: no reward, no interest
            return new BlindResult(RoundOutcome.SKIPPED, BigDecimal.ZERO, BigDecimal.ZERO,
                    round.getTarget(), round.getHandsRemaining(), 0);

        run.getStats().recordUnusedDiscards(round.getDiscardsRemaining());   // Garbage Tag's run accumulator

        // The Mirage: the seat's own best hand is excluded from the settled score (the score the points
        // award reads); the target check already resolved on the raw banked score, so clearing is unaffected.
        BigDecimal settled = round.getScore();
        BossBlind boss = run.effectiveBoss();
        if (boss != null && boss.dropsOwnHighestHand()) settled = settled.subtract(round.getBestHandScore());

        return new BlindResult(round.getOutcome(), settled, round.getBestHandScore(), round.getTarget(),
                round.getHandsRemaining(), run.getMoney() - entryMoney);
    }

    private void cashOut(Run run, Round round, Blind blind) {
        int reward = run.getStake().rewardFor(blind);   // per-seat: the Black Stake and up pay nothing for a Small Blind
        int gold = GOLD_CARD_BONUS * countGold(round);
        int rental = totalRental(run);

        if (run.getSleeve() == Sleeve.FRUGAL) {
            // Frugal trades interest for a flat payout on what went unused: $2 a hand, $1 a discard, no interest.
            int frugal = round.getHandsRemaining() * FRUGAL_PER_HAND
                       + round.getDiscardsRemaining() * FRUGAL_PER_DISCARD;
            run.addMoney(reward + frugal + gold - rental);
        } else {
            int interest = interest(run.getMoney(), run.getInterestCap());   // on money held entering cash-out
            int handsBonus = round.getHandsRemaining() * HAND_BONUS;
            run.addMoney(reward + handsBonus + interest + gold - rental);
        }

        // Anaglyph: clearing a boss blind pays a Double Tag and a Fool on top of the usual cash-out.
        if (blind == Blind.BOSS && run.getDeckType().rewardsBossBonus()) {
            run.grantTag(SkipTag.DOUBLE_TAG);
            run.createConsumable(Tarots.THE_FOOL.spec());
        }

        for (JokerCard joker : run.getJokers())
            if (!joker.isDebuffed()) joker.trigger(Trigger.ON_ROUND_END, run);

        // Blue Seal -> Planet of round.getLastPlayedType(): pending the Planet catalog and Run.createConsumable.
    }

    private void maintainStickers(Run run) {
        for (JokerCard joker : run.getJokers()) joker.tickStickers();
        for (ConsumableCard consumable : run.getConsumables()) consumable.tickStickers();
    }

    private static int interest(int money, int cap) {
        return money <= 0 ? 0 : Math.min(money / INTEREST_PER, cap);
    }

    private static int countGold(Round round) {
        int n = 0;
        for (DeckCard card : round.getHand()) if (card.getEnhancement() == Enhancement.GOLD) n++;
        return n;
    }

    private static int totalRental(Run run) {
        int total = 0;
        for (JokerCard joker : run.getJokers()) total += joker.getRentalCost();
        for (ConsumableCard consumable : run.getConsumables()) total += consumable.getRentalCost();
        return total;
    }
}