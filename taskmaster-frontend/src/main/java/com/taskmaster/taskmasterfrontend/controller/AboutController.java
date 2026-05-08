package com.taskmaster.taskmasterfrontend.controller;

import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import com.taskmaster.taskmasterfrontend.util.ThemeManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.Year;
import java.util.List;

/**
 * Controlador de la pantalla "Acerca de TaskMaster".
 *
 * <p>Muestra información general de la aplicación: versión, descripción,
 * copyright y badges de tecnologías utilizadas. También gestiona la apertura
 * de sub-diálogos modales para términos de uso, política de privacidad,
 * licencias de terceros y reconocimientos.</p>
 *
 * @author Carlos
 */
public class AboutController {

    @FXML private VBox aboutRoot;
    @FXML private Label versionLabel;
    @FXML private Label  descriptionLabel;
    @FXML private Label  copyrightLabel;

    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Inicializa la vista con los textos localizados, el año de copyright
     * y el color apropiado según el tema activo.
     */
    @FXML
    public void initialize() {
        versionLabel.setText(lm.get("about.version"));
        descriptionLabel.setText(lm.get("about.description"));
        copyrightLabel.setText("© " + Year.now().getValue() + " Carlos Riera · TaskMaster TFG DAM");

        // Adaptamos el color de los textos secundarios al tema activo
        boolean dark = ThemeManager.getInstance().isDark();
        String mutedColor = dark ? "#6b6b8a" : "#aaaaaa";
        versionLabel.setStyle("-fx-text-fill: " + mutedColor + ";");
        copyrightLabel.setStyle("-fx-text-fill: " + mutedColor + ";");
    }

    /**
     * Abre el diálogo modal con los términos de uso de la aplicación.
     */
    @FXML
    private void handleTerms() {
        showInfoDialog(lm.get("about.btn.terms"), lm.get("about.terms.content"));
    }

    /**
     * Abre el diálogo modal con la política de privacidad.
     */
    @FXML
    private void handlePrivacy() {
        showInfoDialog(lm.get("about.btn.privacy"), lm.get("about.privacy.content"));
    }

    /**
     * Abre el diálogo modal con las licencias de las bibliotecas de terceros.
     */
    @FXML
    private void handleLicenses() {
        showLicensesDialog();
    }

    /**
     * Abre el diálogo modal con los reconocimientos y créditos.
     */
    @FXML
    private void handleAcknowledgements() {
        showInfoDialog(lm.get("about.btn.acknowledgements"), lm.get("about.acknowledgements.content"));
    }

    // -------------------------------------------------------------------------
    // Diálogos modales
    // -------------------------------------------------------------------------

    /**
     * Muestra un diálogo modal genérico con un título y un cuerpo de texto.
     *
     * @param title título del diálogo
     * @param body  contenido de texto que se mostrará en el área de texto
     */
    private void showInfoDialog(String title, String body) {
        Stage dialog = buildDialogStage(title, 520, 440);

        HBox header = buildDialogHeader(title);

        // Área de texto de solo lectura con scroll automático
        TextArea textArea = new TextArea(body);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.getStyleClass().add("about-text-area");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        HBox footer = buildDialogFooter(dialog);

        VBox root = new VBox(header, textArea, footer);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        root.getStyleClass().add("dialog-body");
        root.setPrefHeight(440);

        showDialog(dialog, root, 520, 440);
    }

