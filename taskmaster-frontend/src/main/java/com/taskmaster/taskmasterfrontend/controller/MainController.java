package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.AppContext;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MAINCONTROLLER
 *
 * Controlador de la pantalla principal.
 * Gestiona la lista de proyectos en el sidebar y las tareas en el panel derecho.
 */
public class MainController {

    @FXML private Label usernameLabel;
    @FXML private VBox projectListContainer;
    @FXML private Label projectTitleLabel;
    @FXML private VBox taskContainer;
    @FXML private Label emptyLabel;
    @FXML private Button newTaskButton;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> priorityFilter;
    @FXML private HBox taskFiltersBar;
    @FXML private VBox projectCardsContainer;
    @FXML private Label emptyProjectsLabel;
    @FXML private ComboBox<String> projectStatusFilter;
    @FXML private ComboBox<String> projectPriorityFilter;
    @FXML private VBox projectsPanel;
    @FXML private VBox taskPanel;

    private Long selectedProjectId;
    private String selectedCategory;
    private TrashController trashController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private final List<Long> projectIds = new ArrayList<>();

    private javafx.scene.Node originalRightPanel;

    // ─── colores por categoría para las barras de progreso ───────────────────
    private static final String COLOR_PERSONAL  = "#a78bfa";
    private static final String COLOR_ESTUDIOS  = "#34d399";
    private static final String COLOR_TRABAJO   = "#fb923c";
    private static final String COLOR_DEFAULT   = "#a78bfa";

