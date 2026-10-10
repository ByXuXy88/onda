# Privacidad

Onda no requiere cuenta y no incorpora analítica, publicidad propia ni servidor de sincronización.

Los programas seguidos, los favoritos, la cola, el estado de escuchado, el progreso y las referencias de descarga se guardan en el dispositivo. Android gestiona la descarga de los audios en el espacio de la aplicación.

La búsqueda envía el texto escrito al catálogo público de Apple Podcasts mediante la API de búsqueda de iTunes. Al añadir un enlace de Apple se consulta también el identificador del programa para obtener su RSS público y su portada. Onda no accede a la biblioteca personal ni inicia sesión en Apple. Apple recibe la solicitud de red y puede aplicar sus propias políticas de tratamiento de datos.

Al abrir un RSS, cargar una portada o descargar un episodio, el servidor del editor recibe la solicitud de red, incluida la dirección IP. Los audios y vídeos pueden contener anuncios del editor. Onda no recomprime ni modifica esos archivos.

Las portadas se guardan en una caché local limitada; Android puede liberarla si necesita espacio. Los ajustes se guardan en el dispositivo. Las miniaturas de los episodios usan la imagen publicada en el RSS o la portada del programa.

Los archivos OPML se leen localmente para extraer los enlaces RSS. No se suben a un servidor de Onda. Al seleccionar los programas, la aplicación consulta sus fuentes públicas.

La exportación OPML escribe los títulos y RSS en la ubicación que eliges mediante Android. No incluye progreso, favoritos, cola, estado de escuchado ni audio o vídeo. Cuando abres Apple Podcasts desde Ajustes, se usa el navegador o la app que Android elija; cualquier inicio de sesión se gestiona allí y Onda no recibe las credenciales.

Puedes quitar programas desde la aplicación y elegir si borrar también sus descargas. Quitar un programa conservando las descargas mantiene el progreso y la caché local para recuperarlos si vuelves a añadirlo. La desinstalación elimina los datos locales y las descargas de la aplicación.

En 1.6, al activar descargas automáticas o avisos por programa, Android puede consultar periódicamente los RSS en segundo plano. Las descargas automáticas se encolan para Wi-Fi. Las notificaciones aparecen localmente y requieren el permiso de Android.

Descubrir consulta las listas públicas de Apple para el país asociado al idioma y la categoría elegidos y consulta hasta 12 RSS candidatos para comprobar el idioma. Al abrir capítulos se consulta el archivo JSON HTTPS publicado por el editor.

La copia completa JSON se guarda en la ubicación elegida mediante Android; contiene programas, favoritos, cola, progreso y ajustes. Onda no la envía a un servidor propio y no incluye audio, vídeo, claves ni referencias de descarga de otros dispositivos. Al restaurarla se sustituye el estado portátil tras comprobar el archivo y confirmar la operación.

## Gemini · Beta opcional

La beta 1.9.0 incorpora análisis de anuncios con una clave API personal de Gemini. Abrir su pantalla no envía audio. Consultar los modelos disponibles envía la clave a la API oficial de Google para autenticar la petición. Antes de cada análisis, Onda muestra el archivo, su tamaño, duración y modelo, y pide confirmar que se envíe **el audio completo** a Google. El uso puede consumir datos, cuota y generar cargos en la cuenta del usuario. Google recibe el audio, la clave, las solicitudes y la dirección IP, y aplica las condiciones de tratamiento correspondientes al proyecto y servicio de Gemini del usuario. Onda no envía la biblioteca, favoritos ni historial de reproducción.

La clave se guarda cifrada con AES-GCM y una clave no exportable de Android Keystore. Está separada de los ajustes portátiles y nunca se incluye en las copias de seguridad JSON. Puedes sustituirla o eliminarla desde la pantalla de Gemini. Esta pantalla no permite capturas y no muestra de nuevo la clave guardada.

El audio se sube a Files API como archivo temporal. Onda intenta eliminar ese archivo de Google al terminar o fallar el análisis; una pérdida de conexión o cancelación durante la subida puede impedir la eliminación inmediata. Files API elimina los archivos temporales automáticamente tras 48 horas. Esto no constituye una promesa de borrado de registros o de otros datos procesados por Google. Cancelar no revierte el consumo de solicitudes ya enviadas.

