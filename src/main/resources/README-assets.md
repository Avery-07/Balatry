# Client assets

The client loads these at startup and **falls back gracefully** if they're missing, so the
app runs either way — it just looks better once you drop the real files in.

## Pixel font (optional)

Drop a `.ttf` (or `.otf`) pixel font here:

    src/main/resources/font/game.ttf

The client loads `/font/game.ttf`; if absent it falls back to the platform `Monospaced` font.
No code change needed — the loaded font's family is applied to the whole UI at runtime.
Free options that fit the Balatro look: **m6x11**, **monogram**, **m5x7** (check each license).

## Playing-card sprite sheet (optional)

Drop the sheet here:

    src/main/resources/cards/deck.png

Expected grid (matches the sheet you provided):

- **4 rows = suits**, top to bottom: **Heart, Club, Diamond, Spade**
- **13 columns = ranks**, left to right: **2, 3, 4, 5, 6, 7, 8, 9, 10, J, Q, K, A**

Cell size is computed at runtime from the image (`width / 13`, `height / 4`), so any resolution
works as long as the grid is uniform. If the file is absent, cards render as vector 4-color
faces (blue clubs, orange diamonds, red hearts, dark spades).
