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
