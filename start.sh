#!/bin/bash

# =============================================================================
# TaskMaster — Script de arranque
# Inicia el backend Spring Boot y el frontend JavaFX.
# Al cerrar el frontend, detiene el backend automáticamente.
# =============================================================================

echo ""
echo "╔══════════════════════════════════╗"
echo "║   TaskMaster — Iniciando         ║"
echo "╚══════════════════════════════════╝"
echo ""

# Arranca el backend en segundo plano
echo "Arrancando backend..."
cd taskmaster-backend
./mvnw spring-boot:run &
BACKEND_PID=$!
cd ..

# Espera a que el backend esté listo
echo "Esperando al backend..."
until curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/auth/login | grep -qE "^[0-9]+$"; do
    sleep 1
done
echo "✓ Backend listo"

# Arranca el frontend (bloquea hasta que el usuario cierra la app)
echo "Arrancando frontend..."
cd taskmaster-frontend
./mvnw javafx:run

# Al cerrar el frontend, detiene el backend
echo ""
echo "Cerrando TaskMaster..."
kill $BACKEND_PID 2>/dev/null
wait $BACKEND_PID 2>/dev/null
echo "✓ TaskMaster cerrado."
echo ""