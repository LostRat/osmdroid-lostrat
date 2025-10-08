package org.osmdroid.views.overlay;

import android.graphics.Canvas;
import android.graphics.Point;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

import org.osmdroid.api.IMapView;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.Overlay.Snappable;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.Arrays;

import android.os.Build;
import android.util.ArrayMap;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;

/**
 * https://github.com/osmdroid/osmdroid/issues/154
 *
 * @author dozd
 * @since 5.0.0
 */
public class DefaultOverlayManager extends AbstractList<Overlay> implements OverlayManager {

    private TilesOverlay mTilesOverlay;

    private final CopyOnWriteArrayList<Overlay> mOverlayList;

    // API 23+ optimization: Spatial indexing for fast tap detection
    private final Map<Integer, List<Overlay>> mSpatialIndex = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
            new ArrayMap<>() : new HashMap<>();
    private final List<Overlay> mVisibleOverlays = new ArrayList<>();
    private final int GRID_SIZE = 256; // pixels
    private BoundingBox mLastViewport = null;
    private double mLastZoomLevel = -1;

    // ENHANCED FIX: Z-Index layer system with predefined layers
    public enum OverlayLayer {
        BACKGROUND_TILES(0),      // Tile overlays, base maps
        BACKGROUND_SHAPES(1),     // Background polylines, polygons
        DECORATION(2),            // Tiny markers, vertex dots, decorative elements
        MAIN_CONTENT(3),          // Main polylines, primary content
        INTERACTIVE_BACKGROUND(4), // Clickable polylines, selectable shapes
        INTERACTIVE_CONTENT(5),   // Main markers, important interactive elements
        USER_DRAWING(6),          // User-drawn lines that should be on top
        OVERLAY_CONTROLS(7),      // UI overlays, controls
        POPUP_CONTENT(8),         // Info windows, popups
        DEBUG_OVERLAY(9);         // Debug information, always on top

        private final int zIndex;

        OverlayLayer(int zIndex) {
            this.zIndex = zIndex;
        }

        public int getZIndex() {
            return zIndex;
        }
    }

    private final Map<OverlayLayer, List<Overlay>> mLayeredOverlays = new HashMap<>();
    private final Map<Overlay, OverlayLayer> mOverlayToLayer = new HashMap<>();
    private boolean mUseLayerSystem = true;

    public DefaultOverlayManager(final TilesOverlay tilesOverlay) {
        setTilesOverlay(tilesOverlay);
        mOverlayList = new CopyOnWriteArrayList<>();

        // Initialize layer system
        for (OverlayLayer layer : OverlayLayer.values()) {
            mLayeredOverlays.put(layer, new CopyOnWriteArrayList<>());
        }
    }

    @Override
    public Overlay get(final int pIndex) {
        return mOverlayList.get(pIndex);
    }

    @Override
    public int size() {
        return mOverlayList.size();
    }

    @Override
    public void add(final int pIndex, final Overlay pElement) {
        if (pElement == null) {
            //#396 fix, null check
            Exception ex = new Exception();
            Log.e(IMapView.LOGTAG, "Attempt to add a null overlay to the collection. This is probably a bug and should be reported!", ex);
        } else {
            mOverlayList.add(pIndex, pElement);
            // ENHANCED FIX: Automatically assign overlay to appropriate layer
            assignOverlayToLayer(pElement);
        }
    }

    @Override
    public Overlay remove(final int pIndex) {
        Overlay removed = mOverlayList.remove(pIndex);
        if (removed != null) {
            // ENHANCED FIX: Remove from layer system
            removeOverlayFromLayer(removed);
        }
        return removed;
    }

    @Override
    public boolean remove(final Object o) {
        // Remove from main list first
        boolean removed = mOverlayList.remove(o);
        // Then clean up layer system if it was an overlay
        if (removed && o instanceof Overlay) {
            removeOverlayFromLayer((Overlay) o);
        }
        return removed;
    }

    @Override
    public Overlay set(final int pIndex, final Overlay pElement) {
        //#396 fix, null check
        if (pElement == null) {
            Exception ex = new Exception();
            Log.e(IMapView.LOGTAG, "Attempt to set a null overlay to the collection. This is probably a bug and should be reported!", ex);
            return null;
        } else {
            Overlay overlay = mOverlayList.set(pIndex, pElement);
            return overlay;
        }
    }


    @Override
    public TilesOverlay getTilesOverlay() {
        return mTilesOverlay;
    }

    @Override
    public void setTilesOverlay(final TilesOverlay tilesOverlay) {
        mTilesOverlay = tilesOverlay;
    }

