# Gemini en Onda 2.0

## Beta experimental · Gemini

[**Onda 1.9.0-beta.5**](https://github.com/ByXuXy88/onda/releases/tag/v1.9.0-beta.5) añade análisis opcional de anuncios con la clave API personal de Gemini. Onda 2.0 conserva estas funciones como opciones experimentales.

1. Instala **Onda-2.0.0.apk** sobre Onda, sin desinstalarla. Conserva el paquete, la firma y los datos; Android no permite volver a instalar una versión con un código de versión inferior sobre esta beta.
2. Abre **Ajustes → Saltar anuncios · Gemini Beta**. Añade tu clave API y elige un modelo que admita audio entre los disponibles para tu cuenta, o escribe su identificador.
3. Activa **Preparar con Gemini antes de reproducir** y confirma la autorización para analizar los episodios que reproduzcas. Funciona con **wifi y datos móviles**; **Solo wifi** es opcional y está desactivado inicialmente.
4. Pulsa **Play**. Si no existe un análisis válido, Onda obtiene audio temporal, muestra la preparación y espera a que Gemini analice el archivo completo para detectar anuncios y organizar capítulos con subtemas en una sola solicitud de generación. Después empieza automáticamente y salta los tramos detectados, incluidos los del comienzo. La cola prepara cada episodio antes de escucharlo.
5. En el reproductor, **Temas detectados por Gemini** muestra título, resumen, intervalo y subtemas desplegables; toca un tema para ir a ese momento. Los capítulos oficiales permanecen en su propia sección. Los tiempos generados solo se muestran para el mismo audio analizado.
6. Abre **Descubrir → Para ti**, activa la personalización y escucha episodios con temas de Gemini al menos 30 segundos. Pulsa **Buscar recomendaciones** para consultar hasta tres temas en el catálogo público. Verás la razón de cada sugerencia; nunca se añade un programa automáticamente.
7. Puedes cancelar desde el reproductor o la notificación. Un error deja la escucha pausada: pulsa Play para reintentar o **Escuchar sin análisis** para ese episodio. No hay reintentos de pago automáticos. Una preparación cancelada no puede iniciar después otro episodio.

Los análisis de betas anteriores siguen funcionando y no se reenvían automáticamente solo para añadir temas. Si quieres capítulos en un episodio ya preparado, abre **Anuncios y temas · Gemini Beta** desde el reproductor y pulsa **Actualizar anuncios y temas**: reutiliza su audio temporal mientras exista, solicita confirmación y vuelve a analizar anuncios y temas juntos.

También puedes seguir analizando manualmente una descarga desde **Anuncios · Gemini Beta**. Ese análisis conserva su confirmación individual.
Al completar el análisis, los tramos quedan guardados y **se saltan automáticamente** al reproducir el archivo analizado, incluso sin conexión mientras siga disponible. Puedes apagar los saltos por episodio, eliminar el análisis o usar **Deshacer último salto de anuncio** en el reproductor. Deshacer conserva ese tramo en las próximas escuchas hasta volver a analizar.

**Para ti** mantiene un historial local independiente de hasta 100 episodios escuchados desde su activación, con los temas y el nombre del programa. Los favoritos reciben más peso y se excluyen programas ya seguidos. El historial se puede borrar sin cambiar favoritos, progreso ni capítulos y no se exporta en la copia portátil. Desactivar la función detiene el aprendizaje y las búsquedas; conserva el historial hasta que lo borres. Al buscar se envían hasta tres temas al catálogo de Apple, sin audio, clave API ni historial completo. Las búsquedas no añaden llamadas a Gemini; usan temas ya obtenidos en el análisis. No se infieren atributos personales del oyente. Los resultados pueden no coincidir con sus gustos.

**Gemini no es infalible:** puede omitir anuncios, confundir conversación con publicidad o calcular mal los tiempos y saltarse contenido del podcast. La beta no garantiza detección ni sincronización precisa. Esta advertencia aparece en la app y antes de cada análisis.

La beta acepta claves con punto como `AQ.A…`, además de las claves anteriores, y diferencia los errores de formato, cifrado y almacenamiento sin mostrar la clave.

La clave se cifra mediante Android Keystore y se excluye de las copias de seguridad. Los resultados son locales y tampoco forman parte de la copia portátil. La preparación reproduce **exactamente el archivo analizado**, conservado en la caché privada temporal; nunca aplica esos tiempos a una segunda transmisión RSS, que podría insertar anuncios distintos. La caché conserva los episodios analizados pendientes, aunque haya más de tres. Al alcanzar el final de reproducción borra únicamente el audio temporal de ese episodio; conserva los resultados y las descargas manuales. Puedes liberar la caché desde los ajustes de Gemini. Los nuevos episodios requieren espacio libre suficiente; no se borran los pendientes para hacer sitio. Android puede liberar la caché, y entonces será necesario preparar otra vez el episodio. El audio temporal no aparece en Descargas ni se exporta. No se utiliza el micrófono.

La beta admite audio de hasta **512 MB y 9 horas**, sujeto también a los límites del modelo y de la cuenta. No analiza directos ni vídeo separado. El modo automático está desactivado inicialmente y solo analiza episodios seleccionados para escuchar, incluida la cola; no analiza todo el catálogo al actualizar el RSS. La autorización se solicita al activarlo y se puede retirar en Ajustes. Mientras prepara el episodio, mantiene una notificación de progreso y cancelación, también con la pantalla apagada. Android puede interrumpir el trabajo; el proceso no se reanuda solo después de cerrar o reiniciar la app. Cancelar no revierte el consumo de solicitudes ya enviadas. Un fallo conserva el análisis anterior y nunca inicia la escucha con resultados parciales. La preparación tiene un límite de 30 minutos.

Onda 2.0 conserva las pruebas de espera, caché, cancelación y cola, y añade validación de capítulos y subtemas, conservación de resultados al deshacer, identidad del audio en el reproductor y recomendaciones con historial opcional. Los nuevos capítulos generados y la calidad de las sugerencias deben comprobarse en un móvil con contenido real.

La integración usa la API oficial de Gemini, con subida temporal mediante Files API y respuesta JSON validada. Onda intenta borrar su archivo temporal de Google al terminar o fallar; si no puede hacerlo, Files API lo elimina automáticamente tras 48 horas. No hay servidor de Onda ni integración con SponsorBlock en esta beta.

La compilación y las pruebas automatizadas no sustituyen una prueba de precisión con audio real y una clave del usuario. En Onda 2.0, Gemini sigue siendo experimental y opcional.


