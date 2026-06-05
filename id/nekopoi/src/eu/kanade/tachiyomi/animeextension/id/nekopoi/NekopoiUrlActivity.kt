package eu.kanade.tachiyomi.animeextension.id.nekopoi

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * URL activity for handling nekopoi.care links
 */
class NekopoiUrlActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = intent?.data
            putExtra("source", Nekopoi().toString())
        }

        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
        finish()
    }
}
