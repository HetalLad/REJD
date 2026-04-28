package gwu.rejd;

import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import gwu.rejd.extractor.ProjectModelBuilder;
import gwu.rejd.model.ProjectModel;

/**
 * CLI entry point for the REJD AST Parser.
 *
 * Reads a Java source file, parses it with Eclipse JDT, and prints
 * a summary of the parsed model (package, imports, types) to stdout.
 *
 * Usage (Maven):
 *   mvn exec:java                                      <- uses src/samples/Sample.java
 *   mvn exec:java -Dexec.args="path/to/YourFile.java" <- custom input
 *
 * Usage (jar):
 *   java -cp ... gwu.rejd.Main [path/to/YourFile.java]
 *
 * For full diagram generation use the standalone GUI: mvn javafx:run
 * For the Eclipse plugin see: GUI Portion/plugin/
 */
public class Main {

    /** Default input if no argument is supplied. */
    private static final String DEFAULT_INPUT = "src/samples/Sample.java";

    public static void main(String[] args) throws IOException {
        String inputPath = (args.length > 0) ? args[0] : DEFAULT_INPUT;
        Path filePath = Paths.get(inputPath).toAbsolutePath();

        if (!Files.exists(filePath)) {
            System.err.println("[ERROR] Input file not found: " + filePath);
            System.err.println("Usage: mvn exec:java -Dexec.args=\"path/to/YourFile.java\"");
            System.exit(1);
        }

        System.out.println("Parsing : " + filePath);

        // --- Read source ---
        String source = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);

        // --- Configure Eclipse JDT ASTParser ---
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        Map<String, String> options = JavaCore.getOptions();
        options.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_11);
        options.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_11);
        options.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_11);
        parser.setCompilerOptions(options);

        parser.setSource(source.toCharArray());
        parser.setResolveBindings(false);
        parser.setStatementsRecovery(true);
        parser.setBindingsRecovery(false);

        // --- Parse ---
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        ProjectModelBuilder b = new ProjectModelBuilder();
        ProjectModel model = b.build("rejd-demo", cu);

        // --- Print summary ---
        System.out.println("----- PROJECT MODEL -----");
        System.out.println("Package : " + model.getPackageName());
        System.out.println("Imports : " + model.getImports().size());
        model.getImports().forEach(i -> System.out.println("  " + i));
        System.out.println("Types   : " + model.getTypesByFqn().size());
        model.getTypesByFqn().keySet().forEach(fqn -> System.out.println("  " + fqn));
        System.out.println("-------------------------");

        // --- Print any parse errors/warnings ---
        int errors = 0, warnings = 0;
        for (IProblem problem : cu.getProblems()) {
            String location = "Line " + problem.getSourceLineNumber();
            if (problem.isError()) {
                System.err.println("  [ERROR] [" + location + "] " + problem.getMessage());
                errors++;
            } else {
                System.out.println("  [WARN]  [" + location + "] " + problem.getMessage());
                warnings++;
            }
        }
        System.out.printf("Result  : %d error(s), %d warning(s)%n", errors, warnings);
    }
}
