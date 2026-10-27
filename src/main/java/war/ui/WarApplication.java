package war.ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class WarApplication extends Application {
    private WarView view;
    @Override public void start(Stage stage) {
        view = new WarView(stage);
        Scene scene = new Scene(view.root(), 1320, 900);
        scene.getStylesheets().add(WarApplication.class.getResource("table.css").toExternalForm());
        stage.setTitle("War • The Card Table");
        stage.setMinWidth(960); stage.setMinHeight(720);
        stage.setScene(scene); stage.show();
        var args = getParameters().getRaw();
        if (!args.isEmpty() && args.get(0).equals("--smoke-ui")) {
            javafx.application.Platform.runLater(() -> {
                try {
                    if (args.size() != 3 || !args.get(1).equals("--report")) throw new IllegalArgumentException("Use --smoke-ui --report <path>");
                    view.root().applyCss(); view.root().layout();
                    ((javafx.scene.control.Button)view.root().lookup("#next")).fire();
                    if (view.session().cursor() != 1 || view.root().lookupAll(".playing-card").size() != 2)
                        throw new IllegalStateException("Packaged table did not reveal two cards");
                    ((javafx.scene.control.TextField)view.root().lookup("#max-rounds")).setText("1");
                    ((javafx.scene.control.Button)view.root().lookup("#deal")).fire();
                    ((javafx.scene.control.Button)view.root().lookup("#next")).fire();
                    if (view.session().frame().state().result().reason() != war.GameState.EndReason.ROUND_LIMIT)
                        throw new IllegalStateException("Packaged controls did not enforce the round limit");
                    java.nio.file.Files.writeString(java.nio.file.Path.of(args.get(2)), "War packaged GUI: OK\n");
                    javafx.application.Platform.exit();
                } catch (Exception e) { e.printStackTrace(); System.exit(1); }
            });
        }
    }
    @Override public void stop() { if (view != null) view.close(); }
}
