package id.come25136.syamoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.system.measureTimeMillis

data class Comment(
    val text: String,
    val color: Int = Color.WHITE,
    var x: Float = 0f,
    var y: Float = 0f,
    var velocity: Float = 0f,
    var lane: Int = 0, // 上から0インデックス
    var width: Float = 0f,
    var bitmap: Bitmap? = null
)

class CommentRender @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {
    private val globalFontSize = 45f

    private val comments = mutableListOf<Comment>()
    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textSize = globalFontSize
    }
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.DEFAULT_BOLD
        textSize = globalFontSize
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val copyPaint = Paint().apply {
        color = Color.argb((255 * 0.9).toInt(), 255, 255, 255)
    }
    private var drawThread: Thread? = null
    private var drawing = false
    private var laneHeight: Float = 0f
    private var maxLanes = 0

    private val spacing = 3f
    private val topPadding = 5f

    private val fontMetrics = paint.fontMetrics
    private val ascent = fontMetrics.ascent
    private val descent = fontMetrics.descent
    private val totalFontHeight = (-ascent) + descent

    private var lastTime = System.currentTimeMillis()

    private val commentQueue = ConcurrentLinkedQueue<Comment>()

    private val threadPool = Executors.newFixedThreadPool(10)

    private val FPS = 60 // 1秒間のフレーム数
    private val MOVE_DURATION_SECONDS = 5 // コメントが画面を横切る時間（秒）

    init {
        holder.addCallback(this)
        holder.setFormat(PixelFormat.TRANSLUCENT)
//        setZOrderOnTop(true)
    }

    private fun calculateLaneHeight(): Float {
        return totalFontHeight + spacing
    }

    fun addComment(nxPayload: String) {
        threadPool.execute {
            while (!isInitialized()) {
                Thread.sleep(100L)
            }

            val obj = JSONObject(nxPayload)
            val chat = obj.has("chat")
            if (!chat) return@execute

            val comment = Comment(
                text = obj.getJSONObject("chat").getString("content"),
                color = Color.WHITE,
            )

            comment.width = paint.measureText(comment.text)
            paint.color = comment.color
            val textBitmap = createTextBitmap(comment.text, paint)
            comment.bitmap = textBitmap
            comment.x = width.toFloat()

            comment.velocity = (width + comment.width) / (MOVE_DURATION_SECONDS * FPS)

            commentQueue.offer(comment)
        }
    }

    private fun createTextBitmap(text: String, paint: Paint): Bitmap {
        val width = paint.measureText(text).toInt()
        var height = (paint.descent() - paint.ascent()).toInt()
        if (height <= 0) {
            height = 1
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawText(text, 0f, -paint.ascent(), strokePaint)
        canvas.drawText(text, 0f, -paint.ascent(), paint)
        return bitmap
    }

    private fun findAvailableLane(comment: Comment): Int? {
        for (lane in 0 until maxLanes) {
            val laneComments = comments.filter { it.lane == lane }

            val isLaneAvailable = laneComments.none { existingComment ->
                val moveDistance = comment.velocity * MOVE_DURATION_SECONDS
                val existingMoveDistance = existingComment.velocity * MOVE_DURATION_SECONDS

                comment.x - moveDistance < existingComment.x + existingComment.width - existingMoveDistance
            }
            if (isLaneAvailable) {
                return lane
            }
        }
        return null
    }

    private fun drawComments(canvas: Canvas?) {
        if (canvas == null) return

        val fps = 60
        val availableTime = 1000 / fps
        val elapsedTime = System.currentTimeMillis() - lastTime
        if (elapsedTime < availableTime) {
//            Thread.sleep(availableTime - elapsedTime)
        }

        val componentWidth = width.toFloat()

        lastTime = System.currentTimeMillis()

        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        while (commentQueue.isNotEmpty()) {
            val comment = commentQueue.poll()

            val availableLaneIndex = findAvailableLane(comment)

            if (availableLaneIndex != null) {
                comment.lane = availableLaneIndex
            } else {
                comment.lane = Random.nextInt(0, maxLanes)
            }

            comment.y = topPadding + (comment.lane * laneHeight)

            comments.add(comment)
        }

        val time = measureTimeMillis {
            val iterator = comments.iterator()

            while (iterator.hasNext()) {
                val comment = iterator.next()

                comment.bitmap?.let {
                    canvas.drawBitmap(it, comment.x, comment.y, copyPaint)
                }
                comment.x -= comment.velocity * (elapsedTime / availableTime)

                if (comment.x < -comment.width) {
                    iterator.remove()
                }
            }
        }
//        Log.d("CommentRender", "drawComments: ${time}ms")
    }

    private var _initialized = false
    override fun surfaceCreated(holder: SurfaceHolder) {
        laneHeight = calculateLaneHeight()
        maxLanes = (height / laneHeight).toInt().coerceAtLeast(1)

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
