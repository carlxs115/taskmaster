package com.taskmaster.taskmasterfrontend;

import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.ThemeManager;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Clase principal de la aplicación TaskMaster.
 *
 * <p>Punto de entrada de la aplicación JavaFX. Se encarga de:</p>
 * <ul>
 *   <li>Arrancar el proceso del backend Spring Boot antes de mostrar la UI</li>
 *   <li>Esperar a que el backend esté listo para aceptar peticiones</li>
 *   <li>Inicializar la ventana principal ({@link Stage})</li>
 *   <li>Cargar la vista de login y aplicar el tema y el idioma iniciales</li>
 *   <li>Detener el proceso del backend al cerrar la aplicación</li>
 * </ul>
 *
 * @author Carlos
 */
public class MainApp extends Application {

    private static final Logger log = LoggerFactory.getLogger(MainApp.class);

    /** Tamaños de icono disponibles para la ventana de la aplicación. */
    private static final int[] APP_ICON_SIZES = {16, 24, 32, 48, 64, 128, 256, 512};

    /** Ruta base donde se encuentran los iconos de la aplicación. */
    private static final String APP_ICON_PATH = "/com/taskmaster/taskmasterfrontend/images/app-icon/";

    /** Nombre del JAR del backend, debe coincidir con el empaquetado por Maven. */
    private static final String BACKEND_JAR = "taskmaster-0.0.1-SNAPSHOT.jar";

    /** URL del endpoint que usamos para comprobar que el backend está listo, HTTP es suficiente para localhost. */
    private static final String BACKEND_HEALTH_URL = "http://localhost:8080/api/auth/login";

    /** Segundos máximos que esperamos a que el backend arranque. */
    private static final int BACKEND_TIMEOUT_SECONDS = 30;

    /** Proceso del backend lanzado al iniciar la app. */
    private Process backendProcess;

    // -------------------------------------------------------------------------
    // Ciclo de vida de JavaFX
    // -------------------------------------------------------------------------

    /**
     * Se ejecuta antes de {@link #start(Stage)}, en el hilo de inicialización de JavaFX.
     *
     * <p>Arranca el proceso del backend y espera a que esté listo antes de
     * mostrar la interfaz de usuario.</p>
     *
     * @throws Exception si el backend no puede iniciarse o no responde a tiempo
     */
    @Override
    public void init() throws Exception {
        File logFile = new File(System.getProperty("user.home") + "/.taskmaster/startup.log");
        if (!logFile.getParentFile().mkdirs() && !logFile.getParentFile().exists()) {
            throw new RuntimeException("No se pudo crear el directorio de logs");
        }
        try (var pw = new java.io.PrintWriter(new java.io.FileWriter(logFile, true))) {
            pw.println("=== Iniciando TaskMaster ===");
            pw.println("java.home: " + System.getProperty("java.home"));
            pw.println("JAVA_HOME env: " + System.getenv("JAVA_HOME"));
            try {
                String javaExe = findJavaExecutable();
                pw.println("java.exe encontrado: " + javaExe);
                pw.println("appdir property: " + System.getProperty("appdir"));
                pw.flush();
            } catch (Exception e) {
                pw.println("ERROR findJavaExecutable: " + e.getMessage());
            }
            pw.flush();

            try {
                pw.println("Llamando a startBackend()...");
                pw.flush();
                startBackend();
                pw.println("Backend iniciado OK, PID: " + backendProcess.pid());
                pw.flush();
            } catch (Exception e) {
                pw.println("ERROR startBackend: " + e.getClass().getName() + ": " + e.getMessage());
                e.printStackTrace(pw);
                pw.flush();
                throw e;
            }

            try {
                pw.println("Esperando al backend...");
                pw.flush();
                waitForBackend();
                pw.println("Backend listo");
                pw.flush();
            } catch (Exception e) {
                pw.println("ERROR waitForBackend: " + e.getMessage());
                pw.flush();
                throw e;
            }
        }
    }

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
        var cssResource = MainApp.class.getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css");
        if (cssResource != null) scene.getStylesheets().add(cssResource.toExternalForm());

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
     * Se ejecuta al cerrar la aplicación.
     *
     * <p>Detiene el proceso del backend si sigue en ejecución, liberando
     * el puerto 8080 y los recursos asociados.</p>
     *
     */
    @Override
    public void stop() {
        if (backendProcess != null && backendProcess.isAlive()) {
            backendProcess.destroy();
            log.info("Backend detenido");
        }
    }

    // -------------------------------------------------------------------------
    // Gestión del backend
    // -------------------------------------------------------------------------

