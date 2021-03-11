package at.bitfire.gfxtablet

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        setContentView(R.layout.activity_settings)
    }

    companion object {
        const val KEY_PREF_HOST           = "host_preference"
        const val KEY_PREF_STYLUS_ONLY    = "stylus_only_preference"
        const val KEY_DARK_CANVAS         = "dark_canvas_preference"
        const val KEY_KEEP_DISPLAY_ACTIVE = "keep_display_active_preference"
        const val KEY_TEMPLATE_IMAGE      = "key_template_image"
    }
}