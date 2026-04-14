package gwu.rejd;

import org.eclipse.jdt.core.dom.*;

import java.util.List;
import java.util.StringJoiner;

/**
 * An ASTVisitor that prints a full indented parse tree to a StringBuilder.
 *
 * Strategy:
 * - preVisit() / postVisit() are called for EVERY node, making them ideal
 * for depth tracking and printing the raw tree.
 * - For key structural nodes (TypeDeclaration, MethodDeclaration, etc.) a
 * human-readable summary label is added (name, type, modifiers, etc.).
 * - All other nodes fall back to their simple class name.
 *
 * This gives a complete parse tree suitable for understanding the code
 * structure and as input for UML diagram generation.
 */
public class ASTTreePrinter extends ASTVisitor {

    private final StringBuilder out;
    private final CompilationUnit cu;
    private int depth = 0;

    public ASTTreePrinter(StringBuilder out, CompilationUnit cu) {
        this.out = out;
        this.cu = cu;
    }

    // -------------------------------------------------------------------------
    // Core: print every node via preVisit / postVisit
    // -------------------------------------------------------------------------

    @Override
    public void preVisit(ASTNode node) {
        int line = cu.getLineNumber(node.getStartPosition());
        String label = describeNode(node);
        indent();
        out.append(label);
        out.append("  [L").append(line).append("]");
        out.append("\n");
        depth++;
    }

    @Override
    public void postVisit(ASTNode node) {
        depth--;
    }

    // -------------------------------------------------------------------------
    // Node description: return a readable label for each node type
    // -------------------------------------------------------------------------

