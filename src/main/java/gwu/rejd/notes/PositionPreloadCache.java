package gwu.rejd.notes;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PositionPreloadCache {

    private static final ConcurrentHashMap<Path, Map<String, PositionModel>> cache =
            new ConcurrentHashMap<>();

    private PositionPreloadCache() {}

    public static void put(Path projectRoot, Map<String, PositionModel> positions) {
        cache.put(projectRoot.toAbsolutePath().normalize(),
                  new HashMap<>(positions));
    }

    /** Returns the cached hash map, or an empty map if nothing was preloaded. */
    public static Map<String, PositionModel> get(Path projectRoot) {
        return cache.getOrDefault(
                projectRoot.toAbsolutePath().normalize(),
                Collections.emptyMap());
    }

    /** Removes and returns the cached hashmap (one-shot). Returns empty map if absent. */
    public static Map<String, PositionModel> drain(Path projectRoot) {
        Map<String, PositionModel>result = cache.remove(projectRoot.toAbsolutePath().normalize());
        return result != null ? result : Collections.emptyMap();
    }

    public static boolean has(Path projectRoot) {
        return cache.containsKey(projectRoot.toAbsolutePath().normalize());
    }
}
