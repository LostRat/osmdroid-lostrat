# Git Commit Message for DefaultOverlayManager.java

## Recommended Commit Message

```
fix(OverlayManager): Remove parallel stream processing to fix tap handling issues

Removes parallel stream optimizations from DefaultOverlayManager that were
causing non-deterministic behavior in overlay tap handling and visibility
calculations.

**Changes:**

- onSingleTapConfirmed(): Replaces parallelStream() with sequential
  reverse-order iteration for both background layers and tap overlays
- updateVisibleOverlays(): Replaces parallelStream() with sequential
  for-each loop

**Rationale:**

While parallel streams offered theoretical performance gains for large
overlay collections (>10 or >50 items), they introduced issues:

1. **Ordering unpredictability**: Parallel streams do not guarantee
   processing order, which conflicts with the requirement that overlays
   be processed in reverse z-order (topmost first) for correct tap
   event handling.

2. **Side effect safety**: Overlay.onSingleTapConfirmed() may have side
   effects that are not thread-safe when invoked concurrently from
   multiple threads.

3. **anyMatch() short-circuiting**: Using anyMatch() with parallel
   streams could cause multiple overlays to receive tap events before
   a match terminates the stream.

The sequential approach ensures deterministic, correct behavior at the
cost of marginal performance for very large overlay collections.

Closes #[issue-number-if-applicable]
```

---

## Analysis

### What Changed

The diff shows removal of three blocks of parallel stream code:

| Location | Before | After |
|----------|--------|-------|
| `onSingleTapConfirmed` (background layers) | `parallelStream().filter().anyMatch()` | Sequential reverse loop |
| `onSingleTapConfirmed` (tap overlays) | `parallelStream().filter().anyMatch()` | Sequential reverse loop |
| `updateVisibleOverlays` | `parallelStream().filter().collect()` | Sequential for-each loop |

### Why This Fix Was Needed

The original optimization in commit `3fed44084` added parallel stream processing with the intention of improving performance for large overlay collections. However, this approach has fundamental issues:

1. **`anyMatch()` with side effects**: The `overlay.onSingleTapConfirmed()` method is stateful (returns true if tap was consumed). When run in parallel, multiple overlays could simultaneously process the tap before anyMatch() terminates.

2. **Z-order dependency**: Tap handling requires processing overlays from top to bottom (reverse add order). Parallel streams do not preserve this ordering.

3. **Visibility list ordering**: `updateVisibleOverlays` populating `mVisibleOverlays` via parallel stream + `collect()` could result in unpredictable overlay ordering during rendering.

### Performance Consideration

The performance impact of this change is minimal for typical use cases:
- Most apps have <50 overlays, where parallel overhead exceeds any gain
- Sequential iteration over a small list is extremely fast
- Correctness is more important than marginal performance gains
