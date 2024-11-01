package id.come25136.syamoji

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.jakewharton.threetenabp.AndroidThreeTen

class MainActivity : AppCompatActivity() {

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main_2)
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }

//        startActivity(Intent(this, CommentActivity::class.java))
//        return

        try {
            AndroidThreeTen.init(this)
        } catch (e: Exception) {

        }

//        val intent = Intent(applicationContext, VideoRenderActivity::class.java)
//        intent.putExtra("streamUrl", "http://192.168.20.10:40772/api/channels/GR/16/services/23608/stream/")
//        startActivity(intent)

        val intent = Intent(applicationContext, CommentActivity::class.java)
        startActivity(intent)

//        supportFragmentManager.beginTransaction()
//            .replace(R.id.fragment_container, EpgFragment())
//            .commit()
    }
}