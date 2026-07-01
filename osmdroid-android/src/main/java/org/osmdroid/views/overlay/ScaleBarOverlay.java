package org.osmdroid.views.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.WindowManager;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.library.R;
import org.osmdroid.util.DisplayDensityManager;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.constants.GeoConstants;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;

import java.lang.reflect.Field;
import java.util.Locale;

/**
 * ScaleBarOverlay.java
 * <p>
 * Puts a scale bar in the top-left corner of the screen, offset by a configurable
 * number of pixels. The bar is scaled to 1-inch length by querying for the physical
 * DPI of the screen. The size of the bar is printed between the tick marks. A
 * vertical (longitude) scale can be enabled. Scale is printed in metric (kilometers,
 * meters), imperial (miles, feet) and nautical (nautical miles, feet).
 * <p>
 * Author: Erik Burrows, Griffin Systems LLC
 * erik@griffinsystems.org
 * <p>
 * Change Log:
 * 2010-10-08: Inclusion to osmdroid trunk
 * 2015-12-17: Allow for top, bottom, left or right placement by W.  Strickling
 * <p>
 * Usage:
 * <code>
 * MapView map = new MapView(...);
 * ScaleBarOverlay scaleBar = new ScaleBarOverlay(map); // Thiw is an important change of calling!
 * <p>
 * map.getOverlays().add(scaleBar);
 * </code>
 * <p>
 * To Do List:
 * 1. Allow for top, bottom, left or right placement. // done in this changement
 * 2. Scale bar to precise displayed scale text after rounding.
 */

public class ScaleBarOverlay extends Overlay implements GeoConstants {

    // ===========================================================
    // Fields
    // ===========================================================

    private static final Rect sTextBoundsRect = new Rect();

    public enum UnitsOfMeasure {
        metric, imperial, nautical
    }

    // Defaults
    int xOffset = 10;
    int yOffset = 10;
    double minZoom = 0;

    UnitsOfMeasure unitsOfMeasure = UnitsOfMeasure.metric;

    boolean latitudeBar = true;
    boolean longitudeBar = false;

    protected boolean alignBottom = false;
    protected boolean alignRight = false;


    // Internal

    private Context context;
    private MapView mMapView;

    protected final Path barPath = new Path();
    protected final Rect latitudeBarRect = new Rect();
    protected final Rect longitudeBarRect = new Rect();

    private double lastZoomLevel = -1;
    private double lastLatitude = 0.0;

    public float xdpi;
    public float ydpi;
    public int screenWidth;
    public int screenHeight;

    private Paint barPaint;
    private Paint barHaloPaint;
    private Paint bgPaint;
    private Paint textPaint;
    private Paint textBackgroundPaint;

    private boolean centred = false;
    private boolean adjustLength = false;
    private float maxLength;

    // Enhanced styling options
    public enum TextSize { SMALLEST, SMALLER, NORMAL, LARGER, LARGEST }
    private TextSize mTextSizeOption = TextSize.NORMAL;
    private boolean mDensityScalingEnabled = true;
    private float mCornerRadius = 4f;
    private boolean mTextBackgroundEnabled = true;
    private int mTextColor = Color.WHITE;

    // Contrast halo drawn underneath the bar lines so they stay legible on
    // both light and dark map backgrounds (satellite imagery, dark themes, etc.)
    private boolean mBarHaloEnabled = true;
    private int mBarHaloColor = Color.WHITE;
    private float mBarHaloExtraWidth; // total extra stroke width over the bar, in px (density-scaled)

    /**
     * @since 6.1.0
     */
    private int mMapWidth;

    /**
     * @since 6.1.0
     */
    private int mMapHeight;

    // ===========================================================
    // Constructors
    // ===========================================================

    public ScaleBarOverlay(final MapView mapView) {
        this(mapView, mapView.getContext(), 0, 0);
    }

    /**
     * @since 6.1.0
     */
    public ScaleBarOverlay(final Context pContext, final int pMapWidth, final int pMapHeight) {
        this(null, pContext, pMapWidth, pMapHeight);
    }

