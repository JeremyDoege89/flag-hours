package com.jeremy.flaghours.data.remote

import java.io.File
import java.time.Instant

/** Keeps the last raw Open Labor Project responses (never the API key) for troubleshooting. */
class OlpDebugLog(val file: File, private val maxEntries: Int = 40, private val maxBody: Int = 30_000) {

    @Synchronized
    fun record(url: String, status: Int, headers: Map<String, String>, body: String) {
        val interesting = headers.filterKeys { it.startsWith("x-ratelimit") || it == "retry-after" || it == "content-type" || it == "cf-ray" }
        val entry = buildString {
            append("=== ").append(Instant.now()).append(" GET ").append(url).append('\n')
            append("HTTP ").append(status).append('\n')
            interesting.forEach { (k, v) -> append(k).append(": ").append(v).append('\n') }
            append(if (body.length > maxBody) body.take(maxBody) + "\n…(truncated, ${body.length} chars)" else body).append('\n')
        }
        val existing = if (file.exists()) file.readText() else ""
        val entries = (existing.split(SEP).filter { it.isNotBlank() } + entry).takeLast(maxEntries)
        file.parentFile?.mkdirs()
        file.writeText(entries.joinToString(SEP))
    }

    fun read(): String = if (file.exists()) file.readText().replace(SEP, "\n") else ""

    fun clear() {
        file.delete()
    }

    private companion object {
        const val SEP = "\n\u001E\n"
    }
}
