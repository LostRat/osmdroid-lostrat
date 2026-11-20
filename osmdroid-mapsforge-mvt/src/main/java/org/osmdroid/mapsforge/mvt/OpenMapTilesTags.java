package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.model.Tag;
import org.osmdroid.mapsforge.mvt.VectorTile.Feature;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns features of the OpenMapTiles schema (what tilemaker's OpenMapTiles config writes:
 * layers such as {@code transportation} with a {@code class} and {@code subclass}) back
 * into the OSM tags a mapsforge render theme matches, e.g. {@code highway=path}. With these
 * a theme written for mapsforge .map files draws vector tiles unchanged.
 *
 * @since 7.0.3-lostrat
 */
public final class OpenMapTilesTags {

    private OpenMapTilesTags() {
    }

    /** What a feature becomes: its tags and how it is drawn. */
    public static final class Element {
        /** OSM tags, in the order the theme expects them. */
        public List<Tag> tags;
        /** Mapsforge's drawing layer: the OSM {@code layer} tag plus 5, from 0 to 10. */
        public int layer = 5;
        /** Only its name is drawn: the line or area itself comes from another feature. */
        public boolean textOnly;
        /** The line runs against its one-way direction ({@code oneway=-1}): to be turned round. */
        public boolean reversed;
        /**
         * A point is left out below this zoom, as the mapsforge map writer leaves POIs out
         * of a .map file below their {@code zoom-appear}.
         */
        public int minZoom;

        Element(List<Tag> tags) {
            this.tags = tags;
        }

        Element(List<Tag> tags, boolean textOnly, int minZoom) {
            this.tags = tags;
            this.textOnly = textOnly;
            this.minZoom = minZoom;
        }
    }

    /** The feature's name: {@code name:latin}, else {@code name}, else {@code name_en}. */
    public static String name(Feature feature) {
        String name = feature.string("name:latin");
        if (name == null) {
            name = feature.string("name");
        }
        if (name == null) {
            name = feature.string("name_en");
        }
        return name;
    }

    /**
     * The OSM tags a feature of {@code layer} is given, null if it is left out. For checking
     * the conversion against a file.
     */
    public static List<Tag> tags(String layer, Feature feature) {
        if (feature.type == VectorTile.POINT) {
            if ("water_name".equals(layer)) {
                return waterLabel(feature);
            }
            Element element = node(layer, feature);
            return element == null ? null : element.tags;
        }
        Element element = way(layer, feature);
        return element == null ? null : element.tags;
    }

    // Ways

