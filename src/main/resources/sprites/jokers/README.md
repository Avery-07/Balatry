# Joker face textures

Drop one PNG per joker here. The client loads it as the joker's face wherever that joker is
drawn (owned in the HUD, offered in the shop, offered in a Buffoon pack). **Missing files fall
back to the vector tile** — you can add textures one at a time and the app runs either way.

## Naming

The filename is the joker's **display name with every non-alphanumeric character removed**, keeping
the existing capitalisation, plus `.png`:

| Display name    | Code name        | File            |
|-----------------|------------------|-----------------|
| `Half Joker`    | `HALF_JOKER`     | `HalfJoker.png` |
| `Oops! All 6s`  | `OOPS_ALL_SIXES` | `OopsAll6s.png` |
| `Joker`         | `JOKER`          | `Joker.png`     |

(Spaces, `!`, `-`, etc. are all stripped — see `Renderer.jokerTexture`.)

## Format

Any resolution works — the image is scaled to fit the tile **preserving its aspect ratio** (centered,
letterboxed if the shapes differ), never stretched (`Renderer.imageFit`). The prepared set is 71×95 PNG.
Use transparency for rounded corners; the tiles are drawn as plain rectangles.
