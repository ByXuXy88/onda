# Contribuir a Onda

Describe el problema y abre una propuesta antes de cambios amplios. Para corregir errores, explica cómo reproducirlos y qué cambia en la interfaz o la reproducción.

Usa JDK 17, Android SDK 35 y ejecuta:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Comprueba además en un dispositivo la instalación con biblioteca vacía, la adición por búsqueda/RSS, la importación OPML, la reproducción con pantalla apagada y la escucha sin conexión.

Mantén el fondo negro OLED y las descripciones de accesibilidad de los controles. No añadas podcasts predeterminados, credenciales, claves de firma, archivos APK ni datos personales al código del repositorio.
