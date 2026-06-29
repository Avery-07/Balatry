package model.game.player;

import model.cards.DeckCard;
import model.game.BossBlind;
import model.game.rng.RngSource;
import model.game.scoring.HandEvaluation;
import model.game.scoring.HandEvaluator;
import model.game.scoring.HandType;
import model.game.scoring.ScoringEngine;
import model.game.scoring.ScoringResult;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/** One player's play against a single blind: draw pile, hand, remaining hands/discards, banked score, and outcome. */
public final class Round {

    private static final int MAX_SELECTION = 5;
    private static final ScoringEngine ENGINE = new ScoringEngine();

    private final Run run;
    private final HandEvaluator evaluator = new HandEvaluator();   // vanilla; will later be derived from run.getJokers()
    private final long target;
    private final int handSize;
    private final List<DeckCard> drawPile = new ArrayList<>();
    private final List<DeckCard> hand = new ArrayList<>();
    private int handsRemaining;
    private int discardsRemaining;
    private BigDecimal score = BigDecimal.ZERO;
    private RoundOutcome outcome = RoundOutcome.IN_PROGRESS;
    private HandType lastPlayedType;   // for Blue Seal at cash-out; null until a hand is played
    private HandType firstTypeThisRound;   // for The Mouth (only this type playable this round)

    Round(Run run, long target, int handSize, int hands, int discards, RandomGenerator shuffle) {
        this.run = run;
        this.target = target;
        this.handSize = handSize;
        this.handsRemaining = hands;
        this.discardsRemaining = discards;
        drawPile.addAll(run.getDeck());
        shuffle(drawPile, shuffle);
        draw();
    }

    /** Plays 1-5 cards from the hand: evaluates, scores, banks, removes them, redraws, and updates the outcome. */
    public PlayResult play(List<DeckCard> cards) {
        requireInProgress();
        if (handsRemaining <= 0) throw new IllegalStateException("no hands remaining");
        validateSelection(cards);

        HandEvaluation eval = evaluator.evaluate(cards);
        HandType type = eval.type();

        BossBlind boss = run.effectiveBoss();
        if (boss != null) enforceBossRestrictions(boss, cards, type);

        run.getStats().recordHandPlayed(type);   // before scoring: ON_HAND_PLAYED jokers see the current play counted
        if (firstTypeThisRound == null) firstTypeThisRound = type;
        List<DeckCard> heldAfterPlay = new ArrayList<>(hand);
        heldAfterPlay.removeAll(cards);

        if (boss != null && boss.levelsDownPlayed()) run.getHandLevels().levelDown(type);   // The Arm

        long baseChips = run.getHandLevels().chipsFor(type);
        long baseMult  = run.getHandLevels().multFor(type);
        if (boss != null && boss.halvesBase()) { baseChips /= 2; baseMult /= 2; }            // The Flint

        // Matador reads this during scoring (Phase C), so set it before the engine runs.
        run.setBossTriggered(boss != null && boss.triggersOnPlay()
                && (!boss.zerosMoneyOnMostPlayed() || type == run.getStats().getMostPlayedHand()));

        ScoringResult result = ENGINE.score(run, eval.context(), baseChips, baseMult, eval.scoringCards(), heldAfterPlay);

        if (!result.destroyed().isEmpty()) run.getStats().recordGlassDestroyed(result.destroyed().size());
        score = score.add(result.score());
        hand.removeAll(cards);
        hand.removeAll(result.destroyed());
        run.getDeck().removeAll(result.destroyed());   // glass breaks are permanent
        lastPlayedType = type;
        handsRemaining--;

        if (boss != null) applyBossAfterPlay(boss, type, cards.size());

        updateOutcome();
        if (outcome == RoundOutcome.IN_PROGRESS) redraw();   // no redraw once the blind is cleared

        return new PlayResult(type, result.score(), score, result.destroyed());
    }

    /** Discards 1-5 cards from the hand and redraws; costs one discard, never ends the round. */
    public void discard(List<DeckCard> cards) {
        requireInProgress();
        if (discardsRemaining <= 0) throw new IllegalStateException("no discards remaining");
        validateSelection(cards);

        hand.removeAll(cards);
        discardsRemaining--;
        run.getStats().recordDiscard(cards);
        run.fireDiscard(cards);   // jokers (Faceless, Mail-In Rebate, ...) react to the discarded cards
        redraw();
    }

    private void updateOutcome() {
        if (score.compareTo(BigDecimal.valueOf(target)) >= 0) outcome = RoundOutcome.WON;
        else if (handsRemaining == 0)                          outcome = run.tryPreventLoss() ? RoundOutcome.WON : RoundOutcome.LOST;   // Mr. Bones
    }

