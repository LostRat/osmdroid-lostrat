package org.osmdroid.util;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.util.Log;

/**
 * Centralized display density management for osmdroid components.
 * Provides consistent scaling across MapForge tiles, overlays, and UI elements.
 *
 * @since API 23+ optimization
 */
public class DisplayDensityManager {

    private static final String TAG = "DisplayDensityManager";

    // Standard Android density categories
    public static final float DENSITY_LOW = 0.75f;      // ldpi
    public static final float DENSITY_MEDIUM = 1.0f;    // mdpi (baseline)
    public static final float DENSITY_HIGH = 1.5f;      // hdpi
    public static final float DENSITY_XHIGH = 2.0f;     // xhdpi
    public static final float DENSITY_XXHIGH = 3.0f;    // xxhdpi
    public static final float DENSITY_XXXHIGH = 4.0f;   // xxxhdpi

    // Singleton instance
    private static volatile DisplayDensityManager sInstance;

    // Density metrics
    private float mDensity;
    private float mScaledDensity;
    private int mDensityDpi;
    private float mXdpi;
    private float mYdpi;

    // Calculated scale factors
    private float mMapForgeScaleFactor;
    private float mOverlayScaleFactor;
    private float mTextScaleFactor;

    private DisplayDensityManager() {
        // Private constructor for singleton
    }

    /**
     * Initialize the density manager with application context
     */
    public static void initialize(Context context) {
        if (sInstance == null) {
            synchronized (DisplayDensityManager.class) {
                if (sInstance == null) {
                    sInstance = new DisplayDensityManager();
                    sInstance.initializeMetrics(context.getApplicationContext());
                }
            }
        }
    }

