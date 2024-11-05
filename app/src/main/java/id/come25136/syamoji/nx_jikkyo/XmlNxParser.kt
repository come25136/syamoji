package id.come25136.syamoji.nx_jikkyo

import io.reactivex.rxjava3.core.Observable
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.simpleframework.xml.Element
import org.simpleframework.xml.ElementList
import org.simpleframework.xml.Root
import org.simpleframework.xml.core.Persister
import java.io.IOException

interface BaseChannel {
    var video: String
    var thread: Thread
}

@Root(name = "channels", strict = false)
data class Channels(
    @field:ElementList(inline = true, required = false)
    var channelList: MutableList<Channel> = mutableListOf(),

    @field:ElementList(name = "bs_channel", inline = true, required = false)
    var bsChannelList: MutableList<BsChannel> = mutableListOf(),
)

@Root(name = "channel", strict = false)
data class Channel(
    @field:Element(name = "id")
    var id: Int = 0,

    @field:Element(name = "no")
    var no: Int = 0,

    @field:Element(name = "name")
    var name: String = "",

    @field:Element(name = "video")
    override var video: String = "",

    @field:Element(name = "thread")
    override var thread: Thread = Thread()
) : BaseChannel

@Root(name = "bs_channel", strict = false)
data class BsChannel(
    @field:Element(name = "id")
    var id: Int = 0,

    @field:Element(name = "name")
    var name: String = "",

    @field:Element(name = "video")
    override var video: String = "",

    @field:Element(name = "thread")
    override var thread: Thread = Thread()
) : BaseChannel

@Root(name = "thread", strict = false)
data class Thread(
    @field:Element(name = "id")
    var id: Int = 0,

    @field:Element(name = "force", required = false)
    var force: Int = 0,

    @field:Element(name = "viewers", required = false)
    var viewers: Int = 0,

    @field:Element(name = "comments", required = false)
    var comments: Int = 0
)

fun fetchNxXml(url: String): Observable<Channels> {
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
                            val serializer = Persister()
                            val channels = serializer.read(Channels::class.java, xmlData)
                            if (!emitter.isDisposed) {
                                emitter.onNext(channels)
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
