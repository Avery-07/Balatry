package model.game.tags;

import model.items.jokers.Jokers;
import model.items.jokers.Rarity;
import model.items.packs.BoosterPack;
import model.items.packs.PackKind;
import model.items.packs.PackSize;
import model.game.player.Run;
import model.game.rng.RngSource;
import model.game.scoring.HandType;

import java.util.function.Consumer;

/** The skip-tag catalog (master sheet's post-rework roster: Boss, Foil, and Holographic tags removed; Charm and Ethereal renamed to Arcana and Spectral). */
public enum SkipTag {

    // --- immediate: free packs (opened via Run.openPendingPack during the skipped round) ---
    BUFFOON_TAG  ("Buffoon Tag",   run -> run.grantPack(new BoosterPack(PackKind.BUFFOON,   PackSize.MEGA))),
    ARCANA_TAG   ("Arcana Tag",    run -> run.grantPack(new BoosterPack(PackKind.ARCANA,    PackSize.MEGA))),
    SPECTRAL_TAG ("Spectral Tag",  run -> run.grantPack(new BoosterPack(PackKind.SPECTRAL,  PackSize.NORMAL))),
    METEOR_TAG   ("Meteor Tag",    run -> run.grantPack(new BoosterPack(PackKind.CELESTIAL, PackSize.MEGA))),
    STANDARD_TAG ("Standard Tag",  run -> run.grantPack(new BoosterPack(PackKind.STANDARD,  PackSize.MEGA))),
    MYTH_TAG     ("Myth Tag",      run -> run.grantPack(new BoosterPack(PackKind.MYTH,      PackSize.MEGA))),

    // --- immediate: economy and permanent upgrades ---
    ECONOMY_TAG  ("Economy Tag",   run -> run.addMoney(Math.min(Math.max(run.getMoney(), 0), 40))),
    GARBAGE_TAG  ("Garbage Tag",   run -> run.addMoney(2 * run.getStats().getUnusedDiscards())),
    HANDY_TAG    ("Handy Tag",     run -> run.addMoney(2 * run.getStats().getTotalHandsPlayed())),
    SPEED_TAG    ("Speed Tag",     run -> run.addMoney(10 * run.getStats().getBlindsSkipped())),
    JUGGLE_TAG   ("Juggle Tag",    run -> run.setHandSize(run.getHandSize() + 1)),
    INVENTORY_TAG("Inventory Tag", run -> run.setConsumableSlots(run.getConsumableSlots() + 1)),
    ORBITAL_TAG  ("Orbital Tag",   run -> {
        HandType most = run.getStats().getMostPlayedHand();
        if (most != null) for (int i = 0; i < 3; i++) run.getHandLevels().levelUp(most);
    }),
    TOP_UP_TAG   ("Top-up Tag",    run -> {
        for (int i = 0; i < 3; i++)
            run.createJoker(Jokers.randomOfRarity(Rarity.COMMON,
                    run.getRng().streamFor(RngSource.JOKER_GENERATION, run.nextSalt(RngSource.JOKER_GENERATION))).make());
    }),

    // --- next boss: consumed by the Match when the next boss blind is defeated ---
    INVESTMENT_TAG("Investment Tag", Timing.NEXT_BOSS),

    // --- next shop / meta: held pending; consumed by Run#openShop (NEXT_SHOP) and Run#grantTag (Double) ---
    COUPON_TAG   ("Coupon Tag",    Timing.NEXT_SHOP),
    D6_TAG       ("D6 Tag",        Timing.NEXT_SHOP),
    UNCOMMON_TAG ("Uncommon Tag",  Timing.NEXT_SHOP),
    RARE_TAG     ("Rare Tag",      Timing.NEXT_SHOP),
    NEGATIVE_TAG ("Negative Tag",  Timing.NEXT_SHOP),
    VOUCHER_TAG  ("Voucher Tag",   Timing.NEXT_SHOP),
    DOUBLE_TAG   ("Double Tag",    Timing.META);

    /** When a tag's effect lands relative to its grant. */
    public enum Timing { IMMEDIATE, NEXT_SHOP, NEXT_BOSS, META }

    private final String displayName;
    private final Timing timing;
    private final Consumer<Run> effect;

    SkipTag(String displayName, Consumer<Run> effect) {
        this(displayName, Timing.IMMEDIATE, effect);
    }

    SkipTag(String displayName, Timing timing) {
        this(displayName, timing, run -> { });
    }

    SkipTag(String displayName, Timing timing, Consumer<Run> effect) {
        this.displayName = displayName;
        this.timing = timing;
        this.effect = effect;
    }

    public String getDisplayName() { return displayName; }
    public Timing getTiming()      { return timing; }

    /** Resolves an IMMEDIATE tag's effect on {@code run}; no-op for pending timings. */
    public void resolve(Run run) { effect.accept(run); }

    /** Investment Tag's payout, defined here so the number lives with the catalog. */
    public static final int INVESTMENT_PAYOUT = 25;
}
