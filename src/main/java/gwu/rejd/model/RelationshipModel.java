/*
File Name: RelationshipModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: This file Represents a relationship detected between two types,
during static analysis of the Java project.
*/

// Package info
package gwu.rejd.model;

// Import statements
import gwu.rejd.model.enums.RelationshipKind;

import java.util.Objects;

/**
 * This is a immutable model used to store UML-style relationships
 * such as inheritance, association, aggregation, etc.
 */
public final class RelationshipModel {

    private final String sourceFqn;
    private final String targetName;
    private final RelationshipKind kind;

    // Constructor
    public RelationshipModel(String sourceFqn, String targetName, RelationshipKind kind) {
        this.sourceFqn = Objects.requireNonNull(sourceFqn);
        this.targetName = Objects.requireNonNull(targetName);
        this.kind = Objects.requireNonNull(kind);
    }

    public String sourceFqn()  { return sourceFqn; }
    public String targetName() { return targetName; }
    public RelationshipKind kind() { return kind; }

    /**
     * Relationships are considered equal if all
     * core relationship properties match.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelationshipModel)) return false;
        RelationshipModel other = (RelationshipModel) o;
        return sourceFqn.equals(other.sourceFqn)
                && targetName.equals(other.targetName)
                && kind == other.kind;
    }

    // Needed so relationships can be safely stored, in hash-based collections like Set or Map.
    @Override
    public int hashCode() {
        return Objects.hash(sourceFqn, targetName, kind);
    }

    // Helpful for debugging/logging relationship extraction.
    @Override
    public String toString() {
        return sourceFqn + " -[" + kind + "]-> " + targetName;
    }
}
