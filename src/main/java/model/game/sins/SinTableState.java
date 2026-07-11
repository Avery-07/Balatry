package model.game.sins;

import model.game.player.PlayerId;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Table-level, ante-scoped state owned by the active sin — the match analogue of the per-player
 * {@link SinState}. The Match resets it whenever an ante's sin refreshes, so no sin ever reads another's
 * leftovers. Currently hosts Gluttony's communal gauge: the pool of minted dollars and each seat's consumption
 * tally. Both are openly readable — the sin's design says players can see how much everyone consumes.
 */
public final class SinTableState {

    private final Map<PlayerId, Integer> gluttonyUses = new LinkedHashMap<>();
    private int gluttonyGauge;

    /** Resets all table-scoped sin state; called by the Match when an ante's sin refreshes. */
    public void beginAnte() { clearGluttony(); }

    /** Records one consumable use by {@code id}, minting {@code gaugeContribution} dollars into the pool. */
    public void recordGluttonyUse(PlayerId id, int gaugeContribution) {
        gluttonyUses.merge(id, 1, Integer::sum);
        gluttonyGauge += gaugeContribution;
    }

    /** The communal pool minted so far this ante. */
    public int getGluttonyGauge() { return gluttonyGauge; }

    /** How many consumables {@code id} has used this ante. */
    public int gluttonyUses(PlayerId id) { return gluttonyUses.getOrDefault(id, 0); }

    /** Every seat's consumption tally so far this ante, in first-use order. */
    public Map<PlayerId, Integer> getGluttonyUses() { return Collections.unmodifiableMap(gluttonyUses); }

    /** Empties the gauge and tallies (after a payout, or at ante begin). */
    void clearGluttony() { gluttonyUses.clear(); gluttonyGauge = 0; }
}
