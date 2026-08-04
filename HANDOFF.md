# Balatry — session handoff

A multiplayer recreation of *Balatro*. **Java 21, Maven.** Pure-Java model + a JavaFX client.
This doc is the "start here" for a fresh session. Read it, then dig into the files it points at.

## Build & run

- `mvn test` — compiles everything and runs the hand-rolled test harnesses. **No JUnit.** Each test is a
  `*Tests` class with a `public static void main`; `harness.HarnessRunner` discovers every `*Tests` under
  `target/test-classes/model/` and runs each in its own JVM. So **test classes must live under a `model.*`
  package** to be discovered (e.g. client tests are in `model.client`, engine tests in `model.engine`).
- `mvn javafx:run` — launches the client (`client.game.GameClient`). It opens on the **main menu**: type a name,
  then **Host a game** or **Join a game** (address field below the buttons). Sleeve, stake and deck are picked in
  the **lobby**, where everyone can see each other's. Hosting starts a server inside the client process, so no
  separate server is needed. A lobby seats **2-4**. To play locally, run
  `mvn javafx:run` twice: host in the first window, join `localhost` in the second, then Start in the first.
  `mvn exec:java@server` still runs a headless dedicated server that auto-starts when its seats fill.
- **33 harnesses, all green** (`mvn test` → "33 harnesses, 0 failed"). The old two `JokerTests` reds are fixed
  (count is 165; the SCALPER key was already corrected). Keep it green — it is a real safety net now.
- **Dev/cheat overlay** for testing: set the `BALATRY_DEV=true` env var (inherited by the forked app JVM, so
  `$env:BALATRY_DEV="true"; mvn javafx:run` in PowerShell), then press **`*`** in a match to toggle `DevPanel` —
  money/slots/summon-joker/summon-card-with-modifiers/summon-consumable + a Revert undo stack. It mutates the
  **local** model directly (single-player/host testing only — cheats don't go through the action log, so they
  won't sync to other seats or survive a determinism replay). Off unless the flag is set.

## Critical working conventions (read these)

- **I cannot open a JavaFX window in this sandbox** (no display; needs a server + 2nd seat). So the client's
  *drawing* is verified by **compile + eyeball on the user's machine**, not by me. Everything else (model,
  snapshot, engine logic) is unit-tested. When touching the client, compile and rely on the user to confirm
  visuals. This is why the engine is designed so **logic is tested and only `Renderer` drawing is unverified.**
- **Work directly in the main folder** `C:\Users\Mayeul\IdeaProjects\Balatry` — edit those files and run `mvn`
  there. The user runs from main. **Do NOT use a git worktree + `robocopy /MIR` sync** (an earlier workflow): the
  user keeps ~140 texture PNGs the worktree lacks, and `/MIR` deletes everything the source lacks — it wiped the
  textures on every sync. If a stray worktree exists on disk it is unused; ignore it. Removing it must be done from
  a separate terminal (`git worktree remove`), never from a session whose shell is anchored inside it.
- **Textures are the user's source-of-truth content — never delete/overwrite `src/main/resources/sprites/**`.** The
  tree the client loads (`GameClient.loadAssets`): `sprites/jokers/<Name>.png` (per-joker, **plural** folder — note
  `Renderer.jokerTexture` was updated from the old singular `joker/`); `sprites/cards/{cards,Enhancements,Seals,Decks}.png`
  (sheets); `sprites/consumables/{Planets,Tarots,Spectrals}.png`; `sprites/packs/{Arcana,Buffoon,Celestial,Spectral,Standard}Packs.png`;
  `sprites/vouchers/Vouchers.png`. `Decks.png` (card backs) and `difficultyStake/Stakes.png` exist but aren't wired
  yet; there is **no** Myth-pack or relic art. `*/ignore.png` files are scratch — skip them.
- **Determinism is sacred.** Same seed + same actions → bit-identical replays, mirrored across seats
  (`DeterminismTests`). Any RNG use must be keyed/salted deterministically.
  **Never salt an RNG with `Card.id()` / `DeckCard.id()`** — those come from a JVM-global counter that the
  client's own throwaway cards also advance, so they differ between processes. Salt with round-local counters
  (see `Round.drawCount`). Ids are for cross-frame animation identity only; both fields say so.
