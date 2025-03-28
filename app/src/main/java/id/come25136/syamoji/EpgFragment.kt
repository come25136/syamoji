package id.come25136.syamoji

import android.annotation.SuppressLint
import android.content.Intent
import android.text.SpannedString
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.util.UnstableApi
import com.egeniq.androidtvprogramguide.ProgramGuideFragment
import com.egeniq.androidtvprogramguide.R
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.item.Channel
import com.egeniq.androidtvprogramguide.item.Program
import id.come25136.syamoji.iptv.fetchIptvXml
import id.come25136.syamoji.m3u.fetchM3U
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import org.threeten.bp.LocalDate
import org.threeten.bp.ZoneId
import org.threeten.bp.ZoneOffset
import org.threeten.bp.ZonedDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale

@UnstableApi
class EpgFragment : ProgramGuideFragment() {
    private val TAG = EpgFragment::class.java.name

    override val DISPLAY_LOCALE: Locale
        get() = Locale("ja", "JP")
    override val DISPLAY_TIMEZONE: ZoneId
        get() = ZoneOffset.ofHours(9)
    override val SELECTABLE_DAYS_IN_PAST: Int
        get() = 0
    override val DATE_WITH_DAY_FORMATTER: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("d日").withLocale(DISPLAY_LOCALE)

    override fun onScheduleClicked(programGuideSchedule: ProgramGuideSchedule<Program>) {
        val innerSchedule = programGuideSchedule.program
        if (innerSchedule == null) {
            Log.w(TAG, "Unable to open schedule!")
            return
        }

        val streamUrl = innerSchedule.channel.streamUrl.toString()

        if (programGuideSchedule.isCurrentProgram) {
            val intent = Intent(activity, VLCRenderActivity::class.java)
            intent.putExtra("streamUrl", streamUrl)
            intent.putExtra("channelId", innerSchedule.channel.id)
            startActivity(intent)
        } else {
            // 番組詳細ページを開く場合の処理
            // Toast.makeText(context, "Open detail page", Toast.LENGTH_LONG).show()
        }
    }

    override fun onScheduleSelected(programGuideSchedule: ProgramGuideSchedule<Program>?) {
        val titleView = view?.findViewById<TextView>(R.id.programguide_detail_title)
        titleView?.text = programGuideSchedule?.displayTitle
        val metadataView = view?.findViewById<TextView>(R.id.programguide_detail_metadata)
        metadataView?.text = programGuideSchedule?.program?.metadata
        val descriptionView = view?.findViewById<TextView>(R.id.programguide_detail_description)
        descriptionView?.text = programGuideSchedule?.program?.description
    }

    override fun onChannelSelected(channel: ProgramGuideChannel) {
        val titleView = view?.findViewById<TextView>(R.id.programguide_detail_title)
        titleView?.text = channel.name
        val metadataView = view?.findViewById<TextView>(R.id.programguide_detail_metadata)
        metadataView?.text = null
        val descriptionView = view?.findViewById<TextView>(R.id.programguide_detail_description)
        descriptionView?.text = null
    }

    override fun onChannelClicked(channel: ProgramGuideChannel) {
        Toast.makeText(
            context,
            "id.come25136.syamoji.Channel clicked: ${channel.name}",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun isTopMenuVisible(): Boolean {
        return false
    }

    @SuppressLint("CheckResult")
    override fun requestingProgramGuideFor(localDate: LocalDate) {
        setState(State.Loading)

        fetchM3U("http://192.168.20.10:40772/api/iptv/playlist")
            .observeOn(Schedulers.single())
            .map { entries ->
                entries.map { entry ->
                    Channel(
                        entry.metadata["tvg-id"]!!,
                        entry.metadata["group-title"]!!,
                        SpannedString(entry.title),
                        entry.metadata["tvg-logo"],
                        entry.location.url,
                    )
                }
            }
            .subscribe(
                { channels ->
                    val channelsById = channels.associateBy { it.id }

                    fetchIptvXml("http://192.168.20.10:40772/api/iptv/xmltv")
                        .subscribeOn(Schedulers.io())
                        .observeOn(Schedulers.single())
                        .map { tvData ->
                            tvData.programmes.mapIndexed { i, tvDataProgram ->
                                val program = Program(
                                    (channelsById[tvDataProgram.channel]!!.id + (tvDataProgram.start.toEpochSecond() / 60).toString()).toLong(),
                                    tvDataProgram.title,
                                    tvDataProgram.desc ?: "",
                                    if (tvDataProgram.categories.isEmpty()) "" else tvDataProgram.categories[0],
                                    tvDataProgram.categories.toTypedArray(),
                                    channelsById[tvDataProgram.channel]!!,
                                )

                                createSchedule(
                                    program,
                                    tvDataProgram.start,
                                    tvDataProgram.stop,
                                )
                            }.filter {
                                mutableListOf(
                                    "3273601024",
                                    "3273701032",
                                    "3273801040",
                                    "3274101064",
                                    "3273901048",
                                    "3274201072",
                                    "3274001056",
                                    "3239123608",
                                    "400101",
                                    "400141",
                                    "400151",
                                    "400161",
                                    "400171",
                                    "400181",
                                    "400211"
                                ).contains(it.program?.channel?.id) &&
                                        it.program?.title != "放送休止"
                            }.sortedBy { it.startsAtMillis } // 時系列にしないとguideで正しく表示されない（配列順に描画される）
                        }
                        .map { programs ->
                            programs.groupBy {
                                it.program!!.channel.id
                            }
                        }
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe({ programs ->
                            val existsChannels = channels.filter {
                                programs[it.id]?.isNotEmpty() == true
                            }

                            setData(existsChannels, programs, localDate)

                            if (channels.isEmpty() || programs.isEmpty()) {
                                setState(State.Error("No channels loaded."))
                            } else {
                                setState(State.Content)
                            }
                        }, { error ->
                            // エラー処理
                            Log.e("fetchIptvXml", "Error fetching XML", error)
                        })
                },
                { error ->
                    println("エラーが発生しました: ${error.message}")
                }
            )
    }

    private fun createSchedule(
        program: Program,
        startAt: ZonedDateTime,
        endAt: ZonedDateTime,
    ): ProgramGuideSchedule<Program> {
        return ProgramGuideSchedule.createScheduleWithProgram(
            program.id,
            startAt.toInstant(),
            endAt.toInstant(),
            true,
            program.title,
            program,
        )
    }

    override fun requestRefresh() {
        requestingProgramGuideFor(currentDate)
    }
}
