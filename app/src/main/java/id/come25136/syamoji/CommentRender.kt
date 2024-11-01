package id.come25136.syamoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.random.Random
import kotlin.system.measureTimeMillis

data class Comment(
    val text: String,
    val color: Int = Color.WHITE,
    var x: Float = 0f,
    var y: Float = 0f,
    var velocity: Float = 2.5f,
    var lane: Int = 0,
    var textWidth: Float = 0f,
    var bitmap: Bitmap? = null
)

class CommentRender @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    private val comments = mutableListOf<Comment>()
    private val paint = Paint().apply {
        isAntiAlias = true
    }
    private var drawThread: Thread? = null
    private var drawing = false
    private val lanes = mutableListOf<MutableList<Comment>>()
    private var laneHeight: Float = 0f
    private var maxLanes = 0

    private val globalFontSize = 45f
    private val spacing = 30f
    private val topPadding = 10f

    private val fontMetrics = paint.fontMetrics
    private val ascent = fontMetrics.ascent
    private val descent = fontMetrics.descent
    private val totalFontHeight = (-ascent) + descent

    private var lastTime = System.currentTimeMillis()

    init {
        paint.textSize = globalFontSize
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSLUCENT)
//        setZOrderOnTop(true)
    }

    private fun calculateLaneHeight(): Float {
        return totalFontHeight + spacing
    }

    fun addComment(comment: Comment) {
        Thread {
            comment.textWidth = paint.measureText(comment.text)
            paint.color = comment.color
            val textBitmap = createTextBitmap(comment.text, paint)
            comment.bitmap = textBitmap
            comment.x = width.toFloat()

            val availableLaneIndex = lanes.indices.find { lane ->
                val lastCommentInLane = lanes[lane].lastOrNull()
                lastCommentInLane == null || (lastCommentInLane.x + lastCommentInLane.textWidth + spacing) < width.toFloat()
            }

            if (availableLaneIndex != null) {
                comment.lane = availableLaneIndex
                comment.y = topPadding + (availableLaneIndex * laneHeight) + (-ascent)
                lanes[availableLaneIndex].add(comment)
            } else {
                val randomLane = Random.nextInt(0, maxLanes)
                comment.lane = randomLane
                comment.y = topPadding + (randomLane * laneHeight) + (-ascent)
                synchronized(lanes[randomLane]) {
                    lanes[randomLane].add(comment)
                }
            }

            synchronized(comments) {
                comments.add(comment)
                if (comments.size > 300) {
                    removeOldestComment()
                }
            }
        }.start()
    }

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

    private fun drawComments(canvas: Canvas?) {
        if (canvas == null) return

        val fps = 60
        val availableTime = 1000 / fps
        val elapsedTime = System.currentTimeMillis() - lastTime
        if (elapsedTime < availableTime) {
//            Thread.sleep(availableTime - elapsedTime)
        }
        lastTime = System.currentTimeMillis()

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val time = measureTimeMillis {
            synchronized(comments) {
                for (lane in lanes) {
                    synchronized(lane) {
                        val iterator = lane.iterator()
                        synchronized(iterator) {
                            while (iterator.hasNext()) {
                                val comment = iterator.next()
                                synchronized(comment) {
                                    comment.bitmap?.let {
                                        canvas.drawBitmap(it, comment.x, comment.y, null)
                                    }
                                    comment.x -= comment.velocity * (elapsedTime / availableTime)
                                    if (comment.x < -comment.textWidth) {
                                        iterator.remove()
                                        lanes[comment.lane].remove(comment)
                                        comments.remove(comment)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
//        Log.d("CommentRender", "drawComments: ${time}ms")
    }

    private var _initialized = false
    override fun surfaceCreated(holder: SurfaceHolder) {
        laneHeight = calculateLaneHeight()
        maxLanes = (height / laneHeight).toInt().coerceAtLeast(1)
        lanes.clear()
        for (i in 0 until maxLanes) {
            lanes.add(mutableListOf())
        }

        drawing = true
        drawThread = Thread {
            while (drawing) {
                val canvas: Canvas
                val time = measureTimeMillis {
                    canvas = holder.lockHardwareCanvas()
                }
//                Log.d("CommentRender", "lockCanvas: ${time}ms")
                try {
                    drawComments(canvas)
                } finally {
                    val time = measureTimeMillis {
                        // lockHardwareCanvas使用時はリフレッシュレートより早く呼ぶと次の描画まで待たされるので注意
                        holder.unlockCanvasAndPost(canvas)
                    }
//                    Log.d("CommentRender", "unlockCanvasAndPost: ${time}ms")
                }
                Thread.sleep(16)  // 30fps=33 | 60fps=16
            }
        }.apply { start() }

        _initialized = true
    }

    fun isInitialized(): Boolean {
        return _initialized
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        drawing = false
        drawThread?.join()  // スレッド終了待ち
    }
}
