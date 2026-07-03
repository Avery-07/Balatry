package model.game.scoring;

import model.cards.consumables.ConsumableCard;
import model.cards.DeckCard;
import model.cards.jokers.JokerCard;
import model.game.rng.RngSource;
import model.game.player.Run;
import model.modifiers.Enhancement;
import model.modifiers.Seal;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Walks an already-evaluated hand (its {@link HandType} and scoring cards) and produces a score. */
public final class ScoringEngine {

    private static final BigDecimal X1_5 = new BigDecimal("1.5");
    private static final BigDecimal X2   = new BigDecimal("2");

    /** Scores one played hand: scored cards, then held cards, then independent joker/edition contributors. */
    public ScoringResult score(Run run, HandContext hand, long baseChips, long baseMult,
                               List<DeckCard> scoringCards, List<DeckCard> heldCards) {
        ScoringSession s = run.beginScoring(baseChips, baseMult);
        s.setHand(hand);
        List<DeckCard> destroyed = new ArrayList<>();

        // Phase A — scored cards, left to right; retrigger repeats the sub-sequence in place.
        for (int i = 0; i < scoringCards.size(); i++) {
            DeckCard card = scoringCards.get(i);
            if (card.isDebuffed() || run.bossDebuffs(card) || run.relicDebuffs(card)) continue;
            int passes = 1 + retriggers(card) + jokerRetriggers(run, card, true);
            for (int pass = 0; pass < passes; pass++) {
                s.setCurrentCard(card);
                scoreScoredCard(s, run, card, destroyed);
                fireJokers(run, Trigger.ON_SCORED_CARD);
            }
            s.setCurrentCard(null);
        }

        // Phase B — held cards.
        for(DeckCard card : heldCards) {
            if (card.isDebuffed() || run.bossDebuffs(card) || run.relicDebuffs(card)) continue;
            int passes = 1 + retriggers(card) + jokerRetriggers(run, card, false);
            for (int pass = 0; pass < passes; pass++) {
                s.setCurrentCard(card);
                scoreHeldCard(s, card);
                fireJokers(run, Trigger.ON_HELD_CARD);
            }
            s.setCurrentCard(null);
        }

        // Phase C — independent jokers + joker editions, then held-consumable editions.
        // Snapshot, matching Run.fire: an effect may add or remove jokers while the board is being walked.
        for (JokerCard joker : List.copyOf(run.getJokers())) {
            if (joker.isDebuffed()) continue;
            joker.trigger(Trigger.ON_HAND_PLAYED, run);
            s.applyEdition(joker.getEdition());
        }
        for (ConsumableCard consumable : run.getConsumables()) {
            if (consumable.isDebuffed()) continue;
            s.applyEdition(consumable.getEdition());
        }

        BigDecimal score = s.finalScore();
        run.endScoring();
        return new ScoringResult(score, destroyed);
    }

    /** Fires the given trigger on every non-debuffed joker, in order. Iterates a snapshot, matching {@link Run#fire}. */
    private void fireJokers(Run run, Trigger trigger) {
        for (JokerCard joker : List.copyOf(run.getJokers())) {
            if (!joker.isDebuffed()) joker.trigger(trigger, run);
        }
    }

    /** Extra scoring passes for a card. RED_SEAL adds one. */
    private int retriggers(DeckCard card) {
        return card.getSeal() == Seal.RED_SEAL ? 1 : 0;
    }

    /** Extra passes granted by jokers for {@code card} this phase, summed across the board. */
    private int jokerRetriggers(Run run, DeckCard card, boolean played) {
        int extra = 0;
        for (JokerCard joker : run.getJokers()) {
            if (joker.isDebuffed()) continue;
            extra += played ? joker.playedRetriggers(run, card) : joker.heldRetriggers(run, card);
        }
        return extra;
    }

    /** Applies one scored card's chips, enhancement, edition and seal to the session. */
    private void scoreScoredCard(ScoringSession s, Run run, DeckCard card, List<DeckCard> destroyed) {
        s.addChips(card.getRank().getChips());

        Enhancement e = card.getEnhancement();
        if (e != null) switch (e) {
            case BONUS -> s.addChips(30);
            case MULT  -> s.addMult(5);
            case GLASS -> {
                s.multiplyMult(X2);
                if (run.roll(RngSource.GLASS_BREAK, 1, 4) && !destroyed.contains(card)) {
                    destroyed.add(card);
                }
            }
            case STONE -> s.addChips(50);
            case LUCKY -> {
                if (run.roll(RngSource.LUCKY_MULT, 1, 5))   s.addMult(20);
                if (run.roll(RngSource.LUCKY_MONEY, 1, 10)) run.addMoney(10);
            }
            default -> { }   // WILD/STEEL/GOLD: nothing at scored time
        }

        s.applyEdition(card.getEdition());
        if (card.getSeal() == Seal.GOLD_SEAL) run.addMoney(3);
    }

    /** Applies one held card's enhancement (STEEL/STONE) to the session. */
    private void scoreHeldCard(ScoringSession s, DeckCard card) {
        Enhancement e = card.getEnhancement();
        if (e != null) switch (e) {
            case STEEL -> s.multiplyMult(X1_5);
            default -> { }
        }
    }

}