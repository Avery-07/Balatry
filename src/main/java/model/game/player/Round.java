package model.game.player;

import model.cards.DeckCard;
import model.game.BossBlind;
import model.game.bosses.SharedDiscardPool;
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
    private HandType debuffedHandType;     // The Hivemind: this type scores no base chips/mult; set by the boss resolver
    private BigDecimal bestHandScore = BigDecimal.ZERO;   // highest single-hand score this round (Mirage/Shave exclusions)
    private DeckCard forcedCard;           // Cerulean Bell: must be included in every play and discard; null when inactive
    private boolean acted;                 // whether this seat has played or discarded (skipping requires an untouched round)

    Round(Run run, long target, int handSize, int hands, int discards, RandomGenerator shuffle) {
        this.run = run;
        this.target = target;
        this.handSize = handSize;
        this.handsRemaining = hands;
        this.discardsRemaining = discards;
        drawPile.addAll(run.getDeck());
        shuffle(drawPile, shuffle);
        draw();
        refreshForcedCard();   // Cerulean Bell: the deal's forced card
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
        if (boss != null && type == debuffedHandType) { baseChips = 0; baseMult = 0; }       // The Hivemind (gated on the live boss, so Luchador lifts it)

        // Matador reads this during scoring (Phase C), so set it before the engine runs.
        run.setBossTriggered(boss != null && boss.triggersOnPlay()
                && (!boss.zerosMoneyOnMostPlayed() || type == run.getStats().getMostPlayedHand()));

        ScoringResult result = ENGINE.score(run, eval.context(), baseChips, baseMult, eval.scoringCards(), heldAfterPlay);
        BigDecimal handScore = result.score();
        if (run.getMatch() != null)   // Lust: the diversity multiplier transforms the hand's final score
            handScore = run.getMatch().getSinModifier().adjustHandScore(run, type, handScore);

        acted = true;
        run.recordAntePlayed(cards);   // The Pillar: this ante's played cards (non-boss blinds only; see Run)

        if (!result.destroyed().isEmpty()) run.getStats().recordGlassDestroyed(result.destroyed().size());
        score = score.add(handScore);
        if (handScore.compareTo(bestHandScore) > 0) bestHandScore = handScore;
        hand.removeAll(cards);
        hand.removeAll(result.destroyed());
        run.destroyDeckCards(result.destroyed());   // glass breaks are permanent (hand removal above is idempotent)
        lastPlayedType = type;
        handsRemaining--;

        if (boss != null) applyBossAfterPlay(boss, type, cards.size());

        if (run.getMatch() != null)
            run.getMatch().getSinModifier().onHandScored(run, handScore);   // Greed: the chips-to-money ladder

        // Meeting the target no longer ends the round: chips fund the points share, so play continues while
        // hands remain. The round resolves when hands run out, or earlier via a voluntary finish().
        if (handsRemaining == 0) resolve();
        else {
            redraw();
            run.rollCrimsonHeart();   // Crimson Heart: a different joker is disabled for the next hand
        }

        return new PlayResult(type, handScore, score, result.destroyed());
    }

    /** Voluntarily ends the round, resolving the outcome from the banked score. */
    public void finish() {
        requireInProgress();
        resolve();
    }

    /** Skips this blind: only legal before the seat has played or discarded. */
    public void skip() {
        requireInProgress();
        if (acted) throw new IllegalStateException("cannot skip after playing or discarding");
        outcome = RoundOutcome.SKIPPED;
    }

    /** Resolves the terminal outcome from the banked score; Mr. Bones may turn a miss into a save. */
    private void resolve() {
        if (score.compareTo(BigDecimal.valueOf(target)) >= 0) outcome = RoundOutcome.WON;
        else outcome = run.tryPreventLoss() ? RoundOutcome.WON : RoundOutcome.LOST;   // Mr. Bones
    }

    /** Whether the banked score has reached the target (the round itself stays open while hands remain). */
    public boolean isTargetMet() { return score.compareTo(BigDecimal.valueOf(target)) >= 0; }

    /** Discards 1-5 cards from the hand and redraws; costs one discard (from The Commons' shared pool when active). */
    public void discard(List<DeckCard> cards) {
        requireInProgress();
        SharedDiscardPool shared = run.sharedDiscardPool();   // The Commons: the table's pool replaces the personal counter
        if (shared != null) {
            if (shared.getRemaining() <= 0) throw new IllegalStateException("no shared discards remaining");
        } else if (discardsRemaining <= 0) {
            throw new IllegalStateException("no discards remaining");
        }
        validateSelection(cards);

        acted = true;
        hand.removeAll(cards);
        if (shared != null) shared.consume();
        else discardsRemaining--;
        run.getStats().recordDiscard(cards);
        run.fireDiscard(cards);   // jokers (Faceless, Mail-In Rebate, ...) react to the discarded cards
        redraw();
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
        refreshForcedCard();   // Cerulean Bell: re-pick if the forced card left the hand
    }

    /** Cerulean Bell: keeps one random hand card forced into every play and discard. */
    private void refreshForcedCard() {
        BossBlind boss = run.effectiveBoss();
        if (boss == null || !boss.forcesCardSelection()) { forcedCard = null; return; }
        if (forcedCard != null && containsIdentity(hand, forcedCard)) return;
        forcedCard = hand.isEmpty() ? null : hand.get(run.bossEffectStream().nextInt(hand.size()));
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
            RandomGenerator r = run.getRng().streamFor(RngSource.BOSS_EFFECT, run.nextSalt(RngSource.BOSS_EFFECT));
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
        if (forcedCard != null && !containsIdentity(cards, forcedCard) && run.effectiveBoss() != null)
            throw new IllegalStateException("the forced card must be part of every play and discard (Cerulean Bell)");
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
    /** Discards left: The Commons' shared pool when active for this seat, otherwise the personal counter. */
    public int getDiscardsRemaining() {
        SharedDiscardPool shared = run.sharedDiscardPool();
        return shared != null ? shared.getRemaining() : discardsRemaining;
    }

    /** The highest single-hand score banked this round (feeds the Mirage/Shave exclusions and BlindResult). */
    public BigDecimal getBestHandScore() { return bestHandScore; }

    /** The hand type debuffed this round (zero base chips/mult when played), or null. Set by the boss resolver. */
    public HandType getDebuffedHandType() { return debuffedHandType; }

    /** The Hivemind: debuffs {@code type} for this round. Called by the Match-level boss resolver at the deal. */
    public void setDebuffedHandType(HandType type) { this.debuffedHandType = type; }

    /** Cerulean Bell: the card every play and discard must include, or null when the constraint is inactive. */
    public DeckCard getForcedCard() { return forcedCard; }
    public RoundOutcome getOutcome()  { return outcome; }

    /** The most recently played hand type, or {@code null} if none yet (used by Blue Seal at cash-out). */
    public HandType getLastPlayedType() { return lastPlayedType; }
}