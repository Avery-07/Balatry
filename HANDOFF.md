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
- **31 harnesses currently pass.**

## Critical working conventions (read these)

- **I cannot open a JavaFX window in this sandbox** (no display; needs a server + 2nd seat). So the client's
  *drawing* is verified by **compile + eyeball on the user's machine**, not by me. Everything else (model,
  snapshot, engine logic) is unit-tested. When touching the client, compile and rely on the user to confirm
  visuals. This is why the engine is designed so **logic is tested and only `Renderer` drawing is unverified.**
- **Worktree → main sync.** Work happens in a git worktree (`.claude/worktrees/...`); the user runs from the main
  folder `C:\Users\Mayeul\IdeaProjects\Balatry`. **After each change, sync to the main folder** and confirm
  `mvn test` passes there too. Use a full mirror, not a per-file copy:
  `robocopy <worktree>\src <main>\src /MIR` — `git status` collapses untracked directories, so file-by-file
  syncing silently misses new packages (this bit once, after a package rename).
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

- **Jokers: 164** (137 base + 27 new). **38 are inert described stubs** (`b -> b` in `Jokers.java`) — ~24
  base-game (Astronomer, Shortcut, Splash, Four Fingers, Pareidolia, …), Merchant and The Void (need a passive
  slot-bonus hook), and 9 multiplayer ones needing new engine events (The Mimic, Espionnage, Vulture, Telescope,
  Transparent Joker, …). Every joker has a description (`Jokers.Descriptions`) and, where it carries a live
  variable, a `JokerSpec.state(...)` renderer for the tooltip.
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

1. **Finish the scoring animation.** The model half is done and tested; the client half is a first pass and
   **needs eyeballing before tuning** — staged-row layout, beat cadence, pop intensity.
   Known gaps: held-card (Steel) and sin/boss beats have no on-screen anchor (they fall back to a centred
   square); no sound.
2. **Implement the 38 stub jokers.** Three tiers: easy (existing hooks suffice), medium (`HandEvaluator`
   flexibility for Four Fingers / Shortcut / Dyscalculie; trait checks for Pareidolia / Smeared), hard (the
   cross-player ones need new engine events like "an opponent failed a blind").
3. **Assets (free visual win, no code):** drop the card sprite sheet at `src/main/resources/cards/deck.png`
   (4 suit rows H/C/D/S, 13 rank cols 2→A) and a pixel font at `src/main/resources/font/game.ttf`. The client
   loads both with fallbacks. See `src/main/resources/README-assets.md`.
   **Textures are designed but unbuilt** — the agreed plan is one file per item keyed by enum name
   (`/sprites/joker/MAIL_IN_REBATE.png`), an `Assets` cache returning null for missing files, and the existing
   vector look as the fallback. Editions are deliberately **not** textures: they are canvas-drawn effects
   (Foil/Holo/Polychrome as gradients + blend modes, Negative as a precomputed inverted variant).
4. **Reconnect** — the log-replay architecture makes it feasible (send the log, replay, resume) but it is a real
   protocol design task. Kicking is the smaller sibling.
5. **Skip-pack** (optional) — the model forces spending the whole pick budget; a `clearOpening` action would
   allow Balatro-style skipping.

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