    private String describeNode(ASTNode node) {
        if (node instanceof CompilationUnit) {
            return "CompilationUnit";
        }
        if (node instanceof PackageDeclaration) {
            PackageDeclaration pd = (PackageDeclaration) node;
            return "Package: " + pd.getName().getFullyQualifiedName();
        }
        if (node instanceof ImportDeclaration) {
            ImportDeclaration id = (ImportDeclaration) node;
            String name = id.getName().getFullyQualifiedName();
            if (id.isOnDemand())
                name += ".*";
            return "Import: " + (id.isStatic() ? "static " : "") + name;
        }
        if (node instanceof TypeDeclaration) {
            return describeType((TypeDeclaration) node);
        }
        if (node instanceof EnumDeclaration) {
            EnumDeclaration ed = (EnumDeclaration) node;
            String mods = modString(ed.getModifiers());
            return "Enum: " + (mods.isEmpty() ? "" : mods + " ") + ed.getName().getIdentifier();
        }
        if (node instanceof EnumConstantDeclaration) {
            EnumConstantDeclaration ec = (EnumConstantDeclaration) node;
            return "EnumConstant: " + ec.getName().getIdentifier();
        }
        if (node instanceof AnnotationTypeDeclaration) {
            AnnotationTypeDeclaration atd = (AnnotationTypeDeclaration) node;
            return "@interface: " + atd.getName().getIdentifier();
        }
        if (node instanceof MethodDeclaration) {
            return describeMethod((MethodDeclaration) node);
        }
        if (node instanceof FieldDeclaration) {
            return describeField((FieldDeclaration) node);
        }
        if (node instanceof SingleVariableDeclaration) {
            SingleVariableDeclaration svd = (SingleVariableDeclaration) node;
            return "Param: " + svd.getType() + (svd.isVarargs() ? "..." : "") + " " + svd.getName().getIdentifier();
        }
        if (node instanceof VariableDeclarationFragment) {
            VariableDeclarationFragment vdf = (VariableDeclarationFragment) node;
            return "VarFragment: " + vdf.getName().getIdentifier()
                    + (vdf.getInitializer() != null ? " = " + vdf.getInitializer() : "");
        }

        // --- Statements ---
        if (node instanceof Block) {
            Block b = (Block) node;
            return "Block {" + b.statements().size() + " statements}";
        }
        if (node instanceof IfStatement) {
            IfStatement is = (IfStatement) node;
            return "if (" + is.getExpression() + ")";
        }
        if (node instanceof WhileStatement) {
            WhileStatement ws = (WhileStatement) node;
            return "while (" + ws.getExpression() + ")";
        }
        if (node instanceof DoStatement) {
            DoStatement ds = (DoStatement) node;
            return "do-while (" + ds.getExpression() + ")";
        }
        if (node instanceof ForStatement) {
            ForStatement fs = (ForStatement) node;
            return "for (" + listToString(fs.initializers()) + "; "
                    + fs.getExpression() + "; " + listToString(fs.updaters()) + ")";
        }
        if (node instanceof EnhancedForStatement) {
            EnhancedForStatement efs = (EnhancedForStatement) node;
            return "for-each (" + efs.getParameter().getType() + " "
                    + efs.getParameter().getName() + " : " + efs.getExpression() + ")";
        }
        if (node instanceof ReturnStatement) {
            ReturnStatement rs = (ReturnStatement) node;
            return "return" + (rs.getExpression() != null ? " " + rs.getExpression() : "");
        }
        if (node instanceof ExpressionStatement) {
            ExpressionStatement es = (ExpressionStatement) node;
            return "expr: " + es.getExpression();
        }
        if (node instanceof VariableDeclarationStatement) {
            VariableDeclarationStatement vds = (VariableDeclarationStatement) node;
            return "var-decl: " + vds.getType() + " " + listToString(vds.fragments());
        }
        if (node instanceof ThrowStatement) {
            ThrowStatement ts = (ThrowStatement) node;
            return "throw " + ts.getExpression();
        }
        if (node instanceof TryStatement) {
            return "try";
        }
        if (node instanceof CatchClause) {
            CatchClause cc = (CatchClause) node;
            return "catch (" + cc.getException().getType() + " " + cc.getException().getName() + ")";
        }
        if (node instanceof SwitchStatement) {
            SwitchStatement ss = (SwitchStatement) node;
            return "switch (" + ss.getExpression() + ")";
        }
        if (node instanceof SwitchCase) {
            SwitchCase sc = (SwitchCase) node;
            return sc.isDefault() ? "default:" : "case " + listToString(sc.expressions()) + ":";
        }
        if (node instanceof BreakStatement) {
            BreakStatement bs = (BreakStatement) node;
            return "break" + (bs.getLabel() != null ? " " + bs.getLabel() : "");
        }
        if (node instanceof ContinueStatement) {
            ContinueStatement cs = (ContinueStatement) node;
            return "continue" + (cs.getLabel() != null ? " " + cs.getLabel() : "");
        }
        if (node instanceof AssertStatement) {
            AssertStatement as = (AssertStatement) node;
            return "assert " + as.getExpression()
                    + (as.getMessage() != null ? " : " + as.getMessage() : "");
        }
        if (node instanceof LabeledStatement) {
            LabeledStatement ls = (LabeledStatement) node;
            return "label " + ls.getLabel() + ":";
        }

        // --- Annotations ---
        if (node instanceof MarkerAnnotation) {
            MarkerAnnotation ma = (MarkerAnnotation) node;
            return "@" + ma.getTypeName();
        }
        if (node instanceof NormalAnnotation) {
            NormalAnnotation na = (NormalAnnotation) node;
            return "@" + na.getTypeName() + "(...)";
        }
        if (node instanceof SingleMemberAnnotation) {
            SingleMemberAnnotation sma = (SingleMemberAnnotation) node;
            return "@" + sma.getTypeName() + "(" + sma.getValue() + ")";
        }

        // --- Types ---
        if (node instanceof SimpleType) {
            return "Type: " + ((SimpleType) node).getName().getFullyQualifiedName();
        }
        if (node instanceof PrimitiveType) {
            return "Type: " + node.toString();
        }
        if (node instanceof ParameterizedType) {
            return "Type: " + node.toString();
        }
        if (node instanceof ArrayType) {
            return "Type: " + node.toString();
        }
        if (node instanceof WildcardType) {
            return "Type: " + node.toString();
        }

        // --- Literals and names ---
        if (node instanceof SimpleName) {
            return "Name: " + ((SimpleName) node).getIdentifier();
        }
        if (node instanceof QualifiedName) {
            return "Name: " + node.toString();
        }
        if (node instanceof StringLiteral) {
            String val = ((StringLiteral) node).getLiteralValue();
            if (val.length() > 40)
                val = val.substring(0, 37) + "...";
            return "StringLiteral: \"" + val + "\"";
        }
        if (node instanceof NumberLiteral) {
            return "NumberLiteral: " + ((NumberLiteral) node).getToken();
        }
        if (node instanceof BooleanLiteral) {
            return "BooleanLiteral: " + ((BooleanLiteral) node).booleanValue();
        }
        if (node instanceof NullLiteral) {
            return "NullLiteral";
        }
        if (node instanceof CharacterLiteral) {
            return "CharLiteral: '" + ((CharacterLiteral) node).getEscapedValue() + "'";
        }

        // --- Expressions ---
        if (node instanceof InfixExpression) {
            InfixExpression ie = (InfixExpression) node;
            return "InfixExpr: " + ie.getLeftOperand() + " " + ie.getOperator() + " " + ie.getRightOperand();
        }
        if (node instanceof Assignment) {
            Assignment a = (Assignment) node;
            return "Assignment: " + a.getLeftHandSide() + " " + a.getOperator() + " " + a.getRightHandSide();
        }
        if (node instanceof MethodInvocation) {
            MethodInvocation mi = (MethodInvocation) node;
            String target = mi.getExpression() != null ? mi.getExpression() + "." : "";
            return "MethodCall: " + target + mi.getName().getIdentifier() + "(...)";
        }
        if (node instanceof ClassInstanceCreation) {
            ClassInstanceCreation cic = (ClassInstanceCreation) node;
            return "new " + cic.getType() + "(...)";
        }
        if (node instanceof FieldAccess) {
            FieldAccess fa = (FieldAccess) node;
            return "FieldAccess: " + fa.getExpression() + "." + fa.getName();
        }
        if (node instanceof LambdaExpression) {
            return "Lambda: (...)  -> ...";
        }
        if (node instanceof ExpressionMethodReference) {
            return "MethodRef: " + node.toString();
        }
        if (node instanceof ConditionalExpression) {
            ConditionalExpression ce = (ConditionalExpression) node;
            return "Ternary: " + ce.getExpression() + " ? ... : ...";
        }
        if (node instanceof CastExpression) {
            CastExpression ce = (CastExpression) node;
            return "Cast: (" + ce.getType() + ") " + ce.getExpression();
        }
        if (node instanceof InstanceofExpression) {
            InstanceofExpression ie = (InstanceofExpression) node;
            return "instanceof: " + ie.getLeftOperand() + " instanceof " + ie.getRightOperand();
        }
        if (node instanceof PrefixExpression) {
            PrefixExpression pe = (PrefixExpression) node;
            return "Prefix: " + pe.getOperator() + pe.getOperand();
        }
        if (node instanceof PostfixExpression) {
            PostfixExpression pe = (PostfixExpression) node;
            return "Postfix: " + pe.getOperand() + pe.getOperator();
        }
        if (node instanceof ArrayAccess) {
            ArrayAccess aa = (ArrayAccess) node;
            return "ArrayAccess: " + aa.getArray() + "[" + aa.getIndex() + "]";
        }
        if (node instanceof ArrayCreation) {
            ArrayCreation ac = (ArrayCreation) node;
            return "new " + ac.getType() + "[]";
        }
        if (node instanceof Modifier) {
            return "Modifier: " + ((Modifier) node).getKeyword();
        }

        // Fallback: use the class simple name
        return node.getClass().getSimpleName();
    }

