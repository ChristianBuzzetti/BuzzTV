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
- **Cinco temas nuevos**, dibujados en código y con licencia CC0: [`gamer/`](../themes/gamer/),
  [`cine/`](../themes/cine/), [`synthwave/`](../themes/synthwave/), [`estadio/`](../themes/estadio/) y
  [`atardecer/`](../themes/atardecer/).

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
memoria que cualquier otro tema con una sola imagen. El APK crece unos 6 KB.

## Cómo funciona

| Pieza | Archivo |
|---|---|
| Listar, aplicar, restablecer y borrar temas de la biblioteca | `launcher/app/src/main/java/com/gathin/tvhome/ThemeLibrary.java` |
| El selector (diálogo con miniaturas) | `launcher/app/src/main/java/com/gathin/tvhome/ThemePicker.java` |
| Escritura por el provider en `themes/<nombre>/` (firmware que bloquea Android/data) | `ThemeFiles.java`, `ThemeProvider.java` |
| Carga desde la computadora | `tools/themes.sh`, `tools/themes.ps1` |
| Pruebas | `launcher/app/src/test/.../ThemeLibraryTest.java`, `ThemeFilesTest.java`, `tests/test_themes_library.py` |

El paquete sigue siendo `com.gathin.tvhome`, así que todas las herramientas del original sirven sin cambios.
Si el launcher que está en la TV se compiló en otra computadora, la instalación falla con
`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, porque la firma de depuración es distinta. En ese caso hay que
desinstalar ese launcher primero (`tools/tv.py 'pm uninstall com.gathin.tvhome'`, con otro launcher como Home)
y volver a correr `build-and-install.sh`.
