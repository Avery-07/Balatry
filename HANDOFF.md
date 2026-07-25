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
- **31 harnesses.** All pass except `JokerTests`, which trips two *pre-existing content* checks (not regressions —
  see Known issues). Everything else is green.

## Critical working conventions (read these)

- **I cannot open a JavaFX window in this sandbox** (no display; needs a server + 2nd seat). So the client's
  *drawing* is verified by **compile + eyeball on the user's machine**, not by me. Everything else (model,
  snapshot, engine logic) is unit-tested. When touching the client, compile and rely on the user to confirm
  visuals. This is why the engine is designed so **logic is tested and only `Renderer` drawing is unverified.**
- **The main folder is authoritative — do NOT mirror the worktree over it.** Earlier sessions worked in a git
  worktree and mirrored to the main folder with `robocopy /MIR`. That is now **dangerous**: the user edits the
  main folder `C:\Users\Mayeul\IdeaProjects\Balatry` directly, and a `/MIR` mirror has **silently reverted their
  work** — the joker implementations were lost this way (the git reflog shows "brought back joker changes that
  were accidentally removed"). **Work directly in the main folder.** If you must use the worktree, copy back only
  the specific files you touched — never `/MIR`. Assume the worktree is stale relative to main.
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

- **Jokers: 165** (137 base + new; Blueprint et al. added). **26 are inert stubs** (`b -> b` in `Jokers.java`) —
  the backlog (HIKER, SPLASH, FOUR_FINGERS, Astronomer, Shortcut, the cross-player ones …). The rest are
  implemented. Every joker has a description (`Jokers.Descriptions`), and ~36 carry a `.state` tooltip descriptor.
- **Joker current-effect tooltips** use `JokerSpec.state`, a pure descriptor `(JokerCard, JokerInfo) -> String`
  (with a 1-arg `(JokerCard) -> String` overload for counter-only jokers). `model.game.player.JokerInfo` is a
  **read-only** view of the run (deck size, money, jokers, cards sold, discards remaining, …): no setters, so a
  descriptor *cannot* mutate the model. This deliberately replaces an `ON_HOVERED` **trigger** — hover is a
  local, unlogged, FX-thread event, and a firing trigger there runs a full mutating `JokerEffect`, which would
  desync the seats. **Do not re-add ON_HOVERED as a trigger.** To describe a new joker, extend `JokerInfo` (add
  a reader, never a setter) and add a `.state(...)`. Evaluated in `MatchSnapshot.jokerViews` (FX thread, settled
  model), so it must stay pure — `JokerTests.currentEffectDescriptors` asserts describing mutates nothing.
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
- **Shop**: default **3 card slots / 3 packs / 2 vouchers** (verified empirically). Packs are capped at **2 buys
  per shop visit** ("choose 2 of 3", `ShopSetup.packBuyLimit`, resets each blind); vouchers are **1 redemption
  per ante** and **stable across an ante's blinds** — the voucher roll is salted by ante, not shop index, so they
  no longer reroll every blind. The shop UI shows both remaining allowances beside the row headers. The **Lust
  sin** adds 2 extra card/pack items on its ante *by design* (`LustModifier.EXTRA_ITEMS`), so a Lust shop is not
  "3 items" — that is the sin, not a bug.
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

## What's next

**The user's current wishlist** (their priority), each with a pointer to where the work lives:

1. **Fix the scoring-animation timing.** Two concrete bugs, both with known root cause:
   - *The final score appears before the chips×mult reel finishes.* `Hud` drives the round-score `Counter` from
     the snapshot's already-banked score while chips/mult follow the reel, so the total shows the answer early.
     Fix: while `ui.reel.playing()`, drive the score readout from the reel's running total (prior round score +
     the current beat's `chipsAfter × multAfter`), not the snapshot's final value.
   - *The last played card's trigger animation fires twice.* `ScoreReel` keeps `currentIndex()` on the last beat
     during its settle tail (`draining`), so `Ui.liveEvent()` still returns that beat and `scorePop` pops the card
     again. Fix: return no live event while `reel.draining()` (or drop the index when the last beat completes).
   - Still-open animation gaps from the first pass: held-card (Steel) and sin/boss beats have no on-screen anchor
     (they fall back to a centred square); no sound.
2. **Background reacts to phase + a vortex effect.** `PaintField` already does the swirl; add (a) per-phase
   palettes/params (selection / blind / result / shop) that `Background` lerps toward when `ui.s.phase()` changes,
   and (b) a stronger central **vortex** (raise `spinAmount`/`spinSpeed`, or make them grow toward the centre).
   `Background` may read the phase (not hidden info) but must otherwise stay model-blind. Colours are three hex
   ints on `PaintField`.
3. **Finish the joker current-effect tooltips.** Mechanism is `JokerSpec.state` + `JokerInfo` (see Current content
   state — **not** a trigger). Done: the 5 former ON_HOVERED jokers converted off the mutating pattern, plus Wee
   Joker / Obelisk / Campfire / Loyalty Card / Erosion / Stone Joker / Fortune Teller / Bull / Abstract. **To do:**
   (a) audit the ~90 "plain" jokers for ones that compute a value from run state and would benefit (Bootstraps,
   Baseball Card, Blackboard, …) — extend `JokerInfo` as needed; (b) review the existing `.state` strings for
   correct numbers/wording (the user flagged some may need correcting).
4. **Run Info overlay: add tags, hand-type levels + play counts, and redeemed vouchers.** `Overlays.runInfo` shows
   only standings today. `handLevels` is already in the snapshot; pending tags, per-hand-type play counts
   (`PlayerStats`), and redeemed vouchers need new snapshot fields.
5. **Hovering the skip button shows the tag it would grant.** In `SelectionScreen`, register a `ui.tip` on the
   skip button with the skip tag's description. `skipTag` is in the snapshot as a *name* — add its description text.
6. **Verify rarity weighting** — confirm `Jokers.weightedRandom` makes rarer jokers genuinely harder to roll; a
   test asserting the weight ordering (Common > Uncommon > Rare > Legendary appearance rate) would pin it.
7. **GPU / faster background** (perf, optional). Canvas is CPU; the cheap first step is moving `field.render` to a
   daemon thread with a double buffer (noted in `Background`'s javadoc — the effect is cosmetic, needn't be
   frame-synced). True GPU needs a different surface (an FX node fed by a shader lib) — a larger rewrite.

**Standing backlog:**

8. **Implement the 26 stub jokers.** Tiers: easy (existing hooks suffice), medium (`HandEvaluator` flexibility for
   Four Fingers / Shortcut / Dyscalculie; trait checks for Pareidolia / Smeared), hard (cross-player ones need new
   engine events like "an opponent failed a blind").
9. **Assets (free visual win, no code):** card sprite sheet at `src/main/resources/cards/deck.png` (4 suit rows
   H/C/D/S, 13 rank cols 2→A) and a pixel font at `src/main/resources/font/game.ttf`; the client loads both with
   fallbacks (`README-assets.md`). Textures are designed but unbuilt — one file per item keyed by enum name
   (`/sprites/joker/MAIL_IN_REBATE.png`), an `Assets` cache returning null for misses, vector look as fallback.
   Editions are deliberately **not** textures: canvas-drawn effects (Foil/Holo/Polychrome as gradients + blend
   modes, Negative as a precomputed inverted variant).
10. **Reconnect / kicking** — the log-replay architecture makes reconnect feasible (send the log, replay, resume)
    but it is a real protocol design task; kicking is the smaller sibling.
11. **Skip-pack** (optional) — the model forces spending the whole pick budget; a `clearOpening` action would
    allow Balatro-style skipping.

**Deliberately out of scope so far** (decide consciously before starting): endless mode past ante 8 (placeholder
scaling exists), run persistence/saves, spectating, and anti-cheat (lockstep means every client holds all hidden
information — fine for friends, unfixable for strangers without a server-authoritative rewrite).

## Known issues (open)

- **`JokerTests` — two pre-existing content failures, both one-liners.** The catalog assertion says 164 jokers
  but there are **165** (bump it), and **`Scalper`** is a bare stub with an empty description (give it text, or a
  placeholder like the other stubs) which trips "every joker has a description". Neither is a regression.
- **Scoring-animation timing** — see What's next #1 (score shows early; last card pops twice).
- **Pack-buy limit is per shop visit** (resets each blind). If it should be per *ante*, move the counter off
  `Shop` onto the ante like the voucher redemption.
- **A voucher redeemed earlier in an ante reappears greyed** in that ante's later blinds (shown non-redeemable
  rather than removed). Acceptable, but noted.

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
