package com.librecode.app

import android.content.Context
import android.util.Base64
import java.io.File
import java.security.KeyStore
import java.security.cert.Certificate
import java.util.concurrent.TimeUnit

/** Internal-only executable runner; argument arrays are passed directly, never through a shell. */
internal class NativeTools(private val context: Context) {
    private val allowed = setOf("adb", "fastboot", "curl", "sqlite3", "etc1tool", "hprof-conv", "e2fsdroid", "sload_f2fs", "mke2fs", "make_f2fs", "make_f2fs_casefold")
    private fun caBundle(): File {
        val file = File(context.filesDir, "system-ca-bundle.pem")
        if (!file.exists()) {
            val store = KeyStore.getInstance("AndroidCAStore").apply { load(null) }
            val temp = File(context.filesDir, "system-ca-bundle.tmp")
            temp.bufferedWriter(Charsets.US_ASCII).use { writer ->
                val aliases = store.aliases()
                while (aliases.hasMoreElements()) {
                    val cert: Certificate = store.getCertificate(aliases.nextElement()) ?: continue
                    writer.write("-----BEGIN CERTIFICATE-----\n")
                    writer.write(Base64.encodeToString(cert.encoded, Base64.DEFAULT)); writer.write("-----END CERTIFICATE-----\n")
                }
            }
            check(temp.renameTo(file)) { "No se pudo preparar certificados" }
        }
        return file
    }
    fun run(tool: String, args: List<String>): String {
        require(tool in allowed) { "Herramienta no disponible" }
        require(args.size <= 128 && args.all { it.length <= 4096 && !it.contains('\u0000') }) { "Argumentos inválidos" }
        val exe = File(context.applicationInfo.nativeLibraryDir, "lib" + tool.replace('-', '_') + ".so")
        require(exe.exists()) { "El ejecutable $tool no está disponible para esta arquitectura" }
        val builder = ProcessBuilder(listOf(exe.absolutePath) + args).redirectErrorStream(true).directory(context.filesDir)
        builder.environment()["HOME"] = context.filesDir.absolutePath
        builder.environment()["TMPDIR"] = context.cacheDir.absolutePath
        if (tool == "curl") builder.environment()["CURL_CA_BUNDLE"] = caBundle().absolutePath
        val process = builder.start()
        val output = StringBuilder(); val limit = 65536
        val reader = Thread {
            process.inputStream.bufferedReader(Charsets.UTF_8).use { r ->
                val buffer = CharArray(4096)
                while (true) { val n = r.read(buffer); if (n < 0) break; synchronized(output) { val keep = minOf(n, limit - output.length); if (keep > 0) output.append(buffer, 0, keep) } }
            }
        }
        reader.isDaemon = true; reader.start()
        val finished = try { process.waitFor(45, TimeUnit.SECONDS) } catch (e: InterruptedException) { process.destroyForcibly(); Thread.currentThread().interrupt(); throw e }
        if (!finished) { process.destroyForcibly(); process.waitFor(3, TimeUnit.SECONDS) }
        reader.join(2000)
        val text = synchronized(output) { output.toString() }
        return text + if (finished) "\n[Código de salida: ${process.exitValue()}]" else "\n[Tiempo agotado; proceso cancelado]"
    }
}