    // -------------------------------------------------------------------------
    // Helpers for key node types
    // -------------------------------------------------------------------------

    private String describeType(TypeDeclaration td) {
        String kind = td.isInterface() ? "Interface" : "Class";
        StringBuilder sb = new StringBuilder(kind + ": ");
        String mods = modString(td.getModifiers());
        if (!mods.isEmpty())
            sb.append(mods).append(" ");
        sb.append(td.getName().getIdentifier());
        if (!td.typeParameters().isEmpty()) {
            sb.append("<").append(listToString(td.typeParameters())).append(">");
        }
        if (td.getSuperclassType() != null) {
            sb.append(" extends ").append(td.getSuperclassType());
        }
        List<?> ifaces = td.superInterfaceTypes();
        if (!ifaces.isEmpty()) {
            sb.append(td.isInterface() ? " extends " : " implements ");
            sb.append(listToString(ifaces));
        }
        return sb.toString();
    }

    private String describeMethod(MethodDeclaration md) {
        StringBuilder sb = new StringBuilder();
        String mods = modString(md.getModifiers());
        if (!mods.isEmpty())
            sb.append(mods).append(" ");
        if (md.isConstructor()) {
            sb.append("Constructor: ").append(md.getName().getIdentifier());
        } else {
            sb.append("Method: ").append(md.getReturnType2()).append(" ").append(md.getName().getIdentifier());
        }
        sb.append("(");
        StringJoiner params = new StringJoiner(", ");
        for (Object p : md.parameters()) {
            SingleVariableDeclaration svd = (SingleVariableDeclaration) p;
            params.add(svd.getType() + (svd.isVarargs() ? "..." : "") + " " + svd.getName().getIdentifier());
        }
        sb.append(params).append(")");
        if (!md.thrownExceptionTypes().isEmpty()) {
            sb.append(" throws ").append(listToString(md.thrownExceptionTypes()));
        }
        return sb.toString();
    }

    private String describeField(FieldDeclaration fd) {
        String mods = modString(fd.getModifiers());
        String type = fd.getType().toString();
        StringJoiner names = new StringJoiner(", ");
        for (Object frag : fd.fragments()) {
            VariableDeclarationFragment vdf = (VariableDeclarationFragment) frag;
            String n = vdf.getName().getIdentifier();
            if (vdf.getInitializer() != null)
                n += " = " + vdf.getInitializer();
            names.add(n);
        }
        return "Field: " + (mods.isEmpty() ? "" : mods + " ") + type + " " + names;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private void indent() {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    /** Build modifier string from int bitmask (e.g. "public static final"). */
    private String modString(int flags) {
        StringJoiner sj = new StringJoiner(" ");
        if (Modifier.isPublic(flags))
            sj.add("public");
        if (Modifier.isProtected(flags))
            sj.add("protected");
        if (Modifier.isPrivate(flags))
            sj.add("private");
        if (Modifier.isStatic(flags))
            sj.add("static");
        if (Modifier.isFinal(flags))
            sj.add("final");
        if (Modifier.isAbstract(flags))
            sj.add("abstract");
        if (Modifier.isSynchronized(flags))
            sj.add("synchronized");
        if (Modifier.isNative(flags))
            sj.add("native");
        if (Modifier.isTransient(flags))
            sj.add("transient");
        if (Modifier.isVolatile(flags))
            sj.add("volatile");
        if (Modifier.isDefault(flags))
            sj.add("default");
        return sj.toString();
    }

    private String listToString(List<?> list) {
        StringJoiner sj = new StringJoiner(", ");
        for (Object o : list)
            sj.add(o.toString());
        return sj.toString();
    }
}
