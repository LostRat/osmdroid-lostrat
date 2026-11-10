package org.osmdroid.views.overlay.infowindow;

import android.content.Context;
import androidx.core.text.HtmlCompat;
import android.text.Spanned;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

import org.osmdroid.api.IMapView;
import org.osmdroid.library.R;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.OverlayWithIW;



/**
 * {@link org.osmdroid.views.overlay.infowindow.BasicInfoWindow} is the default
 * implementation of {@link org.osmdroid.views.overlay.infowindow.InfoWindow} for an
 * {@link org.osmdroid.views.overlay.OverlayWithIW}.
 * <p>
 * It handles a title, a description and a sub-description.
 * Clicking on the bubble will close it.
 *
 * <img alt="Class diagram around Marker class" width="686" height="413" src='./doc-files/marker-infowindow-classes.png' />
 *
 * @author M.Kergall
 * @see Marker
 */
public class BasicInfoWindow extends InfoWindow {

    /**
     * resource id value meaning "undefined resource id"
     */
    public static final int UNDEFINED_RES_ID = 0;

    static int mTitleId = UNDEFINED_RES_ID,
            mDescriptionId = UNDEFINED_RES_ID,
            mSubDescriptionId = UNDEFINED_RES_ID,
            mImageId = UNDEFINED_RES_ID; //resource ids

    private static void setResIds(Context context) {
        String packageName = context.getPackageName(); //get application package name
        mTitleId = context.getResources().getIdentifier("id/bubble_title", null, packageName);
        mDescriptionId = context.getResources().getIdentifier("id/bubble_description", null, packageName);
        mSubDescriptionId = context.getResources().getIdentifier("id/bubble_subdescription", null, packageName);
        mImageId = context.getResources().getIdentifier("id/bubble_image", null, packageName);
        if (mTitleId == UNDEFINED_RES_ID || mDescriptionId == UNDEFINED_RES_ID
                || mSubDescriptionId == UNDEFINED_RES_ID || mImageId == UNDEFINED_RES_ID) {
            Log.e(IMapView.LOGTAG, "BasicInfoWindow: unable to get res ids in " + packageName);
        }
    }

    private static float sMaxFontScale = 1.5f; // system font scale above this does not grow the bubble text

    /**
     * Cap the system font scale the stock bubble (bonuspack_bubble) follows (default 1.5).
     * Its title, description and sub-description all grow with the font scale up to the cap.
     * 0 or less keeps the layout's own sizes: title uncapped, descriptions fixed.
     * Applies to bubbles created afterwards.
     */
    public static void setMaxFontScale(float maxFontScale) {
        sMaxFontScale = maxFontScale;
    }

    private void setTextSizeDp(int id, float dp) {
        final View v = mView.findViewById(id);
        if (v instanceof TextView)
            ((TextView) v).setTextSize(TypedValue.COMPLEX_UNIT_DIP, dp);
    }

    public BasicInfoWindow(int layoutResId, MapView mapView) {
        super(layoutResId, mapView);

        if (mTitleId == UNDEFINED_RES_ID)
            setResIds(mapView.getContext());

        if (layoutResId == R.layout.bonuspack_bubble && sMaxFontScale > 0) {
            final float scale = Math.min(
                    mapView.getResources().getConfiguration().fontScale, sMaxFontScale);
            setTextSizeDp(mTitleId, 14 * scale);
            setTextSizeDp(mDescriptionId, 12 * scale);
            setTextSizeDp(mSubDescriptionId, 10 * scale);
        }

        //default behavior: close it when clicking on the bubble:
        mView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                if (e.getAction() == MotionEvent.ACTION_UP)
                    close();
                return true;
            }
        });
    }

    @Override
    public void onOpen(Object item) {
        OverlayWithIW overlay = (OverlayWithIW) item;
        String title = overlay.getTitle();
        if (title == null)
            title = "";
        if (mView == null) {
            Log.w(IMapView.LOGTAG, "Error trapped, BasicInfoWindow.open, mView is null!");
            return;
        }
        TextView temp = ((TextView) mView.findViewById(mTitleId /*R.id.title*/));

        if (temp != null) temp.setText(title);

        String snippet = overlay.getSnippet();
        if (snippet == null)
            snippet = "";
        Spanned snippetHtml = HtmlCompat.fromHtml(snippet, HtmlCompat.FROM_HTML_MODE_LEGACY);
        ((TextView) mView.findViewById(mDescriptionId /*R.id.description*/)).setText(snippetHtml);

        //handle sub-description, hidding or showing the text view:
        TextView subDescText = (TextView) mView.findViewById(mSubDescriptionId);
        String subDesc = overlay.getSubDescription();
        if (subDesc != null && !("".equals(subDesc))) {
            subDescText.setText(HtmlCompat.fromHtml(subDesc, HtmlCompat.FROM_HTML_MODE_LEGACY));
            subDescText.setVisibility(View.VISIBLE);
        } else {
            subDescText.setVisibility(View.GONE);
        }

    }

    @Override
    public void onClose() {
        //by default, do nothing
    }

}
