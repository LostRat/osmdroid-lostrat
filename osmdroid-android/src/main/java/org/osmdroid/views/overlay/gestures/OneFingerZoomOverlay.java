package org.osmdroid.views.overlay.gestures;

import android.annotation.SuppressLint;
import android.view.MotionEvent;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;

@SuppressLint("NewApi")
public class OneFingerZoomOverlay extends Overlay {
    private static final float PIXELS_PER_ZOOM_LEVEL = 300f;
    private static final float DRAG_START_THRESHOLD_PX = 12f;

    /*
     * This overlay owns the full second-tap sequence for Google's
     * double-tap-hold-drag zoom gesture. Three details are intentional:
     * 1. Drag zoom uses signed per-move Y deltas, so dragging up and down zoom
     *    in opposite directions instead of always zooming in.
     * 2. A double-tap with no drag still performs the normal anchored zoom-in
     *    on release; otherwise installing this overlay breaks plain double-tap.
     * 3. While the second tap is held, GestureDetector may still emit a
     *    long-press callback. Consuming that callback keeps app-level custom
     *    long-press timers from interrupting the active one-finger zoom.
     */
    private boolean mIsDoubleClick;
    private boolean mDragZoomStarted;
    private float mAnchorX;
    private float mAnchorY;
    private float mLastY;

    @Override
    public boolean onDoubleTap(MotionEvent event, MapView mapView) {
        mIsDoubleClick = true;
        mDragZoomStarted = false;
        mAnchorX = event.getX();
        mAnchorY = event.getY();
        mLastY = mAnchorY;
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent event, MapView mapView) {
        if (!mIsDoubleClick) {
            return super.onDoubleTapEvent(event, mapView);
        }

        final int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_UP) {
            finishDoubleTap(mapView);
        } else if (action == MotionEvent.ACTION_CANCEL) {
            reset();
        }
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event, MapView mapView) {
        if (mIsDoubleClick) {
            final int action = event.getActionMasked();
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN:
                    reset();
                    return super.onTouchEvent(event, mapView);
                case MotionEvent.ACTION_MOVE:
                    handleMove(event, mapView);
                    break;
                case MotionEvent.ACTION_UP:
                    finishDoubleTap(mapView);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    reset();
                    break;
            }
            return true;
        }
        return super.onTouchEvent(event, mapView);
    }

    @Override
    public boolean onLongPress(MotionEvent event, MapView mapView) {
        if (mIsDoubleClick) {
            return true;
        }
        reset();
        return super.onLongPress(event, mapView);
    }

    private void handleMove(MotionEvent event, MapView mapView) {
        final float y = event.getY();
        final float totalDeltaY = y - mAnchorY;
        if (!mDragZoomStarted && Math.abs(totalDeltaY) < DRAG_START_THRESHOLD_PX) {
            return;
        }

        mDragZoomStarted = true;
        final float deltaY = y - mLastY;
        // Signed movement: dragging down zooms in and dragging up zooms out.
        mapView.getController().setZoom(mapView.getZoomLevelDouble()
                + deltaY / PIXELS_PER_ZOOM_LEVEL);
        mLastY = y;
    }

    private void finishDoubleTap(MapView mapView) {
        if (!mDragZoomStarted) {
            mapView.getController().zoomInFixing(Math.round(mAnchorX), Math.round(mAnchorY));
        }
        reset();
    }

    private void reset() {
        mIsDoubleClick = false;
        mDragZoomStarted = false;
    }
}
