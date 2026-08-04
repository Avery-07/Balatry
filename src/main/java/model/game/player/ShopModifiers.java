package model.game.player;

/**
 * The shop- and pack-appearance modifiers a run accumulates from vouchers (and starting sleeves/decks): the
 * card-row appearance weights, the shiny-edition rate, and the content flags (Omen Globe, Telescope, Observatory,
 * Showman, Encore, Magic Trick, Illusion). Grouped off {@link Run} so a new appearance voucher touches this class
 * rather than the run's core state. Written by voucher effects; read by the shop/pack generation pools and — for
 * Observatory — the scoring engine.
 */
public final class ShopModifiers {

    private int tarotWeightBonus;     // Tarot Merchant/Tycoon: extra shop-appearance weight for Tarots
    private int planetWeightBonus;    // Planet Merchant/Tycoon: extra shop-appearance weight for Planets
    private int relicWeightBonus;     // Relic Merchant/Tycoon: extra shop-appearance weight for Relics
    private int editionRate;          // Hone (1) / Glow Up (2): shiny editions on shop jokers (0 = none)
    private boolean omenGlobe;        // Omen Globe: Spectral cards may appear in Arcana packs
    private boolean telescope;        // Telescope: a Celestial pack always includes your most-played hand's Planet
    private boolean observatory;      // Observatory: held Planets apply their hand's Mult when scoring
    private boolean showman;          // Showman: the shop may offer items you already own (no de-duplication)
    private boolean encore;           // Encore: the shop favours jokers/consumables you already own
    private boolean magicTrickCards;  // Magic Trick: playing cards may appear in the shop card row
    private boolean illusion;         // Illusion: shop/pack playing cards roll their modifiers at boosted odds

    public boolean isMagicTrickActive()   { return magicTrickCards; }
    public void setMagicTrickCards(boolean b) { magicTrickCards = b; }
    public boolean isIllusionActive()     { return illusion; }
    public void setIllusion(boolean b)    { illusion = b; }
    public int getTarotWeightBonus()      { return tarotWeightBonus; }
    public void addTarotWeight(int n)     { tarotWeightBonus += n; }
    public int getPlanetWeightBonus()     { return planetWeightBonus; }
    public void addPlanetWeight(int n)    { planetWeightBonus += n; }
    public int getRelicWeightBonus()      { return relicWeightBonus; }
    public void addRelicWeight(int n)     { relicWeightBonus += n; }
    public int getEditionRate()           { return editionRate; }
    public void setEditionRate(int n)     { editionRate = Math.max(editionRate, n); }   // never downgrade Glow Up back to Hone
    public boolean hasOmenGlobe()         { return omenGlobe; }
    public void setOmenGlobe(boolean b)   { omenGlobe = b; }
    public boolean hasTelescope()         { return telescope; }
    public void setTelescope(boolean b)   { telescope = b; }
    public boolean hasObservatory()       { return observatory; }
    public void setObservatory(boolean b) { observatory = b; }
    public boolean isShowman()            { return showman; }
    public void setShowman(boolean b)     { showman = b; }
    public boolean isEncore()             { return encore; }
    public void setEncore(boolean b)      { encore = b; }
}
