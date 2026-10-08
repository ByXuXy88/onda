# Onda · Tus podcasts en Android

Onda nació de una necesidad sencilla: escuchar en Android los podcasts que seguíamos en Apple Podcasts, sin depender de su versión web.

El proyecto parte de esa idea y busca evolucionar hacia una aplicación independiente para escuchar tus podcasts favoritos, crear tu propia biblioteca y llevar tus episodios contigo, también sin conexión.

Cada persona decide qué programas añadir. Onda ofrece una interfaz en negro OLED, controles por iconos y reproducción en segundo plano. No es una aplicación oficial de Apple ni sincroniza tu cuenta de Apple Podcasts.

**Una instalación nueva no incluye ningún podcast ni ningún audio.**

## Funciones

- Buscar podcasts por nombre en el catálogo público de Apple Podcasts.
- Añadir podcasts mediante un enlace RSS público HTTPS.
- Importar bibliotecas OPML con hasta 100 programas, incluidas carpetas anidadas.
- Cambiar entre programas y quitarlos de la biblioteca, con borrado opcional de sus descargas.
- Reproducir con pantalla apagada y controles multimedia de Android.
- Descargar episodios, escucharlos sin conexión y conservar el progreso.
- Icono adaptativo, icono temático en Android 13+ y descripciones de accesibilidad.

Android 9 o posterior. Versión actual: **1.2**.

## Uso

Abre Onda y toca **+**. Elige búsqueda por nombre, enlace RSS o archivo OPML. Selecciona un programa en la lista superior para ver sus episodios.

El triángulo reproduce, las barras pausan y las flechas circulares retroceden 15 segundos o avanzan 30 segundos. La flecha de descarga guarda un episodio. Espera a que se indique «Disponible sin conexión» antes de escucharlo sin Internet. Las descargas pueden utilizar datos móviles, pero no itinerancia.

## Compilar

Necesitas JDK 17, Android SDK 35 y Build Tools 35.0.0. Define `ANDROID_HOME`, o crea un `local.properties` con la ruta de tu SDK:

```properties
sdk.dir=/ruta/a/android-sdk
```

En Linux/macOS:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

En Windows usa `gradlew.bat`. El APK aparece en `app/build/outputs/apk/debug/app-debug.apk`.

## GitHub

[Descarga Onda 1.2 para Android](https://github.com/ByXuXy88/onda/releases/tag/v1.2). En la sección Assets encontrarás `Onda.apk` y el paquete completo del proyecto.

El proyecto incluye un flujo de GitHub Actions que compila, ejecuta pruebas y análisis de Android y ofrece el APK como artefacto en la pestaña Actions.

## Firma para distribución

No hay claves de firma en el repositorio. Las compilaciones debug sin configuración usan la clave local de desarrollo de Android. Los APK de ejecuciones independientes de GitHub pueden tener firmas distintas y no sirven como actualizaciones entre sí.

Para publicar actualizaciones, usa una clave privada estable que controles y mantenla fuera del repositorio. El proyecto admite estas variables de entorno:

- `ONDA_KEYSTORE_PATH`: ruta absoluta al archivo de firma.
- `ONDA_KEYSTORE_PASSWORD`: contraseña del almacén.
- `ONDA_KEY_ALIAS`: alias de la clave.
- `ONDA_KEY_PASSWORD`: contraseña de la clave.

Con esas variables configuradas, `./gradlew assembleRelease` genera `app/build/outputs/apk/release/app-release.apk`. Conserva la misma clave para las versiones siguientes. El APK de actualización personal entregado conserva la firma de las versiones anteriores; esa clave no se incluye en este proyecto público.

## Biblioteca vacía y actualización

Una instalación limpia empieza vacía y no consulta fuentes RSS al arrancar. Una actualización conserva los programas ya guardados por el usuario. Se mantiene una migración para instalaciones personales anteriores que ya tenían AM en caché; nunca añade AM en una instalación nueva. Los episodios y podcasts usados en pruebas no se empaquetan en la aplicación.

## Límites actuales

La búsqueda depende del catálogo público de Apple y filtra programas sin RSS HTTPS disponible. No sincroniza cuentas de Apple Podcasts, compras ni historial de otros servicios. La importación OPML guarda la lista de programas; los episodios se cargan al seleccionarlos. La biblioteca y el progreso son locales.

El funcionamiento con pantalla apagada y las descargas debe verificarse también en dispositivos reales. Las pruebas automatizadas cubren el estado inicial vacío, la biblioteca, la importación, la búsqueda, el progreso y la compatibilidad con datos anteriores.

## Privacidad y licencia

Lee [PRIVACY.md](PRIVACY.md). Código distribuido bajo [MIT](LICENSE). Onda es un proyecto independiente y no es una aplicación oficial de Apple o Google. Los podcasts y sus derechos pertenecen a sus respectivos editores.
