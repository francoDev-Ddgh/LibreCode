# LibreCode

Chat Android sencillo en Kotlin con proveedores Google Gemini, Anthropic, DeepSeek, Kimi y OpenAI. La conversación existe solo en memoria y desaparece al cerrar la actividad; no hay historial de chats. La configuración del proveedor, URL completa y API key se guarda **en texto plano** en SharedPreferences privadas de la aplicación, solo para pruebas. No subas claves al repositorio; desinstalar la app elimina la configuración. No hay sincronización ni copia de seguridad.

Al abrir la aplicación selecciona el protocolo, nombre, URL HTTPS completa del endpoint y clave. La URL debe corresponder al protocolo seleccionado; para servicios compatibles con `/chat/completions`, selecciona **OpenAI · Chat Completions**, no OpenAI · Responses; Google GenerateContent incluye el modelo en la URL. Los otros modelos se pueden cambiar en el diálogo. El historial de la conversación activa se envía al proveedor en cada petición. No se admiten todavía adjuntos, herramientas ni streaming. URLs personalizadas reciben tu API key: confía únicamente en servidores que controles.

Android 8+ (API 26); edge-to-edge con márgenes del sistema y teclado. La app usa recursos y vistas nativas, sin dependencias de interfaz externas.

## Compilación y releases

Cada push a `main` y cada ejecución manual del workflow compilan `assembleRelease` con R8 y reducción de recursos, firman el APK y publican una release con `LibreCode-release.apk`. En repositorios con permisos restringidos habilita **Settings → Actions → General → Workflow permissions → Read and write permissions**.

Para conservar la misma firma entre compilaciones, configura los secretos `RELEASE_KEYSTORE_BASE64` (archivo keystore codificado en base64), `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` y `RELEASE_KEY_PASSWORD`. Si no se configuran se genera una clave release temporal por ejecución: el APK es instalable pero **no se podrá actualizar encima de otra compilación con distinta firma**. No es una clave debug. Para compilar localmente con Gradle 8.9, JDK 17 y Android SDK 35, ejecuta `gradle :app:assembleRelease`; sin las variables `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD` el APK local queda sin firmar.
