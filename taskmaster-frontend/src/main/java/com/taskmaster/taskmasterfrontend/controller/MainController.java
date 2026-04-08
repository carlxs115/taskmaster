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
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
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

public class MainController {

    // ── FXML refs ─────────────────────────────────────────────────────────────
    @FXML private Button userMenuButton;
    @FXML private Button btnHome;
    @FXML private Button btnAllTasks;
    @FXML private Button btnPersonal;
    @FXML private Button btnEstudios;
    @FXML private Button btnTrabajo;
    @FXML private Button btnSettings;
    @FXML private Button btnTrash;
    @FXML private VBox  projectListContainer;
    @FXML private VBox  taskContainer;
    @FXML private Label emptyLabel;
    @FXML private HBox  taskFiltersBar;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> priorityFilter;
    @FXML private ComboBox<String> sortFilter;
    @FXML private Button sortDirectionBtn;
    @FXML private Label areaTitle;
    @FXML private Button createButton;
    @FXML private VBox  mainArea;
    @FXML private TextField searchField;

    // ── Filtros y orden ───────────────────────────────────────────────────────
    private List<JsonNode> currentTasks = new ArrayList<>();
    private boolean sortAscending = true;

    // ── Estado ────────────────────────────────────────────────────────────────
    private Long   selectedProjectId;
    private String selectedCategory;
    private boolean viewingAllTasks = false;
    private TrashController trashController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // ── Colores de categoría ──────────────────────────────────────────────────
    private static final String COLOR_PERSONAL = "#a78bfa";
    private static final String COLOR_ESTUDIOS = "#34d399";
    private static final String COLOR_TRABAJO  = "#fb923c";

    // =========================================================================
    //  INICIALIZACIÓN
    // =========================================================================
    @FXML
    public void initialize() {
        String username = AppContext.getInstance().getCurrentUsername();

        String initials = username.length() >= 2
                ? username.substring(0, 2).toUpperCase()
                : username.toUpperCase();
        userMenuButton.setText(username + "  ▾");

        statusFilter.setItems(FXCollections.observableArrayList(
                "Todas", "Pendiente", "En progreso", "Completada", "Cancelada"));
        statusFilter.setPromptText("Estado");
        statusFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> applyFiltersAndSort());