    @FXML
    public void initialize() {
        // Mostramos el nombre del usuario autenticado
        usernameLabel.setText(AppContext.getInstance().getCurrentUsername());

        statusFilter.setItems(FXCollections.observableArrayList(
                "Todos", "TODO", "IN_PROGRESS", "DONE", "CANCELLED"
        ));
        statusFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) handleStatusFilter();
                }
        );
        priorityFilter.setItems(FXCollections.observableArrayList(
                "Todas", "LOW", "MEDIUM", "HIGH", "URGENT"
        ));
        priorityFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> {
                    if (newVal != null) handlePriorityFilter();
                }
        );
        projectStatusFilter.setItems(FXCollections.observableArrayList(
                "Todos", "TODO", "IN_PROGRESS", "DONE", "CANCELLED"
        ));
        projectPriorityFilter.setItems(FXCollections.observableArrayList(
                "Todas", "LOW", "MEDIUM", "HIGH", "URGENT"
        ));

        loadProjects();
        loadHome();
    }

    /**
     * LOAD PROJECTS (sidebar + panel central)
     */
    public void loadProjects() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/projects");

                if (response.statusCode() == 200) {
                    JsonNode projects = objectMapper.readTree(response.body());

                    List<String> names = new ArrayList<>();
                    List<Long> ids = new ArrayList<>();
                    List<String> statuses = new ArrayList<>();
                    List<String> priorities = new ArrayList<>();
                    List<String> categories = new ArrayList<>();

                    for (JsonNode p : projects) {
                        names.add(p.get("name").asText());
                        ids.add(p.get("id").asLong());
                        statuses.add(p.has("status") && !p.get("status").isNull()
                                ? p.get("status").asText() : "TODO");
                        priorities.add(p.has("priority") && !p.get("priority").isNull()
                                ? p.get("priority").asText() : "MEDIUM");
                        categories.add(p.has("category") && !p.get("category").isNull()
                                ? p.get("category").asText() : "PERSONAL");
                    }

                    Platform.runLater(() -> {
                        projectIds.clear();
                        projectIds.addAll(ids);
                        renderSideBar(names, ids);
                        renderProjectCards(names, ids, statuses, priorities, categories);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert("Error", "No se pudieron cargar los proyectos"));
            }
        }).start();
    }

    private void renderSideBar(List<String> names, List<Long> ids) {
        projectListContainer.getChildren().clear();
        for (int i = 0; i < names.size(); i++) {
            final Long pid = ids.get(i);
            final String name = names.get(i);

            // Dot de color
            String dotColor = getCategoryColorForIndex(i);

            HBox row = new HBox();
            row.setAlignment(Pos.CENTER_LEFT);
            row.setSpacing(4);

            // Dot indicador
            Label dot = new Label("●");
            dot.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 9px; -fx-padding: 0 0 0 16;");

            Button btn = new Button(name);
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: rgba(255,255,255,0.6); " +
                    "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                    "-fx-padding: 7 4 7 6;");
            btn.setOnAction(e -> {
                selectedProjectId = pid;
                selectedCategory  = null;
                projectTitleLabel.setText(name);
                newTaskButton.setDisable(false);
                loadTasksForProject(pid);
            });

            Button menuBtn = new Button("⋯");
            menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: transparent; " +
                    "-fx-cursor: hand; -fx-font-size: 16px; -fx-padding: 4 8 4 4;");
            menuBtn.setOnAction(e -> {
                ContextMenu cm = new ContextMenu();
                MenuItem edit   = new MenuItem("✏ Editar");
                MenuItem delete = new MenuItem("🗑 Eliminar");
                edit.setOnAction(ev   -> handleEditProject(pid, name));
                delete.setOnAction(ev -> handleDeleteProject(pid, name));
                cm.getItems().addAll(edit, delete);
                cm.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
            });

            row.setOnMouseEntered(e -> {
                btn.setStyle("-fx-background-color: rgba(255,255,255,0.08); -fx-text-fill: white; " +
                        "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                        "-fx-padding: 7 4 7 6;");
                menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #cccccc; " +
                        "-fx-cursor: hand; -fx-font-size: 16px; -fx-padding: 4 8 4 4;");
            });
            row.setOnMouseExited(e -> {
                btn.setStyle("-fx-background-color: transparent; -fx-text-fill: rgba(255,255,255,0.6); " +
                        "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                        "-fx-padding: 7 4 7 6;");
                menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: transparent; " +
                        "-fx-cursor: hand; -fx-font-size: 16px; -fx-padding: 4 8 4 4;");
            });

            row.getChildren().addAll(dot, btn, menuBtn);
            projectListContainer.getChildren().add(row);
        }
    }

    /**
     * HOME - renderizado principal
     */
    private void loadHome() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService()
                        .get("/api/tasks/home");

                if (response.statusCode() == 200) {
                    JsonNode home = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderHome(home));
                }
            } catch (Exception e) {
                Platform.runLater(() ->
                        showAlert("Error", "No se pudo cargar el home"));
            }
        }).start();
    }

    private void renderHome(JsonNode home) {
        taskContainer.getChildren().clear();

        // Saludo + fecha
        String username = AppContext.getInstance().getCurrentUsername();
        LocalDate today = LocalDate.now();
        String dayName  = today.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        String dateStr  = dayName.substring(0, 1).toUpperCase() + dayName.substring(1)
                + ", " + today.format(DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "ES")));

        // Contar tareas pendientes hoy (con dueDate = hoy o sin fecha, no DONE/CANCELLED)
        int pendingToday = countPendingTasks(home);

        HBox greetingBox = new HBox();
        greetingBox.setStyle("-fx-background-color: white; -fx-padding: 20 24 20 24; " +
                "-fx-border-color: #e8e8e8; -fx-border-width: 0 0 1 0;");
        greetingBox.setAlignment(Pos.CENTER_LEFT);

        VBox greetingText = new VBox(3);
        Label greetingLabel = new Label("Buenos días, " + username);
        greetingLabel.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #1e1e2e;");

        String subText = dateStr + (pendingToday > 0
                ? "  ·  " + pendingToday + " tarea" + (pendingToday > 1 ? "s" : "") + " pendiente" + (pendingToday > 1 ? "s" : "")
                : "  ·  Todo al día ✓");
        Label subLabel = new Label(subText);
        subLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888;");
        greetingText.getChildren().addAll(greetingLabel, subLabel);
        greetingBox.getChildren().add(greetingText);
        taskContainer.getChildren().add(greetingBox);

        // Stats row
        int[] stats = computeStats(home);
        // stats: [0]=pendientes, [1]=en progreso, [2]=completadas, [3]=proyectos activos

        HBox statsRow = new HBox(10);
        statsRow.setStyle("-fx-padding: 16 20 8 20;");
        statsRow.getChildren().addAll(
                createStatCard(String.valueOf(stats[0]), "Pendientes",    "#3b82f6"),
                createStatCard(String.valueOf(stats[1]), "En progreso",   "#f59e0b"),
                createStatCard(String.valueOf(stats[2]), "Completadas",   "#22c55e"),
                createStatCard(String.valueOf(stats[3]), "Proyectos activos", "#e11d48")
        );
        for (javafx.scene.Node card : statsRow.getChildren()) {
            HBox.setHgrow(card, Priority.ALWAYS);
        }
        taskContainer.getChildren().add(statsRow);

        // Dos columnas: proyectos | tareas próximas
        HBox twoCol = new HBox(14);
        twoCol.setStyle("-fx-padding: 8 20 20 20;");
        VBox.setVgrow(twoCol, Priority.ALWAYS);

        VBox projectsCol = buildProjectsColumn(home.get("projects"));
        VBox tasksCol    = buildUpcomingTasksColumn(home);

        HBox.setHgrow(projectsCol, Priority.ALWAYS);
        HBox.setHgrow(tasksCol,    Priority.ALWAYS);

        twoCol.getChildren().addAll(projectsCol, tasksCol);
        taskContainer.getChildren().add(twoCol);

        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
    }

    // Tarjeta de estadística
    private VBox createStatCard(String number, String label, String dotColor) {
        VBox card = new VBox(4);
        card.setStyle("-fx-background-color: white; -fx-padding: 12 14 12 14; " +
                "-fx-background-radius: 8px; -fx-border-color: #e8e8e8; " +
                "-fx-border-radius: 8px; -fx-border-width: 1;");
        card.setAlignment(Pos.TOP_LEFT);

        Label num = new Label(number);
        num.setStyle("-fx-font-size: 22px; -fx-font-weight: bold; -fx-text-fill: #1e1e2e;");

        HBox labelRow = new HBox(5);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 8px;");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");
        labelRow.getChildren().addAll(dot, lbl);

        card.getChildren().addAll(num, labelRow);
        return card;
    }

    // Columna izquierda: proyectos con barra de progreso
    private VBox buildProjectsColumn(JsonNode projects) {
        VBox panel = createPanel();

        HBox header = createPanelHeader("Proyectos activos",
                projects != null ? projects.size() + " proyectos" : "0 proyectos");
        panel.getChildren().add(header);

        if (projects == null || !projects.isArray() || projects.isEmpty()) {
            Label empty = new Label("No hay proyectos activos");
            empty.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px; -fx-padding: 8 0 0 0;");
            panel.getChildren().add(empty);
            return panel;
        }

        int idx = 0;
        for (JsonNode project : projects) {
            String pName     = project.get("name").asText();
            String pCategory = project.has("category") ? project.get("category").asText() : "PERSONAL";
            JsonNode tasks   = project.get("tasks");

            int total = (tasks != null && tasks.isArray()) ? tasks.size() : 0;
            int done  = 0;
            if (tasks != null && tasks.isArray()) {
                for (JsonNode t : tasks) {
                    if ("DONE".equals(t.has("status") ? t.get("status").asText() : "")) done++;
                }
            }
            double pct = total > 0 ? (double) done / total * 100 : 0;
            String barColor = getCategoryColor(pCategory);

            // Separador entre items (no antes del primero)
            if (idx > 0) {
                Separator sep = new Separator();
                sep.setStyle("-fx-background-color: #f0f0f0;");
                panel.getChildren().add(sep);
            }

            VBox item = new VBox(5);
            item.setStyle("-fx-padding: 10 0 10 0;");

            HBox nameRow = new HBox();
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(pName);
            nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);
            Label pctLabel = new Label(Math.round(pct) + "%");
            pctLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");
            nameRow.getChildren().addAll(nameLabel, pctLabel);

            // Barra de progreso
            StackPane barBg = new StackPane();
            barBg.setStyle("-fx-background-color: #f0f0f5; -fx-background-radius: 2px;");
            barBg.setMinHeight(5);
            barBg.setMaxHeight(5);

            HBox barFill = new HBox();
            barFill.setStyle("-fx-background-color: " + barColor + "; -fx-background-radius: 2px;");
            barFill.setMinHeight(5);
            barFill.setMaxHeight(5);
            // Forzamos el ancho proporcional al porcentaje
            barFill.setMaxWidth(Double.MAX_VALUE);
            barBg.getChildren().add(barFill);
            // Usamos listener para calcular el ancho real
            final double pctFinal = pct;
            barBg.widthProperty().addListener((obs, o, n) ->
                    barFill.setPrefWidth(n.doubleValue() * pctFinal / 100.0));

            HBox metaRow = new HBox(6);
            metaRow.setAlignment(Pos.CENTER_LEFT);
            metaRow.getChildren().addAll(
                    createBadge(pCategory, getCategoryBadgeStyle(pCategory)),
                    createBadge(total + " tareas", "-fx-background-color: #f0f0f5; " +
                            "-fx-text-fill: #666666; -fx-background-radius: 10px; " +
                            "-fx-font-size: 10px; -fx-padding: 2 7 2 7;")
            );

            item.getChildren().addAll(nameRow, barBg, metaRow);
            panel.getChildren().add(item);
            idx++;
        }

        return panel;
    }

    // Columna derecha: tareas próximas
    private VBox buildUpcomingTasksColumn(JsonNode home) {
        // Recogemos TODAS las tareas de todos los proyectos + sueltas
        List<JsonNode> allTasks = new ArrayList<>();
        JsonNode projects = home.get("projects");
        if (projects != null && projects.isArray()) {
            for (JsonNode p : projects) {
                JsonNode tasks = p.get("tasks");
                if (tasks != null && tasks.isArray()) {
                    for (JsonNode t : tasks) allTasks.add(t);
                }
            }
        }
        for (String cat : new String[]{"personalTasks", "estudiosTasks", "trabajoTasks"}) {
            JsonNode catTasks = home.get(cat);
            if (catTasks != null && catTasks.isArray()) {
                for (JsonNode t : catTasks) allTasks.add(t);
            }
        }

        // Filtramos: no DONE ni CANCELLED, ordenamos por dueDate (nulls al final)
        allTasks.removeIf(t -> {
            String s = t.has("status") ? t.get("status").asText() : "";
            return "DONE".equals(s) || "CANCELLED".equals(s);
        });
        allTasks.sort((a, b) -> {
            boolean aHas = a.has("dueDate") && !a.get("dueDate").isNull();
            boolean bHas = b.has("dueDate") && !b.get("dueDate").isNull();
            if (!aHas && !bHas) return 0;
            if (!aHas) return 1;
            if (!bHas) return -1;
            return a.get("dueDate").asText().compareTo(b.get("dueDate").asText());
        });

        // Mostramos máximo 6
        List<JsonNode> upcoming = allTasks.subList(0, Math.min(6, allTasks.size()));

        VBox panel = createPanel();
        HBox header = createPanelHeader("Tareas próximas",
                allTasks.size() + " pendiente" + (allTasks.size() != 1 ? "s" : ""));
        panel.getChildren().add(header);

        if (upcoming.isEmpty()) {
            Label empty = new Label("¡Todo al día! Sin tareas pendientes.");
            empty.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px; -fx-padding: 8 0 0 0;");
            panel.getChildren().add(empty);
            return panel;
        }

        LocalDate today = LocalDate.now();

        int idx = 0;
        for (JsonNode task : upcoming) {
            if (idx > 0) {
                Separator sep = new Separator();
                sep.setStyle("-fx-background-color: #f0f0f0;");
                panel.getChildren().add(sep);
            }

            String title    = task.get("title").asText();
            String priority = task.has("priority") ? task.get("priority").asText() : "MEDIUM";
            String category = task.has("category") ? task.get("category").asText() : "PERSONAL";
            Long   taskId   = task.get("id").asLong();

            // Fecha de vencimiento
            String dueLbl   = "";
            boolean isUrgentDate = false;
            if (task.has("dueDate") && !task.get("dueDate").isNull()) {
                try {
                    LocalDate due = LocalDate.parse(task.get("dueDate").asText().substring(0, 10));
                    if (due.equals(today))           { dueLbl = "Hoy";      isUrgentDate = true; }
                    else if (due.equals(today.plusDays(1))) { dueLbl = "Mañana"; }
                    else dueLbl = due.format(DateTimeFormatter.ofPattern("d MMM", new Locale("es", "ES")));
                } catch (Exception ignored) {}
            }

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 9 0 9 0; -fx-cursor: hand;");

            // Círculo check
            Label check = new Label();
            check.setMinSize(16, 16);
            check.setMaxSize(16, 16);
            check.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 8px; " +
                    "-fx-background-radius: 8px; -fx-cursor: hand;");
            // Acción: marcar como DONE
            check.setOnMouseClicked(e -> {
                new Thread(() -> {
                    try {
                        AppContext.getInstance().getApiService()
                                .patch("/api/tasks/" + taskId + "/status?status=DONE", null);
                        Platform.runLater(this::loadHome);
                    } catch (Exception ex) {
                        Platform.runLater(() -> showAlert("Error", "No se pudo actualizar la tarea"));
                    }
                }).start();
            });

            VBox body = new VBox(3);
            HBox.setHgrow(body, Priority.ALWAYS);

            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");
            titleLabel.setMaxWidth(Double.MAX_VALUE);

            HBox meta = new HBox(6);
            meta.setAlignment(Pos.CENTER_LEFT);
            if (!dueLbl.isEmpty()) {
                Label dueLabel = new Label(dueLbl);
                dueLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " +
                        (isUrgentDate ? "#dc2626" : "#888888") + ";");
                meta.getChildren().add(dueLabel);
            }
            meta.getChildren().add(createBadge(category, getCategoryBadgeStyle(category)));

            body.getChildren().addAll(titleLabel, meta);

            // Badge prioridad si es HIGH o URGENT
            if ("URGENT".equals(priority) || "HIGH".equals(priority)) {
                Label priBadge = new Label(priority);
                priBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                        "-fx-background-radius: 10px; -fx-text-fill: white; " +
                        "-fx-background-color: " + getPriorityColor(priority) + ";");
                row.getChildren().addAll(check, body, priBadge);
            } else {
                row.getChildren().addAll(check, body);
            }

            panel.getChildren().add(row);
            idx++;
        }

        return panel;
    }

    // Helpers de panel
    private VBox createPanel() {
        VBox panel = new VBox(0);
        panel.setStyle("-fx-background-color: white; -fx-padding: 14 16 14 16; " +
                "-fx-background-radius: 10px; -fx-border-color: #e8e8e8; " +
                "-fx-border-radius: 10px; -fx-border-width: 1;");
        return panel;
    }

    private HBox createPanelHeader(String title, String count) {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-padding: 0 0 12 0;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #1e1e2e;");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label countLabel = new Label(count);
        countLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888; -fx-padding: 2 8 2 8; " +
                "-fx-background-color: #f0f0f5; -fx-background-radius: 20px;");

        header.getChildren().addAll(titleLabel, countLabel);
        return header;
    }

    private Label createBadge(String text, String style) {
        Label badge = new Label(text);
        badge.setStyle(style);
        return badge;
    }

    // Cálculo de stats
    private int[] computeStats(JsonNode home) {
        int pending = 0, inProgress = 0, done = 0, activeProjects = 0;

        JsonNode projects = home.get("projects");
        if (projects != null && projects.isArray()) {
            activeProjects = projects.size();
            for (JsonNode p : projects) {
                JsonNode tasks = p.get("tasks");
                if (tasks != null && tasks.isArray()) {
                    for (JsonNode t : tasks) {
                        String s = t.has("status") ? t.get("status").asText() : "";
                        if ("TODO".equals(s))        pending++;
                        else if ("IN_PROGRESS".equals(s)) inProgress++;
                        else if ("DONE".equals(s))   done++;
                    }
                }
            }
        }
        for (String cat : new String[]{"personalTasks", "estudiosTasks", "trabajoTasks"}) {
            JsonNode catTasks = home.get(cat);
            if (catTasks != null && catTasks.isArray()) {
                for (JsonNode t : catTasks) {
                    String s = t.has("status") ? t.get("status").asText() : "";
                    if ("TODO".equals(s))        pending++;
                    else if ("IN_PROGRESS".equals(s)) inProgress++;
                    else if ("DONE".equals(s))   done++;
                }
            }
        }
        return new int[]{pending, inProgress, done, activeProjects};
    }

    private int countPendingTasks(JsonNode home) {
        int[] stats = computeStats(home);
        return stats[0] + stats[1]; // TODO + IN_PROGRESS
    }

    // Colores
    private String getCategoryColor(String category) {
        return switch (category) {
            case "PERSONAL"  -> COLOR_PERSONAL;
            case "ESTUDIOS"  -> COLOR_ESTUDIOS;
            case "TRABAJO"   -> COLOR_TRABAJO;
            default          -> COLOR_DEFAULT;
        };
    }

    private String getCategoryColorForIndex(int i) {
        String[] colors = {COLOR_PERSONAL, COLOR_ESTUDIOS, COLOR_TRABAJO,
                "#60a5fa", "#f472b6", "#34d399"};
        return colors[i % colors.length];
    }

    private String getCategoryBadgeStyle(String category) {
        return switch (category) {
            case "PERSONAL"  -> "-fx-background-color: #f3e8ff; -fx-text-fill: #6b21a8; " +
                    "-fx-background-radius: 10px; -fx-font-size: 10px; -fx-padding: 2 7 2 7;";
            case "ESTUDIOS"  -> "-fx-background-color: #fef3c7; -fx-text-fill: #92400e; " +
                    "-fx-background-radius: 10px; -fx-font-size: 10px; -fx-padding: 2 7 2 7;";
            case "TRABAJO"   -> "-fx-background-color: #dbeafe; -fx-text-fill: #1e40af; " +
                    "-fx-background-radius: 10px; -fx-font-size: 10px; -fx-padding: 2 7 2 7;";
            default          -> "-fx-background-color: #f0f0f5; -fx-text-fill: #666666; " +
                    "-fx-background-radius: 10px; -fx-font-size: 10px; -fx-padding: 2 7 2 7;";
        };
    }

    private String getStatusColor(String status) {
        return switch (status) {
            case "TODO"        -> "#95a5a6";
            case "IN_PROGRESS" -> "#3498db";
            case "DONE"        -> "#2ecc71";
            case "CANCELLED"   -> "#e74c3c";
            default            -> "#95a5a6";
        };
    }

    private String getPriorityColor(String priority) {
        return switch (priority) {
            case "URGENT" -> "#e74c3c";
            case "HIGH"   -> "#e67e22";
            case "MEDIUM" -> "#3498db";
            case "LOW"    -> "#95a5a6";
            default       -> "#95a5a6";
        };
    }

    //  PANEL CENTRAL - tarjetas de proyectos
    private void renderProjectCards(List<String> names, List<Long> ids,
                                    List<String> statuses, List<String> priorities,
                                    List<String> categories) {
        projectCardsContainer.getChildren().clear();
        projectCardsContainer.getChildren().add(emptyProjectsLabel);

        if (names.isEmpty()) {
            emptyProjectsLabel.setVisible(true);
            return;
        }
        emptyProjectsLabel.setVisible(false);

        for (int i = 0; i < names.size(); i++) {
            final Long   pid      = ids.get(i);
            final String pName    = names.get(i);
            final String status   = statuses.get(i);
            final String priority = priorities.get(i);
            final String category = categories.get(i);

            HBox card = new HBox(10);
            card.setAlignment(Pos.CENTER_LEFT);
            card.setStyle("-fx-background-color: white; -fx-padding: 12 16 12 16; " +
                    "-fx-background-radius: 8px; -fx-border-color: #e8e8e8; " +
                    "-fx-border-radius: 8px; -fx-cursor: hand; -fx-border-width: 1;");
            card.getProperties().put("status", status);
            card.getProperties().put("priority", priority);

            Label nameLabel = new Label("📁 " + pName);
            nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");
            HBox.setHgrow(nameLabel, Priority.ALWAYS);

            Label statusBadge = new Label(status);
            statusBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                    "-fx-background-radius: 10px; -fx-text-fill: white; " +
                    "-fx-background-color: " + getStatusColor(status) + ";");
            Label priorityBadge = new Label(priority);
            priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                    "-fx-background-radius: 10px; -fx-text-fill: white; " +
                    "-fx-background-color: " + getPriorityColor(priority) + ";");

            card.setOnMouseClicked(e -> {
                selectedProjectId = pid;
                selectedCategory  = null;
                projectTitleLabel.setText(pName);
                newTaskButton.setDisable(false);
                loadTasksForProject(pid);
            });
            card.getChildren().addAll(nameLabel, statusBadge, priorityBadge);
            projectCardsContainer.getChildren().add(card);
        }
    }

    // =========================================================================
    //  PANEL DE TAREAS — lista normal (no home)
    // =========================================================================
    private void renderTasks(JsonNode tasks) {
        taskContainer.getChildren().clear();
        taskContainer.getChildren().add(emptyLabel);

        if (!tasks.isArray() || tasks.isEmpty()) {
            emptyLabel.setText("No hay tareas aquí actualmente");
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);

        for (JsonNode task : tasks) {
            taskContainer.getChildren().add(createTaskCard(task));
        }
    }

    private HBox createTaskCard(JsonNode task) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-padding: 12 16 12 16; " +
                "-fx-background-radius: 8px; -fx-border-color: #e8e8e8; " +
                "-fx-border-radius: 8px; -fx-border-width: 1;");
        card.setStyle(card.getStyle()); // force

        String status   = task.get("status").asText();
        String title    = task.get("title").asText();
        String priority = task.get("priority").asText();
        Long   taskId   = task.get("id").asLong();

        card.getProperties().put("status",   status);
        card.getProperties().put("priority", priority);

        CheckBox checkBox = new CheckBox();
        checkBox.setSelected("DONE".equals(status));

        Label titleLabel = new Label(title);
        updateTitleStyle(titleLabel, "DONE".equals(status));
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label priorityBadge = new Label(priority);
        priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        final boolean[] updating = {false};
        checkBox.selectedProperty().addListener((obs, was, is) -> {
            if (updating[0]) return;
            String newStatus = is ? "DONE" : "TODO";
            new Thread(() -> {
                try {
                    HttpResponse<String> resp = AppContext.getInstance().getApiService()
                            .patch("/api/tasks/" + taskId + "/status?status=" + newStatus, null);
                    Platform.runLater(() -> {
                        if (resp.statusCode() == 200) {
                            updating[0] = true;
                            updateTitleStyle(titleLabel, is);
                            updating[0] = false;
                        } else {
                            updating[0] = true;
                            checkBox.setSelected(was);
                            updating[0] = false;
                            showAlert("Error", "No se pudo cambiar el estado");
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> checkBox.setSelected(was));
                }
            }).start();
        });

        Button editBtn = new Button("✏️");
        editBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 14px;");
        editBtn.setOnAction(e -> handleEditTask(taskId, task));

        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-font-size: 14px;");
        deleteBtn.setOnAction(e -> handleDeleteTask(taskId));

        card.getChildren().addAll(checkBox, titleLabel, priorityBadge, editBtn, deleteBtn);
        return card;
    }

    private void updateTitleStyle(Label titleLabel, boolean done) {
        if (done) {
            titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #aaaaaa; -fx-strikethrough: true;");
        } else {
            titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");
        }
    }

    // =========================================================================
    //  NAVEGACIÓN
    // =========================================================================
    @FXML
    private void handleGoHome() {
        selectedProjectId = null;
        selectedCategory  = null;
        projectTitleLabel.setText("Inicio");
        newTaskButton.setDisable(false);

        javafx.scene.layout.BorderPane root =
                (javafx.scene.layout.BorderPane) usernameLabel.getScene().getRoot();
        HBox centerHBox = (HBox) root.getCenter();
        centerHBox.getChildren().removeIf(n -> n.getUserData() != null &&
                n.getUserData().equals("trash"));
        // Quitar settings si está
        centerHBox.getChildren().removeIf(n -> n.getUserData() != null &&
                n.getUserData().equals("settings"));

        projectsPanel.setVisible(true);
        projectsPanel.setManaged(true);
        taskPanel.setVisible(true);
        taskPanel.setManaged(true);

        // Ocultar filtros de tareas en home
        taskFiltersBar.setVisible(false);
        taskFiltersBar.setManaged(false);

        loadHome();
    }

    @FXML
    private void handleAllTasks() {
        removeOverlayPanels();
        selectedProjectId = null;
        selectedCategory  = null;
        projectTitleLabel.setText("Todas las tareas");
        projectsPanel.setVisible(false);
        projectsPanel.setManaged(false);
        taskPanel.setVisible(true);
        taskPanel.setManaged(true);
        showTaskFilters();

        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/tasks/personal");
                if (response.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las tareas"));
            }
        }).start();
    }

    @FXML private void handleCategoryPersonal() { loadTasksByCategory("PERSONAL",  "👤 Personal"); }
    @FXML private void handleCategoryEstudios() { loadTasksByCategory("ESTUDIOS",  "📚 Estudios"); }
    @FXML private void handleCategoryTrabajo()  { loadTasksByCategory("TRABAJO",   "💼 Trabajo");  }

    private void loadTasksByCategory(String category, String title) {
        removeOverlayPanels();
        selectedProjectId = null;
        selectedCategory  = category;
        projectTitleLabel.setText(title);
        projectsPanel.setVisible(false);
        projectsPanel.setManaged(false);
        taskPanel.setVisible(true);
        taskPanel.setManaged(true);
        showTaskFilters();

        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/tasks/category/" + category);
                if (response.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las tareas"));
            }
        }).start();
    }

    private void loadTasksForProject(Long projectId) {
        projectsPanel.setVisible(true);
        projectsPanel.setManaged(true);
        showTaskFilters();

        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/tasks?projectId=" + projectId);
                if (response.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las tareas"));
            }
        }).start();
    }

    private void showTaskFilters() {
        taskFiltersBar.setVisible(true);
        taskFiltersBar.setManaged(true);
    }

    private void removeOverlayPanels() {
        javafx.scene.layout.BorderPane root =
                (javafx.scene.layout.BorderPane) usernameLabel.getScene().getRoot();
        HBox centerHBox = (HBox) root.getCenter();
        centerHBox.getChildren().removeIf(n -> {
            Object ud = n.getUserData();
            return "trash".equals(ud) || "settings".equals(ud);
        });
    }

    // =========================================================================
    //  FILTROS
    // =========================================================================
    @FXML private void handleProjectStatusFilter()   { filterProjectCards(); }
    @FXML private void handleProjectPriorityFilter() { filterProjectCards(); }

    private void filterProjectCards() {
        String status   = projectStatusFilter.getValue();
        String priority = projectPriorityFilter.getValue();
        projectCardsContainer.getChildren().forEach(node -> {
            if (node instanceof HBox row) {
                boolean sm = status   == null || status.equals("Todos")  || status.equals(row.getProperties().get("status"));
                boolean pm = priority == null || priority.equals("Todas") || priority.equals(row.getProperties().get("priority"));
                row.setVisible(sm && pm);
                row.setManaged(sm && pm);
            }
        });
    }

    @FXML
    private void handleStatusFilter() {
        String selected = statusFilter.getValue();
        if (selected == null || selected.equals("Todos")) { reloadTasks(); return; }
        if (selectedProjectId != null) {
            new Thread(() -> {
                try {
                    HttpResponse<String> response = AppContext.getInstance().getApiService()
                            .get("/api/tasks/filter/status?projectId=" + selectedProjectId + "&status=" + selected);
                    if (response.statusCode() == 200) {
                        JsonNode tasks = objectMapper.readTree(response.body());
                        Platform.runLater(() -> renderTasks(tasks));
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("Error", "No se pudieron filtrar las tareas"));
                }
            }).start();
        } else {
            filterTaskCardsByStatus(selected);
        }
    }

    @FXML
    private void handlePriorityFilter() {
        String selected = priorityFilter.getValue();
        if (selected == null || selected.equals("Todas")) { reloadTasks(); return; }
        if (selectedProjectId != null) {
            new Thread(() -> {
                try {
                    HttpResponse<String> response = AppContext.getInstance().getApiService()
                            .get("/api/tasks/filter/priority?projectId=" + selectedProjectId + "&priority=" + selected);
                    if (response.statusCode() == 200) {
                        JsonNode tasks = objectMapper.readTree(response.body());
                        Platform.runLater(() -> renderTasks(tasks));
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("Error", "No se pudieron filtrar las tareas"));
                }
            }).start();
        } else {
            filterTaskCardsByPriority(selected);
        }
    }

    private void filterTaskCardsByStatus(String status) {
        taskContainer.getChildren().forEach(node -> {
            if (node instanceof HBox card) {
                boolean match = status.equals(card.getProperties().get("status"));
                card.setVisible(match);
                card.setManaged(match);
            }
        });
    }

    private void filterTaskCardsByPriority(String priority) {
        taskContainer.getChildren().forEach(node -> {
            if (node instanceof HBox card) {
                boolean match = priority.equals(card.getProperties().get("priority"));
                card.setVisible(match);
                card.setManaged(match);
            }
        });
    }

    // =========================================================================
    //  PAPELERA Y AJUSTES — arreglado para el layout de 3 elementos
    // =========================================================================
    @FXML
    private void handleTrash() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/trash-view.fxml"));
            VBox trashView = loader.load();
            HBox.setHgrow(trashView, Priority.ALWAYS);
            trashView.setUserData("trash");

            TrashController controller = loader.getController();
            trashController = controller;
            controller.setOnTrashChanged(() -> { loadProjects(); reloadTasks(); });

            projectsPanel.setVisible(false);
            projectsPanel.setManaged(false);
            taskPanel.setVisible(false);
            taskPanel.setManaged(false);

            javafx.scene.layout.BorderPane root =
                    (javafx.scene.layout.BorderPane) usernameLabel.getScene().getRoot();
            HBox centerHBox = (HBox) root.getCenter();
            centerHBox.getChildren().removeIf(n -> {
                Object ud = n.getUserData();
                return "trash".equals(ud) || "settings".equals(ud);
            });
            centerHBox.getChildren().add(trashView);
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir la papelera");
        }
    }

    @FXML
    private void handleSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/settings-view.fxml"));
            VBox settingsView = loader.load();
            HBox.setHgrow(settingsView, Priority.ALWAYS);
            settingsView.setUserData("settings");

            projectsPanel.setVisible(false);
            projectsPanel.setManaged(false);
            taskPanel.setVisible(false);
            taskPanel.setManaged(false);

            javafx.scene.layout.BorderPane root =
                    (javafx.scene.layout.BorderPane) usernameLabel.getScene().getRoot();
            HBox centerHBox = (HBox) root.getCenter();
            // Quitamos cualquier overlay previo (papelera u otro ajustes)
            centerHBox.getChildren().removeIf(n -> {
                Object ud = n.getUserData();
                return "trash".equals(ud) || "settings".equals(ud);
            });
            centerHBox.getChildren().add(settingsView);
        } catch (Exception e) {
            showAlert("Error", "No se pudo abrir los ajustes");
        }
    }

    // =========================================================================
    //  DIALOGS
    // =========================================================================
    @FXML
    private void handleNewProject() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-project-dialog.fxml"));
            VBox root = loader.load();
            NewProjectController controller = loader.getController();
            controller.setOnProjectCreated(() -> { loadProjects(); reloadTasks(); });
            Stage dialog = new Stage();
            dialog.setTitle("Nuevo proyecto");
            dialog.setScene(new Scene(root, 400, 480));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    @FXML
    private void handleNewTask() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-task-dialog.fxml"));
            VBox root = loader.load();
            NewTaskController controller = loader.getController();
            controller.initData(selectedProjectId);
            controller.setOnTaskCreated(this::reloadTasks);
            Stage dialog = new Stage();
            dialog.setTitle("Nueva tarea");
            dialog.setScene(new Scene(root, 600, 420));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    private void handleEditProject(Long projectId, String projectName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-project-dialog.fxml"));
            VBox root = loader.load();
            EditProjectController controller = loader.getController();
            controller.initData(projectId, projectName);
            controller.setOnProjectUpdated(this::loadProjects);
            Stage dialog = new Stage();
            dialog.setTitle("Editar proyecto");
            dialog.setScene(new Scene(root, 500, 480));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    private void handleDeleteProject(Long projectId, String projectName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar proyecto");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Seguro que quieres eliminar \"" + projectName + "\"? " +
                "El proyecto y todas sus tareas irán a la papelera.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> resp = AppContext.getInstance()
                                .getApiService().delete("/api/projects/" + projectId);
                        Platform.runLater(() -> {
                            if (resp.statusCode() == 200 || resp.statusCode() == 204) {
                                if (projectId.equals(selectedProjectId)) handleGoHome();
                                loadProjects();
                                reloadTasks();
                                if (trashController != null) trashController.refresh();
                            } else {
                                showAlert("Error", "No se pudo eliminar el proyecto");
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert("Error", "Error de conexión"));
                    }
                }).start();
            }
        });
    }

    private void handleEditTask(Long taskId, JsonNode task) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml"));
            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(task);
            controller.setOnTaskUpdated(this::reloadTasks);
            Stage dialog = new Stage();
            dialog.setTitle("Editar tarea");
            dialog.setScene(new Scene(root, 500, 420));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    private void handleDeleteTask(Long taskId) {
        final Long   currentProjectId = selectedProjectId;
        final String currentCategory  = selectedCategory;
        final String currentTitle     = projectTitleLabel.getText();

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar tarea");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Seguro que quieres eliminar esta tarea? Irá a la papelera.");
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> resp = AppContext.getInstance()
                                .getApiService().delete("/api/tasks/" + taskId);
                        Platform.runLater(() -> {
                            if (resp.statusCode() == 200 || resp.statusCode() == 204) {
                                if (currentProjectId != null) loadTasksForProject(currentProjectId);
                                else if (currentCategory != null) loadTasksByCategory(currentCategory, currentTitle);
                                else loadHome();
                                if (trashController != null) trashController.refresh();
                            } else {
                                showAlert("Error", "No se pudo eliminar la tarea");
                            }
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert("Error", "Error de conexión"));
                    }
                }).start();
            }
        });
    }

    @FXML
    private void handleLogout() {
        AppContext.getInstance().logout();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml"));
            Stage stage = (Stage) usernameLabel.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 500));
            stage.setTitle("TaskMaster");
        } catch (IOException e) {
            showAlert("Error", "No se pudo cerrar la sesión");
        }
    }

    // =========================================================================
    //  UTILS
    // =========================================================================
    private void reloadTasks() {
        if (selectedProjectId != null) loadTasksForProject(selectedProjectId);
        else if (selectedCategory != null) loadTasksByCategory(selectedCategory, projectTitleLabel.getText());
        else loadHome();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
