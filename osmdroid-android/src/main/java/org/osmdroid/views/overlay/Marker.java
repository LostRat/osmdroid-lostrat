package org.osmdroid.views.overlay;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.MotionEvent;

import org.osmdroid.tileprovider.BitmapPool;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.Distance;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.RectL;
import org.osmdroid.views.MapView;
import org.osmdroid.views.MapViewRepository;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow;

/**
 * A marker is an icon placed at a particular point on the map's surface that can have a popup-{@link org.osmdroid.views.overlay.infowindow.InfoWindow} (a bubble)
 * Mimics the Marker class from Google Maps Android API v2 as much as possible. Main differences:<br>
 * <p>
 * - Doesn't support Z-Index: as other osmdroid overlays, Marker is drawn in the order of appearance. <br>
 * - The icon can be any standard Android Drawable, instead of the BitmapDescriptor introduced in Google Maps API v2. <br>
 * - The icon can be changed at any time. <br>
 * - The InfoWindow hosts a standard Android View. It can handle Android widgets like buttons and so on. <br>
 * - Supports a "sub-description", to be displayed in the InfoWindow, under the snippet, in a smaller text font. <br>
 * - Supports an image, to be displayed in the InfoWindow. <br>
 * - Supports "panning to view" on/off option (when touching a marker, center the map on marker position). <br>
 * - Opening a Marker InfoWindow automatically close others only if it's the same InfoWindow shared between Markers. <br>
 * - Events listeners are set per marker, not per map. <br>
 *
 * <img alt="Class diagram around Marker class" width="686" height="413" src='src='./doc-files/marker-infowindow-classes.png' />
 *
 * @author M.Kergall
 * @see MarkerInfoWindow
 * see also <a href="http://developer.android.com/reference/com/google/android/gms/maps/model/Marker.html">Google Maps Marker</a>
 */
public class Marker extends OverlayWithIW {

    /* attributes for text labels, used for osmdroid gridlines */
    protected int mTextLabelBackgroundColor = Color.WHITE;
    protected int mTextLabelForegroundColor = Color.BLACK;
    protected int mTextLabelFontSize = 24;

    /*attributes for standard features:*/
    protected Drawable mIcon;
    protected GeoPoint mPosition;
    protected float mBearing;
    protected float mAnchorU, mAnchorV;
    protected float mIWAnchorU, mIWAnchorV;
    protected float mAlpha;
    protected boolean mDraggable, mIsDragged;
    protected boolean mFlat;
    protected OnMarkerClickListener mOnMarkerClickListener;
    protected OnMarkerDragListener mOnMarkerDragListener;

    /*attributes for non-standard features:*/
    protected Drawable mImage;
    protected boolean mPanToView;
    protected float mDragOffsetY;

    /*internals*/
    protected Point mPositionPixels;
    protected Resources mResources;

    /**
     * @since 6.0.3
     */
    private MapViewRepository mMapViewRepository;

    /**
     * Touch buffer in pixels for hit detection. Default is 10 pixels.
     * Set to 0 to use exact bounds checking (legacy behavior).
     * @since 6.3.0
     */
    protected float mTouchBuffer = 10.0f;

    /**
     * Cached density multiplier for touch buffer scaling
     * @since 6.3.0
     */
    private float mDensity = 1.0f;

    /**
     * Global default touch buffer for new markers
     * @since 6.3.0
     */
    private static float sDefaultTouchBuffer = 10.0f;

    /**
     * Usual values in the (U,V) coordinates system of the icon image
     */
    public static final float ANCHOR_CENTER = 0.5f, ANCHOR_LEFT = 0.0f, ANCHOR_TOP = 0.0f, ANCHOR_RIGHT = 1.0f, ANCHOR_BOTTOM = 1.0f;

    /**
     * @since 6.0.3
     */
    private boolean mDisplayed;
    private final Rect mRect = new Rect();
    private final Rect mOrientedMarkerRect = new Rect();
    private Paint mPaint;

    public Marker(MapView mapView) {
        this(mapView, (mapView.getContext()));
    }

