package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.graphics.Curve;
import org.mapsforge.core.graphics.Display;
import org.mapsforge.core.graphics.Paint;
import org.mapsforge.core.graphics.Position;
import org.mapsforge.core.graphics.SymbolOrientation;
import org.mapsforge.core.graphics.TextOrientation;
import org.mapsforge.core.model.Rectangle;
import org.mapsforge.core.model.Tag;
import org.mapsforge.core.util.Parameters;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.PointOfInterest;
import org.mapsforge.map.datastore.Way;
import org.mapsforge.map.layer.renderer.PolylineContainer;
import org.mapsforge.map.layer.renderer.StandardRenderer;
import org.mapsforge.map.rendertheme.RenderCallback;
import org.mapsforge.map.rendertheme.RenderContext;

/**
 * The part of {@link MvtDirectRenderer} and {@link MvtStandardRenderer} that differs from
 * the stock {@code StandardRenderer.processReadMapData}: a {@link LabelOnlyWay} is matched
 * against the theme with a callback that passes on only area captions and way texts.
 */
final class LabelOnlyRendering {

    /** The renderer's own steps for everything that is not a label-only way. */
    interface Steps {
        void renderPointOfInterest(RenderContext renderContext, PointOfInterest poi);

        void renderWay(RenderContext renderContext, PolylineContainer way);

        void renderWaterBackground(RenderContext renderContext);
    }

    private final RenderCallback textOnly;

    LabelOnlyRendering(StandardRenderer renderer) {
        this.textOnly = new TextOnlyCallback(renderer);
    }

    /** Same order as {@code StandardRenderer.processReadMapData}: POIs, ways, then the water background. */
    void process(Steps steps, RenderContext renderContext, MapReadResult mapReadResult) {
        if (mapReadResult == null) {
            return;
        }
        for (PointOfInterest pointOfInterest : mapReadResult.pois) {
            steps.renderPointOfInterest(renderContext, pointOfInterest);
        }
        for (Way way : mapReadResult.ways) {
            PolylineContainer container = new PolylineContainer(way, renderContext.rendererJob.tile,
                    renderContext.rendererJob.tile);
            if (way instanceof LabelOnlyWay) {
                renderLabelOnlyWay(renderContext, container);
            } else {
                steps.renderWay(renderContext, container);
            }
        }
        if (mapReadResult.isWater) {
            steps.renderWaterBackground(renderContext);
        }
    }

    /** {@code StandardRenderer.renderWay} with the text-only callback. */
    private void renderLabelOnlyWay(RenderContext renderContext, PolylineContainer way) {
        renderContext.setDrawingLayer(way.getLayer());
        if (way.isClosedWay()) {
            if (renderContext.rendererJob.tile.zoomLevel >= Parameters.POLYGON_ZOOM_MIN) {
                renderContext.renderTheme.matchClosedWay(textOnly, renderContext, way);
            } else if (!Parameters.POLYGON_EXCEPTIONS.isEmpty()) {
                for (Tag tag : Parameters.POLYGON_EXCEPTIONS) {
                    if (way.getTags().contains(tag)) {
                        renderContext.renderTheme.matchClosedWay(textOnly, renderContext, way);
                        break;
                    }
                }
            }
        } else {
            renderContext.renderTheme.matchLinearWay(textOnly, renderContext, way);
        }
    }

    /**
     * Passes area captions and way texts to the renderer's current callback and drops
     * lines, areas, symbols and POI output. Looks the callback up on every call, so a
     * subclass overriding {@code getRenderCallback()} is honoured.
     */
    private static final class TextOnlyCallback implements RenderCallback {
        private final StandardRenderer renderer;

        TextOnlyCallback(StandardRenderer renderer) {
            this.renderer = renderer;
        }

        @Override
        public void renderAreaCaption(RenderContext renderContext, Display display, int priority, String caption,
                                      float horizontalOffset, float verticalOffset, Paint fill, Paint stroke,
                                      Position position, int maxTextWidth, PolylineContainer way) {
            renderer.getRenderCallback().renderAreaCaption(renderContext, display, priority, caption,
                    horizontalOffset, verticalOffset, fill, stroke, position, maxTextWidth, way);
        }

        @Override
        public void renderWayText(RenderContext renderContext, Display display, int priority, String text, float dy,
                                  Paint fill, Paint stroke, boolean repeat, float repeatGap, float repeatStart,
                                  TextOrientation textOrientation, PolylineContainer way) {
            renderer.getRenderCallback().renderWayText(renderContext, display, priority, text, dy, fill, stroke,
                    repeat, repeatGap, repeatStart, textOrientation, way);
        }

        @Override
        public void renderArea(RenderContext renderContext, Paint fill, Paint stroke, int level, PolylineContainer way) {
        }

        @Override
        public void renderAreaSymbol(RenderContext renderContext, Display display, int priority, Bitmap symbol,
                                     PolylineContainer way) {
        }

        @Override
        public void renderPointOfInterestCaption(RenderContext renderContext, Display display, int priority,
                                                 String caption, float horizontalOffset, float verticalOffset,
                                                 Paint fill, Paint stroke, Position position, int maxTextWidth,
                                                 PointOfInterest poi) {
        }

        @Override
        public void renderPointOfInterestCircle(RenderContext renderContext, float radius, Paint fill, Paint stroke,
                                                int level, PointOfInterest poi) {
        }

        @Override
        public void renderPointOfInterestSymbol(RenderContext renderContext, Display display, int priority,
                                                Rectangle boundary, Bitmap symbol, PointOfInterest poi) {
        }

        @Override
        public void renderWay(RenderContext renderContext, Paint stroke, float dy, Curve curveStyle, int level,
                              PolylineContainer way) {
        }

        @Override
        public void renderWaySymbol(RenderContext renderContext, Display display, int priority, Bitmap symbol,
                                    float dy, Rectangle boundary, boolean repeat, float repeatGap, float repeatStart,
                                    SymbolOrientation symbolOrientation, PolylineContainer way) {
        }
    }
}
