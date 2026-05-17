# TaskMaster

<p align="center">
  <img src="taskmaster-frontend/src/main/resources/com/taskmaster/taskmasterfrontend/images/app-icon/icon_128.png" alt="TaskMaster logo" width="96"/>
</p>

<p align="center">
  Gestor de proyectos y tareas de escritorio — <strong>completamente offline, sin cuentas, sin la nube</strong>.
</p>

<p align="center">
  <img alt="Java 21" src="https://img.shields.io/badge/Java-21-007396?logo=openjdk&logoColor=white"/>
  <img alt="Spring Boot" src="https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white"/>
  <img alt="JavaFX" src="https://img.shields.io/badge/JavaFX-21-007396"/>
  <img alt="SQLite" src="https://img.shields.io/badge/SQLite-embebida-003B57?logo=sqlite&logoColor=white"/>
  <img alt="License" src="https://img.shields.io/badge/licencia-MIT-blue"/>
</p>

---

TaskMaster es una aplicación de escritorio para la gestión avanzada de tareas personales y profesionales, desarrollada como TFC del ciclo formativo **DAM** (Desarrollo de Aplicaciones Multiplataforma). Orientada a usuarios que priorizan la privacidad y el control total sobre sus datos frente a soluciones convencionales basadas en la nube.

Toda la información se almacena en el equipo del usuario. Sin registro, sin conexión a internet, sin servidores de terceros.

## ¿Por qué TaskMaster?

Las herramientas de productividad más populares están optimizadas 
para el usuario general: simples, en la nube y con las funcionalidades más útiles reservadas 
para planes de pago. TaskMaster parte de un planteamiento diferente.

| | TaskMaster | Otras herramientas |
|---|---|---|
| Funciona sin internet | ✅ | ❌ |
| Datos en tu equipo, sin terceros | ✅ | ❌ |
| Subtareas jerárquicas reales | ✅ | Limitado o de pago |
| WorkLog — registro de tiempo por tarea | ✅ | Planes de pago |
| Estadísticas de productividad | ✅ | Planes de pago |
| Sin límite de proyectos ni tareas | ✅ | Freemium |
| Temas visuales personalizables | ✅ | ❌ |
| Código abierto y gratuito para siempre | ✅ | ❌ |

## Características

- **Proyectos y tareas jerarquizadas** — Crea proyectos con tareas y subtareas, categorías, prioridades (`Baja`, `Media`, `Alta`, `Urgente`), fechas límite y estados (`Nuevo`, `En curso`, `Completado`, `Entregado`, `Cancelado`).
- **Registro de tiempo (WorkLog)** — Registra horas dedicadas por tarea al estilo Redmine, con historial de entradas y totales acumulados.
- **Panel de estadísticas** — Análisis de productividad accesible desde la pantalla principal.
- **Vista de calendario** — Visualiza las fechas límite de tus tareas en su día correspondiente.
- **Búsqueda y filtros en tiempo real** — Filtra por estado, prioridad, categoría y fecha; ordena y agrupa por proyectos.
- **Papelera automática** — Eliminación lógica con restauración y purga configurable por el usuario.
- **Registro de actividad** — Historial de modificaciones por tarea con marcas de tiempo.
- **Perfil de usuario** — Autenticación local con credenciales cifradas (BCrypt), edición de perfil y estadísticas de uso.
- **14 temas visuales** — Modo claro, oscuro y variantes de color.
- **Internacionalización** — Interfaz disponible en español e inglés.
- **Manual de usuario integrado** — Documentación HTML accesible directamente desde la aplicación.

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 3.5, Spring Data JPA, Hibernate, Spring Security |
| Frontend | JavaFX 21, FXML, CSS (sistema de temas con variables) |
| Base de datos | SQLite embebida (sin servidor externo) |
| Iconografía | Ikonli 12.3 + FontAwesome 5 |
| Autenticación | HTTP Basic + BCrypt |
| Build | Maven (multi-módulo) |
| JDK | Java 21 (LTS) |

## Arquitectura

TaskMaster aplica el patrón **MVC** con separación estricta entre módulos:

