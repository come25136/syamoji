package id.come25136.syamoji.iptv

import io.reactivex.rxjava3.core.Observable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.threeten.bp.ZoneId
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.IOException
import java.io.StringReader

data class Tv(
    var channels: MutableList<Channel> = mutableListOf(),
    var programmes: MutableList<Programme> = mutableListOf()
)

data class Channel(
    var id: String = "",
    var displayNames: MutableList<String> = mutableListOf(),
    var icon: Icon? = null
)

data class Icon(
    var src: String = ""
)

data class Programme(
    var start: ZonedDateTime = ZonedDateTime.now(),
    var stop: ZonedDateTime = ZonedDateTime.now(),
    var channel: String = "",
    var title: String = "",
    var desc: String = "",
    var categories: MutableList<String> = mutableListOf()
)

fun parseIptvXml(xml: String): Tv {
    val tv = Tv()
    var currentChannel: Channel? = null
    var currentProgramme: Programme? = null
    var currentText = ""

    val factory = XmlPullParserFactory.newInstance()
    val parser = factory.newPullParser()
    parser.setInput(StringReader(xml))

    // カスタムフォーマッター (例: 20241122120000 +0000)
    val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")

    // デフォルトのタイムゾーン (例: UTC)
    val defaultZone = ZoneId.of("Asia/Tokyo")

    var eventType = parser.eventType
    while (eventType != XmlPullParser.END_DOCUMENT) {
        when (eventType) {
            XmlPullParser.START_TAG -> {
                when (parser.name) {
                    "channel" -> {
                        currentChannel = Channel(id = parser.getAttributeValue(null, "id"))
                    }
                    "display-name" -> {
                        currentText = ""
                    }
                    "icon" -> {
                        currentChannel?.icon = Icon(src = parser.getAttributeValue(null, "src") ?: "")
                    }
                    "programme" -> {
                        val start = parser.getAttributeValue(null, "start")?.let {
                            ZonedDateTime.parse(it, formatter)
                        } ?: ZonedDateTime.now(defaultZone)

                        val stop = parser.getAttributeValue(null, "stop")?.let {
                            ZonedDateTime.parse(it, formatter)
                        } ?: ZonedDateTime.now(defaultZone)

                        currentProgramme = Programme(
                            start = start.withZoneSameInstant(defaultZone),
                            stop = stop.withZoneSameInstant(defaultZone),
                            channel = parser.getAttributeValue(null, "channel") ?: ""
                        )
                    }
                    "title", "desc", "category" -> {
                        currentText = ""
                    }
                }
            }
            XmlPullParser.TEXT -> {
                currentText = parser.text.trim()
            }
            XmlPullParser.END_TAG -> {
                when (parser.name) {
                    "channel" -> {
                        currentChannel?.let { tv.channels.add(it) }
                        currentChannel = null
                    }
                    "display-name" -> {
                        currentChannel?.displayNames?.add(currentText)
                    }
                    "programme" -> {
                        currentProgramme?.let { tv.programmes.add(it) }
                        currentProgramme = null
                    }
                    "title" -> {
                        currentProgramme?.title = currentText
                    }
                    "desc" -> {
                        currentProgramme?.desc = currentText
                    }
                    "category" -> {
                        currentProgramme?.categories?.add(currentText)
                    }
                }
            }
        }
        eventType = parser.next()
    }
    return tv
}

fun fetchIptvXml(url: String): Observable<Tv> {
    return Observable.create { emitter ->
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!emitter.isDisposed) {
                    emitter.onError(e)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    response.body?.string()?.let { xmlData ->
                        try {
                            val tvData = parseIptvXml(xmlData)
                            tvData.programmes = tvData.programmes.filter { it.title.isNotBlank() }.toMutableList()
                            if (!emitter.isDisposed) {
                                emitter.onNext(tvData)
                                emitter.onComplete()
                            }
                        } catch (e: Exception) {
                            if (!emitter.isDisposed) {
                                emitter.onError(e)
                            }
                        }
                    } ?: run {
                        if (!emitter.isDisposed) {
                            emitter.onError(NullPointerException("Response body is null"))
                        }
                    }
                } else {
                    if (!emitter.isDisposed) {
                        emitter.onError(IOException("HTTP error code: ${response.code}"))
                    }
                }
            }
        })
    }
}
