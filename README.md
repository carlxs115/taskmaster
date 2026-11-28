# TaskMaster

<p align="center">
  <img src="taskmaster-frontend/src/main/resources/com/taskmaster/taskmasterfrontend/images/app-icon/icon_128.png" alt="TaskMaster logo" width="96"/>
</p>

<p align="center">
  Gestor de proyectos y tareas de escritorio — <strong>completamente offline</strong>.
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
| Temas visuales personalizados | ✅ | ❌ |
| Código abierto y gratuito para siempre | ✅ | ❌ |

## Características

- **Proyectos y tareas jerarquizadas** — Crea proyectos con tareas y subtareas, categorías, prioridades (`Baja`, `Media`, `Alta`, `Urgente`), fechas límite y estados (`Nuevo`, `En curso`, `Completado`, `Entregado`, `Cancelado`).
- **Registro de tiempo (WorkLog)** — Registra horas dedicadas por tarea, con historial de entradas y totales acumulados.
- **Panel de estadísticas** — Análisis de productividad accesible desde la pantalla principal.
- **Vista de calendario** — Visualiza las fechas límite de tus tareas en su día correspondiente.
- **Búsqueda y filtros en tiempo real** — Filtra por estado, prioridad, categoría y fecha; ordena y agrupa por proyectos.
- **Papelera automática** — Eliminación lógica con restauración y purga configurable por el usuario.
- **Registro de actividad** — Historial de modificaciones por tarea con marcas de tiempo.
- **Perfil de usuario** — Autenticación local con credenciales cifradas (BCrypt), edición de perfil y estadísticas de uso.
- **14 temas visuales** — Modo claro, oscuro y variantes de color.
- **Internacionalización** — Interfaz disponible en español e inglés.
- **Manual de usuario integrado** — Documentación HTML accesible directamente desde la aplicación.
- **Exportación e importación de datos** — Exporta tus proyectos y tareas en JSON (para volver a importarlos en cualquier cuenta, sin borrar nada) o en CSV (para hojas de cálculo).

## Stack tecnológico

| Capa | Tecnología |
|---|---|
| Backend | Spring Boot 3.5, Spring Data JPA, Hibernate, Flyway, Spring Security |
| Frontend | JavaFX 21, FXML, CSS (sistema de temas con variables) |
| Base de datos | SQLite embebida (sin servidor externo) |
| Iconografía | Ikonli 12.3 + FontAwesome 5 |
| Autenticación | HTTP Basic + BCrypt |
| Build | Maven (dos módulos independientes, con Maven Wrapper) |
| JDK | Java 21 (LTS) |

## Arquitectura

TaskMaster aplica el patrón **MVC** con separación estricta entre módulos:

```
taskmaster/
├── taskmaster-backend/          # API REST — Spring Boot
│   ├── pom.xml
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
│   ├── pom.xml
│   └── src/main/
│       ├── java/
│       │   ├── controller/      # Controladores de pantalla (20 vistas)
│       │   ├── service/         # ApiService — comunicación con el backend
│       │   └── util/            # ThemeManager, LanguageManager, IconCatalog…
│       └── resources/
│           ├── *.fxml           # Definición de vistas (20 pantallas)
│           ├── themes/          # 14 temas visuales CSS
│           ├── i18n/            # messages_es / messages_en
│           ├── help/            # Manual de usuario HTML autocontenido
│           └── images/          # Iconos de la aplicación (multi-resolución)
│
├── scripts/
│   └── build-installer.ps1      # Construye el instalador de Windows (jlink + jpackage + WiX)
│
├── install.sh                   # Instalador de Java 21 para Linux (detecta gestor de paquetes)
└── start.sh                     # Script de arranque Linux (compila el backend y abre la app)
```

La comunicación entre frontend y backend se realiza mediante API REST interna sobre localhost, lo que permite evolucionar ambas capas de forma independiente.

## Requisitos previos

| Método | Requisitos |
|---|---|
| Windows (instalador) | Windows 10 o superior — sin requisitos adicionales |
| Linux (scripts) | Java 21 se instala automáticamente con `install.sh` |
| Desde el código fuente | JDK 21+ (Maven se incluye mediante `mvnw`) |
| Generar el instalador de Windows | Windows 10 o superior, JDK 21 y WiX Toolset 3.x en el `PATH` |

> [!NOTE]
> La base de datos SQLite se crea automáticamente en el primer arranque. No se requiere instalar ni configurar ningún motor de base de datos.

## Instalación y ejecución

### Windows — Instalador

