package id.come25136.syamoji.util

import id.come25136.syamoji.BuildConfig
import okhttp3.Request

class RequestUtil {
    companion object {
        private val userAgent: String = "syamoji/come25136_${BuildConfig.REVISION} (Android)"

        fun requestBuilder(url: String): Request {
            return Request.Builder()
                .header("user-agent", userAgent)
                .url(url)
                .build()
        }
    }
}