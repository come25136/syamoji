/*
 * Copyright (c) 2020, Egeniq
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.egeniq.androidtvprogramguide.item

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Spanned
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.egeniq.androidtvprogramguide.R
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import java.net.URL
import kotlin.math.max
import kotlin.math.min

data class Channel(
    override val id: String, // Mirakurun内部ではnetworkId + serviceIdとして扱われているもの
    val group: String, // GR, BS, CS
    override val name: Spanned,
    override val logoUrl: String?, // チャンネルロゴ
    val streamUrl: URL
) : ProgramGuideChannel

data class Program(
    val id: Long,
    val title: String,
    val description: String,
    val metadata: String,
    val categories: Array<String>,
    val channel: Channel
)

class ProgramGuideItemView<T : Program> : FrameLayout {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    var schedule: ProgramGuideSchedule<T>? = null

    private val staticItemPadding: Int =
        resources.getDimensionPixelOffset(R.dimen.programguide_item_padding)

    private var itemTextHeight: Int = 0
    private var maxHeightForRipple: Int = 0
    private var preventParentRelayout = false

    private val titleView: TextView
    private val descriptionView: TextView
    private val progressView: ProgressBar

    init {
        View.inflate(context, R.layout.programguide_item_program, this)

        titleView = findViewById(R.id.title)
        descriptionView = findViewById(R.id.description)
        progressView = findViewById(R.id.progress)

        // フォーカス状態の変更を監視
        setOnFocusChangeListener { _, hasFocus ->
            updateBackgroundForFocus(hasFocus)
        }
    }

    private fun updateBackgroundForFocus(hasFocus: Boolean) {
        var category =
            schedule?.program?.categories?.firstOrNull()?.split(Regex("[／\\s・]"))?.firstOrNull()
        val backgroundColor = if (hasFocus) {
            when (category) {
                "sports" -> R.color.programguide_sports_focused_color
                "news" -> R.color.programguide_news_focused_color
                else -> R.color.programguide_default_focused_color
            }
        } else {
            when (category) {
                "ドキュメンタリー" -> R.color.programguide_documentary_color
                "スポーツ" -> R.color.programguide_sports_color
                "アニメ" -> R.color.programguide_anime_color
                "ドラマ" -> R.color.programguide_drama_color
                "バラエティ" -> R.color.programguide_variety_color
                "情報" -> R.color.programguide_info_color
                "趣味" -> R.color.programguide_hobby_color
                "ニュース" -> R.color.programguide_news_color
                "劇場" -> R.color.programguide_theater_color
                "音楽" -> R.color.programguide_music_color
                "映画" -> R.color.programguide_movie_color
                "福祉" -> R.color.programguide_welfare_color
                else -> R.color.programguide_default_color
            }
        }

        // ボーダー付きの背景を作成
        val borderColor = if (hasFocus) {
            resources.getColor(R.color.programguide_border_focused_color, context.theme)
        } else {
            resources.getColor(R.color.programguide_border_default_color, context.theme)
        }
        val borderWidth = resources.getDimensionPixelSize(R.dimen.programguide_item_border_width)

        val drawable = GradientDrawable().apply {
            setColor(resources.getColor(backgroundColor, context.theme))
            setStroke(borderWidth, borderColor)
            cornerRadius = resources.getDimension(R.dimen.programguide_gap_item_corner_radius)
        }
        background = drawable
    }

    fun setValues(
        scheduleItem: ProgramGuideSchedule<T>,
        fromUtcMillis: Long,
        toUtcMillis: Long,
        gapTitle: String,
        displayProgress: Boolean
    ) {
        schedule = scheduleItem
        val layoutParams = layoutParams
        if (layoutParams != null) {
            val spacing = resources.getDimensionPixelSize(R.dimen.programguide_item_spacing)
            layoutParams.height =
                scheduleItem.height - 2 * spacing // Here we subtract the spacing, otherwise the calculations will be wrong at other places
            if (layoutParams.height < 1) {
                layoutParams.height = 1
            }
            setLayoutParams(layoutParams)
        }
        var title = schedule?.displayTitle
        if (scheduleItem.isGap) {
            title = gapTitle
            setBackgroundResource(R.drawable.programguide_gap_item_background)
            isClickable = false
        } else {
            // 初期背景を設定
            updateBackgroundForFocus(hasFocus = false)
            isClickable = scheduleItem.isClickable
        }
        title =
            if (title?.isEmpty() == true)
                resources.getString(R.string.programguide_title_no_program) else title

        titleView.text = title
        descriptionView.text = schedule?.program?.description

        initProgress(
            ProgramGuideUtil.convertMillisToPixel(
                startMillis = scheduleItem.startsAtMillis,
                endMillis = scheduleItem.endsAtMillis
            )
        )
        if (displayProgress) {
            updateProgress(System.currentTimeMillis())
        } else {
            progressView.visibility = View.GONE
        }

        titleView.measure(
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        itemTextHeight = titleView.measuredHeight - titleView.paddingTop - titleView.paddingBottom
        maxHeightForRipple = ProgramGuideUtil.convertMillisToPixel(fromUtcMillis, toUtcMillis)
    }

    private fun initProgress(width: Int) {
        progressView.max = width
    }

    fun updateProgress(now: Long) {
        schedule?.let {
            val hasEnded = now > it.endsAtMillis
            if (it.isCurrentProgram.not()) {
                progressView.visibility = View.GONE
            } else {
//                progressView.visibility = View.VISIBLE
                progressView.progress =
                    ProgramGuideUtil.convertMillisToPixel(it.startsAtMillis, now)
            }
            this.isActivated = !hasEnded
        }
    }

    /** Update programItemView to handle alignments of text. */
    fun updateVisibleArea() {
        val parentView = parent as View
        if (layoutDirection == LAYOUT_DIRECTION_LTR) {
            layoutVisibleArea(
                parentView.top + parentView.paddingTop - top,
                bottom - parentView.bottom
            )
        } else {
            layoutVisibleArea(
                parentView.top - top,
                bottom - parentView.bottom + parentView.paddingTop
            )
        }
    }

    /**
     * Layout title and episode according to visible area.
     *
     *
     * Here's the spec.
     * 1. Don't show text if it's shorter than 48dp.
     * 2. Try showing whole text in visible area by placing and wrapping text, but do not wrap text less than 30min.
     * 3. Episode title is visible only if title isn't multi-line.
     *
     * @param topOffset Amount of pixels the view sticks out on the left side of the screen. If it is negative, it does not stick out.
     * @param bottomOffset Amount of pixels the view sticks out on the right side of the screen. If it is negative, it does not stick out.
     */
    private fun layoutVisibleArea(topOffset: Int, bottomOffset: Int) {
        val height = schedule?.height ?: 0
        var topPadding = max(0, topOffset)
        var bottomPadding = max(0, bottomOffset)
        val minHeight = min(height, itemTextHeight + 2 * staticItemPadding)
        if (topPadding > 0 && height - topPadding < minHeight) {
            topPadding = max(0, height - minHeight)
        }
        if (bottomPadding > 0 && height - bottomPadding < minHeight) {
            bottomPadding = max(0, height - minHeight)
        }

        if (parent.layoutDirection == LAYOUT_DIRECTION_LTR) {
            if (topPadding + staticItemPadding != paddingTop || bottomPadding + staticItemPadding != paddingBottom) {
                // The size of this view is kept, no need to tell parent.
                preventParentRelayout = true

//                titleView.setPaddingRelative(
//                    0,
//                    topPadding + staticItemPadding,
//                    0,
//                    bottomPadding + staticItemPadding,
//                )
                preventParentRelayout = false
            }
        } else {
            if (topPadding + staticItemPadding != paddingBottom || bottomPadding + staticItemPadding != paddingBottom) {
                // In this case, we need to tell the parent to do a relayout, RTL is a bit more complicated, it seems.
//                titleView.setPaddingRelative(
//                    0,
//                    bottomPadding + staticItemPadding,
//                    0,
//                    topPadding + staticItemPadding,
//                )
            }
        }
    }

    override fun requestLayout() {
        if (preventParentRelayout) {
            // Trivial layout, no need to tell parent.
            forceLayout()
        } else {
            super.requestLayout()
        }
    }

    fun clearValues() {
        tag = null
        schedule = null
    }
}
