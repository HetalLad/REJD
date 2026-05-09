/*
File Name: ProjectpModel.java
Authors: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: The file describes an object to describe the projects.
*/

// Package info
package gwu.rejd.model;

// Import statements
import java.util.*;

/**
Public class describes the projects.
*/
public final class ProjectModel {
  private final String projectId;
  private final String packageName;
  private final List<String> imports;
  private final PackageNode packageRoot;
  private final Map<String, TypeModel> typesByFqn;

  public ProjectModel(
      String projectId,
      String packageName,
      List<String> imports,
      PackageNode packageRoot,
      Map<String, TypeModel> typesByFqn
  ) {
    this.projectId = Objects.requireNonNull(projectId);
    this.packageName = packageName == null ? "" : packageName;
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
