package com.example.entryplayer.ent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Rewrites asset paths within `project.json` for offline use.
 *
 * Entry projects reference images and sounds via relative or absolute paths. When serving projects
 * via the embedded HTTP server, these paths must be rewritten to point to the server's `/project/`
 * endpoint. This helper walks the JSON tree recursively and replaces any string that looks like
 * an asset file path with the appropriate URL. Only values that end in common image/audio
 * extensions are modified; HTTP/HTTPS/data URIs are left untouched.
 */
object ProjectRewriter {

    /**
     * Rewrite asset paths in the given `project.json` file so that assets are served from
     * the provided local base URL (e.g. `http://127.0.0.1:18080/project/`).
     */
    fun rewriteProjectJsonForLocalServer(
        projectJson: File,
        projectRoot: File,
        localBaseUrl: String
    ) {
        val text = projectJson.readText()
        val root = JSONObject(text)
        rewriteJsonRecursively(root, projectRoot, localBaseUrl)
        projectJson.writeText(root.toString())
    }

    /**
     * Recursively walk a JSON object or array and rewrite any string values representing asset paths.
     */
    private fun rewriteJsonRecursively(
        any: Any?,
        projectRoot: File,
        localBaseUrl: String
    ) {
        when (any) {
            is JSONObject -> {
                val keys = any.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val v = any.get(k)
                    when (v) {
                        is JSONObject, is JSONArray -> rewriteJsonRecursively(v, projectRoot, localBaseUrl)
                        is String -> {
                            val newValue = rewriteAssetPathIfNeeded(v, projectRoot, localBaseUrl)
                            if (newValue != v) any.put(k, newValue)
                        }
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until any.length()) {
                    val v = any.get(i)
                    when (v) {
                        is JSONObject, is JSONArray -> rewriteJsonRecursively(v, projectRoot, localBaseUrl)
                        is String -> {
                            val newValue = rewriteAssetPathIfNeeded(v, projectRoot, localBaseUrl)
                            if (newValue != v) any.put(i, newValue)
                        }
                    }
                }
            }
        }
    }

    /**
     * If the given string appears to be an asset path (image/audio), rewrite it to the local server URL.
     */
    private fun rewriteAssetPathIfNeeded(
        value: String,
        projectRoot: File,
        localBaseUrl: String
    ): String {
        // Already a URL? Leave untouched.
        if (value.startsWith("http://") || value.startsWith("https://") || value.startsWith("data:")) {
            return value
        }
        val lower = value.lowercase()
        val looksLikeAsset = listOf(
            ".png", ".jpg", ".jpeg", ".gif", ".webp", ".svg",
            ".mp3", ".wav", ".ogg", ".m4a"
        ).any { lower.endsWith(it) }
        if (!looksLikeAsset) return value
        // Normalize path relative to project root
        val normalized = value.removePrefix("./").removePrefix("/")
        val f = File(projectRoot, normalized)
        if (f.exists()) {
            return localBaseUrl + normalized.replace("\\", "/")
        }
        // Fallback: search by filename
        val byName = projectRoot.walkTopDown().firstOrNull {
            it.isFile && it.name == File(normalized).name
        }
        return if (byName != null) {
            val rel = byName.relativeTo(projectRoot).path.replace("\\", "/")
            localBaseUrl + rel
        } else {
            value
        }
    }
}