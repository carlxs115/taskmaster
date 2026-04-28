package com.taskmaster.taskmasterfrontend;

import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.ThemeManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

/**
 * Clase principal de la aplicación TaskMaster.
 *
 * <p>Punto de entrada de la aplicación JavaFX. Se encarga de inicializar
 * la ventana principal ({@link Stage}), cargar la vista de login y aplicar
 * el tema y el idioma iniciales.</p>
 *
 * @author Carlos
 */
public class MainApp extends Application {

    /**
     * Inicializa y muestra la ventana principal de la aplicación.
     *
     * <p>Carga la vista de login desde su FXML, aplica el tema Amatista por defecto
     * y registra la escena en el {@link ThemeManager} para su gestión posterior.</p>
     *
     * @param stage El {@link Stage} principal proporcionado por JavaFX.
     * @throws IOException Si no se puede cargar el archivo FXML de la vista de login.
     */
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(
                MainApp.class.getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml"),
                LanguageManager.getInstance().getBundle()
        );
        Scene scene = new Scene(fxmlLoader.load(), 400, 520);

        // Tema Amatista fijo para login
        String css = MainApp.class.getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css"
        ).toExternalForm();
        scene.getStylesheets().add(css);

        // Registrar scene en ThemeManager (se sobreescribirá al navegar al main)
        ThemeManager.getInstance().setMainScene(scene);

        stage.setTitle("TaskMaster");
        stage.setScene(scene);
        stage.setWidth(400);
        stage.setHeight(520);
        stage.setMinWidth(400);
        stage.setMinHeight(520);
        stage.centerOnScreen();
        stage.show();
    }

    /**
     * Método principal. Lanza la aplicación JavaFX.
     *
     * @param args Argumentos de línea de comandos (no utilizados).
     */
    public static void main(String[] args) {
        launch();
    }
}
