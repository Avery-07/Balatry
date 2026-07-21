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
- 31 harnesses currently pass.

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

- `GameClient` — orchestrator: canvas, loop, host/join, input dispatch, screen switching. Until the match starts
  it hands the whole screen, the keyboard and the clicks to `Menu`.
- `Menu` — the pre-match UI: main menu (name entry, Host/Join + address) and the lobby (roster up to 4, your own
  sleeve/stake, the host's deck picker, Start). It owns its state because no snapshot exists before the match
  begins. **Loadout is picked in the lobby, not the menu** — so everyone can see what everyone else chose. Its
  cyclers send the pick to the server and adopt what comes back in the next `LOBBY` frame, so the roster on
  screen is always the server's, never a local guess.
- `Ui` — shared per-frame context: renderer, snapshot, vm, status, click registries (`buttons`,
  `packButtons`, `selectables`, `jokerSel`), selection state (`selKind`/`selIndex`/`jokerTarget`), `button()` helper.
- `Hand` — the animated hand: reconcile-from-snapshot (keeps selection + motion across frames — this fixed the
  original "selection wiped every frame" bug), fan layout, draw, hit-test, selection→model-index mapping.
- `Hud` — sidebar + top joker/consumable slots + deck pile (persistent every phase).
- `Screen` + `SelectionScreen` / `BlindScreen` / `ShopScreen` / `ResultScreen` / `FinishedScreen` — one per phase's center panel.
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
- **Decks / sleeves / stakes** (`DeckType`, `Sleeve`, `Stake`): the deck is **table-shared** (`MatchConfig.deckType`),
  the sleeve and stake are **per-seat** (`SeatConfig` → `Match.createSeated`). **All of them are live now.**
  Compositions (Abandoned/Crowded/Checkered/Erratic, Fracture) in `Decks`/`LoadoutTests`; behavioural decks —
  Plasma (target ×2 in `Match.getCurrentTarget`, chips/mult averaged in `Run.balanceIfPlasma` *after* the engine
  so joker arithmetic is untouched), Bazaar (pack surcharge + reroll refreshes packs — `fillPacks` folds `rerolls`
  into its salt only after a refresh so other decks' openers stay stable), Ghost/Anaglyph (shop spectrals; boss
  pays Double Tag + Fool) — plus Frugal ($2/hand + $1/discard, **no interest**, in `RoundSettlement.cashOut`) and
  Celestial (+2 levels every hand type per ante in `Run.beginAnte`; the shop's Planet band falls back to Tarot).
  The seat-aware shop roll is `ShopPool.roll(Run, stream)` (default delegates to the seat-blind roll, so test
  pools are unaffected). Covered by `LoadoutTests` (composition) and `LoadoutEffectTests` (behaviour).
- **Stickers: all 8 live** (`Sticker`, `StickerState`, and the lifecycle owner `model.game.player.Stickers`).
  Floating (keyed drift per hand), Delayed (transient `Card.isSuppressed` silence, first hand only — cleared
  unconditionally in settlement so a lost round can't leak it), Fragile (debuffed when a hand scores <10% of the
  target), Sticky (growing sell toll, charged in `Board.sell`, refused when unaffordable). The Red/Blue/Gold
  stakes now roll them onto shop jokers (1-in-4, cumulative pools — `Stickers.poolFor`) via `Shop.rollSlot`.
  Covered by `StickerTests`.
- Bosses: the ante boss is locked at ante start (visible during selection) and has an `effect()` description.

## What's next (recommended order)

1. ~~**Match-over / FINISHED screen**~~ — done: `FinishedScreen` (VICTORY/DEFEAT verdict, placing, final ranked
   standings with podium colors). Registered in `GameClient.screens`. Drawing unverified as usual — eyeball it.
2. ~~**A loadout picker + lobby**~~ — done. The server now has a **lobby phase**: each client announces its own
   name/sleeve/stake on connect (`JOIN`), the server assigns seats and broadcasts the roster (`LOBBY`), and the
   host's `BEGIN` broadcasts one `START` setup that every side builds its match from. Nothing guesses the roster
   any more, so the old silent-desync footgun is gone. Covered by `LobbyTests`; `NetTests` plays a full match
   through it.
   **Disconnects are handled too:** a dropped socket becomes an `Action.PlayerLeft` in the log (so every seat
   learns of it at the same point in the same replay). The seat forfeits any live round, is excluded from every
   barrier, and keeps its earned points; dropping below 2 active seats ends the match. Covered by
   `DisconnectTests`, including a real socket close. **Still missing:** reconnect and kicking.
3. ~~**The 4 missing stickers**~~ / ~~**inert decks & sleeves**~~ — done (see Current content state). Every deck,
   sleeve and stake in the doc now does what it says. `StickerTests` + `LoadoutEffectTests` cover them.
4. ~~**Juice pass (round 1)**~~ — done, unverified visually:
   - **Count-ups + pops**: `client.engine.Counter` (tested in `EngineTests`) — displayed value glides to the real
     one, increases spike a `popScale()` the renderer multiplies into the font size. `Hud` holds four (score,
     chips, mult, money) and feeds them from the snapshot each frame; `GameClient` advances `hud` in the loop.
   - **Card fly-outs**: `Hand` keeps entities the snapshot dropped in an `exiting` list (~0.45s, fading via
     `gc().setGlobalAlpha`) — played cards fly up, discarded fly off right. `BlindScreen` calls
     `hand.expectExit(PLAYED/DISCARDED)` before submitting so the next reconcile knows which way.
   - **Sticker/edition visibility**: `ShopItem.badge` + new `JokerView(name, badge, debuffed)` in the snapshot
     ("Foil · Sticky $4"; Sticky shows its **live** toll). Shop tiles and the top joker bar draw a gold footer
     strip; debuffed jokers grey out. Tested in `SnapshotTests.jokerBadgeSnapshot`.
   **Round 2 candidates** (after eyeballing): hand-type + score popup at play position, joker wiggle on trigger,
   money delta floaters, screen shake on big scores. Sound is still entirely absent.
   **Round 2 (user-reported fixes), done:**
   - **Hover works now.** `Ui` tracks the mouse (`mouseX/Y`), screens register `Ui.Tip` regions, and
     `Overlays.tooltip` draws the topmost one under the cursor after everything else. Registered: hand cards
     (readable title + enhancement/seal), held jokers (description + badge + debuff note), held items
     (description + target demands), shop slots/vouchers/packs (the tooltip strings that already existed but
     were never drawn).
   - **Deck-pile hover** opens the full-deck view (`Overlays.deckContents`): 13×4 grid, spent cards greyed,
     duplicate counts (Crowded/Erratic) as "live/total" under the cell. Data: `MatchSnapshot.deckCards`
     (`DeckCardView(rank, suit, live)`), live = draw pile + hand during a round, everything otherwise.
   - **Play preview**: selecting cards shows the detected hand ("Two Pair  lv.2") above the chips×mult boxes,
     and the boxes preview what that play scores at this seat's levels (`MatchSnapshot.handLevels`, evaluated
     client-side with the same `HandEvaluator` the model scores with). Deselecting falls back to the last play.
   - **Targeted consumables can't be wasted**: `ConsumableSpec.minTargets` (12 tarots annotated; Death=2,
     World=3), enforced in `Run.useConsumable` (throws, card kept) and mirrored in the client (greyed Use +
     hint). Covered in `TarotTests.targetRequirements`.
   - **Pack names fixed**: `BoosterPack` had no `toString`, so shop tiles showed object hashes. Now
     `displayName()` ("Mega Arcana Pack").
   **Round 3 (user-reported fixes), done:**
   - **Editions everywhere**: hand-card labels carry the edition (`<FOIL>` marker in `describe`), and the card
     tooltip reads it; held consumables/relics gained a badge (`ItemView.badge`) shown in their tooltip.
   - **Joker live state in the tooltip**: `JokerSpec.state(Function<JokerCard,String>)` (builder) +
     `stateOf(card)`. Annotated: Mail-In Rebate (rank), To Do List (hand), Ancient Joker (suit), The Idol (card),
     Ice Cream / Popcorn (decay left), Green Joker / Ride the Bus / Castle / Siamese Cat (bonus), Ramen (Xmult).
     Un-annotated jokers with a non-zero counter fall back to "Value: n"; zero shows nothing. Ships as
     `JokerView.state`.
   - **Deck view is the real deck**: one drawn card per physical card (destroyed = absent, created = visible,
     duplicates side by side, rows overlap when fat); **live = still in the draw pile**, so held cards grey out
     too. NOTE: the model package rename `model.cards` → `model.items` happened this session (user side).
   **Round 4 (animation pass), done — all drawing unverified as usual:**
   - **Menu transitions**: `client.engine.Fader` (fade-to-black, switch exactly once at full black, fade out);
     `GameClient` routes every screen switch through it (menu→lobby, lobby→match, back to menu). Menus also
     slide up on entry (`Menu.enterMode` + a Tween; hitboxes are shifted to match during the slide).
   - **Permanent card animation**: `client.engine.Idle` — pure (time, seed) sway/bob; hand cards sway ±1.4°
     and bob ±2px, joker/consumable tiles bob, all de-phased by seed so rows ripple.
   - **Face-down cards + flip**: the four hiding bosses (House / Wheel / Fish / Mark) are **implemented in the
     model** (`BossBlind.hide*` flags, `Round.maybeFaceDown` + `isFaceDown`). The Wheel's roll is salted by a
     **round-local draw counter, never `DeckCard.id`** (ids are a JVM-global counter that UI throwaway cards
     also advance — not replay-safe). The snapshot **masks** hidden cards (rank/suit −1, blank label) so no
     tooltip/sort/preview can leak them. `CardEntity` carries a flip tween (0.28s, squash through edge-on);
     `Renderer.card(..., flipT)` draws the back past the midpoint — vector back now, deck texture hook
     (`backSheet`) ready. Covered by `FaceDownTests`.
   - **Drag & drop — one grammar for the whole table**: press-move-release with a 7px threshold (clicks still
     work; a finished drag swallows its click). Hand cards drag as themselves (`Hand.manualRank` commits the
     visual order; Sort buttons and manual drag cancel each other). **Every tile row is a retained
     `client.engine.TileRow`** (tested): jokers, consumables/relics, and all three shop shelves. Tiles match
     across frames by **`Card.id`** (new: a cosmetic JVM-global id on the `Card` base — NEVER use it as an RNG
     salt, see the field note), so a reorder broadcast *slides* tiles instead of teleporting. Dragging parts the
     row around the held tile; release in the row's band submits `MoveJoker`/`MoveConsumable`/`MoveRelic`
     (consumables and relics only reorder within their own class); any invalid drop just glides back — no
     special case, the layout retargets every tile every frame. **Shop tiles have no drop target by design**:
     they lift and glide home, purely for feel consistency. Sold shop slots render as static holes outside the
     row; `Ui` owns the five rows (Hud/ShopScreen lay out + draw, GameClient routes input).
   **Maintainability pass (4-agent /simplify), applied:** display names now have single authorities —
   `HandType.displayName()`, `Rank/Suit.displayName()`, one `Fmt.title` (public; `MatchSnapshot` delegates) —
   which also fixed `Fmt.cardTitle`'s suit array being in sprite-row order instead of enum order (tooltips called
   spades "Hearts"). `Palette.PODIUM_GOLD` + `MatchSnapshot.myStanding()` de-dupe the standings screens. The felt
   gradient is static; the play preview is memoized on (snapshot, selected ids). `Hand`'s exit bookkeeping is one
   mutable holder; dead methods dropped. Menu hitboxes now shift via `Ui.regionOffsetY` applied at registration
   (covers every region type), replacing the buttons-only post-hoc patch. Drag routing is a registered
   `DragTarget(row, active, onDrop)` list — adding a draggable surface is one entry, no string-keyed switches.
   Skipped knowingly: caching the deck-hover grouping (hover-gated, trivial), unifying `Hand` onto `TileRow`
   (split justified: fan layout, selection, flip), lazy tooltip building (not worth the plumbing).
   **Round 5 — the scoring animation (in progress).** Design: played cards stand centre-screen and each
   contribution plays as a **trigger + effect pair** — the source card/joker pops, a small square beside it names
   what it did — with the readouts climbing beat by beat.
   - **Model half, done & tested** (`ScoringEventTests`, 27 checks): `ScoringEvent(sourceId, sourceName, kind,
     amount, chipsAfter, multAfter)` with kinds BASE/CHIPS/MULT/XMULT/MONEY/RETRIGGER/DESTROYED/BALANCE.
     `ScoringSession` records every mutator against whichever card the engine last named via `setSource`, so the
     log's order **is** the engine's execution order for free; `ScoringEngine` names each contributor and emits
     the ownerless BASE beat; `Run.addMoney` joins the timeline when a session is live (gold seal, Lucky, money
     jokers); `Run.balanceIfPlasma` appends its BALANCE beat (otherwise the reel would count to totals the model
     then replaced). **Key invariant, asserted: replaying the log's final `chipsAfter × multAfter` reproduces the
     banked score** — the client never re-derives arithmetic. Ships as `MatchSnapshot.lastPlay()`.
   - **Client half, first pass**: `client.engine.ScoreReel` (pure, tested) walks the timeline, accelerating
     ÷1.1 every 2 beats down to a 0.07s floor — no skipping, no batching, no fast-forward (per design). `Hand`
     now *stages* played cards centre-screen instead of flying them off, releasing them when the reel drains;
     `Overlays.scoreEffect` draws the effect square (anchored under the source, above it near the screen bottom);
     `Hud` counters follow the live beat's running totals while the reel runs.
   - **Not done yet:** held-card (Steel) and sin/boss beats have no on-screen anchor yet; the staged-row layout
     and all timings are unverified — **this is the part that most needs eyeballing.**
