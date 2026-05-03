package com.taskmaster.taskmasterfrontend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.taskmaster.taskmasterfrontend.util.*;
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
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controlador principal de la aplicación TaskMaster.
 *
 * <p>Gestiona la navegación entre todas las vistas (home, tareas, proyectos,
 * ajustes, seguridad, papelera y ayuda), el sidebar con los proyectos del usuario,
 * los filtros y el orden de las tareas, la búsqueda en tiempo real, y la apertura
 * de diálogos modales para crear, editar y eliminar tareas y proyectos.</p>
 *
 * <p>Actúa como coordinador central: instancia los controladores de cada vista,
 * les inyecta sus callbacks y gestiona la pila de navegación para permitir
 * volver a la vista anterior.</p>
 *
 * @author Carlos
 */
public class MainController {

    // ── FXML refs ─────────────────────────────────────────────────────────────
    @FXML private Label userMenuButton;
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
    @FXML private Label filterLabel;
    @FXML private Label sortLabel;
    @FXML private Button clearFiltersBtn;
    @FXML private HBox  taskFiltersBar;
    @FXML private ComboBox<String> statusFilter;
    @FXML private ComboBox<String> priorityFilter;
    @FXML private ComboBox<String> sortFilter;
    @FXML private Button sortDirectionBtn;
    @FXML private Label areaTitle;
    @FXML private Button createButton;
    @FXML private VBox  mainArea;
    @FXML private TextField searchField;
    @FXML private Button btnSecurity;
    @FXML private StackPane sidebarAvatarContainer;
    @FXML private Button btnHelp;
    @FXML private HBox searchContainer;
    @FXML private FontIcon sortDirectionIcon;

    private AvatarView sidebarAvatar;

    private final java.util.Deque<Runnable> navigationStack = new java.util.ArrayDeque<>();
    private ProjectDetailController activeProjectDetailController;
    private final LanguageManager lm = LanguageManager.getInstance();

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
    /** Color de acento de la categoría Personal. */
    private static final String COLOR_PERSONAL = "#a78bfa";

    /** Color de acento de la categoría Estudios. */
    private static final String COLOR_ESTUDIOS = "#34d399";

    /** Color de acento de la categoría Trabajo. */
    private static final String COLOR_TRABAJO  = "#fb923c";

    /**
     * Inicializa la pantalla principal: configura el avatar del sidebar,
     * el menú de usuario, los combos de filtros y orden, carga los proyectos
     * del sidebar y navega a la vista home.
     */
    @FXML
    public void initialize() {
        sidebarAvatar = new AvatarView(32);
        sidebarAvatarContainer.getChildren().setAll(sidebarAvatar);
        sidebarAvatar.loadForCurrentUser();

        String username = AppContext.getInstance().getCurrentUsername();

        String initials = username.length() >= 2
                ? username.substring(0, 2).toUpperCase()
                : username.toUpperCase();
        userMenuButton.setText(username);

        statusFilter.setItems(FXCollections.observableArrayList(
                lm.get("common.all"), lm.get("status.todo"), lm.get("status.inprogress"),
                lm.get("status.done"), lm.get("status.cancelled")));
        statusFilter.setPromptText(lm.get("common.status"));
        statusFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> applyFiltersAndSort());

