package org.osmdroid.mapsforge;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class MapsForgeTileCacheKeysTest {

    @Test
    public void forMapAndThemeUsesSafeReadableParts() {
        final String key = MapsForgeTileCacheKeys.forMapAndTheme(
                new File("maps/Forest Park 2025.map"),
                "Default Path.xml",
                0.75f);

        Assert.assertTrue(key.startsWith("mapsforge-forest-park-2025.map-default-path.xml-scale-75-"));
        Assert.assertTrue(key.matches("[a-z0-9.-]+"));
    }

    @Test
    public void forMapAndThemeSeparatesThemesAndScale() {
        final File mapFile = new File("maps/forest-park-2025.map");

        final String defaultTheme = MapsForgeTileCacheKeys.forMapAndTheme(mapFile, "default", 0.6f);
        final String hikeTheme = MapsForgeTileCacheKeys.forMapAndTheme(mapFile, "hike", 0.6f);
        final String higherScale = MapsForgeTileCacheKeys.forMapAndTheme(mapFile, "default", 1.0f);

        Assert.assertNotEquals(defaultTheme, hikeTheme);
        Assert.assertNotEquals(defaultTheme, higherScale);
    }

    @Test
    public void forMapsAndThemePreservesMapOrderInHash() {
        final File first = new File("maps/first.map");
        final File second = new File("maps/second.map");

        final String firstThenSecond = MapsForgeTileCacheKeys.forMapsAndTheme(
                new File[]{first, second}, "default", 0.6f);
        final String secondThenFirst = MapsForgeTileCacheKeys.forMapsAndTheme(
                new File[]{second, first}, "default", 0.6f);

        Assert.assertNotEquals(firstThenSecond, secondThenFirst);
    }

    @Test
    public void forMapAndThemeIgnoresAbsolutePath() {
        // Same basename/size/mtime under different directories must share a cache key.
        final File underMaps = new File("maps/forest-park-2025.map");
        final File underCache = new File("cache/copy/forest-park-2025.map");

        final String fromMaps = MapsForgeTileCacheKeys.forMapAndTheme(underMaps, "default", 0.6f);
        final String fromCache = MapsForgeTileCacheKeys.forMapAndTheme(underCache, "default", 0.6f);

        Assert.assertEquals(fromMaps, fromCache);
    }

    @Test
    public void currentRenderScaleFallsBackToDefaultWithoutManager() {
        // DisplayDensityManager is never initialized in unit tests, matching the case
        // where applyDensityScaling() is a no-op and tiles render at the default scale.
        Assert.assertEquals(1.0f, MapsForgeTileCacheKeys.currentRenderScale(), 0.0f);
    }

    @Test
    public void sanitizeFallsBackForBlankValues() {
        Assert.assertEquals("mapsforge", MapsForgeTileCacheKeys.sanitize("   "));
        Assert.assertEquals("map-theme", MapsForgeTileCacheKeys.sanitize(" Map Theme "));
        Assert.assertEquals("theme-v4.xml", MapsForgeTileCacheKeys.sanitize("theme/v4.xml"));
    }
}
