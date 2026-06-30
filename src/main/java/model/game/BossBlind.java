package model.game;

import model.cards.DeckCard;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.random.RandomGenerator;

/**
 * The Balatry boss blinds. The five vanilla suit/face-debuff blinds (Club, Goad, Window, Plant, Head) are removed;
 * The Water is retuned to −1 discard; and six new blinds are added (The Quartz, The Hivemind, The Commons,
 * The Bandwagon, The Mirage, The Shave). Each constant carries declarative effect data that the round/scoring
 * layer reads: a chip-target multiplier, hand/discard/hand-size changes, a per-card debuff predicate, a random
 * per-card debuff fraction, base chip/mult halving, play restrictions, and play-time hooks.
 *
 * <p>Effects that are purely visual in a model layer (cards drawn face down), that need state not yet tracked
 * (per-ante play history, per-hand joker disabling), or that resolve across players (see {@link #isCrossPlayer})
 * are present in the roster but inert, noted at their constant.
 *
 * <p>A boss may be disabled for a player by Chicot (owned) or Luchador (sold this round); the round/scoring layer
 * gates every effect on {@code run.effectiveBoss()}, so a disabled boss is simply absent.
 */
public enum BossBlind {

    THE_HOOK     ("The Hook",      b -> b.afterPlayDiscard(2).playTriggered()),
    THE_OX       ("The Ox",        b -> b.oxZeroMoney().playTriggered()),
    THE_HOUSE    ("The House",     b -> b),                         // first hand drawn face down — cosmetic in a model layer
    THE_WALL     ("The Wall",      b -> b.target(2)),               // 4× base chips (2× a normal boss)
    THE_WHEEL    ("The Wheel",     b -> b),                         // 1 in 5 cards drawn face down — cosmetic
    THE_ARM      ("The Arm",       b -> b.levelDownPlayed().playTriggered()),
    THE_FISH     ("The Fish",      b -> b),                         // cards drawn face down after each hand — cosmetic
    THE_PSYCHIC  ("The Psychic",   b -> b.mustPlayFive()),
    THE_WATER    ("The Water",     b -> b.discards(-1)),            // start the round with one fewer discard (Balatry tweak)
    THE_MANACLE  ("The Manacle",   b -> b.handSize(-1)),
    THE_EYE      ("The Eye",       b -> b.noRepeatType()),
    THE_MOUTH    ("The Mouth",     b -> b.singleType()),
    THE_SERPENT  ("The Serpent",   b -> b.fixedDraw(3)),
    THE_PILLAR   ("The Pillar",    b -> b),                         // debuff cards played earlier this ante — needs ante-scoped play history
    THE_NEEDLE   ("The Needle",    b -> b.singleHand()),
    THE_TOOTH    ("The Tooth",     b -> b.toothLoss().playTriggered()),
    THE_FLINT    ("The Flint",     b -> b.halveBase()),
    THE_MARK     ("The Mark",      b -> b),                         // face cards drawn face down (they still score) — cosmetic

    // New Balatry regular blinds. The Quartz is single-player and live. The cross-player ones carry declarative
    // metadata (see isCrossPlayer / dropsGlobalHighestHand) but resolve to no effect until a Match-level boss
    // resolver computes their aggregates and injects them per round; until then they behave as a free blind.
    THE_QUARTZ   ("The Quartz",    b -> b.randomDebuffOneIn(7)),    // ~1 in 7 of your cards are debuffed this round
    THE_HIVEMIND ("The Hivemind",  b -> b.crossPlayer()),           // debuff the most-played hand type across all players
    THE_COMMONS  ("The Commons",   b -> b.crossPlayer()),           // all players share one discard pool (sum of their discards)
    THE_BANDWAGON("The Bandwagon", b -> b.crossPlayer()),           // debuff the joker owned by the most players
    THE_MIRAGE   ("The Mirage",    b -> b.dropOwnHighest()),       // your highest-scoring hand is excluded from your final score
    THE_SHAVE    ("The Shave",     b -> b.crossPlayer().dropGlobalHighest()),  // only the single highest hand across all players is excluded

    // Finishers (base game: every 8th ante). Included for completeness; with antes 1-7 they are not selected.
    AMBER_ACORN  ("Amber Acorn",   b -> b.finisher()),              // flips/shuffles jokers — cosmetic/information effect
    VERDANT_LEAF ("Verdant Leaf",  b -> b.finisher().debuffUntilJokerSold()),
    VIOLET_VESSEL("Violet Vessel", b -> b.finisher().target(3)),    // 6× base chips (3× a normal boss)
    CRIMSON_HEART("Crimson Heart", b -> b.finisher()),              // disables a random joker each hand — needs per-hand joker disabling
    CERULEAN_BELL("Cerulean Bell", b -> b.finisher());             // forces one card to stay selected — a UI constraint

    private final String displayName;
    private int targetMultiplier = 1;
    private int handSizeDelta = 0;
    private int fixedDraw = 0;
    private int afterPlayDiscard = 0;
    private int discardDelta = 0;
    private int randomDebuffOneIn = 0;
    private boolean singleHand, halveBase, levelDownPlayed;
    private boolean mustPlayFive, noRepeatType, singleType;
    private boolean toothLoss, oxZero, debuffUntilSold, playTriggered, finisher;
    private boolean crossPlayer, dropsOwnHighest, dropsGlobalHighest;
    private Predicate<DeckCard> debuff = c -> false;

