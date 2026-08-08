package model.game.sins;

import model.items.Card;
import model.items.jokers.JokerCard;
import model.game.player.PlayerId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Table-level, ante-scoped state owned by the active sin — the match analogue of the per-player {@link SinState}. */
public final class SinTableState {

    private final Map<PlayerId, Integer> gluttonyUses = new LinkedHashMap<>();
    private int gluttonyGauge;
    private final Map<String, PlayerId> greedClaims = new LinkedHashMap<>();   // Greed: item identity -> first buyer
    private final List<EnvyPurchase> envyLog = new ArrayList<>();              // Envy: this phase's copyable purchases
    private JokerCard prideLegendary;                                          // Pride: this phase's auctioned joker
    private final Map<PlayerId, Integer> prideBids = new LinkedHashMap<>();    // Pride: standing bids, insertion order

    /** Resets all table-scoped sin state; called by the Match when an ante's sin refreshes. */
    public void beginAnte() { clearGluttony(); clearGreedClaims(); clearEnvyLog(); clearPrideAuction(); }

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

    // --- Greed: items claimed this shop phase; mirrored copies elsewhere are debuffed ---

    /** Records that {@code buyer} bought the item with {@code identity}; the first buyer keeps the claim. */
    public void recordGreedClaim(String identity, PlayerId buyer) { greedClaims.putIfAbsent(identity, buyer); }

    /** Every claim this shop phase, in purchase order: item identity to first buyer. */
    public Map<String, PlayerId> getGreedClaims() { return Collections.unmodifiableMap(greedClaims); }

    /** Empties the claims (each round begin: claims live for one shop phase). */
    public void clearGreedClaims() { greedClaims.clear(); }

    // --- Envy: what everyone bought this shop phase, copyable at twice the price paid ---

    /** One copyable purchase: who bought what, and what they actually paid. */
    public record EnvyPurchase(PlayerId buyer, Card item, int pricePaid) { }

    public void recordEnvyPurchase(PlayerId buyer, Card item, int pricePaid) {
        envyLog.add(new EnvyPurchase(buyer, item, pricePaid));
    }

    /** Every purchase this shop phase, in order — openly visible ("players see what others bought"). */
    public List<EnvyPurchase> getEnvyLog() { return Collections.unmodifiableList(envyLog); }

    public void clearEnvyLog() { envyLog.clear(); }

    // --- Pride: the shop phase's legendary auction ---

    public JokerCard getPrideLegendary() { return prideLegendary; }
    public void setPrideLegendary(JokerCard joker) { prideLegendary = joker; }

    /** Adds {@code amount} to {@code id}'s total paid into the auction (all-pay: the money is already spent). */
    public void addPrideBid(PlayerId id, int amount) { prideBids.merge(id, amount, Integer::sum); }

    /** Each seat's total paid so far, in first-bid order (a blind auction — never shown to rivals). */
    public Map<PlayerId, Integer> getPrideBids() { return Collections.unmodifiableMap(prideBids); }

    public void clearPrideAuction() { prideLegendary = null; prideBids.clear(); }
}