    public Marker(MapView mapView, final Context resourceProxy) {
        super();
        mMapViewRepository = mapView.getRepository();
        mResources = mapView.getContext().getResources();
        mBearing = 0.0f;
        mAlpha = 1.0f; //opaque
        mPosition = new GeoPoint(0.0, 0.0);
        mAnchorU = ANCHOR_CENTER;
        mAnchorV = ANCHOR_CENTER;
        mIWAnchorU = ANCHOR_CENTER;
        mIWAnchorV = ANCHOR_TOP;
        mDraggable = false;
        mIsDragged = false;
        mPositionPixels = new Point();
        mPanToView = true;
        mDragOffsetY = 0.0f;
        mFlat = false; //billboard
        mOnMarkerClickListener = null;
        mOnMarkerDragListener = null;
        // Initialize density for touch buffer scaling
        mDensity = mapView.getContext().getResources().getDisplayMetrics().density;
        mTouchBuffer = sDefaultTouchBuffer;
        setDefaultIcon();
        setInfoWindow(mMapViewRepository.getDefaultMarkerInfoWindow());
    }

    /**
     * Sets the icon for the marker. Can be changed at any time.
     * This is used on the map view.
     * The anchor will be left unchanged; you may need to call {@link #setAnchor(float, float)}
     * Two exceptions:
     * - for text icons, the anchor is set to (center, center)
     * - for the default icon, the anchor is set to the corresponding position (the tip of the teardrop)
     * Related methods: {@link #setTextIcon(String)}, {@link #setDefaultIcon()} and {@link #setAnchor(float, float)}
     *
     * @param icon if null, the default osmdroid marker is used.
     */
    public void setIcon(final Drawable icon) {
        if (icon != null) {
            mIcon = icon;
        } else {
            setDefaultIcon();
        }
    }

    /**
     * @since 6.0.3
     */
    public void setDefaultIcon() {
        mIcon = mMapViewRepository.getDefaultMarkerIcon();
        setAnchor(ANCHOR_CENTER, ANCHOR_BOTTOM);
    }

