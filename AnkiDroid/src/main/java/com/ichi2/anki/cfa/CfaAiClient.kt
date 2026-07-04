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

/**
 * One AI tab-fill result. [source] is "ai" on success, "fallback" otherwise;
 * [target] is the side that was generated ("back" or "front"), or null.
 */
data class CfaAiResult(
    val ok: Boolean,
    val text: String,
    val target: String?,
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

    /**
     * Bidirectional tab-fill via the proxy configured in [col]: send both sides,
     * the server generates whichever is empty (front->back or back->front).
     * Never throws.
     */
    fun fill(
        col: Collection,
        front: String,
        back: String,
        poster: CfaHttpPost = DEFAULT_POST,
    ): CfaAiResult = fill(proxyUrl(col), proxyToken(col), front, back, poster)

    /** Pure overload: POST {front, back} to the proxy at [baseUrl]; parse the result. */
    fun fill(
        baseUrl: String,
        token: String,
        front: String,
        back: String,
        poster: CfaHttpPost,
    ): CfaAiResult {
        if (front.isBlank() == back.isBlank()) {
            // both empty or both filled -> nothing to generate
            return CfaAiResult(false, "", null, "fallback", null, "nothing_to_fill")
        }
        val body =
            JSONObject()
                .put("front", front)
                .put("back", back)
                .toString()
        return try {
            val (status, resp) = poster.post("${baseUrl.trimEnd('/')}/cfa/tabfill", token, body)
            if (status != 200) {
                return CfaAiResult(false, "", null, "fallback", null, "http_$status")
            }
            val o = JSONObject(resp)
            CfaAiResult(
                ok = o.optBoolean("ok", false),
                text = o.optString("text", ""),
                target = o.optString("target", "").ifBlank { null },
                source = o.optString("source", "fallback"),
                model = o.optString("model", "").ifBlank { null },
                error = o.optString("error", "").ifBlank { null },
            )
        } catch (e: Exception) {
            CfaAiResult(false, "", null, "fallback", null, "client_error:${e.javaClass.simpleName}")
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
