package com.taskmaster.taskmasterfrontend;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * MAINAPP
 *
 * Punto de entrada de la aplicación JavaFX.
 * Stage -> es la ventana principal de la app.
 * Scene -> es el contenido que se muestra dentro de la ventana.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                MainApp.class.getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml")
        );
        Scene scene = new Scene(fxmlLoader.load(), 400, 500);
        stage.setTitle("TaskMaster");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