    /** Tags for a line or polygon feature of {@code layer}; null if the theme has no use for it. */
    public static Element way(String layer, Feature feature) {
        boolean isArea = feature.type == VectorTile.POLYGON;
        String cls = orEmpty(feature.string("class"));
        String subclass = feature.string("subclass");
        List<Tag> tags = new ArrayList<>(4);
        Element element = new Element(tags);

        switch (layer) {
            case "transportation": {
                Tag road = road(cls, subclass, isOne(feature.integer("ramp")));
                if (road == null) {
                    return null;
                }
                if (isArea) {
                    // Squares and platforms; road bridges' outlines have no rule in the theme.
                    if (!(road.key.equals("man_made") || road.value.equals("pedestrian")
                            || road.value.equals("footway") || road.value.equals("platform"))) {
                        return null;
                    }
                    tags.add(road);
                    if (road.key.equals("highway") && !road.value.equals("platform")) {
                        tags.add(new Tag("area", "yes"));
                    }
                } else {
                    tags.add(road);
                    // The tiles keep no tracktype, only whether the surface is paved: a
                    // paved track is drawn as the theme's best grade, the rest as ungraded.
                    if (road.value.equals("track") && "paved".equals(feature.string("surface"))) {
                        tags.add(new Tag("tracktype", "grade1"));
                    }
                }
                String brunnel = feature.string("brunnel");
                if ("bridge".equals(brunnel)) {
                    tags.add(new Tag("bridge", "yes"));
                } else if ("tunnel".equals(brunnel)) {
                    tags.add(new Tag("tunnel", "yes"));
                }
                String access = feature.string("access");
                if ("private".equals(access) || "destination".equals(access)) {
                    tags.add(new Tag("access", access));
                }
                Long oneway = feature.integer("oneway");
                if (oneway != null && (oneway == 1 || oneway == -1)) {
                    tags.add(new Tag("oneway", "yes"));
                    element.reversed = oneway == -1;
                }
                Long osmLayer = feature.integer("layer");
                long drawing = (osmLayer == null ? 0 : osmLayer) + 5;
                element.layer = (int) Math.max(0, Math.min(10, drawing));
                break;
            }

            case "transportation_name": {
                // The same roads again, cut differently, carrying the names. The theme
                // names roads, trails and ski lifts, not railways.
                if (isArea) {
                    return null;
                }
                Tag road = road(cls, subclass, false);
                if (road == null || !(road.key.equals("highway") || road.key.equals("aerialway"))) {
                    return null;
                }
                tags.add(road);
                String name = name(feature);
                if (name != null) {
                    tags.add(new Tag("name", name));
                }
                String ref = feature.string("ref");
                if (ref != null) {
                    tags.add(new Tag("ref", ref));
                }
                if (tags.size() <= 1) {
                    return null;
                }
                element.textOnly = true;
                break;
            }

            case "waterway": {
                if (isArea || cls.isEmpty()) {
                    return null;
                }
                tags.add(new Tag("waterway", cls));
                String brunnel = feature.string("brunnel");
                if ("bridge".equals(brunnel)) {
                    tags.add(new Tag("bridge", "yes"));
                } else if ("tunnel".equals(brunnel)) {
                    tags.add(new Tag("tunnel", "yes"));
                }
                String name = name(feature);
                if (name != null) {
                    tags.add(new Tag("name", name));
                }
                break;
            }

            case "water":
                if (!isArea) {
                    return null;
                }
                switch (cls) {
                    case "ocean":
                        tags.add(new Tag("natural", "sea"));
                        break;
                    case "swimming_pool":
                        tags.add(new Tag("leisure", "swimming_pool"));
                        break;
                    case "dock":
                        tags.add(new Tag("waterway", "dock"));
                        break;
                    default:
                        tags.add(new Tag("natural", "water"));
                        break;
                }
                break;

            case "landcover": {
                Tag tag = isArea ? landcover(cls, subclass == null ? "" : subclass) : null;
                if (tag == null) {
                    return null;
                }
                tags.add(tag);
                break;
            }

            case "landuse": {
                Tag tag = isArea ? LANDUSE.get(cls) : null;
                if (tag == null) {
                    return null;
                }
                tags.add(tag);
                break;
            }

            case "park": {
                if (!isArea) {
                    return null;
                }
                String value = cls.equals("national_park") || cls.equals("nature_reserve") ? cls : "protected_area";
                tags.add(new Tag("boundary", value));
                String name = name(feature);
                if (name != null) {
                    tags.add(new Tag("name", name));
                }
                break;
            }

            case "building":
                if (!isArea) {
                    return null;
                }
                tags.add(new Tag("building", "yes"));
                break;

            case "boundary": {
                Long level = feature.integer("admin_level");
                if (isArea || level == null || isOne(feature.integer("maritime"))) {
                    return null;
                }
                tags.add(new Tag("admin_level", Long.toString(level)));
                break;
            }

            case "aeroway":
                if (cls.isEmpty()) {
                    return null;
                }
                tags.add(new Tag("aeroway", cls));
                break;

            case "contour": {
                // Contour tiles: class major, medium or minor, ele in feet.
                if (isArea || cls.isEmpty()) {
                    return null;
                }
                tags.add(new Tag("contour_ext", "elevation_" + cls));
                VectorTile.Value ele = feature.get("ele");
                if (ele != null) {
                    tags.add(new Tag("ele", ele.text()));
                }
                break;
            }

            default:
                return null;
        }
        element.tags = tags;
        return element;
    }

