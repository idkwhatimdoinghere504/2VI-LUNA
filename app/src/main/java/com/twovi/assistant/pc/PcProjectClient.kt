package com.twovi.assistant.pc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class SharedRoot(val id: String, val name: String)
data class ProjectEntry(val name: String, val path: String, val folder: Boolean, val bytes: Long = 0L)
data class PcSkill(val name: String, val description: String, val folder: String)
data class ProjectDocument(val name: String, val path: String, val text: String)

class PcProjectClient {
    private val client = OkHttpClient.Builder()
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun roots(address: String, code: String): List<SharedRoot> = withContext(Dispatchers.IO) {
        val json = get(address, code, "/api/roots")
        val roots = json.optJSONArray("roots") ?: throw IOException("The PC bridge did not return project folders.")
        (0 until roots.length()).mapNotNull { index ->
            roots.optJSONObject(index)?.let { SharedRoot(it.optString("id"), it.optString("name")) }
        }
    }

    suspend fun tree(address: String, code: String, root: String, path: String): List<ProjectEntry> = withContext(Dispatchers.IO) {
        val query = "?root=${encode(root)}&path=${encode(path)}"
        val items = get(address, code, "/api/tree$query").optJSONArray("items")
            ?: throw IOException("The PC bridge returned no file list.")
        (0 until items.length()).mapNotNull { index ->
            items.optJSONObject(index)?.let { item ->
                ProjectEntry(
                    name = item.optString("name"),
                    path = item.optString("path"),
                    folder = item.optString("type") == "folder",
                    bytes = item.optLong("bytes"),
                )
            }
        }
    }

    suspend fun file(address: String, code: String, root: String, path: String): ProjectDocument = withContext(Dispatchers.IO) {
        val json = get(address, code, "/api/file?root=${encode(root)}&path=${encode(path)}")
        ProjectDocument(json.optString("name"), json.optString("path"), json.optString("text"))
    }

    suspend fun skills(address: String, code: String): List<PcSkill> = withContext(Dispatchers.IO) {
        val skills = get(address, code, "/api/skills").optJSONArray("skills")
            ?: throw IOException("The PC bridge returned no skill list.")
        (0 until skills.length()).mapNotNull { index ->
            skills.optJSONObject(index)?.let {
                PcSkill(it.optString("name"), it.optString("description"), it.optString("folder"))
            }
        }
    }

    private fun get(address: String, code: String, endpoint: String): JSONObject {
        val base = normalizeAddress(address)
        val request = Request.Builder()
            .url(base + endpoint)
            .header("Authorization", "Bearer ${code.trim()}")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val error = try { JSONObject(body).optString("error") } catch (_: Exception) { "" }
                throw IOException(error.ifBlank { "PC bridge returned HTTP ${response.code}. Check the address and pairing code." })
            }
            return try { JSONObject(body) } catch (exception: Exception) {
                throw IOException("The PC bridge sent an invalid response.", exception)
            }
        }
    }

    private fun normalizeAddress(value: String): String {
        val cleaned = value.trim().trimEnd('/')
        if (!cleaned.startsWith("http://") && !cleaned.startsWith("https://")) {
            throw IOException("Enter the full PC address, including http://")
        }
        val uri = try { java.net.URI(cleaned) } catch (_: Exception) { throw IOException("Check the PC address format.") }
        if (uri.userInfo != null || uri.query != null || uri.fragment != null || uri.port !in 1..65535) {
            throw IOException("Enter the local PC address and port shown by the companion window.")
        }
        val host = uri.host?.lowercase() ?: throw IOException("Check the PC address format.")
        val secondOctet = host.removePrefix("172.").substringBefore('.').toIntOrNull()
        val privateHost = host == "localhost" || host == "127.0.0.1" || host.startsWith("10.") ||
            host.startsWith("192.168.") || host.startsWith("169.254.") ||
            (host.startsWith("172.") && secondOctet != null && secondOctet in 16..31)
        if (!privateHost) throw IOException("Use the private Wi-Fi address printed by the PC companion.")
        return cleaned
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
}
