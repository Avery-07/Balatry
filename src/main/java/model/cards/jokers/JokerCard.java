package model.cards.jokers;

import model.cards.Card;
import model.cards.capability.Sellable;
import model.game.player.Run;
import model.game.scoring.Trigger;

public final class JokerCard extends Card implements Sellable {
    private final JokerSpec spec;
    private int counter; // per-instance scaling variable
    private int sellValue;

    public JokerCard(JokerSpec spec, int shopValue) {
        this.spec = spec;
        setShopValue(shopValue);
        setSellValue(shopValue / 2);
    }

    /** Applies the spec's effect for this trigger. Callers must check {@link #isDebuffed()} first; this does not. */
    public void trigger(Trigger trigger, Run run) {
        spec.effectFor(trigger).apply(run, this);
    }

    public JokerSpec getSpec()       { return spec; }
    public int getCounter()          { return counter; }
    public void setCounter(int value) { counter = value; }

    @Override public int getSellValue()        { return sellValue; }
    @Override public void setSellValue(int value) { sellValue = value; }
}