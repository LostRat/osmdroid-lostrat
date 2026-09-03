# 16KB Page Size Compatibility Fix Summary

**Date:** December 19, 2024  
**Target:** Google Play requirement starting November 1, 2025  
**Issue:** `libsqliteX.so` not aligned to 16KB boundaries

## Problem Statement

```
APK not compatible with 16 KB devices. Some libraries have LOAD segments not aligned at 16 KB boundaries:
lib/arm64-v8a/libsqliteX.so
lib/x86_64/libsqliteX.so
```

## Root Cause Analysis

The `libsqliteX.so` library comes from:
- **Dependency:** `mil.nga.geopackage:geopackage-android:6.7.4`
- **Specific Module:** `io.requery:sqlite-android`
- **Issue:** This bundled SQLite library is not aligned to 16KB boundaries

## Solution Applied to osmdroid-lostrat

### 1. Library-Level Changes (osmdroid-geopackage module)

**File:** `osmdroid-geopackage/build.gradle`

```gradle
implementation ("mil.nga.geopackage:geopackage-android:6.7.4"){
    exclude group: 'com.google.android.gms'
    exclude group: 'com.google.maps.android'
    exclude group: 'com.android.support',module: 'support-v13'
    
    // KEY FIX: Exclude problematic SQLite library
    exclude group: 'io.requery', module: 'sqlite-android'
    exclude module: 'sqlite-android'
}

android {
    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}
```

### 2. Sample App Changes (OpenStreetMapViewer)

**File:** `OpenStreetMapViewer/build.gradle`

```gradle
// Same exclusions in consuming app
implementation("mil.nga.geopackage:geopackage-android:6.7.4") {
    exclude group: 'com.google.android.gms'
    exclude group: 'com.google.maps.android'
    exclude group: 'com.android.support', module: 'support-v13'
    
    exclude group: 'io.requery', module: 'sqlite-android'
    exclude module: 'sqlite-android'
}

// Replace with system SQLite
implementation 'androidx.sqlite:sqlite:2.4.0'
implementation 'androidx.sqlite:sqlite-framework:2.4.0'
```### 3. Gra
dle Properties

**File:** `gradle.properties`

```properties
# Modern approach - deprecated properties removed
# android.bundle.enableUncompressedNativeLibs was removed in AGP 8.1+
```

### 4. Resolution Strategy

**File:** `OpenStreetMapViewer/build.gradle`

```gradle
configurations.all {
    resolutionStrategy {
        // Force use of system SQLite
        force 'androidx.sqlite:sqlite:2.4.0'
        force 'androidx.sqlite:sqlite-framework:2.4.0'
    }
}

android {
    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
        }
    }
    
    bundle {
        language { enableSplit = false }
        density { enableSplit = false }
        abi { enableSplit = false }
    }
}
```

## Critical Issue: Your Consuming App

**The fix must be applied in YOUR app that imports osmdroid as a dependency!**

Even though osmdroid-lostrat library excludes the problematic SQLite, your consuming app might:
1. Still pull in the original geopackage dependency
2. Not have the exclusions applied
3. Bundle the problematic `libsqliteX.so`

## Required Fix for Your App

**In your app's `build.gradle`:**

```gradle
dependencies {
    implementation 'com.github.lostrat:osmdroid-android:6.1.22-lostrat-SNAPSHOT'
    implementation 'com.github.lostrat:osmdroid-geopackage:6.1.22-lostrat-SNAPSHOT'
    
    // CRITICAL: Add these exclusions in your app too
    implementation("mil.nga.geopackage:geopackage-android:6.7.4") {
        exclude group: 'io.requery', module: 'sqlite-android'
        exclude module: 'sqlite-android'
    }
    
    // Use system SQLite
    implementation 'androidx.sqlite:sqlite:2.4.0'
    implementation 'androidx.sqlite:sqlite-framework:2.4.0'
}

configurations.all {
    resolutionStrategy {
        force 'androidx.sqlite:sqlite:2.4.0'
        force 'androidx.sqlite:sqlite-framework:2.4.0'
    }
}

android {
    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}
```## Why
 the Fix Works

1. **Removes Root Cause:** Excludes `io.requery:sqlite-android` that contains `libsqliteX.so`
2. **System SQLite:** Uses Android's built-in SQLite (always 16KB aligned)
3. **Transitive Dependencies:** Prevents any dependency from pulling in the problematic library
4. **Force Resolution:** Ensures system SQLite is used everywhere

## Verification Steps

### 1. Check APK Contents
```bash
# Extract and check APK
unzip -l your-app.apk | grep sqlite
# Should NOT show libsqliteX.so
```

### 2. Gradle Dependency Analysis
```bash
./gradlew :app:dependencies --configuration releaseRuntimeClasspath | grep sqlite
# Should show androidx.sqlite, NOT io.requery
```

### 3. Build and Test
```bash
./gradlew clean assembleRelease
# Check for 16KB warning - should be gone
```

## Expected Results

✅ **Before Fix:**
```
lib/arm64-v8a/libsqliteX.so ❌
lib/x86_64/libsqliteX.so ❌
```

✅ **After Fix:**
```
No libsqliteX.so files in APK ✅
Uses system SQLite ✅
16KB compatible ✅
```

## Troubleshooting

### If Warning Persists:

1. **Check all geopackage dependencies** in your app
2. **Add exclusions to ALL geopackage imports**
3. **Verify no transitive dependencies pull in sqlite-android**
4. **Use `./gradlew :app:dependencies` to debug**

### Alternative: Complete Exclusion
```gradle
configurations.all {
    exclude group: 'io.requery', module: 'sqlite-android'
    exclude module: 'sqlite-android'
}
```

## Benefits of This Approach

- ✅ **16KB Compliant:** Meets Google Play Nov 2025 requirement
- ✅ **Smaller APK:** No bundled SQLite library
- ✅ **Better Performance:** System SQLite optimized per Android version
- ✅ **Security:** Always up-to-date SQLite from system
- ✅ **Compatibility:** Works with all geopackage features

## Key Takeaway

**The 16KB fix must be applied in BOTH:**
1. **osmdroid library** (done ✅)
2. **Your consuming app** (needs to be done ⚠️)

This is because dependency exclusions don't automatically propagate to consuming applications.