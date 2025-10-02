package org.osmdroid.views.overlay.gestures;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;

import org.osmdroid.events.MapEvent;
import org.osmdroid.events.RotationEvent;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.IOverlayMenuProvider;
import org.osmdroid.views.overlay.Overlay;

import java.util.concurrent.CopyOnWriteArrayList;

public class RotationGestureOverlay extends Overlay implements
        RotationGestureDetector.RotationListener, IOverlayMenuProvider {
    private final static boolean SHOW_ROTATE_MENU_ITEMS = false;
    private final static String TAG = "RotationGestureOverlay";

    private final static int MENU_ENABLED = getSafeMenuId();
    private final static int MENU_ROTATE_CCW = getSafeMenuId();
    private final static int MENU_ROTATE_CW = getSafeMenuId();

    private final RotationGestureDetector mRotationDetector;
    protected MapView mMapView;
    private boolean mOptionsMenuEnabled = true;
    
    // Listener management
    private final CopyOnWriteArrayList<RotationGestureListener> mRotationListeners = new CopyOnWriteArrayList<>();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    
    // Rotation state tracking
    private enum RotationState { IDLE, ROTATING }
    private RotationState mRotationState = RotationState.IDLE;
    private float mAccumulatedAngle = 0f;

    /**
     * use {@link #RotationGestureOverlay(MapView)} instead.
     */
    @Deprecated
    public RotationGestureOverlay(Context context, MapView mapView) {
        this(mapView);
    }

    public RotationGestureOverlay(MapView mapView) {
        super();
        mMapView = mapView;
        mRotationDetector = new RotationGestureDetector(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event, MapView mapView) {
        mRotationDetector.onTouch(event);
        
        // Handle rotation end when touch is released
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (mRotationState == RotationState.ROTATING) {
                handleRotationEnd();
            }
        }
        
        return super.onTouchEvent(event, mapView);
    }

    @Override
    public void onRotate(float deltaAngle) {
        // Handle rotation start
        if (mRotationState == RotationState.IDLE) {
            mRotationState = RotationState.ROTATING;
            mAccumulatedAngle = 0f;
            onRotationGestureStarted(mMapView.getMapOrientation());
            notifyRotationStarted(mMapView.getMapOrientation());
        }
        
        mAccumulatedAngle += deltaAngle;
        
        // Apply rotation immediately - no throttling for smooth rotation
        float newOrientation = mMapView.getMapOrientation() + deltaAngle;
        mMapView.setMapOrientation(newOrientation);
        
        // Notify listeners
        onRotationGestureDelta(deltaAngle, newOrientation);
        notifyRotation(deltaAngle, newOrientation);
    }

    @Override
    public void onDetach(MapView map) {
        // Handle rotation end if we're currently rotating
        if (mRotationState == RotationState.ROTATING) {
            handleRotationEnd();
        }
        
        // Clean up listeners and handlers
        mRotationListeners.clear();
        mMainHandler.removeCallbacksAndMessages(null);
        mMapView = null;
    }

    @Override
    public boolean isOptionsMenuEnabled() {
        return mOptionsMenuEnabled;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu pMenu, int pMenuIdOffset, MapView pMapView) {
        pMenu.add(0, MENU_ENABLED + pMenuIdOffset, Menu.NONE, "Enable rotation").setIcon(
                android.R.drawable.ic_menu_info_details);
        if (SHOW_ROTATE_MENU_ITEMS) {
            pMenu.add(0, MENU_ROTATE_CCW + pMenuIdOffset, Menu.NONE,
                    "Rotate maps counter clockwise").setIcon(android.R.drawable.ic_menu_rotate);
            pMenu.add(0, MENU_ROTATE_CW + pMenuIdOffset, Menu.NONE, "Rotate maps clockwise")
                    .setIcon(android.R.drawable.ic_menu_rotate);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem pItem, int pMenuIdOffset, MapView pMapView) {
        if (pItem.getItemId() == MENU_ENABLED + pMenuIdOffset) {
            if (this.isEnabled()) {
                mMapView.setMapOrientation(0);
                notifyProgrammaticChange(0);
                this.setEnabled(false);
            } else {
                this.setEnabled(true);
                return true;
            }
        } else if (pItem.getItemId() == MENU_ROTATE_CCW + pMenuIdOffset) {
            float newOrientation = mMapView.getMapOrientation() - 10;
            mMapView.setMapOrientation(newOrientation);
            notifyProgrammaticChange(newOrientation);
        } else if (pItem.getItemId() == MENU_ROTATE_CW + pMenuIdOffset) {
            float newOrientation = mMapView.getMapOrientation() + 10;
            mMapView.setMapOrientation(newOrientation);
            notifyProgrammaticChange(newOrientation);
        }

        return false;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu pMenu, final int pMenuIdOffset, final MapView pMapView) {
        pMenu.findItem(MENU_ENABLED + pMenuIdOffset).setTitle(
                this.isEnabled() ? "Disable rotation" : "Enable rotation");
        return false;
    }

    @Override
    public void setOptionsMenuEnabled(boolean enabled) {
        mOptionsMenuEnabled = enabled;
    }

    @Override
    public void setEnabled(final boolean pEnabled) {
        mRotationDetector.setEnabled(pEnabled);
        super.setEnabled(pEnabled);
    }
    
    // ========== Listener Management Methods ==========
    
    /**
     * Adds a rotation gesture listener to receive rotation events.
     * Listeners are called on the main UI thread.
     * 
     * @param listener The listener to add
     */
    public void addRotationGestureListener(RotationGestureListener listener) {
        if (listener != null && !mRotationListeners.contains(listener)) {
            mRotationListeners.add(listener);
        }
    }
    
    /**
     * Removes a rotation gesture listener.
     * 
     * @param listener The listener to remove
     */
    public void removeRotationGestureListener(RotationGestureListener listener) {
        mRotationListeners.remove(listener);
    }
    
    /**
     * Removes all rotation gesture listeners.
     */
    public void clearRotationGestureListeners() {
        mRotationListeners.clear();
    }
    
    /**
     * Gets the number of registered rotation gesture listeners.
     * 
     * @return Number of listeners
     */
    public int getRotationGestureListenerCount() {
        return mRotationListeners.size();
    }
    
    // ========== Protected Methods for Extension ==========
    
    /**
     * Called when a rotation gesture starts. Override in subclasses for custom behavior.
     * 
     * @param orientation Current map orientation when rotation started
     */
    protected void onRotationGestureStarted(float orientation) {
        // Default implementation does nothing - override in subclasses
    }
    
    /**
     * Called during rotation gesture with delta changes. Override in subclasses for custom behavior.
     * 
     * @param deltaAngle The change in angle for this rotation step
     * @param currentOrientation Current total map orientation
     */
    protected void onRotationGestureDelta(float deltaAngle, float currentOrientation) {
        // Default implementation does nothing - override in subclasses
    }
    
    /**
     * Called when a rotation gesture ends. Override in subclasses for custom behavior.
     * 
     * @param orientation Final map orientation when rotation ended
     */
    protected void onRotationGestureEnded(float orientation) {
        // Default implementation does nothing - override in subclasses
    }
    
    /**
     * Gets the current MapView. Protected for subclass access.
     * 
     * @return The current MapView, may be null if detached
     */
    protected MapView getMapView() {
        return mMapView;
    }
    
    /**
     * Gets the current accumulated angle for the ongoing rotation gesture.
     * 
     * @return Accumulated angle in degrees, 0 if no rotation in progress
     */
    protected float getAccumulatedAngle() {
        return mAccumulatedAngle;
    }
    
    /**
     * Gets the current rotation state.
     * 
     * @return Current rotation state (IDLE or ROTATING)
     */
    protected boolean isRotating() {
        return mRotationState == RotationState.ROTATING;
    }
    
    // ========== Private Notification Methods ==========
    
    private void notifyRotationStarted(final float orientation) {
        if (mRotationListeners.isEmpty()) return;
        
        final RotationEvent event = new RotationEvent(mMapView, orientation, 0f, RotationEvent.RotationType.GESTURE_START);
        
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (RotationGestureListener listener : mRotationListeners) {
                    try {
                        listener.onRotationStarted(event);
                    } catch (Exception e) {
                        Log.w(TAG, "Exception in rotation listener onRotationStarted", e);
                    }
                }
            }
        });
    }
    
    private void notifyRotation(final float deltaAngle, final float currentOrientation) {
        if (mRotationListeners.isEmpty()) return;
        
        final RotationEvent event = new RotationEvent(mMapView, currentOrientation, deltaAngle, RotationEvent.RotationType.GESTURE_DELTA);
        
        // Post to main thread without throttling for smooth updates
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (RotationGestureListener listener : mRotationListeners) {
                    try {
                        listener.onRotation(event);
                    } catch (Exception e) {
                        Log.w(TAG, "Exception in rotation listener onRotation", e);
                    }
                }
            }
        });
    }
    
    private void notifyRotationEnded(final float orientation) {
        if (mRotationListeners.isEmpty()) return;
        
        final RotationEvent event = new RotationEvent(mMapView, orientation, 0f, RotationEvent.RotationType.GESTURE_END);
        
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (RotationGestureListener listener : mRotationListeners) {
                    try {
                        listener.onRotationEnded(event);
                    } catch (Exception e) {
                        Log.w(TAG, "Exception in rotation listener onRotationEnded", e);
                    }
                }
            }
        });
    }
    
    private void notifyProgrammaticChange(final float orientation) {
        if (mRotationListeners.isEmpty()) return;
        
        final RotationEvent event = new RotationEvent(mMapView, orientation, 0f, RotationEvent.RotationType.PROGRAMMATIC_CHANGE);
        
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                for (RotationGestureListener listener : mRotationListeners) {
                    try {
                        listener.onRotation(event);
                    } catch (Exception e) {
                        Log.w(TAG, "Exception in rotation listener programmatic change", e);
                    }
                }
            }
        });
    }
    
    private void handleRotationEnd() {
        if (mRotationState == RotationState.ROTATING) {
            mRotationState = RotationState.IDLE;
            float currentOrientation = mMapView != null ? mMapView.getMapOrientation() : 0f;
            onRotationGestureEnded(currentOrientation);
            notifyRotationEnded(currentOrientation);
        }
    }
}
