package app.trazo.offline

import android.util.Log
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Servidor HTTP mínimo en 127.0.0.1 que sirve archivos de estilo.
 *
 * MapLibre en Android no descarga mapas sin conexión con un estilo
 * `file://` (lo manda al cliente HTTP y falla: maplibre-gl-native#609), así
 * que el estilo de la descarga se sirve desde aquí por `http://127.0.0.1`.
 * Solo escucha en el propio móvil y solo sirve los archivos registrados.
 */
object LocalStyleServer {

    private const val TAG = "TrazoStyleServer"
    private val files = mutableMapOf<String, File>()
    private var socket: ServerSocket? = null

    /** URL http://127.0.0.1:puerto/nombre para [file]. */
    @Synchronized
    fun urlFor(file: File): String {
        val s = socket ?: start()
        files[file.name] = file
        return "http://127.0.0.1:${s.localPort}/${file.name}"
    }

    private fun start(): ServerSocket {
        val s = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        socket = s
        thread(name = "trazo-style-server", isDaemon = true) {
            while (!s.isClosed) {
                runCatching {
                    s.accept().use { client ->
                        val request = client.getInputStream().bufferedReader().readLine().orEmpty()
                        val name = request.split(" ").getOrNull(1)?.trimStart('/')?.substringBefore('?').orEmpty()
                        val file = synchronized(this) { files[name] }
                        val out = client.getOutputStream()
                        if (file == null || !file.exists()) {
                            out.write("HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        } else {
                            val body = file.readBytes()
                            out.write(
                                ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n" +
                                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray()
                            )
                            out.write(body)
                        }
                        out.flush()
                    }
                }.onFailure { Log.w(TAG, "Petición fallida: ${it.message}") }
            }
        }
        return s
    }
}
