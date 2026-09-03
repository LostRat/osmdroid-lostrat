# reference/

Reference code and notes kept with the LostRat osmdroid fork for local development.
**Nothing in this folder is on a Gradle source set.** It is not compiled, not tested,
and not maintained. Copy a file into a module (or a sample fragment / unit test) before
relying on it, and expect to update it for current APIs.

Android Studio still indexes these files, so search and "Go to symbol" work.

Formal fork documentation lives in [`docs/`](../docs/) (changelog, dated HTML reports,
layer-system guide). This folder is for the working material behind it.

## Layout

| Folder | What it holds | Tracked? |
|--------|---------------|----------|
| `notes/` | AI-assisted session notes and analyses from Aug 2025 onward: overlay performance, cache manager, marker tap fixes, 16 KB page size, hardware-layer polyline experiments, density scaling, and one HTML optimization report. | yes |
| `local/` | Captured diffs, build logs, bug reports, extracted jars, and snapshots from the user's private apps. Never committed. | no (`.gitignore`) |

## Rules

- Keep original `package` declarations and dates in file names so provenance is obvious.
- Add a dated one-line note to the log below when you drop something in.
- If a reference file should keep working, turn it into an OpenStreetMapViewer sample
  fragment or a unit test instead, so it compiles and rots visibly. That is how
  `SampleRotationListener` replaced a stray example Activity in the library (2026-09-03).

## Log

- 2026-09-03: created. Moved ~30 root-level session notes into `notes/`, and
  `ProfileLines.java` (private app snapshot), diff/log captures, `classes.jar`, and
  `bugreport.zip` into `local/`. Gemini CLI session notes
  (`gemini/*.md`, Sep-Dec 2025) moved to `notes/gemini/`.
