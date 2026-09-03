# AGENTS.md

Guidance for AI coding assistants (Claude Code, Codex, Cursor, Gemini CLI, Kiro, etc.)
working in this repository. Tool-specific files (`CLAUDE.md`, `GEMINI.md`) point here for
the parts that apply to every tool.

## Related repositories: use full paths, never copies

Assistants can read outside the current folder. Older tools could not, so related code
was sometimes **copied into** a repo just so the assistant could see it. Those copies go
stale, confuse builds and searches, and occasionally get compiled by accident.

When you need code from a related project, **open it at its real location**:

| Project | Full path | Role |
|---------|-----------|------|
| osmdroid-lostrat (this fork) | `F:\OSMDROID-LOSTRAT` | Library. Publishes `com.github.lostrat:osmdroid-*` to Maven Local. |
| Test-Osmdroid-LostRat | `F:\ANDROIDPROJECT\TestOsmdroidLostRat` | Integration test app that consumes this fork from Maven Local. |
| upstream osmdroid clone | `F:\ANDROIDPROJECT\osmdroid` | Archived upstream, for diffing only. Do not edit. |

## Prune copied-in reference code when you see it

If you find a folder that is a copy of another repository or module (for example an
`osmdroid-android/` directory inside an app that already depends on the published
artifact, or a whole sibling project vendored under `src/`), treat it as a candidate for
removal:

1. Confirm it is not on any Gradle source set or `settings.gradle` include.
2. Compare it with the real project at its full path; note if anything in the copy is
   newer than the original and tell the user.
3. Suggest deleting it and replacing it with a full-path pointer in this file.
4. If the user wants to keep a small piece as reference, move it under `reference/`
   (see `reference/README.md`), never under a source set.

Do not delete such folders yourself; list them with full paths and let the user do it.

## Other conventions

- Never `git commit` or `git push`. Present a commit plan instead.
- `docs/` is the curated documentation (see the table in `README.md`); read it before
  changing the overlay layer system, density scaling, polyline colouring or the tile
  cache. `reference/notes/` is uncurated session material and may be stale.
- Complex work gets a dated HTML report under `docs/` plus an entry in
  `docs/CHANGELOG-lostrat.{md,html}` and, when user-facing, the fork section at the top
  of `README.md`.
- Scratch code and session notes go under `reference/`; machine-specific captures and
  anything from the user's private apps go under the gitignored `reference/local/`.
  Private app code must never be committed here, even as reference.
- Text files use CRLF line endings. Patch them with newline-preserving edits.
- Build with the Gradle wrapper (`./gradlew`). The version of record is
  `pom.version` in `gradle.properties`.