    @Override
    public Iterable<Overlay> overlaysReversed() {
        return new Iterable<Overlay>() {

            /**
             * @since 6.1.0
             */
            private ListIterator<Overlay> bulletProofReverseListIterator() {
                while (true) {
                    try {
                        return mOverlayList.listIterator(mOverlayList.size());
                    } catch (final IndexOutOfBoundsException e) {
                        // thread-concurrency fix - in case an item is removed in a very inappropriate time
                        // cf. https://github.com/osmdroid/osmdroid/issues/1260
                    }
                }
            }

            @Override
            public Iterator<Overlay> iterator() {
                final ListIterator<Overlay> i = bulletProofReverseListIterator();

                return new Iterator<Overlay>() {
                    @Override
                    public boolean hasNext() {
                        return i.hasPrevious();
                    }

                    @Override
                    public Overlay next() {
                        return i.previous();
                    }

                    @Override
                    public void remove() {
                        i.remove();
                    }
                };
            }
        };
    }

    @Override
    public List<Overlay> overlays() {
        return mOverlayList;
    }


    @Override
    public void onDraw(final Canvas c, final MapView pMapView) {
        onDrawHelper(c, pMapView, pMapView.getProjection());
    }

    /**
     * @since 6.1.0
     */
    @Override
    public void onDraw(final Canvas c, final Projection pProjection) {
        onDrawHelper(c, null, pProjection);
    }

    /**
     * @param pMapView    may be null
     * @param pProjection may NOT be null
     * @since 6.1.0
     */
    private void onDrawHelper(final Canvas c, final MapView pMapView, final Projection pProjection) {
        //fix for https://github.com/osmdroid/osmdroid/issues/904
        if (mTilesOverlay != null)
            mTilesOverlay.protectDisplayedTilesForCache(c, pProjection);
        for (final Overlay overlay : mOverlayList) {
            if (overlay != null && overlay.isEnabled() && overlay instanceof TilesOverlay) {
                ((TilesOverlay) overlay).protectDisplayedTilesForCache(c, pProjection);
            }
        }

        //always pass false, the shadow parameter will be removed in a later version of osmdroid, this change should result in the on draw being called twice
        if (mTilesOverlay != null && mTilesOverlay.isEnabled()) {
            if (pMapView != null) {
                mTilesOverlay.draw(c, pMapView, false);
            } else {
                mTilesOverlay.draw(c, pProjection);
            }
        }

        //always pass false, the shadow parameter will be removed in a later version of osmdroid, this change should result in the on draw being called twice
        if (mUseLayerSystem) {
            // ENHANCED FIX: Draw overlays in proper layer order
            drawWithLayers(c, pMapView, pProjection);
        } else {
            // Legacy drawing order
            for (final Overlay overlay : mOverlayList) {
                //#396 fix, null check
                if (overlay != null && overlay.isEnabled()) {
                    if (pMapView != null) {
                        overlay.draw(c, pMapView, false);
                    } else {
                        overlay.draw(c, pProjection);
                    }
                }
            }
        }
        //potential fix for #52 pMapView.invalidate();
    }

    /**
     * ENHANCED FIX: Draw overlays in proper layer order (lowest to highest z-index)
     * @since Enhanced layer system
     */
    private void drawWithLayers(final Canvas c, final MapView pMapView, final Projection pProjection) {
        // Draw layers from lowest to highest z-index
        OverlayLayer[] layers = OverlayLayer.values();
        Arrays.sort(layers, (a, b) -> Integer.compare(a.getZIndex(), b.getZIndex()));

        for (OverlayLayer layer : layers) {
            List<Overlay> layerOverlays = mLayeredOverlays.get(layer);
            if (layerOverlays == null) continue;

            for (final Overlay overlay : layerOverlays) {
                if (overlay != null && overlay.isEnabled()) {
                    if (pMapView != null) {
                        overlay.draw(c, pMapView, false);
                    } else {
                        overlay.draw(c, pProjection);
                    }
                }
            }
        }

        // Fallback: Draw any overlays not assigned to layers (for compatibility)
        for (final Overlay overlay : mOverlayList) {
            if (overlay != null && overlay.isEnabled() && !mOverlayToLayer.containsKey(overlay)) {
                if (pMapView != null) {
                    overlay.draw(c, pMapView, false);
                } else {
                    overlay.draw(c, pProjection);
                }
            }
        }
    }

    @Override
    public void onDetach(final MapView pMapView) {
        if (mTilesOverlay != null) {
            mTilesOverlay.onDetach(pMapView);
        }

        for (final Overlay overlay : this.overlaysReversed()) {
            overlay.onDetach(pMapView);
        }
        this.clear();
    }

    @Override
    public void onPause() {
        if (mTilesOverlay != null) {
            mTilesOverlay.onPause();
        }

        for (final Overlay overlay : this.overlaysReversed()) {
            overlay.onPause();
        }
    }

    @Override
    public void onResume() {
        if (mTilesOverlay != null) {
            mTilesOverlay.onResume();
        }

        for (final Overlay overlay : this.overlaysReversed()) {
            overlay.onResume();
        }
    }

