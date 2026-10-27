package jp.ikegami.roastscale.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import jp.ikegami.roastscale.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class AvailableRelease(
    val tag: String,
    val notes: String,
    val apkUrl: String,
    val shaUrl: String,
    val versionCode: Int
)

object ReleaseUpdater {
    private const val API = "https://api.github.com/repos/IKEGAMI-99/ROAST-SCALE/releases/latest"

    private fun request(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 20_000
        readTimeout = 50_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "ROAST-SCALE-Android")
    }

    private fun parseVersionCode(tag: String): Int? {
        val match = Regex("^v?(\\d+)\\.(\\d+)\\.(\\d+)$").matchEntire(tag) ?: return null
        val (major, minor, patch) = match.destructured
        return (major.toIntOrNull() ?: return null) * 10000 +
            (minor.toIntOrNull() ?: return null) * 100 + (patch.toIntOrNull() ?: return null)
    }

    suspend fun check(): AvailableRelease? = withContext(Dispatchers.IO) {
        val conn = request(API)
        try {
            if (conn.responseCode == 404) return@withContext null
            require(conn.responseCode == 200) { "GitHub API: " + conn.responseCode }
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val code = parseVersionCode(tag) ?: return@withContext null
            if (code <= BuildConfig.VERSION_CODE) return@withContext null
            val assets = json.getJSONArray("assets")
            var apk = ""
            var checksum = ""
            for (index in 0 until assets.length()) {
                val item = assets.getJSONObject(index)
                when (item.getString("name")) {
                    "roast-scale.apk" -> apk = item.getString("browser_download_url")
                    "roast-scale.apk.sha256" -> checksum = item.getString("browser_download_url")
                }
            }
            if (apk.isBlank() || checksum.isBlank()) null
            else AvailableRelease(tag, json.optString("body").take(2000), apk, checksum, code)
        } finally {
            conn.disconnect()
        }
    }

    suspend fun download(context: Context, release: AvailableRelease, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            val target = File(dir, "roast-scale.apk")
            val temp = File(dir, "roast-scale.download")
            val checksumConnection = request(release.shaUrl)
            val expectedSha = try {
                require(checksumConnection.responseCode == 200)
                checksumConnection.inputStream.bufferedReader().use { it.readText() }
                    .trim().split(Regex("\\s+")).first().lowercase()
            } finally {
                checksumConnection.disconnect()
            }
            require(expectedSha.matches(Regex("[a-f0-9]{64}"))) { "SHA256形式が不正です" }
            val connection = request(release.apkUrl)
            try {
                require(connection.responseCode == 200) { "APK取得失敗: " + connection.responseCode }
                val length = connection.contentLengthLong
                val digest = MessageDigest.getInstance("SHA-256")
                var done = 0L
                temp.outputStream().buffered().use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(128 * 1024)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            done += n
                            if (length > 0) onProgress((done * 100 / length).toInt().coerceIn(0, 100))
                        }
                    }
                }
                require(length <= 0L || done == length) { "APKが途中で切れました" }
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                require(actual == expectedSha) { "APK SHA256が一致しません" }
                val archive = if (Build.VERSION.SDK_INT >= 33)
                    context.packageManager.getPackageArchiveInfo(temp.absolutePath, PackageManager.PackageInfoFlags.of(0L))
                else context.packageManager.getPackageArchiveInfo(temp.absolutePath, 0)
                require(archive?.packageName == context.packageName) { "パッケージ名が異なるAPKです" }
                require(archive.longVersionCode > BuildConfig.VERSION_CODE) { "更新版ではありません" }
                if (target.exists()) target.delete()
                require(temp.renameTo(target)) { "APK保存に失敗しました" }
                target
            } finally {
                connection.disconnect()
                if (temp.exists()) temp.delete()
            }
        }

    /** Android checks the signature against the installed app before overwriting. */
    fun install(context: Context, apk: File) {
        if (Build.VERSION.SDK_INT >= 26 && !context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + context.packageName))
            settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", apk)
        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
