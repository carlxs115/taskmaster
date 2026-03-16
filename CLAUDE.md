# TaskMaster — Contexto del proyecto

## Descripción
App de escritorio: gestor de tareas (TFG DAM)
- Backend: Java + Spring Boot 3.5.0 + JPA/Hibernate + H2 (dev) / PostgreSQL (prod)
- Frontend: JavaFX 21.0.6 con FXML + JDK 25
- Arquitectura: MVC frontend, capas backend (Controller/Service/Repository/Model)

## Estructura local
```
taskmaster/
├── taskmaster-backend/     ← Spring Boot
└── taskmaster-frontend/    ← JavaFX
```

## Backend

### Entidades principales
- `User` — id, username, email, password (BCrypt), birthDate, createdAt, UserSettings
- `Project` — id, name, description, category, status, priority, user (ManyToOne), soft delete
- `Task` — id, title, description, status, priority, category, dueDate, user (ManyToOne), project (nullable), parentTask (self-ref), soft delete
- `UserSettings` — trashRetentionDays (default 30)

### Enums
- `TaskStatus`: TODO, IN_PROGRESS, DONE, CANCELLED
- `TaskPriority`: LOW, MEDIUM, HIGH, URGENT
- `TaskCategory`: PERSONAL, ESTUDIOS, TRABAJO

### Reglas de negocio clave
- No se puede marcar una tarea como DONE si tiene subtareas pendientes
- Soft delete en cascada: al eliminar una tarea, sus subtareas también van a la papelera
- Las tareas personales (project=null) se identifican por usuario via task.user
- El scheduler purga automáticamente tareas/proyectos expirados según trashRetentionDays

### Seguridad
- Basic Auth (STATELESS), CSRF desactivado
- /api/auth/** y /h2-console/** públicos, resto requiere autenticación

### Endpoints principales
```
POST   /api/auth/register
POST   /api/auth/login

GET    /api/projects
POST   /api/projects
PUT    /api/projects/{id}
DELETE /api/projects/{id}

GET    /api/tasks/home          ← devuelve proyectos+tareas+categorías para el home
GET    /api/tasks?projectId=
GET    /api/tasks/personal
GET    /api/tasks/category/{category}
GET    /api/tasks/trash
POST   /api/tasks
PATCH  /api/tasks/{id}/status

GET    /api/settings
PATCH  /api/settings/trash-retention?days=
```

## Frontend

### Flujo de navegación
- `MainApp` arranca con `login-view.fxml` (400x500)
- Login exitoso navega a `main-view.fxml` (900x600)
- Layout: BorderPane con topbar oscuro + HBox central (sidebar 220px + projectsPanel + taskPanel)

### AppContext (singleton)
- Guarda `currentUserId`, `currentUsername`, instancia de `ApiService`
- `ApiService` usa HttpClient con Basic Auth en Base64

### Paneles dinámicos
El HBox central tiene 3 hijos fijos (sidebar, projectsPanel, taskPanel).
Para papelera y ajustes se ocultan projectsPanel+taskPanel y se añade el nuevo panel con `userData="trash"` o `userData="settings"`.
Para volver al home se hace `removeIf` por userData y se restauran los paneles.

### Home (renderHome)
- Saludo + fecha en español
- 4 stat cards: pendientes, en progreso, completadas, proyectos activos
- Columna izquierda: proyectos con barra de progreso (DONE/total)
- Columna derecha: tareas próximas ordenadas por dueDate, máx 6

### Convenciones CSS en JavaFX
- NO usar rgba() — JavaFX no lo soporta en estilos inline
- Usar hex sólidos: #13131f (fondo oscuro), #2a2a3e (bordes), #9999bb (texto secundario), #a78bfa (acento morado)
- Colores categoría: PERSONAL=#a78bfa, ESTUDIOS=#34d399, TRABAJO=#fb923c

## Estado actual (último commit)
- ✅ Backend completo con autenticación, CRUD proyectos/tareas, papelera, scheduler
- ✅ Frontend: login, registro, home rediseñado, proyectos, tareas, papelera, ajustes
- ✅ Task.user añadido para aislar tareas personales por usuario

### Commits
1. `init: estructura inicial Spring Boot`
2. `feat: añadir entidades User, Project, Task y Tag`
3. `feat: añadir repositorios User, Project, Task y Tag`
4. `feat: añadir servicios User, Project y Task`
5. `feat: añadir papelera, birthDate, UserSettings y servicios actualizados`
6. `feat: añadir configuración de seguridad Spring Security`
7. `feat: añadir controladores AuthController, ProjectController y TaskController`
8. `feat: añadir manejo de excepciones global`
9. `fix: corregir anotación @PostMapping en register y actualizar application.properties`
10. `refactor: reorganizar estructura en taskmaster-backend y taskmaster-frontend`
11. `feat: añadir pantallas de login y registro JavaFX`
12. `feat: añadir ApiService y AppContext para conexión con backend`
13. `feat: añadir pantalla principal con sidebar de proyectos y panel de tareas`
14. `feat: añadir diálogo de nuevo proyecto y fix autenticación Basic Auth`
15. `feat: añadir diálogo de nueva tarea y fix selección de proyectos`
16. `feat: completar tareas con checkbox`
17. `feat: añadir categorías Personal, Estudios y Trabajo a tareas y proyectos`
18. `feat: mejorar sidebar con categorías, proyectos dinámicos y nuevo layout`
19. `feat: añadir editar y eliminar tareas`
20. `feat: añadir home con proyectos y tareas, editar/eliminar proyectos`
21. `feat: añadir papelera, ajustes de periodo de papelera y vaciado automático`
22. `feat: añadir panel de proyectos con estado/prioridad y filtros de tareas`
23. `feat: rediseño panel principal con proyectos/tareas separados, filtros y mejoras en el sidebar`
24. `feat: rediseño completo del home con stats, proyectos con progreso y tareas próximas`


## Pendientes
- [ ] Aplicar últimos cambios visuales del topbar y sidebar (hex en vez de rgba)
- [ ] Tema claro/oscuro en ajustes
- [ ] Función felicitación cumpleaños
- [ ] Estadísticas básicas