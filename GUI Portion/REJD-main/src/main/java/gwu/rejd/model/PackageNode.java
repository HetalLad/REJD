package gwu.rejd.model;

import java.util.*;

public final class PackageNode {
  private final String name; // segment name (e.g., "util"), root = ""
  private final Map<String, PackageNode> children = new LinkedHashMap<>();
  private final List<String> typeFqns = new ArrayList<>();

  public PackageNode(String name) {
    this.name = name == null ? "" : name;
  }

  public String getName() { return name; }
  public Map<String, PackageNode> getChildren() { return Collections.unmodifiableMap(children); }
  public List<String> getTypeFqns() { return Collections.unmodifiableList(typeFqns); }

  public PackageNode getOrCreateChild(String segment) {
    return children.computeIfAbsent(segment, PackageNode::new);
  }

  public void addType(String typeFqn) {
    typeFqns.add(typeFqn);
  }
}