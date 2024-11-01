package id.come25136.syamoji

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.util.AttributeSet
import android.util.Log
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
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
) : GLSurfaceView(context, attrs) {

    private val renderer = CommentRenderer(context)
    private var initialized = false  // 初期化フラグ

    init {
        setEGLContextClientVersion(2) // OpenGL ES 2.0を使用
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(PixelFormat.TRANSLUCENT)
        setZOrderOnTop(true)

        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun addComment(comment: Comment) {
        renderer.addComment(comment)
    }

    fun isInitialized(): Boolean {
        return initialized
    }

    private inner class CommentRenderer(private val context: Context) : Renderer {
        private val comments = mutableListOf<Comment>()
        private val paint = Paint().apply {
            isAntiAlias = true
            textSize = 40f // フォントサイズの設定
        }

        private var lastTime = System.currentTimeMillis()
        private val spacing = 20f
        private var laneHeight: Float = 0f
        private var maxLanes = 0

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 0f)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
            initialized = true  // surfaceCreatedが呼ばれたタイミングで初期化フラグをtrueにする
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
            laneHeight = paint.textSize + spacing
            maxLanes = (height / laneHeight).toInt().coerceAtLeast(1)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f) // 透明な背景
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            val elapsedTime = System.currentTimeMillis() - lastTime
            val availableTime = 16  // 60fpsに相当
            if (elapsedTime < availableTime) {
                Thread.sleep((availableTime - elapsedTime).toLong())
            }
            lastTime = System.currentTimeMillis()

            val time = measureTimeMillis {
                try {
                    synchronized(comments) {
                        for (comment in comments) {
                            synchronized(comment) {
                                comment.bitmap?.let {
                                    drawBitmap(it, comment.x, comment.y)
                                }
                                comment.x -= comment.velocity * (elapsedTime / availableTime)
                                if (comment.x < -comment.textWidth) {
                                    comment.bitmap?.recycle() // メモリ解放のためBitmapをリサイクル
                                    comments.remove(comment)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                }
            }
            Log.d("CommentRender", "drawComments: ${time}ms")

            Thread.sleep(100)
        }

        fun addComment(comment: Comment) {
            comment.bitmap = createTextBitmap(comment.text, paint, comment.color)
            comment.x = 1.0f // 初期のX位置（画面右端から左へ流れる）
            comment.y = Random.nextInt(0, maxLanes) * laneHeight
            synchronized(comments) {
                comments.add(comment)
            }
        }

        private fun createTextBitmap(text: String, paint: Paint, color: Int): Bitmap {
            paint.color = color // コメントの色を設定
            val width = paint.measureText(text).toInt()
            val height = (paint.descent() - paint.ascent()).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawText(text, 0f, -paint.ascent(), paint) // ビットマップにテキストを描画
            return bitmap
        }


        private fun drawBitmap(bitmap: Bitmap, x: Float, y: Float) {
            val textureId = loadTexture(bitmap)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)

            val vertices = floatArrayOf(
                x, y, 0.0f,               // 左上
                x + bitmap.width, y, 0.0f, // 右上
                x, y + bitmap.height, 0.0f, // 左下
                x + bitmap.width, y + bitmap.height, 0.0f // 右下
            )

            val vertexBuffer = createFloatBuffer(vertices)
            GLES20.glVertexAttribPointer(0, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(0)

            val textureCoordinates = floatArrayOf(
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f
            )
            val textureBuffer = createFloatBuffer(textureCoordinates)
            GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
            GLES20.glEnableVertexAttribArray(1)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDeleteTextures(1, intArrayOf(textureId), 0)
        }

        private fun loadTexture(bitmap: Bitmap): Int {
            val texture = IntArray(1)
            GLES20.glGenTextures(1, texture, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0])

            GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameterf(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR.toFloat()
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE
            )
            GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE
            )

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

            return texture[0]
        }

        private fun createFloatBuffer(data: FloatArray): java.nio.FloatBuffer {
            return java.nio.ByteBuffer.allocateDirect(data.size * 4).run {
                order(java.nio.ByteOrder.nativeOrder())
                asFloatBuffer().apply {
                    put(data)
                    position(0)
                }
            }
        }
    }
}