- Reference docs the user provided live in `C:\Users\Mayeul\Downloads\Balatry Documentation\`
  (`Balatry Jokers.md`, `Balatry Game Content.md`, `Balatro Consumable Reference.md`, `Balatry Overview & Sins.md`).

## Architecture

```
model.items.*  cards: DeckCard, jokers, consumables, relics, vouchers, packs, DeckType, Decks
model.game.*   rules: Match, Round, Run, Shop, scoring, bosses, sins, tags, rng, host, net
model.modifiers.*  Edition, Enhancement, Seal, Sticker, StickerState
   └─ MatchSnapshot   (client/)  — immutable, seat-relative DTO built from the model each frame.
                                    THE information boundary (opponents get only points+rank). Tested via
                                    SnapshotTests. Any UI reads this, never the model directly.
   └─ MatchViewModel  (client/)  — holds an FX property<MatchSnapshot>; the single action choke point.
   └─ client.game.GameClient     — the shippable client: a JavaFX Canvas redrawn by an AnimationTimer.
   └─ client.engine.*            — pure, rendering-agnostic, unit-tested (EngineTests).
```

**`client.engine` — everything animated lives here, and it is all tested:**
`Easing`, `Tween`, `Motion` (2D tween pair), `Layout` (hand fan + hit-test `Rect`), `CardEntity` (a retained
card: position, selection, flip, drag), `Reconciler` (diff a snapshot into retained entities),
`Counter` (count-up + pop), `Fader` (fade-to-black screen transition), `Idle` (permanent sway/bob, pure
`(time, seed)`), `TileRow` (a retained, draggable row of tiles), `ScoreReel` (plays a scoring timeline back),
`PaintField` (the animated backdrop's swirling paint math).

**The client is a game-loop Canvas renderer** (chosen over the old retained-node JavaFX because the goal is a
*shippable, juicy* Balatro-like), decomposed into focused components in `client.game`:

- `GameClient` — orchestrator: canvas, loop, host/join, input dispatch (click + press/drag/release + keys),
  screen switching. Until the match starts it hands the whole screen, keyboard and clicks to `Menu`.
- `Menu` — pre-match UI: main menu (name, Host/Join + address) and the lobby (roster up to 4, your own
  sleeve/stake, the host's deck picker, Start). It owns its state because no snapshot exists yet. **Loadout is
  picked in the lobby, not the menu** — so everyone sees everyone's. Its cyclers send the pick to the server and
  adopt what comes back in the next `LOBBY` frame; the roster on screen is always the server's.
- `Ui` — shared per-frame context: renderer, snapshot, vm, status, click/hover registries (`buttons`,
  `packButtons`, `selectables`, `jokerSel`, `tips`), selection state, mouse position, the five `TileRow`s, the
  `ScoreReel` + its per-frame source rects, and `regionOffsetY` (see gotchas).
- `Hand` — the animated hand: reconcile-from-snapshot (keeps selection + motion across frames — this fixed the
  original "selection wiped every frame" bug), fan layout, idle sway, flip, drag-to-reorder, exit animations,
  and the **staging area** where played cards stand while the scoring reel runs.
- `Hud` — sidebar (counters, play preview) + top joker/consumable tile rows + deck pile; persistent every phase.
- `Screen` + `SelectionScreen` / `BlindScreen` / `ShopScreen` / `ResultScreen` / `FinishedScreen` — one per
  phase's center panel.
- `Overlays` — contextual Buy/Use/Sell buttons, relic targeting, pack modal, Run Info standings, hover tooltips,
  the deck-contents view, and `scoreEffect` (the scoring animation's effect squares).
- `Renderer` — **the only untested piece**: draw primitives (panels, text, sprite-or-vector cards, card backs).
- `Background` — the looping animated backdrop's **plumbing** (buffer, image, update cadence, blit). The effect
  itself is `client.engine.PaintField`: the Balatro-style swirling paint, as pure math — swirl (polar rotation
  growing with radius + time), iterated domain warp (the marbled turbulence), then three-colour banding with a
  highlight boost. Rendered small (128px wide) and stretched, so the chunkiness *is* the look; recomputed at
  30Hz independently of the frame rate. Every knob (colours, spin, zoom, contrast, `warpSteps`) is a public
  field on `PaintField`. Purely cosmetic — never reads the model or snapshot.
- `Waiting` — the shared "your move is in, waiting on the others" barrier button (result + shop).
- `Palette` / `Fmt` — colors, display strings.

### Client interaction model (matches the game design)
- **Select an item → contextual action buttons appear beside it** (Balatro-style). Shop items → Buy; held
  jokers → Sell; held consumables → Use/Sell.
- **Relics are used like tarots**: select the target **card(s) in hand**, select the relic, hit **Use** — it
  derives rank/suit/hand-type from the selection. Katadesmos reads a **selected joker**. Pyre/Limos/Harpax are
  random/all-above (no target). Aegis/Metabole/Mimesis: just Use. (No relic needs a seat picker.)
- **Pack opening** is a modal overlay: buying a pack auto-opens it; click options until the budget is spent.
- **Drag is one grammar for the whole table**: press-move-release, 7px threshold, a finished drag swallows its
  click. Hand cards reorder locally (`Hand.manualRank`; the Sort buttons and manual drag cancel each other).
  Jokers and consumables/relics submit `MoveJoker`/`MoveConsumable`/`MoveRelic` when dropped in their row's band
  (consumables and relics reorder only within their own class). **Shop tiles have no drop target by design** —
  they lift and glide home, purely for feel. An invalid drop needs no special case: the layout retargets every
  tile every frame, so it just glides back.
- **Hover** shows tooltips (cards, jokers with their live variable, items, shop tiles); hovering the deck pile
  opens the full-deck view.
- **Every lockstep barrier tells you it registered your move.** Blind selection says "chosen — waiting…";
  Continue (result) and Next Round (shop) turn into "Ready — waiting for others…" with an *N of M players ready*
  tally (`Waiting`). Without this a pressed button just stops responding and reads as a freeze.

## Current content state

- **Jokers: 165 — all implemented (no `b -> b` stubs remain).** The last 9 were done this session, each tested:
  - **Single-seat (new hooks added):** Hiker (per-`DeckCard` `bonusChips`, scored in `ScoringEngine`), Scalper
    (`Trigger.ON_SHOP_EMPTIED`, fired from `Shop.buy`), To the Moon (extra $1 interest per $5 at settlement,
    uncapped — in `RoundSettlement`, read by ownership), Chaos the Clown (first shop reroll free — in `Shop`, read
    by ownership). To the Moon and Chaos are **passive**: their enum entries stay `b -> b` and the effect lives where
    it acts, read via `Run.ownsActiveJoker(spec)`.
  - **Cross-seat (all `SEAT_COUPLING`):** Stargazing (the old "Telescope"; $3 when a hand type matches a rival's last,
    via `PlayerStats.getLastHandType`), Vulture (+X0.25 Mult per opponent who fails a blind — new
    `Trigger.ON_BLIND_SETTLED`, fired for every active seat after `awardPoints` in `Match.toResult`), Transparent
    Joker (after 2 rounds, sell-to-copy the leader's random joker; the copy is queued on `Run.queueJokerFromSale` and
    placed by `Board.sell` once the sale frees the slot), The Mimic (registers a delegate on **every** trigger and
    applies the leader's same-slot joker's effect for that trigger — copies the *whole* ability, e.g. Cloud 9's
    end-of-round payout, not just `ON_HAND_PLAYED`), Espionnage (arms a same-slot joker debuff on every seat above,
    reusing `Afflictions.armJokerDebuff` — the Katadesmos mechanism).
  - **Cross-seat pattern** (see Robin Hood / Generational Hater / Copyright, and now the five above): `Match m =
    run.getMatch()` (guard null), then `m.getRun(id)` / `m.getPlayers()` / `m.seatsAbove(id)` / `m.getStandings()`,
    fired at a settled trigger (`ON_ROUND_END`, `ON_BOSS_DEFEATED`, `ON_BLIND_SETTLED`), and mark the joker
    `JokerTrait.SEAT_COUPLING` (which also excludes it from `DeterminismTests`' mirror-asserted runs). Deterministic
    because the replay order is fixed even though seats legitimately diverge; keep any RNG keyed and off the base path.
- Every joker has a description (`Jokers.Descriptions`, keyed by ENUM name) and, where it carries a live variable, a
  `JokerSpec.state(...)` renderer. Rank-reading jokers must route rank checks through `countsAs(run, card, rank)` /
  `anyRank(run, card, predicate)` (Jokers.java) so **Dyscalculie** shifts them; face checks go through `run.isFaceCard(c)`
  so **Pareidolia** reaches them.
- **Consumables / relics: fully implemented and described.** Targeted consumables declare `ConsumableSpec.minTargets`,
  enforced in `Run.useConsumable` (refused, card kept) and mirrored in the UI.
- **Vouchers: all live.** `BLANK` is an intentional no-op (Antimatter's prerequisite).
  The wiring uses per-run knobs on `Run` (`tarot/planet/relicWeightBonus`, `editionRate`, `omenGlobe`/`telescope`/
  `observatory`/`showman`/`encore`, `magicTrickCards`/`illusion`): Tarot/Planet/Relic Merchant & Tycoon add shop
  appearance weight (read in `CatalogShopPool`), Hone/Glow Up stamp editions on shop jokers, Omen Globe puts
  Spectrals in Arcana packs, Telescope plants your most-played Planet in a Celestial pack, Observatory adds a held
  Planet's Mult in scoring (`ScoringEngine` Phase C), **Showman** removes the new own-item shop de-duplication,
  **Encore** biases the shop toward owned jokers. Covered by `VoucherEffectTests`.
- **Textures & editions (client).** A texture-atlas layer in `Renderer` draws real art everywhere: playing cards
  (`cards.png`, transparent-backed → an enhancement/base cell is drawn under the face; seals overlaid), jokers,
  planets/tarots/spectrals, packs (kind sheet, size→cell 0/1/2), and vouchers. **Editions are real now** —
  `EditionArt` computes Balatro's actual foil-shader pattern into animated buffers, blitted with a SCREEN blend
  (`Renderer.editionEffect`); Foil/Holo/Poly + Negative (dark-navy). Enhancement/seal/edition ordinals are threaded
  through the card-family snapshot views + `CardEntity`/`Reconciler` (backward-compat constructors). **The voucher
  cell map is a placeholder** — `Renderer.VOUCHER_CELL` points every voucher at cell 0; the user is filling it in
  (search `TODO(mapping)`).
- **Standard packs & Magic Trick** offer real playing cards that roll enhancement/seal/edition (stackable) via
  `model.items.PlayingCards`; **Illusion** boosts those odds. Rendered as actual card faces (`CardFace` on
  `ShopItem`/`PackOption`). Tested by `PlayingCardTests`.
- **Sins are togglable per match.** The lobby has a host-set "Ante Sins: ON/OFF" (threaded through
  `MatchSetup.sinsEnabled` → the wire form's 4th tab field → `MatchConfig`; off = `SinSelector.NONE`, so
  `activeSin` is null and the modifier is `SinModifier.NONE`). Each `Sin` now has `displayName()`/`description()`;
  the sidebar sin panel hovers to show the effect, and `AnteBanner` shows an old→new sin handover on ante change.
- **Relics**: Pyre destroys a consumable from every seat above; Limos & Harpax hit a random seat above
  (`RelicKind.RANDOM_RIVAL`); Katadesmos takes a joker slot from a selected joker.
- Every seat starts with **$4**. Relics **share the consumable slot pool**.
- **Decks / sleeves / stakes — all live.** Deck is **table-shared** (`MatchConfig.deckType`); sleeve and stake are
  **per-seat** (`SeatConfig` → `Match.createSeated`). Compositions in `Decks`; behavioural decks: Plasma (target
  ×2, chips/mult averaged in `Run.balanceIfPlasma` *after* the engine so joker arithmetic is untouched), Bazaar,
  Ghost, Anaglyph; sleeves: Frugal (replaces interest), Celestial (+2 levels/ante, no shop Planets). Seat-aware
  shop rolls go through `ShopPool.roll(Run, stream)`.
- **Stickers: all 8 live** (`model.game.player.Stickers` owns the lifecycle). Floating, Delayed, Fragile, Sticky
  join Eternal/Perishable/Rental/Debuffed; the Red/Blue/Gold stakes roll them onto shop jokers (1-in-4,
  cumulative pools).
- **Bosses**: the ante boss locks at ante start and shows its `effect()` during selection. The four face-down
  bosses (House / Wheel / Fish / Mark) are implemented; the snapshot **masks** hidden cards (rank/suit −1, blank
  label) so nothing can leak them.
- **Networking**: lobby phase (`JOIN`/`LOBBY`/`LOADOUT`/`DECK`/`BEGIN`/`START`/`CLOSED`), in-client hosting, and
  disconnect handling (a dropped socket becomes `Action.PlayerLeft` in the log, so every seat learns of it at the
  same point in the same replay). **Missing: reconnect and kicking.**

## Test index (33 harnesses)

- **Model rules**: `MatchTests`, `RoundTests`, `SettlementTests`, `ShopTests`, `StandingsTests`,
  `PlayerStatsTests`, `HandEvaluatorTests`, `HandLevelsTests`, `TriggerTests`, `ActionTests`
- **Content**: `JokerTests`, `TarotTests`, `PlanetTests`, `SpectralTests`, `RelicTests`, `TagTests`,
  `SinTests`, `BossBlindTests`, `BossBehaviorTests`, `StickerTests`, `LoadoutTests`, `LoadoutEffectTests`,
  `FaceDownTests`, `PlayingCardTests` (pack/Magic-Trick playing cards + modifiers), `VoucherEffectTests` (the
  newly-wired vouchers)
- **Scoring**: `ScoringEventTests` (the animation timeline + its invariants)
- **Determinism & transport**: `DeterminismTests`, `HostTests`, `NetTests`, `LobbyTests`, `DisconnectTests`
- **Client-facing**: `SnapshotTests` (the information boundary), `EngineTests` (every `client.engine` class)

## What's next (recommended order)

1. **Eyeball the large batch of un-verified client visuals** (no display here). Everything from this session is
   compile-verified only: the **edition shimmers** (`EditionArt` intensities are guesses — foil blue, poly alpha,
   negative navy are tunable constants), all the **textures** (playing cards + enhancement/seal, consumables,
   packs, vouchers), the **collection** screen, the **ante-change banner** and **sin hover**, the **dev panel**,
   and the earlier shop/top-bar redesign. Confirm they read right before building more UI on top.
2. **Relics in the shop — DONE.** Relics now roll into the shop card row at a base ~14/114 share (like Tarots), a
   `RELIC_WEIGHT` band in `CatalogShopPool`; Relic Merchant/Tycoon add `Run.relicWeightBonus` (+20/+40, like the
   Tarot/Planet vouchers) so both are live. Buy/pricing already existed (`RelicCard` is a `MarketCard`, $5, sell
   $2; `run.acquire`/`canAcquire` handle it — relics share the consumable slot pool). Covered by
   `VoucherEffectTests`; `ShopTests`' base-row assertion now allows relics. **Still open here:** relics have **no
   art** — a shop relic tile falls back to the labeled vector panel (`consumableFace`/`jokerTexture` miss the
   name), so add **relic textures** (+ a `relicFace`/cell map in `Renderer`, mirroring `voucherFace`) and
   **Myth-pack textures**; fill in the **voucher cell map** if the user hasn't (`Renderer.VOUCHER_CELL`).
   (`Decks.png` card backs and `Stakes.png` are now wired — deck back on the pile, stake chip on the right rail.)
3. **Stub jokers — DONE.** All 9 are implemented and tested (see Current content state). `Decks.png`/`Stakes.png`
   are now wired too (deck-back on the pile + stake chip on the right rail, previews in the lobby cyclers). Relics
   in the shop still lack art (item 2). Remaining texture gaps: **relic art** (+ a `relicFace`/cell map in
   `Renderer`) and **Myth-pack art**; confirm the **voucher cell map** (`Renderer.VOUCHER_CELL`).
4. **Reconnect** — the log-replay architecture makes it feasible (send the log, replay, resume) but it is a real
   protocol design task. Kicking is the smaller sibling.
5. **Wrath's per-round pack** still vanishes unopened on a *played* (won/lost) round — the pending-pack "Open"
   prompt was scoped to *skipped* rounds. Extending it to the played-round barrier would close that.

## This session — what landed (model bits tested; client visuals compile-only, NOT eyeballed)

**Textures & the atlas layer (all in `Renderer`, fed by `GameClient.loadAssets`).**
- Per-joker PNGs via `jokerTexture(displayName)` — key = display name with non-alphanumerics stripped
  (`Half Joker`→`HalfJoker.png`), folder now `sprites/jokers/` (plural). `imageFit` draws aspect-preserved, never
  stretched. Vector tile is the fallback everywhere.
- **Playing cards**: `cards.png` is a 13×4 grid, **transparent-backed**, so `Renderer.card(...)` draws a base first
  (the enhancement's cell from `Enhancements.png`, or cell 0 for a plain card — Stone shows no rank/suit), then the
  face, then the seal (`Seals.png`), then the edition. `card()` now takes enhancement/seal/edition ordinals.
- **Consumable / pack / voucher atlases**: `planet/tarot/spectral` cells (name→cell maps, keyed by display name),
  per-kind 2×2 pack sheets (size→cell), and `Vouchers.png`. `consumableFace` / `packFace` / `voucherFace` draw
  them at the shop / pack / HUD tiles. **Cell maps are in `Renderer`** — enhancement/seal/planet/tarot/spectral are
  correct; **`VOUCHER_CELL` is a placeholder (all cell 0)** the user is filling (`TODO(mapping)`).
- **Editions are real** — `EditionArt` computes Balatro's foil-shader pattern (radial ripples + angular streak +
  axis bands) into card-sized buffers rebuilt ~24Hz, blitted SCREEN-blended in `editionEffect`; Foil/Holo/Poly +
  Negative (dark navy). `Renderer.clock(ui.now)` drives it.
- **Plumbing**: an `edition` (and earlier `enhancement`/`seal`) ordinal is threaded through `CardFace`,
  `HandCardView`, `DeckCardView`, `JokerView`, `ItemView`, `ShopItem`, `PackOption`, and `CardEntity`/`Reconciler`
  — all with backward-compatible constructors so `EngineTests` compiles untouched.

**Model content.**
- `model.items.PlayingCards` — the shared roller for Standard-pack / Magic-Trick playing cards: independent
  enhancement/seal/edition rolls (stackable), boosted odds under Illusion. `BoosterPack` STANDARD uses it;
  `CatalogShopPool` adds a Magic-Trick playing-card band. Editions on played cards already score.
- **11 stubbed vouchers implemented** (see Current content state); **Relic Merchant/Tycoon deferred**.
- **Sins togglable** in the lobby (`MatchSetup.sinsEnabled` → wire form's 4th field → `MatchConfig`; off = `SinSelector.NONE`).
  `Sin` gained `displayName()`/`description()`.
- Fixed the two `JokerTests` reds → suite green.

**Client UX (un-eyeballed).**
- **Collection** screen: `Menu`'s main menu has a "Collection" button opening a paged grid of every joker (idle
  sway/bob, hover tooltips via the shared `Overlays.tooltip`). Menu path now calls `overlays.tooltip(ui)`.
- **Sin hover** (sidebar) + **`AnteBanner`** (old→new sin handover, slides/fades on ante change; triggered in
  `onSnapshot` off `ante` increasing).
- **Skip-granted (and Wrath) packs open on the skip screen**: the blind barrier holds for a skipped seat with a
  pending pack (`MatchHost.allRoundsResolved`), and `Overlays.pendingPackPrompt` offers "Open" → `Action.OpenPack`
  → the existing modal. `MatchSnapshot.pendingPacks` exposes them.
- **Pack overlay** made shorter (its Use button was hidden behind the dealt hand) and the dealt hand + jokers +
  consumables are now **drag-reorderable during a pack**.
- **Shop + top-bar redesign**: `Ui.SLOT_H` 118→190 (+`SLOT_TILE_W/H` 108×146); `ShopScreen` rewritten to fill the
  center region with framed inset shelves (card shelf; Voucher + Booster Packs), tiles 116×156, price tags centered.
- **`DevPanel`** (`*`, gated by `BALATRY_DEV`) + expanded `Log` (new `DEV` category; phase/connect logging).

## Earlier session — what landed (all model tests green)

**New engine machinery** (reuse these before adding more):
- **Triggers** (`Trigger.java`): `ON_CARD_DESTROYED`, `ON_PACK_OPENED`, `ON_PACK_SKIPPED`, `ON_LUCKY_TRIGGERED`,
  `ON_JOKER_DESTROYED`, plus `ON_PURCHASE_PRICING` now knows the item. Each card-context trigger uses a transient
  channel on `Run` (like `fireDiscard`/`getLastDiscarded`): `getDestroyedCard()`, `getDestroyedJoker()`,
  `getPurchaseItem()`.
- **JokerTraits** (`JokerTrait.java`): `FOUR_FINGERS`, `SHORTCUT`, `SMEARED`, `SPLASH`, `DYSCALCULIA`, `PAREIDOLIA`,
  `PROBABILITY_DOUBLER`. Query with `run.hasActiveTrait(trait)`.
- **HandEvaluator** is now configured per-play from the run's traits via `HandEvaluator.forTraits(ff, sc, sm, sp, dys)`
  (the ONE trait→options mapping; `Round.evaluatorFor(run)` uses it, and the client preview uses the same via the
  snapshot's `EvalFlags`). Options: 4-card flush/straight, straight gap, smeared suits, splash, dyscalculia
  (brute-forces each card as its rank or `Rank.numberedAbove()`). Face cards never shift; numbered cycle A→2→…→10→A.
- **Packs**: consumables/relics are **used immediately** on pick (not stored); a temporary hand is dealt for targeting
  when a pack is opened outside a round (`Run.getSelectionHand()`); Skip via `Action.SkipPack`/`Run.skipPack()`.
- **Pricing**: `Run.beginPurchase(item)` + `getPurchaseItem()` lets `ON_PURCHASE_PRICING` jokers (Astronomer, Curator)
  see what's being bought and call `run.makePurchaseFree()`.
- **Fix**: `RoundSettlement.cashOut` now iterates a joker snapshot (a self-destructing ON_ROUND_END joker — Gros
  Michel, Diet Cola — used to `ConcurrentModificationException`).

**Jokers implemented (17):** Superposition, Diet Cola, Astronomer, Curator, Canio, Hallucination, Red Joker, Lucky
Cat, Four Fingers, Shortcut, Smeared, Splash, Pareidolia, Dyscalculie, Oops! All 6s, Chef Joker, Copyright. All have
model tests in `JokerTests`/`HandEvaluatorTests`.

**Client UX (ALL un-eyeballed — needs verification on your machine):**
- Scoring: held-card (Steel) and ownerless (base/sin/Plasma) beats now anchor properly; fixed the Plasma "Balanced"
  square showing twice; the round-score readout waits for the chips×mult boxes before tallying.
- Blind barrier: skipping/finishing now shows an irremovable "waiting for others" popup — over the blind-selection
  tiles for a skip, over the round board for a finish (`Ui.atBlindBarrier()`).
- Run Info overlay: added poker-hand levels + usage counts and redeemed vouchers.
- Skip button shows the skip-tag's effect on hover; stickers now show on ALL card tooltips (was jokers/items only).
- Pack overlay reworked: click-to-preview + Use (no accidental commit), hover descriptions, Skip button, relic
  targeting like tarots, dealt hand centered under the panel.
- **Dynamic slot positioning** for joker/consumable/shop/pack rows via `Layout.slots(...)` (centered, count-scaled,
  compresses when crowded — the deck-hover rule). Joker/consumable zone split in `Hud.drawTopSlots` is a tunable
  guess (left 60% / right 30%) — check it looks right.
- Idle sway+bob on every card-like tile (jokers, consumables, shop, pack) via `Renderer.rotated(...)` + `ui.now`.
- Lust shop now adds exactly 1 extra card + 1 extra pack (was up to +2 packs → the "5 packs" bug); base pack slots
  stay 3.

**Deliberately out of scope so far** (decide consciously before starting): endless mode past ante 8 (placeholder
scaling exists), run persistence/saves, spectating, and anti-cheat (lockstep means every client holds all hidden
information — fine for friends, unfixable for strangers without a server-authoritative rewrite).

## Gotchas

**Snapshot / client**
- `MatchSnapshot.of` has a test seam: `of(Match, PlayerId)` (the client path delegates). Use it in harnesses.
- Adding a snapshot field means updating the record **and** the `of(...)` constructor call (easy to duplicate an
  arg — watch it).
- The `Rank` enum ordinal maps directly to sprite columns; `Suit` is mapped explicitly (HEARTS→row0, CLUBS→1,
  DIAMONDS→2, SPADES→3) in `Renderer`. **Display names are not that order** — use `Rank/Suit.displayName()`,
  `HandType.displayName()` and `Fmt.title`, which are the single authorities (a hand-rolled suit array in `Fmt`
  once made tooltips call spades "Hearts").
- `Ui.regionOffsetY` is applied when a region is *registered*, so a screen drawing under a `gc.translate` (the
  menu slide) gets correct hitboxes for every region type. Don't patch hitbox lists after the fact.
- `Ui`'s per-frame state (`tips`, `sourceRects`, the click registries) is cleared in `newFrame()`.
- **A card modifier (enhancement/seal/edition) shown on a card touches ~9 places**: the `Renderer.card(...)` params,
  `CardFace`/`HandCardView`/`DeckCardView`/`JokerView`/`ItemView`/`ShopItem`/`PackOption` records + their builders,
  and `CardEntity`/`Reconciler.Desired`. Keep backward-compatible overloads on `CardEntity`/`Desired` or
  `EngineTests` breaks. Editions are drawn by `editionEffect`, not `card()`'s texture path, so tile draws (jokers,
  shop/pack faces) call it separately.
- Texture atlas cell maps live in `Renderer` (`ENH_CELL`, `SEAL_CELL`, planet/tarot/spectral, pack size, `VOUCHER_CELL`).
  Sheet order ≠ enum order (e.g. tarots swap Justice/Strength; spectrals shift by one and skip Exorcism/Black Hole).
- `EditionArt` shares one animated buffer per edition across all cards; it's cheap but means every foil card shimmers
  in phase. Tunable intensity constants are at the top of `EditionArt` and in `editionEffect`.
- **`DevPanel` cheats mutate the local model directly** — single-player/host testing only; they never enter the
  action log, so they desync multiplayer and won't survive a determinism replay. Gated behind `BALATRY_DEV`.

**Model rules**
- **Determinism when adding shop/pack rolls: gate new RNG so the *base* path is byte-identical.** `CatalogShopPool`
  and `BoosterPack` only draw for a voucher/flag when it's active (e.g. edition rolls, Showman dedup, Omen Globe)
  — with no voucher the stream is consumed exactly as before, which is why `DeterminismTests` stayed green. Follow
  this whenever a new knob affects generation.
- **Sins off = `SinSelector.NONE`** (returns null); `Sins.modifierFor(null)` is already `SinModifier.NONE`, and
  `Match.getActiveSin()` is then null (snapshot shows "None"). The `Sin` enum's `toString()` is unchanged (still
  the constant name), so `DeterminismTests`' string dumps are unaffected — only `displayName()`/`description()`
  were added.
- **Stakes are per-seat, so chip targets are too.** Ask `match.getCurrentTarget(playerId)`, never the no-arg form
  (that one is the White-stake baseline). Same for cash-out (`run.getStake().rewardFor(blind)`) and rerolls.
- Stake target growth compounds **per ante above the first**, so ante 1 is identical at every stake by design.
- Starting vouchers (Silk sleeve, Eclipse deck) use `run.grantVoucher`, not `redeemVoucher` — the latter would
  burn the seat's one redemption for that ante.
- Anything that rewrites the score *after* `ScoringEngine.score` (Plasma) must also append a `ScoringEvent`, or
  the scoring animation counts up to totals the model then silently replaced.
- **Barrier state lives on `Match`, the barrier *check* lives on `MatchHost`.** `blindChoice` and `readySeats`
  are both `Match` fields with accessors (`hasChosenBlind`/`allChosen`, `isReady`/`readyCount`/`allReady`) because
  the snapshot has to show them; `MatchHost.advanceIfBarrierMet` is what actually crosses. Put new per-seat
  barrier state on `Match` for the same reason — a UI that cannot see it can only render a dead button.

**Networking / multiplayer**
- `MatchClient.getLocalHost()` is **null until the match starts**. Guard with `isStarted()`; the client exists
  during the lobby, when there is no model at all. `MatchViewModel` is only built once `onStarted` fires.
- Seat 0 is the host by definition (first to join). Deck picking and starting are server-enforced, not just
  hidden in the UI — see `MatchServer.handleLobby`.
- Player-typed names reach the wire, so they go through `MatchSetup.sanitize` (it strips `,` `:` and tabs, the
  roster line's separators). `LobbyTests` covers the smuggling case.
- **Barriers must be measured against `match.getActiveSeats()`, never `getSeats()`** — the latter still includes
  players who left, so using it deadlocks the table on a disconnect.
- Seats are never removed from a running match (indices are baked into every logged action); a departed seat
  stays in `getSeats()` and the standings, flagged by `hasDeparted`. Re-indexing happens only in the lobby.
- **The host leaving the lobby closes it** (the server lives in the host's process): the server broadcasts
  `CLOSED` and drops everyone. A *guest* leaving is survivable — the roster shrinks and seats below move up.
- `MatchClient.Callbacks.onClosed` fires exactly once for any end of connection. `GameClient` ignores it when it
  tore the connection down itself (`client == null`).
- The socket harnesses (`LobbyTests`, `NetTests`, `DisconnectTests`) are **load-sensitive**: the runner starts
  each harness in its own JVM, so waits are generous (`LobbyTests.WAIT_MS`). An isolated failure that passes on
  re-run is almost always that, not a real bug — confirm by running the class standalone.
