package model.game;

import model.items.DeckCard;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.random.RandomGenerator;

/** The Balatry boss blinds. The five vanilla suit/face-debuff blinds (Club, Goad, Window, Plant, Head) are removed; The Water is retuned to −1 discard; and six new blinds are added (The Quartz, The Hivemind, The Commons, The Bandwagon, The Mirage, The Shave). */
public enum BossBlind {

    THE_HOOK     ("The Hook",      "Discards 2 random held cards after each hand played", b -> b.afterPlayDiscard(2).playTriggered()),
    THE_OX       ("The Ox",        "Playing your most-played hand type sets your money to $0", b -> b.oxZeroMoney().playTriggered()),
    THE_HOUSE    ("The House",     "First hand is drawn face down", b -> b),
    THE_WALL     ("The Wall",      "Extra-large blind (4× base chips)", b -> b.target(2)),
    THE_WHEEL    ("The Wheel",     "1 in 5 cards drawn face down", b -> b),
    THE_ARM      ("The Arm",       "Levels down the hand type you play", b -> b.levelDownPlayed().playTriggered()),
    THE_FISH     ("The Fish",      "Cards drawn face down after each hand", b -> b),
    THE_PSYCHIC  ("The Psychic",   "You must play 5 cards each hand", b -> b.mustPlayFive()),
    THE_WATER    ("The Water",     "Start the round with 1 fewer discard", b -> b.discards(-1)),
    THE_MANACLE  ("The Manacle",   "−1 hand size this round", b -> b.handSize(-1)),
    THE_EYE      ("The Eye",       "No repeat hand types this round", b -> b.noRepeatType()),
    THE_MOUTH    ("The Mouth",     "Only one hand type may be played", b -> b.singleType()),
    THE_SERPENT  ("The Serpent",   "Always draw 3 cards after a play or discard", b -> b.fixedDraw(3)),
    THE_PILLAR   ("The Pillar",    "Cards played in this ante's earlier blinds are debuffed", b -> b.debuffAntePlayed()),
    THE_NEEDLE   ("The Needle",    "Only one hand", b -> b.singleHand()),
    THE_TOOTH    ("The Tooth",     "Lose $1 per card played", b -> b.toothLoss().playTriggered()),
    THE_FLINT    ("The Flint",     "Base chips and mult are halved", b -> b.halveBase()),
    THE_MARK     ("The Mark",      "Face cards are drawn face down", b -> b),

    // New Balatry regular blinds. The Quartz and The Mirage are single-player, resolved in Round/RoundSettlement.
    THE_QUARTZ   ("The Quartz",    "~1 in 7 of your cards are debuffed this round", b -> b.randomDebuffOneIn(7)),
    THE_HIVEMIND ("The Hivemind",  "Debuffs the most-played hand type across all players", b -> b.crossPlayer()),
    THE_COMMONS  ("The Commons",   "All players share one discard pool", b -> b.crossPlayer()),
    THE_BANDWAGON("The Bandwagon", "Debuffs the joker owned by the most players", b -> b.crossPlayer()),
    THE_MIRAGE   ("The Mirage",    "Your highest-scoring hand is excluded from your score", b -> b.dropOwnHighest()),
    THE_SHAVE    ("The Shave",     "Only the single highest hand across all players is excluded", b -> b.crossPlayer().dropGlobalHighest()),

    // Finishers (base game: every 8th ante). Included for completeness; with antes 1-7 they are not selected.
    AMBER_ACORN  ("Amber Acorn",   "Joker board order is randomized at the deal", b -> b.finisher().shuffleJokers()),
    VERDANT_LEAF ("Verdant Leaf",  "All cards are debuffed until a joker is sold", b -> b.finisher().debuffUntilJokerSold()),
    VIOLET_VESSEL("Violet Vessel", "Extra-large blind (6× base chips)", b -> b.finisher().target(3)),
    CRIMSON_HEART("Crimson Heart", "A different random joker is disabled each hand", b -> b.finisher().disableJokerPerHand()),
    CERULEAN_BELL("Cerulean Bell", "One card is forced into every play and discard", b -> b.finisher().forceCardSelection());

    private final String displayName;
    private final String effect;
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
    private boolean debuffAntePlayed, jokerPerHand, forcedSelection, shuffleJokers;
    private Predicate<DeckCard> debuff = c -> false;

    BossBlind(String displayName, String effect, UnaryOperator<BossBlind> define) {
        this.displayName = displayName;
        this.effect = effect;
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
    private BossBlind debuffAntePlayed()            { this.debuffAntePlayed = true; return this; }
    private BossBlind disableJokerPerHand()         { this.jokerPerHand = true; return this; }
    private BossBlind forceCardSelection()          { this.forcedSelection = true; return this; }
    private BossBlind shuffleJokers()               { this.shuffleJokers = true; return this; }
    private BossBlind debuff(Predicate<DeckCard> p) { this.debuff = p; return this; }

    // --- accessors ---
    public String displayName()              { return displayName; }
    /** A short, human-readable description of this boss's effect, for the blind-selection screen. */
    public String effect()                   { return effect; }
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

    /** Whether this blind's effect is resolved across players rather than within one run (The Hivemind, The Commons, The Bandwagon, The Shave); such blinds are resolved by their {@code model.game.bosses} behaviour, created per boss round via {@code BossBehaviors.behaviorFor}. */
    public boolean isCrossPlayer()           { return crossPlayer; }

    /** The Mirage: this player's single highest-scoring hand is excluded from their final (ranking) score. */
    public boolean dropsOwnHighestHand()     { return dropsOwnHighest; }

    /** The Shave: only the single highest-scoring hand across all players is excluded (cross-player Mirage). */
    public boolean dropsGlobalHighestHand()  { return dropsGlobalHighest; }

    /** The Pillar: cards played in this ante's earlier (non-boss) blinds are debuffed; consulted via Run#bossDebuffs. */
    public boolean debuffsAntePlayed()       { return debuffAntePlayed; }

    /** Crimson Heart: a different random joker is disabled each hand (rolled at the deal and after every play). */
    public boolean disablesJokerPerHand()    { return jokerPerHand; }

    /** Cerulean Bell: one random card in hand is forced into every play and discard, re-picked when it leaves. */
    public boolean forcesCardSelection()     { return forcedSelection; }

    /** Amber Acorn: the joker board order is randomized at the deal (adjacency effects feel it). */
    public boolean shufflesJokers()          { return shuffleJokers; }

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
