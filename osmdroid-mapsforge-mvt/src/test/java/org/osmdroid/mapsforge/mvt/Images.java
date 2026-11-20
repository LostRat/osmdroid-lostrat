package org.osmdroid.mapsforge.mvt;

import java.io.File;
import java.lang.reflect.Method;

/**
 * PNG pixels through reflection: Android unit tests compile against android.jar, which has
 * no java.awt, though the test JVM has it at run time.
 */
final class Images {
    private Images() {
    }

    /** ARGB pixels of a PNG, row by row; element {@code [w*h]} and {@code [w*h+1]} hold width and height. */
    static int[] read(File png) throws Exception {
        Class<?> imageIo = Class.forName("javax.imageio.ImageIO");
        Object image = imageIo.getMethod("read", File.class).invoke(null, png);
        int w = (Integer) image.getClass().getMethod("getWidth").invoke(image);
        int h = (Integer) image.getClass().getMethod("getHeight").invoke(image);
        int[] pixels = new int[w * h + 2];
        Method getRgb = image.getClass().getMethod("getRGB", int.class, int.class, int.class, int.class,
                int[].class, int.class, int.class);
        getRgb.invoke(image, 0, 0, w, h, pixels, 0, w);
        pixels[w * h] = w;
        pixels[w * h + 1] = h;
        return pixels;
    }

    static void write(int[] rgb, int w, int h, File png) throws Exception {
        Class<?> bufferedImage = Class.forName("java.awt.image.BufferedImage");
        int typeIntRgb = bufferedImage.getField("TYPE_INT_RGB").getInt(null);
        Object image = bufferedImage.getConstructor(int.class, int.class, int.class).newInstance(w, h, typeIntRgb);
        bufferedImage.getMethod("setRGB", int.class, int.class, int.class, int.class, int[].class, int.class,
                int.class).invoke(image, 0, 0, w, h, rgb, 0, w);
        Class<?> renderedImage = Class.forName("java.awt.image.RenderedImage");
        Class.forName("javax.imageio.ImageIO").getMethod("write", renderedImage, String.class, File.class)
                .invoke(null, image, "png", png);
    }
}