    /**
     * Muestra un diálogo modal con una tabla de licencias de las
     * bibliotecas utilizadas en el proyecto.
     */
    private void showLicensesDialog() {
        Stage dialog = buildDialogStage(lm.get("about.btn.licenses"), 600, 460);

        HBox header = buildDialogHeader(lm.get("about.btn.licenses"));

        // Tabla de licencias con tres columnas
        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().add("activity-table");
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<String[], String> colLib = new TableColumn<>(lm.get("about.licenses.col.library"));
        colLib.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[0]));
        colLib.setPrefWidth(200);

        TableColumn<String[], String> colLic = new TableColumn<>(lm.get("about.licenses.col.license"));
        colLic.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[1]));
        colLic.setPrefWidth(120);

        TableColumn<String[], String> colNote = new TableColumn<>(lm.get("about.licenses.col.note"));
        colNote.setCellValueFactory(c -> new SimpleStringProperty(c.getValue()[2]));

        table.getColumns().addAll(List.of(colLib, colLic, colNote));

        // Datos de licencias
        String[][] licenseData = {
                { "JavaFX 21.0.6",         "GPL v2 + Classpath",  "OpenJFX — openjfx.io" },
                { "Spring Boot 3.5.0",     "Apache 2.0",          "spring.io/projects/spring-boot" },
                { "Spring Security",       "Apache 2.0",          "spring.io/projects/spring-security" },
                { "Spring Data JPA",       "Apache 2.0",          "spring.io/projects/spring-data-jpa" },
                { "Hibernate ORM",         "LGPL 2.1",            "hibernate.org" },
                { "H2 Database",           "MPL 2.0 / EPL 1.0",   "h2database.com" },
                { "PostgreSQL JDBC",       "BSD 2-Clause",        "jdbc.postgresql.org" },
                { "Jackson Databind",      "Apache 2.0",          "github.com/FasterXML/jackson" },
                { "Maven",                 "Apache 2.0",          "maven.apache.org" },
                { "Lombok",                "MIT",                  "projectlombok.org" },
        };

        for (String[] row : licenseData) {
            table.getItems().add(row);
        }

        HBox footer = buildDialogFooter(dialog);

        VBox tableWrapper = new VBox(table);
        tableWrapper.setPadding(new Insets(12, 20, 0, 20));
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tableWrapper, Priority.ALWAYS);

        VBox root = new VBox(header, tableWrapper, footer);
        VBox.setVgrow(tableWrapper, Priority.ALWAYS);
        root.getStyleClass().add("dialog-body");

        showDialog(dialog, root, 600, 460);
    }

    /**
     * Construye la cabecera estándar de un diálogo con el título indicado.
     *
     * @param title título a mostrar en la cabecera
     * @return {@link HBox} con la cabecera configurada
     */
    private HBox buildDialogHeader(String title) {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-header-title");
        header.getChildren().add(titleLabel);

        return header;
    }

    /**
     * Construye el pie de diálogo estándar con el botón Cerrar.
     *
     * @param dialog diálogo que se cerrará al pulsar el botón
     * @return {@link HBox} con el pie configurado
     */
    private HBox buildDialogFooter(Stage dialog) {
        Button closeBtn = new Button(lm.get("common.close"));
        closeBtn.getStyleClass().add("btn-confirm-accent");
        closeBtn.setOnAction(e -> dialog.close());

        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 20, 16, 20));

        return footer;
    }

    /**
     * Construye y configura un {@link Stage} modal con las dimensiones indicadas.
     *
     * @param title título de la ventana del diálogo
     * @param w     anchura en píxeles
     * @param h     altura en píxeles
     * @return {@link Stage} configurado listo para recibir su escena
     */
    private Stage buildDialogStage(String title, double w, double h) {
        Stage dialog = new Stage();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(aboutRoot.getScene().getWindow());
        dialog.setResizable(false);

        return dialog;
    }

    /**
     * Aplica la escena al diálogo, hereda los estilos CSS activos
     * del scene principal y muestra el diálogo de forma bloqueante.
     *
     * @param dialog diálogo al que aplicar la escena
     * @param root   contenido raíz a mostrar
     * @param w      anchura de la escena en píxeles
     * @param h      altura de la escena en píxeles
     */
    private void showDialog(Stage dialog, VBox root, double w, double h) {
        Scene scene = new Scene(root, w, h);
        // Heredamos el CSS activo del scene principal para que el tema se aplique al diálogo
        scene.getStylesheets().addAll(aboutRoot.getScene().getStylesheets());
        dialog.setScene(scene);
        dialog.showAndWait();
    }
}