# LibreCode

Chat Android sencillo en Kotlin con Google Gemini, Anthropic, DeepSeek, Kimi y OpenAI. El menú ☰ de la izquierda permite iniciar un chat y abrir el historial local. Mantén pulsado un chat en el historial para borrarlo. Las conversaciones se guardan en un archivo privado, **sin cifrar**, después de recibir una respuesta; la conversación pendiente se puede perder si se cierra la app. Al abrir un chat anterior se usa el proveedor configurado actualmente: su contenido se envía a ese proveedor cuando se envía un nuevo mensaje.

El texto del asistente se muestra en Markdown: cursiva, negrita, listas, enlaces, tablas GFM y bloques de código. No hay streaming, adjuntos ni herramientas por ahora.

La configuración del proveedor y la API key se guardan **en texto plano** en SharedPreferences privadas, solo para pruebas. No subas claves al repositorio. Desinstalar la app elimina el historial y la configuración; no hay sincronización ni copia de seguridad.

Selecciona el protocolo, nombre, URL HTTPS completa y clave. La URL debe corresponder al protocolo: para servicios compatibles con `/chat/completions`, selecciona **OpenAI · Chat Completions**, no OpenAI · Responses. Google GenerateContent incluye el modelo en la URL. Los otros modelos se cambian en el diálogo. Las URLs personalizadas reciben tu API key: confía únicamente en servidores que controles.

Android 8+ (API 26); edge-to-edge con márgenes del sistema y teclado. Interfaz nativa inspirada en la estética del chat de Claude; Markdown usa Markwon.

## Compilación y releases

Cada push a `main` y cada ejecución manual compilan `assembleRelease` con R8 en modo completo, reducción de recursos, filtrado de idiomas y zipalign; firman y publican `LibreCode-release.apk`. En repositorios con permisos restringidos habilita **Settings → Actions → General → Workflow permissions → Read and write permissions**.

Para conservar la misma firma entre compilaciones, configura los secretos `RELEASE_KEYSTORE_BASE64` (keystore en base64), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD`. Si no se configuran se genera una clave release temporal por ejecución: **hay que desinstalar la versión anterior para instalar la nueva**, lo cual borra historial y configuración. No es una clave debug. Para compilar localmente con Gradle 8.9, JDK 17 y Android SDK 35, ejecuta `gradle :app:assembleRelease`; sin las variables `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` el APK local queda sin firmar.
