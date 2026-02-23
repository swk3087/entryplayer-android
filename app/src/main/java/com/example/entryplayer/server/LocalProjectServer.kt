package com.example.entryplayer.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection

/**
 * A simple HTTP server that serves two namespaces:
 *  - `/player/` – files from the app's assets folder (HTML/JS/CSS for Entry player)
 *  - `/project/` – files from the extracted project directory (JSON, images, sounds)
 *
 * This server listens on the loopback interface (127.0.0.1) and a fixed port. It does not accept
 * external connections.
 */
class LocalProjectServer(
    private val context: Context,
    port: Int
) : NanoHTTPD("127.0.0.1", port) {

    @Volatile
    private var projectRoot: File? = null

    /**
     * Set the root directory of the extracted project. When set, the server will serve files from
     * this directory under the `/project/` path.
     */
    fun setProjectRoot(root: File) {
        projectRoot = root
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri ?: "/"
        return try {
            when {
                uri.startsWith("/player/") -> serveAsset(uri.removePrefix("/player/"))
                uri.startsWith("/project/") -> serveProjectFile(uri.removePrefix("/project/"))
                uri == "/" -> newFixedLengthResponse(
                    Response.Status.OK, "text/plain", "Entry Player Local Server"
                )
                else -> newFixedLengthResponse(
                    Response.Status.NOT_FOUND, "text/plain", "Not Found"
                )
            }
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR, "text/plain", "Server Error: ${e.message}"
            )
        }
    }

    /**
     * Serve a file from the assets under the `/player/` path.
     */
    private fun serveAsset(assetPath: String): Response {
        val clean = assetPath.trimStart('/')
        val mime = guessMimeType(clean)
        val input = context.assets.open(clean)
        val response = newChunkedResponse(Response.Status.OK, mime, input)
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    /**
     * Serve a file from the extracted project directory under the `/project/` path.
     */
    private fun serveProjectFile(projectPath: String): Response {
        val root = projectRoot ?: return newFixedLengthResponse(
            Response.Status.NOT_FOUND, "text/plain", "No project loaded"
        )
        val file = safeProjectFile(root, projectPath)
        if (!file.exists() || !file.isFile) {
            return newFixedLengthResponse(
                Response.Status.NOT_FOUND, "text/plain", "File not found"
            )
        }
        val mime = guessMimeType(file.name)
        val input = FileInputStream(file)
        val response = newChunkedResponse(Response.Status.OK, mime, input)
        response.addHeader("Cache-Control", "no-store")
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }

    /**
     * Prevent path traversal by ensuring file stays within the project root directory.
     */
    private fun safeProjectFile(root: File, path: String): File {
        val f = File(root, path.trimStart('/'))
        val rootPath = root.canonicalPath
        val filePath = f.canonicalPath
        if (!filePath.startsWith(rootPath)) {
            throw SecurityException("Path traversal blocked")
        }
        return f
    }

    /**
     * Guess MIME type based on file extension.
     */
    private fun guessMimeType(name: String): String {
        return URLConnection.guessContentTypeFromName(name) ?: when {
            name.endsWith(".js") -> "application/javascript"
            name.endsWith(".json") -> "application/json"
            name.endsWith(".html") -> "text/html"
            name.endsWith(".css") -> "text/css"
            name.endsWith(".svg") -> "image/svg+xml"
            name.endsWith(".wasm") -> "application/wasm"
            else -> "application/octet-stream"
        }
    }
}