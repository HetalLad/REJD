package gwu.rejd.generator;

import net.sourceforge.plantuml.SourceStringReader;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

public class DiagramRenderer {

    /**
     * Renders a PlantUML diagram string to a PNG file at the given path.
     *
     * @param plantUmlString valid PlantUML source (e.g. from PlantUmlClassDiagramGenerator)
     * @param outputPath     destination path for the generated PNG
     * @throws IOException if the file cannot be written or PlantUML rendering fails
     */
    public void render(String plantUmlString, Path outputPath) throws IOException {
        try (OutputStream out = new FileOutputStream(outputPath.toFile())) {
            SourceStringReader reader = new SourceStringReader(plantUmlString);
            reader.outputImage(out);
        }
    }
}
