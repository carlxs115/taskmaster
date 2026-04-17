package com.taskmaster.taskmasterfrontend.controller;

import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.geometry.Rectangle2D;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;

/**
 * AVATARCROPCONTROLLER
 *
 * Diálogo modal para recortar una imagen en círculo antes de subirla como avatar.
 *
 * Funcionalidad:
 *   - El usuario selecciona una imagen (pasada desde el controller padre).
 *   - La imagen se muestra en un área circular fija de 256x256 px.
 *   - Se puede arrastrar la imagen para reposicionarla.
 *   - Se puede ajustar el zoom con un slider (1.0x - 3.0x).
 *   - Al guardar, se genera un PNG de 256x256 ya recortado en círculo.
 *
 * El resultado se obtiene con getCroppedImageBytes() tras showAndWait().
 */
public class AvatarCropController {

    @FXML private StackPane cropContainer;
    @FXML private ImageView imageView;
    @FXML private Slider zoomSlider;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    /** Tamaño final del avatar guardado, en píxeles. */
    private static final int OUTPUT_SIZE = 256;

    private Image sourceImage;
    private byte[] resultBytes;
    private boolean saved = false;

    // Estado del arrastre
    private double dragAnchorX, dragAnchorY;
    private double imageTranslateAnchorX, imageTranslateAnchorY;

    @FXML
    public void initialize() {
        // Máscara circular visual en el contenedor
        Circle clip = new Circle(OUTPUT_SIZE / 2.0, OUTPUT_SIZE / 2.0, OUTPUT_SIZE / 2.0);
        cropContainer.setClip(clip);

        // Slider de zoom: escala la imagen en vivo
        zoomSlider.valueProperty().addListener((obs, oldV, newV) -> {
            imageView.setScaleX(newV.doubleValue());
            imageView.setScaleY(newV.doubleValue());
        });

        // Arrastrar para reposicionar
        imageView.setOnMousePressed(e -> {
            dragAnchorX = e.getSceneX();
            dragAnchorY = e.getSceneY();
            imageTranslateAnchorX = imageView.getTranslateX();
            imageTranslateAnchorY = imageView.getTranslateY();
        });
        imageView.setOnMouseDragged(e -> {
            double dx = e.getSceneX() - dragAnchorX;
            double dy = e.getSceneY() - dragAnchorY;
            imageView.setTranslateX(imageTranslateAnchorX + dx);
            imageView.setTranslateY(imageTranslateAnchorY + dy);
        });
    }

    /** Inyecta la imagen a recortar (llamar antes de showAndWait). */
    public void setImage(Image image) {
        this.sourceImage = image;
        imageView.setImage(image);

        // Ajuste inicial: que la imagen cubra el área circular (cover, no contain)
        double scale = Math.max(
                OUTPUT_SIZE / image.getWidth(),
                OUTPUT_SIZE / image.getHeight()
        );
        imageView.setFitWidth(image.getWidth() * scale);
        imageView.setFitHeight(image.getHeight() * scale);
        imageView.setTranslateX(0);
        imageView.setTranslateY(0);
        imageView.setScaleX(1.0);
        imageView.setScaleY(1.0);
        zoomSlider.setValue(1.0);
    }

    @FXML
    private void handleSave() {
        try {
            resultBytes = renderToPng();
            saved = true;
            close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void handleCancel() {
        saved = false;
        close();
    }

    private void close() {
        ((Stage) cancelButton.getScene().getWindow()).close();
    }

    /** Devuelve los bytes PNG del recorte, o null si se canceló. */
    public byte[] getCroppedImageBytes() {
        return saved ? resultBytes : null;
    }

    /**
     * Renderiza el contenido del cropContainer (ya clipado circularmente)
     * a un PNG de 256x256. Usa JavaFX Snapshot + conversión via SwingFXUtils.
     */
    private byte[] renderToPng() throws Exception {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        // Recortamos exactamente al área circular visible
        params.setViewport(new Rectangle2D(0, 0, OUTPUT_SIZE, OUTPUT_SIZE));

        WritableImage fxImage = new WritableImage(OUTPUT_SIZE, OUTPUT_SIZE);
        cropContainer.snapshot(params, fxImage);

        BufferedImage bImage = SwingFXUtils.fromFXImage(fxImage, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bImage, "png", baos);
        return baos.toByteArray();
    }
}
