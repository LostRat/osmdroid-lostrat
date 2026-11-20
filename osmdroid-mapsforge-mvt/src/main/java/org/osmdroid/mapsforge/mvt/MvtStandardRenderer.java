package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.PointOfInterest;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.renderer.PolylineContainer;
import org.mapsforge.map.layer.renderer.StandardRenderer;
import org.mapsforge.map.rendertheme.RenderContext;

/**
 * A {@link StandardRenderer} that draws only the captions and way texts of a
 * {@link LabelOnlyWay}. Everything else is drawn as the superclass draws it. Use it where a
 * label pass would use a plain {@code StandardRenderer}, e.g. in a label store.
 *
 * @since 7.0.3-lostrat
 */
public class MvtStandardRenderer extends StandardRenderer {

    private final LabelOnlyRendering labelOnly = new LabelOnlyRendering(this);

    private final LabelOnlyRendering.Steps steps = new LabelOnlyRendering.Steps() {
        @Override
        public void renderPointOfInterest(RenderContext renderContext, PointOfInterest poi) {
            MvtStandardRenderer.this.renderPointOfInterest(renderContext, poi);
        }

        @Override
        public void renderWay(RenderContext renderContext, PolylineContainer way) {
            MvtStandardRenderer.this.renderWay(renderContext, way);
        }

        @Override
        public void renderWaterBackground(RenderContext renderContext) {
            MvtStandardRenderer.this.renderWaterBackground(renderContext);
        }
    };

    public MvtStandardRenderer(MapDataStore mapDataStore, GraphicFactory graphicFactory, boolean renderLabels) {
        super(mapDataStore, graphicFactory, renderLabels);
    }

    public MvtStandardRenderer(MapDataStore mapDataStore, GraphicFactory graphicFactory, boolean renderLabels,
                               HillsRenderConfig hillsRenderConfig) {
        super(mapDataStore, graphicFactory, renderLabels, hillsRenderConfig);
    }

    @Override
    public void processReadMapData(RenderContext renderContext, MapReadResult mapReadResult) {
        labelOnly.process(steps, renderContext, mapReadResult);
    }
}