5. **Skip-pack** (optional) — the model forces spending the whole pick budget; a `clearOpening` action would allow Balatro-style skipping.
6. **Implement the ~35 stub jokers** (24 base-game + Merchant/The Void + 9 multiplayer ones needing engine events).
7. **Assets (free visual win, no code):** drop the card sprite sheet at `src/main/resources/cards/deck.png`
   (4 suit rows H/C/D/S, 13 rank cols 2→A) and a pixel font at `src/main/resources/font/game.ttf`. The client
   loads both with fallbacks. See `src/main/resources/README-assets.md`.

## Gotchas
- `MatchSnapshot.of` has a test seam: `of(Match, PlayerId)` (the client path delegates). Use it in harnesses.
- The `Rank` enum ordinal maps directly to sprite columns; `Suit` is mapped explicitly (HEARTS→row0, CLUBS→1,
  DIAMONDS→2, SPADES→3) in `Renderer`/`GameClient`.
- Adding a snapshot field means updating the record **and** the `of(...)` constructor call (easy to duplicate an arg — watch it).
- **Stakes are per-seat, so chip targets are too.** Ask `match.getCurrentTarget(playerId)`, never the no-arg form
  (that one is the White-stake baseline). Same for cash-out (`run.getStake().rewardFor(blind)`) and reroll pricing.