    @Override
    public boolean onKeyDown(final int keyCode, final KeyEvent event, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onKeyDown(keyCode, event, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onKeyUp(final int keyCode, final KeyEvent event, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onKeyUp(keyCode, event, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onTouchEvent(event, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onTrackballEvent(final MotionEvent event, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onTrackballEvent(event, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onSnapToItem(final int x, final int y, final Point snapPoint, final IMapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay instanceof Snappable) {
                if (((Snappable) overlay).onSnapToItem(x, y, snapPoint, pMapView)) {
                    return true;
                }
            }
        }

        return false;
    }

    /* GestureDetector.OnDoubleTapListener */

    @Override
    public boolean onDoubleTap(final MotionEvent e, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onDoubleTap(e, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onDoubleTapEvent(final MotionEvent e, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onDoubleTapEvent(e, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onSingleTapConfirmed(final MotionEvent e, final MapView pMapView) {
        if (mUseLayerSystem) {
            return onSingleTapConfirmedWithLayers(e, pMapView);
        } else {
            return onSingleTapConfirmedLegacy(e, pMapView);
        }
    }

    /**
     * ENHANCED FIX: Layer-based tap handling - Higher layers get priority
     * @since Enhanced layer system
     */
    private boolean onSingleTapConfirmedWithLayers(final MotionEvent e, final MapView pMapView) {
        // Process layers from highest to lowest z-index
        OverlayLayer[] layers = OverlayLayer.values();

        // Sort layers by z-index (highest first)
        Arrays.sort(layers, (a, b) -> Integer.compare(b.getZIndex(), a.getZIndex()));

        for (OverlayLayer layer : layers) {
            List<Overlay> layerOverlays = mLayeredOverlays.get(layer);
            if (layerOverlays == null || layerOverlays.isEmpty()) {
                continue;
            }

            // For interactive layers, check all overlays in reverse order (last added first)
            if (isInteractiveLayer(layer)) {
                // Log.d(IMapView.LOGTAG, "Processing interactive layer " + layer +
                //       " with " + layerOverlays.size() + " overlays");

                // Iterate in reverse to match expected behavior (topmost overlay first)
                for (int i = layerOverlays.size() - 1; i >= 0; i--) {
                    final Overlay overlay = layerOverlays.get(i);

                    // Skip non-interactive overlays for performance
                    if (overlay == null || !overlay.isEnabled() || !overlay.isInteractive()) {
                        continue;
                    }

                    if (overlay instanceof Marker) {
                        Marker m = (Marker) overlay;
//                        Log.d(IMapView.LOGTAG, "  Checking marker ID=" + m.getId() +
//                              ", enabled=" + overlay.isEnabled() + ", interactive=" + overlay.isInteractive());
                    }
                    if (overlay.onSingleTapConfirmed(e, pMapView)) {
                        // Log.d(IMapView.LOGTAG, "  Overlay handled tap: " + overlay);
                        return true;
                    }
                }
            } else {
                // For background layers, use spatial optimization
                List<Overlay> nearbyOverlays = getNearbyOverlaysInLayer(e, pMapView, layer);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && nearbyOverlays.size() > 10) {
                    // API 24+: Use parallel streams for many overlays
                    if (nearbyOverlays.parallelStream()
                        .filter(overlay -> overlay != null && overlay.isInteractive())
                        .anyMatch(overlay -> overlay.onSingleTapConfirmed(e, pMapView))) {
                        return true;
                    }
                } else {
                    // Sequential processing in reverse order (last added first)
                    for (int i = nearbyOverlays.size() - 1; i >= 0; i--) {
                        final Overlay overlay = nearbyOverlays.get(i);
                        if (overlay != null && overlay.isInteractive() && overlay.onSingleTapConfirmed(e, pMapView)) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Legacy tap handling for backward compatibility
     * @since Core architectural fix
     */
    private boolean onSingleTapConfirmedLegacy(final MotionEvent e, final MapView pMapView) {
        // API 23+ optimization: Use spatial indexing and viewport culling for fast tap detection
        updateVisibleOverlaysIfNeeded(pMapView);

        final int x = Math.round(e.getX());
        final int y = Math.round(e.getY());

        // Get overlays near the tap point using spatial index
        List<Overlay> nearbyOverlays = getOverlaysNearPoint(x, y, pMapView);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && nearbyOverlays.size() > 10) {
            // API 24+: Use parallel streams for many overlays
            return nearbyOverlays.parallelStream()
                .filter(overlay -> overlay.isInteractive())
                .anyMatch(overlay -> overlay.onSingleTapConfirmed(e, pMapView));
        } else {
            // Sequential processing for fewer overlays or older APIs
            for (final Overlay overlay : nearbyOverlays) {
                if (overlay.isInteractive() && overlay.onSingleTapConfirmed(e, pMapView)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * API 23+ optimization: Update visible overlays only when viewport changes
     * @since API 23+ optimization
     */
    private void updateVisibleOverlaysIfNeeded(MapView mapView) {
        BoundingBox currentViewport = mapView.getProjection().getBoundingBox();
        double currentZoom = mapView.getZoomLevelDouble();

        // Only update if viewport or zoom changed significantly
        if (mLastViewport == null ||
            !mLastViewport.equals(currentViewport) ||
            Math.abs(currentZoom - mLastZoomLevel) > 0.1) {

            updateVisibleOverlays(mapView);
            buildSpatialIndex(mapView);
            mLastViewport = currentViewport;
            mLastZoomLevel = currentZoom;
        }
    }

    /**
     * API 23+ optimization: Build spatial index for fast overlay lookup
     * @since API 23+ optimization
     */
    private void buildSpatialIndex(MapView mapView) {
        mSpatialIndex.clear();
        Projection projection = mapView.getProjection();

        for (Overlay overlay : mVisibleOverlays) {
            if (overlay instanceof Polyline || overlay instanceof Marker ||
                overlay instanceof ItemizedIconOverlay) {
                addOverlayToSpatialGrid(overlay, projection);
            }
        }
    }

    /**
     * API 23+ optimization: Add overlay to spatial grid
     * @since API 23+ optimization
     */
    private void addOverlayToSpatialGrid(Overlay overlay, Projection projection) {
        BoundingBox bounds = getOverlayBounds(overlay);
        if (bounds == null) return;

        // Convert bounds to screen coordinates and add to grid cells
        Point topLeft = projection.toPixels(new GeoPoint(bounds.getLatNorth(), bounds.getLonWest()), null);
        Point bottomRight = projection.toPixels(new GeoPoint(bounds.getLatSouth(), bounds.getLonEast()), null);

        int startGridX = topLeft.x / GRID_SIZE;
        int endGridX = bottomRight.x / GRID_SIZE;
        int startGridY = topLeft.y / GRID_SIZE;
        int endGridY = bottomRight.y / GRID_SIZE;

        for (int gridX = startGridX; gridX <= endGridX; gridX++) {
            for (int gridY = startGridY; gridY <= endGridY; gridY++) {
                int key = gridX * 10000 + gridY;
                // API 23+ compatible: Manual computeIfAbsent implementation
                List<Overlay> overlaysInCell = mSpatialIndex.get(key);
                if (overlaysInCell == null) {
                    overlaysInCell = new ArrayList<>();
                    mSpatialIndex.put(key, overlaysInCell);
                }
                overlaysInCell.add(overlay);
            }
        }
    }

    /**
     * API 23+ optimization: Get overlays near a screen point
     * @since API 23+ optimization
     */
    private List<Overlay> getOverlaysNearPoint(int x, int y, MapView mapView) {
        int gridX = x / GRID_SIZE;
        int gridY = y / GRID_SIZE;
        int key = gridX * 10000 + gridY;

        List<Overlay> nearby = mSpatialIndex.get(key);
        return nearby != null ? nearby : Collections.emptyList();
    }

    /**
     * API 23+ optimization: Update list of visible overlays
     * @since API 23+ optimization
     */
    private void updateVisibleOverlays(MapView mapView) {
        mVisibleOverlays.clear();
        BoundingBox viewport = mapView.getProjection().getBoundingBox();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mOverlayList.size() > 50) {
            // API 24+: Use parallel streams for large overlay collections
            mVisibleOverlays.addAll(
                mOverlayList.parallelStream()
                    .filter(overlay -> isOverlayVisible(overlay, viewport))
                    .collect(Collectors.toList())
            );
        } else {
            // Sequential processing for smaller collections or older APIs
            for (Overlay overlay : mOverlayList) {
                if (isOverlayVisible(overlay, viewport)) {
                    mVisibleOverlays.add(overlay);
                }
            }
        }
    }

    /**
     * API 23+ optimization: Check if overlay is visible in viewport
     * @since API 23+ optimization
     */
    private boolean isOverlayVisible(Overlay overlay, BoundingBox viewport) {
        BoundingBox overlayBounds = getOverlayBounds(overlay);
        return overlayBounds == null || viewport.overlaps(overlayBounds, 0);
    }

    /**
     * API 23+ optimization: Get bounding box for overlay
     * @since API 23+ optimization
     */
    private BoundingBox getOverlayBounds(Overlay overlay) {
        try {
            if (overlay instanceof PolyOverlayWithIW) {
                return ((PolyOverlayWithIW) overlay).getBounds();
            } else if (overlay instanceof Marker) {
                GeoPoint point = ((Marker) overlay).getPosition();
                return new BoundingBox(point.getLatitude(), point.getLongitude(),
                                     point.getLatitude(), point.getLongitude());
            }
        } catch (Exception e) {
            // Fallback: if getBounds fails, assume overlay is always visible
        }
        // For other overlay types, assume they're always visible
        return null;
    }

    /**
     * ENHANCED FIX: Assign overlay to appropriate layer automatically
     * @since Enhanced layer system
     */
    private void assignOverlayToLayer(Overlay overlay) {
        // Defensive null check
        if (overlay == null) {
            // Log.w(IMapView.LOGTAG, "Attempt to auto-assign null overlay to layer. Ignoring.");
            return;
        }

        OverlayLayer layer = determineOverlayLayer(overlay);
        assignOverlayToLayer(overlay, layer);
    }

    /**
     * ENHANCED FIX: Assign overlay to specific layer
     * @since Enhanced layer system
     */
    public void assignOverlayToLayer(Overlay overlay, OverlayLayer layer) {
        // Defensive null checks
        if (overlay == null) {
            // Log.w(IMapView.LOGTAG, "Attempt to assign null overlay to layer. Ignoring.");
            return;
        }
        if (layer == null) {
            // Log.w(IMapView.LOGTAG, "Attempt to assign overlay to null layer. Ignoring.");
            return;
        }

        // Remove from current layer if assigned
        removeOverlayFromLayer(overlay);

        // Add to new layer
        List<Overlay> layerList = mLayeredOverlays.get(layer);
        if (layerList != null) {
            if (!layerList.contains(overlay)) {
                layerList.add(overlay);
            }
            // ALWAYS update the tracking map to maintain consistency
            mOverlayToLayer.put(overlay, layer);
        }
    }

    /**
     * ENHANCED FIX: Remove overlay from its current layer
     * @since Enhanced layer system
     */
    private void removeOverlayFromLayer(Overlay overlay) {
        // Defensive null check
        if (overlay == null) {
            Log.w(IMapView.LOGTAG, "Attempt to remove null overlay from layer. Ignoring.");
            return;
        }

        OverlayLayer currentLayer = mOverlayToLayer.remove(overlay);
        if (currentLayer != null) {
            List<Overlay> layerList = mLayeredOverlays.get(currentLayer);
            if (layerList != null) {
                layerList.remove(overlay);
            }
        } else {
            // Fallback: remove from all layers if not tracked
            for (List<Overlay> layerList : mLayeredOverlays.values()) {
                layerList.remove(overlay);
            }
        }
    }

    /**
     * ENHANCED FIX: Automatically determine appropriate layer for overlay
     * @since Enhanced layer system
     */
    private OverlayLayer determineOverlayLayer(Overlay overlay) {
        // Tiles and base maps
        if (overlay instanceof TilesOverlay) {
            return OverlayLayer.BACKGROUND_TILES;
        }

        // Interactive markers and clickable items
        if (overlay instanceof Marker) {
            // Check if it's a tiny decorative marker
            Marker marker = (Marker) overlay;
            boolean isDecoration = isDecorationMarker(marker);
            OverlayLayer result = isDecoration ? OverlayLayer.DECORATION : OverlayLayer.INTERACTIVE_CONTENT;

            // Debug logging
//            Log.d(IMapView.LOGTAG, "Marker layer assignment: ID=" + marker.getId() +
//                  ", isDecoration=" + isDecoration +
//                  ", layer=" + result);

            return result;
        }

        if (overlay instanceof ItemizedIconOverlay || overlay instanceof ClickableIconOverlay) {
            return OverlayLayer.INTERACTIVE_CONTENT;
        }

        // Polylines and polygons
        if (overlay instanceof Polyline || overlay instanceof Polygon) {
            // Check if it's user-drawn (you can add custom logic here)
            if (isUserDrawnOverlay(overlay)) {
                return OverlayLayer.USER_DRAWING;
            }
            // Since we can't access click listener directly, use other indicators for interactivity
            if (overlay instanceof PolyOverlayWithIW) {
                PolyOverlayWithIW polyOverlay = (PolyOverlayWithIW) overlay;
                // Check if it has info window or other interactive features
                if (polyOverlay.getInfoWindow() != null || polyOverlay.getTitle() != null) {
                    return OverlayLayer.INTERACTIVE_BACKGROUND;
                }
            }
            return OverlayLayer.MAIN_CONTENT;
        }

        // Folder overlays
        if (overlay instanceof FolderOverlay) {
            if (hasInteractiveChildren((FolderOverlay) overlay)) {
                return OverlayLayer.INTERACTIVE_CONTENT;
            }
            return OverlayLayer.MAIN_CONTENT;
        }

        // Default to main content
        return OverlayLayer.MAIN_CONTENT;
    }

    /**
     * ENHANCED FIX: Check if marker is decorative (tiny, non-interactive)
     * @since Enhanced layer system
     */
    private boolean isDecorationMarker(Marker marker) {
        // Decorative markers typically have no title, snippet, and are not draggable
        // Since we can't access the click listener directly, we use other indicators
        // If a marker has an ID set, it's considered interactive (used for click handling)
        return marker.getTitle() == null &&
               marker.getSnippet() == null &&
               !marker.isDraggable() &&
               marker.getInfoWindow() == null &&
               marker.getId() == null;
    }

    /**
     * ENHANCED FIX: Check if overlay is user-drawn
     * @since Enhanced layer system
     */
    private boolean isUserDrawnOverlay(Overlay overlay) {
        // You can add custom logic here to identify user-drawn overlays
        // For example, check for specific tags, properties, or naming conventions

        // Example: Check if overlay has a specific ID pattern
        if (overlay instanceof Marker) {
            Marker marker = (Marker) overlay;
            String title = marker.getTitle();
            return title != null && title.startsWith("USER_DRAWN_");
        }

        if (overlay instanceof Polyline) {
            // You could check for custom properties or use reflection if needed
            // For now, return false - users can manually assign to USER_DRAWING layer
        }

        return false; // Default implementation
    }

    /**
     * ENHANCED FIX: Helper method to mark overlay as interactive
     * Since we can't detect click listeners directly, provide manual method
     * @since Enhanced layer system
     */
    public void markAsInteractive(Overlay overlay) {
        // Defensive null check
        if (overlay == null) {
            Log.w(IMapView.LOGTAG, "Attempt to mark null overlay as interactive. Ignoring.");
            return;
        }

        if (overlay instanceof Marker) {
            assignOverlayToLayer(overlay, OverlayLayer.INTERACTIVE_CONTENT);
        } else if (overlay instanceof Polyline || overlay instanceof Polygon) {
            assignOverlayToLayer(overlay, OverlayLayer.INTERACTIVE_BACKGROUND);
        }
    }

    /**
     * ENHANCED FIX: Helper method to mark overlay as decoration
     * @since Enhanced layer system
     */
    public void markAsDecoration(Overlay overlay) {
        // Defensive null check
        if (overlay == null) {
            Log.w(IMapView.LOGTAG, "Attempt to mark null overlay as decoration. Ignoring.");
            return;
        }

        assignOverlayToLayer(overlay, OverlayLayer.DECORATION);
    }

    /**
     * ENHANCED FIX: Helper method to mark overlay as user-drawn
     * @since Enhanced layer system
     */
    public void markAsUserDrawn(Overlay overlay) {
        // Defensive null check
        if (overlay == null) {
            Log.w(IMapView.LOGTAG, "Attempt to mark null overlay as user-drawn. Ignoring.");
            return;
        }

        assignOverlayToLayer(overlay, OverlayLayer.USER_DRAWING);
    }

    /**
     * ENHANCED FIX: Check if folder contains interactive overlays
     * @since Enhanced layer system
     */
    private boolean hasInteractiveChildren(FolderOverlay folder) {
        for (Overlay child : folder.getItems()) {
            OverlayLayer childLayer = determineOverlayLayer(child);
            if (isInteractiveLayer(childLayer)) {
                return true;
            }
        }
        return false;
    }

    /**
     * ENHANCED FIX: Check if layer is interactive
     * @since Enhanced layer system
     */
    private boolean isInteractiveLayer(OverlayLayer layer) {
        return layer == OverlayLayer.INTERACTIVE_CONTENT ||
               layer == OverlayLayer.INTERACTIVE_BACKGROUND ||
               layer == OverlayLayer.OVERLAY_CONTROLS ||
               layer == OverlayLayer.POPUP_CONTENT;
    }

    /**
     * ENHANCED FIX: Get nearby overlays in specific layer
     * @since Enhanced layer system
     */
    private List<Overlay> getNearbyOverlaysInLayer(MotionEvent e, MapView mapView, OverlayLayer layer) {
        updateVisibleOverlaysIfNeeded(mapView);

        final int x = Math.round(e.getX());
        final int y = Math.round(e.getY());

        List<Overlay> nearbyOverlays = getOverlaysNearPoint(x, y, mapView);
        List<Overlay> layerNearby = new ArrayList<>();

        List<Overlay> layerOverlays = mLayeredOverlays.get(layer);
        if (layerOverlays != null) {
            for (Overlay overlay : nearbyOverlays) {
                if (layerOverlays.contains(overlay)) {
                    layerNearby.add(overlay);
                }
            }
        }

        return layerNearby;
    }

    /**
     * ENHANCED FIX: Enable/disable layer system
     * @since Enhanced layer system
     */
    public void setUseLayerSystem(boolean useLayerSystem) {
        mUseLayerSystem = useLayerSystem;
        if (useLayerSystem) {
            // Reassign all existing overlays to appropriate layers
            for (OverlayLayer layer : OverlayLayer.values()) {
                mLayeredOverlays.get(layer).clear();
            }
            mOverlayToLayer.clear();

            for (Overlay overlay : mOverlayList) {
                assignOverlayToLayer(overlay);
            }
        }
    }

    /**
     * ENHANCED FIX: Check if layer system is enabled
     * @since Enhanced layer system
     */
    public boolean isUsingLayerSystem() {
        return mUseLayerSystem;
    }

    /**
     * Gets the current layer assignment for the specified overlay.
     * <p>
     * This method allows you to query which layer an overlay is currently assigned to,
     * which determines its z-order (drawing order) and touch event priority. Overlays
     * in higher z-index layers are drawn on top and receive touch events first.
     * </p>
     * <p>
     * The layer system provides the following predefined layers (from lowest to highest z-index):
     * <ul>
     *   <li>{@link OverlayLayer#BACKGROUND_TILES} - Tile overlays and base maps</li>
     *   <li>{@link OverlayLayer#BACKGROUND_SHAPES} - Background polylines and polygons</li>
     *   <li>{@link OverlayLayer#DECORATION} - Tiny markers, vertex dots, decorative elements</li>
     *   <li>{@link OverlayLayer#MAIN_CONTENT} - Main polylines and primary content</li>
     *   <li>{@link OverlayLayer#INTERACTIVE_BACKGROUND} - Clickable polylines and selectable shapes</li>
     *   <li>{@link OverlayLayer#INTERACTIVE_CONTENT} - Main markers and important interactive elements</li>
     *   <li>{@link OverlayLayer#USER_DRAWING} - User-drawn lines that should be on top</li>
     *   <li>{@link OverlayLayer#OVERLAY_CONTROLS} - UI overlays and controls</li>
     *   <li>{@link OverlayLayer#POPUP_CONTENT} - Info windows and popups</li>
     *   <li>{@link OverlayLayer#DEBUG_OVERLAY} - Debug information, always on top</li>
     * </ul>
     * </p>
     * <p>
     * When an overlay is added to the overlay manager using {@link #add(Overlay)}, it is
     * automatically assigned to an appropriate layer based on its type. You can reassign
     * an overlay to a different layer using {@link #assignOverlayToLayer(Overlay, OverlayLayer)}.
     * </p>
     * <p>
     * When an overlay is removed from the overlay manager using {@link #remove(Object)} or
     * {@link #remove(int)}, it is automatically removed from its assigned layer, and subsequent
     * calls to this method will return null for that overlay.
     * </p>
     *
     * @param overlay The overlay to query. May be null.
     * @return The {@link OverlayLayer} that the overlay is currently assigned to, or null if:
     *         <ul>
     *           <li>The overlay parameter is null</li>
     *           <li>The overlay has not been added to this overlay manager</li>
     *           <li>The overlay has been removed from this overlay manager</li>
     *           <li>The overlay was added before the layer system was enabled</li>
     *           <li>The layer system is disabled (see {@link #setUseLayerSystem(boolean)})</li>
     *         </ul>
     * @see #assignOverlayToLayer(Overlay, OverlayLayer)
     * @see #getOverlaysInLayer(OverlayLayer)
     * @see #setUseLayerSystem(boolean)
     * @since Enhanced layer system
     */
    public OverlayLayer getOverlayLayer(Overlay overlay) {
        return mOverlayToLayer.get(overlay);
    }

    /**
     * ENHANCED FIX: Get all overlays in a specific layer
     * @since Enhanced layer system
     */
    public List<Overlay> getOverlaysInLayer(OverlayLayer layer) {
        List<Overlay> layerOverlays = mLayeredOverlays.get(layer);
        return layerOverlays != null ? new ArrayList<>(layerOverlays) : new ArrayList<>();
    }

    /**
     * Validates the consistency of the layer system data structures for debugging purposes.
     * <p>
     * This package-private method checks that the three data structures used by the layer system
     * are in sync:
     * <ul>
     *   <li>{@code mOverlayList} - The main list of all overlays</li>
     *   <li>{@code mLayeredOverlays} - Map from layer to list of overlays in that layer</li>
     *   <li>{@code mOverlayToLayer} - Map from overlay to its assigned layer</li>
     * </ul>
     * </p>
     * <p>
     * The method performs the following consistency checks:
     * <ol>
     *   <li>Every overlay in {@code mOverlayToLayer} must exist in its corresponding layer list in {@code mLayeredOverlays}</li>
     *   <li>Every overlay in a layer list in {@code mLayeredOverlays} must be tracked in {@code mOverlayToLayer}</li>
     * </ol>
     * </p>
     * <p>
     * If any inconsistencies are found, warnings are logged to help identify bugs in the layer
     * management code. This method is intended for debugging and testing purposes.
     * </p>
     * <p>
     * Note: This method does not check if overlays in {@code mOverlayList} are properly assigned
     * to layers, as overlays can exist in the main list without being in the layer system
     * (for backward compatibility when the layer system is disabled).
     * </p>
     *
     * @return true if all data structures are consistent, false if inconsistencies were found
     * @since Enhanced layer system - Task 5
     */
    public boolean validateLayerConsistency() {
        boolean isConsistent = true;

        // Check 1: All overlays in mOverlayToLayer must exist in their corresponding layer lists
        for (Map.Entry<Overlay, OverlayLayer> entry : mOverlayToLayer.entrySet()) {
            Overlay overlay = entry.getKey();
            OverlayLayer layer = entry.getValue();

            List<Overlay> layerList = mLayeredOverlays.get(layer);
            if (layerList == null) {
                Log.w(IMapView.LOGTAG, "Layer consistency check failed: Layer " + layer +
                      " does not exist in mLayeredOverlays for overlay " + overlay);
                isConsistent = false;
            } else if (!layerList.contains(overlay)) {
                Log.w(IMapView.LOGTAG, "Layer consistency check failed: Overlay " + overlay +
                      " is tracked in mOverlayToLayer as being in layer " + layer +
                      " but does not exist in that layer's list");
                isConsistent = false;
            }
        }

        // Check 2: All overlays in layer lists must be tracked in mOverlayToLayer
        for (Map.Entry<OverlayLayer, List<Overlay>> entry : mLayeredOverlays.entrySet()) {
            OverlayLayer layer = entry.getKey();
            List<Overlay> layerList = entry.getValue();

            if (layerList != null) {
                for (Overlay overlay : layerList) {
                    if (overlay == null) {
                        Log.w(IMapView.LOGTAG, "Layer consistency check failed: Null overlay found in layer " + layer);
                        isConsistent = false;
                        continue;
                    }

                    OverlayLayer trackedLayer = mOverlayToLayer.get(overlay);
                    if (trackedLayer == null) {
                        Log.w(IMapView.LOGTAG, "Layer consistency check failed: Overlay " + overlay +
                              " exists in layer " + layer + " but is not tracked in mOverlayToLayer");
                        isConsistent = false;
                    } else if (trackedLayer != layer) {
                        Log.w(IMapView.LOGTAG, "Layer consistency check failed: Overlay " + overlay +
                              " exists in layer " + layer + " but mOverlayToLayer says it's in layer " + trackedLayer);
                        isConsistent = false;
                    }
                }
            }
        }

        if (isConsistent) {
            Log.d(IMapView.LOGTAG, "Layer consistency check passed: All data structures are in sync");
        }

        return isConsistent;
    }

    /* OnGestureListener */

    @Override
    public boolean onDown(final MotionEvent pEvent, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onDown(pEvent, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onFling(final MotionEvent pEvent1, final MotionEvent pEvent2,
                           final float pVelocityX, final float pVelocityY, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onFling(pEvent1, pEvent2, pVelocityX, pVelocityY, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onLongPress(final MotionEvent pEvent, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onLongPress(pEvent, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onScroll(final MotionEvent pEvent1, final MotionEvent pEvent2,
                            final float pDistanceX, final float pDistanceY, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onScroll(pEvent1, pEvent2, pDistanceX, pDistanceY, pMapView)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public void onShowPress(final MotionEvent pEvent, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            overlay.onShowPress(pEvent, pMapView);
        }
    }

    @Override
    public boolean onSingleTapUp(final MotionEvent pEvent, final MapView pMapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay.onSingleTapUp(pEvent, pMapView)) {
                return true;
            }
        }

        return false;
    }

    // ** Options Menu **//

    @Override
    public void setOptionsMenusEnabled(final boolean pEnabled) {
        for (final Overlay overlay : mOverlayList) {
            if ((overlay instanceof IOverlayMenuProvider)
                    && ((IOverlayMenuProvider) overlay).isOptionsMenuEnabled()) {
                ((IOverlayMenuProvider) overlay).setOptionsMenuEnabled(pEnabled);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu pMenu, final int menuIdOffset, final MapView mapView) {
        boolean result = true;
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay instanceof IOverlayMenuProvider) {
                final IOverlayMenuProvider overlayMenuProvider = (IOverlayMenuProvider) overlay;
                if (overlayMenuProvider.isOptionsMenuEnabled()) {
                    result &= overlayMenuProvider.onCreateOptionsMenu(pMenu, menuIdOffset, mapView);
                }
            }
        }

        if (mTilesOverlay != null && mTilesOverlay.isOptionsMenuEnabled()) {
            result &= mTilesOverlay.onCreateOptionsMenu(pMenu, menuIdOffset, mapView);
        }

        return result;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu pMenu, final int menuIdOffset, final MapView mapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay instanceof IOverlayMenuProvider) {
                final IOverlayMenuProvider overlayMenuProvider = (IOverlayMenuProvider) overlay;
                if (overlayMenuProvider.isOptionsMenuEnabled()) {
                    overlayMenuProvider.onPrepareOptionsMenu(pMenu, menuIdOffset, mapView);
                }
            }
        }

        if (mTilesOverlay != null && mTilesOverlay.isOptionsMenuEnabled()) {
            mTilesOverlay.onPrepareOptionsMenu(pMenu, menuIdOffset, mapView);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item, final int menuIdOffset, final MapView mapView) {
        for (final Overlay overlay : this.overlaysReversed()) {
            if (overlay instanceof IOverlayMenuProvider) {
                final IOverlayMenuProvider overlayMenuProvider = (IOverlayMenuProvider) overlay;
                if (overlayMenuProvider.isOptionsMenuEnabled() &&
                        overlayMenuProvider.onOptionsItemSelected(item, menuIdOffset, mapView)) {
                    return true;
                }
            }
        }

        if (mTilesOverlay != null &&
                mTilesOverlay.isOptionsMenuEnabled() &&
                mTilesOverlay.onOptionsItemSelected(item, menuIdOffset, mapView)) {
            return true;
        }

        return false;
    }

}
