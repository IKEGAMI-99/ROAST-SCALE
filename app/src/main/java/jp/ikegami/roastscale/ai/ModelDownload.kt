package jp.ikegami.roastscale.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** Resume-capable download of the public E4B model to private app storage. */
object ModelDownload {
    private const val URL_MODEL =
        "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm"
    private const val MIN_SIZE = 3_000_000_000L
    fun file(context: Context) = File(context.filesDir, "gemma-4-E4B-it.litertlm")
    fun isReady(context: Context): Boolean = file(context).length() > MIN_SIZE

    suspend fun download(context: Context, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val target = file(context)
        if (isReady(context)) return@withContext
        val part = File(context.filesDir, "gemma-4-E4B-it.litertlm.part")
        var existing = part.length()
        val connection = (URL(URL_MODEL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "ROAST-SCALE/1.0")
            if (existing > 0) setRequestProperty("Range", "bytes=" + existing + "-")
        }
        try {
            val status = connection.responseCode
            require(status == 200 || status == 206) { "Model HTTP " + status }
            if (status == 200) {
                existing = 0L
                part.delete()
            }
            val total = connection.contentLengthLong.takeIf { it > 0 }?.plus(existing) ?: -1L
            RandomAccessFile(part, "rw").use { output ->
                output.seek(existing)
                connection.inputStream.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var downloaded = existing
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        output.write(buffer, 0, n)
                        downloaded += n
                        if (total > 0L) onProgress((downloaded * 100 / total).toInt().coerceIn(0, 99))
                    }
                }
            }
            require(part.length() > MIN_SIZE) { "モデルのファイルサイズが不足しています" }
            if (total > 0) require(part.length() == total) { "ダウンロードが途中で終了しました" }
            require(part.renameTo(target)) { "モデルの保存に失敗しました" }
            onProgress(100)
        } finally {
            connection.disconnect()
        }
    }
}
