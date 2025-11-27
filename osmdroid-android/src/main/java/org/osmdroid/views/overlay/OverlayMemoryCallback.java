package org.osmdroid.views.overlay;

import org.osmdroid.util.BoundingBox;

/**
 * Optional callback interface for advanced users who want to coordinate their application
 * cache with overlay memory management.
 * <p>
 * This interface is completely optional - the DefaultOverlayManager handles all memory
 * issues internally and works perfectly without any callbacks set. The callback is only
 * for applications that want to optimize their own data loading patterns.
 * </p>
 * <p>
 * Example usage for applications with geographic data caches:
 * <pre>
 * overlayManager.setMemoryCallback(new OverlayMemoryCallback() {
 *     {@literal @}Override
 *     public void onMemoryPressure(double suggestedReduction) {
 *         // Optional: Remove some cached polylines to reduce memory usage
 *         myDataCache.clearOldestEntries((int)(myDataCache.size() * suggestedReduction));
 *     }
 *
 *     {@literal @}Override
 *     public void onZoomChanged(double oldZoom, double newZoom, BoundingBox viewport) {
 *         // Optional: Load appropriate detail level for new zoom
 *         if (newZoom > oldZoom + 1) {
 *             myDataCache.loadHighDetailData(viewport);
 *         }
 *     }
 * });
 * </pre>
 * </p>
 *
 * @since Overlay memory optimization
 */
public interface OverlayMemoryCallback {

    /**
     * Called when the overlay manager detects memory pressure and suggests reducing
     * the number of active overlays.
     * <p>
     * This callback is optional - if not implemented or if an exception occurs,
     * the overlay manager continues working normally with its built-in memory
     * management strategies.
     * </p>
     * <p>
     * Applications can use this callback to:
     * <ul>
     *   <li>Clear cached geographic data that's not currently visible</li>
     *   <li>Reduce the detail level of polylines outside the viewport</li>
     *   <li>Remove temporary overlays that are no longer needed</li>
     * </ul>
     * </p>
     *
     * @param suggestedReduction A value between 0.0 and 1.0 indicating the suggested
     *                          percentage of overlays to remove. For example, 0.3 means
     *                          removing about 30% of current overlays would help reduce
     *                          memory pressure.
     */
    void onMemoryPressure(double suggestedReduction);

    /**
     * Called when the zoom level changes significantly, which often triggers
     * applications to load different levels of geographic detail.
     * <p>
     * This callback is optional and helps applications coordinate their data
     * loading with the overlay manager's memory management. Applications can
     * use this to batch their overlay updates and prevent memory spikes.
     * </p>
     * <p>
     * Common use cases:
     * <ul>
     *   <li>Loading high-detail polylines when zooming in</li>
     *   <li>Switching to simplified geometry when zooming out</li>
     *   <li>Loading data for newly visible geographic areas</li>
     * </ul>
     * </p>
     *
     * @param oldZoom The previous zoom level
     * @param newZoom The current zoom level
     * @param viewport The current map viewport bounds
     */
    void onZoomChanged(double oldZoom, double newZoom, BoundingBox viewport);
}
