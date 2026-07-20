package model.game.player;

import model.items.DeckCard;
import model.items.jokers.JokerCard;
import model.game.BossBlind;
import model.modifiers.Sticker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Boss-imposed state living beside a {@link Run}, like {@link PlayerStats} and {@link Afflictions}: which boss is active and whether this seat disabled it, plus every piece of per-round boss bookkeeping — The Quartz's debuffed cards (restored at round end), The Pillar's ante-played set (cleared at ante start), Crimson Heart's disabled joker (sticker owned here, mirroring the Katadesmos convention), Verdant Leaf's sold flag, and the per-play boss-triggered flag Matador reads. */
public final class BossState {

    private BossBlind activeBoss;          // the boss for the current blind, or null (small/big blinds, headless rounds)
    private boolean luchadorDisable;       // per-round: Luchador was sold, disabling the boss for this player
    private boolean verdantSold;           // per-round: a joker was sold (lifts Verdant Leaf's all-cards debuff)
    private boolean bossTriggered;         // per-play: the boss ability fired this hand (read by Matador)

    private final List<DeckCard> quartzDebuffed = new ArrayList<>();   // cards The Quartz debuffed this round
    private final Set<DeckCard> antePlayed = new HashSet<>();          // The Pillar: identity set of this ante's non-boss plays
    private JokerCard crimsonJoker;        // Crimson Heart: the joker currently disabled, or null
    private boolean crimsonStickerAdded;   // whether the DEBUFFED sticker on crimsonJoker came from here

    BossState() {}

    // --- the active boss and per-player disabling ---

    public BossBlind getActiveBoss() { return activeBoss; }
    void setActiveBoss(BossBlind boss) { activeBoss = boss; }

    boolean isLuchadorDisabled() { return luchadorDisable; }
    void disableForRound()       { luchadorDisable = true; }

    // --- Verdant Leaf / Matador flags ---

    boolean jokerSoldThisRound() { return verdantSold; }
    void noteJokerSold()         { verdantSold = true; }

    public boolean isBossTriggered()   { return bossTriggered; }
    public void setBossTriggered(boolean v) { bossTriggered = v; }

    // --- The Quartz ---

    void noteQuartzDebuffed(DeckCard card) { quartzDebuffed.add(card); }

    // --- The Pillar ---

    /** Records {@code cards} as played this ante; only non-boss blinds count ("earlier blinds"). */
    void recordAntePlayed(List<DeckCard> cards) {
        if (activeBoss == null) antePlayed.addAll(cards);
    }

    boolean wasPlayedThisAnte(DeckCard card) { return antePlayed.contains(card); }

    // --- Crimson Heart (the sticker is owned here; only what this object added is stripped) ---

    /** The currently disabled joker, or null. */
    public JokerCard getCrimsonDisabledJoker() { return crimsonJoker; }

    JokerCard clearCrimsonHeart() {
        JokerCard previous = crimsonJoker;
        if (crimsonStickerAdded && crimsonJoker != null) crimsonJoker.remove(Sticker.DEBUFFED);
        crimsonJoker = null;
        crimsonStickerAdded = false;
        return previous;
    }

    void disableJoker(JokerCard pick) {
        if (pick.isDebuffed()) return;   // a genuinely debuffed joker stays debuffed; we don't own its sticker
        pick.apply(Sticker.DEBUFFED);
        crimsonJoker = pick;
        crimsonStickerAdded = true;
    }

    // --- lifecycle (called by Run) ---

    /** A new round begins under {@code boss}; per-round flags reset. */
    void beginRound(BossBlind boss) {
        activeBoss = boss;
        luchadorDisable = false;
        verdantSold = false;
        bossTriggered = false;
    }

    /** The round ends: restore Quartz debuffs, re-enable Crimson Heart's joker, drop the boss. */
    void endRound() {
        for (DeckCard card : quartzDebuffed) card.remove(Sticker.DEBUFFED);
        quartzDebuffed.clear();
        clearCrimsonHeart();
        activeBoss = null;
    }

    /** A new ante begins: The Pillar's play history resets. */
    void beginAnte() { antePlayed.clear(); }
}
