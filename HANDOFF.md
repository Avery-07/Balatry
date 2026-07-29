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
- **31 harnesses; 30 pass. `JokerTests` fails on two PRE-EXISTING content bugs** (not from recent work), worth fixing early
  so the suite is green again:
  1. Its catalog assertion expects **164** jokers but there are **165** (`Jokers.values().length`) — bump the test to 165, or
     remove the extra joker.
  2. `SCALPER`'s description is keyed by display name `"Scalper"` in `Jokers.Descriptions.MAP` instead of the enum name
     `"SCALPER"`, so `Descriptions.of("SCALPER")` returns empty and "every joker has a description" fails. Fix the key.
  These two reds were **not** touched recently. The last few sessions made client-only changes that **compiled
  clean**; `mvn test` was **not** re-run, so confirm the suite before relying on it being green.

## Critical working conventions (read these)

- **I cannot open a JavaFX window in this sandbox** (no display; needs a server + 2nd seat). So the client's
  *drawing* is verified by **compile + eyeball on the user's machine**, not by me. Everything else (model,
  snapshot, engine logic) is unit-tested. When touching the client, compile and rely on the user to confirm
  visuals. This is why the engine is designed so **logic is tested and only `Renderer` drawing is unverified.**
- **Work directly in the main folder** `C:\Users\Mayeul\IdeaProjects\Balatry` — edit those files and run `mvn`
  there. The user runs from main. **Do NOT use a git worktree + `robocopy /MIR` sync** (an earlier workflow): the
  user keeps ~140 joker texture PNGs in `src/main/resources/sprites/joker/`, which are not in any worktree, and
  `/MIR` deletes everything the source lacks — it wiped the textures on every sync. If a stray worktree exists on
  disk, it is unused; ignore it. Removing it must be done from a separate terminal (`git worktree remove`), never
  from a session whose shell is anchored inside it.
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

