package gwu.rejd.gui;

import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public class ClassDiagramView extends BorderPane {

    private final WebView webView = new WebView();
    private double zoomLevel = 1.0;

    public ClassDiagramView() {
        setCenter(webView);
        webView.getEngine().setJavaScriptEnabled(true);
        webView.setContextMenuEnabled(false);
        
        webView.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                double x = event.getX();
                double y = event.getY();

                Object result = webView.getEngine().executeScript(
                    "(function() {" +
                    "  const el = document.elementFromPoint(" + x + ", " + y + ");" +
                    "  return !!(el && el.closest('.node'));" +
                    "})()"
                );

                boolean clickedNode = Boolean.TRUE.equals(result);

                if (clickedNode) {
                    webView.getEngine().executeScript(
                        "showContextMenu(" + x + ", " + y + ");"
                    );
                } else {
                    webView.getEngine().executeScript("hideContextMenu();");
                }

                event.consume();
            }
        });

        webView.addEventFilter(ScrollEvent.SCROLL, event -> {
            if (event.isControlDown()) {
                double delta = event.getDeltaY() > 0 ? 0.1 : -0.1;
                zoomLevel = Math.max(0.4, Math.min(2.5, zoomLevel + delta));
                webView.getEngine().executeScript("setZoom(" + zoomLevel + ");");
                event.consume();
            }
        });
    }

    public void renderGraph(String graphJson) {
        WebEngine engine = webView.getEngine();
        var htmlUrl = getClass().getResource("/web/simple-diagram.html");

        if (htmlUrl == null) {
            engine.loadContent("<html><body>simple-diagram.html not found in resources/web</body></html>");
            return;
        }

        engine.load(htmlUrl.toExternalForm());

        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                try {
                    engine.executeScript("renderGraph(" + graphJson + ");");
                    engine.executeScript("setZoom(" + zoomLevel + ");");
                } catch (Exception e) {
                    engine.loadContent("<html><body>Render invocation failed: " + escapeHtml(e.toString()) + "</body></html>");
                }
            }
        });
    }

    public void clear() {
        webView.getEngine().loadContent(
                "<html><body style='font-family: Arial; padding: 16px;'>No diagram loaded.</body></html>"
        );
        zoomLevel = 1.0;
    }

    private String escapeHtml(String text) {
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}