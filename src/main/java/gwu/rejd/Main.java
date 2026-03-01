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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the REJD AST Parser.
 *
 * Reads a Java source file, parses it with Eclipse JDT, captures any
 * errors/warnings, and writes the full AST parse tree to "parsedtree"
 * in the project root directory.
 *
 * Usage (Maven):
 * mvn exec:java <- uses src/samples/Sample.java
 * mvn exec:java -Dexec.args="path/to/YourFile.java" <- custom input
 *
 * Usage (jar):
 * java -cp ... gwu.rejd.Main [path/to/YourFile.java]
 */
public class Main {

    /** Output file written to project root. */
    private static final String OUTPUT_FILE = "parsedtree";

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
        parser.setResolveBindings(false); // standalone: no classpath available
        parser.setStatementsRecovery(true); // produce partial tree even on errors
        parser.setBindingsRecovery(false);

        // --- Parse ---
        CompilationUnit cu = (CompilationUnit) parser.createAST(null);

        // --- Collect diagnostics ---
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (IProblem problem : cu.getProblems()) {
            String location = "Line " + problem.getSourceLineNumber();
            String msg = "[" + location + "] " + problem.getMessage();
            if (problem.isError()) {
                errors.add(msg);
            } else {
                // IProblem has no isWarning(); everything non-error is a warning/info
                warnings.add(msg);
            }
        }

        // --- Build the output ---
        StringBuilder out = new StringBuilder();
        writeHeader(out, filePath, source, errors, warnings);

        out.append("\n");
        separator(out, "FULL AST PARSE TREE");
        out.append("\n");

        ASTTreePrinter printer = new ASTTreePrinter(out, cu);
        cu.accept(printer);

        // --- Write to "parsedtree" in project root ---
        Path outputFile = Paths.get(OUTPUT_FILE).toAbsolutePath();
        Files.write(outputFile, out.toString().getBytes(StandardCharsets.UTF_8));

        System.out.println("Output  : " + outputFile);
        System.out.printf("Result  : %d error(s), %d warning(s)%n",
                errors.size(), warnings.size());

        if (!errors.isEmpty()) {
            System.out.println("\nParse errors:");
            errors.forEach(e -> System.out.println("  " + e));
        }
    }

    // -------------------------------------------------------------------------
    // Output helpers
    // -------------------------------------------------------------------------

    private static void writeHeader(StringBuilder out, Path filePath, String source,
            List<String> errors, List<String> warnings) {
        separator(out, "REJD — JAVA AST PARSE TREE REPORT");
        out.append("\n");
        out.append("  Source    : ").append(filePath).append("\n");
        out.append("  Generated : ").append(new Date()).append("\n");
        out.append("  Parser    : Eclipse JDT  (JLS latest)\n");
        out.append("  Lines     : ").append(source.split("\n", -1).length).append("\n");
        out.append("  Characters: ").append(source.length()).append("\n");
        out.append("\n");

        if (errors.isEmpty() && warnings.isEmpty()) {
            out.append("  DIAGNOSTICS: No errors or warnings.\n");
        } else {
            if (!errors.isEmpty()) {
                out.append("  ERRORS (").append(errors.size()).append("):\n");
                for (String e : errors) {
                    out.append("    [ERROR] ").append(e).append("\n");
                }
            }
            if (!warnings.isEmpty()) {
                out.append("  WARNINGS (").append(warnings.size()).append("):\n");
                for (String w : warnings) {
                    out.append("    [WARN]  ").append(w).append("\n");
                }
            }
        }
    }

    private static void separator(StringBuilder out, String title) {
        String line = "=".repeat(64);
        out.append(line).append("\n");
        out.append("  ").append(title).append("\n");
        out.append(line).append("\n");
    }
}
