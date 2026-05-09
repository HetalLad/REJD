/*
 * File Name: DiagramRenderer.java
 * Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
 * Description: Handles rendering PlantUML source strings
 * into PNG diagram images.
 */

package gwu.rejd.generator;

import net.sourceforge.plantuml.SourceStringReader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Utility class for converting generated PlantUML
 * text into diagram image files.
 */
public class DiagramRenderer {

    /**
     * Renders the generated PlantUML text as a PNG diagram.
     */
    public void render(String plantUmlString, Path outputPath) throws IOException {
        try (OutputStream out = new FileOutputStream(outputPath.toFile())) {
            SourceStringReader reader = new SourceStringReader(plantUmlString);
            reader.outputImage(out);
        }
    }
}