    private void draw() {
        while (hand.size() < handSize && !drawPile.isEmpty()) {
            hand.add(drawPile.remove(drawPile.size() - 1));
        }
    }

    /** Post-action draw: The Serpent always draws a fixed count; otherwise refill to hand size. */
    private void redraw() {
        BossBlind boss = run.effectiveBoss();
        if (boss != null && boss.fixedDraw() > 0) {
            for (int i = 0; i < boss.fixedDraw() && !drawPile.isEmpty(); i++)
                hand.add(drawPile.remove(drawPile.size() - 1));
        } else {
            draw();
        }
    }

    /** Boss play restrictions (The Psychic / The Eye / The Mouth); throws if the play is not allowed. */
    private void enforceBossRestrictions(BossBlind boss, List<DeckCard> cards, HandType type) {
        if (boss.requiresFiveCards() && cards.size() != 5)
            throw new IllegalStateException("The Psychic: must play exactly 5 cards");
        if (boss.forbidsRepeatType() && run.getStats().getHandPlaysThisRound(type) > 0)
            throw new IllegalStateException("The Eye: hand type already played this round");
        if (boss.oneTypeOnly() && firstTypeThisRound != null && type != firstTypeThisRound)
            throw new IllegalStateException("The Mouth: only " + firstTypeThisRound + " may be played this round");
    }

    /** Boss effects applied after a hand scores: The Ox, The Tooth, The Hook. */
    private void applyBossAfterPlay(BossBlind boss, HandType type, int cardsPlayed) {
        if (boss.zerosMoneyOnMostPlayed() && type == run.getStats().getMostPlayedHand())
            run.addMoney(-run.getMoney());                 // The Ox
        if (boss.losesDollarPerCard())
            run.addMoney(-cardsPlayed);                    // The Tooth
        int n = boss.afterPlayDiscard();                   // The Hook
        if (n > 0) {
            RandomGenerator r = run.getRng().streamFor(RngSource.MISC, run.nextSalt(RngSource.MISC));
            for (int i = 0; i < n && !hand.isEmpty(); i++) hand.remove(r.nextInt(hand.size()));
        }
    }

    private void validateSelection(List<DeckCard> cards) {
        if (cards == null || cards.isEmpty() || cards.size() > MAX_SELECTION)
            throw new IllegalArgumentException("a play/discard takes 1-" + MAX_SELECTION + " cards, got "
                    + (cards == null ? 0 : cards.size()));
        for (DeckCard c : cards)
            if (!containsIdentity(hand, c)) throw new IllegalArgumentException("card is not in hand");
        for (int i = 0; i < cards.size(); i++)
            for (int j = i + 1; j < cards.size(); j++)
                if (cards.get(i) == cards.get(j)) throw new IllegalArgumentException("a card appears twice in the selection");
    }

    private void requireInProgress() {
        if (outcome != RoundOutcome.IN_PROGRESS) throw new IllegalStateException("round is over: " + outcome);
    }

    private static boolean containsIdentity(List<DeckCard> list, DeckCard card) {
        for (DeckCard c : list) if (c == card) return true;
        return false;
    }

    /** Removes {@code card} (by identity) from the hand; used by destructive consumables (e.g. The Hanged Man). */
    void removeFromHand(DeckCard card) { hand.removeIf(c -> c == card); }

    /** Adds {@code card} to the hand; used by card-creating spectrals (Familiar, Cryptid, ...). */
    void addToHand(DeckCard card) { hand.add(card); }

    /** Seeded Fisher-Yates, so identical decks shuffle identically on a shared seed. */
    private static void shuffle(List<DeckCard> cards, RandomGenerator rng) {
        for (int i = cards.size() - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            DeckCard tmp = cards.get(i); cards.set(i, cards.get(j)); cards.set(j, tmp);
        }
    }

    public List<DeckCard> getHand()   { return List.copyOf(hand); }
    public List<DeckCard> getDrawPile() { return List.copyOf(drawPile); }
    public BigDecimal getScore()      { return score; }
    public long getTarget()           { return target; }
    public int getHandsRemaining()    { return handsRemaining; }
    public int getDiscardsRemaining() { return discardsRemaining; }
    public RoundOutcome getOutcome()  { return outcome; }

    /** The most recently played hand type, or {@code null} if none yet (used by Blue Seal at cash-out). */
    public HandType getLastPlayedType() { return lastPlayedType; }
}