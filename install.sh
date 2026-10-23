#!/bin/bash

# =============================================================================
# TaskMaster — Script de instalación
# Comprueba e instala Java 21 si no está disponible en el sistema.
# Compatible con: Ubuntu/Debian, Fedora/RHEL, Arch/Manjaro, openSUSE, SDKMAN
# =============================================================================

check_java() {
    if command -v java &>/dev/null; then
        version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
        if [ "$version" -ge 21 ] 2>/dev/null; then
            echo "✓ Java $version detectado"
            return 0
        else
            echo "Java $version detectado, pero se requiere Java 21 o superior."
            return 1
        fi
    fi
    return 1
}

install_java() {
    echo "Instalando Java 21..."
    if command -v apt &>/dev/null; then
        sudo apt update && sudo apt install -y openjdk-21-jdk
    elif command -v dnf &>/dev/null; then
        sudo dnf install -y java-21-openjdk-devel
    elif command -v pacman &>/dev/null; then
        sudo pacman -S --noconfirm jdk21-openjdk
    elif command -v zypper &>/dev/null; then
        sudo zypper install -y java-21-openjdk-devel
    elif command -v sdk &>/dev/null; then
        sdk install java 21-tem
    else
        echo ""
        echo "No se pudo instalar Java automáticamente."
        echo "Instala Java 21 manualmente desde: https://adoptium.net"
        echo "O usa SDKMAN: https://sdkman.io"
        exit 1
    fi
    echo "✓ Java 21 instalado correctamente"
}

echo ""
echo "╔══════════════════════════════════╗"
echo "║   TaskMaster — Instalación       ║"
echo "╚══════════════════════════════════╝"
echo ""

check_java || install_java

echo ""
echo "✓ Todo listo. Ejecuta ./start.sh para arrancar la aplicación."
echo ""