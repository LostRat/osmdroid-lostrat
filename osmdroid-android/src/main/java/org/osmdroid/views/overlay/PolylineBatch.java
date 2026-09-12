package org.osmdroid.views.overlay;

import android.os.Looper;

import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.DefaultOverlayManager.OverlayLayer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * An ordered, structurally immutable batch of polylines prepared on a worker thread.
 * Construct polylines with a null MapView, finish their geometry and styling, then build
 * the batch. Transfer it with View.post or another safe handoff to the UI thread.
 * The polyline objects are NOT immutable: after handoff the worker must not touch them.
 * Use explicit layers so preparation never needs to inspect the live map.
 *
 * @see DefaultOverlayManager#replacePolylineBatch(PolylineBatch, PolylineBatch)
 */
public final class PolylineBatch {
    private static final PolylineBatch EMPTY = new Builder().build();
    final List<Polyline> polylines;
    final Set<Polyline> members;
    final Map<OverlayLayer, List<Polyline>> layers;

    private PolylineBatch(Builder builder) {
        polylines = Collections.unmodifiableList(new ArrayList<>(builder.polylines));
        members = Collections.unmodifiableSet(new HashSet<>(polylines));
        Map<OverlayLayer, List<Polyline>> copy = new EnumMap<>(OverlayLayer.class);
        for (Map.Entry<OverlayLayer, List<Polyline>> entry : builder.layers.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        layers = Collections.unmodifiableMap(copy);
    }

    public static PolylineBatch empty() {
        return EMPTY;
    }

    /** In insertion order; neither this list nor the builder owns mutable geometry copies. */
    public List<Polyline> getPolylines() {
        return polylines;
    }

    /**
     * UI thread only, before publication. Restores map density and the default info window
     * for detached polylines, preserving any custom info windows. Does not add or draw them.
     */
    public void attachToMap(MapView mapView) {
        requireMainThread();
        Objects.requireNonNull(mapView, "mapView");
        for (Polyline polyline : polylines) {
            polyline.attachToMap(mapView);
        }
    }

    static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("Polyline batch publication requires the main thread");
        }
    }

    /** Worker-confined builder. A built batch is unaffected by subsequent builder additions. */
    public static final class Builder {
        private final List<Polyline> polylines = new ArrayList<>();
        private final Set<Polyline> members = new HashSet<>();
        private final Map<OverlayLayer, List<Polyline>> layers = new EnumMap<>(OverlayLayer.class);

        /** Adds a complete, exclusively owned polyline; order within each layer is preserved. */
        public Builder add(Polyline polyline, OverlayLayer layer) {
            Objects.requireNonNull(polyline, "polyline");
            Objects.requireNonNull(layer, "layer");
            if (polyline.getParent() != null) {
                throw new IllegalArgumentException("Batch polylines cannot belong to a folder");
            }
            if (!members.add(polyline)) {
                throw new IllegalArgumentException("Duplicate polyline in batch");
            }
            polylines.add(polyline);
            List<Polyline> list = layers.get(layer);
            if (list == null) {
                list = new ArrayList<>();
                layers.put(layer, list);
            }
            list.add(polyline);
            return this;
        }

        public PolylineBatch build() {
            return new PolylineBatch(this);
        }
    }
}
