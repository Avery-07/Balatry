package model.game.rng;

/**
 * The distinct purposes for which the game draws randomness; each is an independent keyed stream.
 * Each constant carries a stable {@code code} that feeds the RNG key. Never reuse a code; only append new ones.
 */
public enum RngSource {
    BOSS_BLIND         (1),        // boss blind selection for an ante
    SHOP_CONTENTS      (2),        // cards spawn in a shop slot
    PACK_CONTENTS      (3),        // cards offered inside a booster pack
    CARD_EDITION       (4),        // whether a shop card rolls foil/holographic/etc...
    CARD_STICKER       (5),        // whether a shop playing card rolls eternal/rental/etc...
    CARD_ENHANCEMENT   (6),        // whether a playing card rolls bonus/mult/etc...
    CARD_SEAL          (7),        // whether a playing card rolls red/blue/etc...
    JOKER_GENERATION   (8),        // joker produced by an effect
    TAROT_GENERATION   (9),        // tarot produced by an effect
    PLANET_GENERATION  (10),       // planet produced by an effect
    SPECTRAL_GENERATION(11),       // spectral produced by an effect
    CARD_DESTROY       (12),       // card destruction (deterministic / non-counter removal)
    ANTE_MODIFIER      (13),       // which sin is active for an ante

    // counter-keyed emergent-event streams: each salted by a per-source occurrence counter (see PlayerStats / Run#roll)
    GLASS_BREAK        (14),       // a played Glass card's shatter roll
    LUCKY_MULT         (15),       // a played Lucky card's +mult roll
    LUCKY_MONEY        (16),       // a played Lucky card's +money roll
    WHEEL_OF_FORTUNE   (17),       // Wheel of Fortune's edition roll

    DECK_SHUFFLE       (18),       // per-round deck shuffle

    STAR_NEGATIVE      (19),       // The Star's negative-edition roll + target pick
    MOON_EDITION       (20),       // The Moon's edition roll + target pick
    SUN_EDITION        (21),       // The Sun's edition roll + target pick

    SHOP_PACKS         (22),       // structural: which booster packs fill the shop's pack row
    SHOP_VOUCHERS      (23),       // structural: which vouchers fill the shop's voucher row

    RELIC_EFFECT       (24),       // a relic's emergent roll (e.g. Pyre's random consumable pick)
    BOSS_EFFECT        (25),       // a boss blind's emergent roll (e.g. The Quartz's per-card debuff)
    SKIP_TAG           (26),       // structural: which skip tag each blind carries (table-level, same for all seats)

    MISC               (99);       // anything not yet promoted to its own source

    private final int code;

    RngSource(int code) { this.code = code; }

    /** Stable identifier mixed into the RNG key. Independent of ordinal(). */
    public int getCode() { return code; }
}