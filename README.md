# FIZZ FOLLIES

*A Fizzworks Picture, in Glorious Fizzicolor.*

A bubble shooter for Android in the style of a 1930s rubber-hose cartoon. It is written in Kotlin with no game engine. All the art, animation, music and sound are generated in code.

Baron Von Boil has bottled up every note in town inside a mountain of bubbles. You play Fizz, a dancing seltzer bottle, and blast them loose one reel at a time.

## The show

The game has **10 reels**, each with **12 scenes**, for **120 levels** in total. Every reel has its own painted scenery and ends with a fight against the Baron, who wears a different hat each time.

| Reel | Name | What's new |
|---|---|---|
| 1 | Barnyard Hoedown | The basics, music notes, the Applause-O-Meter |
| 2 | Big Top Ballyhoo | Stones, bombs, the Jazz Bubble |
| 3 | Spooky Shuffle | Ghost bubbles that change colour, the Trumpet Blast |
| 4 | Sea Shanty | Caged critters; *Beat the Drop* (the ceiling lowers) |
| 5 | Toyland Two-Step | Ink blots that spread |
| 6 | Skyscraper Swing | Everything mixed, six colours |
| 7 | Moonbeam Mambo | Double-locked cages |
| 8 | Snowball Serenade | Ink, iron bars and a falling sky |
| 9 | Jungle Jamboree | Six colours, no mercy |
| 10 | The Boilerworks | The grand finale |

### Goals

- **Clear the Stage:** pop and drop the bubbles. When only three are left, they take a bow and the scene is won.
- **Free the Music:** release every bubble that holds a music note.
- **Beat the Drop:** the scenery comes down a row every few shots, so clear the stage first.
- **Boil the Baron:** hit him directly, or pop bubbles right next to him. He keeps boiling over with more.

### Difficulty

Difficulty rises in chunks. Within each reel it climbs scene by scene. Each new reel starts a little easier than the last one ended, but higher than the previous reel started. Each reel also brings in a new trick.

Every level was played by a bot, which set the shot budgets, par and star scores. A deliberately clumsy bot (wobbly aim, the odd poor choice) still has to clear each level at least half the time.

### Progress

- **Stars:** each level awards 1 to 3 stars.
- **Opening reels:** you need enough stars, and the previous reel's Baron must be beaten.
- **Tickets:** earned by clearing levels. Spend them on boosters (Spyglass, +5 shots, Firecracker) or on an Encore after losing.
- **Power shots:** big pops fill the Applause-O-Meter. When it's full, tap it for a power shot. There are three: Firecracker, Jazz Bubble and Trumpet Blast. Power shots never cost a shot.

## Controls

- Drag anywhere above Fizz to aim, and let go to fire.
- Tap Fizz to swap the loaded bubble with the one in his glove.
- Tap the Applause-O-Meter when it's full.

## The 1930s look

- **Inked art and faces:** every bubble critter is hand-inked, and each colour has its own face, so colours can be told apart without colour vision.
- **Line boil:** outlines shimmer like hand-drawn cels.
- **Rubber-hose motion:** everything bounces in time with the band.
- **Old film effects:** grain, scratches, dust, flicker, gate weave, iris wipes and silent-film title cards.
- **Theatre setting:** velvet curtains, footlights and painted scenery.
- **Music:** a synthesised hot-jazz band (tuba, banjo, brushes, clarinet, xylophone) that swells with the applause, plus optional record crackle.
- **Sound effects:** slide whistles, bonks and a sad trombone.

## Build

```
./build.sh          # build/FizzFollies.apk
```

## Tests (JVM, using a small android.graphics shim)

```
./t.sh UiShots Monkey Campaign AudioTest Report LevelSheet
./t.sh Calibrate    # regenerates LevelData.kt from bot play
```
