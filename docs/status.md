# Status

**Current:** v1.0.0, the first public release (being prepared).

## v1.0.0

- `main` starts with the initial import commit: the source zip contents,
  byte for byte, plus `.gitattributes` (no line-ending conversion) and
  `.gitignore`.
- `feat/fizzfollies-development` adds the README (with screenshots rendered
  by the game's own `UiShots` and `MapShot` tests), CHANGELOG, LICENSE (MIT for the code;
  the bundled fonts are Fascinate, Josefin Sans and Limelight under OFL 1.1
  and Ultra under Apache 2.0, texts in `docs/licenses/`), project docs, the
  Claude Code skills, agent and hooks, CI, and the branch protection
  settings.
- The scripts use `/home/claude/fizz` as the project path (not
  `fizzfollies`); CI recreates exactly that.
- `jvmtest/Calibrate.kt` and `jvmtest/Soften.kt` rewrite
  `src/com/pranvir/fizz/LevelData.kt`, and `IconGen` rewrites `res/`. None
  of them run in CI or without the owner's approval.
- Release asset: the original APK, renamed `FizzFollies-v1.0.0.apk`
  (SHA-256 `eb9cc54404be010f6ce3476878af54d7c2d39ee763ce6fb7b32bd2da4aea8795`),
  signed with the original key.

## Next

Nothing planned. Before the first release built from source, create the new
permanent signing key (see [workflow.md](workflow.md#signing-keys)).
