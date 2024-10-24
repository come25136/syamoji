// app/src/main/java/id/come25136/syamoji/CommentRender.kt
package id.come25136.syamoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.View
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

data class Comment(
    val text: String,
    val color: Int = Color.WHITE,
    var x: Float = 0f,
    var y: Float = 0f,
    var velocity: Float = 5f, // コメントの移動速度
    var lane: Int = 0, // コメントが所属するレーン番号
    var textWidth: Float = 0f, // テキスト幅のキャッシュ
    var bitmap: Bitmap? = null // テキストをレンダリングしたビットマップ
)

class CommentRender @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val comments = mutableListOf<Comment>() // 全コメントを保持
    private val paint = Paint().apply {
        isAntiAlias = true
    }
    private var job: Job? = null
    private val lanes = mutableListOf<MutableList<Comment>>() // レーンごとのコメントリスト
    private var laneHeight: Float = 0f // レーンの高さ
    private var maxLanes = 0

    // グローバル設定
    private val globalFontSize = 40f // グローバルなフォントサイズ
    private val spacing = 20f // コメント間の最小スペース（ピクセル単位）
    private val topPadding = 10f // ビューの上部パディング（ピクセル単位）

    // フォントメトリクスの取得
    private val fontMetrics = paint.fontMetrics
    private val ascent = fontMetrics.ascent // 負の値
    private val descent = fontMetrics.descent // 正の値
    private val totalFontHeight = (-ascent) + descent // フォントの総高さ

    // バックグラウンド用のPaintインスタンス
    private val backgroundPaint = Paint().apply {
        isAntiAlias = true
        textSize = globalFontSize
    }

    private var lastTime = System.currentTimeMillis()

    init {
        paint.textSize = globalFontSize
    }

    private var bitmap: Bitmap? = null
    private var bitmapCanvas: Canvas? = null
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        laneHeight = calculateLaneHeight()
        maxLanes = (h / laneHeight).toInt().coerceAtLeast(1) // 最低1レーン
        lanes.clear()
        for (i in 0 until maxLanes) {
            lanes.add(mutableListOf())
        }
        bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmapCanvas = Canvas(bitmap!!)
    }

    private fun calculateLaneHeight(): Float {
        return totalFontHeight + spacing
    }

    // コメントを追加するメソッド
    fun addComment(comment: Comment) {
        CoroutineScope(Dispatchers.Default).launch {
            comment.textWidth = backgroundPaint.measureText(comment.text)
            paint.color = comment.color
            val textBitmap = createTextBitmap(comment.text, paint)
            comment.bitmap = textBitmap
            comment.x = width.toFloat()

            val availableLaneIndex = lanes.indices.find { lane ->
                val lastCommentInLane = lanes[lane].lastOrNull()
                if (lastCommentInLane != null) {
                    return@find (lastCommentInLane.x + lastCommentInLane.textWidth + spacing) < width.toFloat()
                }
                true
            }

            if (availableLaneIndex != null) {
                comment.lane = availableLaneIndex
                comment.y = topPadding + (availableLaneIndex * laneHeight) + (-ascent)
                lanes[availableLaneIndex].add(comment)
            } else {
                val randomLane = Random.nextInt(0, maxLanes)
                comment.lane = randomLane
                comment.y = topPadding + (randomLane * laneHeight) + (-ascent)
                lanes[randomLane].add(comment)
            }

            withContext(Dispatchers.Main) {
                comments.add(comment)
                if (comments.size > 300) {
                    removeOldestComment()
                }
            }
        }
    }

    // テキストをビットマップにレンダリングする関数
    private fun createTextBitmap(text: String, paint: Paint): Bitmap {
        val width = paint.measureText(text).toInt()
        val height = (paint.descent() - paint.ascent()).toInt()
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(text, 0f, -paint.ascent(), paint)
        return bitmap
    }

    private fun removeOldestComment() {
        if (comments.isNotEmpty()) {
            val oldestComment = comments.removeAt(0)
            lanes[oldestComment.lane].remove(oldestComment)
        }
    }

    private var drawing = false
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val fps = 30
        val availableTime = 1000 / fps
        val elapsedTime = System.currentTimeMillis() - lastTime
        if (elapsedTime < availableTime) {
            bitmap?.let {
                canvas.drawBitmap(it, 0f, 0f, null)
            }

            return
        }
        lastTime = System.currentTimeMillis()

        drawing = true

//        canvas.drawColor(Color.TRANSPARENT) // 背景をクリア
        bitmapCanvas!!.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

        for (lane in lanes) {
            val iterator = lane.iterator()
            while (iterator.hasNext()) {
                try {
                    val comment = iterator.next()
                    if (comment.bitmap != null) {
                        bitmapCanvas!!.drawBitmap(comment.bitmap!!, comment.x, comment.y, null)
                    }

                    comment.x -= comment.velocity * (elapsedTime / availableTime)
                    if (comment.x < -comment.textWidth) {
                        iterator.remove()
                        comments.remove(comment)
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }

        bitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }

                drawing = false
    }

    private var _isInitialized = false
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        _isInitialized = true

        job = CoroutineScope(Dispatchers.Default).launch {

            while (isActive) {
                delay(5)
//                Log.d("CommentRender", "${elapsedTime}, ${availableTime - elapsedTime}")
                postInvalidate() // 描画をリクエスト
            }
        }
    }

    fun isInitialized(): Boolean {
        return _isInitialized
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        job?.cancel()
    }
}
