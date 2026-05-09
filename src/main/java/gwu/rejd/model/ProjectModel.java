/*
File Name: ProjectpModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: Stores the parsed representation of an entire Java project,
including package hierarchy, imports, and discovered types.
*/

// Package info
package gwu.rejd.model;

// Import statements
import java.util.*;

/**
The Public class describes the projects.
*/
public final class ProjectModel {
  private final String projectId;
  private final String packageName;
  private final List<String> imports;
  private final PackageNode packageRoot;
  // This maps fully qualified type names to their models
  private final Map<String, TypeModel> typesByFqn;

  public ProjectModel(
      String projectId,
      String packageName,
      List<String> imports,
      PackageNode packageRoot,
      Map<String, TypeModel> typesByFqn
  ) {
    this.projectId = Objects.requireNonNull(projectId);
    // Some projects may not define a root package
    this.packageName = packageName == null ? "" : packageName;
    // Defensive copies can help keep the model immutable
    this.imports = Collections.unmodifiableList(new ArrayList<>(imports == null ? List.of() : imports));
    this.packageRoot = Objects.requireNonNull(packageRoot);
    this.typesByFqn = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(typesByFqn)));
  }

  public String getProjectId() { return projectId; }
  public String getPackageName() { return packageName; }
  public List<String> getImports() { return imports; }
  public PackageNode getPackageRoot() { return packageRoot; }
  public Map<String, TypeModel> getTypesByFqn() { return typesByFqn; }
}
