package org.osmdroid.mapsforge.mvt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds MVT tiles from features with string or integer properties. */
final class TileBuilder {
    private final Map<String, LayerBuilder> layers = new LinkedHashMap<>();

    LayerBuilder layer(String name) {
        LayerBuilder layer = layers.get(name);
        if (layer == null) {
            layer = new LayerBuilder(name);
            layers.put(name, layer);
        }
        return layer;
    }

    byte[] build() {
        Pbf tile = new Pbf();
        for (LayerBuilder layer : layers.values()) {
            tile.message(3, layer.build());
        }
        return tile.toBytes();
    }

    static final class LayerBuilder {
        private final String name;
        private final List<String> keys = new ArrayList<>();
        private final List<Object> values = new ArrayList<>();
        private final List<Pbf> features = new ArrayList<>();

        LayerBuilder(String name) {
            this.name = name;
        }

        /** {@code properties} alternate key, value (String, Long/Integer or Boolean). */
        LayerBuilder feature(int type, Pbf.Geometry geometry, Object... properties) {
            long[] tags = new long[properties.length];
            for (int i = 0; i + 1 < properties.length; i += 2) {
                tags[i] = index(keys, (String) properties[i]);
                tags[i + 1] = index(values, properties[i + 1]);
            }
            features.add(Pbf.feature(type, tags, geometry.build()));
            return this;
        }

        private static <T> int index(List<T> list, T item) {
            int i = list.indexOf(item);
            if (i < 0) {
                list.add(item);
                i = list.size() - 1;
            }
            return i;
        }

        Pbf build() {
            Pbf layer = new Pbf().uint(15, 2).string(1, name);
            for (Pbf feature : features) {
                layer.message(2, feature);
            }
            for (String key : keys) {
                layer.string(3, key);
            }
            for (Object value : values) {
                Pbf v = new Pbf();
                if (value instanceof String) {
                    v.string(1, (String) value);
                } else if (value instanceof Boolean) {
                    v.uint(7, (Boolean) value ? 1 : 0);
                } else {
                    v.uint(6, Pbf.zigzag(((Number) value).longValue()));
                }
                layer.message(4, v);
            }
            layer.uint(5, 4096);
            return layer;
        }
    }
}
