/*
File Name: RelationshipModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file describes the relationship models.
*/

// Package info
package gwu.rejd.model;

// Import statements
import gwu.rejd.model.enums.RelationshipKind;

import java.util.Objects;

/**
* RelationshipModel class defines objects to describe relationship between two objects.
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

    // Comparing two objects
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelationshipModel)) return false;
        RelationshipModel other = (RelationshipModel) o;
        return sourceFqn.equals(other.sourceFqn)
                && targetName.equals(other.targetName)
                && kind == other.kind;
    }

    // Hahses the data
    @Override
    public int hashCode() {
        return Objects.hash(sourceFqn, targetName, kind);
    }

    // String representation of the object
    @Override
    public String toString() {
        return sourceFqn + " -[" + kind + "]-> " + targetName;
    }
}
