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

package com.egeniq.androidtvprogramguide.util

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import com.egeniq.androidtvprogramguide.entity.ProgramGuideSchedule
import com.egeniq.androidtvprogramguide.item.ProgramGuideItemView
import java.util.concurrent.TimeUnit

object ProgramGuideUtil {
    private var HEIGHT_PER_HOUR = 0
    private const val INVALID_INDEX = -1

    var lastClickedSchedule: ProgramGuideSchedule<*>? = null

    /**
     * プログラムガイドで1時間に対応するピクセル幅を設定します。これがメインスレッドからのみ呼び出されると仮定し、
     * 同期化は行いません。
     */
    fun setHeightPerHour(heightPerHour: Int) {
        HEIGHT_PER_HOUR = heightPerHour
    }


    @JvmStatic
    fun convertMillisToPixel(millis: Long): Int {
        return (millis * HEIGHT_PER_HOUR / TimeUnit.HOURS.toMillis(1)).toInt()
    }

    @JvmStatic
    fun convertMillisToPixel(startMillis: Long, endMillis: Long): Int {
        // まずピクセルに変換して、丸め誤差の蓄積を避けます。
        return convertMillisToPixel(endMillis) - convertMillisToPixel(startMillis)
    }

    /** プログラムガイドで指定されたピクセルに対応するミリ秒を取得します。 */
    fun convertPixelToMillis(pixel: Int): Long {
        return pixel * TimeUnit.HOURS.toMillis(1) / HEIGHT_PER_HOUR
    }

    /**
     * フォーカス範囲に従って、指定されたプログラム行でフォーカスされるべきビューを返します。
     *
     * @param keepCurrentProgramFocused `true`の場合、可能であれば現在のプログラムにフォーカスし、
     * そうでない場合は一般的なロジックにフォールバックします。
     */
    fun findNextFocusedProgram(
        programRow: View,
        focusRangeTop: Int,
        focusRangeBottom: Int,
        keepCurrentProgramFocused: Boolean
    ): View? {
        val focusables = ArrayList<View>()
        findFocusables(programRow, focusables)

        if (lastClickedSchedule != null) {
            // 可能であれば現在のプログラムを選択します。
            for (i in focusables.indices) {
                val focusable = focusables[i]
                if (focusable is ProgramGuideItemView<*> && focusable.schedule?.id == lastClickedSchedule?.id) {
                    lastClickedSchedule = null
                    return focusable
                }
            }
            lastClickedSchedule = null
        }

        if (keepCurrentProgramFocused) {
            // 可能であれば現在のプログラムを選択します。
            for (i in focusables.indices) {
                val focusable = focusables[i]
                if (focusable is ProgramGuideItemView<*> && isCurrentProgram(focusable)) {
                    return focusable
                }
            }
        }

        // 完全に重なっているフォーカス可能なビューの中で最も大きいものを見つけます。
        var maxFullyOverlappedHeight = Integer.MIN_VALUE
        var maxPartiallyOverlappedHeight = Integer.MIN_VALUE
        var nextFocusIndex = INVALID_INDEX
        for (i in focusables.indices) {
            val focusable = focusables[i]
            val focusableRect = Rect()
            focusable.getGlobalVisibleRect(focusableRect)
            if (focusableRect.top <= focusRangeTop && focusRangeBottom <= focusableRect.bottom) {
                // 古いフォーカス範囲がフォーカス可能なビューの中に完全に収まっている場合、直接返します。
                return focusable
            } else if (focusRangeTop <= focusableRect.top && focusableRect.bottom <= focusRangeBottom) {
                // フォーカス可能なビューが古いフォーカス範囲の中に完全に収まっている場合、最も広いものを選択します。
                val height = focusableRect.height()
                if (height > maxFullyOverlappedHeight) {
                    nextFocusIndex = i
                    maxFullyOverlappedHeight = height
                }
            } else if (maxFullyOverlappedHeight == Integer.MIN_VALUE) {
                val overlappedHeight = if (focusRangeTop <= focusableRect.top)
                    focusRangeBottom - focusableRect.top
                else
                    focusableRect.bottom - focusRangeTop
                if (overlappedHeight > maxPartiallyOverlappedHeight) {
                    nextFocusIndex = i
                    maxPartiallyOverlappedHeight = overlappedHeight
                }
            }
        }
        return if (nextFocusIndex != INVALID_INDEX) {
            focusables[nextFocusIndex]
        } else null
    }

    /**
     * 指定された[ProgramGuideItemView]に表示されているプログラムが現在のプログラムである場合に`true`を返します。
     */
    fun isCurrentProgram(view: ProgramGuideItemView<*>): Boolean {
        return view.schedule?.isCurrentProgram == true
    }

    private fun findFocusables(v: View, outFocusable: ArrayList<View>) {
        if (v.isFocusable) {
            outFocusable.add(v)
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findFocusables(v.getChildAt(i), outFocusable)
            }
        }
    }

    /** 指定されたビューが指定されたコンテナの子孫である場合に`true`を返します。 */
    fun isDescendant(container: ViewGroup, view: View?): Boolean {
        if (view == null) {
            return false
        }
        var p: ViewParent? = view.parent
        while (p != null) {
            if (p === container) {
                return true
            }
            p = p.parent
        }
        return false
    }

    /**
     * 時間を指定された`timeUnit`に切り捨てます。例えば、時間が5:32:11でtimeUnitが1時間（60 * 60 * 1000）の場合、
     * 出力は5:00:00になります。
     */
    fun floorTime(timeMs: Long, timeUnit: Long): Long {
        return timeMs - timeMs % timeUnit
    }
}
