/*
File Name: PackageNode.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file describes the package nodes.
*/

// Package info
package gwu.rejd.model;

import java.util.*;

/**
 * Immutable-style package tree node are used while
 * building package navigation and diagram grouping.
 */
public final class PackageNode {
  private final String name; // segment name (e.g., "util"), root = ""

  // Child package nodes are nested under this package
  private final Map<String, PackageNode> children = new LinkedHashMap<>();

  // These are fully qualified names of types belonging to this package
  private final List<String> typeFqns = new ArrayList<>();

  public PackageNode(String name) {
    this.name = name == null ? "" : name;
  }

  public String getName() { return name; }
  public Map<String, PackageNode> getChildren() { return Collections.unmodifiableMap(children); }
  public List<String> getTypeFqns() { return Collections.unmodifiableList(typeFqns); }

  /**
   * This Returns an existing child package if present,
   * otherwise creates and stores a new one.
   */
  public PackageNode getOrCreateChild(String segment) {
    return children.computeIfAbsent(segment, PackageNode::new);
  }

   /**
   * This adds a fully qualified type name to this package node.
   */
  public void addType(String typeFqn) {
    typeFqns.add(typeFqn);
  }
}
