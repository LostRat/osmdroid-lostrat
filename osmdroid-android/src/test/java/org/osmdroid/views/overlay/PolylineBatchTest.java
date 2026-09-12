package org.osmdroid.views.overlay;

import android.graphics.Canvas;
import android.view.MotionEvent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.DefaultOverlayManager.OverlayLayer;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.FutureTask;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class PolylineBatchTest {
    private final DefaultOverlayManager manager = new DefaultOverlayManager(null);

    private PolylineBatch batch(Polyline... lines) {
        PolylineBatch.Builder builder = new PolylineBatch.Builder();
        for (Polyline line : lines) {
            builder.add(line, OverlayLayer.INTERACTIVE_BACKGROUND);
        }
        return builder.build();
    }

    @Test
    public void replacementPreservesUnrelatedOverlaysAndAllowsRetainedMembers() {
        Polyline unrelated = new Polyline();
        manager.add(unrelated);
        manager.assignOverlayToLayer(unrelated, OverlayLayer.USER_DRAWING);
        Polyline old = new Polyline();
        Polyline retained = new Polyline();
        Polyline added = new Polyline();
        PolylineBatch first = batch(old, retained);
        manager.replacePolylineBatch(PolylineBatch.empty(), first);
        PolylineBatch next = new PolylineBatch.Builder()
                .add(added, OverlayLayer.MAIN_CONTENT)
                .add(retained, OverlayLayer.INTERACTIVE_BACKGROUND).build();
        manager.replacePolylineBatch(first, next);
        assertTrue(manager.validateLayerConsistency());
        assertEquals(Arrays.asList(unrelated, added, retained), manager.overlays());
        assertEquals(Collections.singletonList(unrelated), manager.getOverlaysInLayer(OverlayLayer.USER_DRAWING));
        assertEquals(Collections.singletonList(added), manager.getOverlaysInLayer(OverlayLayer.MAIN_CONTENT));
        assertEquals(Collections.singletonList(retained), manager.getOverlaysInLayer(OverlayLayer.INTERACTIVE_BACKGROUND));
        assertNull(manager.getOverlayLayer(old));
        manager.replacePolylineBatch(next, PolylineBatch.empty());
        assertEquals(Collections.singletonList(unrelated), manager.overlays());
        assertTrue(manager.getOverlaysInLayer(OverlayLayer.INTERACTIVE_BACKGROUND).isEmpty());
    }

    @Test
    public void invalidReplacementDoesNotPartiallyRemoveCurrentBatch() {
        Polyline old = new Polyline();
        Polyline unrelated = new Polyline();
        PolylineBatch first = batch(old);
        manager.replacePolylineBatch(PolylineBatch.empty(), first);
        manager.add(unrelated);
        assertThrows(IllegalArgumentException.class,
                () -> manager.replacePolylineBatch(first, batch(new Polyline(), unrelated)));
        assertEquals(Arrays.asList(old, unrelated), manager.overlays());
        assertEquals(OverlayLayer.INTERACTIVE_BACKGROUND, manager.getOverlayLayer(old));
        assertThrows(IllegalArgumentException.class,
                () -> manager.replacePolylineBatch(batch(new Polyline()), PolylineBatch.empty()));
    }

    @Test
    public void builderSnapshotsOrderAndRejectsDuplicateOrFolderMembers() {
        Polyline line = new Polyline();
        PolylineBatch.Builder builder = new PolylineBatch.Builder()
                .add(line, OverlayLayer.MAIN_CONTENT);
        PolylineBatch first = builder.build();
        builder.add(new Polyline(), OverlayLayer.USER_DRAWING);
        assertEquals(Collections.singletonList(line), first.getPolylines());
        assertThrows(UnsupportedOperationException.class, () -> first.getPolylines().clear());
        assertThrows(IllegalArgumentException.class, () -> builder.add(line, OverlayLayer.USER_DRAWING));
        Polyline child = new Polyline();
        child.setPoints(Arrays.asList(new GeoPoint(0.0, 0.0), new GeoPoint(1.0, 1.0)));
        new FolderOverlay().add(child);
        assertThrows(IllegalArgumentException.class, () -> batch(child));
    }

    @Test
    public void detachedGeometryCanBePreparedOnWorkerButPublicationCannot() throws Exception {
        FutureTask<PolylineBatch> task = new FutureTask<>(() -> {
            Polyline line = new Polyline();
            line.setPoints(Arrays.asList(new GeoPoint(45.0, -122.0), new GeoPoint(45.1, -122.1)));
            line.setTitle("Prepared road");
            line.getOutlinePaint().setStrokeWidth(7);
            PolylineBatch prepared = batch(line);
            assertNull(line.getInfoWindow());
            assertThrows(IllegalStateException.class,
                    () -> manager.replacePolylineBatch(PolylineBatch.empty(), prepared));
            assertThrows(IllegalStateException.class, () -> prepared.attachToMap(null));
            return prepared;
        });
        new Thread(task, "polyline-batch-test").start();
        PolylineBatch prepared = task.get();
        assertTrue(manager.isEmpty());
        MapView map = new MapView(RuntimeEnvironment.getApplication());
        try {
            prepared.attachToMap(map);
            Polyline line = prepared.getPolylines().get(0);
            assertSame(map.getRepository().getDefaultPolylineInfoWindow(), line.getInfoWindow());
            assertEquals(map.getResources().getDisplayMetrics().density, line.mDensity, 0);
            assertEquals(2, line.getActualPoints().size());
            assertEquals(7, line.getOutlinePaint().getStrokeWidth(), 0);
            manager.replacePolylineBatch(PolylineBatch.empty(), prepared);
            assertEquals(1, manager.size());
        } finally {
            map.onDetach();
        }
    }

    @Test
    public void attachmentPreservesCustomInfoWindowAndListener() {
        MapView map = new MapView(RuntimeEnvironment.getApplication());
        try {
            Polyline line = new Polyline();
            org.osmdroid.views.overlay.infowindow.InfoWindow custom =
                    map.getRepository().getDefaultPolygonInfoWindow();
            line.setInfoWindow(custom);
            Polyline.OnClickListener listener = (poly, view, point) -> true;
            line.setOnClickListener(listener);
            line.attachToMap(map);
            assertSame(custom, line.getInfoWindow());
            assertSame(listener, line.mOnClickListener);
        } finally {
            map.onDetach();
        }
    }

    @Test
    public void batchUsesLayerDrawOrderAndReverseTapOrder() {
        List<String> calls = new ArrayList<>();
        RecordingPolyline road = new RecordingPolyline("road", calls);
        RecordingPolyline path = new RecordingPolyline("path", calls);
        RecordingPolyline top = new RecordingPolyline("top", calls);
        PolylineBatch batch = new PolylineBatch.Builder()
                .add(top, OverlayLayer.INTERACTIVE_CONTENT)
                .add(road, OverlayLayer.INTERACTIVE_BACKGROUND)
                .add(path, OverlayLayer.INTERACTIVE_BACKGROUND).build();
        manager.replacePolylineBatch(PolylineBatch.empty(), batch);
        manager.onDraw(null, (Projection) null);
        assertEquals(Arrays.asList("road", "path", "top"), calls);
        calls.clear();
        assertTrue(manager.onSingleTapConfirmed(null, null));
        assertEquals(Collections.singletonList("tap:top"), calls);
        calls.clear();
        top.setEnabled(false);
        assertTrue(manager.onSingleTapConfirmed(null, null));
        assertEquals(Collections.singletonList("tap:path"), calls);
        top.setEnabled(true);
        calls.clear();
        manager.setUseLayerSystem(false);
        manager.onDraw(null, (Projection) null);
        assertEquals(Arrays.asList("top", "road", "path"), calls);
    }

    @Test
    public void largeBatchBypassesPerOverlayAddAndRemoveAndKeepsOldSnapshotStable() {
        DefaultOverlayManager bulkOnly = new DefaultOverlayManager(null) {
            @Override public void add(int index, Overlay overlay) { fail("individual add"); }
            @Override public boolean remove(Object overlay) { fail("individual remove"); return false; }
        };
        PolylineBatch.Builder builder = new PolylineBatch.Builder();
        for (int i = 0; i < 2000; i++) {
            builder.add(new Polyline(), OverlayLayer.INTERACTIVE_BACKGROUND);
        }
        PolylineBatch first = builder.build();
        bulkOnly.replacePolylineBatch(PolylineBatch.empty(), first);
        java.util.Iterator<Overlay> snapshot = bulkOnly.overlays().iterator();
        bulkOnly.replacePolylineBatch(first, batch(new Polyline()));
        int oldCount = 0;
        while (snapshot.hasNext()) { snapshot.next(); oldCount++; }
        assertEquals(2000, oldCount);
        assertEquals(1, bulkOnly.size());
        assertEquals(1, bulkOnly.getOverlaysInLayer(OverlayLayer.INTERACTIVE_BACKGROUND).size());
    }

    private static class RecordingPolyline extends Polyline {
        private final String name;
        private final List<String> calls;
        RecordingPolyline(String name, List<String> calls) { this.name = name; this.calls = calls; }
        @Override public void draw(Canvas canvas, Projection projection) { calls.add(name); }
        @Override public boolean onSingleTapConfirmed(MotionEvent event, MapView map) {
            calls.add("tap:" + name);
            return true;
        }
    }
}
