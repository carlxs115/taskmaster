#!/bin/bash

# =============================================================================
# TaskMaster — Script de arranque
# Compila el JAR del backend si no existe y arranca el frontend JavaFX.
# El frontend se encarga de lanzar el backend y de detenerlo al cerrarse.
# =============================================================================

cd "$(dirname "$0")" || exit 1

BACKEND_JAR="taskmaster-backend/target/taskmaster-0.0.1-SNAPSHOT.jar"

echo ""
echo "╔══════════════════════════════════╗"
echo "║   TaskMaster — Iniciando         ║"
echo "╚══════════════════════════════════╝"
echo ""

# Compila el backend la primera vez (o si se ha borrado target/)
if [ ! -f "$BACKEND_JAR" ]; then
    echo "Compilando backend (solo la primera vez)..."
    (cd taskmaster-backend && ./mvnw -q -B package -DskipTests) || {
        echo "✗ No se pudo compilar el backend."
        exit 1
    }
    echo "✓ Backend compilado"
fi

# Arranca el frontend (bloquea hasta que el usuario cierra la app)
echo "Arrancando TaskMaster..."
cd taskmaster-frontend
./mvnw -q -B javafx:run

echo ""
echo "✓ TaskMaster cerrado."
echo ""