        priorityFilter.setItems(FXCollections.observableArrayList(
                lm.get("common.all"), lm.get("priority.low"), lm.get("priority.medium"),
                lm.get("priority.high"), lm.get("priority.urgent")));
        priorityFilter.setPromptText(lm.get("common.priority"));
        priorityFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> applyFiltersAndSort());

        sortFilter.setItems(FXCollections.observableArrayList(
                lm.get("sort.title"), lm.get("id"),
                lm.get("common.duedate"), lm.get("common.priority")));
        sortFilter.setPromptText(lm.get("sort.criteria"));
        sortFilter.getSelectionModel().selectedItemProperty().addListener(
                (obs, o, n) -> applyFiltersAndSort());
        loadProjects();
        loadHome();
        LanguageManager.getInstance().bundleProperty().addListener((obs, oldBundle, newBundle) -> {
            refreshSidebar();
        });

        Platform.runLater(() ->
                System.out.println("Stylesheets: " + btnHome.getScene().getStylesheets())
        );
    }

    // ── Menú de usuario ───────────────────────────────────────────────────────

    /**
     * Muestra el menú contextual del usuario con las opciones
     * "Ver perfil" y "Cerrar sesión".
     */
    @FXML
    private void handleUserMenu() {
        ContextMenu menu = new ContextMenu();

        MenuItem viewProfile = new MenuItem(lm.get("topbar.profile"));
        viewProfile.setGraphic(new FontIcon("fas-id-card"));
        viewProfile.setOnAction(e -> handleViewProfile());

        SeparatorMenuItem sep = new SeparatorMenuItem();

        MenuItem logout = new MenuItem(lm.get("topbar.logout"));
        logout.setGraphic(new FontIcon("fas-sign-out-alt"));
        logout.getStyleClass().add("menu-item-danger-icon");
        logout.setOnAction(e -> handleLogout());

        menu.getItems().addAll(viewProfile, sep, logout);
        menu.show(userMenuButton, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    /**
     * Navega a la vista de perfil del usuario, limpia la selección
     * del sidebar y registra el callback para refrescar el username
     * y el avatar al actualizar el perfil.
     */
    private void handleViewProfile() {
        clearSidebarSelection();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/profile-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox profileView = loader.load();
            HBox.setHgrow(profileView, Priority.ALWAYS);
            profileView.setUserData("profile");
            ProfileController controller = loader.getController();
            controller.setOnProfileUpdated(() -> {
                String username = AppContext.getInstance().getCurrentUsername();
                userMenuButton.setText(username);
                if (sidebarAvatar != null) sidebarAvatar.refresh();
                loadHome();
            });
            swapMainAreaWith(profileView);
        } catch (IOException e) {
            showAlert("error.title", "error.open.profile");
        }
    }

    // ── Botón crear ───────────────────────────────────────────────────────────

    /**
     * Gestiona el botón de creación. En la vista home muestra un menú
     * contextual con las opciones "Nuevo proyecto" y "Nueva tarea";
     * en el resto de vistas abre directamente el diálogo de nueva tarea.
     */
    @FXML
    private void handleCreateMenu() {
        boolean isHome = selectedProjectId == null && selectedCategory == null && !viewingAllTasks;

        if (!isHome) {
            handleNewTask();
            return;
        }

        ContextMenu menu = new ContextMenu();

        MenuItem newProject = new MenuItem(lm.get("topbar.new.project"));
        newProject.setGraphic(new FontIcon("fas-folder-plus"));
        newProject.setOnAction(e -> handleNewProject());

        MenuItem newTask = new MenuItem(lm.get("topbar.new.task"));
        newTask.setGraphic(new FontIcon("fas-plus"));
        newTask.setOnAction(e -> handleNewTask());

        menu.getItems().addAll(newProject, newTask);
        menu.show(createButton, javafx.geometry.Side.BOTTOM, 0, 4);
    }

    // ── Proyectos — sidebar ───────────────────────────────────────────────────

    /**
     * Obtiene los proyectos del usuario desde el backend y los renderiza
     * en el sidebar. Puede llamarse desde otros controladores para forzar
     * una actualización.
     */
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
                Platform.runLater(() -> showAlert("error.title", "error.load.projects"));
            }
        }).start();
    }

    /**
     * Construye las filas del sidebar de proyectos con el indicador de color,
     * el botón de nombre y el botón de menú contextual (editar/eliminar).
     * Configura también los efectos de hover y la selección activa.
     *
     * @param names Nombres de los proyectos.
     * @param ids   Identificadores de los proyectos.
     * @param nodes Nodos JSON de cada proyecto, usados al abrir el detalle.
     */
    private void renderSidebar(List<String> names, List<Long> ids, List<JsonNode> nodes) {
        projectListContainer.getChildren().clear();
        for (int i = 0; i < names.size(); i++) {
            final Long   pid  = ids.get(i);
            final String name = names.get(i);
            final JsonNode pNode = nodes.get(i);

            String dotColor = getCategoryColorForIndex(i);

            HBox row = new HBox(4);
            row.setUserData(pid);
            row.setAlignment(Pos.CENTER_LEFT);

            Label dot = new Label("●");
            dot.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 9px; -fx-padding: 0 0 0 16;");

            Button btn = new Button(name);
            btn.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(btn, Priority.ALWAYS);
            btn.getStyleClass().add("sidebar-project-btn");

            Button menuBtn = new Button();
            menuBtn.getStyleClass().add("sidebar-project-dots");
            FontIcon dotsIcon = new FontIcon("fas-ellipsis-h");
            dotsIcon.getStyleClass().add("sidebar-project-dots-icon");
            menuBtn.setGraphic(dotsIcon);

            btn.setOnAction(e -> {
                selectedProjectId = pid;
                selectedCategory  = null;
                setSidebarProjectActive(pid);
                openProjectDetail(pNode);
            });

            menuBtn.setOnAction(e -> {
                ContextMenu cm = new ContextMenu();

                MenuItem edit = new MenuItem(lm.get("common.menu.edit"));
                edit.setGraphic(new FontIcon("fas-pen"));
                edit.setOnAction(ev -> handleEditProject(pid, name));

                MenuItem delete = new MenuItem(lm.get("common.menu.delete"));
                delete.setGraphic(new FontIcon("fas-trash"));
                delete.getStyleClass().add("menu-item-danger");
                delete.setOnAction(ev -> handleDeleteProject(pid, name));

                cm.getItems().addAll(edit, delete);
                cm.show(menuBtn, javafx.geometry.Side.BOTTOM, 0, 0);
            });

            row.setOnMouseEntered(e -> {
                if (!pid.equals(selectedProjectId)) {
                    btn.getStyleClass().removeAll("sidebar-project-btn");
                    btn.getStyleClass().add("sidebar-project-btn-hover");
                }
                menuBtn.getStyleClass().removeAll("sidebar-project-dots");
                menuBtn.getStyleClass().add("sidebar-project-dots-visible");
            });

            row.setOnMouseExited(e -> {
                if (!pid.equals(selectedProjectId)) {
                    btn.getStyleClass().removeAll("sidebar-project-btn-hover");
                    if (!btn.getStyleClass().contains("sidebar-project-btn"))
                        btn.getStyleClass().add("sidebar-project-btn");
                }
                menuBtn.getStyleClass().removeAll("sidebar-project-dots-visible");
                if (!menuBtn.getStyleClass().contains("sidebar-project-dots"))
                    menuBtn.getStyleClass().add("sidebar-project-dots");
            });

            row.getChildren().addAll(dot, btn, menuBtn);
            projectListContainer.getChildren().add(row);
        }
    }

    // ── Home ──────────────────────────────────────────────────────────────────

    /**
     * Obtiene los datos de la vista home desde el backend y los renderiza.
     */
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
                Platform.runLater(() -> showAlert("error.title", "error.load.home"));
            }
        }).start();
    }

    /**
     * Construye y renderiza la vista home: saludo con fecha, banner de
     * cumpleaños si aplica, tarjetas de estadísticas y las dos columnas
     * de proyectos activos y tareas próximas.
     *
     * @param home Nodo JSON con los datos de la vista home.
     */
    private void renderHome(JsonNode home) {
        taskContainer.getChildren().clear();
        hideFilters();

        // ── Saludo + fecha ────────────────────────────────────────────────────
        String username = AppContext.getInstance().getCurrentUsername();
        LocalDate today = LocalDate.now();
        Locale locale = LanguageManager.getInstance().getBundle().getLocale();
        String dayName = today.getDayOfWeek().getDisplayName(TextStyle.FULL, locale);
        String dateStr = dayName.substring(0, 1).toUpperCase() + dayName.substring(1)
                + ", " + today.format(DateTimeFormatter.ofPattern(lm.get("home.date.pattern"), locale));
        int pending = countPendingTasks(home);
        String pendingStr = pending > 1
                ? java.text.MessageFormat.format(lm.get("home.pending.tasks.plural"), pending)
                : pending == 1
                ? java.text.MessageFormat.format(lm.get("home.pending.tasks"), pending)
                : lm.get("home.up.to.date");
        String subText = dateStr + "  ·  " + pendingStr;

        HBox greetingBox = new HBox();
        greetingBox.getStyleClass().add("home-greeting-box");
        greetingBox.setAlignment(Pos.CENTER_LEFT);
        VBox greetingText = new VBox(3);
        Label greetingLabel = new Label(
                java.text.MessageFormat.format(lm.get("home.greeting"),
                        AppContext.getInstance().getCurrentUsername()));
        greetingLabel.getStyleClass().add("home-greeting-label");
        Label subLabel = new Label(subText);
        subLabel.getStyleClass().add("home-greeting-sub");
        greetingText.getChildren().addAll(greetingLabel, subLabel);
        greetingBox.getChildren().add(greetingText);
        taskContainer.getChildren().add(greetingBox);

        // Banner de cumpleaños
        LocalDate birthDate = AppContext.getInstance().getCurrentBirthDate();
        if (birthDate != null) {
            if (birthDate.getMonthValue() == today.getMonthValue()
                    && birthDate.getDayOfMonth() == today.getDayOfMonth()) {
                HBox birthdayBanner = new HBox(10);
                birthdayBanner.getStyleClass().add("birthday-banner");
                birthdayBanner.setAlignment(Pos.CENTER_LEFT);

                FontIcon cakeIcon = new FontIcon("fas-birthday-cake");
                cakeIcon.getStyleClass().add("birthday-banner-icon");

                Label birthdayLabel = new Label(
                        java.text.MessageFormat.format(lm.get("birthday.header"),
                                AppContext.getInstance().getCurrentUsername()));
                birthdayLabel.getStyleClass().add("birthday-banner-label");

                birthdayBanner.getChildren().addAll(cakeIcon, birthdayLabel);
                taskContainer.getChildren().add(birthdayBanner);
            }
        }

        // ── Stats ─────────────────────────────────────────────────────────────
        int[] stats = computeStats(home);
        HBox statsRow = new HBox(10);
        statsRow.setStyle("-fx-padding: 16 20 8 20;");
        statsRow.getChildren().addAll(
                createStatCard(String.valueOf(stats[0]), lm.get("profile.stats.pending"),    "#3b82f6"),
                createStatCard(String.valueOf(stats[1]), lm.get("status.inprogress"), "#f59e0b"),
                createStatCard(String.valueOf(stats[2]), lm.get("common.done"),       "#22c55e"),
                createStatCard(String.valueOf(stats[3]), lm.get("common.active.projects"),   "#e11d48")
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

    /**
     * Crea una tarjeta de estadística con un número, una etiqueta y un
     * punto de color indicador.
     *
     * @param number   Valor numérico a mostrar.
     * @param label    Etiqueta descriptiva del valor.
     * @param dotColor Color del punto indicador en formato hex.
     * @return {@link VBox} con el contenido visual de la tarjeta.
     */
    private VBox createStatCard(String number, String label, String dotColor) {
        VBox card = new VBox(4);
        card.getStyleClass().add("stat-card");
        card.setAlignment(Pos.TOP_LEFT);

        Label num = new Label(number);
        num.getStyleClass().add("stat-card-number");

        HBox labelRow = new HBox(5);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + dotColor + "; -fx-font-size: 8px;");
        Label lbl = new Label(label);
        lbl.getStyleClass().add("stat-card-label");
        labelRow.getChildren().addAll(dot, lbl);
        card.getChildren().addAll(num, labelRow);
        return card;
    }

    /**
     * Construye la columna de proyectos activos de la vista home, mostrando
     * el nombre, barra de progreso, badges de estado/prioridad/categoría y
     * el contador de tareas de cada proyecto.
     *
     * @param projects Array JSON con los proyectos activos del usuario.
     * @return {@link VBox} con el panel de proyectos.
     */
    private VBox buildProjectsColumn(JsonNode projects) {
        VBox panel = createPanel();
        panel.getChildren().add(createPanelHeader(lm.get("common.active.projects"),
                projects != null ? projects.size() + " " + lm.get("common.active.projects").toLowerCase() : "0"));

        if (projects == null || !projects.isArray() || projects.isEmpty()) {
            Label empty = new Label(lm.get("home.no.projects"));
            empty.getStyleClass().add("detail-empty-label");
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
            nameLabel.getStyleClass().add("home-panel-title");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label pctLabel = new Label(Math.round(pct) + "%");
            pctLabel.getStyleClass().add("project-stat-label");
            nameRow.getChildren().addAll(nameLabel, spacer, pctLabel);

            StackPane barBg = new StackPane();
            barBg.getStyleClass().add("progress-bar-bg");
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
                    createBadge(total + " " + lm.get("common.tasks").toLowerCase(), "-fx-background-color: -tm-bg-app; " +
                            "-fx-text-fill: -tm-text-secondary; -fx-background-radius: 10px; " +
                            "-fx-font-size: 10px; -fx-padding: 2 7 2 7;"));

            item.getChildren().addAll(nameRow, barBg, metaRow);
            item.setStyle("-fx-padding: 10 0 10 0; -fx-cursor: hand;");
            item.setOnMouseClicked(e -> openProjectDetail(project));
            panel.getChildren().add(item);
        }
        return panel;
    }

    /**
     * Construye la columna de tareas próximas de la vista home, mostrando
     * las 6 tareas pendientes más cercanas a su fecha límite ordenadas
     * por fecha ascendente.
     *
     * @param home Nodo JSON con todos los datos de la vista home.
     * @return {@link VBox} con el panel de tareas próximas.
     */
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
        panel.getChildren().add(createPanelHeader(lm.get("home.upcoming.tasks"),
                allTasks.size() + " " + (allTasks.size() != 1
                        ? lm.get("home.pending.tasks.plural").replace("{0}", "").trim()
                        : lm.get("home.pending.tasks").replace("{0}", "").trim())));

        if (upcoming.isEmpty()) {
            Label empty = new Label(lm.get("home.all.done"));
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
                        dueLbl = lm.get("date.overdue");
                        isUrgentDate = true;
                        isOverdue = true;
                    }
                    else if (due.equals(today))                  { dueLbl = lm.get("common.date.today");    isUrgentDate = true; }
                    else if (due.equals(today.plusDays(1))) { dueLbl = lm.get("date.tomorrow"); }
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
                    Platform.runLater(() -> showAlert("error.title", "error.update.task"));
                }
            }).start());

            Label titleLabel = new Label(title);
            titleLabel.getStyleClass().add("profile-field-value");

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
            row.setOnMouseClicked(e -> {
                if (e.getTarget() != check) {
                    openTaskDetail(task);
                }
            });
            row.setStyle("-fx-padding: 9 0 9 0; -fx-cursor: hand;");
            panel.getChildren().add(row);
        }
        return panel;
    }

    // ── Lista de tareas ───────────────────────────────────────────────────────

    /**
     * Renderiza la lista de tareas en el área principal y guarda los datos
     * originales para que los filtros y el orden puedan operar sobre ellos.
     *
     * @param tasks Array JSON con las tareas a mostrar.
     */
    private void renderTasks(JsonNode tasks) {
        taskContainer.getChildren().clear();
        taskContainer.getChildren().add(emptyLabel);
        if (!tasks.isArray() || tasks.isEmpty()) {
            emptyLabel.setText(lm.get("tasks.empty.current"));
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

    /**
     * Aplica los filtros de estado y prioridad y el criterio de orden
     * seleccionados sobre los datos originales y vuelve a renderizar la lista.
     */
    private void applyFiltersAndSort() {
        String statusVal   = statusFilter.getValue();
        String priorityVal = priorityFilter.getValue();
        String sortCrit    = sortFilter.getValue();

        // 1. Filtrar desde los datos originales
        List<JsonNode> result = currentTasks.stream()
                .filter(t -> {
                    if (statusVal == null || statusVal.equals(lm.get("common.all"))) return true;
                    return matchesStatusLabel(t.get("status").asText(), statusVal);
                })
                .filter(t -> {
                    if (priorityVal == null || priorityVal.equals(lm.get("common.all"))) return true;
                    return matchesPriorityLabel(t.get("priority").asText(), priorityVal);
                })
                .collect(java.util.stream.Collectors.toList());

        // 2. Ordenar
        if (sortCrit != null) {
            result.sort((a, b) -> {
                int cmp = 0;
                if (sortCrit.equals(lm.get("sort.title"))) {
                    cmp = a.get("title").asText().compareToIgnoreCase(b.get("title").asText());
                } else if (sortCrit.equals(lm.get("id"))) {
                    cmp = Long.compare(a.get("id").asLong(), b.get("id").asLong());
                } else if (sortCrit.equals(lm.get("common.duedate"))) {
                    boolean aH = a.has("dueDate") && !a.get("dueDate").isNull();
                    boolean bH = b.has("dueDate") && !b.get("dueDate").isNull();
                    if (!aH && !bH)     cmp = 0;
                    else if (!aH)       cmp = 1;
                    else if (!bH)       cmp = -1;
                    else cmp = a.get("dueDate").asText().compareTo(b.get("dueDate").asText());
                } else if (sortCrit.equals(lm.get("common.priority"))) {
                    cmp = Integer.compare(
                            priorityOrder(a.has("priority") ? a.get("priority").asText() : "MEDIUM"),
                            priorityOrder(b.has("priority") ? b.get("priority").asText() : "MEDIUM")
                    );
                }
                return sortAscending ? cmp : -cmp;
            });
        }

        // 3. Renderizar
        taskContainer.getChildren().clear();
        taskContainer.getChildren().add(emptyLabel);
        if (result.isEmpty()) {
            emptyLabel.setText(lm.get("tasks.empty.filter"));
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

    /**
     * Comprueba si el código de estado de una tarea coincide con la
     * etiqueta localizada seleccionada en el filtro.
     *
     * @param enumVal Código de estado (p.ej. {@code "IN_PROGRESS"}).
     * @param label   Etiqueta localizada del filtro.
     * @return {@code true} si coinciden.
     */
    private boolean matchesStatusLabel(String enumVal, String label) {
        return switch (enumVal) {
            case "TODO"        -> label.equals(lm.get("status.todo"));
            case "IN_PROGRESS" -> label.equals(lm.get("status.inprogress"));
            case "DONE"        -> label.equals(lm.get("status.done"));
            case "CANCELLED"   -> label.equals(lm.get("status.cancelled"));
            default            -> false;
        };
    }

    /**
     * Comprueba si el código de prioridad de una tarea coincide con la
     * etiqueta localizada seleccionada en el filtro.
     *
     * @param enumVal Código de prioridad (p.ej. {@code "HIGH"}).
     * @param label   Etiqueta localizada del filtro.
     * @return {@code true} si coinciden.
     */
    private boolean matchesPriorityLabel(String enumVal, String label) {
        return switch (enumVal) {
            case "LOW"    -> label.equals(lm.get("priority.low"));
            case "MEDIUM" -> label.equals(lm.get("priority.medium"));
            case "HIGH"   -> label.equals(lm.get("priority.high"));
            case "URGENT" -> label.equals(lm.get("priority.urgent"));
            default       -> false;
        };
    }

    /**
     * Restablece todos los filtros y el orden a sus valores por defecto
     * y vuelve a renderizar la lista de tareas sin filtros.
     */
    @FXML
    private void handleClearFilters() {
        resetComboBox(statusFilter,   lm.get("common.status"));
        resetComboBox(priorityFilter, lm.get("common.priority"));
        resetComboBox(sortFilter,     lm.get("sort.criteria"));
        sortAscending = true;
        sortDirectionIcon.setIconLiteral("fas-arrow-up");
        applyFiltersAndSort();
    }

    /**
     * Construye la tarjeta visual de una tarea con checkbox de estado,
     * identificador, título, badges de estado y prioridad, indicador de
     * vencimiento y menú de acciones (editar/eliminar).
     *
     * @param task Nodo JSON con los datos de la tarea.
     * @return {@link HBox} con el contenido visual de la tarjeta.
     */
    private HBox createTaskCard(JsonNode task) {
        HBox card = new HBox(12);
        card.setAlignment(Pos.CENTER_LEFT);

        card.getStyleClass().add("task-card");

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

        titleLabel.setOnMouseClicked(e -> openTaskDetail(task));
        titleLabel.getStyleClass().add("task-title");

        Label statusBadge = new Label(translateStatus(status));
        statusBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getStatusColor(status) + ";");

        Label priorityBadge = new Label(translatePriority(priority));
        priorityBadge.setStyle("-fx-font-size: 11px; -fx-padding: 2 8 2 8; " +
                "-fx-background-radius: 10px; -fx-text-fill: white; " +
                "-fx-background-color: " + getPriorityColor(priority) + ";");

        Label idLabel = new Label("#" + taskId);
        idLabel.getStyleClass().add("task-id-label");

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
                            showAlert("error.title", "error.update.status");
                        }
                    });
                } catch (Exception e) {
                    Platform.runLater(() -> checkBox.setSelected(was));
                }
            }).start();
        });

        Button menuBtn = MenuButtonFactory.createEditDeleteMenu(
                lm.get("common.menu.edit"),
                lm.get("common.menu.delete"),
                () -> handleEditTask(taskId, task),
                () -> handleDeleteTask(taskId)
        );

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
            card.getStyleClass().removeAll("task-card");
            card.getStyleClass().add("task-card-overdue");
            Label overdueLabel = new Label(lm.get("date.overdue"));
            overdueLabel.setStyle("-fx-font-size: 10px; -fx-padding: 2 7 2 7; " +
                    "-fx-background-radius: 10px; -fx-text-fill: #991b1b; " +
                    "-fx-background-color: #fee2e2;");
            card.getChildren().addAll(checkBox, idLabel, titleLabel, overdueLabel,
                    statusBadge, priorityBadge, spacer, menuBtn);
        } else {
            card.getChildren().addAll(checkBox, idLabel, titleLabel,
                    statusBadge, priorityBadge, spacer, menuBtn);
        }
        return card;
    }

    /**
     * Abre la vista de detalle de un proyecto en el área principal,
     * configurando los callbacks de cierre, actualización y apertura de tareas.
     *
     * @param project Nodo JSON con los datos del proyecto a mostrar.
     */
    private void openProjectDetail(JsonNode project) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/project-detail-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            root.setUserData("detail");
            HBox.setHgrow(root, Priority.ALWAYS);
            root.setMaxWidth(Double.MAX_VALUE);
            root.setMaxHeight(Double.MAX_VALUE);
            ProjectDetailController controller = loader.getController();
            controller.initData(project);
            controller.setOnClose(this::navigateBack);
            activeProjectDetailController = controller;

            controller.setOnProjectUpdated(() -> {
                loadProjects();
                reloadTasks();
                new Thread(() -> {
                    try {
                        HttpResponse<String> r = AppContext.getInstance()
                                .getApiService().get("/api/projects/" + project.get("id").asLong());
                        if (r.statusCode() == 200) {
                            JsonNode updated = objectMapper.readTree(r.body());
                            Platform.runLater(() -> activeProjectDetailController.initData(updated));
                        }
                    } catch (Exception ignored) {}
                }).start();
            });

            controller.setOnOpenTaskDetail(task -> {
                navigationStack.push(() -> openProjectDetail(project));
                openTaskDetail(task);
            });

            controller.setOnClose(() -> {
                activeProjectDetailController = null;
                navigateBack();
            });

            navigationStack.clear();
            swapMainAreaWith(root);
        } catch (IOException e) {
            showAlert("error.title", "error.open.project.detail");
        }
    }

    /**
     * Abre la vista de detalle de una tarea en el área principal,
     * configurando los callbacks de cierre y apertura de subtareas.
     *
     * @param task Nodo JSON con los datos de la tarea a mostrar.
     */
    private void openTaskDetail(JsonNode task) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/task-detail-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            root.setUserData("detail");
            HBox.setHgrow(root, Priority.ALWAYS);
            root.setMaxWidth(Double.MAX_VALUE);
            root.setMaxHeight(Double.MAX_VALUE);
            TaskDetailController controller = loader.getController();
            controller.initData(task);
            controller.setOnTaskChanged(() -> {});
            controller.setOnClose(this::navigateBack);
            controller.setOnOpenSubtaskDetail(subtask -> {
                navigationStack.push(() -> openTaskDetail(task));
                openSubtaskDetail(subtask);
            });
            swapMainAreaWith(root);
        } catch (IOException e) {
            showAlert("error.title", "error.open.task.detail");
        }
    }

    /**
     * Abre la vista de detalle de una subtarea en el área principal,
     * configurando el callback de cierre.
     *
     * @param subtask Nodo JSON con los datos de la subtarea a mostrar.
     */
    private void openSubtaskDetail(JsonNode subtask) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/task-detail-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            root.setUserData("detail");
            HBox.setHgrow(root, Priority.ALWAYS);
            root.setMaxWidth(Double.MAX_VALUE);
            root.setMaxHeight(Double.MAX_VALUE);
            TaskDetailController controller = loader.getController();
            controller.initDataAsSubtask(subtask);
            controller.setOnClose(this::navigateBack);
            swapMainAreaWith(root);
        } catch (IOException e) {
            showAlert("error.title", "error.open.subtask.detail");
        }
    }

    /**
     * Actualiza el estilo CSS del título de una tarjeta de tarea
     * según si está completada o no.
     *
     * @param l    Etiqueta de título a actualizar.
     * @param done {@code true} si la tarea está completada.
     */
    private void updateTitleStyle(Label l, boolean done) {
        l.getStyleClass().removeAll("task-title", "task-title-done");
        l.getStyleClass().add(done ? "task-title-done" : "task-title");
    }

    // ── Navegación ────────────────────────────────────────────────────────────

    /**
     * Navega a la vista home, limpia el estado de selección y oculta los filtros.
     */
    @FXML
    private void handleGoHome() {
        selectedProjectId = null;
        selectedCategory  = null;
        viewingAllTasks = false;
        activeProjectDetailController = null;
        areaTitle.setText(LanguageManager.getInstance().get("sidebar.home"));
        removeOverlayPanels();
        showMainArea();
        hideFilters();
        setSidebarActive(btnHome);
        loadHome();
    }

    /**
     * Navega a la vista de todas las tareas personales del usuario y muestra los filtros.
     */
    @FXML
    private void handleAllTasks() {
        activeProjectDetailController = null;
        removeOverlayPanels();
        showMainArea();
        selectedProjectId = null;
        selectedCategory  = null;
        viewingAllTasks   = true;
        areaTitle.setText(LanguageManager.getInstance().get("sidebar.all.tasks"));
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
                Platform.runLater(() -> showAlert("error.title", "error.load.tasks"));
            }
        }).start();
    }

    /**
     * Navega a la vista de tareas de la categoría Personal.
     */
    @FXML private void handleCategoryPersonal() {
        activeProjectDetailController = null;
        viewingAllTasks = false;
        removeOverlayPanels();
        showMainArea();
        loadTasksByCategory("PERSONAL", LanguageManager.getInstance().get("sidebar.personal"));
        setSidebarActive(btnPersonal);
    }

    /**
     * Navega a la vista de tareas de la categoría Estudios.
     */
    @FXML private void handleCategoryEstudios() {
        activeProjectDetailController = null;
        viewingAllTasks = false;
        removeOverlayPanels();
        showMainArea();
        loadTasksByCategory("ESTUDIOS", LanguageManager.getInstance().get("sidebar.estudios"));
        setSidebarActive(btnEstudios);
    }

    /**
     * Navega a la vista de tareas de la categoría Trabajo.
     */
    @FXML private void handleCategoryTrabajo()  {
        activeProjectDetailController = null;
        viewingAllTasks = false;
        removeOverlayPanels();
        showMainArea();
        loadTasksByCategory("TRABAJO",  LanguageManager.getInstance().get("sidebar.trabajo"));
        setSidebarActive(btnTrabajo);
    }

    /**
     * Carga las tareas de la categoría indicada desde el backend y las
     * renderiza en el área principal con los filtros visibles.
     *
     * @param category Código de categoría ({@code "PERSONAL"}, {@code "ESTUDIOS"} o {@code "TRABAJO"}).
     * @param title    Título a mostrar en la cabecera del área principal.
     */
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
                Platform.runLater(() -> showAlert("error.title", "error.load.tasks"));
            }
        }).start();
    }

    /**
     * Carga las tareas del proyecto indicado desde el backend y las
     * renderiza en el área principal con los filtros visibles.
     *
     * @param projectId Identificador del proyecto.
     */
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
                Platform.runLater(() -> showAlert("error.title", "error.load.tasks"));
            }
        }).start();
    }

    // ── Filtros ───────────────────────────────────────────────────────────────

    /**
     * Aplica el filtro de estado seleccionado sobre la lista de tareas actual.
     * Si hay un proyecto activo, consulta el backend; si no, filtra las tarjetas
     * ya renderizadas en el contenedor.
     */
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
                    Platform.runLater(() -> showAlert("error.title", "error.filter.tasks"));
                }
            }).start();
        } else filterTaskCardsByStatus(selected);
    }

    /**
     * Aplica el filtro de prioridad seleccionado sobre la lista de tareas actual.
     * Si hay un proyecto activo, consulta el backend; si no, filtra las tarjetas
     * ya renderizadas en el contenedor.
     */
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
                    Platform.runLater(() -> showAlert("error.title", "error.filter.tasks"));
                }
            }).start();
        } else filterTaskCardsByPriority(selected);
    }

    /**
     * Filtra las tarjetas visibles del contenedor de tareas según el estado indicado.
     *
     * @param status Código de estado a mostrar.
     */
    private void filterTaskCardsByStatus(String status) {
        taskContainer.getChildren().forEach(node -> {
            if (node instanceof VBox wrapper && !wrapper.getChildren().isEmpty()
                    && wrapper.getChildren().getFirst() instanceof HBox card) {
                boolean match = status.equals(card.getProperties().get("status"));
                wrapper.setVisible(match); wrapper.setManaged(match);
            }
        });
    }

    /**
     * Filtra las tarjetas visibles del contenedor de tareas según la prioridad indicada.
     *
     * @param priority Código de prioridad a mostrar.
     */
    private void filterTaskCardsByPriority(String priority) {
        taskContainer.getChildren().forEach(node -> {
            if (node instanceof VBox wrapper && !wrapper.getChildren().isEmpty()
                    && wrapper.getChildren().getFirst() instanceof HBox card) {
                boolean match = priority.equals(card.getProperties().get("priority"));
                wrapper.setVisible(match); wrapper.setManaged(match);
            }
        });
    }

    /**
     * Ordena las tarjetas del contenedor según el criterio seleccionado en el combo
     * de orden (título, ID, fecha límite o prioridad).
     */
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

    /**
     * Alterna la dirección de orden (ascendente/descendente) y vuelve a
     * aplicar los filtros y el orden actuales.
     */
    @FXML
    private void handleSortDirection() {
        sortAscending = !sortAscending;
        // Actualizar texto del botón (necesitas el @FXML ref)
        sortDirectionIcon.setIconLiteral(sortAscending ? "fas-arrow-up" : "fas-arrow-down");
        applyFiltersAndSort();
    }

    /**
     * Devuelve el valor numérico de orden de una prioridad para la ordenación,
     * donde URGENT=0 (mayor prioridad primero en orden ascendente).
     *
     * @param p Código de prioridad.
     * @return Valor numérico de orden.
     */
    private int priorityOrder(String p) {
        return switch (p) {
            case "URGENT" -> 0;
            case "HIGH"   -> 1;
            case "MEDIUM" -> 2;
            case "LOW"    -> 3;
            default       -> 2;
        };
    }

    /**
     * Renderiza una lista de tareas ya filtrada en el contenedor principal.
     *
     * @param tasks Lista de nodos JSON de tareas a mostrar.
     */
    private void renderFilteredTasks(List<JsonNode> tasks) {
        taskContainer.getChildren().clear();
        taskContainer.getChildren().add(emptyLabel);
        if (tasks.isEmpty()) {
            emptyLabel.setText(lm.get("tasks.empty.filter"));
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

    /**
     * Extrae el texto del título de una tarjeta de tarea buscando el
     * {@link Label} con {@code HGrow=ALWAYS}.
     *
     * @param card Tarjeta de tarea.
     * @return Texto del título, o cadena vacía si no se encuentra.
     */
    private String getCardTitle(HBox card) {
        // El título es el Label con HGrow=ALWAYS (segundo hijo tras el checkbox)
        return card.getChildren().stream()
                .filter(n -> n instanceof Label && HBox.getHgrow(n) == Priority.ALWAYS)
                .map(n -> ((Label) n).getText())
                .findFirst().orElse("");
    }

    /**
     * Extrae el identificador de tarea almacenado en las propiedades de la tarjeta.
     *
     * @param card Tarjeta de tarea.
     * @return Identificador de la tarea, o {@code 0} si no está disponible.
     */
    private long getCardId(HBox card) {
        Object id = card.getProperties().get("taskId");
        return id instanceof Long l ? l : 0L;
    }

    // ── Overlays ──────────────────────────────────────────────────────────────

    /**
     * Navega a la vista de ajustes y la muestra en el área principal.
     */
    @FXML
    private void handleSettings() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/settings-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox settingsView = loader.load();
            HBox.setHgrow(settingsView, Priority.ALWAYS);
            settingsView.setUserData("settings");
            setSidebarActive(btnSettings);
            swapMainAreaWith(settingsView);
        } catch (Exception e) {
            showAlert("error.title", "error.open.settings");
        }
    }

    /**
     * Navega a la vista de seguridad y la muestra en el área principal.
     */
    @FXML
    private void handleSecurity() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/security-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox securityView = loader.load();
            HBox.setHgrow(securityView, Priority.ALWAYS);
            securityView.setUserData("settings");
            setSidebarActive(btnSecurity);
            swapMainAreaWith(securityView);
        } catch (Exception e) {
            showAlert("error.title", "error.open.settings");
        }
    }

    /**
     * Navega a la vista de papelera, la muestra en el área principal y
     * registra el callback para recargar proyectos y tareas al restaurar elementos.
     */
    @FXML
    private void handleTrash() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/trash-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox trashView = loader.load();
            HBox.setHgrow(trashView, Priority.ALWAYS);
            trashView.setUserData("trash");
            TrashController controller = loader.getController();
            trashController = controller;
            controller.setOnTrashChanged(() -> { loadProjects(); reloadTasks(); });
            setSidebarActive(btnTrash);
            swapMainAreaWith(trashView);
        } catch (IOException e) {
            showAlert("error.title", "error.open.trash");
        }
    }

    /**
     * Muestra el menú contextual de ayuda con las opciones "Manual de usuario"
     * y "Acerca de TaskMaster".
     */
    @FXML
    private void handleHelp() {
        ContextMenu menu = new ContextMenu();

        MenuItem manualItem = new MenuItem(lm.get("help.manual"));
        manualItem.setGraphic(new FontIcon("fas-book"));
        manualItem.setOnAction(e -> showAlert("help.soon.title", "help.manual.soon"));

        MenuItem aboutItem = new MenuItem(lm.get("help.about"));
        aboutItem.setGraphic(new FontIcon("fas-info-circle"));
        aboutItem.setOnAction(e -> showAboutView());

        menu.getItems().addAll(manualItem, new SeparatorMenuItem(), aboutItem);
        menu.show(btnHelp, javafx.geometry.Side.RIGHT, 4, 0);
    }

    /**
     * Navega a la vista "Acerca de TaskMaster" y la muestra en el área principal.
     */
    private void showAboutView() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource(
                            "/com/taskmaster/taskmasterfrontend/about-view.fxml"),
                    lm.getBundle()
            );
            VBox view = loader.load();
            HBox.setHgrow(view, Priority.ALWAYS);
            view.setUserData("settings"); // reutilizamos "settings" para que swapMainAreaWith lo limpie
            clearSidebarSelection();
            swapMainAreaWith(view);
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("error.title", "error.open.dialog");
        }
    }

    /**
     * Oculta el área principal, elimina cualquier overlay activo y añade
     * el nuevo overlay al {@link HBox} central del layout.
     *
     * @param overlay Nodo a mostrar como overlay en el área principal.
     */
    private void swapMainAreaWith(javafx.scene.Node overlay) {
        mainArea.setVisible(false);
        mainArea.setManaged(false);
        HBox centerHBox = getCenterHBox();
        centerHBox.getChildren().removeIf(n -> {
            Object ud = n.getUserData();
            return "trash".equals(ud) || "settings".equals(ud)
                    || "profile".equals(ud) || "detail".equals(ud);
        });
        centerHBox.getChildren().add(overlay);
    }

    /**
     * Hace visible el área principal.
     */
    private void showMainArea() {
        mainArea.setVisible(true);
        mainArea.setManaged(true);
    }

    /**
     * Elimina todos los overlays activos (papelera, ajustes, perfil, detalle)
     * del {@link HBox} central y muestra el área principal.
     */
    private void removeOverlayPanels() {
        getCenterHBox().getChildren().removeIf(n -> {
            Object ud = n.getUserData();
            return "trash".equals(ud) || "settings".equals(ud) || "profile".equals(ud) || "detail".equals(ud);
        });
        showMainArea();
    }

    /**
     * Obtiene el {@link HBox} central del {@link javafx.scene.layout.BorderPane} raíz.
     *
     * @return El contenedor central de la escena principal.
     */
    private HBox getCenterHBox() {
        javafx.scene.layout.BorderPane root =
                (javafx.scene.layout.BorderPane) btnHome.getScene().getRoot();
        return (HBox) root.getCenter();
    }

    private static final String SIDEBAR_ACTIVE   = "sidebar-btn-active";
    private static final String SIDEBAR_INACTIVE = "sidebar-btn";

    /**
     * Marca el botón del sidebar indicado como activo y desactiva el resto.
     *
     * @param active Botón del sidebar a marcar como activo.
     */
    private void setSidebarActive(Button active) {
        clearSidebarSelection();
        active.getStyleClass().removeAll("sidebar-btn");
        active.getStyleClass().add("sidebar-btn-active");
    }

    /**
     * Marca la fila del sidebar correspondiente al proyecto indicado como activa
     * y desactiva el resto de botones del sidebar y filas de proyectos.
     *
     * @param projectId Identificador del proyecto activo.
     */
    private void setSidebarProjectActive(Long projectId) {
        for (Button btn : new Button[]{btnHome, btnAllTasks, btnPersonal,
                btnEstudios, btnTrabajo, btnSettings, btnSecurity, btnTrash}) {
            btn.getStyleClass().removeAll("sidebar-btn-active");
            if (!btn.getStyleClass().contains("sidebar-btn"))
                btn.getStyleClass().add("sidebar-btn");
        }
        for (Node node : projectListContainer.getChildren()) {
            if (node instanceof HBox row) {
                if (row.getChildren().size() >= 2 && row.getChildren().get(1) instanceof Button btn) {
                    Object tag = row.getUserData();
                    boolean isActive = tag instanceof Long pid && pid.equals(projectId);
                    btn.getStyleClass().removeAll(
                            "sidebar-project-btn", "sidebar-project-btn-hover", "sidebar-project-btn-active");
                    btn.getStyleClass().add(isActive
                            ? "sidebar-project-btn-active"
                            : "sidebar-project-btn");
                }
                if (row.getChildren().size() >= 3 && row.getChildren().get(2) instanceof Button dots) {
                    dots.getStyleClass().removeAll("sidebar-project-dots-visible");
                    if (!dots.getStyleClass().contains("sidebar-project-dots"))
                        dots.getStyleClass().add("sidebar-project-dots");
                }
            }
        }
    }

    /**
     * Muestra la barra de filtros y el campo de búsqueda,
     * y actualiza el texto del botón de creación a "Nueva tarea".
     */
    private void showFilters() {
        resetComboBox(statusFilter,   lm.get("common.status"));
        resetComboBox(priorityFilter, lm.get("common.priority"));
        resetComboBox(sortFilter,     lm.get("sort.criteria"));
        sortAscending = true;
        sortDirectionIcon.setIconLiteral("fas-arrow-up");
        taskFiltersBar.setVisible(true);
        taskFiltersBar.setManaged(true);
        showSearch();
        createButton.setText(LanguageManager.getInstance().get("topbar.create.task"));
        Node chevron = createButton.lookup(".btn-create-chevron");
        if (chevron != null) {
            chevron.setVisible(false);
            chevron.setManaged(false);
        }
        Label createLabel = (Label) createButton.lookup(".btn-create-label");
        if (createLabel != null) {
            createLabel.setText(LanguageManager.getInstance().get("topbar.create.task"));
        }
    }

    /**
     * Restablece un {@link ComboBox} a su estado sin selección,
     * mostrando el texto de placeholder indicado.
     *
     * @param combo      ComboBox a restablecer.
     * @param promptText Texto de placeholder a mostrar.
     */
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

    /**
     * Oculta la barra de filtros y el campo de búsqueda,
     * y restablece el texto del botón de creación.
     */
    private void hideFilters() {
        taskFiltersBar.setVisible(false);
        taskFiltersBar.setManaged(false);
        hideSearch();
        createButton.setText(lm.get("topbar.create"));
        Node chevron = createButton.lookup(".btn-create-chevron");
        if (chevron != null) {
            chevron.setVisible(true);
            chevron.setManaged(true);
        }
        Label createLabel = (Label) createButton.lookup(".btn-create-label");
        if (createLabel != null) {
            createLabel.setText(lm.get("topbar.create"));
        }
    }

    /**
     * Hace visible el campo de búsqueda y lo limpia.
     */
    private void showSearch() {
        searchContainer.setVisible(true);
        searchContainer.setManaged(true);
        searchField.clear();
    }

    /**
     * Oculta el campo de búsqueda y lo limpia.
     */
    private void hideSearch() {
        searchContainer.setVisible(false);
        searchContainer.setManaged(true);
        searchField.clear();
    }

    /**
     * Filtra las tarjetas visibles del contenedor de tareas en tiempo real
     * según el texto introducido en el campo de búsqueda, buscando por
     * título y por identificador numérico de la tarea.
     */
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

    // ── Diálogos ──────────────────────────────────────────────────────────────

    /**
     * Abre el diálogo modal de creación de nuevo proyecto y recarga
     * el sidebar y las tareas al crearlo.
     */
    @FXML
    private void handleNewProject() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-project-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            NewProjectController controller = loader.getController();

            controller.setOnProjectCreated(() -> { loadProjects(); reloadTasks(); });
            showAsDialog(root, lm.get("new.project.title"));
        } catch (IOException e) {
            showAlert("error.title", "error.open.dialog");
        }
    }

    /**
     * Abre el diálogo modal de creación de nueva tarea, preseleccionando
     * el proyecto o la categoría activos, y recarga las tareas al crearla.
     */
    @FXML
    private void handleNewTask() {
        final Long currentProjectId = selectedProjectId;
        final String currentCategory = selectedCategory;
        final String currentTitle = areaTitle.getText();

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/new-task-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            NewTaskController controller = loader.getController();

            controller.initData(selectedProjectId);
            if (currentCategory != null) {
                controller.setPreSelectedCategory(currentCategory);
            }

            controller.setOnTaskCreated(this::reloadTasks);
            showAsDialog(root, lm.get("new.task.title"));
        } catch (IOException e) {
            showAlert("error.title", "error.open.dialog");
        }
    }

    /**
     * Abre el diálogo modal de edición de un proyecto y, al guardarlo,
     * recarga el sidebar y actualiza la vista de detalle si está activa.
     *
     * @param projectId   Identificador del proyecto a editar.
     * @param projectName Nombre actual del proyecto.
     */
    private void handleEditProject(Long projectId, String projectName) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-project-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            EditProjectController controller = loader.getController();
            controller.initData(projectId, projectName);
            controller.setOnProjectUpdated(() -> {
                loadProjects();
                if (activeProjectDetailController != null) {
                    new Thread(() -> {
                        try {
                            HttpResponse<String> r = AppContext.getInstance()
                                    .getApiService().get("/api/projects/" + projectId);
                            if (r.statusCode() == 200) {
                                JsonNode updated = objectMapper.readTree(r.body());
                                Platform.runLater(() -> activeProjectDetailController.initData(updated));
                            }
                        } catch (Exception ignored) {}
                    }).start();
                } else {
                    reloadTasks();
                }
            });
            showAsDialog(root, lm.get("edit.project.title"));
        } catch (IOException e) {
            showAlert("error.title", "error.open.dialog");
        }
    }

    /**
     * Muestra un diálogo de confirmación y, si el usuario acepta, elimina
     * el proyecto del backend, recarga el sidebar y navega al home si
     * el proyecto eliminado era el activo.
     *
     * @param projectId   Identificador del proyecto a eliminar.
     * @param projectName Nombre del proyecto, usado en el mensaje de confirmación.
     */
    private void handleDeleteProject(Long projectId, String projectName) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("confirm.delete.title.project"));
        confirm.setHeaderText(null);
        confirm.setContentText(
                java.text.MessageFormat.format(lm.get("confirm.delete.project"), projectName));
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
                            } else showAlert("error.title", "error.delete.project");
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert("error.title", "error.connection"));
                    }
                }).start();
            }
        });
    }

    /**
     * Abre el diálogo modal de edición de una tarea y recarga la lista al guardar.
     *
     * @param taskId Identificador de la tarea a editar.
     * @param task   Nodo JSON con los datos actuales de la tarea.
     */
    private void handleEditTask(Long taskId, JsonNode task) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/edit-task-dialog.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            VBox root = loader.load();
            EditTaskController controller = loader.getController();
            controller.initData(task);
            controller.setOnTaskUpdated(this::reloadTasks);
            Stage dialog = new Stage();
            dialog.setTitle(lm.get("common.task.edit"));
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(btnHome.getScene().getWindow());
            Scene scene = new Scene(root);
            applyThemeToScene(scene);
            dialog.setScene(scene);
            dialog.showAndWait();
        } catch (IOException e) {
            showAlert("error.title", "error.open.dialog");
        }
    }

    /**
     * Muestra un diálogo de confirmación y, si el usuario acepta, elimina
     * la tarea del backend y recarga la vista activa.
     *
     * @param taskId Identificador de la tarea a eliminar.
     */
    private void handleDeleteTask(Long taskId) {
        final Long   currentProjectId = selectedProjectId;
        final String currentCategory  = selectedCategory;
        final String currentTitle     = areaTitle.getText();
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle(lm.get("common.delete.task.title"));
        confirm.setHeaderText(null);
        confirm.setContentText(lm.get("confirm.delete.task"));
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
                                } else {
                                reloadTasks();
                                }
                                if (trashController != null) trashController.refresh();
                            } else showAlert("error.title", "error.delete.task");
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert("error.title", "error.connection"));
                    }
                }).start();
            }
        });
    }

    /**
     * Registra el logout en el backend, limpia la sesión en {@link AppContext}
     * y navega a la pantalla de login.
     */
    @FXML
    private void handleLogout() {
        try {
            // Llamada síncrona para asegurar que el log se registra antes de limpiar credenciales
            AppContext.getInstance().getApiService().postWithAuth("/api/auth/logout", "");
        } catch (Exception ignored) {}

        AppContext.getInstance().logout();
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/taskmaster/taskmasterfrontend/login-view.fxml"),
                    LanguageManager.getInstance().getBundle()
            );
            Stage stage = (Stage) mainArea.getScene().getWindow();
            Scene scene = new Scene(loader.load(), 400, 520);
            scene.getStylesheets().add(getClass().getResource(
                            "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css")
                    .toExternalForm());
            stage.setScene(scene);
            stage.setMinWidth(400);
            stage.setMinHeight(520);
            stage.setMaximized(false);
            stage.setWidth(400);
            stage.setHeight(520);
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.setTitle("TaskMaster");
        } catch (IOException e) {
            showAlert("error.title", "error.logout");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Recarga la vista activa (proyecto, categoría, todas las tareas o home)
     * según el estado de navegación actual.
     */
    private void reloadTasks() {
        if (selectedProjectId != null) loadTasksForProject(selectedProjectId);
        else if (selectedCategory != null) loadTasksByCategory(selectedCategory, areaTitle.getText());
        else if (viewingAllTasks) handleAllTasks();
        else loadHome();
    }

    /**
     * Calcula las estadísticas globales de tareas del usuario a partir
     * de los datos de la vista home.
     *
     * @param home Nodo JSON con todos los datos de la vista home.
     * @return Array de enteros con {@code [pendientes, en progreso, completadas, proyectos activos]}.
     */
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

    /**
     * Calcula el número total de tareas pendientes (en estado TODO o IN_PROGRESS).
     *
     * @param home Nodo JSON con los datos de la vista home.
     * @return Número de tareas pendientes.
     */
    private int countPendingTasks(JsonNode home) {
        int[] s = computeStats(home); return s[0] + s[1];
    }

    /**
     * Crea un panel {@link VBox} con el estilo CSS {@code home-panel}.
     *
     * @return Panel vacío con el estilo aplicado.
     */
    private VBox createPanel() {
        VBox panel = new VBox(0);
        panel.getStyleClass().add("home-panel");
        return panel;
    }

    /**
     * Crea la cabecera de un panel con un título y un contador alineados.
     *
     * @param title Título del panel.
     * @param count Texto del contador a mostrar en el extremo derecho.
     * @return {@link HBox} con el contenido de la cabecera.
     */
    private HBox createPanelHeader(String title, String count) {
        HBox header = new HBox();
        header.setAlignment(Pos.CENTER_LEFT);
        header.setStyle("-fx-padding: 0 0 12 0;");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("home-panel-title");
        HBox.setHgrow(titleLabel, Priority.ALWAYS);

        Label countLabel = new Label(count);
        countLabel.getStyleClass().add("home-panel-count");

        header.getChildren().addAll(titleLabel, countLabel);
        return header;
    }

    /**
     * Elimina la selección activa de todos los botones del sidebar
     * y de todas las filas de proyectos.
     */
    private void clearSidebarSelection() {
        for (Button btn : new Button[]{btnHome, btnAllTasks, btnPersonal,
                btnEstudios, btnTrabajo, btnSettings, btnSecurity, btnTrash, btnHelp}) {
            btn.getStyleClass().removeAll("sidebar-btn-active");
            if (!btn.getStyleClass().contains("sidebar-btn"))
                btn.getStyleClass().add("sidebar-btn");
        }
        for (Node node : projectListContainer.getChildren()) {
            if (node instanceof HBox row) {
                if (row.getChildren().size() >= 2 && row.getChildren().get(1) instanceof Button b) {
                    b.getStyleClass().removeAll("sidebar-project-btn-active");
                    if (!b.getStyleClass().contains("sidebar-project-btn"))
                        b.getStyleClass().add("sidebar-project-btn");
                }
                if (row.getChildren().size() >= 3 && row.getChildren().get(2) instanceof Button dots) {
                    dots.setStyle("-fx-background-color: transparent; -fx-text-fill: transparent; " +
                            "-fx-font-size: 10px; -fx-font-weight: bold; -fx-cursor: hand; " +
                            "-fx-padding: 2 6 2 6; -fx-background-radius: 6px;");
                }
            }
        }
    }

    /**
     * Navega hacia atrás en la pila de navegación. Si hay una vista anterior
     * registrada la restaura; si no, elimina los overlays y muestra el área principal.
     */
    private void navigateBack() {
        if (!navigationStack.isEmpty()) {
            navigationStack.pop().run();
        } else {
            removeOverlayPanels();
        }
    }

    /**
     * Crea un {@link Label} con el texto y el estilo CSS inline indicados.
     *
     * @param text  Texto del badge.
     * @param style Estilo CSS inline a aplicar.
     * @return {@link Label} configurado como badge.
     */
    private Label createBadge(String text, String style) {
        Label badge = new Label(text); badge.setStyle(style); return badge;
    }

    /**
     * Devuelve el color hex de acento asociado a una categoría.
     *
     * @param c Código de categoría.
     * @return Color en formato hex.
     */
    private String getCategoryColor(String c) {
        return switch (c) {
            case "PERSONAL" -> COLOR_PERSONAL;
            case "ESTUDIOS" -> COLOR_ESTUDIOS;
            case "TRABAJO"  -> COLOR_TRABAJO;
            default -> COLOR_PERSONAL;
        };
    }

    /**
     * Devuelve el color hex de acento para una fila del sidebar de proyectos,
     * rotando entre los colores disponibles según el índice.
     *
     * @param i Índice de la fila en el sidebar.
     * @return Color en formato hex.
     */
    private String getCategoryColorForIndex(int i) {
        String[] colors = {COLOR_PERSONAL, COLOR_ESTUDIOS, COLOR_TRABAJO, "#60a5fa", "#f472b6"};
        return colors[i % colors.length];
    }

    /**
     * Devuelve el estilo CSS inline del badge de categoría.
     *
     * @param c Código de categoría.
     * @return Cadena de estilo CSS con color de fondo y de texto.
     */
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

    /**
     * Devuelve el color hex asociado a un estado de tarea o proyecto.
     *
     * @param s Código de estado.
     * @return Color en formato hex.
     */
    private String getStatusColor(String s) {
        return switch (s) {
            case "TODO" -> "#95a5a6";
            case "IN_PROGRESS" -> "#3498db";
            case "DONE" -> "#2ecc71";
            case "SUBMITTED" -> "#8e44ad";
            case "CANCELLED"   -> "#e74c3c";
            default -> "#95a5a6";
        };
    }

    /**
     * Devuelve el color hex asociado a una prioridad de tarea o proyecto.
     *
     * @param p Código de prioridad.
     * @return Color en formato hex.
     */
    private String getPriorityColor(String p) {
        return switch (p) {
            case "URGENT" -> "#e74c3c";
            case "HIGH"   -> "#e67e22";
            case "MEDIUM" -> "#3498db";
            case "LOW"    -> "#95a5a6";
            default -> "#95a5a6";
        };
    }

    /**
     * Traduce un código de estado del backend a su etiqueta localizada.
     *
     * @param status Código de estado.
     * @return Etiqueta localizada correspondiente.
     */
    private String translateStatus(String status) {
        return switch (status) {
            case "TODO"        -> lm.get("status.TODO");
            case "IN_PROGRESS" -> lm.get("status.IN_PROGRESS");
            case "DONE"        -> lm.get("status.DONE");
            case "SUBMITTED"   -> lm.get("status.SUBMITTED");
            case "CANCELLED"   -> lm.get("status.CANCELLED");
            default            -> status;
        };
    }

    /**
     * Traduce un código de prioridad del backend a su etiqueta localizada.
     *
     * @param priority Código de prioridad.
     * @return Etiqueta localizada correspondiente.
     */
    private String translatePriority(String priority) {
        return switch (priority) {
            case "LOW"    -> lm.get("priority.LOW");
            case "MEDIUM" -> lm.get("priority.MEDIUM");
            case "HIGH"   -> lm.get("priority.HIGH");
            case "URGENT" -> lm.get("priority.URGENT");
            default       -> priority;
        };
    }

    /**
     * Muestra un diálogo de información con el título y mensaje obtenidos
     * de las claves de localización indicadas.
     *
     * @param titleKey   Clave de localización del título.
     * @param messageKey Clave de localización del mensaje.
     */
    private void showAlert(String titleKey, String messageKey) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(lm.get(titleKey));
        alert.setHeaderText(null);
        alert.setContentText(lm.get(messageKey));
        alert.showAndWait();
    }

    /**
     * Crea un diálogo modal con el contenido indicado, aplica el tema activo
     * y lo muestra de forma bloqueante.
     *
     * @param root  Contenido raíz a mostrar en el diálogo.
     * @param title Título de la ventana del diálogo.
     */
    private void showAsDialog(VBox root, String title) {
        Stage dialog = new Stage();
        dialog.setTitle(title);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(btnHome.getScene().getWindow());
        Scene scene = new Scene(root);
        applyThemeToScene(scene);
        dialog.setScene(scene);
        dialog.centerOnScreen();
        System.out.println("centrado");
        dialog.showAndWait();
    }

    /**
     * Aplica el tema activo del {@link ThemeManager} a la escena indicada,
     * cargando primero el CSS base y luego el tema seleccionado.
     *
     * @param scene Escena a la que aplicar el tema.
     */
    private void applyThemeToScene(Scene scene) {
        com.taskmaster.taskmasterfrontend.util.ThemeManager tm =
                com.taskmaster.taskmasterfrontend.util.ThemeManager.getInstance();
        // Cargar siempre el CSS base primero
        String baseUrl = getClass().getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css") != null
                ? getClass().getResource(
                "/com/taskmaster/taskmasterfrontend/themes/theme-amatista.css").toExternalForm()
                : null;
        if (baseUrl != null) scene.getStylesheets().add(baseUrl);
        // Luego el tema activo si no es Amatista
        String cssFile = "/com/taskmaster/taskmasterfrontend/themes/"
                + tm.getCssFileNamePublic();
        String themeUrl = getClass().getResource(cssFile) != null
                ? getClass().getResource(cssFile).toExternalForm()
                : null;
        if (themeUrl != null && !themeUrl.equals(baseUrl))
            scene.getStylesheets().add(themeUrl);
        // Fondo del Scene
        scene.setFill(javafx.scene.paint.Color.web(tm.getBgApp()));
    }

    /**
     * Actualiza los textos de todos los elementos del sidebar y de la barra
     * de filtros con las cadenas del idioma actualmente seleccionado.
     */
    private void refreshSidebar() {
        btnHome.setText(lm.get("sidebar.home"));
        btnAllTasks.setText(lm.get("sidebar.all.tasks"));
        btnPersonal.setText(lm.get("sidebar.personal"));
        btnEstudios.setText(lm.get("sidebar.estudios"));
        btnTrabajo.setText(lm.get("sidebar.trabajo"));
        btnSettings.setText(lm.get("sidebar.settings"));
        btnSecurity.setText(lm.get("sidebar.security"));
        btnTrash.setText(lm.get("sidebar.trash"));
        btnHelp.setText(lm.get("sidebar.help"));
        createButton.setText(lm.get("topbar.create"));
        searchField.setPromptText(lm.get("topbar.search.prompt"));
        statusFilter.setPromptText(lm.get("common.status"));
        priorityFilter.setPromptText(lm.get("common.priority"));
        sortFilter.setPromptText(lm.get("sort.criteria"));
        filterLabel.setText(lm.get("filter.label"));
        sortLabel.setText(lm.get("sort.label"));
        clearFiltersBtn.setText(lm.get("filter.clear"));

        String currentStatus   = statusFilter.getValue();
        String currentPriority = priorityFilter.getValue();
        String currentSort     = sortFilter.getValue();

        statusFilter.setItems(FXCollections.observableArrayList(
                lm.get("common.all"), lm.get("status.todo"), lm.get("status.inprogress"),
                lm.get("status.done"), lm.get("status.cancelled")));
        priorityFilter.setItems(FXCollections.observableArrayList(
                lm.get("common.all"), lm.get("priority.low"), lm.get("priority.medium"),
                lm.get("priority.high"), lm.get("priority.urgent")));
        sortFilter.setItems(FXCollections.observableArrayList(
                lm.get("sort.title"), lm.get("id"),
                lm.get("common.duedate"), lm.get("common.priority")));

        Label createLabel = (Label) createButton.lookup(".btn-create-label");
        if (createLabel != null) {
            boolean isHome = selectedProjectId == null && selectedCategory == null && !viewingAllTasks;
            createLabel.setText(lm.get(isHome ? "topbar.create" : "topbar.create.task"));
        }
    }
}