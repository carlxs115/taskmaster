package com.taskmaster.taskmasterfrontend.controller;

import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.time.Year;

/**
 * ABOUTCONTROLLER
 *
 * Controlador de la pantalla "Acerca de TaskMaster".
 * Muestra información de la aplicación, autor, tecnologías y
 * abre sub-diálogos para términos, privacidad, licencias y reconocimientos.
 */
public class AboutController {

    @FXML private VBox aboutRoot;
    @FXML private Label versionLabel;
    @FXML private Label  descriptionLabel;
    @FXML private Label  copyrightLabel;
    @FXML private FlowPane techBadges;

    private final LanguageManager lm = LanguageManager.getInstance();

    // ── Datos de cada badge tecnológico: nombre, fondo, texto ──
    private static final String[][] TECH_DATA = {
            { "JavaFX 21.0.6", "#dbeafe", "#1e40af" },
            { "Spring Boot 3", "#dcfce7", "#166534" },
            { "JDK 25",        "#f3e8ff", "#6b21a8" },
            { "H2 / PostgreSQL","#fef3c7", "#92400e" },
            { "Maven",         "#fee2e2", "#991b1b" },
            { "IntelliJ IDEA", "#e0f2fe", "#0c4a6e" },
    };

    @FXML
    public void initialize() {
        versionLabel.setText(lm.get("about.version"));
        descriptionLabel.setText(lm.get("about.description"));
        copyrightLabel.setText("© " + Year.now().getValue() + " Carlos Riera · TaskMaster TFG DAM");

        buildTechBadges();
    }

    // ─────────────────────────────────────────────
    //  Badges de tecnología
    // ─────────────────────────────────────────────
    private void buildTechBadges() {
        techBadges.getChildren().clear();
        for (String[] tech : TECH_DATA) {
            Label badge = new Label(tech[0]);
            badge.setStyle(
                    "-fx-font-size: 11px; " +
                            "-fx-padding: 4 10 4 10; " +
                            "-fx-background-radius: 10px; " +
                            "-fx-background-color: " + tech[1] + "; " +
                            "-fx-text-fill: " + tech[2] + ";"
            );
            techBadges.getChildren().add(badge);
        }
    }

    // ─────────────────────────────────────────────
    //  Handlers de los botones legales
    // ─────────────────────────────────────────────
    @FXML
    private void handleTerms() {
        showInfoDialog(
                lm.get("about.btn.terms"),
                lm.get("about.terms.content")
        );
    }

    @FXML
    private void handlePrivacy() {
        showInfoDialog(
                lm.get("about.btn.privacy"),
                lm.get("about.privacy.content")
        );
    }

    @FXML
    private void handleLicenses() {
        showLicensesDialog();
    }

    @FXML
    private void handleAcknowledgements() {
        showInfoDialog(
                lm.get("about.btn.acknowledgements"),
                lm.get("about.acknowledgements.content")
        );
    }

    // ─────────────────────────────────────────────
    //  Diálogo genérico de texto
    // ─────────────────────────────────────────────
    private void showInfoDialog(String title, String body) {
        Stage dialog = buildDialogStage(title, 520, 440);

        // Cabecera
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-header-title");
        header.getChildren().add(titleLabel);

        // Cuerpo con scroll
        TextArea textArea = new TextArea(body);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.getStyleClass().add("about-text-area");
        VBox.setVgrow(textArea, Priority.ALWAYS);

        // Botón cerrar
        Button closeBtn = new Button(lm.get("common.close"));
        closeBtn.getStyleClass().add("btn-confirm-accent");
        closeBtn.setOnAction(e -> dialog.close());

        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 20, 16, 20));

        VBox root = new VBox(header, textArea, footer);
        VBox.setVgrow(textArea, Priority.ALWAYS);
        root.getStyleClass().add("dialog-body");
        root.setPrefHeight(440);

        showDialog(dialog, root, 520, 440);
    }

    // ─────────────────────────────────────────────
    //  Diálogo de licencias con tabla
    // ─────────────────────────────────────────────

    private void showLicensesDialog() {
        Stage dialog = buildDialogStage(lm.get("about.btn.licenses"), 600, 460);

        // Cabecera
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("dialog-header");
        Label titleLabel = new Label(lm.get("about.btn.licenses"));
        titleLabel.getStyleClass().add("dialog-header-title");
        header.getChildren().add(titleLabel);

        // Tabla
        TableView<String[]> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.getStyleClass().add("activity-table");
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<String[], String> colLib = new TableColumn<>(lm.get("about.licenses.col.library"));
        colLib.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[0]));
        colLib.setPrefWidth(200);

        TableColumn<String[], String> colLic = new TableColumn<>(lm.get("about.licenses.col.license"));
        colLic.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[1]));
        colLic.setPrefWidth(120);

        TableColumn<String[], String> colNote = new TableColumn<>(lm.get("about.licenses.col.note"));
        colNote.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue()[2]));

        table.getColumns().addAll(colLib, colLic, colNote);

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

        // Botón cerrar
        Button closeBtn = new Button(lm.get("common.close"));
        closeBtn.getStyleClass().add("btn-confirm-accent");
        closeBtn.setOnAction(e -> dialog.close());

        HBox footer = new HBox(closeBtn);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.setPadding(new Insets(12, 20, 16, 20));

        VBox tableWrapper = new VBox(table);
        tableWrapper.setPadding(new Insets(12, 20, 0, 20));
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(tableWrapper, Priority.ALWAYS);

        VBox root = new VBox(header, tableWrapper, footer);
        VBox.setVgrow(tableWrapper, Priority.ALWAYS);
        root.getStyleClass().add("dialog-body");

        showDialog(dialog, root, 600, 460);
    }

    // ─────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────

    private Stage buildDialogStage(String title, double w, double h) {
        Stage dialog = new Stage();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(aboutRoot.getScene().getWindow());
        dialog.setResizable(false);
        return dialog;
    }

    private void showDialog(Stage dialog, VBox root, double w, double h) {
        Scene scene = new Scene(root, w, h);
        // Inyectar el mismo CSS activo que usa el Scene principal
        scene.getStylesheets().addAll(
                aboutRoot.getScene().getStylesheets()
        );
        dialog.setScene(scene);
        dialog.showAndWait();
    }

}
