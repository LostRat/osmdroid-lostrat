package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.graphics.GraphicFactory;
import org.mapsforge.map.datastore.MapDataStore;
import org.mapsforge.map.datastore.MapReadResult;
import org.mapsforge.map.datastore.PointOfInterest;
import org.mapsforge.map.layer.hills.HillsRenderConfig;
import org.mapsforge.map.layer.labels.LabelStore;
import org.mapsforge.map.layer.renderer.DirectRenderer;
import org.mapsforge.map.layer.renderer.PolylineContainer;
import org.mapsforge.map.rendertheme.RenderContext;

/**
 * A {@link DirectRenderer} that draws only the captions and way texts of a
 * {@link LabelOnlyWay}, so the road names vector tiles carry on separate features are not
 * also stroked as roads. Everything else is drawn as the superclass draws it.
 * <p>
 * When labels come from a label store (as with {@code MapDataStoreLabelStore}), that
 * store's own renderer must handle label-only ways too: use {@link MvtStandardRenderer}
 * there.
 *
 * @since 7.0.3-lostrat
 */
public class MvtDirectRenderer extends DirectRenderer {

    private final LabelOnlyRendering labelOnly = new LabelOnlyRendering(this);

    private final LabelOnlyRendering.Steps steps = new LabelOnlyRendering.Steps() {
        @Override
        public void renderPointOfInterest(RenderContext renderContext, PointOfInterest poi) {
            MvtDirectRenderer.this.renderPointOfInterest(renderContext, poi);
        }

        @Override
        public void renderWay(RenderContext renderContext, PolylineContainer way) {
            MvtDirectRenderer.this.renderWay(renderContext, way);
        }

        @Override
        public void renderWaterBackground(RenderContext renderContext) {
            MvtDirectRenderer.this.renderWaterBackground(renderContext);
        }
    };

    public MvtDirectRenderer(MapDataStore mapDataStore, GraphicFactory graphicFactory, LabelStore labelStore,
                             boolean renderLabels, boolean cacheLabels, HillsRenderConfig hillsRenderConfig) {
        super(mapDataStore, graphicFactory, labelStore, renderLabels, cacheLabels, hillsRenderConfig);
    }

    @Override
    public void processReadMapData(RenderContext renderContext, MapReadResult mapReadResult) {
        labelOnly.process(steps, renderContext, mapReadResult);
    }
}
