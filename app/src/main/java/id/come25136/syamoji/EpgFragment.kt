package id.come25136.syamoji

import android.annotation.SuppressLint
import android.text.Spanned
import android.text.SpannedString
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions.withCrossFade
import com.egeniq.androidtvprogramguide.ProgramGuideFragment
import com.egeniq.androidtvprogramguide.R
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.schedulers.Schedulers
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import retrofit2.adapter.rxjava3.RxJava3CallAdapterFactory
import retrofit2.http.Query

// Mirakurun APIのインターフェース
interface MirakurunApi {
    @GET("api/channels")
    fun getChannels(): Single<List<Channel>>

    @GET("api/programs")
    fun getPrograms(@Query("serviceId") channelId: String): Single<List<MirakurunProgram>>
}

// Mirakurunから取得するデータのモデル
data class Channel(
    val type: String, val channel: String, val name: String, val services: List<Service>
)

data class Service(
    val id: Long, val serviceId: Int, val networkId: Int, val name: String
)

data class MirakurunProgram(
    val id: Long,
    val name: String,
    val startAt: Long,
    val duration: Long,
    val description: String
)

class EpgFragment : ProgramGuideFragment<EpgFragment.SimpleProgram>() {
    private val TAG = EpgFragment::class.java.name

    data class SimpleChannel(
        override val id: String,
        override val name: Spanned?,
        override val imageUrl: String?,
        val channelNumber: String,
        val serviceId: Int
    ) : ProgramGuideChannel

    data class SimpleProgram(
        val id: String, val description: String, val metadata: String
    )

    // Retrofitのインスタンスを作成
    private val retrofit: Retrofit =
        Retrofit.Builder().baseUrl("http://192.168.20.10:40772/") // MirakurunのURLに変更
            .addConverterFactory(GsonConverterFactory.create())
            .addCallAdapterFactory(RxJava3CallAdapterFactory.create()).build()

    private val mirakurunApi: MirakurunApi = retrofit.create(MirakurunApi::class.java)

