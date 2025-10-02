package org.osmdroid.views.overlay.gestures;

import android.view.View;
import android.widget.ImageView;

import org.osmdroid.views.MapView;

/**
 * Example custom rotation overlay that demonstrates how to extend RotationGestureOverlay
 * with custom behavior and UI element references.
 * 
 * This example shows how to:
 * - Extend RotationGestureOverlay for custom behavior
 * - Add UI element references to the overlay
 * - Override rotation event methods for custom handling
 * - Integrate with activity UI components
 * 
 * <p>Example usage in an Activity:</p>
 * <pre>
 * ImageView compassIcon = findViewById(R.id.compass_icon);
 * CustomRotationOverlay rotationOverlay = new CustomRotationOverlay(mapView, compassIcon);
 * mapView.getOverlays().add(rotationOverlay);
 * </pre>
 * 
 * @author osmdroid team
 */
public class CustomRotationOverlay extends RotationGestureOverlay {
    
    private ImageView mCompassIcon;
    private View mRotationIndicator;
    private boolean mUpdateCompass = true;
    private boolean mShowRotationIndicator = true;
    
    /**
     * Creates a custom rotation overlay with a compass icon reference.
     * 
     * @param mapView The MapView this overlay is attached to
     * @param compassIcon ImageView to rotate counter to the map rotation (can be null)
     */
    public CustomRotationOverlay(MapView mapView, ImageView compassIcon) {
        super(mapView);
        this.mCompassIcon = compassIcon;
    }
    
    /**
     * Creates a custom rotation overlay with both compass and rotation indicator references.
     * 
     * @param mapView The MapView this overlay is attached to
     * @param compassIcon ImageView to rotate counter to the map rotation (can be null)
     * @param rotationIndicator View to show/hide during rotation gestures (can be null)
     */
    public CustomRotationOverlay(MapView mapView, ImageView compassIcon, View rotationIndicator) {
        super(mapView);
        this.mCompassIcon = compassIcon;
        this.mRotationIndicator = rotationIndicator;
    }
    
    /**
     * Sets the compass icon to be updated during rotation.
     * 
     * @param compassIcon ImageView to rotate counter to the map rotation
     */
    public void setCompassIcon(ImageView compassIcon) {
        this.mCompassIcon = compassIcon;
    }
    
    /**
     * Sets the rotation indicator view to show/hide during rotation gestures.
     * 
     * @param rotationIndicator View to show during rotation
     */
    public void setRotationIndicator(View rotationIndicator) {
        this.mRotationIndicator = rotationIndicator;
    }
    
    /**
     * Enables or disables compass icon updates.
     * 
     * @param updateCompass true to update compass icon, false to disable
     */
    public void setUpdateCompass(boolean updateCompass) {
        this.mUpdateCompass = updateCompass;
    }
    
    /**
     * Enables or disables rotation indicator visibility changes.
     * 
     * @param showRotationIndicator true to show indicator during rotation, false to disable
     */
    public void setShowRotationIndicator(boolean showRotationIndicator) {
        this.mShowRotationIndicator = showRotationIndicator;
    }
    
    @Override
    protected void onRotationGestureStarted(float orientation) {
        super.onRotationGestureStarted(orientation);
        
        // Show rotation indicator when rotation starts
        if (mShowRotationIndicator && mRotationIndicator != null) {
            mRotationIndicator.setVisibility(View.VISIBLE);
        }
    }
    
    @Override
    protected void onRotationGestureDelta(float deltaAngle, float currentOrientation) {
        super.onRotationGestureDelta(deltaAngle, currentOrientation);
        
        // Update compass icon to rotate with the map
        if (mUpdateCompass && mCompassIcon != null) {
            // Rotate the compass icon to match the map orientation
            mCompassIcon.setRotation(currentOrientation);
        }
    }
    
    @Override
    protected void onRotationGestureEnded(float orientation) {
        super.onRotationGestureEnded(orientation);
        
        // Hide rotation indicator when rotation ends
        if (mShowRotationIndicator && mRotationIndicator != null) {
            mRotationIndicator.setVisibility(View.GONE);
        }
        
        // Final compass update
        if (mUpdateCompass && mCompassIcon != null) {
            mCompassIcon.setRotation(orientation);
        }
    }
    
    @Override
    public void onDetach(MapView map) {
        // Clean up UI references to prevent memory leaks
        mCompassIcon = null;
        mRotationIndicator = null;
        super.onDetach(map);
    }
    
    /**
     * Manually updates the compass icon to match the current map orientation.
     * Useful for initializing the compass when the overlay is first added.
     */
    public void updateCompassToCurrentOrientation() {
        if (mUpdateCompass && mCompassIcon != null && getMapView() != null) {
            float currentOrientation = getMapView().getMapOrientation();
            mCompassIcon.setRotation(currentOrientation);
        }
    }
    
    /**
     * Gets the current compass icon reference.
     * 
     * @return The compass ImageView, may be null
     */
    public ImageView getCompassIcon() {
        return mCompassIcon;
    }
    
    /**
     * Gets the current rotation indicator reference.
     * 
     * @return The rotation indicator View, may be null
     */
    public View getRotationIndicator() {
        return mRotationIndicator;
    }
}