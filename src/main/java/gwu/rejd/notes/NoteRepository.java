/*
File name: NoteRepository.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag 
Description: The NoteRepository is initalized here to maintain a 'repository' of notes.
*/

// Package Statement
package gwu.rejd.notes;

// Import statements
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and writes notes to {projectRoot}/.rejd/rejd-notes.json.
 * Every save writes the full list — no partial writes.
 */
public class NoteRepository {

    private static final String FOLDER    = ".rejd";
    private static final String FILE_NAME = "rejd-notes.json";
    private static final Gson   GSON      = new Gson();

    private NoteRepository() {}

    /**
     * Save all notes to {projectRoot}/.rejd/rejd-notes.json.
     * Creates the .rejd folder if it does not exist.
     */
    public static void save(Path projectRoot, List<NoteModel> notes) {
        try {
            Path dir = projectRoot.resolve(FOLDER);
            Files.createDirectories(dir);
            Path file = dir.resolve(FILE_NAME);
            Files.write(file, GSON.toJson(notes).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            System.err.println("[NoteRepository] save failed: " + e.getMessage());
        }
    }

    /**
     * Load all notes from {projectRoot}/.rejd/rejd-notes.json.
     * Returns an empty list if the file does not exist.
     */
    public static List<NoteModel> load(Path projectRoot) {
        try {
            Path file = projectRoot.resolve(FOLDER).resolve(FILE_NAME);
            if (!Files.exists(file)) return new ArrayList<>();
            String json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            Type listType = new TypeToken<List<NoteModel>>() {}.getType();
            List<NoteModel> result = GSON.fromJson(json, listType);
            return result != null ? result : new ArrayList<>();
        } catch (IOException e) {
            System.err.println("[NoteRepository] load failed: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
