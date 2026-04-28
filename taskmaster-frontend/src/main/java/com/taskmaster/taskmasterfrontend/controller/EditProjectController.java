package com.taskmaster.taskmasterfrontend.controller;

import com.taskmaster.taskmasterfrontend.util.AppContext;
import com.taskmaster.taskmasterfrontend.util.LanguageManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.net.http.HttpResponse;

/**
 * Controlador del diálogo de edición de proyecto.
 *
 * <p>Carga los datos actuales del proyecto desde el backend al recibir
 * el identificador, y permite modificar el nombre, la descripción, la
 * categoría, el estado y la prioridad. Los valores de los combos se
 * muestran localizados y se traducen al código del backend antes de enviar.</p>
 *
 * @author Carlos
 */
public class EditProjectController {

    @FXML private TextField nameField;
    @FXML private TextArea descriptionField;
    @FXML private ComboBox<String> categoryCombo;
    @FXML private ComboBox<String> statusCombo;
    @FXML private ComboBox<String> priorityCombo;
    @FXML private Label errorLabel;

    private Long projectId;
    private Runnable onProjectUpdated;

    private final LanguageManager lm = LanguageManager.getInstance();

    /**
     * Registra el callback que se ejecutará tras actualizar el proyecto correctamente.
     *
     * @param callback Acción a ejecutar al completar la actualización.
     */
    public void setOnProjectUpdated(Runnable callback) {
        this.onProjectUpdated = callback;
    }

    /**
     * Inicializa los combos de categoría, estado y prioridad con sus
     * valores localizados y sus selecciones por defecto.
     */
    @FXML
    public void initialize() {
        categoryCombo.setItems(FXCollections.observableArrayList(
                lm.get("category.PERSONAL"), lm.get("category.ESTUDIOS"), lm.get("category.TRABAJO")));
        categoryCombo.setValue(lm.get("category.PERSONAL"));

        statusCombo.setItems(FXCollections.observableArrayList(
                lm.get("status.todo"), lm.get("status.inprogress"),
                lm.get("status.done.project"), lm.get("status.cancelled.project")));
        statusCombo.setValue(lm.get("status.todo"));

        priorityCombo.setItems(FXCollections.observableArrayList(
                lm.get("priority.low"), lm.get("priority.medium"),
                lm.get("priority.high"), lm.get("priority.urgent")));
        priorityCombo.setValue(lm.get("priority.medium"));
    }

