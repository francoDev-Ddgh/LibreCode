# LibreCode

Chat Android sencillo en Kotlin con Google Gemini, Anthropic, DeepSeek, Kimi, OpenAI y Ollama nativo. El botón ☰ abre un panel lateral desde la izquierda con el historial local, el botón de nuevo chat y **Configuraciones**. Desde Configuraciones puedes elegir **Proveedores**, **Ajustes generales** o **Cambios generales**; las dos últimas pantallas están reservadas para el futuro. Mantén pulsado un chat en el panel para borrarlo. Las conversaciones se guardan en un archivo privado, **sin cifrar**, después de recibir una respuesta; la conversación pendiente se puede perder si se cierra la app. Al abrir un chat anterior se usa el proveedor configurado actualmente: su contenido se envía a ese proveedor cuando se envía un nuevo mensaje.

El texto del asistente se muestra en Markdown: cursiva, negrita, listas, enlaces, tablas GFM y bloques de código. No hay streaming, adjuntos ni herramientas por ahora.

La configuración del proveedor y la API key se guardan **en texto plano** en SharedPreferences privadas, solo para pruebas. No subas claves al repositorio. Desinstalar la app elimina el historial y la configuración; no hay sincronización ni copia de seguridad.

En Proveedores puedes configurar los proveedores predefinidos o agregar uno personalizado seleccionando el protocolo Google Gemini, OpenAI Chat Completions, OpenAI Responses o Anthropic Messages. Cada proveedor conserva su propia configuración y clave en texto plano; «Guardar y usar» lo convierte en activo. Puedes eliminar proveedores personalizados. Selecciona el protocolo, nombre, URL HTTPS completa y clave. La URL debe corresponder al protocolo: para servicios compatibles con `/chat/completions`, selecciona **OpenAI · Chat Completions**, no OpenAI · Responses. Google GenerateContent incluye el modelo en la URL. Los otros modelos se cambian en el diálogo. Las URLs personalizadas reciben tu API key: confía únicamente en servidores que controles.

Android 8+ (API 26); edge-to-edge con márgenes del sistema y teclado. Interfaz nativa inspirada en la estética del chat de Claude; Markdown usa Markwon.

## Compilación y releases

Cada push a `main` y cada ejecución manual compilan `assembleRelease` con R8 en modo completo, reducción de recursos, filtrado de idiomas y zipalign; firman y publican `LibreCode-release.apk`. En repositorios con permisos restringidos habilita **Settings → Actions → General → Workflow permissions → Read and write permissions**.

Para conservar la misma firma entre compilaciones, configura los secretos `RELEASE_KEYSTORE_BASE64` (keystore en base64), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD`. Si no se configuran se genera una clave release temporal por ejecución: **hay que desinstalar la versión anterior para instalar la nueva**, lo cual borra historial y configuración. No es una clave debug. Para compilar localmente con Gradle 8.9, JDK 17 y Android SDK 35, ejecuta `gradle :app:assembleRelease`; sin las variables `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` el APK local queda sin firmar.

## Modelos y Ollama

En Configuraciones → Proveedores, configura nombre, URL completa y API key; la consulta de modelos empieza automáticamente. Agrega o quita modelos con +/− y guarda. También puedes introducir un ID manual si el servidor no expone listado. El botón de la esquina superior derecha permite cambiar entre todos los modelos seleccionados sin borrar la conversación. Al enviar un mensaje, el proveedor activo recibe la conversación actual; tenlo en cuenta antes de cambiar de proveedor. Los nombres y especificaciones provienen del catálogo cuando están disponibles, o se generan a partir del ID.

La consulta usa Gemini `/models`, Anthropic `/models`, OpenAI-compatible `/models` u Ollama `/api/tags`, derivados de la URL de generación. Gemini requiere una URL que incluya `/models/MODELO:generateContent`; Anthropic termina en `/messages`, OpenAI-compatible en `/chat/completions` o `/responses`, Ollama en `/api/chat`. Algunos servidores y claves no permiten listar modelos o usan rutas diferentes: agrega un ID manual si falla.

Ollama no necesita API key por defecto. Configura la IP local real de tu servidor, por ejemplo `http://192.168.1.20:11434/api/chat`, y habilita que Ollama escuche en la red y el firewall correspondiente. `localhost` en Android es el propio teléfono, no la computadora. Para Ollama se permite HTTP únicamente hacia direcciones privadas/locales o hosts `.local`; HTTP no cifra datos ni claves, así que úsalo solo en una red de confianza. Para otros proveedores se exige HTTPS.