Solo se guardan localmente los tiempos, etiquetas, modelo utilizado, ajuste de salto y referencias a la descarga analizada. No se conserva una transcripción ni la respuesta bruta. Los resultados no se comparten ni forman parte de la copia portátil. Puedes eliminarlos por episodio; eliminar la clave conserva los tramos ya analizados para escucharlos sin nuevas llamadas a Gemini. Los saltos modifican la posición del reproductor, no el archivo original.


### Preparación automática antes de escuchar (beta 3)

El modo «Preparar con Gemini antes de reproducir» está desactivado por defecto. Al activarlo, la persona autoriza el envío completo de los episodios que elija reproducir mientras el modo esté activo, incluida la cola, mediante su clave personal. No se analiza el catálogo completo ni se escucha el micrófono. El modo admite wifi y datos móviles, con «Solo wifi» opcional. La descarga temporal y la subida a Google consumen datos; la API puede consumir cuota o generar cargos.

Onda espera al análisis completo antes de empezar. Conserva el mismo audio en una caché privada temporal para evitar aplicar los tiempos a anuncios dinámicos diferentes. Conserva los episodios pendientes sin sustituirlos al llegar a tres archivos y elimina el audio temporal al alcanzar el final de reproducción. Cada archivo admite hasta 512 MB y las nuevas preparaciones requieren espacio libre suficiente. Los resultados del análisis y las descargas manuales se conservan; la persona puede liberar la caché desde los ajustes. Android puede borrar esta caché, lo que requerirá una nueva preparación. El audio y sus resultados no se incluyen en copias de seguridad ni en la sección Descargas. Un error no inicia reproducción ni reintentos automáticos; la persona puede reintentar o elegir escuchar sin análisis. Las solicitudes ya enviadas pueden haber consumido cuota aunque se cancelen. La pantalla y una notificación permiten consultar el progreso y cancelar.

### Temas generados y recomendaciones (beta 4)

El mismo análisis de audio con Gemini devuelve anuncios, capítulos, resúmenes breves, subtemas y etiquetas generales de interés. No se pide una transcripción ni un perfil personal del oyente. Los tiempos generados son aproximados, se vinculan al audio analizado y no sustituyen los capítulos publicados por el editor. Los análisis anteriores se reutilizan y solo se actualizan por petición del usuario; actualizar puede consumir cuota o generar cargos.

Para ti está desactivado inicialmente. Al activarlo, Onda guarda en el móvil un historial independiente de hasta 100 episodios escuchados al menos 30 segundos, con sus temas, identificador, programa y RSS. Los favoritos tienen más peso en la selección de temas. Solo al pulsar Buscar recomendaciones se consultan hasta tres temas en la búsqueda pública de Apple Podcasts. No se envían a ese catálogo el audio, la clave API ni el historial completo. No se hacen solicitudes adicionales a Gemini para recomendar ni se añaden podcasts automáticamente.

El usuario puede desactivar Para ti o borrar su historial desde esa pantalla. Desactivarlo detiene el aprendizaje y las consultas, y conserva el historial hasta que se borre. Borrar no modifica favoritos, progreso, anuncios ni capítulos; si la función permanece activa, aprenderá de próximas escuchas. El historial de Para ti y los resultados de Gemini no se exportan en la copia portátil.

## Transcripciones e inicio de Onda 2.0

El inicio organiza episodios ya guardados y progreso local, sin consultar servicios de personalización. Las transcripciones se descargan desde el enlace HTTPS publicado por el editor al abrir su visor; el servidor recibe la petición y la dirección IP. Se guardan en la caché privada del dispositivo, que Android puede liberar. La búsqueda de frases se hace localmente. Esta función no envía audio ni texto a Gemini ni genera una transcripción nueva. Los tiempos son los del editor y pueden variar con anuncios dinámicos. Los ajustes de velocidad y silencios por programa, el límite de espacio automático y la preferencia de bajar volumen se incluyen en la copia portátil; las transcripciones en caché no.
