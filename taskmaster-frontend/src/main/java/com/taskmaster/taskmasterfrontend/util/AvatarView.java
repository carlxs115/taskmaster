package com.taskmaster.taskmasterfrontend.util;

import com.taskmaster.taskmasterfrontend.service.ApiService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Pos;

import java.io.ByteArrayInputStream;

/**
 * Componente visual reutilizable que muestra el avatar de un usuario.
 *
 * <p>Si el usuario tiene una foto subida, la descarga del backend y la
 * muestra recortada en círculo. Si no, muestra un círculo de color
 * determinista con las iniciales del nombre de usuario como fallback,
 * de forma similar a los avatares de Google o Discord.</p>
 *
 * <p>La descarga de la imagen se realiza en un hilo secundario para no
 * bloquear el hilo de la UI de JavaFX.</p>
 *
 * @author Carlos
 */
public class AvatarView extends StackPane {

    private static final Logger log = LoggerFactory.getLogger(AvatarView.class);

    private final Circle background;
    private final Label initialsLabel;
    private final ImageView imageView;

    /**
     * Paleta de colores para el avatar de iniciales.
     * El color se selecciona de forma determinista a partir del username,
     * garantizando que el mismo usuario siempre tenga el mismo color.
     */
    private static final String[] PALETTE = {
            "#7c3aed", "#ec4899", "#f59e0b", "#22c55e", "#3b82f6",
            "#e11d48", "#14b8a6", "#a855f7", "#f97316", "#06b6d4"
    };

    /**
     * Construye el componente con el tamaño indicado, inicializando
     * el círculo de fondo, la etiqueta de iniciales y el {@link ImageView}
     * con clip circular.
     *
     * @param size diámetro del avatar en píxeles
     */
    public AvatarView(double size) {
        // Círculo de fondo (visible solo en modo fallback de iniciales)
        this.background = new Circle(size / 2);

        // Iniciales centradas sobre el círculo (visibles solo en modo fallback)
        this.initialsLabel = new Label();
        this.initialsLabel.setTextFill(Color.WHITE);
        this.initialsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: " + (size * 0.4) + "px;");

        // Imagen recortada en círculo (visible solo cuando hay foto cargada)
        this.imageView = new ImageView();
        this.imageView.setFitWidth(size);
        this.imageView.setFitHeight(size);
        this.imageView.setPreserveRatio(true);
        // El clip hace que la imagen aparezca recortada en forma circular
        this.imageView.setClip(new Circle(size / 2, size / 2, size / 2));
        this.imageView.setVisible(false);

        setAlignment(Pos.CENTER);
        setMinSize(size, size);
        setMaxSize(size, size);
        getChildren().addAll(background, initialsLabel, imageView);
    }

    /**
     * Carga el avatar del usuario autenticado actualmente en {@link AppContext}.
     * Atajo conveniente para no tener que pasar los datos manualmente.
     */
    public void loadForCurrentUser() {
        AppContext ctx = AppContext.getInstance();
        loadForUser(ctx.getCurrentUserId(), ctx.getCurrentUsername(), ctx.hasAvatar());
    }

    /**
     * Carga el avatar del usuario indicado.
     *
     * <p>Si {@code hasAvatar} es {@code false} o {@code userId} es {@code null},
     * muestra el fallback de iniciales directamente sin realizar ninguna
     * petición HTTP. En caso contrario, descarga la imagen en un hilo
     * secundario y actualiza el componente al recibirla.</p>
     *
     * @param userId    identificador del usuario
     * @param username  nombre de usuario, usado para el fallback de iniciales
     * @param hasAvatar {@code true} si el usuario tiene una foto subida
     */
    public void loadForUser(Long userId, String username, boolean hasAvatar) {
        // Pintamos el fallback de inmediato para tener algo visible mientras carga
        renderFallback(username);

        // Si no tiene avatar o no hay userId no hacemos la petición HTTP
        if (!hasAvatar || userId == null) return;

        // Descargamos la imagen en un hilo secundario para no bloquear la UI.
        // Le damos nombre al hilo para facilitar la depuración si aparece en logs.
        Thread avatarThread = new Thread(() -> {
            try {
                ApiService api = AppContext.getInstance().getApiService();
                byte[] bytes = api.getBytes("/api/users/" + userId + "/avatar");

                if (bytes != null && bytes.length > 0) {
                    Image image = new Image(new ByteArrayInputStream(bytes));
                    // Actualizamos la UI siempre desde el hilo de JavaFX
                    Platform.runLater(() -> {
                        imageView.setImage(image);
                        imageView.setVisible(true);
                        initialsLabel.setVisible(false);
                        background.setVisible(false);
                    });
                }
            } catch (Exception e) {
                // Si falla la descarga nos quedamos con el fallback ya pintado
                log.warn("No se pudo cargar el avatar del usuario {}: {}", userId, e.getMessage());
            }
        }, "avatar-loader-" + userId); // nombre descriptivo para el hilo

        // Marcamos como daemon para que no impida el cierre de la app
        avatarThread.setDaemon(true);
        avatarThread.start();
    }

    /**
     * Fuerza la recarga del avatar del usuario actual.
     * Útil tras subir o eliminar la foto de perfil.
     */
    public void refresh() {
        loadForCurrentUser();
    }

    // -------------------------------------------------------------------------
    // Métodos privados
    // -------------------------------------------------------------------------

    /**
     * Muestra el fallback de iniciales: calcula las iniciales del username,
     * asigna un color determinista de la paleta y oculta el {@link ImageView}.
     *
     * @param username nombre de usuario del que se extraen las iniciales
     */
    private void renderFallback(String username) {
        if (username == null || username.isBlank()) username = "?";

        // Tomamos los dos primeros caracteres como iniciales, o uno si el nombre es muy corto
        String initials = username.length() >= 2
                ? username.substring(0, 2).toUpperCase()
                : username.substring(0, 1).toUpperCase();

        initialsLabel.setText(initials);
        background.setFill(Color.web(colorFor(username)));

        // Ocultamos la imagen y mostramos el fallback
        imageView.setImage(null);
        imageView.setVisible(false);
        initialsLabel.setVisible(true);
        background.setVisible(true);
    }

    /**
     * Devuelve un color de la paleta de forma determinista a partir del username.
     * El mismo nombre produce siempre el mismo color porque se basa en el hashCode.
     *
     * @param username nombre de usuario
     * @return color en formato hex seleccionado de {@link #PALETTE}
     */
    private static String colorFor(String username) {
        // Math.abs evita índices negativos si el hashCode es negativo
        int hash = Math.abs(username.hashCode());
        return PALETTE[hash % PALETTE.length];
    }


}