    /** The OSM key and value for a {@code transportation} class, as a tag; null if none. */
    private static Tag road(String cls, String subclass, boolean ramp) {
        switch (cls) {
            case "motorway":
            case "trunk":
            case "primary":
            case "secondary":
            case "tertiary":
                return new Tag("highway", ramp ? cls + "_link" : cls);
            case "minor":
                return new Tag("highway", subclass != null ? subclass : "unclassified");
            case "service":
            case "track":
                return new Tag("highway", cls);
            case "busway":
            case "bus_guideway":
            case "raceway":
                return new Tag("highway", "service");
            case "path":
                return new Tag("highway", subclass != null ? subclass : "path");
            case "rail":
                return new Tag("railway", subclass != null ? subclass : "rail");
            case "transit":
                return new Tag("railway", subclass != null ? subclass : "light_rail");
            case "ferry":
                return new Tag("route", "ferry");
            case "aerialway":
                return new Tag("aerialway", subclass != null ? subclass : "cable_car");
            case "pier":
                return new Tag("man_made", "pier");
            default:
                return cls.endsWith("_construction") ? new Tag("highway", "construction") : null;
        }
    }

    private static Tag landcover(String cls, String subclass) {
        switch (cls) {
            case "wood":
                return subclass.equals("forest") ? new Tag("landuse", "forest") : new Tag("natural", "wood");
            case "wetland":
                return new Tag("natural", "wetland");
            case "ice":
                return new Tag("natural", "glacier");
            case "sand":
                return new Tag("natural", subclass.equals("beach") ? "beach" : "sand");
            case "rock":
                return new Tag("natural", subclass.equals("scree") ? "scree" : "bare_rock");
            case "farmland":
                return new Tag("landuse", subclass.equals("orchard") || subclass.equals("vineyard") ? subclass : "farmland");
            case "grass":
                switch (subclass) {
                    case "garden":
                    case "flowerbed":
                        return new Tag("leisure", "garden");
                    case "park":
                        return new Tag("leisure", "park");
                    case "golf_course":
                        return new Tag("leisure", "golf_course");
                    case "meadow":
                    case "recreation_ground":
                    case "allotments":
                    case "village_green":
                        return new Tag("landuse", subclass);
                    case "grassland":
                    case "heath":
                    case "scrub":
                    case "fell":
                        return new Tag("natural", subclass);
                    case "shrubbery":
                        return new Tag("natural", "scrub");
                    default:
                        return new Tag("landuse", "grass");
                }
            default:
                return null;
        }
    }

    private static final Map<String, Tag> LANDUSE;

    static {
        Map<String, Tag> result = new HashMap<>();
        for (String value : new String[]{"residential", "commercial", "industrial", "retail", "railway", "garages",
                "quarry", "military", "cemetery", "brownfield", "construction", "landfill"}) {
            result.put(value, new Tag("landuse", value));
        }
        for (String value : new String[]{"school", "kindergarten", "college", "university", "hospital", "library",
                "bus_station", "parking"}) {
            result.put(value, new Tag("amenity", value));
        }
        for (String value : new String[]{"pitch", "playground", "track", "stadium", "dog_park"}) {
            result.put(value, new Tag("leisure", value));
        }
        result.put("zoo", new Tag("tourism", "zoo"));
        result.put("dam", new Tag("waterway", "dam"));
        LANDUSE = Collections.unmodifiableMap(result);
    }

    // Nodes

