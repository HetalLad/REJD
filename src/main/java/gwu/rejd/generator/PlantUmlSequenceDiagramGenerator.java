/*
 * File Name: PlantUmlSequenceDiagramGenerator.java
 * Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
 * Description: Generates PlantUML sequence diagrams by walking
 * Java AST method calls and building participant interactions.
 */

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
	
	private static final int MAX_CALL_DEPTH = 3;

    /**
	 * Generates a PlantUML sequence diagram for the selected method.
	 * Walks the AST to collect method calls and participant interactions.
	 */
	
    public String generate(ProjectModel projectModel, CompilationUnit cu, String methodId) {
        // Extract the owner type from the methodId
        int hash = methodId.indexOf('#');
        if (hash < 0) throw new IllegalArgumentException("Invalid methodId: " + methodId);
        String ownerFqn = methodId.substring(0, hash);
        String callerSimple = simpleNameOf(ownerFqn);

        TypeModel ownerType = projectModel.getTypesByFqn().get(ownerFqn);
        if (ownerType == null) {
            throw new IllegalArgumentException("No TypeModel found for: " + ownerFqn);
        }

        // Make sure the method exists in the parsed model
        boolean found = ownerType.getMethods().stream()
                .anyMatch(m -> m.getMethodId().equals(methodId));
        if (!found) {
            throw new IllegalArgumentException("No MethodModel found for methodId: " + methodId);
        }

        // Find the matching MethodDeclaration in the AST
        MethodDeclaration targetDecl = findMethodDeclaration(cu, ownerFqn, methodId, projectModel);
        if (targetDecl == null || targetDecl.getBody() == null) {
            throw new IllegalArgumentException(
                    "MethodDeclaration not found or has no body for: " + methodId);
        }

        // Build a map of local variables and their declared types
        Map<String, String> localVars = new LinkedHashMap<>();
        // Seed with method parameters 
        for (Object p : targetDecl.parameters()) {
            SingleVariableDeclaration svd = (SingleVariableDeclaration) p;
            localVars.put(svd.getName().getIdentifier(), baseTypeName(svd.getType().toString()));
        }
        // Then collect local variables declared inside the method body
        LocalVarCollector varCollector = new LocalVarCollector();
        targetDecl.getBody().accept(varCollector);
        localVars.putAll(varCollector.varTypes);

        // Collect method calls from the AST
        CallCollector collector = new CallCollector();
        targetDecl.getBody().accept(collector);

        // Used for resolving callees to known project types
        Set<String> knownSimpleNames = new LinkedHashSet<>();
        for (TypeModel t : projectModel.getTypesByFqn().values()) {
            knownSimpleNames.add(t.getSimpleName());
        }

        // Build PlantUML output
        StringBuilder sb = new StringBuilder();
        sb.append("@startuml\n\n");

        // Collect all participants involved in the sequence flow
        Set<String> participants = new LinkedHashSet<>();
        participants.add(callerSimple);
        for (CallRecord call : collector.calls) {
            String callee = resolveCallee(call.rawCallee, knownSimpleNames, localVars, callerSimple);
            participants.add(callee != null ? callee : "External");
        }

        sb.append("actor User\n");
        for (String p : participants) {
            sb.append("participant ").append(p).append("\n");
        }
        sb.append("\n");

        // Simulated entry point into the sequence
        String entryMethodName = methodId.substring(hash + 1, methodId.indexOf('('));
        sb.append("User -> ").append(callerSimple).append(" : ").append(entryMethodName).append("()\n");
        sb.append("activate ").append(callerSimple).append("\n\n");

     // Render nested method calls recursively
        emitCallsRecursive(
                sb,
                callerSimple,
                collector.calls,
                knownSimpleNames,
                localVars,
                cu,
                0,
                new LinkedHashSet<>()
        );
        
        sb.append("\ndeactivate ").append(callerSimple).append("\n");
        sb.append("\n@enduml\n");
        return sb.toString();
    }

    // AST visitors used for collecting method calls and local variables
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
         * full traversal into all nested blocks.
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

    // Helper methods

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

        // Self-calls stay within the current participant
        if ("this".equals(rawCallee) || "super".equals(rawCallee)) return callerSimple;

        // Resolve using locally declared variable types first
        if (localVars.containsKey(rawCallee)) return localVars.get(rawCallee);

        // Direct simple name match (static call or type name in the model)
        if (knownSimpleNames.contains(rawCallee)) return rawCallee;

        // Capitalise first letter — covers field names matching a type
        String capitalised = Character.toUpperCase(rawCallee.charAt(0)) + rawCallee.substring(1);
        if (knownSimpleNames.contains(capitalised)) return capitalised;

        // Qualified expression — take the last segment and try 
        if (rawCallee.contains(".")) {
            String last = rawCallee.substring(rawCallee.lastIndexOf('.') + 1);
            if (localVars.containsKey(last)) return localVars.get(last);
            if (knownSimpleNames.contains(last)) return last;
            String capLast = Character.toUpperCase(last.charAt(0)) + last.substring(1);
            if (knownSimpleNames.contains(capLast)) return capLast;
        }

        return null; // unrecognised — skip
    }
    
    private void emitCallsRecursive(
            StringBuilder sb,
            String caller,
            List<CallRecord> calls,
            Set<String> knownSimpleNames,
            Map<String, String> localVars,
            CompilationUnit cu,
            int depth,
            Set<String> visitedMethods) {

        if (depth >= MAX_CALL_DEPTH) {
            return;
        }

        for (CallRecord call : calls) {
            String callee = resolveCallee(call.rawCallee, knownSimpleNames, localVars, caller);

            if (callee == null) {
                callee = "External";
            }

            if (call.label.startsWith("new ")) {
                sb.append("create ").append(callee).append("\n");
                sb.append(caller).append(" -> ").append(callee)
                  .append(" : <<create>> ").append(call.label).append("\n");
            } else {
                sb.append(caller).append(" -> ").append(callee)
                  .append(" : ").append(call.label).append("\n");
            }

            sb.append("activate ").append(callee).append("\n");

            MethodDeclaration calleeDecl = findMethodBySimpleName(cu, callee, call.label);

            if (calleeDecl != null && calleeDecl.getBody() != null) {
                String visitKey = callee + "#" + call.label;

                if (!visitedMethods.contains(visitKey)) {
                    visitedMethods.add(visitKey);

                    Map<String, String> nestedLocalVars = buildLocalVarMap(calleeDecl);

                    CallCollector nestedCollector = new CallCollector();
                    calleeDecl.getBody().accept(nestedCollector);

                    emitCallsRecursive(
                            sb,
                            callee,
                            nestedCollector.calls,
                            knownSimpleNames,
                            nestedLocalVars,
                            cu,
                            depth + 1,
                            visitedMethods
                    );
                }
            }

            sb.append("deactivate ").append(callee).append("\n");
        }
    }

    private MethodDeclaration findMethodBySimpleName(
            CompilationUnit cu,
            String typeSimpleName,
            String callLabel) {

        if (callLabel == null || callLabel.startsWith("new ")) {
            return null;
        }

        String methodName = callLabel;

        if (methodName.endsWith("()")) {
            methodName = methodName.substring(0, methodName.length() - 2);
        }

        for (Object t : cu.types()) {
            if (t instanceof TypeDeclaration) {
                TypeDeclaration td = (TypeDeclaration) t;

                if (!td.getName().getIdentifier().equals(typeSimpleName)) {
                    continue;
                }

                for (MethodDeclaration md : td.getMethods()) {
                    if (!md.isConstructor()
                            && md.getName().getIdentifier().equals(methodName)) {
                        return md;
                    }
                }
            }
        }

        return null;
    }

    private Map<String, String> buildLocalVarMap(MethodDeclaration methodDecl) {
        Map<String, String> vars = new LinkedHashMap<>();

        for (Object p : methodDecl.parameters()) {
            SingleVariableDeclaration svd = (SingleVariableDeclaration) p;
            vars.put(svd.getName().getIdentifier(), baseTypeName(svd.getType().toString()));
        }

        LocalVarCollector varCollector = new LocalVarCollector();
        methodDecl.getBody().accept(varCollector);
        vars.putAll(varCollector.varTypes);

        return vars;
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
