package gwu.rejd.gui;

import gwu.rejd.extractor.RelationshipExtractor;
import gwu.rejd.generator.SimpleGraphGenerator;
import gwu.rejd.model.ProjectModel;
import gwu.rejd.model.RelationshipModel;

import java.util.List;

public class ClassDiagramController {

    private final RelationshipExtractor relationshipExtractor = new RelationshipExtractor();
    private final SimpleGraphGenerator graphGenerator = new SimpleGraphGenerator();
    private final ProjectModelFilter projectModelFilter = new ProjectModelFilter();
    private final RelationshipScopeFilter relationshipScopeFilter = new RelationshipScopeFilter();
    private final ClassDiagramView view;

    private ProjectModel currentProject;
    private DiagramScope currentScope = DiagramScope.entireProject();

    public ClassDiagramController(ClassDiagramView view) {
        this.view = view;
    }

    public void showProject(ProjectModel projectModel) {
        this.currentProject = projectModel;
        renderCurrentScope();
    }

    public void setScope(DiagramScope scope) {
        this.currentScope = scope != null ? scope : DiagramScope.entireProject();
        renderCurrentScope();
    }

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

    public void clear() {
        currentProject = null;
        currentScope = DiagramScope.entireProject();
        view.clear();
    }
}