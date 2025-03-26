/*
 * 著作権 (c) 2020, Egeniq
 *
 * ライセンスはApache License, Version 2.0 ("ライセンス")に基づいてライセンスされています。
 * ライセンスに従わない限り、このファイルを使用することはできません。
 * ライセンスのコピーは以下の場所から入手できます。
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * 適用される法律または書面によって許可されない限り、ライセンスに基づいて配布されたソフトウェアは
 * "現状のまま"で提供され、明示または黙示を問わず、いかなる保証もありません。
 * ライセンスに基づく権利と制限の詳細については、ライセンスを参照してください。
 */

package com.egeniq.androidtvprogramguide.row

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver.OnGlobalLayoutListener
import androidx.recyclerview.widget.LinearLayoutManager
import com.egeniq.androidtvprogramguide.ProgramGuideHolder
import com.egeniq.androidtvprogramguide.ProgramGuideManager
import com.egeniq.androidtvprogramguide.R
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView
import com.egeniq.androidtvprogramguide.timeline.ProgramGuideTimelineGridView
import com.egeniq.androidtvprogramguide.timeline.ProgramGuideTimelineRow
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class ProgramGuideRowGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : ProgramGuideTimelineGridView(context, attrs, defStyle) {
    companion object {
        private val ONE_HOUR_MILLIS = TimeUnit.HOURS.toMillis(1)
        private val HALF_HOUR_MILLIS = ONE_HOUR_MILLIS / 2
    }

    private var keepFocusToCurrentProgram: Boolean = false

    private lateinit var programGuideHolder: ProgramGuideHolder<*>
    private lateinit var programGuideManager: ProgramGuideManager<*>
    private var timeRowView: ProgramGuideTimelineGridView? = null

    var channel: ProgramGuideChannel? = null
        private set
    private val minimumStickOutHeight =
        resources.getDimensionPixelOffset(R.dimen.programguide_minimum_item_height_sticking_out_behind_channel_column)

    private val layoutListener = object : OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            viewTreeObserver.removeOnGlobalLayoutListener(this)
            updateChildVisibleArea()
        }
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        val itemView = child as ProgramGuideItemView<*>
        if (top <= itemView.bottom && itemView.top <= bottom) {
            itemView.updateVisibleArea()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent?): Boolean {
        val timeRow = timeRowView ?: return super.onTouchEvent(e)
        if (e?.action == MotionEvent.ACTION_UP) {
            performClick()
        }
        return timeRow.dispatchTouchEvent(e)
    }

    override fun onScrolled(dx: Int, dy: Int) {
        // コールバックを削除して、updateChildVisibleAreaが2回呼び出されるのを防ぎます。
        viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
        super.onScrolled(dx, dy)
        updateChildVisibleArea()
    }

    // RTLが解決された後にこのAPIを呼び出します。（つまり、ビューが測定された後。）
    private fun isDirectionStart(direction: Int): Boolean {
        return if (layoutDirection == View.LAYOUT_DIRECTION_LTR)
            direction == View.FOCUS_UP
        else
            direction == View.FOCUS_DOWN
    }

    // RTLが解決された後にこのAPIを呼び出します。（つまり、ビューが測定された後。）
    private fun isDirectionEnd(direction: Int): Boolean {
        return if (layoutDirection == View.LAYOUT_DIRECTION_LTR)
            direction == View.FOCUS_DOWN
        else
            direction == View.FOCUS_UP
    }

    override fun focusSearch(focused: View, direction: Int): View? {
        val focusedEntry = (focused as ProgramGuideItemView<*>).schedule
            ?: return super.focusSearch(focused, direction)
        val fromMillis = programGuideManager.getFromUtcMillis()
        val toMillis = programGuideManager.getToUtcMillis()

        Log.d(
            "focusSearch",
            "fromMillis:${
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(fromMillis))
            } toMillis:${
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(toMillis))
            }"
        )
        Log.d("focusSearch", "focusedEntry:${focusedEntry.program}")

        if (isDirectionStart(direction) || direction == View.FOCUS_BACKWARD) {
            if (focusedEntry.startsAtMillis < fromMillis) {
                // 現在のエントリがビューの外で開始されます。左に揃えるかスクロールします。
                scrollByTime(
                    max(-ONE_HOUR_MILLIS, focusedEntry.startsAtMillis - fromMillis)
                )
                return focused
            }
        } else if (isDirectionEnd(direction) || direction == View.FOCUS_FORWARD) {
            if (focusedEntry.endsAtMillis > toMillis) {
                // 現在のエントリがビューの外で終了します。右（またはRTLの場合は左）にスクロールします。
                scrollByTime(ONE_HOUR_MILLIS)
                return focused
            }
        }

        val target = super.focusSearch(focused, direction)
        if (target !is ProgramGuideItemView<*>) {
            if (isDirectionEnd(direction) || direction == View.FOCUS_FORWARD) {
                if (focusedEntry.endsAtMillis != toMillis) {
                    // フォーカスされたエントリが最後のエントリです。右端に揃えます。
                    scrollByTime(focusedEntry.endsAtMillis - toMillis)
                    return focused
                }
            }
            return target
        }

        val targetEntry = target.schedule ?: return target

        // fromMillisとtargetEntry.startsAtMillisのログを出力
        val fromMillisFormatted =
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date(fromMillis))
        val targetEntryStartsAtMillisFormatted = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            .format(java.util.Date(targetEntry.startsAtMillis))
        Log.d(
            "focusSearch",
            "fromMillis:$fromMillisFormatted startsAtMillis:$targetEntryStartsAtMillisFormatted ${targetEntry.program}"
        )

        if (isDirectionStart(direction) || direction == View.FOCUS_BACKWARD) {
            if (targetEntry.startsAtMillis < fromMillis && targetEntry.endsAtMillis < fromMillis + HALF_HOUR_MILLIS) {
                // ターゲットエントリがビューの外で開始されます。左（またはRTLの場合は右）に揃えるかスクロールします。
                scrollByTime(
                    max(-ONE_HOUR_MILLIS, targetEntry.startsAtMillis - fromMillis)
                )
            }
        } else if (isDirectionEnd(direction) || direction == View.FOCUS_FORWARD) {
            if (targetEntry.startsAtMillis > fromMillis + ONE_HOUR_MILLIS + HALF_HOUR_MILLIS) {
                // ターゲットエントリがビューの外で開始されます。右（またはRTLの場合は左）に揃えるかスクロールします。
                scrollByTime(
                    min(
                        ONE_HOUR_MILLIS,
                        targetEntry.startsAtMillis - fromMillis - ONE_HOUR_MILLIS
                    )
                )
            }
        }

        return target
    }

    private fun scrollByTime(timeToScroll: Long) {
        programGuideManager.shiftTime(timeToScroll)
    }

    override fun onChildDetachedFromWindow(child: View) {
        if (child.hasFocus()) {
            // フォーカスされたビューは更新された場合にのみデタッチできます。
            val entry = (child as ProgramGuideItemView<*>).schedule
            if (entry?.program == null) {
                // 情報が読み込まれたためにフォーカスが失われます。すぐにフォーカスを要求します。
                // （このエントリは、実際のエントリがアタッチされた後にデタッチされるため、アタッチされているエントリにフォーカスを再開するために以下のアプローチを取ることはできません。）
                post { requestFocus() }
            } else if (entry.isCurrentProgram) {
                // 現在のプログラムがガイドに表示されています。
                // 現在のプログラムを含む更新されたエントリはすぐに再度アタッチされるため、onChildAttachedToWindow()でフォーカスを戻します。
                keepFocusToCurrentProgram = true
            }
        }
        super.onChildDetachedFromWindow(child)
    }

    override fun onChildAttachedToWindow(child: View) {
        super.onChildAttachedToWindow(child)
        if (keepFocusToCurrentProgram) {
            val entry = (child as ProgramGuideItemView<*>).schedule
            if (entry?.isCurrentProgram == true) {
                keepFocusToCurrentProgram = false
                post { requestFocus() }
            }
        }
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        // この部分はデフォルトの子フォーカス動作をインターセプトするために必要です。
        // フォーカスが上から来て、左のチャンネル列の後ろに隠れているアイテムがある場合、デフォルトのフォーカス動作はそれを選択します。
        // しかし、このアイテムはチャンネル列の後ろにあるため、ユーザーには選択されていることが見えません。
        // したがって、この発生をチェックし、可能であれば次のアイテムを選択します。
        val gridHasFocus = programGuideHolder.programGuideGrid.hasFocus()
        if (child == null) {
            super.requestChildFocus(child, focused)
            return
        }

        if (!gridHasFocus) {
            findNextFocusableChild(child)?.let {
                super.requestChildFocus(child, focused)
                it.requestFocus()
                // このスキップは、グローバルフォーカス変更リスナーがイベントを間違った順序で受け取るために必要です。
                // 最初に置換アイテム、次に古いアイテム。
                // 2番目のものをスキップすることで、（正しい）置換アイテムのみがリスナーに通知されます。
                programGuideHolder.programGuideGrid.markCorrectChild(it)
                return
            }
        }
        super.requestChildFocus(child, focused)
    }

    private fun findNextFocusableChild(child: View): View? {
        // 子がフォーカス可能かどうかをチェックして返す
        val topEdge = child.top
        val bottomEdge = child.top + child.height
        val viewPosition = layoutManager?.getPosition(child)

        if (layoutDirection == LAYOUT_DIRECTION_LTR && (topEdge >= programGuideHolder.programGuideGrid.getFocusRange().lower ||
                    bottomEdge >= programGuideHolder.programGuideGrid.getFocusRange().lower + minimumStickOutHeight)
        ) {
            return child
        } else if (layoutDirection == LAYOUT_DIRECTION_RTL && (bottomEdge <= programGuideHolder.programGuideGrid.getFocusRange().upper ||
                    topEdge <= programGuideHolder.programGuideGrid.getFocusRange().upper - minimumStickOutHeight)
        ) {
            // RTLモード
            return child
        }

        // そうでない場合、次の子があるかどうかをチェックし、再帰的に再テストする
        if (viewPosition != null && viewPosition >= 0 && viewPosition < (layoutManager?.itemCount
                ?: (0 - 1))
        ) {
            val nextChild = layoutManager?.findViewByPosition(viewPosition + 1)
            nextChild?.let {
                return findNextFocusableChild(it)
            }
        }

        return null
    }

    public override fun onRequestFocusInDescendants(
        direction: Int,
        previouslyFocusedRect: Rect?
    ): Boolean {
        val programGrid = programGuideHolder.programGuideGrid
        // 前のフォーカス範囲に従ってフォーカスを与える
        val focusRange = programGrid.getFocusRange()
        val nextFocus = ProgramGuideUtil.findNextFocusedProgram(
            this,
            focusRange.lower,
            focusRange.upper,
            programGrid.isKeepCurrentProgramFocused()
        )

        if (nextFocus != null) {
            return nextFocus.requestFocus()
        }
        val result = super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
        if (!result) {
            // LeanbackLibraryのデフォルトのフォーカス検索ロジックが失敗することがあります。
            // フォールバックソリューションとして、最初のフォーカス可能なビューにフォーカスを要求します。
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                if (child.isShown && child.hasFocusable()) {
                    return child.requestFocus()
                }
            }
        }
        return result
    }

    fun setChannel(channelToSet: ProgramGuideChannel) {
        channel = channelToSet
    }

    fun setTimelineRow(timelineRow: ProgramGuideTimelineRow) {
        timeRowView = timelineRow
    }

    /** [ProgramGuideHolder]のインスタンスを設定します。 */
    fun setProgramGuideFragment(fragment: ProgramGuideHolder<*>) {
        programGuideHolder = fragment
        programGuideManager = programGuideHolder.programGuideManager
    }

    /** `currentScrollOffset`でスクロールをリセットします。 */
    fun resetScroll(scrollOffset: Int) {
        val channel = channel
        val startTime =
            ProgramGuideUtil.convertPixelToMillis(scrollOffset) + programGuideManager.getStartTime()
        val position = if (channel == null) {
            -1
        } else {
            programGuideManager.getProgramIndexAtTime(channel.id, startTime)
        }
        if (position < 0) {
            layoutManager?.scrollToPosition(0)
        } else if (channel?.id != null) {
            val slug = channel.id
            val entry = programGuideManager.getScheduleForChannelIdAndIndex(slug, position)
            val offset = ProgramGuideUtil.convertMillisToPixel(
                programGuideManager.getStartTime(),
                entry.startsAtMillis
            ) - scrollOffset
            (layoutManager as LinearLayoutManager).scrollToPositionWithOffset(position, offset)
            // b/31598505への回避策。プログラムの期間が長すぎる場合、
            // scrollToPositionWithOffset()の後にRecyclerView.onScrolled()が呼び出されません。
            // したがって、この場合、子の可視領域を自分で更新する必要があります。
            // scrollToPositionWithOffset()はrequestLayout()を呼び出すため、これをリッスンして、
            // レイアウトが調整された後、つまりスクロールが終了した後にプログラムアイテムの可視領域が正しく更新されることを確認します。
            viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        }
    }

    internal fun updateChildVisibleArea() {
        for (i in 0 until childCount) {
            val child = getChildAt(i) as ProgramGuideItemView<*>
            if (top < child.bottom && child.top < bottom) {
                child.updateVisibleArea()
            }
        }
    }

    fun isFirstItem(newFocus: View?): Boolean {
        return newFocus != null && layoutManager?.findViewByPosition(0) == newFocus
    }
}
