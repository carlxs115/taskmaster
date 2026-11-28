package com.taskmaster.taskmasterfrontend;

import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.time.Duration;
import java.util.Locale;

/**
 * Clase principal de la aplicación TaskMaster.
 *
 * <p>Punto de entrada de la aplicación JavaFX. Se encarga de:</p>
 * <ul>
 *   <li>Comprobar que el puerto del backend está libre y, si lo está, arrancar el
 *       proceso del backend Spring Boot antes de mostrar la UI</li>
 *   <li>Reutilizar el backend de TaskMaster que ya escuche en ese puerto, o avisar
 *       y salir si lo ocupa otro programa</li>
 *   <li>Esperar a que el backend esté listo para aceptar peticiones</li>
 *   <li>Inicializar la ventana principal ({@link Stage})</li>
 *   <li>Cargar la vista de login y aplicar el tema y el idioma iniciales</li>
 *   <li>Detener el proceso del backend al cerrar la aplicación (solo si lo lanzó ella)</li>
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

    /** Puerto del backend, usado para comprobar que está listo aceptando conexiones TCP. */
    private static final int BACKEND_PORT = 8080;

    /** Segundos máximos que esperamos a que el backend arranque. */
    private static final int BACKEND_TIMEOUT_SECONDS = 30;

    /** Quién atiende el puerto del backend al arrancar la app. */
    private enum BackendState {
        /** El puerto estaba libre y la app lanzó su propio backend. */
        OWN_PROCESS,
        /** El puerto lo ocupaba ya un backend de TaskMaster y se reutiliza. */
        EXTERNAL_TASKMASTER,
        /** El puerto lo ocupa otro programa: la app no arranca. */
        PORT_BUSY
    }

    /** Proceso del backend lanzado al iniciar la app (nulo si no se lanzó ninguno). */
    private Process backendProcess;

    /** Resultado de la comprobación del puerto; lo escribe {@code init()} y lo lee {@code start()}. */
    private volatile BackendState backendState = BackendState.OWN_PROCESS;

    // -------------------------------------------------------------------------
    // Ciclo de vida de JavaFX
    // -------------------------------------------------------------------------

    /**
     * Se ejecuta antes de {@link #start(Stage)}, en el hilo de inicialización de JavaFX.
     *
     * <p>Comprueba el puerto del backend. Si está libre, arranca el proceso del
     * backend y espera a que esté listo. Si lo ocupa un backend de TaskMaster
     * (el ping responde 200), lo reutiliza sin lanzar nada. Si lo ocupa otro
     * programa, no lanza nada y deja que {@link #start(Stage)} avise al usuario.</p>
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

            if (isPortFree()) {
                backendState = BackendState.OWN_PROCESS;
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
            } else if (isTaskMasterRunning()) {
                backendState = BackendState.EXTERNAL_TASKMASTER;
                log.info("Puerto {} ocupado por un backend de TaskMaster: se reutiliza", BACKEND_PORT);
                pw.println("Puerto " + BACKEND_PORT
                        + " ocupado por un backend de TaskMaster: se reutiliza, no se lanza hijo");
                pw.flush();
            } else {
                backendState = BackendState.PORT_BUSY;
                log.warn("Puerto {} ocupado por otro programa: no se lanza el backend", BACKEND_PORT);
                pw.println("Puerto " + BACKEND_PORT
                        + " ocupado por otro programa: no se lanza el backend");
                pw.flush();
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
        if (backendState == BackendState.PORT_BUSY) {
            showPortBusyAlert();
            Platform.exit();
            return;
        }

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
     * <p>Detiene el proceso del backend si lo lanzó esta instancia y sigue en
     * ejecución, liberando el puerto 8080 y los recursos asociados.</p>
     *
     */
    @Override
    public void stop() {
        // Con EXTERNAL_TASKMASTER o PORT_BUSY backendProcess es nulo: no se mata nada ajeno
        if (backendProcess != null && backendProcess.isAlive()) {
            backendProcess.destroy();
            log.info("Backend detenido");
        }
    }

    // -------------------------------------------------------------------------
    // Gestión del backend
    // -------------------------------------------------------------------------

    /**
     * Muestra el aviso de que el puerto del backend lo ocupa otro programa.
     *
     * <p>Se muestra antes de aplicar ningún tema, por lo que usa el estilo por
     * defecto de JavaFX.</p>
     */
    private void showPortBusyAlert() {
        LanguageManager lm = LanguageManager.getInstance();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(lm.get("startup.port.busy.title"));
        alert.setHeaderText(null);
        alert.setContentText(MessageFormat.format(
                lm.get("startup.port.busy.content"), String.valueOf(BACKEND_PORT)));
        alert.showAndWait();
    }

    /**
     * Indica si el puerto del backend está libre.
     *
     * <p>Intenta enlazarlo en {@code 127.0.0.1}, la misma dirección que usa el backend
     * ({@code server.address}), de modo que el resultado coincide con lo que le pasaría
     * al backend al arrancar. Misma lógica que {@code PendingRestoreApplier.isPortFree}.</p>
     *
     * @return {@code true} si se puede enlazar el puerto
     */
    private static boolean isPortFree() {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), BACKEND_PORT));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Crea el cliente HTTP con el que se sondea el backend. */
    private static HttpClient pingClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1000))
                .build();
    }

    /** Crea la petición de sondeo al endpoint público de ping del backend. */
    private static HttpRequest pingRequest() {
        return HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + BACKEND_PORT + "/api/auth/ping"))
                .timeout(Duration.ofMillis(1000))
                .GET()
                .build();
    }

    /**
     * Comprueba si el ping del backend responde 200.
     *
     * @return {@code true} solo con un 200; {@code false} con otro código o cualquier error
     */
    private static boolean isBackendPingOk(HttpClient client, HttpRequest request) {
        try {
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Comprueba si el proceso que ocupa el puerto es un backend de TaskMaster.
     *
     * <p>Reintenta durante unos 3 s (6 intentos cada 500 ms) porque otra instancia
     * que esté arrancando ya tiene el puerto enlazado pero puede tardar en responder.</p>
     *
     * @return {@code true} si el ping responde 200 en algún intento
     */
    private boolean isTaskMasterRunning() throws InterruptedException {
        HttpClient client = pingClient();
        HttpRequest request = pingRequest();
        for (int attempt = 1; attempt <= 6; attempt++) {
            if (isBackendPingOk(client, request)) {
                return true;
            }
            if (attempt < 6) {
                //noinspection BusyWait
                Thread.sleep(500);
            }
        }
        return false;
    }

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
     * Espera a que el backend esté completamente listo para aceptar peticiones HTTP.
     *
     * <p>En lugar de comprobar solo la conexión TCP (que se abre antes de que
     * Hibernate termine de inicializar el esquema), realiza peticiones HTTP reales
     * al endpoint de ping cada 500 ms hasta obtener un 200. Esto garantiza que
     * Spring Boot y JPA están completamente operativos antes de que el frontend
     * empiece a hacer llamadas a la API. Si el proceso hijo muere mientras tanto,
     * deja de esperar.</p>
     *
     * @throws Exception si el backend termina antes de responder o no responde
     *                   en {@link #BACKEND_TIMEOUT_SECONDS} segundos
     */
    private void waitForBackend() throws Exception {
        long deadline = System.currentTimeMillis() + BACKEND_TIMEOUT_SECONDS * 1000L;

        log.info("Esperando a que el backend esté listo...");

        HttpClient client = pingClient();
        HttpRequest request = pingRequest();

        while (System.currentTimeMillis() < deadline) {
            if (isBackendPingOk(client, request)) {
                log.info("Backend listo (ping 200 en puerto {})", BACKEND_PORT);
                return;
            }
            if (backendProcess != null && !backendProcess.isAlive()) {
                throw new RuntimeException("El backend terminó (código "
                        + backendProcess.exitValue() + ") antes de responder");
            }
            //noinspection BusyWait
            Thread.sleep(500);
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