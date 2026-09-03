# Crash Fix: Parallel Stream in DefaultOverlayManager

## Issue
A crash was reported when tapping a marker label:
`android.util.AndroidRuntimeException` in `java.util.stream.ReferencePipeline.anyMatch` called from `DefaultOverlayManager.onSingleTapConfirmedWithLayers`.

## Root Cause
The `onSingleTapConfirmed` method of overlays often interacts with the UI (e.g., `mapView.getController().animateTo()`, showing Toasts, updating Views).
The `DefaultOverlayManager` contained an optimization using `parallelStream()` to check for tap events on overlays.
`parallelStream()` executes tasks on worker threads (ForkJoinPool).
Android views can only be touched from the main thread.
Executing `overlay.onSingleTapConfirmed` on a background thread caused the `AndroidRuntimeException`.

## Fix
Removed `parallelStream()` usage in:
1. `onSingleTapConfirmedWithLayers` (The crash site)
2. `onSingleTapConfirmedLegacy`
3. `updateVisibleOverlays` (Proactive fix)

Replaced with sequential loops.
Also corrected the iteration order in `onSingleTapConfirmedLegacy` to iterate in reverse (top-to-bottom) to ensure the topmost overlay handles the click first, matching the behavior of `onSingleTapConfirmedWithLayers`.

## Verification
The code now uses standard sequential loops on the calling thread (UI thread), ensuring thread safety for UI operations.
