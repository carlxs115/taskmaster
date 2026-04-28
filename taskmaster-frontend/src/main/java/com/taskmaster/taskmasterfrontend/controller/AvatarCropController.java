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
 * Controlador del diálogo de recorte circular de avatar.
 *
 * <p>Permite al usuario ajustar y recortar una imagen en forma circular
 * antes de subirla como foto de perfil. El recorte se gestiona mediante
 * el viewport del {@link ImageView}: el arrastre desplaza la región visible
 * y el slider de zoom reduce el tamaño del viewport, ampliando la imagen.
 * Al guardar, se renderiza el resultado a un PNG de {@value #OUTPUT_SIZE}x{@value #OUTPUT_SIZE} píxeles.</p>
 *
 * @author Carlos
 */
public class AvatarCropController {

    @FXML private StackPane cropContainer;
    @FXML private ImageView imageView;
    @FXML private Slider zoomSlider;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;

    /** Tamaño en píxeles del avatar resultante (ancho y alto). */
    private static final int OUTPUT_SIZE = 256;

    private Image sourceImage;
    private byte[] resultBytes;
    private boolean saved = false;

    // Estado del viewport actual sobre la imagen original (en coords de la imagen)
    private double vpX, vpY, vpSize;

    // Estado del arrastre
    private double dragAnchorX, dragAnchorY;
    private double vpAnchorX, vpAnchorY;

    /**
     * Inicializa el diálogo configurando la máscara circular, el listener
     * del slider de zoom y los manejadores de arrastre sobre el {@link ImageView}.
     */
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

    /**
     * Establece la imagen a recortar e inicializa el viewport centrado
     * sobre el cuadrado central de la imagen.
     * Debe llamarse antes de mostrar el diálogo.
     *
     * @param image Imagen fuente a recortar.
     */
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

    /**
     * Actualiza el tamaño del viewport según el nivel de zoom indicado,
     * manteniendo el punto central del recorte actual.
     *
     * @param zoom Factor de zoom (1.0 = sin zoom, 3.0 = zoom máximo).
     */
    private void updateZoom(double zoom) {
        double minSide = Math.min(sourceImage.getWidth(), sourceImage.getHeight());
        double newSize = minSide / zoom;

        // Centramos el zoom manteniendo el punto central del viewport actual
        double centerX = vpX + vpSize / 2.0;
        double centerY = vpY + vpSize / 2.0;
        setViewport(centerX - newSize / 2.0, centerY - newSize / 2.0, newSize);
    }

    /**
     * Aplica un nuevo viewport sobre la imagen, clampeando las coordenadas
     * para que no se salgan de los límites de la imagen original.
     *
     * @param x    Coordenada X del vértice superior izquierdo del viewport.
     * @param y    Coordenada Y del vértice superior izquierdo del viewport.
     * @param size Tamaño del lado del viewport cuadrado en píxeles de la imagen original.
     */
    private void setViewport(double x, double y, double size) {
        double maxX = sourceImage.getWidth()  - size;
        double maxY = sourceImage.getHeight() - size;
        vpX = Math.max(0, Math.min(x, maxX));
        vpY = Math.max(0, Math.min(y, maxY));
        vpSize = size;
        imageView.setViewport(new Rectangle2D(vpX, vpY, vpSize, vpSize));
    }

    /**
     * Guarda el recorte actual como PNG y cierra el diálogo.
     */
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

    /**
     * Cancela la operación y cierra el diálogo sin guardar.
     */
    @FXML
    private void handleCancel() {
        saved = false;
        close();
    }

    private void close() {
        ((Stage) cancelButton.getScene().getWindow()).close();
    }

    /**
     * Devuelve los bytes PNG del avatar recortado.
     *
     * @return Array de bytes con el PNG resultante, o {@code null} si se canceló.
     */
    public byte[] getCroppedImageBytes() {
        return saved ? resultBytes : null;
    }

    /**
     * Renderiza el contenedor de recorte con la máscara circular aplicada
     * a un PNG de {@value #OUTPUT_SIZE}x{@value #OUTPUT_SIZE} píxeles.
     *
     * @return Array de bytes con la imagen renderizada en formato PNG.
     * @throws Exception Si se produce un error durante el renderizado o la codificación.
     */
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