- Stake target growth compounds **per ante above the first**, so ante 1 is identical at every stake by design.
- Starting vouchers (Silk sleeve, Eclipse deck) use `run.grantVoucher`, not `redeemVoucher` — the latter would
  burn the seat's one redemption for that ante.
- `MatchClient.getLocalHost()` is **null until the match starts**. Guard with `isStarted()`; the client exists
  during the lobby, when there is no model at all. `MatchViewModel` is only built once `onStarted` fires.
- Seat 0 is the host by definition (first to join). Deck picking and starting are server-enforced, not just
  hidden in the UI — see `MatchServer.handleLobby`.
- Player-typed names reach the wire, so they go through `MatchSetup.sanitize` (it strips `,` `:` and tabs, which
  are the roster line's separators). `LobbyTests` covers the smuggling case.
- **Barriers must be measured against `match.getActiveSeats()`, never `getSeats()`** — the latter still includes
  players who left, so using it deadlocks the table on a disconnect. Same for anything that waits on "everyone".
- Seats are never removed from a running match (indices are baked into every logged action); a departed seat
  stays in `getSeats()` and in the standings, flagged by `hasDeparted`. Seat re-indexing happens only in the
  lobby, where no log exists yet.
- **The host leaving the lobby closes it** (the server lives in the host's process): the server broadcasts
  `CLOSED` and drops everyone, and each guest's `onClosed` sends it back to the main menu. A *guest* leaving is
  survivable — the roster just shrinks and the seats below move up.
- `MatchClient.Callbacks.onClosed` fires exactly once for any end of connection (closed lobby, lost server, our
  own disconnect). `GameClient` ignores it when it tore the connection down itself (`client == null`).
