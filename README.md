<div align="center">

<img src="docs/images/onda.svg" alt="Onda" width="340" />

### Tus podcasts. Tu biblioteca. A tu ritmo.

Una app gratuita y de código abierto para escuchar tus podcasts favoritos en Android.<br />
Añade tus programas y lleva tus episodios contigo, también sin conexión.

<p>
  <img src="docs/images/android.svg" alt="Android 9 o posterior" height="28" />
  <img src="docs/images/version.svg" alt="Versión 1.3" height="28" />
  <a href="LICENSE"><img src="docs/images/licencia.svg" alt="Licencia MIT" height="28" /></a>
</p>

<a href="https://github.com/ByXuXy88/onda/releases/tag/v1.3"><img src="docs/images/descargar.svg" alt="Descargar Onda para Android" width="252" /></a>

[Descargas](https://github.com/ByXuXy88/onda/releases/tag/v1.3) · [Informar de un problema](https://github.com/ByXuXy88/onda/issues) · [Contribuir](CONTRIBUTING.md)

**Empieza con la biblioteca vacía. Tú eliges qué podcasts añadir.**

</div>

## Así se ve Onda

| Tu biblioteca | Añade tus podcasts | Usa un enlace RSS |
| :---: | :---: | :---: |
| <img src="docs/images/biblioteca.png" alt="Biblioteca vacía de Onda en negro OLED, con controles por iconos" width="240" /> | <img src="docs/images/anadir.png" alt="Menú para buscar podcasts, añadir RSS o importar OPML" width="240" /> | <img src="docs/images/rss.png" alt="Formulario para añadir un podcast mediante su enlace RSS" width="240" /> |
| Un espacio para tus programas. | Búsqueda, RSS y bibliotecas OPML. | Pega el enlace público de tu podcast. |

<sub>Vistas de la interfaz renderizadas a partir del código de Onda 1.3 con una biblioteca vacía. La apariencia de los diálogos puede variar según la versión de Android.</sub>

## Qué puedes hacer

| | Función | Para qué sirve |
| :---: | --- | --- |
| 🔎 | **Encuentra tus programas** | Busca por nombre en el catálogo público de Apple Podcasts. |
| 📚 | **Crea tu biblioteca** | Añade enlaces RSS HTTPS o importa hasta 100 programas desde un archivo OPML. |
| 🌙 | **Negro OLED** | Interfaz oscura con fondo negro y controles por iconos. |
| 🎧 | **Escucha en segundo plano** | Reproduce con la pantalla apagada y los controles multimedia de Android. |
| ⬇️ | **Llévalos contigo** | Descarga episodios para escucharlos sin conexión y conserva el progreso. |
| ↔️ | **Controla la escucha** | Retrocede 15 segundos, avanza 30 segundos y cambia entre tus programas. |

## Por qué existe Onda

Onda nació de una necesidad sencilla: escuchar en Android los podcasts que seguíamos en Apple Podcasts, sin depender de su versión web.

El proyecto parte de esa idea y busca evolucionar hacia una aplicación independiente para escuchar tus podcasts favoritos, crear tu propia biblioteca y llevar tus episodios contigo, también sin conexión.

Cada persona decide qué programas añadir. **Una instalación nueva no incluye ningún podcast ni ningún audio.** Onda es independiente: no es una aplicación oficial de Apple ni sincroniza tu cuenta de Apple Podcasts.

## Empieza a escuchar

1. Abre la [versión 1.3](https://github.com/ByXuXy88/onda/releases/tag/v1.3) y descarga **Onda.apk** en la sección **Assets**. Necesitas Android 9 o posterior.
2. Instala y abre Onda. Toca **+** para buscar un programa, pegar su RSS o importar una biblioteca OPML.
3. Elige un episodio y toca el icono de reproducción. Para escucharlo sin conexión, descárgalo y espera al estado **Disponible sin conexión**.

Las descargas pueden utilizar datos móviles, pero no itinerancia. Tu biblioteca y el progreso se guardan en el dispositivo.

## Para desarrolladores

<details>
<summary><strong>Compilar, distribuir y conocer los límites actuales</strong></summary>

### Compilar

Necesitas JDK 17, Android SDK 35 y Build Tools 35.0.0. Define `ANDROID_HOME`, o crea un `local.properties` con la ruta de tu SDK:

```properties
sdk.dir=/ruta/a/android-sdk
```

En Linux/macOS:

```sh
sh ./gradlew assembleDebug testDebugUnitTest lintDebug
```

En Windows usa `gradlew.bat`. El APK aparece en `app/build/outputs/apk/debug/app-debug.apk`.

### Firma para distribución

No hay claves de firma en el repositorio. Las compilaciones debug sin configuración usan la clave local de desarrollo de Android. Los APK de ejecuciones independientes de GitHub pueden tener firmas distintas y no sirven como actualizaciones entre sí.

Para publicar actualizaciones, usa una clave privada estable que controles y mantenla fuera del repositorio. El proyecto admite estas variables de entorno:

- `ONDA_KEYSTORE_PATH`: ruta absoluta al archivo de firma.
- `ONDA_KEYSTORE_PASSWORD`: contraseña del almacén.
- `ONDA_KEY_ALIAS`: alias de la clave.
- `ONDA_KEY_PASSWORD`: contraseña de la clave.

Con esas variables configuradas, `sh ./gradlew assembleRelease` genera `app/build/outputs/apk/release/app-release.apk`. Conserva la misma clave para las versiones siguientes. El APK de actualización personal entregado conserva la firma de las versiones anteriores; esa clave no se incluye en este proyecto público.

### Biblioteca vacía y actualización

Una instalación limpia empieza vacía y no consulta fuentes RSS al arrancar. Una actualización conserva los programas ya guardados por el usuario. Se mantiene una migración para instalaciones personales anteriores que ya tenían AM en caché; nunca añade AM en una instalación nueva. Los episodios y podcasts usados en pruebas no se empaquetan en la aplicación.

### Límites actuales

La búsqueda depende del catálogo público de Apple y filtra programas sin RSS HTTPS disponible. No sincroniza cuentas de Apple Podcasts, compras ni historial de otros servicios. La importación OPML guarda la lista de programas; los episodios se cargan al seleccionarlos. La biblioteca y el progreso son locales.

El funcionamiento con pantalla apagada y las descargas debe verificarse también en dispositivos reales. Las pruebas automatizadas cubren el estado inicial vacío, la biblioteca, la importación, la búsqueda, el progreso y la compatibilidad con datos anteriores.


</details>

## Privacidad y licencia

[Privacidad](PRIVACY.md) · [Licencia MIT](LICENSE) · [Cómo contribuir](CONTRIBUTING.md) · [Bibliotecas utilizadas](THIRD_PARTY_NOTICES.md)

Los podcasts y sus derechos pertenecen a sus respectivos editores. Onda no es una aplicación oficial de Apple o Google.
