/*
File Name: ClassDiagramController.java
Author: Anirvinna Jain, Hetal Lad, Saptorshee Nag
Description: File implements the Class Diagram View.
*/

// Package info
package gwu.rejd.gui;

// Import Statements
import gwu.rejd.extractor.RelationshipExtractor;
import gwu.rejd.generator.DiagramRenderer;
import gwu.rejd.generator.PlantUmlSequenceDiagramGenerator;
import gwu.rejd.generator.SimpleGraphGenerator;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.RelationshipModel;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
* Implements the Class Diagram view on the plug-in.
*/
public class ClassDiagramController {

    private final RelationshipExtractor relationshipExtractor = new RelationshipExtractor();
    private final SimpleGraphGenerator graphGenerator = new SimpleGraphGenerator();
    private final ProjectModelFilter projectModelFilter = new ProjectModelFilter();
    private final RelationshipScopeFilter relationshipScopeFilter = new RelationshipScopeFilter();
    private final ClassDiagramView view;

    private ProjectModel currentProject;
    private DiagramScope currentScope = DiagramScope.entireProject();

    // Constructor
    public ClassDiagramController(ClassDiagramView view) {
        this.view = view;
    }

    // To render the project
    public void showProject(ProjectModel projectModel) {
        this.currentProject = projectModel;
        renderCurrentScope();
    }

    // Sets scope and re-renders only if a project is already loaded.
    public void setScope(DiagramScope scope) {
        this.currentScope = scope != null ? scope : DiagramScope.entireProject();
        renderCurrentScope();
    }

    // Sets both project and scope atomically, then renders exactly once.
    public void showProjectWithScope(ProjectModel projectModel, DiagramScope scope) {
        this.currentProject = projectModel;
        this.currentScope = scope != null ? scope : DiagramScope.entireProject();
        renderCurrentScope();
    }

    // Renders the current diagram scope
    private void renderCurrentScope() {
        if (currentProject == null) {
            view.clear();
            return;
        }

        ProjectModel scopedProject = projectModelFilter.filter(currentProject, currentScope);

        List<RelationshipModel> allRelationships = relationshipExtractor.extract(currentProject);
        List<RelationshipModel> scopedRelationships =
                relationshipScopeFilter.filter(currentScope, allRelationships);

        String graphJson = graphGenerator.generate(scopedProject, scopedRelationships);
        view.renderGraph(graphJson);
    }

    /**
     * Generates a sequence diagram for the given method and returns the path to
     * a temporary PNG file. All pipeline logic (PlantUML generation, rendering)
     * lives here in gwu.rejd — callers (Eclipse plugin, standalone app) just
     * pass in the model/CU/methodId and receive a ready-to-display PNG path.
     */
    public Path generateSequenceDiagram(ProjectModel model, CompilationUnit cu, String methodId)
            throws IOException {
        String plantUml = new PlantUmlSequenceDiagramGenerator().generate(model, cu, methodId);
        Path tmpPng = Files.createTempFile("rejd-seq-", ".png");
        new DiagramRenderer().render(plantUml, tmpPng);
        return tmpPng;
    }

    // Clears the canvas
    public void clear() {
        currentProject = null;
        currentScope = DiagramScope.entireProject();
        view.clear();
    }
}