    /**
     * @since 6.0.3
     */
    public void setTextIcon(final String pText) {
        final Paint background = new Paint();
        background.setColor(mTextLabelBackgroundColor);
        final Paint p = new Paint();
        p.setTextSize(mTextLabelFontSize);
        p.setColor(mTextLabelForegroundColor);
        p.setAntiAlias(true);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.LEFT);
        final int width = (int) (p.measureText(pText) + 0.5f);
        final float baseline = (int) (-p.ascent() + 0.5f);
        final int height = (int) (baseline + p.descent() + 0.5f);
        final Bitmap image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        final Canvas c = new Canvas(image);
        c.drawPaint(background);
        c.drawText(pText, 0, baseline, p);
        mIcon = new BitmapDrawable(mResources, image);
        setAnchor(ANCHOR_CENTER, ANCHOR_CENTER);
    }

    /**
     * @return
     * @since 6.0.0?
     */
    public Drawable getIcon() {
        return mIcon;
    }

    public GeoPoint getPosition() {
        return mPosition;
    }

    /**
     * sets the location on the planet where the icon is rendered
     *
     * @param position
     */
    public void setPosition(GeoPoint position) {
        mPosition = position.clone();
        if (isInfoWindowShown()) {
            closeInfoWindow();
            showInfoWindow();
        }
        mBounds = new BoundingBox(position.getLatitude(), position.getLongitude(), position.getLatitude(), position.getLongitude());
    }

    public float getRotation() {
        return mBearing;
    }

    /**
     * rotates the icon in relation to the map
     *
     * @param rotation
     */
    public void setRotation(float rotation) {
        mBearing = rotation;
    }

    /**
     * @param anchorU WIDTH 0.0-1.0 percentage of the icon that offsets the logical center from the actual pixel center point
     * @param anchorV HEIGHT 0.0-1.0 percentage of the icon that offsets the logical center from the actual pixel center point
     */
    public void setAnchor(float anchorU, float anchorV) {
        mAnchorU = anchorU;
        mAnchorV = anchorV;
    }

    public void setInfoWindowAnchor(float anchorU, float anchorV) {
        mIWAnchorU = anchorU;
        mIWAnchorV = anchorV;
    }

    public void setAlpha(float alpha) {
        mAlpha = alpha;
    }

    public float getAlpha() {
        return mAlpha;
    }

    public void setDraggable(boolean draggable) {
        mDraggable = draggable;
    }

    public boolean isDraggable() {
        return mDraggable;
    }

    public void setFlat(boolean flat) {
        mFlat = flat;
    }

    public boolean isFlat() {
        return mFlat;
    }

    /**
     * Removes this Marker from the MapView.
     * Note that this method will operate only if the Marker is in the MapView overlays
     * (it should not be included in a container like a FolderOverlay).
     *
     * @param mapView
     */
    public void remove(MapView mapView) {
        mapView.getOverlays().remove(this);
    }

    public void setOnMarkerClickListener(OnMarkerClickListener listener) {
        mOnMarkerClickListener = listener;
    }

    public void setOnMarkerDragListener(OnMarkerDragListener listener) {
        mOnMarkerDragListener = listener;
    }

    /**
     * set an image to be shown in the InfoWindow  - this is not the marker icon
     */
    public void setImage(Drawable image) {
        mImage = image;
    }

    /**
     * get the image to be shown in the InfoWindow - this is not the marker icon
     */
    public Drawable getImage() {
        return mImage;
    }

    /**
     * set the offset in millimeters that the marker is moved up while dragging
     */
    public void setDragOffset(float mmUp) {
        mDragOffsetY = mmUp;
    }

    /**
     * get the offset in millimeters that the marker is moved up while dragging
     */
    public float getDragOffset() {
        return mDragOffsetY;
    }

    /**
     * Set the InfoWindow to be used.
     * Default is a MarkerInfoWindow, with the layout named "bonuspack_bubble".
     * You can use this method either to use your own layout, or to use your own sub-class of InfoWindow.
     * Note that this InfoWindow will receive the Marker object as an input, so it MUST be able to handle Marker attributes.
     * If you don't want any InfoWindow to open, you can set it to null.
     */
    public void setInfoWindow(MarkerInfoWindow infoWindow) {
        mInfoWindow = infoWindow;
    }

    /**
     * If set to true, when clicking the marker, the map will be centered on the marker position.
     * Default is true.
     */
    public void setPanToView(boolean panToView) {
        mPanToView = panToView;
    }

    /**
     * shows the info window, if it's open, this will close and reopen it
     */
    public void showInfoWindow() {
        if (mInfoWindow == null)
            return;
        final int markerWidth = mIcon.getIntrinsicWidth();
        final int markerHeight = mIcon.getIntrinsicHeight();
        final int offsetX = (int) (markerWidth * (mIWAnchorU - mAnchorU));
        final int offsetY = (int) (markerHeight * (mIWAnchorV - mAnchorV));
        if (mBearing == 0) {
            mInfoWindow.open(this, mPosition, offsetX, offsetY);
            return;
        }
        final int centerX = 0;
        final int centerY = 0;
        final double radians = -mBearing * Math.PI / 180.;
        final double cos = Math.cos(radians);
        final double sin = Math.sin(radians);
        final int rotatedX = (int) RectL.getRotatedX(offsetX, offsetY, centerX, centerY, cos, sin);
        final int rotatedY = (int) RectL.getRotatedY(offsetX, offsetY, centerX, centerY, cos, sin);
        mInfoWindow.open(this, mPosition, rotatedX, rotatedY);
    }

    public boolean isInfoWindowShown() {
        if (mInfoWindow instanceof MarkerInfoWindow) {
            MarkerInfoWindow iw = (MarkerInfoWindow) mInfoWindow;
            return (iw != null) && iw.isOpen() && (iw.getMarkerReference() == this);
        } else
            return super.isInfoWindowOpen();
    }

    @Override
    public void draw(Canvas canvas, Projection pj) {
        if (mIcon == null)
            return;
        if (!isEnabled())
            return;

        pj.toPixels(mPosition, mPositionPixels);

        float rotationOnScreen = (mFlat ? -mBearing : -pj.getOrientation() - mBearing);
        drawAt(canvas, mPositionPixels.x, mPositionPixels.y, rotationOnScreen);
        if (isInfoWindowShown()) {
            //showInfoWindow();
            mInfoWindow.draw();
        }
    }

    /**
     * Null out the static references when the MapView is detached to prevent memory leaks.
     */
    @Override
    public void onDetach(MapView mapView) {
        BitmapPool.getInstance().asyncRecycle(mIcon);
        mIcon = null;
        BitmapPool.getInstance().asyncRecycle(mImage);
        //cleanDefaults();
        this.mOnMarkerClickListener = null;
        this.mOnMarkerDragListener = null;
        this.mResources = null;
        setRelatedObject(null);
        if (isInfoWindowShown())
            closeInfoWindow();
        //	//if we're using the shared info window, this will cause all instances to close

        mMapViewRepository = null;
        setInfoWindow(null);
        onDestroy();


        super.onDetach(mapView);
    }


    /**
     * Prevent memory leaks and call this when you're done with the map
     * reference https://github.com/MKergall/osmbonuspack/pull/210
     */
    @Deprecated
    public static void cleanDefaults() {
    }

    public boolean hitTest(final MotionEvent event, final MapView mapView) {
        // Keep existing null and display checks at the beginning
        if (mIcon == null || !mDisplayed) {
            return false;
        }
        
        final int eventX = (int) event.getX();
        final int eventY = (int) event.getY();
        
        // Fast path: exact bounds check (return true immediately if within exact bounds)
        if (mOrientedMarkerRect.contains(eventX, eventY)) {
            return true;
        }
        
        // Early return if mTouchBuffer is zero or negative
        if (mTouchBuffer <= 0) {
            return false;
        }
        
        // Calculate effective buffer with density scaling
        final float effectiveBuffer = mTouchBuffer * mDensity;
        
        // Early exit check using expanded bounding box
        final Rect expandedRect = new Rect(mOrientedMarkerRect);
        expandedRect.inset(-(int) effectiveBuffer, -(int) effectiveBuffer);
        if (!expandedRect.contains(eventX, eventY)) {
            return false;
        }
        
        // Implement center-based distance calculation using Distance.getSquaredDistanceToPoint()
        final int centerX = mOrientedMarkerRect.centerX();
        final int centerY = mOrientedMarkerRect.centerY();
        final double squaredDistance = Distance.getSquaredDistanceToPoint(
            eventX, eventY, centerX, centerY
        );
        
        // Calculate maximum allowed squared distance (half diagonal + buffer)
        final double halfWidth = mOrientedMarkerRect.width() / 2.0;
        final double halfHeight = mOrientedMarkerRect.height() / 2.0;
        final double maxDistance = Math.sqrt(halfWidth * halfWidth + halfHeight * halfHeight) + effectiveBuffer;
        final double squaredMaxDistance = maxDistance * maxDistance;
        
        // Return comparison result of squared distances
        return squaredDistance <= squaredMaxDistance;
    }

    @Override
    public boolean onSingleTapConfirmed(final MotionEvent event, final MapView mapView) {
        boolean touched = hitTest(event, mapView);
        if (touched) {
            if (mOnMarkerClickListener == null) {
                return onMarkerClickDefault(this, mapView);
            } else {
                return mOnMarkerClickListener.onMarkerClick(this, mapView);
            }
        } else
            return touched;
    }

    public void moveToEventPosition(final MotionEvent event, final MapView mapView) {
        float offsetY = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, mDragOffsetY, mapView.getContext().getResources().getDisplayMetrics());
        final Projection pj = mapView.getProjection();
        setPosition((GeoPoint) pj.fromPixels((int) event.getX(), (int) (event.getY() - offsetY)));
        mapView.invalidate();
    }

    @Override
    public boolean onLongPress(final MotionEvent event, final MapView mapView) {
        boolean touched = hitTest(event, mapView);
        if (touched) {
            if (mDraggable) {
                //starts dragging mode:
                mIsDragged = true;
                closeInfoWindow();
                if (mOnMarkerDragListener != null)
                    mOnMarkerDragListener.onMarkerDragStart(this);
                moveToEventPosition(event, mapView);
            }
        }
        return touched;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event, final MapView mapView) {
        if (mDraggable && mIsDragged) {
            if (event.getAction() == MotionEvent.ACTION_UP) {
                mIsDragged = false;
                if (mOnMarkerDragListener != null)
                    mOnMarkerDragListener.onMarkerDragEnd(this);
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                moveToEventPosition(event, mapView);
                if (mOnMarkerDragListener != null)
                    mOnMarkerDragListener.onMarkerDrag(this);
                return true;
            } else
                return false;
        } else
            return false;
    }

    public void setVisible(boolean visible) {
        if (visible)
            setAlpha(1f);
        else setAlpha(0f);
    }

    //-- Marker events listener interfaces ------------------------------------

    public interface OnMarkerClickListener {
        abstract boolean onMarkerClick(Marker marker, MapView mapView);
    }

    public interface OnMarkerDragListener {
        abstract void onMarkerDrag(Marker marker);

        abstract void onMarkerDragEnd(Marker marker);

        abstract void onMarkerDragStart(Marker marker);
    }

    /**
     * default behaviour when no click listener is set
     */
    protected boolean onMarkerClickDefault(Marker marker, MapView mapView) {
        marker.showInfoWindow();
        if (marker.mPanToView)
            mapView.getController().animateTo(marker.getPosition());
        return true;
    }

    /**
     * Set the touch buffer for hit detection in pixels.
     * The buffer expands the tappable area around the marker icon, making it easier to tap.
     * This is particularly useful on mobile devices where precise tapping can be difficult.
     * 
     * <p><b>Overlapping Markers:</b> When multiple markers have overlapping touch buffers,
     * the tap event is handled by the first marker that responds, based on this priority order:</p>
     * <ol>
     *   <li><b>Layer priority:</b> Markers in higher z-index layers are checked first
     *       (e.g., INTERACTIVE_CONTENT layer has priority over DECORATION layer)</li>
     *   <li><b>Add order:</b> Within the same layer, the last marker added to the map is checked first</li>
     *   <li><b>Distance:</b> If a marker's buffer doesn't reach the tap point, the next marker is checked</li>
     * </ol>
     * 
     * <p>This means the marker closest to the tap point (within its buffer) will typically handle
     * the tap, unless overridden by layer priority or add order. To control priority explicitly,
     * use {@link DefaultOverlayManager#assignOverlayToLayer(Overlay, DefaultOverlayManager.OverlayLayer)}
     * or adjust the buffer sizes.</p>
     * 
     * @param bufferPixels Buffer size in pixels. Use 0 for exact bounds checking (legacy behavior).
     *                     Negative values will be clamped to 0. Default is 10 pixels.
     * @since 6.3.0
     * @see #getTouchBuffer()
     * @see #setDefaultTouchBuffer(float)
     */
    public void setTouchBuffer(float bufferPixels) {
        mTouchBuffer = Math.max(0, bufferPixels);
    }

    /**
     * Get the current touch buffer size in pixels.
     * The touch buffer expands the tappable area around the marker icon.
     * 
     * @return Touch buffer size in pixels
     * @since 6.3.0
     */
    public float getTouchBuffer() {
        return mTouchBuffer;
    }

    /**
     * Set a global default touch buffer for all new markers.
     * This affects markers created after this method is called.
     * Individual markers can override this default using {@link #setTouchBuffer(float)}.
     * 
     * @param bufferPixels Default buffer size in pixels. Negative values will be clamped to 0.
     * @since 6.3.0
     */
    public static void setDefaultTouchBuffer(float bufferPixels) {
        sDefaultTouchBuffer = Math.max(0, bufferPixels);
    }

    /**
     * Get the global default touch buffer size for new markers.
     * 
     * @return Default touch buffer size in pixels
     * @since 6.3.0
     */
    public static float getDefaultTouchBuffer() {
        return sDefaultTouchBuffer;
    }

    /**
     * used for when the icon is explicitly set to null and the title is not, this will
     * style the rendered text label
     *
     * @return
     */
    public int getTextLabelBackgroundColor() {
        return mTextLabelBackgroundColor;
    }

    /**
     * used for when the icon is explicitly set to null and the title is not, this will
     * style the rendered text label
     *
     * @return
     */
    public void setTextLabelBackgroundColor(int mTextLabelBackgroundColor) {
        this.mTextLabelBackgroundColor = mTextLabelBackgroundColor;
    }

    /**
     * used for when the icon is explicitly set to null and the title is not, this will
     * style the rendered text label
     *
     * @return
     */
    public int getTextLabelForegroundColor() {
        return mTextLabelForegroundColor;
    }

    /**
     * used for when the icon is explicitly set to null and the title is not, this will
     * style the rendered text label
     *
     * @return
     */
    public void setTextLabelForegroundColor(int mTextLabelForegroundColor) {
        this.mTextLabelForegroundColor = mTextLabelForegroundColor;
    }

    /**
     * used for when the icon is explicitly set to null and the title is not, this will
     * style the rendered text label
     *
     * @return
     */
    public int getTextLabelFontSize() {
        return mTextLabelFontSize;
    }

    /**
     * used for when the icon is explicitly set to null and the title is not, this will
     * style the rendered text label
     *
     * @return
     */
    public void setTextLabelFontSize(int mTextLabelFontSize) {
        this.mTextLabelFontSize = mTextLabelFontSize;
    }

    /**
     * @since 6.0.3
     */
    public boolean isDisplayed() {
        return mDisplayed;
    }

    /**
     * Optimized drawing
     *
     * @since 6.0.3
     */
    protected void drawAt(final Canvas pCanvas, final int pX, final int pY, final float pOrientation) {
        final int markerWidth = mIcon.getIntrinsicWidth();
        final int markerHeight = mIcon.getIntrinsicHeight();
        final int offsetX = pX - Math.round(markerWidth * mAnchorU);
        final int offsetY = pY - Math.round(markerHeight * mAnchorV);
        mRect.set(offsetX, offsetY, offsetX + markerWidth, offsetY + markerHeight);
        RectL.getBounds(mRect, pX, pY, pOrientation, mOrientedMarkerRect);
        mDisplayed = Rect.intersects(mOrientedMarkerRect, pCanvas.getClipBounds());
        if (!mDisplayed) { // optimization 1: (much faster, depending on the proportions) don't try to display if the Marker is not visible
            return;
        }
        if (mAlpha == 0) {
            return;
        }
        if (pOrientation != 0) { // optimization 2: don't manipulate the Canvas if not needed (about 25% faster) - step 1/2
            pCanvas.save();
            pCanvas.rotate(pOrientation, pX, pY);
        }
        /*
        if (mIcon instanceof BitmapDrawable) { 
            // optimization 3: (about 15% faster) - Unfortunate optimization with displayed size side effects: introduces issue #1738 
            final Paint paint;
            if (mAlpha == 1) {
                paint = null;
            } else {
                if (mPaint == null) {
                    mPaint = new Paint();
                }
                mPaint.setAlpha((int) (mAlpha * 255));
                paint = mPaint;
            }
            pCanvas.drawBitmap(((BitmapDrawable) mIcon).getBitmap(), offsetX, offsetY, paint);
        } else {
        */
            mIcon.setAlpha((int) (mAlpha * 255));
            mIcon.setBounds(mRect);
            mIcon.draw(pCanvas);
        //}
        if (pOrientation != 0) { // optimization 2: step 2/2
            pCanvas.restore();
        }
    }
}
