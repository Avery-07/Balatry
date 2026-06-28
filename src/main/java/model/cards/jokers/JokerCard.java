package model.cards.jokers;

import model.cards.MarketCard;
import model.game.player.Run;
import model.game.scoring.Trigger;

public final class JokerCard extends MarketCard {
    private final JokerSpec spec;
    private int counter; // per-instance scaling variable

    public JokerCard(JokerSpec spec, int shopValue) {
        this.spec = spec;
        price(shopValue);
    }

    /** Prices the joker from its spec — the catalog path, matching consumables and vouchers. */
    public JokerCard(JokerSpec spec) {
        this(spec, spec.getCost());
    }

    /** Applies the spec's effect for this trigger. Callers must check {@link #isDebuffed()} first; this does not. */
    public void trigger(Trigger trigger, Run run) {
        spec.effectFor(trigger).apply(run, this);
    }

    /** Extra passes this joker grants {@code card} when it is a played scoring card. */
    public int playedRetriggers(Run run, model.cards.DeckCard card) { return spec.getPlayedRetrigger().extra(run, this, card); }

    /** Extra passes this joker grants {@code card} when it is held in hand. */
    public int heldRetriggers(Run run, model.cards.DeckCard card)   { return spec.getHeldRetrigger().extra(run, this, card); }

    public JokerSpec getSpec()       { return spec; }
    public int getCounter()          { return counter; }
    public void setCounter(int value) { counter = value; }

    public void addCounter(int i) {
        counter += i;
    }
}