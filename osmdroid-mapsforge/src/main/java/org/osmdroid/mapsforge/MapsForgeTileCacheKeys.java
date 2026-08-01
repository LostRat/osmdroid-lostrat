package org.osmdroid.mapsforge;

import org.osmdroid.util.DisplayDensityManager;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Builds stable tile source names for MapsForge rendered tile caches.
 * <p>
 * Keys are derived from the map files, theme and the <b>render scale factor</b> actually
 * applied to the tile source (see {@link MapsForgeTileSource#applyDensityScaling()} /
 * {@link MapsForgeTileSource#setUserScaleFactor(float)}) — not the raw display density.
 * Tiles rendered at different scales must never share a cache namespace, and densities
 * that clamp to the same scale should share one.
 */
public final class MapsForgeTileCacheKeys {

    private static final String PREFIX = "mapsforge";
    private static final String UNKNOWN_MAP = "unknown-map";
    private static final String DEFAULT_THEME = "default-theme";
    private static final int MAX_READABLE_LENGTH = 96;
    private static final int HASH_LENGTH = 12;

    private MapsForgeTileCacheKeys() {
    }

    /**
     * The render scale that {@link MapsForgeTileSource#applyDensityScaling()} will apply,
     * or 1.0 when the {@link DisplayDensityManager} is not initialized (in which case
     * applyDensityScaling() is a no-op and tiles render at the default scale).
     */
    public static float currentRenderScale() {
        if (!DisplayDensityManager.isInitialized()) {
            return 1.0f;
        }
        return DisplayDensityManager.getInstance().getMapForgeScaleFactor();
    }

    public static String forMapAndTheme(final File mapFile, final String themeName) {
        return forMapAndTheme(mapFile, themeName, currentRenderScale());
    }

    public static String forMapAndTheme(final File mapFile, final String themeName,
                                        final float renderScale) {
        return forMapsAndTheme(new File[]{mapFile}, themeName, renderScale);
    }

    public static String forMapsAndTheme(final File[] mapFiles, final String themeName) {
        return forMapsAndTheme(mapFiles, themeName, currentRenderScale());
    }

    public static String forMapsAndTheme(final File[] mapFiles, final String themeName,
                                         final float renderScale) {
        return forMapsThemeAndVariant(mapFiles, themeName, renderScale, null);
    }

    /**
     * @param renderScale the user scale factor the tile source will render with — pass
     *                    {@link #currentRenderScale()} when using
     *                    {@link MapsForgeTileSource#applyDensityScaling()}, or the exact
     *                    value given to {@link MapsForgeTileSource#setUserScaleFactor(float)}
     */
    public static String forMapsThemeAndVariant(final File[] mapFiles, final String themeName,
                                                final float renderScale, final String variant) {
        final String mapPart = getReadableMapPart(mapFiles);
        final String themePart = sanitize(themeName, DEFAULT_THEME);
        final String scalePart = "scale-" + Math.round(renderScale * 100);
        final String variantPart = sanitize(variant, null);
        final String hash = shortHash(mapFiles, themeName, renderScale, variant);

        final StringBuilder key = new StringBuilder(PREFIX);
        key.append('-').append(mapPart);
        key.append('-').append(themePart);
        key.append('-').append(scalePart);
        if (variantPart != null) {
            key.append('-').append(variantPart);
        }
        key.append('-').append(hash);
        return limitLength(key.toString());
    }

    public static String sanitize(final String value) {
        return sanitize(value, PREFIX);
    }

    private static String getReadableMapPart(final File[] mapFiles) {
        if (mapFiles == null || mapFiles.length == 0) {
            return UNKNOWN_MAP;
        }
        if (mapFiles.length == 1) {
            return sanitize(getFileName(mapFiles[0]), UNKNOWN_MAP);
        }
        final String firstMap = sanitize(getFileName(mapFiles[0]), UNKNOWN_MAP);
        return firstMap + "-plus-" + (mapFiles.length - 1) + "-maps";
    }

    private static String getFileName(final File mapFile) {
        return mapFile != null ? mapFile.getName() : UNKNOWN_MAP;
    }

    private static String sanitize(final String value, final String fallback) {
        if (value == null) {
            return fallback;
        }

        final String trimmed = value.trim().toLowerCase(Locale.US);
        if (trimmed.length() == 0) {
            return fallback;
        }

        final StringBuilder sanitized = new StringBuilder(trimmed.length());
        boolean previousWasSeparator = false;
        for (int i = 0; i < trimmed.length(); i++) {
            final char c = trimmed.charAt(i);
            final boolean safe = (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '.';
            if (safe) {
                sanitized.append(c);
                previousWasSeparator = false;
            } else if (!previousWasSeparator) {
                sanitized.append('-');
                previousWasSeparator = true;
            }
        }

        int start = 0;
        int end = sanitized.length();
        while (start < end && isSeparator(sanitized.charAt(start))) {
            start++;
        }
        while (end > start && isSeparator(sanitized.charAt(end - 1))) {
            end--;
        }

        if (start >= end) {
            return fallback;
        }
        return sanitized.substring(start, end);
    }

    private static boolean isSeparator(final char c) {
        return c == '-' || c == '.';
    }

    private static String shortHash(final File[] mapFiles, final String themeName,
                                    final float renderScale, final String variant) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, themeName);
            updateDigest(digest, Float.toString(renderScale));
            updateDigest(digest, variant);

            if (mapFiles != null) {
                updateDigest(digest, Integer.toString(mapFiles.length));
                for (final File mapFile : mapFiles) {
                    // Name + size + mtime (not absolute path) so relocating the same
                    // map file does not bust the rendered-tile cache namespace.
                    updateDigest(digest, mapFile != null ? mapFile.getName() : null);
                    updateDigest(digest, mapFile != null ? Long.toString(mapFile.length()) : null);
                    updateDigest(digest, mapFile != null ? Long.toString(mapFile.lastModified()) : null);
                }
            }

            final byte[] bytes = digest.digest();
            final StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (final byte b : bytes) {
                hex.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return hex.substring(0, HASH_LENGTH);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private static void updateDigest(final MessageDigest digest, final String value) {
        if (value != null) {
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        }
        digest.update((byte) 0);
    }

    private static String limitLength(final String key) {
        if (key.length() <= MAX_READABLE_LENGTH) {
            return key;
        }
        final int hashStart = key.lastIndexOf('-');
        if (hashStart < 0) {
            return key.substring(0, MAX_READABLE_LENGTH);
        }
        final String hash = key.substring(hashStart);
        return key.substring(0, MAX_READABLE_LENGTH - hash.length()) + hash;
    }
}
