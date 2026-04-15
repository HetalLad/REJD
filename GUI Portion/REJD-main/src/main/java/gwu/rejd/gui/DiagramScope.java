package gwu.rejd.gui;

public class DiagramScope {

    public enum ScopeType {
        ENTIRE_PROJECT,
        PACKAGE
    }

    private final ScopeType type;
    private final String packageName;

    private DiagramScope(ScopeType type, String packageName) {
        this.type = type;
        this.packageName = packageName;
    }

    public static DiagramScope entireProject() {
        return new DiagramScope(ScopeType.ENTIRE_PROJECT, null);
    }

    public static DiagramScope forPackage(String packageName) {
        return new DiagramScope(ScopeType.PACKAGE, packageName);
    }

    public ScopeType getType() {
        return type;
    }

    public String getPackageName() {
        return packageName;
    }

    public boolean isEntireProject() {
        return type == ScopeType.ENTIRE_PROJECT;
    }
}