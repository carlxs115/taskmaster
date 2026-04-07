# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Descripción
App de escritorio: gestor de tareas (TFG DAM)
- Backend: Java 17 + Spring Boot 3.5.x + JPA/Hibernate + H2 (dev) / PostgreSQL (prod)
- Frontend: JavaFX 21.0.6 + JDK 25 + FXML + Jackson para JSON
- Arquitectura: MVC frontend desacoplado del backend via REST + Basic Auth

## Comandos

### Backend (taskmaster-backend/)
```bash
# Compilar
./mvnw compile

# Ejecutar (arranca en localhost:8080, H2 en memoria)
./mvnw spring-boot:run

# Tests
./mvnw test

# Test específico
./mvnw test -Dtest=NombreTest

# Empaquetar JAR
./mvnw package -DskipTests
```

### Frontend (taskmaster-frontend/)
```bash
# Compilar
./mvnw compile

# Ejecutar app JavaFX (requiere backend corriendo)
./mvnw javafx:run

# Tests
./mvnw test
```

> La consola H2 está disponible en http://localhost:8080/h2-console (JDBC URL: `jdbc:h2:mem:taskmasterdb`, usuario: `sa`, sin contraseña).

## Arquitectura

### Estructura de módulos
```
taskmaster/
├── taskmaster-backend/     ← Spring Boot (Java 17)
│   └── src/main/java/com/taskmaster/
│       ├── controller/     ← REST controllers
│       ├── service/        ← Lógica de negocio
│       ├── repository/     ← JPA repositories
│       ├── model/          ← Entidades JPA + Enums
│       ├── dto/            ← request/ y response/
│       ├── security/       ← SecurityConfig, SecurityUtils, UserDetailsServiceImpl
│       ├── exception/      ← GlobalExceptionHandler, ResourceNotFoundException
│       └── TrashScheduler  ← @Scheduled para purga automática de papelera
└── taskmaster-frontend/    ← JavaFX (JDK 25, module-info.java presente)
    └── src/main/java/com/taskmaster/taskmasterfrontend/
        ├── controller/     ← Un controller por FXML
        ├── service/ApiService.java   ← Todas las llamadas HTTP al backend
        ├── util/AppContext.java      ← Singleton: userId, username, ApiService
        └── MainApp.java             ← Punto de entrada, gestiona escenas
```

### Backend

**Entidades principales**
- `User` — id, username, email, password (BCrypt), birthDate, createdAt, UserSettings
- `Project` — id, name, description, category, status, priority, user (ManyToOne), soft delete
- `Task` — id, title, description, status, priority, category, dueDate, user (ManyToOne), project (nullable), parentTask (self-ref), soft delete
- `UserSettings` — trashRetentionDays (default 30)

**Enums**
- `TaskStatus`: TODO, IN_PROGRESS, DONE, CANCELLED
- `TaskPriority`: LOW, MEDIUM, HIGH, URGENT
- `TaskCategory`: PERSONAL, ESTUDIOS, TRABAJO

**Reglas de negocio clave**
- No se puede marcar una tarea como DONE si tiene subtareas pendientes
- Soft delete en cascada: al eliminar una tarea, sus subtareas también van a la papelera
- Las tareas personales (project=null) se identifican por usuario via `task.user`
- El scheduler purga automáticamente tareas/proyectos expirados según `trashRetentionDays`
- `SecurityUtils.getCurrentUser()` extrae el usuario autenticado del contexto de Spring Security; todos los controllers lo usan para aislar datos por usuario

**Seguridad**
- Basic Auth (STATELESS), CSRF desactivado
- `/api/auth/**` y `/h2-console/**` públicos; el resto requiere autenticación

**Endpoints principales**
```
POST   /api/auth/register
POST   /api/auth/login

GET    /api/projects
POST   /api/projects
PUT    /api/projects/{id}
DELETE /api/projects/{id}

GET    /api/tasks/home          ← proyectos+tareas+categorías para el home (HomeResponse)
GET    /api/tasks?projectId=
GET    /api/tasks/personal
GET    /api/tasks/category/{category}
GET    /api/tasks/trash
POST   /api/tasks
PATCH  /api/tasks/{id}/status

GET    /api/user/profile
PUT    /api/user/profile
PUT    /api/user/password
DELETE /api/user

GET    /api/settings
PATCH  /api/settings/trash-retention?days=
```

### Frontend

**Flujo de navegación**
- `MainApp` arranca con `login-view.fxml` (400×500)
- Login exitoso navega a `main-view.fxml` (900×600), controlado por `MainController`
- `MainController` contiene la lógica de todos los paneles principales; los demás controllers son para diálogos o vistas secundarias

**Patrón de paneles dinámicos en main-view**
El HBox central tiene 3 hijos fijos: sidebar (220px), projectsPanel, taskPanel.
Para papelera y ajustes se ocultan projectsPanel+taskPanel y se inyecta el nuevo panel con `userData="trash"` o `userData="settings"`.
Para volver al home se hace `removeIf` por userData y se restauran los paneles.
Perfil y detalle de proyecto/tarea abren en una nueva ventana (`Stage`).

**ApiService**
- Usa `java.net.http.HttpClient` (Java 11+, sin dependencias externas)
- Jackson con `JavaTimeModule` para fechas (`LocalDate`, `LocalDateTime`)
- `BASE_URL = "http://localhost:8080"` — hardcodeado, sin configuración externa
- Credenciales guardadas en memoria tras login; se reenvían en cada petición como `Authorization: Basic <base64>`

**Convenciones CSS en JavaFX**
- NO usar `rgba()` — JavaFX no lo soporta en estilos inline
- Usar hex sólidos: `#13131f` (fondo oscuro), `#2a2a3e` (bordes), `#9999bb` (texto secundario), `#a78bfa` (acento morado)
- Colores por categoría: PERSONAL=`#a78bfa`, ESTUDIOS=`#34d399`, TRABAJO=`#fb923c`
- Los estilos se aplican programáticamente en los controllers (no hay `.css` global de tema)

## Pendientes
### Perfil
- [ ] Icono/Foto
- [ ] Estadísticas
- [ ] Historial de actividad
### Ajustes
- [ ] Tema claro/oscuro
- [ ] Exportar/Importar JSON y CSV
- [ ] Ayuda (Documentación de la app)
- [ ] Acerca de (Información de la app y autor)
### Acceso y permisos en el dispositivo
- [ ] Recordatorios activos en formato de notificación
- [ ] Acceder y editar calendario
### Filtros
- [ ] Ordenar por fecha, prioridad
- [ ] Agrupar por fecha, título (alfabéticamente), prioridad
### Etiquetas (tipos de tareas)
- [ ] Crear/Editar/Eliminar etiquetas para tareas
### Tareas
- [ ] Duplicar tarea
- [ ] En detalles de la tarea mostrar etiquetas e historial de modificaciones
- [ ] Tareas vencidas
### Logs
- [ ] Historial de modificaciones, fecha y hora en cada modificación, en: Ver perfil, Detalles de proyecto, Detalles de tarea, Detalles de subtarea
### Opcional - Ideas a implementar en el futuro
- [ ] Apartado de seguridad
- [ ] Verificación de que el correo existe y conectar con aplicaciones de correo como Gmail y Outlook
- [ ] Chatbot o IA