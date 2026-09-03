# 16KB Page Size Compatibility Fix

**Date:** December 19, 2024  
**Issue:** APK not compatible with 16KB devices due to `libsqliteX.so` alignment  
**Deadline:** November 1st, 2025 (Google Play requirement)

## Problem
```
APK OpenStreetMapViewer-debug.apk is not compatible with 16 KB devices. 
Some libraries have LOAD segments not aligned at 16 KB boundaries:
lib/arm64-v8a/libsqliteX.so
```

## Root Cause
The `libsqliteX.so` library from `mil.nga.geopackage:geopackage-android:6.7.4` is not aligned to 16KB boundaries.

## Solutions Applied

### 1. Gradle Properties (`gradle.properties`)
```gradle
# 16KB page size compatibility (Google Play requirement Nov 2025+)
android.bundle.enableUncompressedNativeLibs=false
android.enableR8.fullMode=true
android.bundle.enableDexingArtifactTransform=false
android.enableJetifier=true
```

### 2. Main App Configuration (`OpenStreetMapViewer/build.gradle`)
```gradle
android {
    defaultConfig {
        targetSdkVersion 34  // Required for 16KB compatibility
        
        ndk {
            abiFilters 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'
        }
    }
    
    packagingOptions {
        jniLibs {
            useLegacyPackaging = false  // Forces 16KB alignment
            pickFirsts += ['**/libsqliteX.so', '**/libsqlite.so']
        }
    }
    
    splits {
        abi {
            enable true
            reset()
            include 'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'
            universalApk true
        }
    }
}
```

### 3. Geopackage Module (`osmdroid-geopackage/build.gradle`)
```gradle
android {
    packagingOptions {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}
```

## Alternative Solutions (if above doesn't work)

### Option A: Exclude Problematic Library
```gradle
implementation("mil.nga.geopackage:geopackage-android:6.7.4") {
    exclude group: 'com.google.android.gms'
    exclude group: 'com.google.maps.android'
    exclude group: 'com.android.support', module: 'support-v13'
    // Try excluding the sqlite library
    exclude module: 'sqlite-android'
}
```

### Option B: Use Alternative Geopackage Version
```gradle
// Try a newer version that might have 16KB compatibility
implementation("mil.nga.geopackage:geopackage-android:6.8.0") {
    // ... excludes
}
```

### Option C: Force Library Replacement
```gradle
configurations.all {
    resolutionStrategy {
        force 'androidx.sqlite:sqlite:2.4.0'  // Use system SQLite
    }
}
```

## Testing Steps

1. **Clean Build:**
   ```bash
   ./gradlew clean
   ./gradlew assembleDebug
   ```

2. **Check APK:**
   - Build APK and check if warning persists
   - Use `aapt dump badging app.apk` to verify

3. **Verify Alignment:**
   ```bash
   unzip -l app.apk | grep libsqlite
   # Check if libraries are properly aligned
   ```

## Expected Results

After applying these changes:
- ✅ No more 16KB alignment warnings
- ✅ APK compatible with 16KB page size devices
- ✅ Ready for Google Play Nov 2025+ requirements
- ✅ Maintains functionality on all Android versions 23+

## Fallback Plan

If the geopackage library continues to cause issues:
1. Consider using Android's built-in SQLite instead of bundled version
2. Update to a newer geopackage library version when available
3. Implement custom SQLite wrapper that ensures 16KB alignment

## References
- [Android 16KB Page Size Guide](https://developer.android.com/16kb-page-size)
- [Google Play 16KB Requirements](https://support.google.com/googleplay/android-developer/answer/14501306)