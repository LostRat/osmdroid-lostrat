package org.osmdroid.mapsforge.mvt;

import org.mapsforge.core.model.Tag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shares {@link Tag} instances and identical tag lists between ways: thousands of
 * buildings carry the same {@code building=yes}. Tags of a fixed vocabulary (keys and
 * values from the mapping tables) are shared across tiles; free text (names, refs, house
 * numbers) only within one tile, so the shared tables stay small. Returned lists are
 * unmodifiable and safe to share.
 */
final class TagInterner {

    /** Shared tables stop growing at this size; lookups still hit, new entries stay unshared. */
    private static final int MAX_SHARED = 8192;

    private final ConcurrentHashMap<Tag, Tag> sharedTags = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<List<Tag>, List<Tag>> sharedLists = new ConcurrentHashMap<>();

    /** Interning state for the conversion of one tile; not thread-safe, used by one thread. */
    final class Local {
        private final Map<Tag, Tag> tags = new HashMap<>();
        private final Map<List<Tag>, List<Tag>> lists = new HashMap<>();

        List<Tag> intern(List<Tag> raw) {
            boolean freeText = false;
            for (int i = 0; i < raw.size(); i++) {
                if (isFreeText(raw.get(i))) {
                    freeText = true;
                    break;
                }
            }
            if (!freeText) {
                List<Tag> shared = sharedLists.get(raw);
                if (shared != null) {
                    return shared;
                }
            } else {
                List<Tag> local = lists.get(raw);
                if (local != null) {
                    return local;
                }
            }
            List<Tag> copy = new ArrayList<>(raw.size());
            for (int i = 0; i < raw.size(); i++) {
                copy.add(tag(raw.get(i)));
            }
            List<Tag> result = Collections.unmodifiableList(copy);
            if (!freeText) {
                if (sharedLists.size() < MAX_SHARED) {
                    sharedLists.put(result, result);
                }
            } else {
                lists.put(result, result);
            }
            return result;
        }

        private Tag tag(Tag tag) {
            if (isFreeText(tag)) {
                Tag local = tags.get(tag);
                if (local == null) {
                    tags.put(tag, tag);
                    return tag;
                }
                return local;
            }
            Tag shared = sharedTags.get(tag);
            if (shared != null) {
                return shared;
            }
            if (sharedTags.size() < MAX_SHARED) {
                sharedTags.put(tag, tag);
            }
            return tag;
        }
    }

    Local local() {
        return new Local();
    }

    private static boolean isFreeText(Tag tag) {
        String key = tag.key;
        return key.equals("name") || key.equals("ref") || key.equals("addr:housenumber");
    }
}