    BossBlind(String displayName, UnaryOperator<BossBlind> define) {
        this.displayName = displayName;
        define.apply(this);   // enum constants are singletons; the builder mutates this instance once
    }

    // --- builder (returns this) ---
    private BossBlind target(int m)                 { this.targetMultiplier = m; return this; }
    private BossBlind handSize(int delta)           { this.handSizeDelta = delta; return this; }
    private BossBlind fixedDraw(int n)              { this.fixedDraw = n; return this; }
    private BossBlind afterPlayDiscard(int n)       { this.afterPlayDiscard = n; return this; }
    private BossBlind discards(int delta)           { this.discardDelta = delta; return this; }
    private BossBlind randomDebuffOneIn(int n)      { this.randomDebuffOneIn = n; return this; }
    private BossBlind singleHand()                  { this.singleHand = true; return this; }
    private BossBlind halveBase()                   { this.halveBase = true; return this; }
    private BossBlind levelDownPlayed()             { this.levelDownPlayed = true; return this; }
    private BossBlind mustPlayFive()                { this.mustPlayFive = true; return this; }
    private BossBlind noRepeatType()                { this.noRepeatType = true; return this; }
    private BossBlind singleType()                  { this.singleType = true; return this; }
    private BossBlind toothLoss()                   { this.toothLoss = true; return this; }
    private BossBlind oxZeroMoney()                 { this.oxZero = true; return this; }
    private BossBlind debuffUntilJokerSold()        { this.debuffUntilSold = true; return this; }
    private BossBlind playTriggered()               { this.playTriggered = true; return this; }
    private BossBlind finisher()                    { this.finisher = true; return this; }
    private BossBlind crossPlayer()                 { this.crossPlayer = true; return this; }
    private BossBlind dropOwnHighest()              { this.dropsOwnHighest = true; return this; }
    private BossBlind dropGlobalHighest()           { this.dropsGlobalHighest = true; return this; }
    private BossBlind debuff(Predicate<DeckCard> p) { this.debuff = p; return this; }

    // --- accessors ---
    public String displayName()              { return displayName; }
    public int targetMultiplier()            { return targetMultiplier; }
    public int handSizeDelta()               { return handSizeDelta; }
    public int fixedDraw()                   { return fixedDraw; }
    public int afterPlayDiscard()            { return afterPlayDiscard; }
    public int discardDelta()                { return discardDelta; }
    public int randomDebuffOneIn()           { return randomDebuffOneIn; }
    public boolean oneHandOnly()             { return singleHand; }
    public boolean halvesBase()              { return halveBase; }
    public boolean levelsDownPlayed()        { return levelDownPlayed; }
    public boolean requiresFiveCards()       { return mustPlayFive; }
    public boolean forbidsRepeatType()       { return noRepeatType; }
    public boolean oneTypeOnly()             { return singleType; }
    public boolean zerosMoneyOnMostPlayed()  { return oxZero; }
    public boolean losesDollarPerCard()      { return toothLoss; }
    public boolean debuffsUntilJokerSold()   { return debuffUntilSold; }
    public boolean triggersOnPlay()          { return playTriggered; }
    public boolean isFinisher()              { return finisher; }

    /**
     * Whether this blind's effect is resolved across players rather than within one run (The Hivemind, The
     * Commons, The Bandwagon, The Shave). These carry their declarative intent here but are inert until a
     * Match-level boss resolver computes the relevant aggregate and applies it per round.
     */
    public boolean isCrossPlayer()           { return crossPlayer; }

    /** The Mirage: this player's single highest-scoring hand is excluded from their final (ranking) score. */
    public boolean dropsOwnHighestHand()     { return dropsOwnHighest; }

    /** The Shave: only the single highest-scoring hand across all players is excluded (cross-player Mirage). */
    public boolean dropsGlobalHighestHand()  { return dropsGlobalHighest; }

    /** Whether {@code card} is debuffed by this boss (suit/face bosses); the engine then skips it while scoring. */
    public boolean debuffs(DeckCard card) { return debuff.test(card); }

    private static final BossBlind[] REGULARS  = Arrays.stream(values()).filter(b -> !b.finisher).toArray(BossBlind[]::new);
    private static final BossBlind[] FINISHERS = Arrays.stream(values()).filter(b ->  b.finisher).toArray(BossBlind[]::new);

    /** Selects this ante's boss from the table-level stream (finishers on every 8th ante, regulars otherwise). */
    public static BossBlind select(RandomGenerator rng, int ante) {
        BossBlind[] pool = (ante % 8 == 0) ? FINISHERS : REGULARS;
        return pool[rng.nextInt(pool.length)];
    }

    /** Selects a boss for {@code ante}, never returning {@code exclude} (Metabole's reroll lands on a different boss). */
    public static BossBlind select(RandomGenerator rng, int ante, BossBlind exclude) {
        BossBlind[] pool = (ante % 8 == 0) ? FINISHERS : REGULARS;
        if (exclude == null || pool.length <= 1) return pool[rng.nextInt(pool.length)];
        BossBlind pick;
        do { pick = pool[rng.nextInt(pool.length)]; } while (pick == exclude);
        return pick;
    }
}
