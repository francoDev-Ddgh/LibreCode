package com.librecode.app

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.Locale

/** Minimal ADB host-server protocol client. This does not start an ADB server or pair devices. */
internal class AdbServerClient(private val port: Int = 5037) {
    data class Device(val serial: String, val state: String, val details: String)
    data class ShellResult(val output: String, val truncated: Boolean)

    private fun connect(): Socket = Socket().apply {
        try {
            connect(InetSocketAddress("127.0.0.1", this@AdbServerClient.port), 3000)
            soTimeout = 15000
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun devices(): List<Device> = connect().use { socket ->
        sendService(socket.getOutputStream(), "host:devices-l")
        checkStatus(socket.getInputStream())
        readLengthPrefixed(socket.getInputStream()).lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
            val fields = line.trim().split(Regex("\\s+"), limit = 3)
            if (fields.size < 2) null else Device(fields[0], fields[1], fields.getOrElse(2) { "" })
        }.toList()
    }

    /** Selects only a currently authorized device, then sends a non-interactive shell service. */
    fun shell(serial: String, command: String, maxBytes: Int = 65536): ShellResult {
        require(serial.isNotBlank() && serial.none { it == '\u0000' || it == '\n' || it == '\r' }) { "Identificador ADB inválido" }
        require(command.isNotBlank() && !command.contains('\u0000')) { "Comando ADB inválido" }
        require(maxBytes in 1..1048576)
        require(devices().any { it.serial == serial && it.state == "device" }) { "El dispositivo ADB no está conectado o autorizado" }
        return connect().use { socket ->
            val input = socket.getInputStream()
            val output = socket.getOutputStream()
            sendService(output, "host:transport:$serial")
            checkStatus(input)
            sendService(output, "shell:$command")
            checkStatus(input)
            val result = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var truncated = false
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                val remaining = maxBytes - result.size()
                if (n > remaining) { if (remaining > 0) result.write(buffer, 0, remaining); truncated = true; break }
                result.write(buffer, 0, n)
            }
            ShellResult(result.toString(StandardCharsets.UTF_8.name()), truncated)
        }
    }

    private fun sendService(output: OutputStream, service: String) {
        val bytes = service.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= 0xffff) { "Solicitud ADB demasiado larga" }
        output.write(String.format(Locale.US, "%04x", bytes.size).toByteArray(StandardCharsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun checkStatus(input: InputStream) {
        when (readExactly(input, 4).toString(StandardCharsets.US_ASCII)) {
            "OKAY" -> return
            "FAIL" -> throw IllegalStateException("Error ADB: ${readLengthPrefixed(input).take(500)}")
            else -> throw IllegalStateException("Respuesta inesperada del servidor ADB")
        }
    }

    private fun readLengthPrefixed(input: InputStream): String {
        val length = readExactly(input, 4).toString(StandardCharsets.US_ASCII).toIntOrNull(16)
            ?: throw IllegalStateException("Longitud ADB inválida")
        return readExactly(input, length).toString(StandardCharsets.UTF_8)
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray {
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val n = input.read(bytes, offset, length - offset)
            if (n < 0) throw EOFException("El servidor ADB cerró la conexión")
            offset += n
        }
        return bytes
    }
}