    override fun onScheduleClicked(programGuideSchedule: ProgramGuideSchedule<SimpleProgram>) {
        val innerSchedule = programGuideSchedule.program
        if (innerSchedule == null) {
            Log.w(TAG, "Unable to open schedule!")
            return
        }
        if (programGuideSchedule.isCurrentProgram) {
            Toast.makeText(context, "Open live player", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Open detail page", Toast.LENGTH_LONG).show()
        }
        updateProgram(programGuideSchedule.copy(displayTitle = programGuideSchedule.displayTitle + " [clicked]"))
    }

    override fun onScheduleSelected(programGuideSchedule: ProgramGuideSchedule<SimpleProgram>?) {
        val titleView = view?.findViewById<TextView>(R.id.programguide_detail_title)
        titleView?.text = programGuideSchedule?.displayTitle
        val metadataView = view?.findViewById<TextView>(R.id.programguide_detail_metadata)
        metadataView?.text = programGuideSchedule?.program?.metadata
        val descriptionView = view?.findViewById<TextView>(R.id.programguide_detail_description)
        descriptionView?.text = programGuideSchedule?.program?.description
        val imageView = view?.findViewById<ImageView>(R.id.programguide_detail_image) ?: return
        if (programGuideSchedule != null) {
            Glide.with(imageView)
                .load("https://picsum.photos/462/240?random=" + programGuideSchedule.displayTitle.hashCode())
                .centerCrop().error(R.drawable.programguide_icon_placeholder)
                .diskCacheStrategy(DiskCacheStrategy.ALL).transition(withCrossFade())
                .into(imageView)
        } else {
            Glide.with(imageView).clear(imageView)
        }
    }

    override fun onChannelSelected(channel: ProgramGuideChannel) {
        val titleView = view?.findViewById<TextView>(R.id.programguide_detail_title)
        titleView?.text = channel.name
        val metadataView = view?.findViewById<TextView>(R.id.programguide_detail_metadata)
        metadataView?.text = null
        val descriptionView = view?.findViewById<TextView>(R.id.programguide_detail_description)
        descriptionView?.text = null
        val imageView = view?.findViewById<ImageView>(R.id.programguide_detail_image) ?: return
        Glide.with(imageView).clear(imageView)
    }

    override fun onChannelClicked(channel: ProgramGuideChannel) {
        Toast.makeText(context, "Channel clicked: ${channel.name}", Toast.LENGTH_LONG).show()
    }

    override fun isTopMenuVisible(): Boolean {
        return false
    }

    @SuppressLint("CheckResult")
    override fun requestingProgramGuideFor(localDate: LocalDate) {
        setState(State.Loading)

        mirakurunApi.getChannels().flatMap { channels ->
            // チャンネルをSimpleChannelに変換
            val simpleChannels = channels.map { channel ->
                channel.services.map { service ->
                    SimpleChannel(
                        "${service.id}",
                        SpannedString(service.name),
                        "http://192.168.20.10:40772/api/services/${service.id}/logo",
                        channel.channel,
                        service.serviceId
                    )
                }
            }.flatten()

            val existsChannels: ArrayList<EpgFragment.SimpleChannel> = ArrayList()

            // 各チャンネルからプログラムを取得
            val channelProgramSingles = simpleChannels.map { channel ->
                mirakurunApi.getPrograms(channel.serviceId.toString()).map { programs ->
                    val scheduleList = programs.map { program ->
                        val startAt = ZonedDateTime.ofInstant(
                            Instant.ofEpochSecond(program.startAt / 1000), ZoneOffset.ofHours(9)
                        )
                        val endAt = ZonedDateTime.ofInstant(
                            Instant.ofEpochSecond(program.startAt / 1000 + program.duration / 1000),
                            ZoneOffset.ofHours(9)
                        )

                        createSchedule(
                            program.id,
                            program.name,
                            program.description,
                            startAt,
                            endAt
                        )
                    }

                    if (scheduleList.isNotEmpty()) {
                        existsChannels.add(channel)
                    }

                    channel.id to scheduleList
                }.onErrorReturn {
                    channel.id to emptyList()
                }
            }

            // Single.zipを使って各チャンネルのプログラムを取得し、マップに変換
            Single.zip(channelProgramSingles) { results ->
                results.filterIsInstance<Pair<String, List<ProgramGuideSchedule<SimpleProgram>>>>()
                    .filter {
                        it.second.isNotEmpty()
                    }
                    .toMap()
            }.map { schedules ->
                // カスタム比較関数を使ってソート
                val sortedChannels = existsChannels.sortedWith { channel1, channel2 ->
                    val channel1Num = channel1.channelNumber.toIntOrNull()
                    val channel2Num = channel2.channelNumber.toIntOrNull()

                    when {
                        channel1.channelNumber == channel2.channelNumber -> {
                            channel1.serviceId.compareTo(channel2.serviceId)
                        }

                        channel1Num != null && channel2Num != null -> {
                            // 両方とも数値に変換できる場合は降順で比較
                            channel2Num.compareTo(channel1Num)
                        }

                        channel1Num != null -> {
                            // channel1だけが数値の場合はchannel1を優先
                            -1 // channel1が前
                        }

                        channel2Num != null -> {
                            // channel2だけが数値の場合はchannel2を優先
                            1 // channel2が前
                        }

                        else -> {
                            // 両方とも数値でない場合は昇順で比較
                            channel1.channelNumber.compareTo(channel2.channelNumber)
                        }
                    }
                }

                // schedulesを使って返す
                Pair(sortedChannels, schedules)
            }
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
            .subscribe({ (channels, schedules) ->
                setData(channels, schedules, localDate)
                if (channels.isEmpty() || schedules.isEmpty()) {
                    setState(State.Error("No channels loaded."))
                } else {
                    setState(State.Content)
                }
            }, {
                Log.e(TAG, "Unable to load example data!", it)
            })
    }

    private fun createSchedule(
        id: Long,
        programName: String, description: String, startAt: ZonedDateTime, endAt: ZonedDateTime
    ): ProgramGuideSchedule<SimpleProgram> {
        val metadata = DateTimeFormatter.ofPattern("'Starts at' HH:mm").format(startAt)
        return ProgramGuideSchedule.createScheduleWithProgram(
            id, startAt.toInstant(), endAt.toInstant(), true, programName, SimpleProgram(
                id.toString(),
                description,
                metadata
            )
        )
    }

    override fun requestRefresh() {
        requestingProgramGuideFor(currentDate)
    }
}
