package gwu.rejd.gui;

import gwu.rejd.extractor.MultiFileProjectLoader;
import gwu.rejd.model.ProjectModel;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.util.List;

public class RejdApp extends Application {

    private static final String ENTIRE_PROJECT_OPTION = "Entire Project";

    @Override
    public void start(Stage stage) throws Exception {
        ClassDiagramView classDiagramView = new ClassDiagramView();
        ClassDiagramController classDiagramController = new ClassDiagramController(classDiagramView);

        ComboBox<String> scopeComboBox = new ComboBox<>();
        scopeComboBox.setPrefWidth(300);

        Label scopeLabel = new Label("Scope:");
        HBox toolbar = new HBox(10, scopeLabel, scopeComboBox, classDiagramView.getExportButton());
        toolbar.setPadding(new Insets(10));

        BorderPane classDiagramContainer = new BorderPane();
        classDiagramContainer.setTop(toolbar);
        classDiagramContainer.setCenter(classDiagramView);

        Tab classDiagramTab = new Tab("Class Diagram");
        classDiagramTab.setClosable(false);
        classDiagramTab.setContent(classDiagramContainer);

        TabPane tabPane = new TabPane();
        tabPane.getTabs().add(classDiagramTab);

        Scene scene = new Scene(tabPane, 1200, 800);
        stage.setTitle("REJD - Class Diagram");
        stage.setScene(scene);
        stage.show();

        MultiFileProjectLoader loader = new MultiFileProjectLoader();

        ProjectModel model = loader.loadProject(
                "rejd-demo",
                Path.of("src/test/resources/validation/multipackage-demo")
        );

        List<String> packages = extractPackages(model);
        scopeComboBox.setItems(FXCollections.observableArrayList());
        scopeComboBox.getItems().add(ENTIRE_PROJECT_OPTION);
        scopeComboBox.getItems().addAll(packages);
        scopeComboBox.setValue(ENTIRE_PROJECT_OPTION);

        scopeComboBox.setOnAction(event -> {
            String selected = scopeComboBox.getValue();

            if (ENTIRE_PROJECT_OPTION.equals(selected)) {
                classDiagramController.setScope(DiagramScope.entireProject());
            } else {
                classDiagramController.setScope(DiagramScope.forPackage(selected));
            }
        });

        classDiagramController.showProject(model);
    }

    private List<String> extractPackages(ProjectModel model) {
        return model.getTypesByFqn().values().stream()
                .map(type -> type.getPackageName())
                .filter(packageName -> packageName != null && !packageName.isBlank())
                .distinct()
                .sorted()
                .toList();
    }

    public static void main(String[] args) {
        launch(args);
    }
}