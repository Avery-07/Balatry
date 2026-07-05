package model.game.player;

import model.cards.DeckCard;
import model.cards.consumables.ConsumableCard;
import model.cards.jokers.JokerCard;
import model.game.Blind;
import model.game.BossBlind;
import model.game.scoring.Trigger;
import model.modifiers.Enhancement;

import java.math.BigDecimal;

/** Resolves a finished round: end-of-round card effects and the cash-out economy, producing a {@link BlindResult}. */
public final class RoundSettlement {

    private static final int GOLD_CARD_BONUS = 3;   // $ per Gold card held at end of round
    private static final int HAND_BONUS = 1;        // $ per remaining hand
    private static final int INTEREST_PER = 5;      // $1 of interest per $5 held

    /** Settles {@code round} against {@code blind}; cash-out applies only when the blind was cleared. */
    public BlindResult settle(Run run, Round round, Blind blind) {
        int entryMoney = run.getMoney();

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
        int interest = interest(run.getMoney(), run.getInterestCap());   // on money held entering cash-out
        int handsBonus = round.getHandsRemaining() * HAND_BONUS;
        int reward = blind.getReward();
        int gold = GOLD_CARD_BONUS * countGold(round);
        int rental = totalRental(run);

        run.addMoney(reward + handsBonus + interest + gold - rental);

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