    private String findJavaExecutable() {
        // En Windows el ejecutable es java.exe, en Linux/Mac es java
        String javaExeName = System.getProperty("os.name").toLowerCase().contains("win")
                ? "java.exe" : "java";

        // 1. java.home del proceso actual
        String javaHome = System.getProperty("java.home");
        File javaExe = new File(javaHome + File.separator + "bin" + File.separator + javaExeName);
        if (javaExe.exists()) return javaExe.getAbsolutePath();

        // 2. JAVA_HOME del sistema
        String envJavaHome = System.getenv("JAVA_HOME");
        if (envJavaHome != null) {
            javaExe = new File(envJavaHome + File.separator + "bin" + File.separator + javaExeName);
            if (javaExe.exists()) return javaExe.getAbsolutePath();
        }

        // 3. PATH del sistema
        for (String path : System.getenv("PATH").split(File.pathSeparator)) {
            javaExe = new File(path + File.separator + javaExeName);
            if (javaExe.exists()) return javaExe.getAbsolutePath();
        }

        throw new RuntimeException("No se encontró java. Instala Java 21 o superior.");
    }

    /**
     * Lanza el JAR del backend como proceso hijo.
     *
     * <p>Resuelve la ruta del JAR del backend buscando en el mismo directorio
     * que contiene el JAR del frontend. Esto funciona tanto en desarrollo
     * (carpeta {@code packaging/input}) como en producción (directorio de instalación).</p>
     *
     * @throws Exception si no se puede construir o lanzar el proceso
     */
    private void startBackend() throws Exception {
        // Usamos el java.exe del JRE que viene empaquetado con la app
        String javaExe = findJavaExecutable();

        // En producción con jpackage, el app dir se pasa como propiedad del sistema
        Path backendJar;
        String appDir = System.getProperty("appdir");
        if (appDir != null) {
            // Producción: jpackage define 'appdir' con la carpeta de los JARs
            backendJar = Path.of(appDir).resolve(BACKEND_JAR);
        } else {
            // Desarrollo: buscamos relativo al JAR del frontend
            Path frontendJar = Path.of(
                    MainApp.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            );
            backendJar = frontendJar
                    .getParent()
                    .getParent()
                    .getParent()
                    .resolve("taskmaster-backend")
                    .resolve("target")
                    .resolve(BACKEND_JAR);
        }

        log.info("Arrancando backend desde: {}", backendJar);

        if (!backendJar.toFile().exists()) {
            throw new RuntimeException("No se encontró el JAR del backend en: " + backendJar);
        }

        ProcessBuilder pb = new ProcessBuilder(javaExe, "-jar", backendJar.toString());
        pb.redirectErrorStream(true);
        // Descartamos la salida del backend para no mezclarla con la del frontend
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        backendProcess = pb.start();

        log.info("Proceso backend iniciado (PID {})", backendProcess.pid());
    }

    /**
     * Espera a que el backend esté listo para aceptar peticiones HTTP.
     *
     * <p>Realiza peticiones cada 500 ms al endpoint indicado hasta que responde
     * (con cualquier código HTTP) o se agota el tiempo de espera.</p>
     *
     * @throws Exception si el backend no responde antes de agotar el tiempo
     */
    private void waitForBackend() throws Exception {
        long deadline = System.currentTimeMillis() + BACKEND_TIMEOUT_SECONDS * 1000L;

        log.info("Esperando a que el backend esté listo...");

        while (System.currentTimeMillis() < deadline) {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                        new URI(BACKEND_HEALTH_URL).toURL().openConnection();
                conn.setConnectTimeout(1000);
                conn.setReadTimeout(1000);
                conn.setRequestMethod("GET");
                int code = conn.getResponseCode();
                log.info("Backend listo (HTTP {})", code);
                return;
            } catch (Exception ignored) {
                //noinspection BusyWait
                Thread.sleep(500);
            }
        }

        throw new RuntimeException("El backend no respondió en " + BACKEND_TIMEOUT_SECONDS + " segundos");
    }

    // -------------------------------------------------------------------------
    // Iconos de la aplicación
    // -------------------------------------------------------------------------

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
                    log.warn("No se encontró el icono: {}", resourcePath);
                }
            } catch (IOException e) {
                log.error("Error al cargar el icono {}: {}", resourcePath, e.getMessage());
            }
        }
    }

    // -------------------------------------------------------------------------
    // Punto de entrada
    // -------------------------------------------------------------------------

    /**
     * Método principal. Lanza la aplicación JavaFX.
     *
     * @param args Argumentos de línea de comandos (no utilizados).
     */
    public static void main(String[] args) {
        Locale.setDefault(Locale.forLanguageTag("es-ES"));
        launch();
    }
}