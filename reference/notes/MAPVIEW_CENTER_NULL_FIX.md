# MapView Center NullPointerException Fix

## Issue
`NullPointerException` when calling `setZoomLevel()` on a MapView before the center has been explicitly set.

### Stack Trace
```
java.lang.NullPointerException: Attempt to invoke virtual method 'double org.osmdroid.util.GeoPoint.getLatitude()' on a null object reference
at org.osmdroid.views.Projection.update(Projection.java:172)
at org.osmdroid.views.Projection.update(Projection.java:140)
at org.osmdroid.views.MapView.getProjection(MapView.java:388)
at org.osmdroid.views.MapView.setExpectedCenter(MapView.java:1880)
at org.osmdroid.views.MapView.setExpectedCenter(MapView.java:1898)
at org.osmdroid.views.MapView.setZoomLevel(MapView.java:530)
at org.osmdroid.views.MapController.setZoom(MapController.java:286)
```

## Root Cause
The `mCenter` field in `MapView` was declared but never initialized. When `setZoomLevel()` is called before any center is set:
1. `setZoomLevel()` calls `getProjection().getCurrentCenter()` to preserve the current center
2. `getProjection()` creates/updates a `Projection` by calling `getExpectedCenter()`
3. `getExpectedCenter()` returns `mCenter`, which is `null`
4. `Projection.update()` receives `null` for `pCenter` parameter
5. At line 172, it tries to call `center.getLatitude()` on the null reference

## Solution
Initialize `mCenter` with a default value of `new GeoPoint(0., 0)` (lat=0, lon=0, which is the intersection of the equator and prime meridian).

### Changed File
- `osmdroid-android/src/main/java/org/osmdroid/views/MapView.java`

### Change
```java
// Before:
private GeoPoint mCenter;

// After:
private GeoPoint mCenter = new GeoPoint(0., 0);
```

## Impact
- Prevents NPE when zoom operations occur before center is explicitly set
- Uses a sensible default (0,0) consistent with other GeoPoint initializations in the codebase
- No breaking changes - existing code that sets center explicitly will continue to work
