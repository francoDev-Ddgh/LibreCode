package com.librecode.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AdbServerClientTest {
    private fun request(input: java.io.InputStream): String {
        fun read(n: Int): ByteArray {
            val bytes = ByteArray(n); var offset = 0
            while (offset < n) { val count = input.read(bytes, offset, n - offset); check(count > 0); offset += count }
            return bytes
        }
        val length = String(read(4), StandardCharsets.US_ASCII).toInt(16)
        return String(read(length), StandardCharsets.UTF_8)
    }

    @Test fun listAndSelectAuthorizedDevice() {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val worker = executor.submit {
                    repeat(2) {
                        server.accept().use { socket ->
                            assertEquals("host:devices-l", request(socket.getInputStream()))
                            val list = "192.168.1.2:5555\tdevice product:test\noffline123\toffline\n"
                            val data = list.toByteArray(StandardCharsets.UTF_8)
                            socket.getOutputStream().write(("OKAY" + "%04x".format(data.size)).toByteArray(StandardCharsets.US_ASCII))
                            socket.getOutputStream().write(data)
                        }
                    }
                    server.accept().use { socket ->
                        assertEquals("host:transport:192.168.1.2:5555", request(socket.getInputStream()))
                        socket.getOutputStream().write("OKAY".toByteArray())
                        assertEquals("shell:getprop ro.product.model", request(socket.getInputStream()))
                        socket.getOutputStream().write("OKAYPixel\n".toByteArray())
                    }
                }
                val client = AdbServerClient(server.localPort)
                assertEquals(2, client.devices().size)
                val result = client.shell("192.168.1.2:5555", "getprop ro.product.model")
                assertEquals("Pixel\n", result.output)
                assertTrue(!result.truncated)
                worker.get(5, TimeUnit.SECONDS)
            } finally { executor.shutdownNow() }
        }
    }
}
