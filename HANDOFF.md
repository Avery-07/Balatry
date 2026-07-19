# Balatry — session handoff

A multiplayer recreation of *Balatro*. **Java 21, Maven.** Pure-Java model + a JavaFX client.
This doc is the "start here" for a fresh session. Read it, then dig into the files it points at.

## Build & run

- `mvn test` — compiles everything and runs the hand-rolled test harnesses. **No JUnit.** Each test is a
  `*Tests` class with a `public static void main`; `harness.HarnessRunner` discovers every `*Tests` under
  `target/test-classes/model/` and runs each in its own JVM. So **test classes must live under a `model.*`
  package** to be discovered (e.g. client tests are in `model.client`, engine tests in `model.engine`).
- `mvn javafx:run` — launches the client (`client.game.GameClient`). Needs a running server
  (`mvn exec:java@server`) **and a second seat** to reach the live screens (2-player lockstep).
- 24 harnesses currently pass.

## Critical working conventions (read these)

- **I cannot open a JavaFX window in this sandbox** (no display; needs a server + 2nd seat). So the client's
  *drawing* is verified by **compile + eyeball on the user's machine**, not by me. Everything else (model,
  snapshot, engine logic) is unit-tested. When touching the client, compile and rely on the user to confirm
  visuals. This is why the engine is designed so **logic is tested and only `Renderer` drawing is unverified.**
- **Worktree → main sync.** Work happens in a git worktree
  (`.claude/worktrees/...`); the user runs from the main folder `C:\Users\Mayeul\IdeaProjects\Balatry`.
  **After each change, copy the touched files to the main folder** and confirm `mvn test` passes there too.
- **Determinism is sacred.** Same seed + same actions → bit-identical replays, mirrored across seats
  (`DeterminismTests`). Any RNG use must be keyed/salted deterministically. `DeckCard.id` and joker
  `SEAT_COUPLING` exist so client animation / cross-player jokers don't break this.
- Reference docs the user provided live in `C:\Users\Mayeul\Downloads\Balatry Documentation\`
  (`Balatry Jokers.md`, `Balatry Game Content.md`, `Balatro Consumable Reference.md`, `Balatry Overview & Sins.md`).

## Architecture

```
model.*  (pure game logic, deterministic, fully tested)
   └─ MatchSnapshot   (client/)  — immutable, seat-relative DTO built from the model each frame.
                                    THE information boundary (opponents get only points+rank). Tested via
                                    SnapshotTests. Any UI reads this, never the model directly.
   └─ MatchViewModel  (client/)  — holds an FX property<MatchSnapshot>; the single action choke point.
   └─ client.game.GameClient     — the shippable client: a JavaFX Canvas redrawn by an AnimationTimer.
   └─ client.engine.*            — pure, rendering-agnostic, unit-tested (EngineTests): Easing, Tween,
                                    Motion, Layout (fan + hit-test Rect), CardEntity, Reconciler.
```

**The client is a game-loop Canvas renderer** (chosen over the old retained-node JavaFX because the goal is a
*shippable, juicy* Balatro-like). It was decomposed from a monolith into focused components in `client.game`:

- `GameClient` — orchestrator: canvas, loop, connect, input dispatch, screen switching.
- `Ui` — shared per-frame context: renderer, snapshot, vm, status, click registries (`buttons`,
  `packButtons`, `selectables`, `jokerSel`), selection state (`selKind`/`selIndex`/`jokerTarget`), `button()` helper.
- `Hand` — the animated hand: reconcile-from-snapshot (keeps selection + motion across frames — this fixed the
  original "selection wiped every frame" bug), fan layout, draw, hit-test, selection→model-index mapping.
- `Hud` — sidebar + top joker/consumable slots + deck pile (persistent every phase).
- `Screen` + `SelectionScreen` / `BlindScreen` / `ShopScreen` / `ResultScreen` — one per phase's center panel.
- `Overlays` — contextual Buy/Use/Sell buttons, relic-as-tarot targeting, pack-opening modal, Run Info standings.
- `Renderer` — the only untested piece: draw primitives (panels, text, sprite-or-vector cards).
- `Palette` / `Fmt` — colors, display strings.

### Client interaction model (important, matches the game design)
- **Select an item → contextual action buttons appear beside it** (Balatro-style). Shop items → Buy; held
  jokers → Sell; held consumables → Use/Sell.
- **Relics are used like tarots**: select the target **card(s) in hand**, select the relic, hit **Use** — it
  derives rank/suit/hand-type from the selection. Katadesmos reads a **selected joker**. Pyre/Limos/Harpax are
  random/all-above (no target). Aegis/Metabole/Mimesis: just Use. (No relic needs a seat picker anymore.)
- **Pack opening** is a modal overlay: buying a pack auto-opens it; click options to pick until the budget is spent.

## Current content state

- **Jokers: 164** (137 base per the doc + 27 new). 16 of the new ones fully implemented; ~11 are **inert
  described stubs** needing engine hooks (The Void, Merchant, Dyscalculie, Chef Joker, StrawBerry, Curator,
  Espionnage, Transparent Joker, The Mimic, Vulture, Telescope) — they sit alongside ~20 base-game stubs
  (Astronomer, Shortcut, Burglar, …). Every joker has a description (centralized map in `Jokers.Descriptions`).
- **Consumables/vouchers/relics: fully described.** Jokers described too (short text; richer prose "later").
- **Relics** were redesigned this session: Pyre = destroys a consumable from every seat above; Limos & Harpax =
  random seat above (`RelicKind.RANDOM_RIVAL`); Katadesmos = joker-slot from a selected joker.
- Every seat starts with **$4**. Relics **share the consumable slot pool**.
- Bosses: the ante boss is locked at ante start (visible during selection) and has an `effect()` description.

## What's next (recommended order)

1. **Match-over / FINISHED screen** — currently `FINISHED` just prints its name; needs a winner + final standings screen.
2. **Juice pass** — score count-up, card fly-out on play/discard (reconciler already supports exit), chip/mult pops.
3. **Skip-pack** (optional) — the model forces spending the whole pick budget; a `clearOpening` action would allow Balatro-style skipping.
4. **Implement the ~11 stub jokers** (multiplayer ones need standings/engine hooks).
5. **Assets (free visual win, no code):** drop the card sprite sheet at `src/main/resources/cards/deck.png`
   (4 suit rows H/C/D/S, 13 rank cols 2→A) and a pixel font at `src/main/resources/font/game.ttf`. The client
   loads both with fallbacks. See `src/main/resources/README-assets.md`.

## Gotchas
- `MatchSnapshot.of` has a test seam: `of(Match, PlayerId)` (the client path delegates). Use it in harnesses.
- The `Rank` enum ordinal maps directly to sprite columns; `Suit` is mapped explicitly (HEARTS→row0, CLUBS→1,
  DIAMONDS→2, SPADES→3) in `Renderer`/`GameClient`.
- Adding a snapshot field means updating the record **and** the `of(...)` constructor call (easy to duplicate an arg — watch it).
