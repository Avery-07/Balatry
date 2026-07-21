package model.items.jokers;

import model.game.scoring.Trigger;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class JokerSpec {
    private final String name;
    private final String description;
    private final Rarity rarity;
    private final Map<Trigger, JokerEffect> effects;
    private final Set<JokerTrait> traits;          // special-interaction capabilities (queried, not fired)
    private final CardRetrigger playedRetrigger;   // extra passes this joker grants a played card
    private final CardRetrigger heldRetrigger;     // extra passes this joker grants a held card
    private final int debtAllowance;               // how far below $0 this joker lets the balance go
    private final int cost;                        // base shop price
    private final Function<JokerCard, String> state;   // live-variable text for the tooltip, or null

    private JokerSpec(Builder b) {
        this.name = b.name;
        this.description = b.description;
        this.rarity = b.rarity;
        this.effects = Map.copyOf(b.effects);
        this.traits = b.traits.isEmpty() ? EnumSet.noneOf(JokerTrait.class) : EnumSet.copyOf(b.traits);
        this.playedRetrigger = b.playedRetrigger;
        this.heldRetrigger = b.heldRetrigger;
        this.debtAllowance = b.debtAllowance;
        this.cost = b.cost;
        this.state = b.state;
    }

    /**
     * The live value this joker instance is playing with, as tooltip text — the counter given meaning. This
     * exists because some jokers make a hidden pick (Mail-In Rebate's rank, The Idol's card) or accumulate a
     * bonus (Ride the Bus) that the player cannot act on without seeing it. Specs that declared a state renderer
     * get their own words; any other joker with a non-zero counter falls back to a bare "Value: n"; jokers with
     * no live state return null and the tooltip shows nothing.
     */
    public String stateOf(JokerCard card) {
        if (state != null) return state.apply(card);
        return card.getCounter() != 0 ? "Value: " + card.getCounter() : null;
    }

    public JokerEffect effectFor(Trigger t) {
        return effects.getOrDefault(t, JokerEffect.NO_OP);
    }

    /** Whether this spec declares {@code trait} (does not consider debuff state — see {@link JokerCard#hasActiveTrait}). */
    public boolean has(JokerTrait trait) { return traits.contains(trait); }

    public CardRetrigger getPlayedRetrigger() { return playedRetrigger; }
    public CardRetrigger getHeldRetrigger()   { return heldRetrigger; }
    public int getDebtAllowance()             { return debtAllowance; }
    public int getCost()                      { return cost; }

    public static Builder named(String name, Rarity rarity) {
        return new Builder(name, rarity);
    }

    public static final class Builder {
        private final String name;
        private String description = "";
        private final Rarity rarity;
        private final Map<Trigger, JokerEffect> effects = new EnumMap<>(Trigger.class);
        private final Set<JokerTrait> traits = EnumSet.noneOf(JokerTrait.class);
        private CardRetrigger playedRetrigger = CardRetrigger.NONE;
        private CardRetrigger heldRetrigger = CardRetrigger.NONE;
        private int debtAllowance = 0;
        private int cost = 0;
        private Function<JokerCard, String> state;
        private Builder(String name, Rarity rarity) { this.name = name; this.rarity = rarity; }
        /** Sets the human-readable effect text shown on hover (optional; defaults to none). */
        public Builder description(String d) { this.description = d; return this; }
        /** Renders this joker's live variable for the tooltip (optional; see {@link JokerSpec#stateOf}). */
        public Builder state(Function<JokerCard, String> s) { this.state = s; return this; }
        public Builder on(Trigger t, JokerEffect e) { effects.put(t, e); return this; }
        public Builder trait(JokerTrait... ts)          { for (JokerTrait t : ts) traits.add(t); return this; }
        public Builder retriggerPlayed(CardRetrigger r) { this.playedRetrigger = r; return this; }
        public Builder retriggerHeld(CardRetrigger r)   { this.heldRetrigger = r; return this; }
        public Builder debtAllowance(int dollars)       { this.debtAllowance = dollars; return this; }
        public Builder cost(int dollars)                { this.cost = dollars; return this; }
        public JokerSpec build() { return new JokerSpec(this); }
    }

    public String getName() { return name; }
    /** Human-readable effect text for the UI; empty when none has been authored yet. */
    public String getDescription() { return description; }
    public Rarity getRarity() { return rarity; }
    public Map<Trigger, JokerEffect> getEffects() { return effects; }

    @Override
    public String toString() {
        return "JokerSpec[name=" + name + ", rarity=" + rarity + "]";
    }
}