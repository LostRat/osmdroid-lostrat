package org.osmdroid.tileprovider.terrain;

import android.graphics.drawable.Drawable;
import android.util.Log;

import org.osmdroid.api.IMapView;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.MapTileModuleProviderBase;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.util.StreamUtils;
import org.osmdroid.util.MapTileIndex;

import java.io.InputStream;

/**
 * Module provider that reads Terrain-RGB tiles from a single archive and hands them to a
 * {@link HillshadeTileSource} for shading.
 * <p>
 * Deliberately single-archive: the hillshade layer is one file at a time, chosen with
 * {@link HillshadeOverlay#setFile(java.io.File)}. The archive is owned and closed by the
 * overlay, not by this provider.
 * </p>
 * <p>
 * Passive: extends {@link MapTileModuleProviderBase} directly, not the file-storage base,
 * so it registers no broadcast receiver and reacts to nothing but the app's calls. The app
 * chose the file; if the file goes away, the app handles that.
 * </p>
 * <p>
 * TODO(seams): {@link TileLoader#loadTile(long)} currently shades one tile in isolation, so
 * Horn's kernel clamps at the tile edge and leaves a one pixel seam. Because the archive is
 * in hand here, the fix is local: read the four edge neighbours (x-1, x+1, y-1, y+1), copy
 * their facing row or column into a grid padded by one pixel on each side, shade the padded
 * grid, and crop the centre.
 * </p>
 *
 * @since 7.0.3-lostrat
 */
public class HillshadeTileProvider extends MapTileModuleProviderBase {

    private final IArchiveFile mArchive;
    private final HillshadeTileSource mTileSource;

    public HillshadeTileProvider(final HillshadeTileSource pTileSource, final IArchiveFile pArchive) {
        super(Configuration.getInstance().getTileFileSystemThreads(),
                Configuration.getInstance().getTileFileSystemMaxQueueSize());
        mTileSource = pTileSource;
        mArchive = pArchive;
    }

    public HillshadeTileSource getHillshadeTileSource() {
        return mTileSource;
    }

    @Override
    protected String getName() {
        return "Terrain-RGB hillshade provider";
    }

    @Override
    protected String getThreadGroupName() {
        return "hillshade";
    }

    @Override
    public MapTileModuleProviderBase.TileLoader getTileLoader() {
        return new TileLoader();
    }

    @Override
    public boolean getUsesDataConnection() {
        return false;
    }

    @Override
    public int getMinimumZoomLevel() {
        return mTileSource.getMinimumZoomLevel();
    }

    @Override
    public int getMaximumZoomLevel() {
        return mTileSource.getMaximumZoomLevel();
    }

    /** The tile source is fixed at construction; this is a no-op kept for the base class contract. */
    @Override
    public void setTileSource(final ITileSource pTileSource) {
    }

    protected class TileLoader extends MapTileModuleProviderBase.TileLoader {

        @Override
        public Drawable loadTile(final long pMapTileIndex) {
            final InputStream in = mArchive.getInputStream(mTileSource, pMapTileIndex);
            if (in == null) {
                return null;
            }
            try {
                return mTileSource.shade(in, pMapTileIndex);
            } catch (final Throwable e) {
                Log.e(IMapView.LOGTAG, "Error shading tile " + MapTileIndex.toString(pMapTileIndex), e);
                return null;
            } finally {
                StreamUtils.closeStream(in);
            }
        }
    }
}
