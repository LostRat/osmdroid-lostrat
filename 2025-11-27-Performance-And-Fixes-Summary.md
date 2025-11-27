# Critical Fixes & Performance Improvements - November 2025

## Overview
This document summarizes several critical fixes and general performance improvements applied to the codebase, ensuring long-term stability and compliance with Google Play requirements.

## 1. 16KB Page Size Compatibility (Critical)
**Target:** Google Play requirement starting November 1, 2025.

### The Issue
Android 15 introduced support for 16KB memory page sizes. Some native libraries, specifically the `libsqliteX.so` bundled with `geopackage-android`, were not aligned to 16KB boundaries, causing the app to fail on 16KB devices.

### The Fix
We have removed the bundled `sqlite-android` dependency and forced the usage of the Android System SQLite, which is guaranteed to be compatible.

**Changes Applied:**
1.  **Exclusions:** Added Gradle exclusions for `io.requery:sqlite-android` in both the library and sample app modules.
2.  **Resolution Strategy:** Forced `androidx.sqlite:sqlite` and `androidx.sqlite:sqlite-framework` versions.

**Action Required for Consumers:**
If you consume this library, you **MUST** apply similar exclusions in your app's `build.gradle` to prevent the problematic library from being pulled in transitively:

```gradle
implementation("mil.nga.geopackage:geopackage-android:6.7.4") {
    exclude group: 'io.requery', module: 'sqlite-android'
}
```

## 2. Marker Interactivity & Performance
*   **Optimization:** We identified that decorative markers (like breadcrumbs) were consuming significant CPU during touch events.
*   **Fix:** Implemented `setInteractive(false)` to allow markers to be visual-only, skipping expensive hit-testing.
*   **Result:** Massive performance gain for maps with hundreds/thousands of static markers.

## 3. Thread Safety
*   **Fix:** `DefaultOverlayManager` now uses `ConcurrentHashMap` for its internal layer storage. This prevents `ConcurrentModificationException` and other crashes when adding or removing overlays from background threads, which is a common pattern when loading map data asynchronously.

## 4. Spatial Indexing
*   **Fix:** Removed the arbitrary limit of 50 overlays per grid cell in the touch detection system. Previously, if more than 50 items were stacked in one spot, some became unclickable. The system now handles any number of overlapping items correctly.
