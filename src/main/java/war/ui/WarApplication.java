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
    }
    @Override public void stop() { if (view != null) view.close(); }
}