- **Jokers: 165.** 17 stubs were implemented in the last session; **9 `b -> b` stubs remain**:
  - **Cross-player (need cross-seat events/mechanisms, all `SEAT_COUPLING`):** Vulture (needs a reliable "an opponent
    lost this blind" signal at settlement), Telescope (opponents' played hand types), Transparent Joker (sell-to-copy a
    leader's joker — needs a joker-copy path + a rounds-owned counter), Espionnage (writes to *other* seats' boards —
    riskiest for determinism), The Mimic (dynamic ability copy of the leader's same-slot joker, like Blueprint cross-seat).
  - **Need a new hook each:** Hiker (a per-DeckCard permanent chip bonus — none exists yet), Scalper (a "shop emptied"
    event), To the Moon (interest-rate change, not just the cap), Chaos the Clown (a free-reroll-per-shop mechanism).
  - **Cross-seat pattern to copy** (see Robin Hood / Generational Hater / Copyright): `Match m = run.getMatch()` (guard
    null), then `m.getRun(id)` / `m.getPlayers()` / `m.seatsAbove(id)` / `m.getStandings()`, fired at a settled trigger
    (ON_ROUND_END, ON_BOSS_DEFEATED), and mark the joker `JokerTrait.SEAT_COUPLING`. Deterministic because the replay
    order is fixed even though seats legitimately diverge.
- Every joker has a description (`Jokers.Descriptions`, keyed by ENUM name) and, where it carries a live variable, a
  `JokerSpec.state(...)` renderer. Rank-reading jokers must route rank checks through `countsAs(run, card, rank)` /
  `anyRank(run, card, predicate)` (Jokers.java) so **Dyscalculie** shifts them; face checks go through `run.isFaceCard(c)`
  so **Pareidolia** reaches them.
- **Consumables / vouchers / relics: fully implemented and described.** Targeted consumables declare
  `ConsumableSpec.minTargets`, enforced in `Run.useConsumable` (refused, card kept) and mirrored in the UI.
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

## Test index (31 harnesses)

- **Model rules**: `MatchTests`, `RoundTests`, `SettlementTests`, `ShopTests`, `StandingsTests`,
  `PlayerStatsTests`, `HandEvaluatorTests`, `HandLevelsTests`, `TriggerTests`, `ActionTests`
- **Content**: `JokerTests`, `TarotTests`, `PlanetTests`, `SpectralTests`, `RelicTests`, `TagTests`,
  `SinTests`, `BossBlindTests`, `BossBehaviorTests`, `StickerTests`, `LoadoutTests`, `LoadoutEffectTests`,
  `FaceDownTests`
- **Scoring**: `ScoringEventTests` (the animation timeline + its invariants)
- **Determinism & transport**: `DeterminismTests`, `HostTests`, `NetTests`, `LobbyTests`, `DisconnectTests`
- **Client-facing**: `SnapshotTests` (the information boundary), `EngineTests` (every `client.engine` class)

## What's next (recommended order)

0. **Fix the two pre-existing `JokerTests` reds** (see Build & run) — 5-minute content fix, restores a green suite.
1. **Eyeball the most recent client work** — the shop redesign, the enlarged joker/consumable top bar, and the new
   joker textures all landed un-verified (no display here). See "Most recent session" below. Confirm the shop fills
   cleanly (no bottom hole), tiles/price tags sit right, and textures fit unstretched before building more UI on top.
2. **Finish the remaining 9 stub jokers** (listed under Current content state). The 5 cross-player ones each need a
   new cross-seat event/mechanism — do them one at a time, tested, not as a batch (determinism is sensitive here).
   Vulture and Transparent Joker are the most self-contained starting points.
3. **Assets:** the card sprite sheet (`src/main/resources/cards/deck.png`, 4 suit rows H/C/D/S × 13 rank cols
   2→A) and pixel font (`src/main/resources/font/game.ttf`) are still optional drop-ins with fallbacks — see
   `README-assets.md`. **Joker textures are now IMPLEMENTED** (see "Most recent session"), and the user has added
   ~140 joker PNGs under `src/main/resources/sprites/joker/`. Remaining texture work, if wanted: extend the same
   `Renderer.jokerTexture` + `imageFit` pattern to consumables/vouchers/relics/packs, and supply the deck sheet.
   Editions stay canvas-drawn effects (Foil/Holo/Polychrome gradients + blend modes, Negative a precomputed
   inverted variant), deliberately **not** textures.
4. **Reconnect** — the log-replay architecture makes it feasible (send the log, replay, resume) but it is a real
   protocol design task. Kicking is the smaller sibling.

## Most recent session — what landed (client-only; compiled, NOT eyeballed, `mvn test` not re-run)

**Joker textures — implemented.**
- `Renderer.jokerTexture(displayName)` lazily loads `/sprites/joker/<Name>.png` and caches it (misses cached as
  null, so the classpath is hit once per name). The key is the **display name with every non-alphanumeric char
  stripped**, keeping capitalisation — `Half Joker` → `HalfJoker.png`, `Oops! All 6s` → `OopsAll6s.png`. This is
  the naming the user's files use; it **supersedes the old "keyed by enum name" plan**. Folder has a `README.md`.
- `Renderer.imageFit(img, x, y, w, h)` draws the texture **scaled to fit, preserving aspect (never stretched)**,
  centered/letterboxed — the user explicitly required no stretching. (The folder `README.md`'s "Format" line still
  says "stretched" — stale wording; the code fits without stretching.)
- Wired at three draw sites, each falling back to the vector tile when no PNG exists: `Hud.drawJokerTile` (owned
  jokers), `ShopScreen.draw` (shop-slot jokers, matched by label), `Overlays.pack` (Buffoon-pack options).
- The user has dropped ~140 texture PNGs into the folder. **Do not delete them** — that folder is source-of-truth
  content (see the working-conventions note about never mirroring a worktree onto main).

**Shop + top-bar layout redesign (Balatro-flavored; needs eyeballing).**
- Top joker/consumable bar enlarged so tiles read big: `Ui.SLOT_H` 118→190, new `Ui.SLOT_TILE_W/H`=108×146;
  `jokerRow`/`itemRow` sized to match. This intentionally lowers the center region (`cTop` grows).
- `ShopScreen` fully rewritten to **fill the whole center region — no empty hole at the bottom** (a prior
  "shrink the panel" attempt left a backdrop gap the user disliked). Now: a header (Next Round / Reroll) over two
  **framed inset shelf rows** that split the remaining height evenly — the card shelf on top, Voucher + Booster
  Packs side by side below. Tiles 116×156 centered per shelf, price tags centered above (shop `TileRow`s sized to
  match). Balatry rolls more items than Balatro (up to 2 vouchers / 4 packs), so packs can still overlap when full.
- Result/Selection screens use fixed, top-anchored panels, so the smaller center region doesn't break them.

## Prior session — what landed (all model tests green)

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

**Model rules**
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
