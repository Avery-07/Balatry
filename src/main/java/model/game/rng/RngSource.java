package model.game.rng;

/** The distinct purposes for which the game draws randomness; each is an independent keyed stream. */
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
    LUST_SHOP_EXTRAS   (27),       // structural: which rows Lust's two extra shop items land in (table-level)
    PRIDE_LEGENDARY    (28),       // structural: which legendary joker Pride auctions each shop phase (table-level)
    DECK_BUILD         (29),       // structural: the starting deck's composition (Erratic's ranks/suits, Fracture's cuts)
    STICKER_FLOAT      (30),       // where a Floating joker drifts to at the start of each hand
    PACK_HAND          (31),       // the temporary hand dealt for targeting a consumable opened from a pack outside a round

    // Per-item category streams: an item's own code (see PlayerStats#nextSalt(source, itemCode)) isolates every
    // joker / relic / tarot / spectral, so its emergent rolls never perturb another item's — the same play always
    // yields the same result regardless of what else is owned.
    JOKER              (40),       // any emergent roll made inside a joker's own effect, keyed by that joker
    RELIC              (41),       // any emergent roll made inside a relic's own effect, keyed by that relic
    TAROT              (42),       // any emergent roll made inside a tarot's own effect, keyed by that tarot
    SPECTRAL           (43),       // any emergent roll made inside a spectral's own effect, keyed by that spectral

    MISC               (99);       // anything not yet promoted to its own source

    private final int code;

    RngSource(int code) { this.code = code; }

    /** Stable identifier mixed into the RNG key. Independent of ordinal(). */
    public int getCode() { return code; }
}