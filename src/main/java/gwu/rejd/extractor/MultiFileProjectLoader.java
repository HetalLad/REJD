package gwu.rejd.extractor;

import gwu.rejd.model.PackageNode;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class MultiFileProjectLoader {

    public ProjectModel loadProject(String projectId, Path rootDirectory) throws IOException {
        ProjectModelBuilder builder = new ProjectModelBuilder();

        Map<String, TypeModel> mergedTypes = new LinkedHashMap<>();
        LinkedHashSet<String> mergedImports = new LinkedHashSet<>();
        PackageNode mergedRoot = new PackageNode("");

        try (Stream<Path> pathStream = Files.walk(rootDirectory)) {
            List<Path> javaFiles = pathStream
                    .filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".java"))
                    .sorted()
                    .toList();

            for (Path javaFile : javaFiles) {
                String source = Files.readString(javaFile, StandardCharsets.UTF_8);
                CompilationUnit cu = parseCompilationUnit(source);

                ProjectModel singleFileModel = builder.build(projectId, cu);

                mergedImports.addAll(singleFileModel.getImports());

                for (Map.Entry<String, TypeModel> entry : singleFileModel.getTypesByFqn().entrySet()) {
                    mergedTypes.put(entry.getKey(), entry.getValue());
                }
            }
        }

        for (TypeModel type : mergedTypes.values()) {
            addToPackageTree(mergedRoot, type.getPackageName(), type.getFqn());
        }

        String dominantPackage = "";
        if (!mergedTypes.isEmpty()) {
            dominantPackage = mergedTypes.values().iterator().next().getPackageName();
        }

        return new ProjectModel(
                projectId,
                dominantPackage,
                new ArrayList<>(mergedImports),
                mergedRoot,
                mergedTypes
        );
    }

    private CompilationUnit parseCompilationUnit(String source) {
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

        return (CompilationUnit) parser.createAST(null);
    }

    private void addToPackageTree(PackageNode root, String packageName, String typeFqn) {
        PackageNode node = root;

        if (packageName != null && !packageName.isBlank()) {
            for (String segment : packageName.split("\\.")) {
                node = node.getOrCreateChild(segment);
            }
        }

        node.addType(typeFqn);
    }
}