    /**
     * @since 6.1.0
     */
    private ScaleBarOverlay(final MapView pMapView, final Context pContext, final int pMapWidth, final int pMapHeight) {
        super();
        mMapView = pMapView;
        context = pContext;
        mMapWidth = pMapWidth;
        mMapHeight = pMapHeight;

        final DisplayMetrics dm = context.getResources().getDisplayMetrics();

        this.barPaint = new Paint();
        this.barPaint.setColor(Color.BLACK);
        this.barPaint.setAntiAlias(true);
        this.barPaint.setStyle(Style.STROKE);
        this.barPaint.setAlpha(255);
        this.barPaint.setStrokeWidth(1.5f * dm.density); // Thinner crossbar
        this.bgPaint = null;

        this.textPaint = new Paint();
        this.textPaint.setColor(Color.BLACK);
        this.textPaint.setAntiAlias(true);
        this.textPaint.setStyle(Style.FILL);
        this.textPaint.setAlpha(255);
        this.textPaint.setTextSize(6f * dm.density); // 40% smaller than 12dp

        // Text background paint
        this.textBackgroundPaint = new Paint();
        this.textBackgroundPaint.setColor(Color.BLACK);
        this.textBackgroundPaint.setStyle(Style.FILL);
        this.textBackgroundPaint.setAlpha(210); // Slightly darker box for better contrast

        // Contrast halo paint for the bar lines. Drawn first, thicker than the
        // bar itself, so the (black) bar reads clearly over any map background.
        this.barHaloPaint = new Paint();
        this.barHaloPaint.setColor(mBarHaloColor);
        this.barHaloPaint.setAntiAlias(true);
        this.barHaloPaint.setStyle(Style.STROKE);
        this.barHaloPaint.setStrokeCap(Paint.Cap.ROUND);
        this.barHaloPaint.setStrokeJoin(Paint.Join.ROUND);
        // ~2dp of halo on each side of the bar line
        this.mBarHaloExtraWidth = 4f * dm.density;

        this.xdpi = dm.xdpi;
        this.ydpi = dm.ydpi;

        this.screenWidth = dm.widthPixels;
        this.screenHeight = dm.heightPixels;

        // DPI corrections for specific models
        String manufacturer = null;
        try {
            final Field field = android.os.Build.class.getField("MANUFACTURER");
            manufacturer = (String) field.get(null);
        } catch (final Exception ignore) {
        }

        //from 2011? //just comment out
//        if ("motorola".equals(manufacturer) && "DROIDX".equals(android.os.Build.MODEL)) {
//
//            // If the screen is rotated, flip the x and y dpi values
//            WindowManager windowManager = (WindowManager) this.context
//                    .getSystemService(Context.WINDOW_SERVICE);
//            if (windowManager != null && windowManager.getDefaultDisplay().getRotation() > 0) {
//                this.xdpi = (float) (this.screenWidth / 3.75);
//                this.ydpi = (float) (this.screenHeight / 2.1);
//            } else {
//                this.xdpi = (float) (this.screenWidth / 2.1);
//                this.ydpi = (float) (this.screenHeight / 3.75);
//            }
//
//        } else
        if ("motorola".equals(manufacturer) && "Droid".equals(android.os.Build.MODEL)) {
            // http://www.mail-archive.com/android-developers@googlegroups.com/msg109497.html
            this.xdpi = 264;
            this.ydpi = 264;
        }

        // set default max length to 1 inch
        maxLength = 2.54f;
    }

    // ===========================================================
    // Getter & Setter
    // ===========================================================

    /**
     * Sets the minimum zoom level for the scale bar to be drawn.
     *
     * @param zoom minimum zoom level
     */
    public void setMinZoom(final double zoom) {
        this.minZoom = zoom;
    }

    /**
     * Sets the scale bar screen offset for the bar. Note: if the bar is set to be drawn centered,
     * this will be the middle of the bar, otherwise the top left corner.
     *
     * @param x x screen offset
     * @param y z screen offset
     */
    public void setScaleBarOffset(final int x, final int y) {
        xOffset = x;
        yOffset = y;
    }

    /**
     * Sets the bar's line width. (the default is 2)
     *
     * @param width the new line width
     */
    public void setLineWidth(final float width) {
        this.barPaint.setStrokeWidth(width);
    }

    /**
     * Sets the text size. (the default is 12)
     *
     * @param size the new text size
     */
    public void setTextSize(final float size) {
        this.textPaint.setTextSize(size);
    }