    /** Tags for a point feature of {@code layer}; null if the theme has no use for it. */
    public static Element node(String layer, Feature feature) {
        String cls = orEmpty(feature.string("class"));
        List<Tag> tags;
        switch (layer) {
            case "poi": {
                String subclass = feature.string("subclass");
                tags = poi(cls, subclass != null ? subclass : cls);
                if (tags.isEmpty()) {
                    return null;
                }
                break;
            }
            case "place":
                if (cls.isEmpty()) {
                    return null;
                }
                tags = list(new Tag("place", cls.equals("quarter") ? "neighbourhood" : cls));
                break;
            case "mountain_peak": {
                tags = list(new Tag("natural", cls.isEmpty() ? "peak" : cls));
                VectorTile.Value ele = feature.get("ele");
                if (ele != null) {
                    tags.add(new Tag("ele", ele.text()));
                }
                break;
            }
            case "aerodrome_label":
                tags = list(new Tag("aeroway", "aerodrome"));
                break;
            case "aeroway": {
                if (cls.isEmpty()) {
                    return null;
                }
                tags = list(new Tag("aeroway", cls));
                String ref = feature.string("ref");
                if (ref != null) {
                    tags.add(new Tag("ref", ref));
                }
                break;
            }
            case "housenumber": {
                VectorTile.Value number = feature.get("housenumber");
                String text = number == null ? null : number.text();
                if (text == null || text.isEmpty()) {
                    return null;
                }
                tags = list(new Tag("addr:housenumber", text));
                break;
            }
            default:
                return null;
        }
        int minZoom = layer.equals("poi") ? poiMinZoom(tags.get(0)) : 0;
        String name = name(feature);
        if (name == null) {
            return new Element(tags, false, minZoom);
        }
        tags.add(new Tag("name", name));
        // The theme names parks, gardens and cemeteries as areas, not as points.
        boolean areaNamed = layer.equals("poi") && !tags.get(0).key.equals("tourism")
                && AREA_NAMED_VALUES.contains(tags.get(0).value);
        return new Element(tags, areaNamed, minZoom);
    }

    /**
     * The tiles hold every POI from zoom 14 on, and the theme would draw some kinds from
     * zoom 13 or 14; a .map file holds most of them back to a closer zoom. These zooms are
     * a guess at that, to keep a town from filling with symbols: what a walker looks for
     * (shelters, huts, camp sites, viewpoints) shows early.
     */
    private static int poiMinZoom(Tag tag) {
        switch (tag.value) {
            case "shelter":
            case "alpine_hut":
            case "camp_site":
            case "caravan_site":
            case "viewpoint":
            case "attraction":
            case "picnic_site":
            case "castle":
            case "ruins":
            case "monument":
            case "station":
            case "bus_station":
                return 0;
            case "hospital":
                return 15;
            default:
                return 16;
        }
    }

