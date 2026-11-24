# BuzzTV

BuzzTV es un fork de [CleanYourTV](https://github.com/Gathin23/cleanyourtv) (MIT). Todo lo del original sigue
igual: el debloat, el launcher, los temas, las herramientas y los scripts de restauración. Este fork agrega
un **selector de temas en la TV**, para cambiar de tema con el control remoto sin usar la computadora.

## Qué agrega

- **Fila "Tema" en Ajustes del launcher.** Lista "Original" y cada tema guardado en la TV, con su
  miniatura (`screenshot.jpg`). Al elegir uno se aplica y Home se reconstruye con él al cerrar los ajustes.
  "Original" vuelve al aspecto incorporado del launcher.
- **Biblioteca de temas en la TV.** Los temas viven en `Android/data/com.gathin.tvhome/files/themes/<nombre>/`,
  con la misma estructura que una carpeta de [`themes/`](../themes/). Se cargan desde la computadora con
  [`tools/themes.sh`](../tools/themes.sh) (en Windows, `tools\themes.ps1`).
- **Diez temas nuevos**, dibujados en código y con licencia CC0: [`gamer/`](../themes/gamer/),
  [`cine/`](../themes/cine/), [`synthwave/`](../themes/synthwave/), [`estadio/`](../themes/estadio/),
  [`atardecer/`](../themes/atardecer/), [`mesh/`](../themes/mesh/), [`cosmos/`](../themes/cosmos/),
  [`neotokyo/`](../themes/neotokyo/), [`oceano/`](../themes/oceano/) y [`patagonia/`](../themes/patagonia/).

## Diseños / Temas de inicio

Independiente del **Fondo** elegido, Configuración del launcher incluye **Diseño / Tema** (`home_theme`, por
defecto `standard` / Estándar), con 5 diseños listos para cualquier fondo:

- **Estándar (`standard`)**: el diseño clásico tipo Apple TV.
- **Cine en casa (`cinema`)**: encabezado limpio, título grande en dorado cálido (`#F6D58E`) sobre el dock y
  foco con brillo dorado.
- **Sala (`bento`)**: reloj grande y fino (`68sp`), fecha y clima dentro de una tarjeta translúcida en la
  esquina superior izquierda, con borde de foco ámbar (`#F5B963`).
- **Modo Juego (`gaming`)**: encabezado HUD en mayúsculas, estética oscura de alto contraste y foco verde
  esmeralda (`#4ADE80`) con esquinas más marcadas.
- **Minimalista (`minimal`)**: reloj, fecha y clima centrados arriba, botones de iconos compactos y dock
  flotante transparente con foco blanco fino.

## Fondos animados y rotación

Dos claves para cada fondo/tema y dos controles directos en Configuración del launcher:

- **Animación de fondo (`wallpaper_animation`)**: selector con **Desactivada**, **Según fondo**, **Según clima**
  (adaptado al clima real vía `LiveWeather`), o cualquiera de los 9 efectos livianos: `rain` (lluvia),
  `storm` (tormenta con relámpagos suaves), `snow` (nieve), `stars` (estrellas y fugaces), `sunbeams`
  (rayos de sol), `clouds` (nubes), `bubbles` (burbujas), `fireflies` (luciérnagas) y `motes` (partículas).
  Mantiene compatibilidad hacia atrás con `wallpaper_motion`.
- **Rotación de fondo (`wallpaper_rotation_minutes`)**: chips directos (`1 min`, `5 min`, `10 min`, `20 min`,
  `1 h`) que ajustan el intervalo de cambio cuando se usa un fondo rotativo.

Solo se animan con Home al frente, a 20–30 cuadros por segundo como máximo, y se frenan al abrir una app, con
el protector de pantalla o al apagar la TV. No corren con `reduced_motion` ni en TVs que Android marca como de poca RAM.

## Clima en vivo

La opción **Clima en vivo** (apagada por defecto, en Configuración del launcher) muestra la temperatura, la
condición y la ciudad junto a la hora en Inicio y en el protector de pantalla (reloj). Cuando **Animación de
fondo** está en **Según clima**, el clima real elige automáticamente el efecto (`storm`, `rain`, `snow`,
`clouds`, `sunbeams` o `stars`).

El launcher sigue sin permiso `INTERNET`. Los datos los provee el add-on liviano [`addons/weather-sync/`](../addons/weather-sync/),
que detecta automáticamente la ciudad aproximada por IP (`get.geojs.io` / `ipwho.is`), consulta
[Open-Meteo](https://open-meteo.com/) sin clave y expone el resultado por `content://com.buzztv.weathersync/current`
sin servicios en segundo plano.

## Uso

```bash
# Instalar el launcher (igual que en el original)
TV_HOST=192.168.1.50 launcher/build-and-install.sh

# Cargar temas en la TV (no aplica ninguno)
TV_HOST=192.168.1.50 tools/themes.sh themes/gamer themes/cine themes/synthwave
TV_HOST=192.168.1.50 tools/themes.sh --all          # todas las carpetas de themes/
TV_HOST=192.168.1.50 tools/themes.sh --list         # qué hay en la TV
TV_HOST=192.168.1.50 tools/themes.sh --remove cine  # sacar uno de la biblioteca
```

```powershell
tools\themes.ps1 -TvHost 192.168.1.50 themes\gamer themes\cine
tools\themes.ps1 -TvHost 192.168.1.50 -All
```

Después, en la TV: **Ajustes del launcher → Tema** y elegir uno.

El nombre de la carpeta es el nombre en la biblioteca: minúsculas, números, `-` y `_` (hasta 40). Volver a
mandar un tema lo reemplaza entero. `tools/theme.sh` sigue funcionando como siempre: aplica un tema
directamente, y en el selector aparece como "Personalizado".

## Rendimiento

Home no lee la biblioteca al arrancar ni mientras navegás: el código de inicio y de navegación es el mismo
que el original. La biblioteca solo se lee al abrir el selector, que decodifica las miniaturas a ~152 dp en
RGB_565 y fuera del hilo principal. Aplicar un tema copia sus archivos en segundo plano (con escritura
atómica: archivo temporal y rename) y escribe un `theme_revision` nuevo, como hace `tools/theme.sh`. Los temas
nuevos usan `wallpaper_true_color` apagado, igual que el valor por defecto, así que el fondo ocupa la misma
memoria que cualquier otro tema con una sola imagen. El APK crece unos 10 KB en total.

## Cómo funciona

| Pieza | Archivo |
|---|---|
| Listar, aplicar, restablecer y borrar temas de la biblioteca | `launcher/app/src/main/java/com/gathin/tvhome/ThemeLibrary.java` |
| El selector de fondo (todos al azar, una sección al azar o una imagen) | `launcher/app/src/main/java/com/gathin/tvhome/BackgroundPicker.java`, `BackgroundLibrary.java` |
| Escritura por el provider en `themes/<nombre>/` (firmware que bloquea Android/data) | `ThemeFiles.java`, `ThemeProvider.java` |
| Carga desde la computadora | `tools/themes.sh`, `tools/themes.ps1` |
| Pruebas | `launcher/app/src/test/.../ThemeLibraryTest.java`, `ThemeFilesTest.java`, `tests/test_themes_library.py` |

El paquete sigue siendo `com.gathin.tvhome`, así que todas las herramientas del original sirven sin cambios.
Si el launcher que está en la TV se compiló en otra computadora, la instalación falla con
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, porque la firma de depuración es distinta. En ese caso hay que
desinstalar ese launcher primero (`tools/tv.py 'pm uninstall com.gathin.tvhome'`, con otro launcher como Home)
y volver a correr `build-and-install.sh`.
