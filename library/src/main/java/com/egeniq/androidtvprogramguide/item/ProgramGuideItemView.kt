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
import android.util.AttributeSet
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.egeniq.androidtvprogramguide.R
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import kotlin.math.max
import kotlin.math.min


class ProgramGuideItemView<T> : FrameLayout {
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
    private val progressView: ProgressBar

    init {
        View.inflate(context, R.layout.programguide_item_program, this)

        titleView = findViewById(R.id.title)
        progressView = findViewById(R.id.progress)
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
            // If the programme is very short, and the table width is also reduced, or the gap is enlarged,
            // there is an edge case that we could go into negative widths. This fixes that.
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
            setBackgroundResource(R.drawable.programguide_item_program_background)
            isClickable = scheduleItem.isClickable
        }
        title =
            if (title?.isEmpty() == true)
                resources.getString(R.string.programguide_title_no_program) else title

        updateText(title)
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
        // Maximum width for us to use a ripple
        maxHeightForRipple = ProgramGuideUtil.convertMillisToPixel(fromUtcMillis, toUtcMillis)
    }

    private fun updateText(title: String?) {
        titleView.text = title
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
                progressView.visibility = View.VISIBLE
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

                titleView.setPaddingRelative(
                    0,
                    topPadding + staticItemPadding,
                    0,
                    bottomPadding + staticItemPadding,
                )
                preventParentRelayout = false
            }
        } else {
            if (topPadding + staticItemPadding != paddingBottom || bottomPadding + staticItemPadding != paddingBottom) {
                // In this case, we need to tell the parent to do a relayout, RTL is a bit more complicated, it seems.
                titleView.setPaddingRelative(
                    0,
                    bottomPadding + staticItemPadding,
                    0,
                    topPadding + staticItemPadding,
                )
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