    private static final Set<String> AREA_NAMED_VALUES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "park", "garden", "dog_park", "golf_course", "cemetery", "grave_yard", "recreation_ground")));

    /**
     * A lake's or pond's name sits on a point of the {@code water_name} layer; the theme
     * captions the water area. Returned as tags for a label-only way at that point, as
     * {@link #node} does for parks ({@link Element#textOnly}).
     */
    public static List<Tag> waterLabel(Feature feature) {
        String name = name(feature);
        if (feature.type != VectorTile.POINT || name == null) {
            return null;
        }
        List<Tag> tags = new ArrayList<>(2);
        tags.add(new Tag("natural", "water"));
        tags.add(new Tag("name", name));
        return tags;
    }

    private static List<Tag> poi(String cls, String subclass) {
        switch (cls) {
            case "place_of_worship": {
                List<Tag> tags = list(new Tag("amenity", cls));
                if (!subclass.equals(cls)) {
                    tags.add(new Tag("religion", subclass));
                }
                return tags;
            }
            case "pitch": {
                List<Tag> tags = list(new Tag("leisure", cls));
                if (!subclass.isEmpty() && !subclass.equals(cls)) {
                    tags.add(new Tag("sport", subclass));
                }
                return tags;
            }
            case "information": {
                List<Tag> tags = list(new Tag("tourism", cls));
                if (!subclass.isEmpty() && !subclass.equals(cls)) {
                    tags.add(new Tag("information", subclass));
                }
                return tags;
            }
            case "shop":
                return list(new Tag("shop", subclass));
            case "bus":
                return list(subclass.equals("bus_station") ? new Tag("amenity", subclass) : new Tag("highway", "bus_stop"));
            case "railway":
                return list(new Tag("railway", subclass));
            case "lodging":
                return list(new Tag(subclass.equals("dormitory") ? "building" : "tourism", subclass));
            case "stadium":
                return list(new Tag("leisure", cls));
            case "campsite":
                return list(new Tag("tourism", "camp_site"));
            default:
                break;
        }
        String key = POI_KEYS.get(subclass);
        if (key != null) {
            return list(new Tag(key, subclass));
        }
        key = POI_KEYS.get(cls);
        if (key != null) {
            return list(new Tag(key, cls));
        }
        return new ArrayList<>(0);
    }

    /**
     * Which OSM key a POI value belongs to. The tiles keep only the value (as
     * {@code subclass}), so this lists the values tilemaker's OpenMapTiles config collects
     * and the ones the theme draws. No value is listed under two keys.
     */
    static final Map<String, String> POI_KEYS;

    static {
        Map<String, String[]> byKey = new LinkedHashMap<>();
        byKey.put("shop", new String[]{"supermarket", "department_store", "mall", "convenience", "general", "kiosk",
                "bakery", "doityourself", "hardware", "houseware", "bicycle", "organic", "farm", "greengrocer", "cheese",
                "butcher", "seafood", "deli", "laundry", "dry_cleaning", "beverages", "alcohol", "books", "newsagent",
                "car", "car_repair", "car_parts", "motorcycle", "sports", "outdoor"});
        byKey.put("amenity", new String[]{"arts_centre", "atm", "bank", "bar", "bbq", "bench", "bicycle_parking",
                "bicycle_rental", "biergarten", "bus_station", "cafe", "charging_station", "cinema", "clinic", "college",
                "community_centre", "courthouse", "dentist", "doctors", "drinking_water", "embassy", "fast_food",
                "ferry_terminal", "fire_station", "food_court", "fountain", "fuel", "grave_yard", "hospital",
                "ice_cream", "kindergarten", "library", "marketplace", "motorcycle_parking", "nightclub",
                "nursing_home", "parking", "pharmacy", "police", "post_box", "post_office", "prison", "pub",
                "public_building", "recycling", "restaurant", "school", "shelter", "taxi", "telephone", "theatre",
                "toilets", "townhall", "university", "veterinary", "waste_basket", "parcel_locker"});
        byKey.put("tourism", new String[]{"alpine_hut", "aquarium", "artwork", "attraction", "bed_and_breakfast",
                "camp_site", "caravan_site", "chalet", "gallery", "guest_house", "hostel", "hotel", "motel", "museum",
                "picnic_site", "theme_park", "viewpoint", "zoo"});
        byKey.put("leisure", new String[]{"dog_park", "escape_game", "garden", "golf_course", "ice_rink", "marina",
                "miniature_golf", "park", "playground", "sports_centre", "swimming_area", "swimming_pool",
                "water_park", "slipway", "track"});
        byKey.put("historic", new String[]{"monument", "castle", "ruins", "memorial", "archaeological_site"});
        byKey.put("barrier", new String[]{"bollard", "border_control", "cycle_barrier", "gate", "lift_gate",
                "sally_port", "stile", "toll_booth", "block", "chain"});
        byKey.put("highway", new String[]{"bus_stop", "traffic_signals", "turning_circle"});
        byKey.put("railway", new String[]{"halt", "station", "subway_entrance", "tram_stop", "level_crossing",
                "crossing"});
        byKey.put("natural", new String[]{"peak", "volcano", "saddle", "cave_entrance", "spring"});
        byKey.put("man_made", new String[]{"lighthouse", "windmill"});
        byKey.put("landuse", new String[]{"cemetery", "brownfield"});
        byKey.put("waterway", new String[]{"dock", "weir"});
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String[]> entry : byKey.entrySet()) {
            for (String value : entry.getValue()) {
                if (!result.containsKey(value)) {
                    result.put(value, entry.getKey());
                }
            }
        }
        POI_KEYS = Collections.unmodifiableMap(result);
    }

    private static List<Tag> list(Tag first) {
        List<Tag> tags = new ArrayList<>(3);
        tags.add(first);
        return tags;
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean isOne(Long value) {
        return value != null && value == 1;
    }
}
