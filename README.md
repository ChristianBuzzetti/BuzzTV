# BuzzTV

> **BuzzTV** es un fork de [CleanYourTV (`Gathin23/cleanyourtv`)](https://github.com/Gathin23/cleanyourtv) bajo licencia **MIT**. Conserva intacta la filosofía del proyecto original —sin publicidad, sin telemetría, sin permiso `INTERNET` en el launcher, desactivación reversible (`pm disable-user`) y compilación 100 % desde el código fuente— y suma **5 diseños de pantalla de inicio completamente distintos**, **selector de fondos en la TV**, **9 efectos animados livianos**, **rotación automática configurable** y **clima en vivo** en Inicio y en el protector de pantalla.

---

## Los 5 diseños de inicio

En **Configuración del launcher → Diseño / Tema**, elegís la arquitectura de tu pantalla de inicio de forma totalmente independiente del fondo y la animación:

| Diseño | Vista real en TV |
|---|---|
| **Sala (`bento`)**<br>Dashboard Bento en 2 columnas: tarjeta translúcida con reloj grande, fecha y clima en vivo a la izquierda, grilla 2×2 de apps con foco ámbar a la derecha y barra inferior de 3 botones (*Aplicaciones*, *Entradas*, *Configuración*). | ![Diseño Sala](docs/screenshots/theme-sala.jpg) |
| **Cine en casa (`cinema`)**<br>Escenario cinematográfico con título principal en dorado cálido, píldora primaria *Abrir*, accesos rápidos a *Entradas* y *Configuración*, y 4 tarjetas grandes translúcidas en la base. | ![Diseño Cine en casa](docs/screenshots/theme-cine.jpg) |
| **Modo Juego (`gaming`)**<br>Estilo consola: lista vertical de apps con borde verde esmeralda a la izquierda, panel spotlight gigante a la derecha y barra de estado inferior (*Entradas*, *Wi-Fi*, *Configuración*). | ![Diseño Modo Juego](docs/screenshots/theme-juego.jpg) |
| **Minimalista (`minimal`)**<br>Reloj grande centrado, fecha y clima arriba, controles compactos de iconos en la esquina superior derecha y 5 tarjetas flotantes sin banda de fondo. | ![Diseño Minimalista](docs/screenshots/theme-minimalista.jpg) |
| **Estándar (`standard`)**<br>El diseño clásico tipo Apple TV de CleanYourTV: reloj y clima arriba a la izquierda y dock horizontal inferior. | ![Diseño Estándar](docs/screenshots/theme-estandar.jpg) |

---

## Ajustes en la TV y protector de pantalla

Todo se configura directamente con el control remoto desde **Configuración del launcher** (manteniendo pulsado **OK** sobre *Configuración*):

| Configuración del launcher | Protector de pantalla con clima en vivo |
|---|---|
| ![Configuración del launcher](docs/screenshots/ajustes.jpg) | ![Protector de pantalla](docs/screenshots/protector-pantalla.jpg) |

---

## Qué incluye BuzzTV

- **5 diseños de inicio independientes del fondo** (`Estándar`, `Cine en casa`, `Sala`, `Modo Juego`, `Minimalista`), con navegación 100 % determinista por D-pad.
- **Selector de fondos en la TV (`Fondo`)**: elegí una imagen puntual, una sección al azar o todos los fondos al azar sin usar la computadora.
- **9 efectos animados livianos (`Animación de fondo`)**: `Desactivada`, `Según fondo`, `Según clima`, o elegir manualmente entre `Lluvia`, `Tormenta` (con relámpagos suaves), `Nieve`, `Estrellas` (con estrellas fugaces), `Rayos de sol`, `Nubes`, `Burbujas`, `Luciérnagas` y `Partículas`. Todos corren a ≤30 fps sin asignaciones por cuadro y se detienen al abrir una app o al apagar la TV.
- **Intervalo de rotación de fondo (`Rotación de fondo`)**: chips rápidos (`1 min`, `5 min`, `10 min`, `20 min`, `1 h`) para fondos rotativos.
- **Clima en vivo (`Clima en vivo`)**: muestra temperatura, condición y ciudad en Inicio y en el protector de pantalla (`ClockDream` / `ClockActivity`), y puede adaptar automáticamente la animación del fondo al clima real. El launcher sigue **sin permiso `INTERNET`**: los datos llegan localmente desde el add-on opcional [`addons/weather-sync/`](addons/weather-sync/) vía `ContentProvider` (`content://com.buzztv.weathersync/current`).
- **Debloat seguro y reversible** heredado de CleanYourTV: solo usa `pm disable-user --user 0`, nunca desinstala paquetes del sistema y siempre guarda respaldo previo (`tools/restore.sh` / `tools/restore.ps1`).

---

## Instalación rápida

Requisitos: **JDK 17**, **Android SDK (platform 34, build-tools 34.0.0)** y **ADB** habilitado en la TV (por ejemplo en `192.168.1.50`).

### Windows (PowerShell)

```powershell
# 1. Compilar e instalar el launcher en la TV
powershell -ExecutionPolicy Bypass -File launcher\build-and-install.ps1 -TvHost 192.168.1.50

# 2. (Opcional) Cargar la biblioteca de fondos en la TV
powershell -ExecutionPolicy Bypass -File tools\themes.ps1 -TvHost 192.168.1.50 -All

# 3. (Opcional) Instalar el add-on de clima en vivo
cd addons\weather-sync
.\gradlew.bat assembleRelease
adb -s 192.168.1.50:5555 install -r app\build\outputs\apk\release\app-release.apk
```

### Linux / macOS

```bash
# 1. Compilar e instalar el launcher en la TV
TV_HOST=192.168.1.50 launcher/build-and-install.sh

# 2. (Opcional) Cargar la biblioteca de fondos en la TV
TV_HOST=192.168.1.50 tools/themes.sh --all

# 3. (Opcional) Instalar el add-on de clima en vivo
cd addons/weather-sync && ./gradlew assembleRelease
adb -s 192.168.1.50:5555 install -r app/build/outputs/apk/release/app-release.apk
```

> Al terminar de instalar y configurar, desactivá la depuración ADB / inalámbrica en tu TV y verificá con `tools/adb-closed.py 192.168.1.50`.

---

## Documentación adicional

- [Detalles técnicos de BuzzTV (`docs/buzztv.md`)](docs/buzztv.md)
- [Guía del launcher (`docs/launcher.md`)](docs/launcher.md)
- [Restauración de paquetes (`docs/restore.md`)](docs/restore.md)
- [Solución de problemas (`docs/TROUBLESHOOTING.md`)](docs/TROUBLESHOOTING.md)
- [Procedimiento de debloat seguro (`PROMPT.md`)](PROMPT.md)

## Licencia y créditos

Distribuido bajo licencia **MIT** ([`LICENSE`](LICENSE)).  
Basado en [CleanYourTV](https://github.com/Gathin23/cleanyourtv) por Gathin23 y colaboradores.
