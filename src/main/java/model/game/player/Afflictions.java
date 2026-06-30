package model.game.player;

import model.cards.DeckCard;
import model.cards.DeckCard.Rank;
import model.cards.DeckCard.Suit;
import model.cards.jokers.JokerCard;
import model.modifiers.Sticker;

import java.util.List;

/**
 * Relic-imposed state living beside a {@link Run}, like {@link PlayerStats} and {@link HandLevels}: the
 * debuffs and shields other players (or the owner) have placed on this seat. Three timescales:
 *
 * <ul>
 *   <li><b>Round-scoped debuffs</b> (Anathema rank, Miasma suit, Katadesmos joker). Armed between rounds as
 *       {@code pending}; promoted to {@code active} at the next {@link #beginRound}; cleared at {@link #endRound}.
 *       Rank/suit are consulted by the scoring engine via {@link #debuffs}; the joker debuff is applied as a
 *       {@link Sticker#DEBUFFED} sticker at round start (and removed at round end only if this object added it,
 *       so a genuine debuff is never stripped).</li>
 *   <li><b>Next-shop debuff</b> (Limos). Armed as {@code pending}; promoted at the next {@link #beginShop};
 *       read by the shop while filling its first slot; cleared at {@link #endShop}.</li>
 *   <li><b>Per-ante shield</b> (Aegis). Armed by the owner; consumed by the first hostile effect that lands;
 *       reset at {@link #beginAnte}.</li>
 * </ul>
 *
 * <p>Suit matching mirrors the boss suit-debuff semantics (a WILD card counts as every suit), so Miasma and a
 * suit boss treat cards identically.
 */
public final class Afflictions {

    private Rank pendingRank, activeRank;
    private Suit pendingSuit, activeSuit;
    private int pendingJokerIndex = -1;
    private JokerCard debuffedJoker;          // the joker this object stickered for the active round, or null
    private boolean addedJokerSticker;        // whether the DEBUFFED sticker on debuffedJoker came from here

    private boolean pendingFirstSlot, activeFirstSlot;
    private boolean aegisArmed;

    // --- arming (called by relic effects, between rounds) ---

    /** Anathema: debuff {@code rank} for this seat during its next round. */
    public void armRankDebuff(Rank rank) { if (rank != null) pendingRank = rank; }

    /** Miasma: debuff {@code suit} for this seat during its next round. */
    public void armSuitDebuff(Suit suit) { if (suit != null) pendingSuit = suit; }

    /** Katadesmos: debuff the joker at board position {@code index} during this seat's next round. */
    public void armJokerDebuff(int index) { if (index >= 0) pendingJokerIndex = index; }

    /** Limos: debuff the first slot of this seat's next shop. */
    public void armFirstSlotDebuff() { pendingFirstSlot = true; }

    /** Aegis: arm a shield that negates the next hostile effect aimed at this seat this ante. */
    public void armAegis() { aegisArmed = true; }

    // --- Aegis ---

    /** Whether a shield is currently armed. */
    public boolean isAegisArmed() { return aegisArmed; }

    /** If a shield is armed, disarm it and report that it absorbed an effect; otherwise report no absorption. */
    public boolean consumeAegis() {
        if (!aegisArmed) return false;
        aegisArmed = false;
        return true;
    }

    // --- round-scoped card debuffs ---

    /** Whether {@code card} is debuffed by an active rank/suit relic this round (consulted by the scoring engine). */
    public boolean debuffs(DeckCard card) {
        if (activeRank != null && card.getRank() == activeRank) return true;
        if (activeSuit == null) return false;
        return switch (activeSuit) {                 // WILD-inclusive, matching boss suit debuffs
            case SPADES   -> card.isSpade();
            case HEARTS   -> card.isHeart();
            case CLUBS    -> card.isClub();
            case DIAMONDS -> card.isDiamond();
        };
    }

    // --- lifecycle (called by Run) ---

    /** Promotes pending round debuffs to active and applies the joker sticker against {@code jokers}. */
    public void beginRound(List<JokerCard> jokers) {
        activeRank = pendingRank; pendingRank = null;
        activeSuit = pendingSuit; pendingSuit = null;
        if (pendingJokerIndex >= 0 && pendingJokerIndex < jokers.size()) {
            JokerCard j = jokers.get(pendingJokerIndex);
            if (!j.isDebuffed()) {                    // don't double-apply; only strip what we add
                j.apply(Sticker.DEBUFFED);
                debuffedJoker = j;
                addedJokerSticker = true;
            }
        }
        pendingJokerIndex = -1;
    }

    /** Clears active round debuffs and removes the joker sticker this object added. */
    public void endRound() {
        activeRank = null;
        activeSuit = null;
        if (addedJokerSticker && debuffedJoker != null) debuffedJoker.remove(Sticker.DEBUFFED);
        debuffedJoker = null;
        addedJokerSticker = false;
    }

    /** Promotes a pending Limos debuff to active for the shop about to open. */
    public void beginShop() { activeFirstSlot = pendingFirstSlot; pendingFirstSlot = false; }

    /** Clears the active Limos debuff when the shop closes. */
    public void endShop() { activeFirstSlot = false; }

    /** Whether the first slot of the open shop is debuffed (read by the shop while filling). */
    public boolean isFirstSlotDebuffed() { return activeFirstSlot; }

    /** Resets the per-ante shield; call at the start of each ante. */
    public void beginAnte() { aegisArmed = false; }
}