    /**
     * Sets the units of measure to be shown in the scale bar
     */
    public void setUnitsOfMeasure(UnitsOfMeasure unitsOfMeasure) {
        this.unitsOfMeasure = unitsOfMeasure;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    /**
     * Gets the units of measure to be shown in the scale bar
     */
    public UnitsOfMeasure getUnitsOfMeasure() {
        return unitsOfMeasure;
    }

    /**
     * Latitudinal / horizontal scale bar flag
     *
     * @param latitude
     */
    public void drawLatitudeScale(final boolean latitude) {
        this.latitudeBar = latitude;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    /**
     * Longitudinal / vertical scale bar flag
     *
     * @param longitude
     */
    public void drawLongitudeScale(final boolean longitude) {
        this.longitudeBar = longitude;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    /**
     * Flag to draw the bar centered around the set offset coordinates or to the right/bottom of the
     * coordinates (default)
     *
     * @param centred set true to centre the bar around the given screen coordinates
     */
    public void setCentred(final boolean centred) {
        this.centred = centred;
        alignBottom = !centred;
        alignRight = !centred;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    public void setAlignBottom(final boolean alignBottom) {
        this.centred = false;
        this.alignBottom = alignBottom;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    public void setAlignRight(final boolean alignRight) {
        this.centred = false;
        this.alignRight = alignRight;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    /**
     * Return's the paint used to draw the bar
     *
     * @return the paint used to draw the bar
     */
    public Paint getBarPaint() {
        return barPaint;
    }

    /**
     * Sets the paint for drawing the bar
     *
     * @param pBarPaint bar drawing paint
     */
    public void setBarPaint(final Paint pBarPaint) {
        if (pBarPaint == null) {
            throw new IllegalArgumentException("pBarPaint argument cannot be null");
        }
        barPaint = pBarPaint;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    /**
     * Returns the paint used to draw the text
     *
     * @return the paint used to draw the text
     */
    public Paint getTextPaint() {
        return textPaint;
    }

    /**
     * Sets the paint for drawing the text
     *
     * @param pTextPaint text drawing paint
     */
    public void setTextPaint(final Paint pTextPaint) {
        if (pTextPaint == null) {
            throw new IllegalArgumentException("pTextPaint argument cannot be null");
        }
        textPaint = pTextPaint;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    /**
     * Sets the background paint. Set to null to disable drawing of background (default)
     *
     * @param pBgPaint the paint for colouring the bar background
     */
    public void setBackgroundPaint(final Paint pBgPaint) {
        bgPaint = pBgPaint;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    /**
     * If enabled, the bar will automatically adjust the length to reflect a round number (starting
     * with 1, 2 or 5). If disabled, the bar will always be drawn in full length representing a
     * fractional distance.
     */
    public void setEnableAdjustLength(boolean adjustLength) {
        this.adjustLength = adjustLength;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    /**
     * Sets the maximum bar length. If adjustLength is disabled this will match exactly the length
     * of the bar. If adjustLength is enabled, the bar will be shortened to reflect a round number
     * in length.
     *
     * @param pMaxLengthInCm maximum length of the bar in the screen in cm. Default is 2.54 (=1 inch)
     */
    public void setMaxLength(final float pMaxLengthInCm) {
        this.maxLength = pMaxLengthInCm;
        lastZoomLevel = -1; // Force redraw of scalebar
    }

    // ===========================================================
    // Methods from SuperClass/Interfaces
    // ===========================================================

    @Override
    public void draw(Canvas c, Projection projection) {

        final double zoomLevel = projection.getZoomLevel();

        if (zoomLevel < minZoom) {
            return;
        }
        final Rect rect = projection.getIntrinsicScreenRect();
        int _screenWidth = rect.width();
        int _screenHeight = rect.height();
        boolean screenSizeChanged = _screenHeight != screenHeight || _screenWidth != screenWidth;
        screenHeight = _screenHeight;
        screenWidth = _screenWidth;
        final IGeoPoint center = projection.fromPixels(screenWidth / 2, screenHeight / 2, null);
        if (zoomLevel != lastZoomLevel || center.getLatitude() != lastLatitude || screenSizeChanged) {
            lastZoomLevel = zoomLevel;
            lastLatitude = center.getLatitude();
            rebuildBarPath(projection);
        }

        int offsetX = xOffset;
        int offsetY = yOffset;
        if (alignBottom) offsetY *= -1;
        if (alignRight) offsetX *= -1;
        if (centred && latitudeBar)
            offsetX += -latitudeBarRect.width() / 2;
        if (centred && longitudeBar)
            offsetY += -longitudeBarRect.height() / 2;

        projection.save(c, false, true);
        c.translate(offsetX, offsetY);

        if (latitudeBar && bgPaint != null)
            c.drawRect(latitudeBarRect, bgPaint);
        if (longitudeBar && bgPaint != null) {
            // Don't draw on top of latitude background...
            int offsetTop = latitudeBar ? latitudeBarRect.height() : 0;
            c.drawRect(longitudeBarRect.left, longitudeBarRect.top + offsetTop,
                    longitudeBarRect.right, longitudeBarRect.bottom, bgPaint);
        }
        // Apply density-aware line scaling
        Paint scaledBarPaint = barPaint;
        if (mDensityScalingEnabled && DisplayDensityManager.isInitialized()) {
            scaledBarPaint = new Paint(barPaint);
            scaledBarPaint.setStrokeWidth(DisplayDensityManager.getInstance().getScaledLineWidth(barPaint.getStrokeWidth()));
        }
        // Draw a contrasting halo underneath so the bar stays visible on any
        // background, then the bar itself on top.
        if (mBarHaloEnabled && barHaloPaint != null) {
            barHaloPaint.setColor(mBarHaloColor);
            barHaloPaint.setStrokeWidth(scaledBarPaint.getStrokeWidth() + mBarHaloExtraWidth);
            c.drawPath(barPath, barHaloPaint);
        }
        c.drawPath(barPath, scaledBarPaint);

        if (latitudeBar) {
            drawLatitudeText(c, projection);
        }
        if (longitudeBar) {
            drawLongitudeText(c, projection);
        }
        projection.restore(c, true);
    }

    // ===========================================================
    // Methods
    // ===========================================================

    public void disableScaleBar() {
        setEnabled(false);
    }

    public void enableScaleBar() {
        setEnabled(true);
    }

    private void drawLatitudeText(final Canvas canvas, final Projection projection) {
        // calculate dots per centimeter
        int xdpcm = (int) ((float) xdpi / 2.54);

        // get length in pixel
        int xLen = (int) (maxLength * xdpcm);

        // Two points, xLen apart, at scale bar screen location
        IGeoPoint p1 = projection.fromPixels((screenWidth / 2) - (xLen / 2), yOffset, null);
        IGeoPoint p2 = projection.fromPixels((screenWidth / 2) + (xLen / 2), yOffset, null);

        // get distance in meters between points
        final double xMeters = ((GeoPoint) p1).distanceToAsDouble(p2);
        // get adjusted distance, shortened to the next lower number starting with 1, 2 or 5
        final double xMetersAdjusted = this.adjustLength ? adjustScaleBarLength(xMeters) : xMeters;
        // get adjusted length in pixels
        final int xBarLengthPixels = (int) (xLen * xMetersAdjusted / xMeters);

        // create text
        final String xMsg = scaleBarLengthText(xMetersAdjusted);
        textPaint.getTextBounds(xMsg, 0, xMsg.length(), sTextBoundsRect);
        final int xTextSpacing = (int) (sTextBoundsRect.height() / 5.0);

        // Apply density-aware text scaling with size option
        Paint scaledTextPaint = new Paint(textPaint);
        float baseSize = textPaint.getTextSize();

        // Apply text size multiplier
        float sizeMultiplier = getTextSizeMultiplier();
        baseSize *= sizeMultiplier;

        if (mDensityScalingEnabled && DisplayDensityManager.isInitialized()) {
            baseSize = DisplayDensityManager.getInstance().getScaledTextSize(baseSize);
        }
        scaledTextPaint.setTextSize(baseSize);
        scaledTextPaint.setColor(mTextColor);

        // Recalculate text bounds with scaled text
        scaledTextPaint.getTextBounds(xMsg, 0, xMsg.length(), sTextBoundsRect);

        float x = xBarLengthPixels / 2 - sTextBoundsRect.width() / 2;
        if (alignRight) x += screenWidth - xBarLengthPixels;
        float y;
        if (alignBottom) {
            y = screenHeight - xTextSpacing * 2;
        } else y = sTextBoundsRect.height() + xTextSpacing;

        // Draw text background if enabled
        if (mTextBackgroundEnabled) {
            float padding = 6f; // More padding around text
            canvas.drawRoundRect(x - padding, y - sTextBoundsRect.height() - padding,
                    x + sTextBoundsRect.width() + padding, y + padding,
                    mCornerRadius, mCornerRadius, textBackgroundPaint);
        }

        canvas.drawText(xMsg, x, y, scaledTextPaint);
    }

    private void drawLongitudeText(final Canvas canvas, final Projection projection) {
        // calculate dots per centimeter
        int ydpcm = (int) ((float) ydpi / 2.54);

        // get length in pixel
        int yLen = (int) (maxLength * ydpcm);

        // Two points, yLen apart, at scale bar screen location
        IGeoPoint p1 = projection
                .fromPixels(screenWidth / 2, (screenHeight / 2) - (yLen / 2), null);
        IGeoPoint p2 = projection
                .fromPixels(screenWidth / 2, (screenHeight / 2) + (yLen / 2), null);

        // get distance in meters between points
        final double yMeters = ((GeoPoint) p1).distanceToAsDouble(p2);
        // get adjusted distance, shortened to the next lower number starting with 1, 2 or 5
        final double yMetersAdjusted = this.adjustLength ? adjustScaleBarLength(yMeters) : yMeters;
        // get adjusted length in pixels
        final int yBarLengthPixels = (int) (yLen * yMetersAdjusted / yMeters);

        // create text
        final String yMsg = scaleBarLengthText(yMetersAdjusted);
        textPaint.getTextBounds(yMsg, 0, yMsg.length(), sTextBoundsRect);
        final int yTextSpacing = (int) (sTextBoundsRect.height() / 5.0);

        // Apply density-aware text scaling with size option
        Paint scaledTextPaint = new Paint(textPaint);
        float baseSize = textPaint.getTextSize();

        // Apply text size multiplier
        float sizeMultiplier = getTextSizeMultiplier();
        baseSize *= sizeMultiplier;

        if (mDensityScalingEnabled && DisplayDensityManager.isInitialized()) {
            baseSize = DisplayDensityManager.getInstance().getScaledTextSize(baseSize);
        }
        scaledTextPaint.setTextSize(baseSize);
        scaledTextPaint.setColor(mTextColor);

        // Recalculate text bounds with scaled text
        scaledTextPaint.getTextBounds(yMsg, 0, yMsg.length(), sTextBoundsRect);

        float x;
        if (alignRight) {
            x = screenWidth - yTextSpacing * 2;
        } else x = sTextBoundsRect.height() + yTextSpacing;
        float y = yBarLengthPixels / 2 + sTextBoundsRect.width() / 2;
        if (alignBottom) y += screenHeight - yBarLengthPixels;

        canvas.save();
        canvas.rotate(-90, x, y);

        // Draw text background if enabled
        if (mTextBackgroundEnabled) {
            float padding = 6f; // More padding around text
            canvas.drawRoundRect(-sTextBoundsRect.width() / 2 - padding, -sTextBoundsRect.height() / 2 - padding,
                    sTextBoundsRect.width() / 2 + padding, sTextBoundsRect.height() / 2 + padding,
                    mCornerRadius, mCornerRadius, textBackgroundPaint);
        }

        canvas.drawText(yMsg, x, y, scaledTextPaint);
        canvas.restore();
    }

    protected void rebuildBarPath(final Projection projection) {   //** modified to protected
        // We want the scale bar to be as long as the closest round-number miles/kilometers
        // to 1-inch at the latitude at the current center of the screen.

        // calculate dots per centimeter
        int xdpcm = (int) ((float) xdpi / 2.54);
        int ydpcm = (int) ((float) ydpi / 2.54);

        // get length in pixel
        int xLen = (int) (maxLength * xdpcm);
        int yLen = (int) (maxLength * ydpcm);

        // Two points, xLen apart, at scale bar screen location
        IGeoPoint p1 = projection.fromPixels((screenWidth / 2) - (xLen / 2), yOffset, null);
        IGeoPoint p2 = projection.fromPixels((screenWidth / 2) + (xLen / 2), yOffset, null);

        // get distance in meters between points
        final double xMeters = ((GeoPoint) p1).distanceToAsDouble(p2);
        // get adjusted distance, shortened to the next lower number starting with 1, 2 or 5
        final double xMetersAdjusted = this.adjustLength ? adjustScaleBarLength(xMeters) : xMeters;
        // get adjusted length in pixels
        final int xBarLengthPixels = (int) (xLen * xMetersAdjusted / xMeters);

        // Two points, yLen apart, at scale bar screen location
        p1 = projection.fromPixels(screenWidth / 2, (screenHeight / 2) - (yLen / 2), null);
        p2 = projection.fromPixels(screenWidth / 2, (screenHeight / 2) + (yLen / 2), null);

        // get distance in meters between points
        final double yMeters = ((GeoPoint) p1).distanceToAsDouble(p2);
        // get adjusted distance, shortened to the next lower number starting with 1, 2 or 5
        final double yMetersAdjusted = this.adjustLength ? adjustScaleBarLength(yMeters) : yMeters;
        // get adjusted length in pixels
        final int yBarLengthPixels = (int) (yLen * yMetersAdjusted / yMeters);

        // create text
        final String xMsg = scaleBarLengthText(xMetersAdjusted);
        final Rect xTextRect = new Rect();
        textPaint.getTextBounds(xMsg, 0, xMsg.length(), xTextRect);
        int xTextSpacing = (int) (xTextRect.height() / 5.0);

        // create text
        final String yMsg = scaleBarLengthText(yMetersAdjusted);
        final Rect yTextRect = new Rect();
        textPaint.getTextBounds(yMsg, 0, yMsg.length(), yTextRect);
        int yTextSpacing = (int) (yTextRect.height() / 5.0);
        int xTextHeight = xTextRect.height();
        int yTextHeight = yTextRect.height();

        barPath.rewind();

        //** alignBottom ad-ons
        int barOriginX = 0;
        int barOriginY = 0;
        int barToX = xBarLengthPixels;
        int barToY = yBarLengthPixels;
        if (alignBottom) {
            xTextSpacing *= -1;
            xTextHeight *= -1;
            barOriginY = getMapHeight();
            barToY = barOriginY - yBarLengthPixels;
        }

        if (alignRight) {
            yTextSpacing *= -1;
            yTextHeight *= -1;
            barOriginX = getMapWidth();
            barToX = barOriginX - xBarLengthPixels;
        }

        // Calculate enhanced end bar dimensions - taller uprights that extend down
        float endBarHeight = xTextHeight * 2.0f; // Much taller uprights
        float endBarWidth = yTextHeight * 2.0f;

        if (latitudeBar) {
            // draw latitude bar with taller uprights extending down
            barPath.moveTo(barToX, barOriginY + endBarHeight);
            barPath.lineTo(barToX, barOriginY - endBarHeight * 0.3f); // Extend up a bit
            barPath.moveTo(barToX, barOriginY);
            barPath.lineTo(barOriginX, barOriginY);
            barPath.moveTo(barOriginX, barOriginY - endBarHeight * 0.3f); // Extend up a bit
            barPath.lineTo(barOriginX, barOriginY + endBarHeight);

            latitudeBarRect.set(barOriginX, barOriginY, barToX, barOriginY + xTextHeight + xTextSpacing * 2);
        }

        if (longitudeBar) {
            // draw longitude bar with taller uprights extending out
            if (!latitudeBar) {
                barPath.moveTo(barOriginX + endBarWidth, barOriginY);
                barPath.lineTo(barOriginX - endBarWidth * 0.3f, barOriginY); // Extend left a bit
                barPath.moveTo(barOriginX, barOriginY);
            }
            barPath.lineTo(barOriginX, barToY);
            barPath.moveTo(barOriginX - endBarWidth * 0.3f, barToY); // Extend left a bit
            barPath.lineTo(barOriginX + endBarWidth, barToY);

            longitudeBarRect.set(barOriginX, barOriginY, barOriginX + yTextHeight + yTextSpacing * 2, barToY);
        }
    }

    /**
     * Returns a reduced length that starts with 1, 2 or 5 and trailing zeros. If set to nautical or
     * imperial the input will be transformed before and after the reduction so that the result
     * holds in that respective unit.
     *
     * @param length length to round
     * @return reduced, rounded (in m, nm or mi depending on setting) result
     */
    private double adjustScaleBarLength(double length) {
        long pow = 0;
        boolean feet = false;
        if (unitsOfMeasure == UnitsOfMeasure.imperial) {
            if (length >= GeoConstants.METERS_PER_STATUTE_MILE / 5)
                length = length / GeoConstants.METERS_PER_STATUTE_MILE;
            else {
                length = length * GeoConstants.FEET_PER_METER;
                feet = true;
            }
        } else if (unitsOfMeasure == UnitsOfMeasure.nautical) {
            if (length >= GeoConstants.METERS_PER_NAUTICAL_MILE / 5)
                length = length / GeoConstants.METERS_PER_NAUTICAL_MILE;
            else {
                length = length * GeoConstants.FEET_PER_METER;
                feet = true;
            }
        }

        while (length >= 10) {
            pow++;
            length /= 10;
        }
        while (length < 1 && length > 0) {
            pow--;
            length *= 10;
        }

        if (length < 2) {
            length = 1;
        } else if (length < 5) {
            length = 2;
        } else {
            length = 5;
        }
        if (feet)
            length = length / GeoConstants.FEET_PER_METER;
        else if (unitsOfMeasure == UnitsOfMeasure.imperial)
            length = length * GeoConstants.METERS_PER_STATUTE_MILE;
        else if (unitsOfMeasure == UnitsOfMeasure.nautical)
            length = length * GeoConstants.METERS_PER_NAUTICAL_MILE;
        length *= Math.pow(10, pow);
        return length;
    }

    protected String scaleBarLengthText(final double meters) {
        switch (unitsOfMeasure) {
            default:
            case metric:
                if (meters >= 1000 * 5) {
                    return getConvertedScaleString(meters, UnitOfMeasure.kilometer, "%.0f");
                } else if (meters >= 1000 / 5) {
                    return getConvertedScaleString(meters, UnitOfMeasure.kilometer, "%.1f");
                } else if (meters >= 20) {
                    return getConvertedScaleString(meters, UnitOfMeasure.meter, "%.0f");
                } else {
                    return getConvertedScaleString(meters, UnitOfMeasure.meter, "%.2f");
                }
            case imperial:
                if (meters >= METERS_PER_STATUTE_MILE * 5) {
                    return getConvertedScaleString(meters, UnitOfMeasure.statuteMile, "%.0f");

                } else if (meters >= METERS_PER_STATUTE_MILE / 5) {
                    return getConvertedScaleString(meters, UnitOfMeasure.statuteMile, "%.1f");
                } else {
                    return getConvertedScaleString(meters, UnitOfMeasure.foot, "%.0f");
                }
            case nautical:
                if (meters >= METERS_PER_NAUTICAL_MILE * 5) {
                    return getConvertedScaleString(meters, UnitOfMeasure.nauticalMile, "%.0f");
                } else if (meters >= METERS_PER_NAUTICAL_MILE / 5) {
                    return getConvertedScaleString(meters, UnitOfMeasure.nauticalMile, "%.1f");
                } else {
                    return getConvertedScaleString(meters, UnitOfMeasure.foot, "%.0f");
                }
        }
    }

    @Override
    public void onDetach(MapView mapView) {
        this.context = null;
        this.mMapView = null;
        barPaint = null;
        barHaloPaint = null;
        bgPaint = null;
        textPaint = null;
        textBackgroundPaint = null;
    }

    /**
     * @since 6.0.0
     */
    private String getConvertedScaleString(final double pMeters,
                                           final GeoConstants.UnitOfMeasure pConversion,
                                           final String pFormat) {
        return getScaleString(
                context,
                String.format(Locale.getDefault(), pFormat,
                        pMeters / pConversion.getConversionFactorToMeters()),
                pConversion);
    }

    /**
     * @since 6.1.1
     */
    public static String getScaleString(final Context pContext,
                                        final String pValue,
                                        final GeoConstants.UnitOfMeasure pUnitOfMeasure) {
        return pContext.getString(
                R.string.format_distance_value_unit,
                pValue, pContext.getString(pUnitOfMeasure.getStringResId()));
    }

    /**
     * @since 6.1.0
     */
    private int getMapWidth() {
        return mMapView != null ? mMapView.getWidth() : mMapWidth;
    }

    /**
     * Set text size option
     */
    public void setTextSizeOption(TextSize textSize) {
        mTextSizeOption = textSize;
        lastZoomLevel = -1; // Force redraw
    }

    /**
     * Enable or disable automatic density scaling
     */
    public void setDensityScalingEnabled(boolean enabled) {
        mDensityScalingEnabled = enabled;
        lastZoomLevel = -1; // Force redraw
    }

    /**
     * Check if density scaling is enabled
     */
    public boolean isDensityScalingEnabled() {
        return mDensityScalingEnabled;
    }

    /**
     * Set corner radius for text background
     */
    public void setCornerRadius(float radius) {
        mCornerRadius = radius;
        lastZoomLevel = -1; // Force redraw
    }

    /**
     * Enable or disable text background boxes
     */
    public void setTextBackgroundEnabled(boolean enabled) {
        mTextBackgroundEnabled = enabled;
        lastZoomLevel = -1; // Force redraw
    }

    /**
     * Sets the color of the scale bar text. Default is white.
     *
     * @param color the text color (ARGB)
     */
    public void setTextColor(int color) {
        mTextColor = color;
        lastZoomLevel = -1; // Force redraw
    }

    /**
     * @return the current scale bar text color
     */
    public int getTextColor() {
        return mTextColor;
    }

    /**
     * Sets the color of the rounded box drawn behind the scale bar text.
     * The current alpha (opacity) of the background paint is preserved.
     *
     * @param color the background box color (RGB; alpha is kept from the existing paint)
     */
    public void setTextBackgroundColor(int color) {
        final int alpha = textBackgroundPaint.getAlpha();
        textBackgroundPaint.setColor(color);
        textBackgroundPaint.setAlpha(alpha);
        lastZoomLevel = -1; // Force redraw
    }

    /**
     * Sets the paint used to draw the rounded box behind the scale bar text.
     *
     * @param pTextBackgroundPaint the text background paint (must not be null)
     */
    public void setTextBackgroundPaint(final Paint pTextBackgroundPaint) {
        if (pTextBackgroundPaint == null) {
            throw new IllegalArgumentException("pTextBackgroundPaint argument cannot be null");
        }
        textBackgroundPaint = pTextBackgroundPaint;
        lastZoomLevel = -1; // Force redraw
    }

    /**
     * @return the paint used to draw the rounded box behind the scale bar text
     */
    public Paint getTextBackgroundPaint() {
        return textBackgroundPaint;
    }

    /**
     * Enables or disables the contrast halo drawn underneath the bar lines.
     * The halo keeps the bar legible over light and dark map backgrounds.
     * Enabled by default.
     *
     * @param enabled true to draw the halo, false to draw the bare bar only
     */
    public void setBarHaloEnabled(boolean enabled) {
        mBarHaloEnabled = enabled;
        lastZoomLevel = -1; // Force redraw
    }

    /**
     * @return whether the bar contrast halo is enabled
     */
    public boolean isBarHaloEnabled() {
        return mBarHaloEnabled;
    }

    /**
     * Sets the color (ARGB) of the contrast halo drawn underneath the bar lines.
     * Default is opaque white, which suits the default black bar. Use a dark
     * color if you switch the bar itself to a light color.
     *
     * @param color the halo color (ARGB)
     */
    public void setBarHaloColor(int color) {
        mBarHaloColor = color;
        lastZoomLevel = -1; // Force redraw
    }

    /**
     * @return the current bar halo color (ARGB)
     */
    public int getBarHaloColor() {
        return mBarHaloColor;
    }

    /**
     * Sets the total extra stroke width of the halo relative to the bar line,
     * in pixels (i.e. the halo extends this many pixels / 2 beyond each side of
     * the bar). Default is ~4dp. Pass a density-scaled value if you need exact
     * control.
     *
     * @param extraWidthPx total extra halo width in pixels (clamped to >= 0)
     */
    public void setBarHaloWidth(float extraWidthPx) {
        mBarHaloExtraWidth = Math.max(0f, extraWidthPx);
        lastZoomLevel = -1; // Force redraw
    }

    private float getTextSizeMultiplier() {
        switch (mTextSizeOption) {
            case SMALLEST: return 0.6f;
            case SMALLER: return 0.8f;
            case NORMAL: return 1.0f;
            case LARGER: return 1.2f;
            case LARGEST: return 1.5f;
            default: return 1.0f;
        }
    }


    /**
     * @since 6.1.0
     */
    private int getMapHeight() {
        return mMapView != null ? mMapView.getHeight() : mMapHeight;
    }
}
