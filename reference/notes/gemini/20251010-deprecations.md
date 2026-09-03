# Java 8 Deprecation Analysis for osmdroid-android

This document lists all deprecated classes, methods, and constructors found within the `osmdroid-android` module. This can be used as a checklist for modernization and cleanup efforts.

## Deprecated Classes

*   `org.osmdroid.util.MapTileListBorderComputer`
*   `org.osmdroid.util.MapTileListComputer`
*   `org.osmdroid.util.MapTileListZoomComputer`
*   `org.osmdroid.views.overlay.ItemizedOverlayWithFocus`
*   `org.osmdroid.views.overlay.GroundOverlay2`
*   `org.osmdroid.views.overlay.GroundOverlay4`
*   `org.osmdroid.views.drawing.OsmBitmapShader`
*   `org.osmdroid.views.drawing.OsmPath`
*   `org.osmdroid.views.util.MyMath`
*   `org.osmdroid.views.util.PathProjection`
*   `org.osmdroid.views.overlay.gridlines.LatLonGridlineOverlay`

## Deprecated Methods, Constructors, and Fields

### `org.osmdroid.api.IGeoPoint`
*   `getLatitudeE6()`
*   `getLongitudeE6()`

### `org.osmdroid.api.IMapController`
*   `setZoom(int)`
*   `zoomTo(int)`
*   `zoomToFixing(int, int, int)`
*   `zoomToSpan(int, int)`

### `org.osmdroid.api.IMapView`
*   `getZoomLevel()`

### `org.osmdroid.LocationListenerProxy`
*   `onStatusChanged(String, int, Bundle)`

### `org.osmdroid.tileprovider.BitmapPool`
*   `applyReusableOptions(BitmapFactory.Options)`
*   `obtainBitmapFromPool()`

### `org.osmdroid.tileprovider.cachemanager.CacheManager`
*   `getMapTileFromCoordinates(double, double, int)`
*   `getCoordinatesFromMapTile(int, int, int)`

### `org.osmdroid.tileprovider.ExpirableBitmapDrawable`
*   `isDrawableExpired(Drawable)`
*   `setDrawableExpired(Drawable)`

### `org.osmdroid.tileprovider.MapTileProviderArray`
*   `isDowngradedMode()`

### `org.osmdroid.tileprovider.MapTileProviderBase`
*   `putExpiredTileIntoCache(MapTileRequestState, Drawable)`
*   `setTileRequestCompleteHandler(Handler)`

### `org.osmdroid.tileprovider.MapTileRequestState`
*   `MapTileRequestState(long, MapTileModuleProviderBase[], IMapTileProviderCallback)`

### `org.osmdroid.tileprovider.modules.INetworkAvailablityCheck`
*   `getRouteToPathExists(int)`

### `org.osmdroid.tileprovider.modules.MapTileApproximater`
*   `setTileSource(ITileSource)`

### `org.osmdroid.tileprovider.modules.MapTileModuleProviderBase`
*   `loadTile(MapTileRequestState)`

### `org.osmdroid.tileprovider.modules.MapTileSqlCacheProvider`
*   `MapTileSqlCacheProvider(IRegisterReceiver, ITileSource, long)`

### `org.osmdroid.tileprovider.modules.TileDownloader`
*   `getHttpExpiresTime(String)`
*   `getHttpCacheControlDuration(String)`
*   `computeExpirationTime(String, String, long)`

### `org.osmdroid.tileprovider.tilesource.BitmapTileSourceBase`
*   `ordinal()`

### `org.osmdroid.tileprovider.tilesource.ITileSource`
*   `ordinal()`

### `org.osmdroid.tileprovider.tilesource.TileSourceFactory`
*   `getTileSource(int)`

### `org.osmdroid.util.BoundingBox`
*   `getCenter()`
*   `getLongitudeSpan()`
*   `getLatitudeSpanE6()`
*   `getLongitudeSpanE6()`

### `org.osmdroid.util.GeoPoint`
*   `GeoPoint(int, int)`
*   `GeoPoint(int, int, int)`
*   `fromIntString(String)`
*   `getLatitudeE6()`
*   `getLongitudeE6()`

### `org.osmdroid.util.GeometryMath`
*   `DEG2RAD`
*   `RAD2DEG`

### `org.osmdroid.util.constants.GeoConstants`
*   `EQUATORCIRCUMFENCE`

### `org.osmdroid.util.TileSystem`
*   `EarthRadius`
*   `MinLatitude`
*   `MaxLatitude`
*   `MinLongitude`
*   `MaxLongitude`
*   `projectionZoomLevel`
*   `MapSize(int)`
*   `LatLongToPixelXY`
*   `LatLongToPixelXYMapSize`
*   `PixelXYToLatLong`
*   `PixelXYToLatLongMapSize`
*   `PixelXYToTileXY`
*   `TileXYToPixelXY`
*   `Clip`

### `org.osmdroid.views.MapController`
*   `zoomToSpan(int, int)`
*   `setZoom(int)`
*   `zoomOutFixing(int, int)`
*   `zoomTo(int)`
*   `zoomToFixing(int, int, int)`

### `org.osmdroid.views.MapView`
*   `setMapCenter`
*   `getZoomLevel`
*   `getZoomLevel(boolean)`
*   `zoomIn`
*   `zoomInFixing`
*   `zoomOut`
*   `zoomOutFixing`
*   `setMapListener`
*   `setBuiltInZoomControls`
*   `setInitCenter`

### `org.osmdroid.views.overlay.FolderOverlay`
*   `FolderOverlay(Context)`

### `org.osmdroid.views.overlay.ItemizedOverlay`
*   `ItemizedOverlay(Context, Drawable)`

### `org.osmdroid.views.overlay.MapEventsOverlay`
*   `MapEventsOverlay(Context, MapEventsReceiver)`

### `org.osmdroid.views.overlay.Marker`
*   `cleanDefaults()`

### `org.osmdroid.views.overlay.Overlay`
*   `Overlay(Context)`

### `org.osmdroid.views.overlay.OverlayWithIW`
*   `OverlayWithIW(Context)`

### `org.osmdroid.views.overlay.Polygon`
*   `getFillColor()`
*   `getStrokeColor()`
*   `getStrokeWidth()`
*   `getPoints()`
*   `setFillColor(int)`
*   `setStrokeColor(int)`
*   `setStrokeWidth(float)`

### `org.osmdroid.views.overlay.Polyline`
*   `getPoints()`
*   `getColor()`
*   `getWidth()`
*   `getPaint()`
*   `setColor(int)`
*   `setWidth(float)`

### `org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider`
*   `onStatusChanged(String, int, Bundle)`

### `org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay`
*   `setDirectionArrow(Bitmap, Bitmap)`
*   `setPersonHotspot(float, float)`

### `org.osmdroid.views.overlay.mylocation.SimpleLocationOverlay`
*   `SimpleLocationOverlay(Context)`
