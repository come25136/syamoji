package id.come25136.syamoji

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

        AndroidThreeTen.init(this)

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, EpgFragment())
            .commit()
    }
}