package org.osmdroid.views.overlay.gestures;

import org.osmdroid.events.RotationEvent;

/**
 * Interface for listening to rotation gesture events on the map.
 * Implement this interface to receive callbacks when the map is rotated
 * through touch gestures or programmatic changes.
 * 
 * <p>Example usage:</p>
 * <pre>
 * RotationGestureOverlay rotationOverlay = new RotationGestureOverlay(mapView);
 * rotationOverlay.addRotationGestureListener(new RotationGestureListener() {
 *     {@literal @}Override
 *     public void onRotationStarted(RotationEvent event) {
 *         // Rotation gesture started
 *         Log.d("Rotation", "Started at: " + event.getCurrentOrientation());
 *     }
 *     
 *     {@literal @}Override
 *     public void onRotation(RotationEvent event) {
 *         // Update UI elements based on rotation
 *         float angle = event.getCurrentOrientation();
 *         myCompassIcon.setRotation(-angle); // Counter-rotate icon
 *     }
 *     
 *     {@literal @}Override
 *     public void onRotationEnded(RotationEvent event) {
 *         // Rotation gesture ended
 *         Log.d("Rotation", "Ended at: " + event.getCurrentOrientation());
 *     }
 * });
 * </pre>
 * 
 * @author osmdroid team
 */
public interface RotationGestureListener {
    
    /**
     * Called when a rotation gesture starts.
     * This is called once at the beginning of a rotation gesture.
     * 
     * @param event The rotation event containing rotation details
     */
    void onRotationStarted(RotationEvent event);
    
    /**
     * Called during rotation gesture with delta changes.
     * This is called repeatedly during a rotation gesture as the angle changes.
     * Use this to update UI elements in real-time during rotation.
     * 
     * @param event The rotation event containing rotation details and delta angle
     */
    void onRotation(RotationEvent event);
    
    /**
     * Called when a rotation gesture ends.
     * This is called once at the end of a rotation gesture.
     * 
     * @param event The rotation event containing final rotation details
     */
    void onRotationEnded(RotationEvent event);
}