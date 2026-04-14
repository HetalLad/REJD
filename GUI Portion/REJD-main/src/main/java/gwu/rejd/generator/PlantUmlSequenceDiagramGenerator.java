package gwu.rejd.generator;

import gwu.rejd.model.MethodModel;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.TypeModel;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PlantUmlSequenceDiagramGenerator {

    /**
     * Generates a PlantUML sequence diagram for the method identified by {@code methodId}.
     *
     * @param projectModel the project model (used to locate the owning type and known types)
     * @param cu           the CompilationUnit AST from which the method body is walked
     * @param methodId     method identifier in the form {@code ownerFqn#name(ParamType,...):ReturnType}
     *                     (constructors use {@code <init>} and no return suffix)
     * @return valid PlantUML sequence diagram string
     * @throws IllegalArgumentException if no matching method is found
     */
    public String generate(ProjectModel projectModel, CompilationUnit cu, String methodId) {
        // --- Locate the owning type simple name from the methodId ---
        int hash = methodId.indexOf('#');
        if (hash < 0) throw new IllegalArgumentException("Invalid methodId: " + methodId);
        String ownerFqn = methodId.substring(0, hash);
        String callerSimple = simpleNameOf(ownerFqn);

        TypeModel ownerType = projectModel.getTypesByFqn().get(ownerFqn);
        if (ownerType == null) {
            throw new IllegalArgumentException("No TypeModel found for: " + ownerFqn);
        }

        // Verify the methodId exists in the model
        boolean found = ownerType.getMethods().stream()
                .anyMatch(m -> m.getMethodId().equals(methodId));
        if (!found) {
            throw new IllegalArgumentException("No MethodModel found for methodId: " + methodId);
        }

        // --- Find the MethodDeclaration in the AST ---
        MethodDeclaration targetDecl = findMethodDeclaration(cu, ownerFqn, methodId, projectModel);
        if (targetDecl == null || targetDecl.getBody() == null) {
            throw new IllegalArgumentException(
                    "MethodDeclaration not found or has no body for: " + methodId);
        }

        // --- Build local variable map: varName -> declared base type ---
        Map<String, String> localVars = new LinkedHashMap<>();
        // Seed with method parameters first
        for (Object p : targetDecl.parameters()) {
            SingleVariableDeclaration svd = (SingleVariableDeclaration) p;
            localVars.put(svd.getName().getIdentifier(), baseTypeName(svd.getType().toString()));
        }
        // Then walk the body for local variable declarations
        LocalVarCollector varCollector = new LocalVarCollector();
        targetDecl.getBody().accept(varCollector);
        localVars.putAll(varCollector.varTypes);

        // --- Collect calls from the method body ---
        CallCollector collector = new CallCollector();
        targetDecl.getBody().accept(collector);

        // --- Build set of known simple names for callee resolution ---
        Set<String> knownSimpleNames = new LinkedHashSet<>();
        for (TypeModel t : projectModel.getTypesByFqn().values()) {
            knownSimpleNames.add(t.getSimpleName());
        }

        // --- Emit PlantUML ---
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n\n");

        // Collect participants: caller, then all callees (User is emitted separately as an actor)
        Set<String> participants = new LinkedHashSet<>();
        participants.add(callerSimple);
        for (CallRecord call : collector.calls) {
            String callee = resolveCallee(call.rawCallee, knownSimpleNames, localVars, callerSimple);
            if (callee != null) participants.add(callee);
        }

        sb.append("actor User\n");
        for (String p : participants) {
            sb.append("participant ").append(p).append("\n");
        }
        sb.append("\n");

        // Entry call from User
        String entryMethodName = methodId.substring(hash + 1, methodId.indexOf('('));
        sb.append("User -> ").append(callerSimple).append(" : ").append(entryMethodName).append("()\n");
        sb.append("activate ").append(callerSimple).append("\n\n");

        // Each collected call
        for (CallRecord call : collector.calls) {
            String callee = resolveCallee(call.rawCallee, knownSimpleNames, localVars, callerSimple);
            if (callee == null) continue; // skip calls to unrecognised / external types

            sb.append(callerSimple).append(" -> ").append(callee)
              .append(" : ").append(call.label).append("\n");
            sb.append("activate ").append(callee).append("\n");
            sb.append("deactivate ").append(callee).append("\n");
        }

        sb.append("\ndeactivate ").append(callerSimple).append("\n");
        sb.append("\n@enduml\n");
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // AST visitor — collects MethodInvocation and ClassInstanceCreation nodes
    // -------------------------------------------------------------------------

    private static class CallRecord {
        final String rawCallee; // expression string as written in source
        final String label;     // display label for the arrow

        CallRecord(String rawCallee, String label) {
            this.rawCallee = rawCallee;
            this.label = label;
        }
    }

    /** Walks VariableDeclarationStatement nodes and maps each variable name to its base type. */
    private static class LocalVarCollector extends ASTVisitor {
        final Map<String, String> varTypes = new LinkedHashMap<>();

        @Override
        public void preVisit(ASTNode node) {
            if (node instanceof VariableDeclarationStatement) {
                VariableDeclarationStatement vds = (VariableDeclarationStatement) node;
                String baseType = baseTypeName(vds.getType().toString());
                for (Object frag : vds.fragments()) {
                    VariableDeclarationFragment vdf = (VariableDeclarationFragment) frag;
                    varTypes.put(vdf.getName().getIdentifier(), baseType);
                }
            }
        }
    }

    private static class CallCollector extends ASTVisitor {
        final List<CallRecord> calls = new ArrayList<>();

        /**
         * preVisit is called unconditionally for every node in the AST,
         * regardless of what any visit() method returns. This guarantees
         * full traversal into all nested blocks (if, for, try, etc.).
         */
        @Override
        public void preVisit(ASTNode node) {
            if (node instanceof MethodInvocation) {
                MethodInvocation mi = (MethodInvocation) node;
                String callee = mi.getExpression() != null
                        ? mi.getExpression().toString()
                        : "this";
                calls.add(new CallRecord(callee, mi.getName().getIdentifier() + "()"));
            } else if (node instanceof ClassInstanceCreation) {
                ClassInstanceCreation cic = (ClassInstanceCreation) node;
                String typeName = baseTypeName(cic.getType().toString());
                calls.add(new CallRecord(typeName, "new " + typeName + "()"));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Finds the MethodDeclaration in the CompilationUnit whose reconstructed
     * methodId matches the given one.
     */
    private MethodDeclaration findMethodDeclaration(
            CompilationUnit cu, String ownerFqn, String methodId, ProjectModel projectModel) {

        // Walk all type declarations in the CU to find the one matching ownerFqn
        for (Object t : cu.types()) {
            MethodDeclaration md = searchTypeForMethod((AbstractTypeDeclaration) t,
                    ownerFqn, methodId, projectModel);
            if (md != null) return md;
        }
        return null;
    }

    private MethodDeclaration searchTypeForMethod(
            AbstractTypeDeclaration typeDecl, String ownerFqn,
            String methodId, ProjectModel projectModel) {

        TypeModel typeModel = projectModel.getTypesByFqn().get(ownerFqn);
        if (typeModel == null) return null;

        if (typeDecl instanceof TypeDeclaration) {
            TypeDeclaration td = (TypeDeclaration) typeDecl;
            String simple = td.getName().getIdentifier();
            if (simple.equals(typeModel.getSimpleName())) {
                for (MethodDeclaration md : td.getMethods()) {
                    if (matchesMethodId(md, ownerFqn, methodId)) return md;
                }
            }
        } else if (typeDecl instanceof EnumDeclaration) {
            EnumDeclaration ed = (EnumDeclaration) typeDecl;
            String simple = ed.getName().getIdentifier();
            if (simple.equals(typeModel.getSimpleName())) {
                for (Object bd : ed.bodyDeclarations()) {
                    if (bd instanceof MethodDeclaration) {
                        MethodDeclaration md = (MethodDeclaration) bd;
                        if (matchesMethodId(md, ownerFqn, methodId)) return md;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Reconstructs the methodId from a MethodDeclaration and checks equality.
     * Mirrors the logic in ProjectModelBuilder#buildMethodId.
     */
    private boolean matchesMethodId(MethodDeclaration md, String ownerFqn, String methodId) {
        boolean isCtor = md.isConstructor();
        String name = isCtor ? "<init>" : md.getName().getIdentifier();
        String returnType = isCtor ? "" : (md.getReturnType2() == null ? "void" : md.getReturnType2().toString());

        StringBuilder sb = new StringBuilder();
        sb.append(ownerFqn).append("#").append(name).append("(");
        boolean first = true;
        for (Object p : md.parameters()) {
            if (!first) sb.append(",");
            SingleVariableDeclaration svd = (SingleVariableDeclaration) p;
            sb.append(svd.getType().toString()).append(svd.isVarargs() ? "..." : "");
            first = false;
        }
        sb.append(")");
        if (!isCtor) sb.append(":").append(returnType);
        return sb.toString().equals(methodId);
    }

    /**
     * Resolves a raw callee expression to a type name for the sequence diagram.
     * Checks the local variable map first, then falls back to name heuristics.
     */
    private String resolveCallee(String rawCallee, Set<String> knownSimpleNames,
                                  Map<String, String> localVars, String callerSimple) {
        if (rawCallee == null || rawCallee.isBlank()) return null;

        // "this" or implicit self-call
        if ("this".equals(rawCallee) || "super".equals(rawCallee)) return callerSimple;

        // Local variable map — resolves e.g. "found" → "Optional", "book" → "Book"
        if (localVars.containsKey(rawCallee)) return localVars.get(rawCallee);

        // Direct simple name match (static call or type name in the model)
        if (knownSimpleNames.contains(rawCallee)) return rawCallee;

        // Capitalise first letter — covers field names matching a type (e.g. "library" → "Library")
        String capitalised = Character.toUpperCase(rawCallee.charAt(0)) + rawCallee.substring(1);
        if (knownSimpleNames.contains(capitalised)) return capitalised;

        // Qualified expression — take the last segment and try (e.g. "this.library" → "Library")
        if (rawCallee.contains(".")) {
            String last = rawCallee.substring(rawCallee.lastIndexOf('.') + 1);
            if (localVars.containsKey(last)) return localVars.get(last);
            if (knownSimpleNames.contains(last)) return last;
            String capLast = Character.toUpperCase(last.charAt(0)) + last.substring(1);
            if (knownSimpleNames.contains(capLast)) return capLast;
        }

        return null; // unrecognised — skip
    }

    private static String baseTypeName(String type) {
        int lt = type.indexOf('<');
        return lt >= 0 ? type.substring(0, lt).trim() : type.trim();
    }

    private String simpleNameOf(String fqn) {
        int dot = fqn.lastIndexOf('.');
        return dot >= 0 ? fqn.substring(dot + 1) : fqn;
    }
}
