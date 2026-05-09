/*
File Name: PositionPreloadCache.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The PositionRepository is initialized here to create a 'repository' of positions.
*/

// Package info
package gwu.rejd.notes;

// Import statements
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads and writes the positions to {projectRoot}/.rejd/rejd-positions.json.
 * Every save writes the full list — no partial writes.
 */
public class PositionRepository {

    private static final String FOLDER    = ".rejd";
    private static final String FILE_NAME = "rejd-positions.json";
    private static final Gson   GSON      = new Gson();

    private PositionRepository() {}

    /**
     * Save all positions to {projectRoot}/.rejd/rrejd-positions.json.
     * Creates the .rejd folder if it does not exist.
     */
    public static void save(Path projectRoot, Map<String, PositionModel> positions) {
        try {
            Path dir = projectRoot.resolve(FOLDER);
            Files.createDirectories(dir);
            Path file = dir.resolve(FILE_NAME);
            Files.write(file, GSON.toJson(positions).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[PositionRepository] save failed: " + e.getMessage());
        }
    }

    /**
     * Load all the positions from {projectRoot}/.rejd/rejd-positions.json.
     * Returns an empty list if the file does not exist.
     */
    public static Map<String, PositionModel> load(Path projectRoot) {
        try {
            Path file = projectRoot.resolve(FOLDER).resolve(FILE_NAME);
            if (!Files.exists(file)) return new HashMap<>();
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Type listType = new TypeToken<Map<String, PositionModel>>() {}.getType();
            Map<String, PositionModel> result = GSON.fromJson(json, listType);
            return result != null ? result : new HashMap<>();
        } catch (IOException e) {
            System.err.println("[PositionRepository] load failed: " + e.getMessage());
            return new HashMap<>();
        }
    }
}
