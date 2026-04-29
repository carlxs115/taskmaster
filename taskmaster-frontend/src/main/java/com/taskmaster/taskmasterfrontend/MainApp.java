package com.taskmaster.taskmasterfrontend;

import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.ThemeManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;

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

    /** Tamaños de icono disponibles para la ventana de la aplicación. */
    private static final int[] APP_ICON_SIZES = {16, 24, 32, 48, 64, 128, 256, 512};

    /** Ruta base donde se encuentran los iconos de la aplicación. */
    private static final String APP_ICON_PATH = "/com/taskmaster/taskmasterfrontend/images/app-icon/";

    /**
     * Inicializa y muestra la ventana principal de la aplicación.
     *
     * <p>Carga la vista de login desde su FXML, aplica el tema Amatista por defecto,
     * registra la escena en el {@link ThemeManager} para su gestión posterior y
     * establece los iconos de la ventana en varios tamaños.</p>
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

        // Cargar iconos de la aplicación en varios tamaños
        loadAppIcons(stage);

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
     * Carga los iconos de la aplicación en todos los tamaños disponibles.
     *
     * <p>Añade los iconos al {@link Stage} en varios tamaños para que el sistema
     * operativo escoja el más adecuado en cada contexto (barra de tareas,
     * Alt+Tab, esquina de la ventana, etc.).</p>
     *
     * @param stage El {@link Stage} al que se añadirán los iconos.
     */
    private void loadAppIcons(Stage stage) {
        for (int size : APP_ICON_SIZES) {
            String resourcePath = APP_ICON_PATH + "icon_" + size + ".png";
            try (InputStream stream = MainApp.class.getResourceAsStream(resourcePath)) {
                if (stream != null) {
                    stage.getIcons().add(new Image(stream));
                } else {
                    System.err.println("No se encontró el icono: " + resourcePath);
                }
            } catch (IOException e) {
                System.err.println("Error al cargar el icono " + resourcePath + ": " + e.getMessage());
            }
        }
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
