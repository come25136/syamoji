package id.come25136.syamoji.iptv

import io.reactivex.rxjava3.core.Observable
import okhttp3.*
import org.simpleframework.xml.Attribute
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import org.simpleframework.xml.convert.Convert
import org.simpleframework.xml.core.Persister
import org.simpleframework.xml.transform.RegistryMatcher
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.io.IOException
import java.io.StringReader


@Root(name = "tv", strict = false)
data class Tv(
    @field:ElementList(entry = "channel", inline = true, required = false)
    var channels: MutableList<Channel> = mutableListOf(),

    @field:ElementList(entry = "programme", inline = true, required = false)
    var programmes: MutableList<Programme> = mutableListOf()
)

@Root(name = "channel", strict = false)
data class Channel(
    @field:ElementList(entry = "display-name", inline = true, required = false)
    var displayNames: MutableList<String> = mutableListOf(), // サービス名とチャンネル番号が入ってくる(https://github.com/Chinachu/Mirakurun/blob/31b22cc83cb2166c117a2fba8f2499d467750dc8/src/Mirakurun/api/iptv/xmltv.ts#L264-L265)

    @field:Element(name = "icon", required = false)
    var icon: Icon? = null,

    @field:Attribute(name = "id", required = true)
    var id: String = ""
)

@Root(name = "icon", strict = false)
data class Icon(
    @field:Attribute(name = "src", required = false)
    var src: String = ""
)

@Root(name = "programme", strict = false)
data class Programme(
    @field:Attribute(name = "start", required = true)
    var start: ZonedDateTime = ZonedDateTime.now(),

    @field:Attribute(name = "stop", required = true)
    var stop: ZonedDateTime = ZonedDateTime.now(),

    @field:Attribute(name = "channel", required = true)
    var channel: String = "",

    @field:Element(name = "title", required = false)
    var title: String = "",

    @field:Element(name = "desc", required = false)
    var desc: String = "",

    @field:ElementList(entry = "category", inline = true, required = false)
    var categories: MutableList<String> = mutableListOf()
)

fun fetchIptvXml(url: String): Observable<Tv> {
    return Observable.create { emitter ->
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // エラーが発生した場合
                if (!emitter.isDisposed) {
                    emitter.onError(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let { xmlData ->
                        // XMLをパースする
                        val m = RegistryMatcher()
                        m.bind(ZonedDateTime::class.java, ZonedDateTimeConverter())
                        val serializer = Persister(m)
                        try {
                            val reader = StringReader(xmlData)
                            val tvData = serializer.read(Tv::class.java, reader)
                            tvData.programmes =
                                tvData.programmes.filter { it.title.isNotBlank() }
                                    .toMutableList()

                            if (!emitter.isDisposed) {
                                // 成功時にデータをEmitterに送信
                                emitter.onNext(tvData)
                                emitter.onComplete()
                            }
                        } catch (e: Exception) {
                            // パースエラー
                            if (!emitter.isDisposed) {
                                emitter.onError(e)
                            }
                        }
                    } ?: run {
                        // レスポンスがnullの場合
                        if (!emitter.isDisposed) {
                            emitter.onError(NullPointerException("Response body is null"))
                        }
                    }
                } else {
                    // レスポンスが不成功の場合
                    if (!emitter.isDisposed) {
                        emitter.onError(IOException("HTTP error code: ${response.code}"))
                    }
                }
            }
        })
    }
}