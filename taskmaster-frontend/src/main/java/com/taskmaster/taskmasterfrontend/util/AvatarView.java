package com.taskmaster.taskmasterfrontend.util;

import com.taskmaster.taskmasterfrontend.service.ApiService;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import javafx.geometry.Pos;

import java.io.ByteArrayInputStream;

public class AvatarView extends StackPane {

    private final double size;
    private final Circle background;
    private final Label initialsLabel;
    private final ImageView imageView;

    /** Paleta de colores para el fallback (estilo Google/Discord). */
    private static final String[] PALETTE = {
            "#7c3aed", "#ec4899", "#f59e0b", "#22c55e", "#3b82f6",
            "#e11d48", "#14b8a6", "#a855f7", "#f97316", "#06b6d4"
    };

    public AvatarView(double size) {
        this.size = size;

        // Círculo de fondo (visible solo en modo fallback)
        this.background = new Circle(size / 2);

        // Iniciales centradas (visibles solo en modo fallback)
        this.initialsLabel = new Label();
        this.initialsLabel.setTextFill(Color.WHITE);
        this.initialsLabel.setStyle("-fx-font-weight: bold; -fx-font-size: " + (size * 0.4) + "px;");

        // Imagen recortada en círculo (visible solo cuando hay foto)
        this.imageView = new ImageView();
        this.imageView.setFitWidth(size);
        this.imageView.setFitHeight(size);
        this.imageView.setPreserveRatio(true);
        this.imageView.setClip(new Circle(size / 2, size / 2, size / 2));
        this.imageView.setVisible(false);

        setAlignment(Pos.CENTER);
        setMinSize(size, size);
        setMaxSize(size, size);
        getChildren().addAll(background, initialsLabel, imageView);
    }

    /** Carga el avatar del usuario autenticado (usa AppContext). */
    public void loadForCurrentUser() {
        AppContext ctx = AppContext.getInstance();
        loadForUser(ctx.getCurrentUserId(), ctx.getCurrentUsername(), ctx.hasAvatar());
    }

    /**
     * Carga el avatar de cualquier usuario.
     * Si hasAvatar es false, muestra directamente las iniciales (evita petición HTTP innecesaria).
     * Si hasAvatar es true, lanza la petición en background y actualiza al volver.
     */
    public void loadForUser(Long userId, String username, boolean hasAvatar) {
        // Primero pintamos el fallback para tener algo visible de inmediato
        renderFallback(username);

        if (!hasAvatar || userId == null) return;

        // Petición asíncrona para no bloquear la UI
        new Thread(() -> {
            try {
                ApiService api = AppContext.getInstance().getApiService();
                byte[] bytes = api.getBytes("/api/users/" + userId + "/avatar");
                if (bytes != null && bytes.length > 0) {
                    Image image = new Image(new ByteArrayInputStream(bytes));
                    Platform.runLater(() -> {
                        imageView.setImage(image);
                        imageView.setVisible(true);
                        initialsLabel.setVisible(false);
                        background.setVisible(false);
                    });
                }
            } catch (Exception e) {
                // Si falla, nos quedamos con el fallback ya pintado
                System.err.println("No se pudo cargar el avatar del usuario " + userId + ": " + e.getMessage());
            }
        }).start();
    }

    /** Dibuja las iniciales sobre un círculo de color determinista. */
    private void renderFallback(String username) {
        if (username == null || username.isBlank()) username = "?";

        String initials = username.length() >= 2
                ? username.substring(0, 2).toUpperCase()
                : username.substring(0, 1).toUpperCase();

        initialsLabel.setText(initials);
        background.setFill(Color.web(colorFor(username)));

        imageView.setImage(null);
        imageView.setVisible(false);
        initialsLabel.setVisible(true);
        background.setVisible(true);
    }

    /**
     * Devuelve un color determinista a partir del username.
     * Mismo username -> siempre mismo color. Distintos usernames -> colores (probablemente) distintos.
     */
    private static String colorFor(String username) {
        int hash = Math.abs(username.hashCode());
        return PALETTE[hash % PALETTE.length];
    }

    /** Fuerza un refresco (p. ej. tras subir o borrar el avatar propio). */
    public void refresh() {
        loadForCurrentUser();
    }
}
