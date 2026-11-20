package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Tag;
import org.mapsforge.map.datastore.Way;

import java.util.List;

/**
 * A way of which only the texts are drawn: captions and names along it, not its line,
 * area or symbols. Vector tiles carry road names on features of their own
 * ({@code transportation_name}), cut differently from the drawn roads, and park and lake
 * names on points; these become label-only ways. {@link MvtDirectRenderer} and
 * {@link MvtStandardRenderer} draw them that way; a stock renderer draws them as ordinary
 * ways.
 * <p>
 * Never equal to an ordinary {@link Way}, and sorted after one with the same contents, so
 * {@code MapReadResult.deduplicate()} keeps both. (A plain {@code Way.equals} or
 * {@code compareTo} called with a label-only way on the right still compares contents
 * only; the ways this module makes never share contents with an ordinary way, since
 * label-only ways always carry a name or ref, or are single points.)
 *
 * @since 7.0.3-lostrat
 */
public class LabelOnlyWay extends Way {

    /** Same arguments as {@link Way#Way(byte, List, LatLong[][], LatLong)}. */
    public LabelOnlyWay(byte layer, List<Tag> tags, LatLong[][] latLongs, LatLong labelPosition) {
        super(layer, tags, latLongs, labelPosition);
    }

    @Override
    public int compareTo(Way other) {
        if (this == other) {
            return 0;
        }
        if (other == null) {
            return -1;
        }
        int result = super.compareTo(other);
        if (result == 0 && !(other instanceof LabelOnlyWay)) {
            return 1;
        }
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof LabelOnlyWay && super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode() * 31 + 0x4C4F;
    }
}
