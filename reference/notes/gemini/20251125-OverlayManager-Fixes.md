# DefaultOverlayManager Fixes & Optimization Plan - 2025-11-25

This plan addresses critical issues identified in `DefaultOverlayManager.java` and aligns the implementation with the original Polyline Optimization goals.

## Priority 1: Critical Bug Fixes & Stability

### Task 1: Fix Thread Safety
**Issue:** `mOverlayToLayer` is a `HashMap` and `mLayeredOverlays` is a `HashMap`, which are not thread-safe. Accessing `add`/`remove` from background threads (common in OSMDroid) will cause crashes or data corruption.
**Action:**
- Change `mOverlayToLayer` to `ConcurrentHashMap`.
- Change `mLayeredOverlays` to `ConcurrentHashMap` (or `Collections.synchronizedMap`), even though keys are Enums, concurrent reads/writes need safety.
- Verify `CopyOnWriteArrayList` usage for values is sufficient.

### Task 2: Fix Spatial Index Data Loss
**Issue:** The spatial index uses a fixed-size array (`Overlay[][]` with `MAX_OVERLAYS_PER_CELL = 50`). If a cell fills up, extra overlays are silently ignored, making them un-clickable.
**Action:**
- Remove the hard limit of 50 overlays per cell.
- Change the internal grid storage from a fixed array `Overlay[]` to a dynamic `List<Overlay>` (or `ArrayList`) per cell.
- While memory allocation is a concern, correctness is paramount. We can reuse `ArrayList` instances or clear them instead of reallocating to mitigate GC pressure.
- Alternatively, implement a robust fallback: if a cell is "full", flag it, and force a full search for taps in that region. (Dynamic list is preferred).

## Priority 2: Logic & Architecture

### Task 3: Fix FolderOverlay Z-Ordering
**Issue:** `FolderOverlay` is assigned to a single layer (e.g., `MAIN_CONTENT`). Its children are forced into that layer's draw order, ignoring their intrinsic types (e.g., a Marker inside a Folder shouldn't be drawn below a Polyline just because the Polyline is in a "higher" layer than the Folder).
**Action:**
- **Option A:** Modify `assignOverlayToLayer` to recursively handle `FolderOverlay`. Do not assign the folder itself to a layer; instead, assign its children to their respective layers.
- **Option B:** Modify the Draw loop. If an overlay is a `FolderOverlay`, iterate its children and draw them based on *their* calculated layer, ignoring the folder's layer.
- **Recommendation:** Option A is cleaner for the existing "Layered List" structure, but Option B preserves the logical grouping of the Folder. Given the current flat layer lists, Option A (flattening folders into layers) is likely the only way to make the Z-Layers work correctly.

## Priority 3: Performance Optimization (Original Goal)

### Task 4: Implement Polyline Batching
**Issue:** The current `DefaultOverlayManager` changes do not improve *drawing* performance, only tap detection.
**Action:**
- Create a new class `PolylineBatch` (or `BatchPolylineOverlay`) as specified in `gemini/20250924-polyline-optimization.md`.
- This class should accept a list of Polylines and draw them in a single pass (sharing Paint objects, reducing JNI calls).
- Integrate this new Overlay type into `DefaultOverlayManager`.

### Task 5: Polyline Simplification
**Action:**
- Implement Ramer-Douglas-Peucker simplification in `PolyOverlayWithIW` (or the new `PolylineBatch`).
- Add `setSimplificationTolerance(double tolerance)` API.

## Execution Strategy
Addressing these tasks in order (1 -> 5) ensures the app remains stable before adding complex optimizations.
