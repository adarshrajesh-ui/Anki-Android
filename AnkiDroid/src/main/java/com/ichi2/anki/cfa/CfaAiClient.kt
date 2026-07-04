// SPDX-License-Identifier: GPL-3.0-or-later
//
// CFA fork — mobile AI client. The phone never holds the OpenAI key; instead it
// POSTs to the server-side AI proxy (tools/cfa/ai_proxy.py) that runs alongside
// the self-hosted sync server, which does the LLM call and returns the draft
// WITH provenance. A shared bearer token (NOT the OpenAI key) gates access.
//
// Contract mirrors the desktop: on any failure (no proxy, no key on the server,
// network error) the result is source == "fallback" so the UI degrades cleanly
// and never blocks note entry.

package com.ichi2.anki.cfa

import com.ichi2.anki.libanki.Collection
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

/** One AI-Back result. [source] is "ai" on success, "fallback" otherwise. */
data class CfaAiResult(
    val ok: Boolean,
    val text: String,
    val source: String,
    val model: String?,
    val error: String?,
)

/** Injection seam for the HTTP POST so the client is unit-testable without network. */
fun interface CfaHttpPost {
    /** POST [jsonBody] to [url] with a Bearer [token]; return (httpStatus, body). */
    fun post(
        url: String,
        token: String,
        jsonBody: String,
    ): Pair<Int, String>
}

object CfaAiClient {
    const val URL_KEY = "cfa_ai_proxy_url"
    const val TOKEN_KEY = "cfa_ai_proxy_token"

    // Emulator -> host machine. Override via the synced col.conf keys above so a
    // real device can point at the LAN address of the machine running the proxy.
    const val DEFAULT_URL = "http://10.0.2.2:27702"
    const val DEFAULT_TOKEN = "cfa-ai-proxy"

    fun proxyUrl(col: Collection): String = (col.config.get<String>(URL_KEY) ?: "").ifBlank { DEFAULT_URL }

    fun proxyToken(col: Collection): String = (col.config.get<String>(TOKEN_KEY) ?: "").ifBlank { DEFAULT_TOKEN }

    /** Draft a card back via the proxy configured in [col]. Never throws. */
    fun tabfill(
        col: Collection,
        front: String,
        notetype: String = "",
        poster: CfaHttpPost = DEFAULT_POST,
    ): CfaAiResult = tabfill(proxyUrl(col), proxyToken(col), front, notetype, poster)

    /** Pure overload: draft a back from [front] against the proxy at [baseUrl]. */
    fun tabfill(
        baseUrl: String,
        token: String,
        front: String,
        notetype: String,
        poster: CfaHttpPost,
    ): CfaAiResult {
        if (front.isBlank()) {
            return CfaAiResult(false, "", "fallback", null, "empty_front")
        }
        val body =
            JSONObject()
                .put("front", front)
                .put("notetype", notetype)
                .toString()
        return try {
            val (status, resp) = poster.post("${baseUrl.trimEnd('/')}/cfa/tabfill", token, body)
            if (status != 200) {
                return CfaAiResult(false, "", "fallback", null, "http_$status")
            }
            val o = JSONObject(resp)
            CfaAiResult(
                ok = o.optBoolean("ok", false),
                text = o.optString("text", ""),
                source = o.optString("source", "fallback"),
                model = o.optString("model", "").ifBlank { null },
                error = o.optString("error", "").ifBlank { null },
            )
        } catch (e: Exception) {
            CfaAiResult(false, "", "fallback", null, "client_error:${e.javaClass.simpleName}")
        }
    }

    /** Default HTTP POST via HttpURLConnection (no extra dependency). */
    val DEFAULT_POST =
        CfaHttpPost { url, token, jsonBody ->
            val conn = (URL(url).openConnection() as HttpURLConnection)
            conn.requestMethod = "POST"
            conn.connectTimeout = 5_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            try {
                conn.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
                Pair(code, text)
            } finally {
                conn.disconnect()
            }
        }
}
