/*
File Name: UserContext.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: Util File.
*/

// Package info
package gwu.rejd.util;

public final class TypeNameUtils {

    private TypeNameUtils() {
    }

    public static String packageNameOf(String fqn) {
        if (fqn == null || fqn.isBlank()) {
            return "";
        }

        int lastDot = fqn.lastIndexOf('.');
        return lastDot >= 0 ? fqn.substring(0, lastDot) : "";
    }
}
