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
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/**
 * AVATARCROPCONTROLLER
 *
 * Diálogo modal para recortar una imagen en círculo antes de subirla como avatar.
 *
 * Implementación basada en viewport del ImageView:
 *   - El ImageView siempre ocupa 256x256 fijos en la UI.
 *   - El viewport define qué región de la imagen original se muestra dentro.
 *   - Arrastrar/zoom modifican el viewport (x, y, width, height en coordenadas de la imagen original).
 *   - Al guardar, se renderiza el ImageView (ya con el viewport aplicado) a PNG.
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

    // Estado del viewport actual sobre la imagen original (en coords de la imagen)
    private double vpX, vpY, vpSize;

    // Estado del arrastre
    private double dragAnchorX, dragAnchorY;
    private double vpAnchorX, vpAnchorY;

    @FXML
    public void initialize() {
        // Máscara circular visual sobre el contenedor
        Circle clip = new Circle(OUTPUT_SIZE / 2.0, OUTPUT_SIZE / 2.0, OUTPUT_SIZE / 2.0);
        cropContainer.setClip(clip);

        // Slider de zoom: cambia el tamaño del viewport (menor viewport = más zoom)
        zoomSlider.valueProperty().addListener((obs, oldV, newV) -> updateZoom(newV.doubleValue()));

        // Arrastre para reposicionar el viewport
        imageView.setOnMousePressed(e -> {
            dragAnchorX = e.getSceneX();
            dragAnchorY = e.getSceneY();
            vpAnchorX = vpX;
            vpAnchorY = vpY;
        });
        imageView.setOnMouseDragged(e -> {
            // Convertimos delta de pantalla a delta en coords de imagen original.
            // El ImageView mide OUTPUT_SIZE pero muestra vpSize píxeles de imagen.
            double ratio = vpSize / OUTPUT_SIZE;
            double dx = (e.getSceneX() - dragAnchorX) * ratio;
            double dy = (e.getSceneY() - dragAnchorY) * ratio;
            setViewport(vpAnchorX - dx, vpAnchorY - dy, vpSize);
        });
    }

    /** Inyecta la imagen a recortar (llamar antes de showAndWait). */
    public void setImage(Image image) {
        this.sourceImage = image;
        imageView.setImage(image);

        // Viewport inicial: cuadrado centrado del tamaño del lado más corto.
        double minSide = Math.min(image.getWidth(), image.getHeight());
        vpSize = minSide;
        vpX = (image.getWidth() - minSide) / 2.0;
        vpY = (image.getHeight() - minSide) / 2.0;

        setViewport(vpX, vpY, vpSize);
        zoomSlider.setValue(1.0);
    }

    /** Aplica un valor de zoom (1.0 = cuadrado completo, 3.0 = solo 1/3 del lado). */
    private void updateZoom(double zoom) {
        double minSide = Math.min(sourceImage.getWidth(), sourceImage.getHeight());
        double newSize = minSide / zoom;

        // Centramos el zoom manteniendo el punto central del viewport actual
        double centerX = vpX + vpSize / 2.0;
        double centerY = vpY + vpSize / 2.0;
        setViewport(centerX - newSize / 2.0, centerY - newSize / 2.0, newSize);
    }

    /** Aplica un viewport clampeado para que no se salga de la imagen. */
    private void setViewport(double x, double y, double size) {
        double maxX = sourceImage.getWidth()  - size;
        double maxY = sourceImage.getHeight() - size;
        vpX = Math.max(0, Math.min(x, maxX));
        vpY = Math.max(0, Math.min(y, maxY));
        vpSize = size;
        imageView.setViewport(new Rectangle2D(vpX, vpY, vpSize, vpSize));
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

    /** Renderiza el cropContainer (ImageView + clip circular) a un PNG de 256x256. */
    private byte[] renderToPng() throws Exception {
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);

        WritableImage fxImage = new WritableImage(OUTPUT_SIZE, OUTPUT_SIZE);
        cropContainer.snapshot(params, fxImage);

        BufferedImage bImage = SwingFXUtils.fromFXImage(fxImage, null);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(bImage, "png", baos);
        return baos.toByteArray();
    }
}