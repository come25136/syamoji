package id.come25136.syamoji.m3u

import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import okhttp3.OkHttpClient
import okhttp3.Request
import net.bjoernpetersen.m3u.M3uParser
import net.bjoernpetersen.m3u.model.M3uEntry
import java.io.IOException

fun fetchM3U(url: String): Single<List<M3uEntry>> {
    val client = OkHttpClient()

    return Single.fromCallable<List<M3uEntry>> {
        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Unexpected code $response")

            val body = response.body?.string() ?: throw IOException("Empty response body")

            val parser = M3uParser
            val playlist = parser.parse(body)

            playlist
        }
    }
        .subscribeOn(Schedulers.io())
}