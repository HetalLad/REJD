package gwu.rejd.model;

import gwu.rejd.model.enums.RelationshipKind;

import java.util.Objects;

public final class RelationshipModel {

    private final String sourceFqn;
    private final String targetName;
    private final RelationshipKind kind;

    public RelationshipModel(String sourceFqn, String targetName, RelationshipKind kind) {
        this.sourceFqn = Objects.requireNonNull(sourceFqn);
        this.targetName = Objects.requireNonNull(targetName);
        this.kind = Objects.requireNonNull(kind);
    }

    public String sourceFqn()  { return sourceFqn; }
    public String targetName() { return targetName; }
    public RelationshipKind kind() { return kind; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RelationshipModel)) return false;
        RelationshipModel other = (RelationshipModel) o;
        return sourceFqn.equals(other.sourceFqn)
                && targetName.equals(other.targetName)
                && kind == other.kind;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceFqn, targetName, kind);
    }

    @Override
    public String toString() {
        return sourceFqn + " -[" + kind + "]-> " + targetName;
    }
}