Descarga `TaskMaster-<versión>.exe` de la [última versión en GitHub Releases](https://github.com/carlxs115/taskmaster/releases/latest) e instálalo normalmente. El instalador incluye un JRE personalizado (jlink + jpackage + WiX), no requiere tener Java instalado en el sistema. Si ya tienes instalada una versión anterior, el instalador la actualiza y conserva tus datos.

Al arrancar la aplicación, el backend se lanza automáticamente como proceso hijo y se cierra al salir.

#### Generar el instalador de Windows

El instalador se construye con `scripts/build-installer.ps1`, que genera un instalador equivalente al `.exe` publicado en GitHub Releases.

Requisitos, en una máquina Windows 10 o superior:

- JDK 21 completo, con `java`, `jlink` y `jpackage` en el `PATH`. Si `JAVA_HOME` está definido, debe apuntar a ese mismo JDK: el script lo usa para localizar `jmods`.
- [WiX Toolset 3.x](https://wixtoolset.org/docs/wix3/) con `candle.exe` y `light.exe` en el `PATH`. JDK 21 no admite WiX 4 ni 5.

Desde PowerShell, en la raíz del repositorio:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\build-installer.ps1
```

Parámetros opcionales:

| Parámetro | Por defecto | Descripción |
|---|---|---|
| `-Version` | `1.1.1` | Versión del instalador; da nombre a `TaskMaster-<versión>.exe` |
| `-Tipo` | `exe` | `exe` genera el instalador; `app-image` genera solo la carpeta de la aplicación, sin necesitar WiX |
| `-Salida` | `taskmaster-frontend\target\installer\dist` | Carpeta de destino |

El script:

- empaqueta el backend y el frontend con el Maven Wrapper (`mvnw.cmd`, sin ejecutar los tests);
- copia las dependencias del frontend, con las bibliotecas de JavaFX para Windows;
- genera con `jlink` un runtime que incluye también los módulos que necesita el backend, porque el frontend lo lanza con su mismo Java;
- crea con `jpackage` el instalador, con el JAR del backend junto al del frontend y un UpgradeCode fijo, para que cada versión actualice la anterior en lugar de instalarse a su lado.

El resultado queda en `taskmaster-frontend\target\installer\dist\TaskMaster-<versión>.exe`.

#### Publicar una versión

El workflow [`instalador-windows.yml`](.github/workflows/instalador-windows.yml) ejecuta el script en un runner de Windows de GitHub Actions, así que no hace falta tener Windows a mano:

1. Cambia `about.version` en `messages_es.properties` y `messages_en.properties` y el valor por defecto de `-Version` en el script.
2. Escribe las notas de la versión en `.github/release-notes/v<versión>.md`.
3. Con esos cambios en `main`, crea y sube el tag: `git tag v<versión> && git push origin v<versión>`.
4. El workflow genera el `.exe` y su SHA-256 y crea con ellos un **borrador** de release. Revísalo en GitHub y publícalo.

Para probar el instalador sin publicar nada, lanza el workflow a mano desde *Actions → Instalador de Windows → Run workflow* con la versión. El `.exe` queda como artefacto del workflow durante 14 días.

El workflow falla si el tag no tiene la forma `vX.Y.Z`, si `about.version` no coincide con la versión o si faltan las notas.

### Linux — Scripts

```bash
# 1. Clona el repositorio
git clone https://github.com/carlxs115/taskmaster.git
cd taskmaster

# 2. Instala Java 21 automáticamente (detecta apt, dnf, pacman, zypper o sdkman)
./install.sh

# 3. Arranca la aplicación
./start.sh
```

No hace falta `sudo` ni tener Maven instalado: los scripts usan el Maven Wrapper (`mvnw`) incluido en cada módulo.

`start.sh` hace lo siguiente:

1. La primera vez, compila el JAR del backend (`taskmaster-backend/target/taskmaster-0.0.1-SNAPSHOT.jar`). Puede tardar unos minutos mientras Maven descarga las dependencias.
2. Arranca el frontend, que lanza el backend como proceso hijo y lo detiene al cerrar la ventana.

> [!IMPORTANT]
> El JAR del backend solo se compila si no existe. Si actualizas el repositorio (`git pull`) o modificas el backend, borra `taskmaster-backend/target` antes de volver a ejecutar `./start.sh` para que se recompile.

### Ejecución desde el código fuente (desarrollo)

El frontend siempre arranca el backend desde su JAR, así que hay que empaquetarlo antes de lanzar la interfaz:

```bash
# 1. Empaqueta el backend (repetir tras cada cambio en el backend)
cd taskmaster-backend
./mvnw package -DskipTests

# 2. Arranca el frontend (lanza y detiene el backend automáticamente)
cd ../taskmaster-frontend
./mvnw javafx:run
```

En Windows, usa `mvnw.cmd` en lugar de `./mvnw`.

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
# Dentro de taskmaster-backend o taskmaster-frontend
./mvnw javadoc:javadoc
```

La documentación se genera en `target/site/apidocs/` dentro de cada módulo.

## Roadmap

Las siguientes funcionalidades están identificadas como líneas de trabajo futuro:

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