    /**
     * Precarga el formulario con el nombre del proyecto e inicia la carga
     * asíncrona del resto de datos desde el backend.
     *
     * @param projectId   Identificador del proyecto a editar.
     * @param projectName Nombre actual del proyecto.
     */
    public void initData(Long projectId, String projectName) {
        this.projectId = projectId;
        nameField.setText(projectName);

        // Cargamos los datos completos del proyecto desde el backend
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/projects/" + projectId);

                if (response.statusCode() == 200) {
                    com.fasterxml.jackson.databind.JsonNode project =
                            new com.fasterxml.jackson.databind.ObjectMapper()
                                    .readTree(response.body());

                    javafx.application.Platform.runLater(() -> {
                        if (project.has("description") && !project.get("description").isNull()) {
                            descriptionField.setText(project.get("description").asText());
                        }
                        if (project.has("category") && !project.get("category").isNull()) {
                            String cat = project.get("category").asText();
                            categoryCombo.setValue(switch (cat) {
                                case "ESTUDIOS" -> lm.get("category.ESTUDIOS");
                                case "TRABAJO"  -> lm.get("category.TRABAJO");
                                default         -> lm.get("category.PERSONAL");
                            });
                        }
                        if (project.has("status") && !project.get("status").isNull()) {
                            String status = project.get("status").asText();
                            statusCombo.setValue(switch (status) {
                                case "IN_PROGRESS" -> lm.get("status.inprogress");
                                case "DONE"        -> lm.get("status.done.project");
                                case "CANCELLED"   -> lm.get("status.cancelled.project");
                                default            -> lm.get("status.todo");
                            });
                        }
                        if (project.has("priority") && !project.get("priority").isNull()) {
                            String priority = project.get("priority").asText();
                            priorityCombo.setValue(switch (priority) {
                                case "LOW"    -> lm.get("priority.low");
                                case "HIGH"   -> lm.get("priority.high");
                                case "URGENT" -> lm.get("priority.urgent");
                                default       -> lm.get("priority.medium");
                            });
                        }
                        nameField.deselect();
                        nameField.getParent().requestFocus();
                    });
                }
            } catch (Exception e) {
                javafx.application.Platform.runLater(() ->
                        showError(lm.get("edit.project.error.load")));
            }
        }).start();
    }

    /**
     * Valida el formulario y envía los datos actualizados al backend.
     * Cierra el diálogo si la operación es exitosa.
     */
    @FXML
    private void handleSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            showError(lm.get("edit.project.error.name"));
            return;
        }

        if (categoryCombo.getValue() == null) {
            showError(lm.get("edit.project.error.category"));
            return;
        }

        new Thread(() -> {
            try {
                String url = "/api/projects/" + projectId +
                        "?name=" + java.net.URLEncoder.encode(name, "UTF-8") +
                        "&description=" + java.net.URLEncoder.encode(
                        descriptionField.getText().trim(), "UTF-8") +
                        "&category=" + mapCategory(categoryCombo.getValue()) +
                        "&status=" + mapStatus(statusCombo.getValue()) +
                        "&priority=" + mapPriority(priorityCombo.getValue());

                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .putNoBody(url);

                Platform.runLater(() -> {
                    if (response.statusCode() == 200) {
                        if (onProjectUpdated != null) onProjectUpdated.run();
                        closeDialog();
                    } else {
                        showError(lm.get("common.error.save"));
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> showError(lm.get("error.connection")));
            }
        }).start();
    }

    /**
     * Cierra el diálogo sin guardar cambios.
     */
    @FXML
    private void handleCancel() {
        closeDialog();
    }

    /**
     * Cierra el diálogo actual.
     */
    private void closeDialog() {
        nameField.getScene().getWindow().hide();
    }

    /**
     * Traduce la etiqueta localizada de categoría al código del backend.
     *
     * @param label Etiqueta localizada seleccionada en el combo.
     * @return Código de categoría ({@code "PERSONAL"}, {@code "ESTUDIOS"} o {@code "TRABAJO"}).
     */
    private String mapCategory(String label) {
        if (label.equals(lm.get("category.ESTUDIOS")))      return "ESTUDIOS";
        else if (label.equals(lm.get("category.TRABAJO")))  return "TRABAJO";
        else return "PERSONAL";
    }

    /**
     * Traduce la etiqueta localizada de estado al código del backend.
     *
     * @param label Etiqueta localizada seleccionada en el combo.
     * @return Código de estado ({@code "TODO"}, {@code "IN_PROGRESS"}, {@code "DONE"} o {@code "CANCELLED"}).
     */
    private String mapStatus(String label) {
        if (label.equals(lm.get("status.inprogress")))           return "IN_PROGRESS";
        else if (label.equals(lm.get("status.done.project")))    return "DONE";
        else if (label.equals(lm.get("status.cancelled.project"))) return "CANCELLED";
        else return "TODO";
    }

    /**
     * Traduce la etiqueta localizada de prioridad al código del backend.
     *
     * @param label Etiqueta localizada seleccionada en el combo.
     * @return Código de prioridad ({@code "LOW"}, {@code "MEDIUM"}, {@code "HIGH"} o {@code "URGENT"}).
     */
    private String mapPriority(String label) {
        if (label.equals(lm.get("priority.low")))         return "LOW";
        else if (label.equals(lm.get("priority.high")))   return "HIGH";
        else if (label.equals(lm.get("priority.urgent"))) return "URGENT";
        else return "MEDIUM";
    }

    /**
     * Muestra un mensaje de error en la etiqueta de error del formulario.
     *
     * @param message Mensaje de error a mostrar.
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
