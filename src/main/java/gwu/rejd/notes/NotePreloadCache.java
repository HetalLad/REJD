package gwu.rejd.notes;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Static cache populated by the Eclipse startup hook so that notes are
 * available immediately when ClassDiagramView initialises, regardless of
 * whether the file-system read has happened yet on that render cycle.
 */
public class NotePreloadCache {

    private static final ConcurrentHashMap<Path, List<NoteModel>> cache =
            new ConcurrentHashMap<>();

    private NotePreloadCache() {}

    public static void put(Path projectRoot, List<NoteModel> notes) {
        cache.put(projectRoot.toAbsolutePath().normalize(),
                  new ArrayList<>(notes));
    }

    /** Returns the cached list, or an empty list if nothing was preloaded. */
    public static List<NoteModel> get(Path projectRoot) {
        return cache.getOrDefault(
                projectRoot.toAbsolutePath().normalize(),
                Collections.emptyList());
    }
}