    /**
     * Get the singleton instance
     */
    public static DisplayDensityManager getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException("DisplayDensityManager not initialized. Call initialize(context) first.");
        }
        return sInstance;
    }

    /**
     * Auto-initialize from any View context (mimics MapView's approach)
     */
    public static void autoInitializeFromView(android.view.View view) {
        if (!isInitialized() && view != null) {
            initialize(view.getContext().getApplicationContext());
        }
    }

    private void initializeMetrics(Context context) {
        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        Configuration config = context.getResources().getConfiguration();

        mDensity = metrics.density;
        mScaledDensity = mDensity * config.fontScale;
        mDensityDpi = metrics.densityDpi;
        mXdpi = metrics.xdpi;
        mYdpi = metrics.ydpi;

        calculateScaleFactors();

        Log.d(TAG, String.format("Density initialized: density=%.2f, scaledDensity=%.2f, dpi=%d",
                mDensity, mScaledDensity, mDensityDpi));
        Log.d(TAG, String.format("Scale factors: mapsforge=%.2f, overlay=%.2f, text=%.2f",
                mMapForgeScaleFactor, mOverlayScaleFactor, mTextScaleFactor));
    }

    private void calculateScaleFactors() {
        // MapForge scale factor: match native MapForge scaling behavior
        // Use inverse relationship similar to gesture threshold approach
        float baseGestureThreshold = 16.0f * mDensity + 0.5f; // Android's applyDimension equivalent
        mMapForgeScaleFactor = Math.max(0.2f, Math.min(1.0f, 0.6f * (34.0f / baseGestureThreshold)));

        // Overlay scale factor: for lines, markers, and shapes
        mOverlayScaleFactor = Math.max(1.0f, mDensity);

        // Text scale factor: for labels and UI text
        mTextScaleFactor = Math.max(1.0f, mScaledDensity);
    }

    /**
     * Get the optimal MapForge user scale factor
     */
    public float getMapForgeScaleFactor() {
        requireInitialized();
        return mMapForgeScaleFactor;
    }

    /**
     * Get the overlay scale factor for lines, markers, etc.
     */
    public float getOverlayScaleFactor() {
        requireInitialized();
        return mOverlayScaleFactor;
    }

    /**
     * Get the text scale factor for labels and UI text
     */
    public float getTextScaleFactor() {
        requireInitialized();
        return mTextScaleFactor;
    }

    /**
     * Scale a dimension (dp) to pixels
     */
    public float dpToPx(float dp) {
        return dp * mDensity;
    }

    /**
     * Scale a text size (sp) to pixels
     */
    public float spToPx(float sp) {
        return sp * mScaledDensity;
    }

    /**
     * Scale pixels to dp
     */
    public float pxToDp(float px) {
        return px / mDensity;
    }

    /**
     * Get scaled line width for overlays
     */
    public float getScaledLineWidth(float baseWidth) {
        autoInitializeIfPossible();
        requireInitialized();
        return baseWidth * mOverlayScaleFactor;
    }

    /**
     * Get scaled text size for overlays
     */
    public float getScaledTextSize(float baseTextSize) {
        autoInitializeIfPossible();
        requireInitialized();
        return baseTextSize * mTextScaleFactor;
    }

    /**
     * Get scaled marker size
     */
    public float getScaledMarkerSize(float baseSize) {
        requireInitialized();
        return baseSize * mOverlayScaleFactor;
    }

    /**
     * Get density category for debugging
     */
    public String getDensityCategory() {
        if (mDensityDpi <= 120) return "ldpi";
        if (mDensityDpi <= 160) return "mdpi";
        if (mDensityDpi <= 240) return "hdpi";
        if (mDensityDpi <= 320) return "xhdpi";
        if (mDensityDpi <= 480) return "xxhdpi";
        return "xxxhdpi";
    }

    /**
     * Get current density metrics
     */
    public float getDensity() {
        return mDensity;
    }

    public float getScaledDensity() {
        return mScaledDensity;
    }

    public int getDensityDpi() {
        return mDensityDpi;
    }

    /**
     * Check if the manager is initialized
     */
    public static boolean isInitialized() {
        return sInstance != null;
    }

    /**
     * Reset the singleton instance (for testing purposes)
     */
    public static synchronized void reset() {
        sInstance = null;
    }

    /**
     * Reset and recalculate scale factors (for tile source changes)
     */
    public static void recalculateScaling() {
        if (sInstance != null) {
            sInstance.calculateScaleFactors();
        }
    }

    /**
     * Detect if map is using DPI scaling and adjust accordingly
     */
    public static void adjustForMapScaling(boolean mapUsesDpiScaling) {
        requireInitialized();

        DisplayDensityManager instance = getInstance();

        // Reset to base values first
        instance.calculateScaleFactors();

        // When map scales tiles to DPI, our overlays need less aggressive scaling
        if (mapUsesDpiScaling) {
            instance.mMapForgeScaleFactor *= 0.7f; // Reduce MapForge scaling
            instance.mOverlayScaleFactor *= 0.8f;  // Reduce overlay scaling
            instance.mTextScaleFactor *= 0.8f;     // Reduce text scaling
        }
    }

    /**
     * Throws clear error if not initialized with helpful fix message
     */
    private static void requireInitialized() {
        if (!isInitialized()) {
            throw new IllegalStateException(
                    "DisplayDensityManager not initialized!\n" +
                            "\n" +
                            "FIX: Add this to your Activity's onCreate() method BEFORE using overlays:\n" +
                            "    DisplayDensityManager.initialize(getApplicationContext());\n" +
                            "\n" +
                            "Example:\n" +
                            "    @Override\n" +
                            "    protected void onCreate(Bundle savedInstanceState) {\n" +
                            "        super.onCreate(savedInstanceState);\n" +
                            "        DisplayDensityManager.initialize(getApplicationContext()); // ADD THIS\n" +
                            "        setContentView(R.layout.activity_main);\n" +
                            "        // ... rest of your code\n" +
                            "    }"
            );
        }
    }

    /**
     * Attempt auto-initialization by finding a View context
     */
    private static void autoInitializeIfPossible() {
        if (isInitialized()) return;

        // Try to find current activity context through stack trace
        try {
            StackTraceElement[] stack = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stack) {
                String className = element.getClassName();
                if (className.contains("MapView") || className.contains("Overlay")) {
                    // We're being called from map-related code, but can't auto-init without context
                    break;
                }
            }
        } catch (Exception e) {
            // Auto-initialization failed, will throw clear error in requireInitialized
        }
    }
}