        priorityFilter.setItems(FXCollections.observableArrayList(
                "Todas", "Baja", "Media", "Alta", "Urgente"));
        priorityFilter.setPromptText("Prioridad");
        priorityFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> applyFiltersAndSort());

        sortFilter.setItems(FXCollections.observableArrayList(
                "Título", "ID", "Fecha límite", "Prioridad"));
        sortFilter.setPromptText("Criterio");
        sortFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> applyFiltersAndSort());
        loadProjects();
        loadHome();
    }

    // =========================================================================
    //  MENÚ DE USUARIO
    // =========================================================================
    @FXML
    private void handleUserMenu() {
        ContextMenu menu = new ContextMenu();

        // Cabecera no clickable con el nombre completo
        MenuItem viewProfile = new MenuItem("👤  Ver Perfil");
        viewProfile.setStyle("-fx-font-size: 13px; -fx-font-weight: bold;");
        viewProfile.setOnAction(e -> handleViewProfile());

        SeparatorMenuItem sep1 = new SeparatorMenuItem();

        MenuItem changePassword = new MenuItem("🔒  Cambiar contraseña");
        changePassword.setStyle("-fx-font-size: 13px;");
        changePassword.setOnAction(e -> openChangePassword());

        SeparatorMenuItem sep2 = new SeparatorMenuItem();

        MenuItem deleteAccount = new MenuItem("🗑  Eliminar cuenta");
        deleteAccount.setStyle("-fx-font-size: 13px; -fx-text-fill: #e74c3c;");
        deleteAccount.setOnAction(e -> openDeleteAccount());

        SeparatorMenuItem sep3 = new SeparatorMenuItem();

        MenuItem logout = new MenuItem("↩  Cerrar sesión");
        logout.setStyle("-fx-font-size: 13px;");
        logout.setOnAction(e -> handleLogout());

        menu.getItems().addAll(viewProfile, sep1, changePassword, sep2, deleteAccount, sep3, logout);
        menu.show(userMenuButton, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    private void handleViewProfile() {
        clearSidebarSelection();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/profile-view.fxml"));
            VBox profileView = loader.load();
            HBox.setHgrow(profileView, Priority.ALWAYS);
            profileView.setUserData("profile");
            ProfileController controller = loader.getController();
            controller.setOnProfileUpdated(() -> {
                String username = AppContext.getInstance().getCurrentUsername();
                userMenuButton.setText(username + "  ▾");
                loadHome();
            });
            swapMainAreaWith(profileView);
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el perfil");
        }
    }

    private void openChangePassword() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/change-password-dialog.fxml"));
            VBox root = loader.load();
            Stage dialog = new Stage();
            dialog.setTitle("Cambiar contraseña");
            dialog.setScene(new Scene(root, 400, 320));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    private void openDeleteAccount() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/delete-account-dialog.fxml"));
            VBox root = loader.load();
            DeleteAccountController controller = loader.getController();
            controller.setOnAccountDeleted(this::handleLogout);
            Stage dialog = new Stage();
            dialog.setTitle("Eliminar cuenta");
            dialog.setScene(new Scene(root, 340, 340));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el diálogo");
        }
    }

    // =========================================================================
    //  BOTÓN CREAR — desplegable Proyecto / Tarea
    // =========================================================================
    @FXML
    private void handleCreateMenu() {

        boolean isHome = "Inicio".equals(areaTitle.getText());

        if (!isHome) {
            handleNewTask();
            return;
        }

        ContextMenu menu = new ContextMenu();
        menu.setStyle("-fx-background-color: white; -fx-border-color: #e8e8e8; " +
                "-fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");

        MenuItem newProject = new MenuItem("📁  Nuevo proyecto");
        newProject.setStyle("-fx-font-size: 13px; -fx-padding: 8 16 8 16;");
        newProject.setOnAction(e -> handleNewProject());

        MenuItem newTask = new MenuItem("✅  Nueva tarea");
        newTask.setStyle("-fx-font-size: 13px; -fx-padding: 8 16 8 16;");
        newTask.setOnAction(e -> handleNewTask());

        menu.getItems().addAll(newProject, newTask);
        menu.show(createButton, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    // =========================================================================
    //  PROYECTOS — sidebar
    // =========================================================================
    public void loadProjects() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/projects");
                if (response.statusCode() == 200) {
                    JsonNode projects = objectMapper.readTree(response.body());
                    List<String> names = new ArrayList<>();
                    List<Long>   ids   = new ArrayList<>();
                    List<JsonNode> nodes    = new ArrayList<>();
                    for (JsonNode p : projects) {
                        names.add(p.get("name").asText());
                        ids.add(p.get("id").asLong());
                        nodes.add(p);
                    }
                    Platform.runLater(() -> renderSidebar(names, ids, nodes));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar los proyectos"));
            }
        }).start();
    }

    private void renderSidebar(List<String> names, List<Long> ids, List<JsonNode> nodes) {
        projectListContainer.getChildren().clear();
        for (int i = 0; i < names.size(); i++) {
            final Long   pid  = ids.get(i);
            final String name = names.get(i);
            final JsonNode pNode = nodes.get(i);
            String dotColor   = getCategoryColorForIndex(i);

            HBox row = new HBox(4);
            row.setUserData(pid);
            row.setAlignment(Pos.CENTER_LEFT);

            Label dot = new Label("●");
            dot.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 9px; -fx-padding: 0 0 0 16;");

            Button btn = new Button(name);
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9999bb; " +
                    "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                    "-fx-padding: 7 4 7 6;");
            btn.setOnAction(e -> {
                selectedProjectId = pid;
                selectedCategory  = null;
                areaTitle.setText(name);
                setSidebarProjectActive(pid);
                loadTasksForProject(pid);
            });

            Button menuBtn = new Button("•••");
            menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: transparent; " +
                    "-fx-font-size: 10px; -fx-font-weight: bold; -fx-cursor: hand; " +
                    "-fx-padding: 2 6 2 6; -fx-background-radius: 6px;");
            menuBtn.setOnAction(e -> {
                ContextMenu cm = new ContextMenu();
                MenuItem detail = new MenuItem("👁 Ver detalles");
                MenuItem edit   = new MenuItem("✏ Editar");
                MenuItem delete = new MenuItem("🗑 Eliminar");
                detail.setStyle("-fx-font-size: 13px; -fx-padding: 6 16 6 16;");
                edit.setStyle("-fx-font-size: 13px; -fx-padding: 6 16 6 16;");
                delete.setStyle("-fx-font-size: 13px; -fx-padding: 6 16 6 16;");
                detail.setOnAction(ev -> openProjectDetail(pNode));
                edit.setOnAction(ev   -> handleEditProject(pid, name));
                delete.setOnAction(ev -> handleDeleteProject(pid, name));
                cm.getItems().addAll(detail, edit, delete);
                cm.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
            });

            row.setOnMouseEntered(e -> {
                btn.setStyle("-fx-background-color: #1e1e35; -fx-text-fill: white; " +
                        "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                        "-fx-padding: 7 4 7 6;");
                menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9999bb; " +
                        "-fx-font-size: 10px; -fx-font-weight: bold; -fx-cursor: hand; " +
                        "-fx-padding: 2 6 2 6; -fx-background-radius: 6px;");
            });
            row.setOnMouseExited(e -> {
                if (pid.equals(selectedProjectId)) {
                    btn.setStyle("-fx-background-color: #2a1f4e; -fx-text-fill: #a78bfa; " +
                            "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                            "-fx-padding: 7 4 7 6;");
                    menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9999bb; " +
                            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-cursor: hand; " +
                            "-fx-padding: 2 6 2 6; -fx-background-radius: 6px;");
                } else {
                    btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9999bb; " +
                            "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                            "-fx-padding: 7 4 7 6;");
                    menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: transparent; " +
                            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-cursor: hand; " +
                            "-fx-padding: 2 6 2 6; -fx-background-radius: 6px;");
                }
            });

            row.getChildren().addAll(dot, btn, menuBtn);
            projectListContainer.getChildren().add(row);
        }
    }

    // =========================================================================
    //  HOME
    // =========================================================================
    private void loadHome() {
        new Thread(() -> {
            try {
                HttpResponse<String> response = AppContext.getInstance()
                        .getApiService().get("/api/tasks/home");
                if (response.statusCode() == 200) {
                    JsonNode home = objectMapper.readTree(response.body());
                    Platform.runLater(() -> renderHome(home));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudo cargar el home"));
            }
        }).start();
    }

    private void renderHome(JsonNode home) {
        taskContainer.getChildren().clear();
        hideFilters();

        // ── Saludo + fecha ────────────────────────────────────────────────────
        String username = AppContext.getInstance().getCurrentUsername();
        LocalDate today = LocalDate.now();
        String dayName  = today.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("es", "ES"));
        String dateStr  = dayName.substring(0, 1).toUpperCase() + dayName.substring(1)
                + ", " + today.format(DateTimeFormatter.ofPattern("d 'de' MMMM", new Locale("es", "ES")));
        int pending = countPendingTasks(home);
        String subText = dateStr + (pending > 0
                ? "  ·  " + pending + " tarea" + (pending > 1 ? "s" : "") + " pendiente" + (pending > 1 ? "s" : "")
                : "  ·  Todo al día ✓");

        HBox greetingBox = new HBox();
        greetingBox.setStyle("-fx-background-color: white; -fx-padding: 20 24 20 24; " +
                "-fx-border-color: #e8e8e8; -fx-border-width: 0 0 1 0;");
        greetingBox.setAlignment(Pos.CENTER_LEFT);
        VBox greetingText = new VBox(3);
        Label greetingLabel = new Label("Buenos días, " + username);
        greetingLabel.setStyle("-fx-font-size: 17px; -fx-font-weight: bold; -fx-text-fill: #1e1e2e;");
        Label subLabel = new Label(subText);
        subLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #888888;");
        greetingText.getChildren().addAll(greetingLabel, subLabel);
        greetingBox.getChildren().add(greetingText);
        taskContainer.getChildren().add(greetingBox);

        // Banner de cumpleaños
        LocalDate birthDate = AppContext.getInstance().getCurrentBirthDate();
        if (birthDate != null) {
            if (birthDate.getMonthValue() == today.getMonthValue()
                    && birthDate.getDayOfMonth() == today.getDayOfMonth()) {
                HBox birthdayBanner = new HBox();
                birthdayBanner.setStyle("-fx-background-color: #fef3c7; -fx-padding: 10 24 10 24; " +
                        "-fx-border-color: #fcd34d; -fx-border-width: 0 0 1 0;");
                birthdayBanner.setAlignment(Pos.CENTER_LEFT);
                Label birthdayLabel = new Label("🎂  ¡Feliz cumpleaños, " +
                        AppContext.getInstance().getCurrentUsername() + "! Que tengas un día genial 🎉");
                birthdayLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #92400e;");
                birthdayBanner.getChildren().add(birthdayLabel);
                taskContainer.getChildren().add(birthdayBanner);
            }
        }

        // ── Stats ─────────────────────────────────────────────────────────────
        int[] stats = computeStats(home);
        HBox statsRow = new HBox(10);
        statsRow.setStyle("-fx-padding: 16 20 8 20;");
        statsRow.getChildren().addAll(
                createStatCard(String.valueOf(stats[0]), "Pendientes",       "#3b82f6"),
                createStatCard(String.valueOf(stats[1]), "En progreso",      "#f59e0b"),
                createStatCard(String.valueOf(stats[2]), "Completadas",      "#22c55e"),
                createStatCard(String.valueOf(stats[3]), "Proyectos activos","#e11d48")
        );
        for (javafx.scene.Node c : statsRow.getChildren()) HBox.setHgrow(c, Priority.ALWAYS);
        taskContainer.getChildren().add(statsRow);

        // ── Dos columnas ──────────────────────────────────────────────────────
        HBox twoCol = new HBox(14);
        twoCol.setStyle("-fx-padding: 8 20 20 20;");
        VBox projectsCol = buildProjectsColumn(home.get("projects"));
        VBox tasksCol    = buildUpcomingTasksColumn(home);
        HBox.setHgrow(projectsCol, Priority.ALWAYS);
        HBox.setHgrow(tasksCol,    Priority.ALWAYS);
        twoCol.getChildren().addAll(projectsCol, tasksCol);
        taskContainer.getChildren().add(twoCol);

        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
    }

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

    private VBox buildProjectsColumn(JsonNode projects) {
        VBox panel = createPanel();
        panel.getChildren().add(createPanelHeader("Proyectos activos ",
                projects != null ? projects.size() + " proyectos" : "0 proyectos"));

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
            if (tasks != null && tasks.isArray())
                for (JsonNode t : tasks)
                    if ("DONE".equals(t.has("status") ? t.get("status").asText() : "")) done++;
            double pct = total > 0 ? (double) done / total * 100 : 0;

            if (idx++ > 0) panel.getChildren().add(new Separator());

            VBox item = new VBox(5);
            item.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(item, Priority.ALWAYS);
            item.setStyle("-fx-padding: 10 0 10 0;");
            HBox nameRow = new HBox();
            nameRow.setMaxWidth(Double.MAX_VALUE);
            nameRow.setAlignment(Pos.CENTER_LEFT);
            Label nameLabel = new Label(pName);
            nameLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label pctLabel = new Label(Math.round(pct) + "%");
            pctLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #888888;");
            nameRow.getChildren().addAll(nameLabel, spacer, pctLabel);

            StackPane barBg = new StackPane();
            barBg.setStyle("-fx-background-color: #f0f0f5; -fx-background-radius: 2px;");
            barBg.setMinHeight(5); barBg.setMaxHeight(5);
            HBox barFill = new HBox();
            String barColor = getCategoryColor(pCategory);
            barFill.setStyle("-fx-background-color: " + barColor + "; -fx-background-radius: 2px;");
            barFill.setMinHeight(5); barFill.setMaxHeight(5);
            barFill.setMaxWidth(Double.MAX_VALUE);
            barBg.getChildren().add(barFill);
            final double pctF = pct;
            barBg.widthProperty().addListener((obs, o, n) ->
                    barFill.setPrefWidth(n.doubleValue() * pctF / 100.0));

            String pStatus   = project.has("status")   && !project.get("status").isNull()
                    ? project.get("status").asText()   : "TODO";
            String pPriority = project.has("priority") && !project.get("priority").isNull()
                    ? project.get("priority").asText() : "MEDIUM";

            HBox metaRow = new HBox(6);
            metaRow.setAlignment(Pos.CENTER_LEFT);
            metaRow.getChildren().addAll(
                    createBadge(translateStatus(pStatus),   "-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                            "-fx-background-radius: 10px; -fx-text-fill: white; " +
                            "-fx-background-color: " + getStatusColor(pStatus) + ";"),
                    createBadge(translatePriority(pPriority), "-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                            "-fx-background-radius: 10px; -fx-text-fill: white; " +
                            "-fx-background-color: " + getPriorityColor(pPriority) + ";"),
                    createBadge(pCategory, getCategoryBadgeStyle(pCategory)),
                    createBadge(total + " tareas", "-fx-background-color: #f0f0f5; " +
                            "-fx-text-fill: #666666; -fx-background-radius: 10px; " +
                            "-fx-font-size: 10px; -fx-padding: 2 7 2 7;"));

            item.getChildren().addAll(nameRow, barBg, metaRow);
            item.setStyle("-fx-padding: 10 0 10 0; -fx-cursor: hand;");
            item.setOnMouseClicked(e -> openProjectDetail(project));
            panel.getChildren().add(item);
        }
        return panel;
    }

    private VBox buildUpcomingTasksColumn(JsonNode home) {
        List<JsonNode> allTasks = new ArrayList<>();
        JsonNode projects = home.get("projects");
        if (projects != null && projects.isArray())
            for (JsonNode p : projects) {
                JsonNode tasks = p.get("tasks");
                if (tasks != null && tasks.isArray())
                    for (JsonNode t : tasks) allTasks.add(t);
            }
        for (String cat : new String[]{"personalTasks", "estudiosTasks", "trabajoTasks"}) {
            JsonNode ct = home.get(cat);
            if (ct != null && ct.isArray()) for (JsonNode t : ct) allTasks.add(t);
        }
        allTasks.removeIf(t -> {
            String s = t.has("status") ? t.get("status").asText() : "";
            return "DONE".equals(s) || "CANCELLED".equals(s);
        });
        allTasks.sort((a, b) -> {
            boolean aH = a.has("dueDate") && !a.get("dueDate").isNull();
            boolean bH = b.has("dueDate") && !b.get("dueDate").isNull();
            if (!aH && !bH) return 0; if (!aH) return 1; if (!bH) return -1;
            return a.get("dueDate").asText().compareTo(b.get("dueDate").asText());
        });
        List<JsonNode> upcoming = allTasks.subList(0, Math.min(6, allTasks.size()));

        VBox panel = createPanel();
        panel.getChildren().add(createPanelHeader("Tareas próximas ",
                allTasks.size() + " pendiente" + (allTasks.size() != 1 ? "s" : "")));

        if (upcoming.isEmpty()) {
            Label empty = new Label("¡Todo al día! Sin tareas pendientes.");
            empty.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px; -fx-padding: 8 0 0 0;");
            panel.getChildren().add(empty);
            return panel;
        }

        LocalDate today = LocalDate.now();
        int idx = 0;
        for (JsonNode task : upcoming) {
            if (idx++ > 0) panel.getChildren().add(new Separator());
            String title    = task.get("title").asText();
            String status   = task.has("status")   ? task.get("status").asText()   : "TODO";
            String priority = task.has("priority") ? task.get("priority").asText() : "MEDIUM";
            String category = task.has("category") ? task.get("category").asText() : "PERSONAL";
            Long   taskId   = task.get("id").asLong();

            String dueLbl = ""; boolean isUrgentDate = false; boolean isOverdue = false;
            if (task.has("dueDate") && !task.get("dueDate").isNull()) {
                try {
                    LocalDate due = LocalDate.parse(task.get("dueDate").asText().substring(0, 10));
                    if (due.isBefore(today)) {
                        dueLbl = "Vencida";
                        isUrgentDate = true;
                        isOverdue = true;
                    }
                    else if (due.equals(today))                  { dueLbl = "Hoy";    isUrgentDate = true; }
                    else if (due.equals(today.plusDays(1))) { dueLbl = "Mañana"; }
                    else dueLbl = due.format(DateTimeFormatter.ofPattern("d MMM", new Locale("es", "ES")));
                } catch (Exception ignored) {}
            }

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 9 0 9 0;");

            Label check = new Label();
            check.setMinSize(16, 16); check.setMaxSize(16, 16);
            check.setStyle("-fx-border-color: #cccccc; -fx-border-radius: 8px; " +
                    "-fx-background-radius: 8px; -fx-cursor: hand;");
            check.setOnMouseClicked(e -> new Thread(() -> {
                try {
                    AppContext.getInstance().getApiService()
                            .patch("/api/tasks/" + taskId + "/status?status=DONE", null);
                    Platform.runLater(this::loadHome);
                } catch (Exception ex) {
                    Platform.runLater(() -> showAlert("Error", "No se pudo actualizar la tarea"));
                }
            }).start());

            Label titleLabel = new Label(title);
            titleLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");

            HBox badges = new HBox(6);
            badges.setAlignment(Pos.CENTER_LEFT);

            if (!dueLbl.isEmpty()) {
                Label dueLabel = new Label(dueLbl);
                if (isOverdue) {
                    dueLabel.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                            "-fx-background-radius: 10px; -fx-text-fill: #991b1b; " +
                            "-fx-background-color: #fee2e2;");
                } else {
                    dueLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " +
                            (isUrgentDate ? "#dc2626" : "#888888") + ";");
                }
                badges.getChildren().add(dueLabel);
            }
            badges.getChildren().add(createBadge(translateStatus(status), "-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                    "-fx-background-radius: 10px; -fx-text-fill: white; " +
                    "-fx-background-color: " + getStatusColor(status) + ";"));

            Label priBadge = new Label(translatePriority(priority));
            priBadge.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                    "-fx-background-radius: 10px; -fx-text-fill: white; " +
                    "-fx-background-color: " + getPriorityColor(priority) + ";");
            badges.getChildren().add(priBadge);

            badges.getChildren().add(createBadge(category, getCategoryBadgeStyle(category)));

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            row.getChildren().addAll(check, titleLabel, badges);
            panel.getChildren().add(row);
        }
        return panel;
    }

    // =========================================================================
    //  LISTA DE TAREAS (vistas que no son home)
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

        // Padding lateral para la lista
        for (JsonNode task : tasks) {
            HBox card = createTaskCard(task);
            VBox wrapper = new VBox(card);
            wrapper.setStyle("-fx-padding: 0 20 0 20;");
            taskContainer.getChildren().add(wrapper);
        }

        // Guardar datos originales para filtros/orden
        currentTasks.clear();
        if (tasks.isArray()) {
            for (JsonNode t : tasks) currentTasks.add(t);
        }
        applyFiltersAndSort(); // renderizar con filtros/orden actuales
    }

    private void applyFiltersAndSort() {
        String statusVal   = statusFilter.getValue();
        String priorityVal = priorityFilter.getValue();
        String sortCrit    = sortFilter.getValue();

        // 1. Filtrar desde los datos originales
        List<JsonNode> result = currentTasks.stream()
                .filter(t -> {
                    if (statusVal == null || statusVal.equals("Todas")) return true;
                    return matchesStatusLabel(t.get("status").asText(), statusVal);
                })
                .filter(t -> {
                    if (priorityVal == null || priorityVal.equals("Todas")) return true;
                    return matchesPriorityLabel(t.get("priority").asText(), priorityVal);
                })
                .collect(java.util.stream.Collectors.toList());

        // 2. Ordenar
        if (sortCrit != null) {
            result.sort((a, b) -> {
                int cmp = switch (sortCrit) {
                    case "Título" ->
                            a.get("title").asText().compareToIgnoreCase(b.get("title").asText());
                    case "ID" ->
                            Long.compare(a.get("id").asLong(), b.get("id").asLong());
                    case "Fecha límite" -> {
                        boolean aH = a.has("dueDate") && !a.get("dueDate").isNull();
                        boolean bH = b.has("dueDate") && !b.get("dueDate").isNull();
                        if (!aH && !bH) yield 0;
                        if (!aH)        yield 1;   // sin fecha → al final
                        if (!bH)        yield -1;
                        yield a.get("dueDate").asText().compareTo(b.get("dueDate").asText());
                    }
                    case "Prioridad" ->
                            Integer.compare(
                                    priorityOrder(a.has("priority") ? a.get("priority").asText() : "MEDIUM"),
                                    priorityOrder(b.has("priority") ? b.get("priority").asText() : "MEDIUM")
                            );
                    default -> 0;
                };
                return sortAscending ? cmp : -cmp;
            });
        }

        // 3. Renderizar
        taskContainer.getChildren().clear();
        taskContainer.getChildren().add(emptyLabel);
        if (result.isEmpty()) {
            emptyLabel.setText("No hay tareas que coincidan con los filtros");
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        for (JsonNode task : result) {
            HBox card = createTaskCard(task);
            VBox wrapper = new VBox(card);
            wrapper.setStyle("-fx-padding: 0 20 0 20;");
            taskContainer.getChildren().add(wrapper);
        }
    }

    private boolean matchesStatusLabel(String enumVal, String label) {
        return switch (enumVal) {
            case "TODO"        -> label.equals("Pendiente");
            case "IN_PROGRESS" -> label.equals("En progreso");
            case "DONE"        -> label.equals("Completada");
            case "CANCELLED"   -> label.equals("Cancelada");
            default            -> false;
        };
    }

    private boolean matchesPriorityLabel(String enumVal, String label) {
        return switch (enumVal) {
            case "LOW"    -> label.equals("Baja");
            case "MEDIUM" -> label.equals("Media");
            case "HIGH"   -> label.equals("Alta");
            case "URGENT" -> label.equals("Urgente");
            default       -> false;
        };
    }

    @FXML
    private void handleClearFilters() {
        resetComboBox(statusFilter,   "Estado");
        resetComboBox(priorityFilter, "Prioridad");
        resetComboBox(sortFilter,     "Criterio");
        sortAscending = true;
        sortDirectionBtn.setText("↑");
        applyFiltersAndSort();
    }

    private HBox createTaskCard(JsonNode task) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setStyle("-fx-background-color: white; -fx-padding: 12 16 12 16; " +
                "-fx-background-radius: 8px; -fx-border-color: #e8e8e8; " +
                "-fx-border-radius: 8px; -fx-border-width: 1; -fx-margin: 0 0 8 0;");

        String status   = task.get("status").asText();
        String title    = task.get("title").asText();
        String priority = task.get("priority").asText();
        Long   taskId   = task.get("id").asLong();

        card.getProperties().put("status",   status);
        card.getProperties().put("priority", priority);
        card.getProperties().put("taskId", taskId);

        CheckBox checkBox = new CheckBox();
        checkBox.setSelected("DONE".equals(status));

        Label titleLabel = new Label(title);
        updateTitleStyle(titleLabel, "DONE".equals(status));
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label statusBadge = new Label(translateStatus(status));
        statusBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getStatusColor(status) + ";");

        Label priorityBadge = new Label(translatePriority(priority));
        priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        Label idLabel = new Label("#" + taskId);
        idLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #9999bb; -fx-padding: 0 4 0 0;");

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
                            reloadTasks();
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

        Button menuBtn = new Button("•••");
        menuBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #666688; " +
                "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; " +
                "-fx-padding: 2 8 2 8; -fx-background-radius: 6px;");
        menuBtn.setOnMouseEntered(e -> menuBtn.setStyle(
                "-fx-background-color: #f0f0f5; -fx-text-fill: #1e1e2e; " +
                        "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; " +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 6px;"));
        menuBtn.setOnMouseExited(e -> menuBtn.setStyle(
                "-fx-background-color: transparent; -fx-text-fill: #666688; " +
                        "-fx-font-size: 16px; -fx-font-weight: bold; -fx-cursor: hand; " +
                        "-fx-padding: 2 8 2 8; -fx-background-radius: 6px;"));

        menuBtn.setOnAction(e -> {
            ContextMenu menu = new ContextMenu();
            menu.setStyle("-fx-background-color: white; -fx-border-color: #e8e8e8; " +
                    "-fx-border-width: 1; -fx-background-radius: 8; -fx-border-radius: 8;");

            MenuItem detail = new MenuItem("👁  Ver detalles");
            detail.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10;");
            detail.setOnAction(ev -> openTaskDetail(task));

            MenuItem edit = new MenuItem("✏️  Editar");
            edit.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10;");
            edit.setOnAction(ev -> handleEditTask(taskId, task));

            MenuItem delete = new MenuItem("🗑  Eliminar");
            delete.setStyle("-fx-font-size: 13px; -fx-padding: 2 10 2 10; -fx-text-fill: #e74c3c;");
            delete.setOnAction(ev -> handleDeleteTask(taskId));

            menu.getItems().addAll(detail, edit, delete);
            menu.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
        });

        boolean isOverdue = false;
        if (task.has("dueDate") && !task.get("dueDate").isNull()
                && !"DONE".equals(status) && !"CANCELLED".equals(status)) {
            try {
                LocalDate dueDate = LocalDate.parse(task.get("dueDate").asText().substring(0, 10));
                isOverdue = dueDate.isBefore(LocalDate.now());
            } catch (Exception ignored) {}
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        if (isOverdue) {
            card.setStyle("-fx-background-color: white; -fx-padding: 12 16 12 14; " +
                    "-fx-background-radius: 8px; -fx-border-color: #e8e8e8; " +
                    "-fx-border-radius: 8px; -fx-border-width: 1; " +
                    "-fx-border-color: #fecaca; " +
                    "-fx-border-left-color: #e74c3c; -fx-border-width: 1 1 1 3;");
            Label overdueLabel = new Label("Vencida");
            overdueLabel.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                    "-fx-background-radius: 10px; -fx-text-fill: #991b1b; " +
                    "-fx-background-color: #fee2e2;");
            card.getChildren().addAll(checkBox, idLabel, titleLabel, overdueLabel, statusBadge, priorityBadge, spacer, menuBtn);
        } else {
            card.getChildren().addAll(checkBox, idLabel, titleLabel, statusBadge, priorityBadge, spacer, menuBtn);
        }
        return card;
    }

    private void openProjectDetail(JsonNode project) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/project-detail-view.fxml"));
            VBox root = loader.load();
            ProjectDetailController controller = loader.getController();
            controller.initData(project);
            Stage dialog = new Stage();
            dialog.setTitle("Detalles del proyecto");
            dialog.setScene(new Scene(root, 620, 600));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el detalle del proyecto");
        }
    }

    private void openTaskDetail(JsonNode task) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/task-detail-view.fxml"));
            VBox root = loader.load();
            TaskDetailController controller = loader.getController();
            controller.initData(task);
            controller.setOnTaskChanged(this::reloadTasks);
            Stage dialog = new Stage();
            dialog.setTitle("Detalles de la tarea");
            dialog.setScene(new Scene(root, 600, 550));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("Error", "No se pudo abrir el detalle de la tarea");
        }
    }

    private void updateTitleStyle(Label l, boolean done) {
        l.setStyle(done
                ? "-fx-font-size: 13px; -fx-text-fill: #aaaaaa; -fx-strikethrough: true;"
                : "-fx-font-size: 13px; -fx-text-fill: #1e1e2e;");
    }

    // =========================================================================
    //  NAVEGACIÓN
    // =========================================================================
    @FXML
    private void handleGoHome() {
        selectedProjectId = null;
        selectedCategory  = null;
        viewingAllTasks = false;
        areaTitle.setText("Inicio");
        removeOverlayPanels();
        showMainArea();
        hideFilters();
        setSidebarActive(btnHome);
        loadHome();
    }

    @FXML
    private void handleAllTasks() {
        removeOverlayPanels();
        showMainArea();
        selectedProjectId = null;
        selectedCategory  = null;
        viewingAllTasks   = true;
        areaTitle.setText("Todas las tareas");
        showFilters();
        setSidebarActive(btnAllTasks);
        new Thread(() -> {
            try {
                HttpResponse<String> r = AppContext.getInstance()
                        .getApiService().get("/api/tasks/personal");
                if (r.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(r.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las tareas"));
            }
        }).start();
    }

    @FXML private void handleCategoryPersonal() {
        viewingAllTasks = false;
        removeOverlayPanels();
        showMainArea();
        loadTasksByCategory("PERSONAL", "👤 Personal");
        setSidebarActive(btnPersonal);
    }
    @FXML private void handleCategoryEstudios() {
        viewingAllTasks = false;
        removeOverlayPanels();
        showMainArea();
        loadTasksByCategory("ESTUDIOS", "📚 Estudios");
        setSidebarActive(btnEstudios);
    }
    @FXML private void handleCategoryTrabajo()  {
        viewingAllTasks = false;
        removeOverlayPanels();
        showMainArea();
        loadTasksByCategory("TRABAJO",  "💼 Trabajo");
        setSidebarActive(btnTrabajo);
    }

    private void loadTasksByCategory(String category, String title) {
        removeOverlayPanels();
        showMainArea();
        selectedProjectId = null;
        selectedCategory  = category;
        areaTitle.setText(title);
        showFilters();
        new Thread(() -> {
            try {
                HttpResponse<String> r = AppContext.getInstance()
                        .getApiService().get("/api/tasks/category/" + category);
                if (r.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(r.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las tareas"));
            }
        }).start();
    }

    private void loadTasksForProject(Long projectId) {
        removeOverlayPanels();
        showMainArea();
        showFilters();
        new Thread(() -> {
            try {
                HttpResponse<String> r = AppContext.getInstance()
                        .getApiService().get("/api/tasks?projectId=" + projectId);
                if (r.statusCode() == 200) {
                    JsonNode tasks = objectMapper.readTree(r.body());
                    Platform.runLater(() -> renderTasks(tasks));
                }
            } catch (Exception e) {
                Platform.runLater(() -> showAlert("Error", "No se pudieron cargar las tareas"));
            }
        }).start();
    }

    // =========================================================================
    //  FILTROS
    // =========================================================================
    @FXML
    private void handleStatusFilter() {
        String selected = statusFilter.getValue();
        if (selected == null || selected.equals("Todos")) { reloadTasks(); return; }
        if (selectedProjectId != null) {
            new Thread(() -> {
                try {
                    HttpResponse<String> r = AppContext.getInstance().getApiService()
                            .get("/api/tasks/filter/status?projectId=" + selectedProjectId + "&status=" + selected);
                    if (r.statusCode() == 200) {
                        JsonNode tasks = objectMapper.readTree(r.body());
                        Platform.runLater(() -> renderTasks(tasks));
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("Error", "No se pudieron filtrar las tareas"));
                }
            }).start();
        } else filterTaskCardsByStatus(selected);
    }

    @FXML
    private void handlePriorityFilter() {
        String selected = priorityFilter.getValue();
        if (selected == null || selected.equals("Todas")) { reloadTasks(); return; }
        if (selectedProjectId != null) {
            new Thread(() -> {
                try {
                    HttpResponse<String> r = AppContext.getInstance().getApiService()
                            .get("/api/tasks/filter/priority?projectId=" + selectedProjectId + "&priority=" + selected);
                    if (r.statusCode() == 200) {
                        JsonNode tasks = objectMapper.readTree(r.body());
                        Platform.runLater(() -> renderTasks(tasks));
                    }
                } catch (Exception e) {
                    Platform.runLater(() -> showAlert("Error", "No se pudieron filtrar las tareas"));
                }
            }).start();
        } else filterTaskCardsByPriority(selected);
    }

    private void filterTaskCardsByStatus(String status) {
        taskContainer.getChildren().forEach(node -> {
            if (node instanceof VBox wrapper && !wrapper.getChildren().isEmpty()
                    && wrapper.getChildren().getFirst() instanceof HBox card) {
                boolean match = status.equals(card.getProperties().get("status"));
                wrapper.setVisible(match); wrapper.setManaged(match);
            }
        });
    }

    private void filterTaskCardsByPriority(String priority) {
        taskContainer.getChildren().forEach(node -> {
            if (node instanceof VBox wrapper && !wrapper.getChildren().isEmpty()
                    && wrapper.getChildren().getFirst() instanceof HBox card) {
                boolean match = priority.equals(card.getProperties().get("priority"));
                wrapper.setVisible(match); wrapper.setManaged(match);
            }
        });
    }

    private void handleSortFilter() {
        String sort = sortFilter.getValue();
        if (sort == null || sort.equals("Ordenar por")) return;

        List<Node> wrappers = new ArrayList<>(taskContainer.getChildren());
        wrappers.sort((a, b) -> {
            if (!(a instanceof VBox va) || !(b instanceof VBox vb)) return 0;
            if (va.getChildren().isEmpty() || vb.getChildren().isEmpty()) return 0;
            if (!(va.getChildren().get(0) instanceof HBox ca) ||
                    !(vb.getChildren().get(0) instanceof HBox cb)) return 0;

            switch (sort) {
                case "Título A-Z" -> {
                    String ta = getCardTitle(ca);
                    String tb = getCardTitle(cb);
                    return ta.compareToIgnoreCase(tb);
                }
                case "Título Z-A" -> {
                    String ta = getCardTitle(ca);
                    String tb = getCardTitle(cb);
                    return tb.compareToIgnoreCase(ta);
                }
                case "ID ↑" -> {
                    long ia = getCardId(ca), ib = getCardId(cb);
                    return Long.compare(ia, ib);
                }
                case "ID ↓" -> {
                    long ia = getCardId(ca), ib = getCardId(cb);
                    return Long.compare(ib, ia);
                }
            }
            return 0;
        });
        taskContainer.getChildren().setAll(wrappers);
    }

    @FXML
    private void handleSortDirection() {
        sortAscending = !sortAscending;
        // Actualizar texto del botón (necesitas el @FXML ref)
        sortDirectionBtn.setText(sortAscending ? "↑" : "↓");
        applyFiltersAndSort();
    }

    /** Orden numérico: URGENT=0 (más urgente primero en asc) */
    private int priorityOrder(String p) {
        return switch (p) {
            case "URGENT" -> 0;
            case "HIGH"   -> 1;
            case "MEDIUM" -> 2;
            case "LOW"    -> 3;
            default       -> 2;
        };
    }

    private void renderFilteredTasks(List<JsonNode> tasks) {
        taskContainer.getChildren().clear();
        taskContainer.getChildren().add(emptyLabel);
        if (tasks.isEmpty()) {
            emptyLabel.setText("No hay tareas que coincidan con los filtros");
            emptyLabel.setVisible(true);
            emptyLabel.setManaged(true);
            return;
        }
        emptyLabel.setVisible(false);
        emptyLabel.setManaged(false);
        for (JsonNode task : tasks) {
            HBox card = createTaskCard(task);
            VBox wrapper = new VBox(card);
            wrapper.setStyle("-fx-padding: 0 20 0 20;");
            taskContainer.getChildren().add(wrapper);
        }
    }

    private String getCardTitle(HBox card) {
        // El título es el Label con HGrow=ALWAYS (segundo hijo tras el checkbox)
        return card.getChildren().stream()
                .filter(n -> n instanceof Label && HBox.getHgrow(n) == Priority.ALWAYS)
                .map(n -> ((Label) n).getText())
                .findFirst().orElse("");
    }

    private long getCardId(HBox card) {
        Object id = card.getProperties().get("taskId");
        return id instanceof Long l ? l : 0L;
    }

    // =========================================================================
    //  OVERLAYS — papelera y ajustes
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
            setSidebarActive(btnTrash);
            swapMainAreaWith(trashView);
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
            setSidebarActive(btnSettings);
            swapMainAreaWith(settingsView);
        } catch (Exception e) {
            e.printStackTrace(); // ← añade esta línea
            showAlert("Error", "No se pudo abrir los ajustes: " + e.getMessage());
        }
    }

    /** Oculta el mainArea y añade el overlay al HBox central */
    private void swapMainAreaWith(javafx.scene.Node overlay) {
        mainArea.setVisible(false);
        mainArea.setManaged(false);
        HBox centerHBox = getCenterHBox();
        centerHBox.getChildren().removeIf(n -> {
            Object ud = n.getUserData();
            return "trash".equals(ud) || "settings".equals(ud) || "profile".equals(ud);
        });
        centerHBox.getChildren().add(overlay);
    }

    private void showMainArea() {
        mainArea.setVisible(true);
        mainArea.setManaged(true);
    }

    private void removeOverlayPanels() {
        getCenterHBox().getChildren().removeIf(n -> {
            Object ud = n.getUserData();
            return "trash".equals(ud) || "settings".equals(ud) || "profile".equals(ud);
        });
        showMainArea();
    }

    private HBox getCenterHBox() {
        javafx.scene.layout.BorderPane root =
                (javafx.scene.layout.BorderPane) btnHome.getScene().getRoot();
        return (HBox) root.getCenter();
    }

    private static final String SIDEBAR_ACTIVE   = "-fx-background-color: #2a1f4e; -fx-text-fill: #a78bfa; -fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; -fx-padding: 8 16 8 18;";
    private static final String SIDEBAR_INACTIVE = "-fx-background-color: transparent; -fx-text-fill: #9999bb; -fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; -fx-padding: 7 16 7 16;";

    private void setSidebarActive(Button active) {
        clearSidebarSelection();
        active.setStyle(SIDEBAR_ACTIVE);
    }

    private void setSidebarProjectActive(Long projectId) {
        // Desactivar todos los botones fijos del sidebar
        for (Button btn : new Button[]{btnHome, btnAllTasks, btnPersonal,
                btnEstudios, btnTrabajo, btnSettings, btnTrash}) {
            btn.setStyle(SIDEBAR_INACTIVE);
        }
        // Recorrer las filas del projectListContainer
        for (Node node : projectListContainer.getChildren()) {
            if (node instanceof HBox row) {
                // El botón del proyecto es el segundo hijo (índice 1)
                if (row.getChildren().size() >= 2 && row.getChildren().get(1) instanceof Button btn) {
                    Object tag = row.getUserData();
                    if (tag instanceof Long pid && pid.equals(projectId)) {
                        btn.setStyle("-fx-background-color: #2a1f4e; -fx-text-fill: #a78bfa; " +
                                "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                                "-fx-padding: 7 4 7 6;");

                        if (row.getChildren().size() >= 3 && row.getChildren().get(2) instanceof Button dots) {
                            dots.setStyle("-fx-background-color: transparent; -fx-text-fill: #9999bb; " +
                                    "-fx-font-size: 10px; -fx-font-weight: bold; -fx-cursor: hand; " +
                                    "-fx-padding: 2 6 2 6; -fx-background-radius: 6px;");
                        }
                    } else {
                        btn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9999bb; " +
                                "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                                "-fx-padding: 7 4 7 6;");

                        if (row.getChildren().size() >= 3 && row.getChildren().get(2) instanceof Button dots) {
                            dots.setStyle("-fx-background-color: transparent; -fx-text-fill: transparent; " +
                                    "-fx-font-size: 10px; -fx-font-weight: bold; -fx-cursor: hand; " +
                                    "-fx-padding: 2 6 2 6; -fx-background-radius: 6px;");
                        }
                    }
                }
            }
        }
    }

    private void showFilters() {
        resetComboBox(statusFilter,   "Estado");
        resetComboBox(priorityFilter, "Prioridad");
        resetComboBox(sortFilter,     "Criterio");
        sortAscending = true;
        sortDirectionBtn.setText("↑");
        taskFiltersBar.setVisible(true);
        taskFiltersBar.setManaged(true);
        showSearch();
        createButton.setText("＋  Nueva tarea");
    }
    private void resetComboBox(ComboBox<String> combo, String promptText) {
        String currentValue = combo.getValue();
        combo.setValue(null);
        combo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setText(promptText);
                    setStyle("-fx-text-fill: #aaaaaa;");
                } else {
                    setText(item);
                    setStyle("-fx-text-fill: #1e1e2e;");
                }
            }
        });
    }
    private void hideFilters() {
        taskFiltersBar.setVisible(false);
        taskFiltersBar.setManaged(false);
        hideSearch();
        createButton.setText("＋  Crear  ▾");
    }
    private void showSearch() {
        searchField.setVisible(true);
        searchField.setManaged(true);
        searchField.clear();
    }
    private void hideSearch() {
        searchField.setVisible(false);
        searchField.setManaged(false);
        searchField.clear();
    }

    @FXML
    private void handleSearch() {
        String query = searchField.getText().trim().toLowerCase();
        taskContainer.getChildren().forEach(node -> {
            if (node instanceof VBox wrapper && !wrapper.getChildren().isEmpty()
            && wrapper.getChildren().get(0) instanceof HBox card) {
                boolean match = true;

                if (!query.isEmpty()) {

                    // Buscar por ID numérico
                    Object idProp = card.getProperties().get("taskId");
                    boolean matchesId = idProp instanceof Long l &&
                            String.valueOf(l).contains(query);

                    // Buscar por título
                    boolean matchesTitle = card.getChildren().stream()
                            .filter(n -> n instanceof Label && HBox.getHgrow(n) == Priority.ALWAYS)
                            .map(n -> ((Label) n).getText().toLowerCase())
                            .anyMatch(t -> t.contains(query));
                    match = matchesId || matchesTitle;
                }
                wrapper.setVisible(match);
                wrapper.setManaged(match);
            }
        });
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
        final Long currentProjectId = selectedProjectId;
        final String currentCategory = selectedCategory;
        final String currentTitle = areaTitle.getText();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-task-dialog.fxml"));
            VBox root = loader.load();
            NewTaskController controller = loader.getController();
            controller.initData(selectedProjectId);

            if (currentCategory != null) {
                controller.setPreSelectedCategory(currentCategory);
            }

            controller.setOnTaskCreated(() -> {
                if (currentProjectId != null) {
                    loadTasksForProject(currentProjectId);
                } else if (currentCategory != null) {
                    loadTasksByCategory(currentCategory, currentTitle);
                } else if (!"Inicio".equals(currentTitle)) {
                    handleAllTasks();
                } else {
                    loadHome();
                }
            });
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
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> resp = AppContext.getInstance()
                                .getApiService().delete("/api/projects/" + projectId);
                        Platform.runLater(() -> {
                            if (resp.statusCode() == 200 || resp.statusCode() == 204) {
                                if (projectId.equals(selectedProjectId)) handleGoHome();
                                loadProjects(); reloadTasks();
                                if (trashController != null) trashController.refresh();
                            } else showAlert("Error", "No se pudo eliminar el proyecto");
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
        final String currentTitle     = areaTitle.getText();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Eliminar tarea");
        confirm.setHeaderText(null);
        confirm.setContentText("¿Seguro que quieres eliminar esta tarea? Irá a la papelera.");
        confirm.showAndWait().ifPresent(r -> {
            if (r == ButtonType.OK) {
                new Thread(() -> {
                    try {
                        HttpResponse<String> resp = AppContext.getInstance()
                                .getApiService().delete("/api/tasks/" + taskId);
                        Platform.runLater(() -> {
                            if (resp.statusCode() == 200 || resp.statusCode() == 204) {
                                if (currentProjectId != null) {
                                    loadTasksForProject(currentProjectId);
                                }
                                else if (currentCategory != null) {
                                    loadTasksByCategory(currentCategory, currentTitle);
                                } else if (!"Inicio".equals(currentTitle)) {
                                    handleAllTasks();
                                } else {
                                    loadHome();
                                }
                                if (trashController != null) trashController.refresh();
                            } else showAlert("Error", "No se pudo eliminar la tarea");
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
            Stage stage = (Stage) mainArea.getScene().getWindow();
            stage.setScene(new Scene(loader.load(), 400, 500));
            stage.setTitle("TaskMaster");
        } catch (IOException e) {
            showAlert("Error", "No se pudo cerrar la sesión");
        }
    }

    // =========================================================================
    //  HELPERS
    // =========================================================================
    private void reloadTasks() {
        if (selectedProjectId != null) loadTasksForProject(selectedProjectId);
        else if (selectedCategory != null) loadTasksByCategory(selectedCategory, areaTitle.getText());
        else if (viewingAllTasks) handleAllTasks();
        else loadHome();
    }

    private int[] computeStats(JsonNode home) {
        int pending = 0, inProgress = 0, done = 0, activeProjects = 0;
        JsonNode projects = home.get("projects");
        if (projects != null && projects.isArray()) {
            activeProjects = projects.size();
            for (JsonNode p : projects) {
                JsonNode tasks = p.get("tasks");
                if (tasks != null && tasks.isArray())
                    for (JsonNode t : tasks) {
                        String s = t.has("status") ? t.get("status").asText() : "";
                        if ("TODO".equals(s)) pending++;
                        else if ("IN_PROGRESS".equals(s)) inProgress++;
                        else if ("DONE".equals(s)) done++;
                    }
            }
        }
        for (String cat : new String[]{"personalTasks", "estudiosTasks", "trabajoTasks"}) {
            JsonNode ct = home.get(cat);
            if (ct != null && ct.isArray())
                for (JsonNode t : ct) {
                    String s = t.has("status") ? t.get("status").asText() : "";
                    if ("TODO".equals(s)) pending++;
                    else if ("IN_PROGRESS".equals(s)) inProgress++;
                    else if ("DONE".equals(s)) done++;
                }
        }
        return new int[]{pending, inProgress, done, activeProjects};
    }

    private int countPendingTasks(JsonNode home) {
        int[] s = computeStats(home); return s[0] + s[1];
    }

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

    private void clearSidebarSelection() {
        for (Button btn : new Button[]{btnHome, btnAllTasks, btnPersonal,
                btnEstudios, btnTrabajo, btnSettings, btnTrash}) {
            btn.setStyle(SIDEBAR_INACTIVE);
        }
        for (Node node : projectListContainer.getChildren()) {
            if (node instanceof HBox row) {
                if (row.getChildren().size() >= 2 && row.getChildren().get(1) instanceof Button b)
                    b.setStyle("-fx-background-color: transparent; -fx-text-fill: #9999bb; " +
                            "-fx-font-size: 13px; -fx-cursor: hand; -fx-alignment: CENTER-LEFT; " +
                            "-fx-padding: 7 4 7 6;");
                if (row.getChildren().size() >= 3 && row.getChildren().get(2) instanceof Button dots)
                    dots.setStyle("-fx-background-color: transparent; -fx-text-fill: transparent; " +
                            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-cursor: hand; " +
                            "-fx-padding: 2 6 2 6; -fx-background-radius: 6px;");
            }
        }
    }

    private Label createBadge(String text, String style) {
        Label badge = new Label(text); badge.setStyle(style); return badge;
    }

    private String getCategoryColor(String c) {
        return switch (c) {
            case "PERSONAL" -> COLOR_PERSONAL;
            case "ESTUDIOS" -> COLOR_ESTUDIOS;
            case "TRABAJO"  -> COLOR_TRABAJO;
            default -> COLOR_PERSONAL;
        };
    }

    private String getCategoryColorForIndex(int i) {
        String[] colors = {COLOR_PERSONAL, COLOR_ESTUDIOS, COLOR_TRABAJO, "#60a5fa", "#f472b6"};
        return colors[i % colors.length];
    }

    private String getCategoryBadgeStyle(String c) {
        return switch (c) {
            case "PERSONAL" -> "-fx-background-color: #f3e8ff; -fx-text-fill: #6b21a8; " +
                    "-fx-background-radius: 10px; -fx-font-size: 10px; -fx-padding: 2 7 2 7;";
            case "ESTUDIOS" -> "-fx-background-color: #fef3c7; -fx-text-fill: #92400e; " +
                    "-fx-background-radius: 10px; -fx-font-size: 10px; -fx-padding: 2 7 2 7;";
            case "TRABAJO"  -> "-fx-background-color: #dbeafe; -fx-text-fill: #1e40af; " +
                    "-fx-background-radius: 10px; -fx-font-size: 10px; -fx-padding: 2 7 2 7;";
            default -> "-fx-background-color: #f0f0f5; -fx-text-fill: #666666; " +
                    "-fx-background-radius: 10px; -fx-font-size: 10px; -fx-padding: 2 7 2 7;";
        };
    }

    private String getStatusColor(String s) {
        return switch (s) {
            case "TODO" -> "#95a5a6"; case "IN_PROGRESS" -> "#3498db";
            case "DONE" -> "#2ecc71"; case "CANCELLED"   -> "#e74c3c";
            default -> "#95a5a6";
        };
    }

    private String getPriorityColor(String p) {
        return switch (p) {
            case "URGENT" -> "#e74c3c"; case "HIGH"   -> "#e67e22";
            case "MEDIUM" -> "#3498db"; case "LOW"    -> "#95a5a6";
            default -> "#95a5a6";
        };
    }

    private String translateStatus(String status) {
        return switch (status) {
            case "TODO"        -> "PENDIENTE";
            case "IN_PROGRESS" -> "EN CURSO";
            case "DONE"        -> "COMPLETADA";
            case "CANCELLED"   -> "CANCELADA";
            default            -> status;
        };
    }


    private String translatePriority(String priority) {
        return switch (priority) {
            case "LOW"    -> "BAJA";
            case "MEDIUM" -> "MEDIA";
            case "HIGH"   -> "ALTA";
            case "URGENT" -> "URGENTE";
            default       -> priority;
        };
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title); alert.setHeaderText(null);
        alert.setContentText(message); alert.showAndWait();
    }
}