package org.osmdroid.samplefragments.location;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.osmdroid.R;
import org.osmdroid.events.RotationEvent;
import org.osmdroid.samplefragments.BaseSampleFragment;
import org.osmdroid.views.overlay.gestures.CustomRotationOverlay;
import org.osmdroid.views.overlay.gestures.RotationGestureListener;

import java.util.Locale;

/**
 * Demonstrates the LostRat fork's rotation listener API.
 * <p>
 * Two mechanisms are shown together:
 * <ol>
 *   <li>{@link CustomRotationOverlay}: a {@code RotationGestureOverlay} subclass that keeps a compass
 *       {@link ImageView} counter-rotated to the map and shows/hides an indicator view while a
 *       two-finger rotation gesture is in progress.</li>
 *   <li>{@link RotationGestureListener}: callbacks with a {@link RotationEvent} carrying the current
 *       orientation, the per-event delta and whether the change came from a gesture or from code.</li>
 * </ol>
 * The two rotate buttons change the orientation programmatically so the programmatic path can be
 * compared with the gesture path.
 * <p>
 * This replaces the earlier {@code org.osmdroid.example.RotationListenerExample} Activity that had been
 * placed inside the library source set (2026-09-03).
 */
public class SampleRotationListener extends BaseSampleFragment implements View.OnClickListener {

    private static final String TAG = "RotationListenerSample";

    private ImageButton mBtnRotateLeft;
    private ImageButton mBtnRotateRight;
    private TextView mRotationDisplay;
    private ImageView mCompassIcon;
    private CustomRotationOverlay mRotationOverlay;

    @Override
    public String getSampleTitle() {
        return "Map Rotation Listener (compass + events)";
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final RelativeLayout root = (RelativeLayout) inflater.inflate(R.layout.map_with_locationbox_controls, null);
        mMapView = root.findViewById(R.id.mapview);

        mBtnRotateLeft = root.findViewById(R.id.btnRotateLeft);
        mBtnRotateLeft.setOnClickListener(this);
        mBtnRotateRight = root.findViewById(R.id.btnRotateRight);
        mBtnRotateRight.setOnClickListener(this);

        mRotationDisplay = root.findViewById(R.id.textViewCurrentLocation);
        mRotationDisplay.setText(formatOrientation(0f));

        // Compass icon pinned to the bottom-right corner of the map.
        mCompassIcon = new ImageView(root.getContext());
        mCompassIcon.setImageResource(R.drawable.ic_menu_compass);
        mCompassIcon.setContentDescription("Compass");
        final float density = getResources().getDisplayMetrics().density;
        final int size = (int) (64 * density);
        final RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(size, size);
        lp.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        lp.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        lp.setMargins(0, 0, (int) (16 * density), (int) (16 * density));
        root.addView(mCompassIcon, lp);

        return root;
    }

    @Override
    public void addOverlays() {
        super.addOverlays();

        // 1. CustomRotationOverlay drives the compass icon and toggles the indicator view.
        mRotationOverlay = new CustomRotationOverlay(mMapView, mCompassIcon, mRotationDisplay);
        mRotationOverlay.setUpdateCompass(true);
        mRotationOverlay.setShowRotationIndicator(false); // keep the text visible; we update it ourselves
        mRotationOverlay.setEnabled(true);

        // 2. RotationGestureListener receives start / delta / end events with a RotationEvent payload.
        mRotationOverlay.addRotationGestureListener(new RotationGestureListener() {
            @Override
            public void onRotationStarted(RotationEvent event) {
                Log.d(TAG, "Rotation started at " + event.getCurrentOrientation() + "°");
                mRotationDisplay.setText("Rotating... " + formatOrientation(event.getCurrentOrientation()));
            }

            @Override
            public void onRotation(RotationEvent event) {
                final float angle = event.getCurrentOrientation();
                mRotationDisplay.setText(formatOrientation(angle));
                if (event.getRotationType() == RotationEvent.RotationType.GESTURE_DELTA) {
                    Log.d(TAG, String.format(Locale.US, "gesture delta %.2f°, orientation %.1f°",
                            event.getDeltaAngle(), angle));
                } else if (event.getRotationType() == RotationEvent.RotationType.PROGRAMMATIC_CHANGE) {
                    Log.d(TAG, "programmatic change to " + angle + "°");
                }
            }

            @Override
            public void onRotationEnded(RotationEvent event) {
                Log.d(TAG, "Rotation ended at " + event.getCurrentOrientation() + "°");
                mRotationDisplay.setText(formatOrientation(event.getCurrentOrientation()));
            }
        });

        mMapView.getOverlays().add(mRotationOverlay);
        mRotationOverlay.updateCompassToCurrentOrientation();
    }

    @Override
    public void onClick(View v) {
        final int id = v.getId();
        float angle = mMapView.getMapOrientation();
        if (id == R.id.btnRotateLeft) {
            angle += 10f;
            if (angle >= 360f) {
                angle -= 360f;
            }
        } else if (id == R.id.btnRotateRight) {
            angle -= 10f;
            if (angle < 0f) {
                angle += 360f;
            }
        } else {
            return;
        }
        // Programmatic rotation: the overlay does not see a gesture, so refresh the compass and
        // label directly.
        mMapView.setMapOrientation(angle);
        if (mRotationOverlay != null) {
            mRotationOverlay.updateCompassToCurrentOrientation();
        }
        mRotationDisplay.setText(formatOrientation(angle));
    }

    @Override
    public void onDestroyView() {
        if (mRotationOverlay != null) {
            mRotationOverlay.clearRotationGestureListeners();
        }
        super.onDestroyView();
    }

    private static String formatOrientation(float angle) {
        return String.format(Locale.US, "Orientation: %.1f°", angle);
    }
}