```
taskmaster/
├── taskmaster-backend/          # API REST — Spring Boot
│   └── src/main/java/
│       ├── config/              # Configuración (almacenamiento de avatares)
│       ├── controller/          # Endpoints REST
│       ├── dto/
│       │   ├── request/         # DTOs de entrada (login, registro, tareas…)
│       │   └── response/        # DTOs de salida (proyectos, tareas, usuario…)
│       ├── exception/           # Excepciones de negocio y handler global
│       ├── model/               # Entidades JPA
│       │   └── enums/           # Estados, prioridades, categorías, temas
│       ├── repository/          # Acceso a datos (Spring Data JPA)
│       ├── security/            # Spring Security, BCrypt, UserDetailsService
│       ├── service/             # Lógica de negocio
│       └── TrashScheduler.java  # Tarea programada de purga automática
│
├── taskmaster-frontend/         # Interfaz de escritorio — JavaFX
│   └── src/main/
│       ├── java/
│       │   ├── controller/      # Controladores de pantalla
│       │   ├── service/         # ApiService — comunicación con el backend
│       │   └── util/            # ThemeManager, LanguageManager, IconCatalog…
│       └── resources/
│           ├── *.fxml           # Definición de vistas
│           ├── themes/          # 14 temas visuales CSS
│           ├── i18n/            # messages_es / messages_en
│           ├── help/            # Manual de usuario HTML autocontenido
│           └── images/          # Iconos de la aplicación (multi-resolución)
│
└── pom.xml                      # POM raíz (multi-módulo)
```

La comunicación entre frontend y backend se realiza mediante API REST interna sobre localhost, lo que permite evolucionar ambas capas de forma independiente.

## Requisitos previos

| Método | Requisitos |
|---|---|
| Windows (instalador) | Windows 10 o superior — sin requisitos adicionales |
| Linux (scripts) | Java 21 se instala automáticamente con `install.sh` |
| Desde el código fuente | JDK 21+, Maven 3.8+ |

> [!NOTE]
> La base de datos SQLite se crea automáticamente en el primer arranque. No se requiere instalar ni configurar ningún motor de base de datos.

## Instalación y ejecución

### Windows — Instalador

Descarga `TaskMaster-1.0.0.exe` desde [GitHub Releases](https://github.com/carlxs115/taskmaster/releases) e instálalo normalmente. El instalador incluye un JRE personalizado (jlink + jpackage + WiX), no requiere tener Java instalado en el sistema.

Al arrancar la aplicación, el backend se lanza automáticamente como proceso hijo y se cierra al salir.

### Linux — Scripts

```bash
# 1. Clona el repositorio
git clone https://github.com/carlxs115/taskmaster.git
cd taskmaster

# 2. Instala Java 21 automáticamente (detecta apt, dnf, pacman, zypper o sdkman)
chmod +x install.sh && ./install.sh

# 3. Arranca la aplicación
chmod +x start.sh && ./start.sh
```

`start.sh` arranca backend y frontend secuencialmente y cierra el backend al salir del frontend.

### Ejecución desde el código fuente (desarrollo)

```bash
# Compila ambos módulos
mvn clean install

# Terminal 1 — backend
cd taskmaster-backend
mvn spring-boot:run

# Terminal 2 — frontend
cd taskmaster-frontend
mvn javafx:run
```

> [!TIP]
> Si tu `JAVA_HOME` apunta a un JDK anterior (por ejemplo, JDK 8 en entornos corporativos), sobreescríbelo para la sesión actual:
>
> ```powershell
> # PowerShell (Windows)
> $env:JAVA_HOME = "C:\path\to\jdk-21"
> ```
> ```bash
> # Bash / Zsh (Linux)
> export JAVA_HOME=/path/to/jdk-21
> ```

## Generar Javadoc

```bash
mvn javadoc:javadoc
```

La documentación se genera en `target/site/apidocs/` dentro de cada módulo.

## Roadmap

Las siguientes funcionalidades están identificadas como líneas de trabajo futuro:

- [ ] **Exportación e importación de datos** (JSON / CSV) para copias de seguridad y migración.
- [ ] **Estimación de tiempo** por tarea, complementando el WorkLog existente.
- [ ] **Dependencias entre tareas** para gestión de proyectos más avanzada.
- [ ] **Autenticación JWT** en sustitución de HTTP Basic para mejorar la seguridad de sesión.
- [ ] **Cifrado del fichero de base de datos** mediante SQLCipher.

A medio y largo plazo se contemplan dos líneas diferenciadas:

- **TaskMaster Offline** — Evolución de la versión actual incorporando las mejoras del roadmap y una versión para móvil complementaria.
- **TaskMaster Teams** — Reorientación hacia equipos de trabajo con sincronización en la nube, colaboración multiusuario, gestión de roles y notificaciones. Mantendría la filosofía de código abierto y uso completamente gratuito.

## Autor

- Carlos Riera
- GitHub: **[@Carlxs115](https://github.com/carlxs115)**

---

<p align="center">Hecho con ☕ y JavaFX</p>
