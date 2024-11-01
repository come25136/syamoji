package id.come25136.syamoji

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

class CommentActivity : AppCompatActivity() {
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ウィンドウの背景を透明にする場合
        window.setBackgroundDrawableResource(android.R.color.transparent)
        setContentView(R.layout.comment_viewer)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
