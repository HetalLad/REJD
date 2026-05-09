/*
Filename: DiagramScope.java
Author: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: Implements the class to describe the diagram scope.
*/

// Package info
package gwu.rejd.gui;

/**
* Public class to implement the scope of the diagram rendered.
*/
public class DiagramScope {

    public enum ScopeType {
        ENTIRE_PROJECT,
        PACKAGE
    }

    private final ScopeType type;
    private final String packageName;

    // Constructor
    private DiagramScope(ScopeType type, String packageName) {
        this.type = type;
        this.packageName = packageName;
    }

    // Scope is the enter project
    public static DiagramScope entireProject() {
        return new DiagramScope(ScopeType.ENTIRE_PROJECT, null);
    }

    // Scope is some package
    public static DiagramScope forPackage(String packageName) {
        return new DiagramScope(ScopeType.PACKAGE, packageName);
    }

    // Getter for the scope type
    public ScopeType getType() {
        return type;
    }

    // Getter for the package name
    public String getPackageName() {
        return packageName;
    }

    // Checks if the scope is the entire project
    public boolean isEntireProject() {
        return type == ScopeType.ENTIRE_PROJECT;
    }
}
