package org.osmdroid.tileprovider;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.Log;

import org.osmdroid.api.IMapView;
import org.osmdroid.tileprovider.modules.ConfigurablePriorityThreadFactory;

import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BitmapPool {

    private final LinkedList<Bitmap> mPool = new LinkedList<>();
    private final ExecutorService mExecutor = Executors.newFixedThreadPool(1,
            new ConfigurablePriorityThreadFactory(Thread.MIN_PRIORITY, getClass().getName()));

    //singleton: begin
    private BitmapPool() {
    }

    private static final BitmapPool sInstance = new BitmapPool();

    public static BitmapPool getInstance() {
        return sInstance;
    }
    //singleton: end

    public void returnDrawableToPool(ReusableBitmapDrawable drawable) {
        Bitmap b = drawable.tryRecycle();
        if (b != null && !b.isRecycled() && b.isMutable() && b.getConfig() != null) {
            synchronized (mPool) {
                mPool.addLast(b);
            }
        } else if (b != null) {
            Log.d(IMapView.LOGTAG, "Rejected bitmap from being added to BitmapPool.");
        }
    }

    /**
     * @deprecated As of 6.0.2, use
     * {@link #applyReusableOptions(BitmapFactory.Options, int, int)} instead.
     */
    @Deprecated
    public void applyReusableOptions(final BitmapFactory.Options aBitmapOptions) {
        // We can not guarantee a bitmap can be reused without knowing the dimensions, so always
        // return null in inBitmap
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            aBitmapOptions.inBitmap = null;
            aBitmapOptions.inSampleSize = 1;
            aBitmapOptions.inMutable = true;
        }
    }

    public void applyReusableOptions(final BitmapFactory.Options aBitmapOptions, final int width, final int height) {
        // API 23+ - always available, no need for version checks
        // First try to get exact size match
        aBitmapOptions.inBitmap = obtainSizedBitmapFromPool(width, height);
        
        // API 19+ - can reuse bitmaps with different dimensions if they have enough allocated bytes
        if (aBitmapOptions.inBitmap == null) {
            aBitmapOptions.inBitmap = obtainLargerBitmapFromPool(width, height);
        }
        
        aBitmapOptions.inSampleSize = 1;
        aBitmapOptions.inMutable = true;
    }

    /**
     * @deprecated As of 6.0.2, use
     * {@link #obtainSizedBitmapFromPool(int, int)} instead.
     */
    @Deprecated
    public Bitmap obtainBitmapFromPool() {
        synchronized (mPool) {
            if (mPool.isEmpty()) {
                return null;
            } else {
                final Bitmap bitmap = mPool.removeFirst();
                if (bitmap.isRecycled()) {
                    return obtainBitmapFromPool(); // recurse
                } else {
                    return bitmap;
                }
            }
        }
    }

    public Bitmap obtainSizedBitmapFromPool(final int aWidth, final int aHeight) {
        synchronized (mPool) {
            if (mPool.isEmpty()) {
                return null;
            } else {
                for (final Bitmap bitmap : mPool) {
                    if (bitmap.isRecycled()) {
                        mPool.remove(bitmap);
                        return obtainSizedBitmapFromPool(aWidth, aHeight); // recurse to prevent ConcurrentModificationException
                    } else if (bitmap.getWidth() == aWidth && bitmap.getHeight() == aHeight) {
                        mPool.remove(bitmap);
                        return bitmap;
                    }
                }
            }
        }

        return null;
    }

    /**
     * API 19+ optimization: Find a bitmap that has enough allocated bytes to be reused
     * for the requested dimensions, even if the dimensions don't match exactly.
     * 
     * @param aWidth  Required width
     * @param aHeight Required height
     * @return A bitmap with sufficient allocated bytes, or null if none available
     * @since API 23+ optimization
     */
    public Bitmap obtainLargerBitmapFromPool(final int aWidth, final int aHeight) {
        synchronized (mPool) {
            if (mPool.isEmpty()) {
                return null;
            }
            
            final int requiredBytes = aWidth * aHeight * 4; // 4 bytes per pixel for ARGB_8888
            
            for (final Bitmap bitmap : mPool) {
                if (bitmap.isRecycled()) {
                    mPool.remove(bitmap);
                    return obtainLargerBitmapFromPool(aWidth, aHeight); // recurse to prevent ConcurrentModificationException
                } else if (bitmap.getAllocationByteCount() >= requiredBytes) {
                    mPool.remove(bitmap);
                    return bitmap;
                }
            }
        }

        return null;
    }

    public void clearBitmapPool() {
        synchronized (sInstance.mPool) {
            while (!sInstance.mPool.isEmpty()) {
                Bitmap bitmap = sInstance.mPool.remove();
                bitmap.recycle();
            }
        }
    }

    /**
     * @since 6.0.0
     * The same code was duplicated in many places: now there's a unique entry point and it's async
     */
    public void asyncRecycle(final Drawable pDrawable) {
        if (pDrawable == null) {
            return;
        }
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                syncRecycle(pDrawable);
            }
        });
    }

    /**
     * @since 6.0.0
     * API 23+ optimization: Removed manual bitmap recycling as it's unnecessary from API 16+
     */
    private void syncRecycle(final Drawable pDrawable) {
        if (pDrawable == null) {
            return;
        }
        // Manual bitmap recycling removed - unnecessary for API 23+
        // The system handles bitmap memory management automatically
        if (pDrawable instanceof ReusableBitmapDrawable) {
            returnDrawableToPool((ReusableBitmapDrawable) pDrawable);
        }
    }
}
