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

package com.egeniq.androidtvprogramguide

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.Range
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.view.children
import androidx.leanback.widget.HorizontalGridView
import com.egeniq.androidtvprogramguide.entity.ProgramGuideChannel
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView
import com.egeniq.androidtvprogramguide.row.ProgramGuideRowGridView
import com.egeniq.androidtvprogramguide.util.OnRepeatedKeyInterceptListener
import com.egeniq.androidtvprogramguide.util.ProgramGuideUtil
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class ProgramGuideGridView<T>(context: Context, attrs: AttributeSet?, defStyle: Int) :
    HorizontalGridView(context, attrs, defStyle) {

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    companion object {
        private const val INVALID_INDEX = -1
        private val FOCUS_AREA_SIDE_MARGIN_MILLIS = TimeUnit.MINUTES.toMillis(15)
        private val TAG: String = ProgramGuideGridView::class.java.name
    }

    interface ChildFocusListener {
        /**
         * フォーカスが移動する前に呼び出されます。`ProgramGrid`の子のみが渡されます。
         * `ProgramGuideGridView#setChildFocusListener(ChildFocusListener)`を参照してください。
         */
        fun onRequestChildFocus(oldFocus: View?, newFocus: View?)
    }

    interface ScheduleSelectionListener<T> {
        // 何も選択されていない場合はnullになることがあります
        fun onSelectionChanged(schedule: ProgramGuideSchedule<T>?)
        fun onChannelSelected(channel: ProgramGuideChannel)
        fun onChannelClicked(channel: ProgramGuideChannel)
    }

    private lateinit var programGuideManager: ProgramGuideManager<*>

    // 新しいフォーカスは[focusRangeLeft, focusRangeRight]と重なります。
    private var focusRangeTop: Int = 0
    private var focusRangeBottom: Int = 0
    private var lastLeftRightDirection: Int = 0
    private var internalKeepCurrentProgramFocused: Boolean = false
    private val tempRect = Rect()
    private var nextFocusByLeftRight: View? = null
    private val columnWidth: Int
    private val selectionRow: Int
    private var lastFocusedView: View? = null
    private var correctScheduleView: View? = null

    private val onRepeatedKeyInterceptListener: OnRepeatedKeyInterceptListener

    var childFocusListener: ChildFocusListener? = null
    var scheduleSelectionListener: ScheduleSelectionListener<T>? = null

    var featureKeepCurrentProgramFocused = true
        set(value) {
            field = value
            internalKeepCurrentProgramFocused = internalKeepCurrentProgramFocused && value
        }

    var featureFocusWrapAround = true

    var featureNavigateWithChannelKeys = false

    var overlapStart = 0

    private val programManagerListener = object : ProgramGuideManager.Listener {

        override fun onSchedulesUpdated() {
            // 何もしない
        }

        override fun onTimeRangeUpdated() {
            // 時間範囲が変更された場合、フォーカス状態をクリアします。
            clearLeftRightFocusState(null)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private val globalFocusChangeListener =
        ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
            if (newFocus !== nextFocusByLeftRight) {
                // UP/DOWNボタン以外のボタンでフォーカスが変更された場合、
                // フォーカス状態をクリアします。
                clearLeftRightFocusState(newFocus)
            }
            nextFocusByLeftRight = null
            if (ProgramGuideUtil.isDescendant(this@ProgramGuideGridView, newFocus)) {
                lastFocusedView = newFocus
                if (newFocus is ProgramGuideItemView<*> && (correctScheduleView == null || correctScheduleView == newFocus)) {
                    scheduleSelectionListener?.onSelectionChanged(newFocus.schedule as ProgramGuideSchedule<T>?)
                } else if (newFocus.id == R.id.programguide_channel_container) {
                    // チャンネルを持つ行グリッドを見つけます
                    val matchingColumn =
                        (newFocus.parent as? ViewGroup)?.children?.firstOrNull { it is ProgramGuideRowGridView } as? ProgramGuideRowGridView
                    val channel = matchingColumn?.channel
                    if (channel != null) {
                        scheduleSelectionListener?.onChannelSelected(channel)
                    } else {
                        Log.e(TAG, "現在の選択に対してチャンネルを特定できませんでした！")
                        scheduleSelectionListener?.onSelectionChanged(null)
                    }
                }
                correctScheduleView = null
            } else {
                scheduleSelectionListener?.onSelectionChanged(null)
            }
        }


    init {
        clearLeftRightFocusState(null)
        // 画面外のものはキャッシュしないでください。通常、ジャンクを減らすために画面外のビューをプリフェッチおよびプリポピュレートすることは良いことですが、
        // プログラムガイドはすべての方向にスクロールできるため、スクロール方向のビューだけでなく、垂直方向のビューも最新の状態に保つ必要があります。
        // 例えば、水平にスクロールするとき、現在のビューポートの上下の行も更新する必要があります。
        setItemViewCacheSize(0)
        val res = context.resources
        columnWidth = res.getDimensionPixelSize(R.dimen.programguide_channel_column_width)
        selectionRow = res.getInteger(R.integer.programguide_selection_row)
        onRepeatedKeyInterceptListener = OnRepeatedKeyInterceptListener(this)
        setOnKeyInterceptListener(onRepeatedKeyInterceptListener)
    }

    /**
     * グリッドビューを初期化します。ビューが実際にウィンドウにアタッチされる前に呼び出される必要があります。
     */
    internal fun initialize(programManager: ProgramGuideManager<*>) {
        programGuideManager = programManager
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusChangeListener)
        if (!isInEditMode) {
            programGuideManager.listeners.add(programManagerListener)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusChangeListener)
        if (!isInEditMode) {
            programGuideManager.listeners.remove(programManagerListener)
        }
        clearLeftRightFocusState(null)
    }


    /** 現在フォーカスされているアイテムの水平範囲を返します。 */
    internal fun getFocusRange(): Range<Int> {
        if (focusRangeTop == Int.MIN_VALUE && focusRangeBottom == Int.MAX_VALUE) {
            clearLeftRightFocusState(null)
        }
        return Range(focusRangeTop, focusRangeBottom)
    }

    private fun updateLeftRightFocusState(focused: View, direction: Int) {
        lastLeftRightDirection = direction
        val bottomMostFocusablePosition = getBottomMostFocusablePosition()
        val focusedRect = tempRect

        // 小さい幅のアイテムにフォーカスするのを避けるために、位置を最も右のフォーカス可能な位置でクリップします。
        focused.getGlobalVisibleRect(focusedRect)
        focusRangeTop = min(focusRangeTop, bottomMostFocusablePosition)
        focusRangeBottom = min(focusRangeBottom, bottomMostFocusablePosition)
        focusedRect.top = min(focusedRect.top, bottomMostFocusablePosition)
        focusedRect.bottom = min(focusedRect.bottom, bottomMostFocusablePosition)

        if (focusedRect.top > focusRangeBottom || focusedRect.bottom < focusRangeTop) {
            Log.w(TAG, "現在のフォーカスが[focusRangeLeft, focusRangeRight]の範囲外です")
            focusRangeTop = focusedRect.top
            focusRangeBottom = focusedRect.bottom
            return
        }
        focusRangeTop = max(focusRangeTop, focusedRect.top)
        focusRangeBottom = min(focusRangeBottom, focusedRect.bottom)
    }

    private fun clearLeftRightFocusState(focus: View?) {
        lastLeftRightDirection = 0
        if (layoutDirection == LAYOUT_DIRECTION_LTR) {
            focusRangeTop = overlapStart
            focusRangeBottom = getBottomMostFocusablePosition()
        } else {
            focusRangeTop = getTopMostFocusablePosition()
            focusRangeBottom = if (!getGlobalVisibleRect(tempRect)) {
                Int.MAX_VALUE
            } else {
                tempRect.height() - overlapStart
            }

        }
        nextFocusByLeftRight = null
        // フォーカスがプログラムアイテムでない場合、グリッドに戻るときに現在のプログラムにフォーカスをドロップします
        // この機能フラグが有効な場合のみ使用されます
        internalKeepCurrentProgramFocused =
            featureKeepCurrentProgramFocused && (focus !is ProgramGuideItemView<*> || ProgramGuideUtil.isCurrentProgram(
                focus
            ))
    }

    private fun getBottomMostFocusablePosition(): Int {
        return if (!getGlobalVisibleRect(tempRect)) {
            Integer.MAX_VALUE
        } else tempRect.bottom - ProgramGuideUtil.convertMillisToPixel(FOCUS_AREA_SIDE_MARGIN_MILLIS)
    }

    private fun getTopMostFocusablePosition(): Int {
        return if (!getGlobalVisibleRect(tempRect)) {
            Integer.MIN_VALUE
        } else tempRect.top + ProgramGuideUtil.convertMillisToPixel(FOCUS_AREA_SIDE_MARGIN_MILLIS)
    }

    private fun focusFind(focused: View, direction: Int): View? {
        val focusedChildIndex = getFocusedChildIndex()
        if (focusedChildIndex == INVALID_INDEX) {
            Log.w(TAG, "フォーカスされている子ビューがありません")
            return null
        }
        val nextChildIndex =
            if (direction == View.FOCUS_LEFT) focusedChildIndex - 1 else focusedChildIndex + 1
        if (nextChildIndex < 0 || nextChildIndex >= childCount) {
            // 頭または末尾に達した場合のラップアラウンド
            if (featureFocusWrapAround) {
                if (selectedPosition == 0) {
                    adapter?.let { adapter ->
                        scrollToPosition(adapter.itemCount - 1)
                    }
                    return null
                } else if (adapter != null && selectedPosition == adapter!!.itemCount - 1) {
                    scrollToPosition(0)
                    return null
                }
                return focused
            } else {
                return null
            }
        }
        val nextFocusedProgram = ProgramGuideUtil.findNextFocusedProgram(
            getChildAt(nextChildIndex),
            focusRangeTop,
            focusRangeBottom,
            internalKeepCurrentProgramFocused
        )
        if (nextFocusedProgram != null) {
            nextFocusedProgram.getGlobalVisibleRect(tempRect)
            nextFocusByLeftRight = nextFocusedProgram

        } else {
            Log.w(TAG, "focusFindは適切なフォーカス可能なものを見つけませんでした")
        }
        return nextFocusedProgram
    }

    // 返される値はVerticalGridViewの位置ではありません。しかし、それは表示されている子の中でViewGroupのインデックスです。
    private fun getFocusedChildIndex(): Int {
        for (i in 0 until childCount) {
            if (getChildAt(i).hasFocus()) {
                return i
            }
        }
        return INVALID_INDEX
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        nextFocusByLeftRight = null
        if (focused == null || focused !== this && !ProgramGuideUtil.isDescendant(this, focused)) {
            return super.focusSearch(focused, direction)
        }
        if (direction == View.FOCUS_LEFT || direction == View.FOCUS_RIGHT) {
            updateLeftRightFocusState(focused, direction)
            val nextFocus = focusFind(focused, direction)
            if (nextFocus != null) {
                return nextFocus
            }
        }
        return super.focusSearch(focused, direction)
    }

    override fun requestChildFocus(child: View, focused: View) {
        childFocusListener?.onRequestChildFocus(focusedChild, child)
        super.requestChildFocus(child, focused)
    }

    override fun onRequestFocusInDescendants(
        direction: Int,
        previouslyFocusedRect: Rect?
    ): Boolean {
        if (lastFocusedView?.isShown == true) {
            if (lastFocusedView?.requestFocus() == true) {
                return true
            }
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }

    fun focusCurrentProgram() {
        internalKeepCurrentProgramFocused = true
        requestFocus()
    }

    fun isKeepCurrentProgramFocused(): Boolean {
        return internalKeepCurrentProgramFocused
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        // OnRepeatedKeyInterceptListenerを適切に処理する必要があります。フォーカスされたアイテムが画面のほぼ端にある場合、次のアイテムへのフォーカス変更は機能しません。
        // フォーカスされたアイテムの位置が希望の位置から遠すぎないように制限します。
        val focusedView = findFocus()
        if (focusedView != null && onRepeatedKeyInterceptListener.isFocusAccelerated) {
            val location = IntArray(2)
            getLocationOnScreen(location)
            val focusedLocation = IntArray(2)
            focusedView.getLocationOnScreen(focusedLocation)
            val x = focusedLocation[1] - location[1]

            val minX = (selectionRow - 1) * columnWidth
            if (x < minX) {
                scrollBy(x - minX, 0)
            }

            val maxY = (selectionRow + 1) * columnWidth
            if (x > maxY) {
                scrollBy(x - maxY, 0)
            }
        }
    }

    /**
     * チャンネルアップ/ダウンキーをインターセプトして、それらでナビゲートします。この機能が有効になっている場合。
     */
    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (featureNavigateWithChannelKeys && event?.action == KeyEvent.ACTION_DOWN) {
            val keyCode = event.keyCode
            val focusedChild = focusedChild
            if (keyCode == KeyEvent.KEYCODE_CHANNEL_UP) {
                focusFind(focusedChild, View.FOCUS_LEFT)?.requestFocus()
                return true
            } else if (keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN) {
                focusFind(focusedChild, View.FOCUS_RIGHT)?.requestFocus()
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    fun markCorrectChild(view: View) {
        correctScheduleView = view
    }
}
