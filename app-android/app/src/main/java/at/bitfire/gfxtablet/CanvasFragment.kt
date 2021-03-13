package at.bitfire.gfxtablet

import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.preference.PreferenceManager
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.extractor.ExtractorsFactory
import com.google.android.exoplayer2.mediacodec.MediaCodecSelector
import com.google.android.exoplayer2.video.MediaCodecVideoRenderer
import java.io.IOException

class CanvasFragment : Fragment(),
                       Player.EventListener,
                       SharedPreferences.OnSharedPreferenceChangeListener
{
    private var mediaPlayer: SimpleExoPlayer? = null
    private lateinit var preferences: SharedPreferences

    private val networkClient: NetworkClient
        get() = ViewModelProvider(requireActivity()).get(NetworkViewModel::class.java).netClient



    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
    {
        val view = inflater.inflate(R.layout.fragment_canvas, container, false)
        val videoBackground: SurfaceView = view.findViewById(R.id.video_view)
        // notify CanvasView of the network client
        val canvas: CanvasView = view.findViewById(R.id.canvas)
        canvas.setNetworkClient(networkClient)

        // Notify the media player about surface creation and destruction
        videoBackground.holder?.addCallback(object : SurfaceHolder.Callback
        {
            override fun surfaceCreated(holder: SurfaceHolder)
            {
                mediaPlayer?.setVideoSurfaceHolder(holder)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

            override fun surfaceDestroyed(holder: SurfaceHolder)
            {
                mediaPlayer?.release()
            }
        })

        return view
    }

    override fun onAttach(context: Context)
    {
        super.onAttach(context)
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDetach()
    {
        super.onDetach()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onPause()
    {
        super.onPause()
        mediaPlayer?.release()
    }

    override fun onResume()
    {
        super.onResume()
        showTemplateImage()
        updateComputerScreenStream()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?)
    {
        // react to changed settings and update the background video or template image accordingly
        when(key)
        {
            SettingsActivity.KEY_SHOW_PC_SCREEN -> updateComputerScreenStream()

            SettingsActivity.KEY_TEMPLATE_IMAGE -> showTemplateImage()
        }
    }

    // background + video stream logic

    override fun onPlayerError(error: ExoPlaybackException)
    {
        Log.w(TAG, "ExoPlayer error: ${error.localizedMessage}")
        preferences.edit()!!.putBoolean(SettingsActivity.KEY_SHOW_PC_SCREEN, false)!!.apply()
    }

    /** Start the video player for the computer's screen stream or hide it according to the current settings.
     *
     * This will show or hide the video player in the background, depending on the setting to show the computer screen.
     * When shown, start the playback by reconnecting to the host computer. */
    private fun updateComputerScreenStream()
    {
        val videoBackground: SurfaceView = requireView().findViewById(R.id.video_view)
        val showScreen = preferences.getBoolean(SettingsActivity.KEY_SHOW_PC_SCREEN, false)
        if (showScreen)
        {
            val hostName = preferences.getString(SettingsActivity.KEY_PREF_HOST, "unknown.invalid")
            val port = NetworkClient.GFXTABLET_MPEG_DASH_PORT
            val videoServer = "http://$hostName:$port/screen.mpd"
            Log.i(TAG, "Connecting to $videoServer")

            val videoOnlyRenderersFactory =
                RenderersFactory { eventHandler, videoListener, audioListener, _, _ ->
                    arrayOf<Renderer>(
                        MediaCodecVideoRenderer(
                            requireContext(), MediaCodecSelector.DEFAULT, 0, eventHandler, videoListener, -1
                        )
                    )
                }
            val mediaPlayer = SimpleExoPlayer.Builder(requireContext(), videoOnlyRenderersFactory, ExtractorsFactory.EMPTY).build()
            this.mediaPlayer = mediaPlayer
            mediaPlayer.addListener(this)
            try
            {
                val src = MediaItem.fromUri(videoServer)
                mediaPlayer.setMediaItem(src)
                mediaPlayer.prepare()
                mediaPlayer.play()
                videoBackground.visibility = View.VISIBLE
            }
            catch (e: IOException)
            {
                e.printStackTrace()
            }
        }
        else
        {
            videoBackground.visibility = View.INVISIBLE
        }
    }

    private fun showTemplateImage()
    {
        val template: ImageView = requireView().findViewById(R.id.canvas_template)
        template.setImageDrawable(null)
        if (template.visibility == View.VISIBLE)
        {
            val picturePath = preferences.getString(SettingsActivity.KEY_TEMPLATE_IMAGE, null)
            if (picturePath != null)
            {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                {
                    val pictureUri = Uri.parse(picturePath)
                    template.setImageURI(pictureUri)
                }
                else
                {
                    try
                    {
                        // TODO load bitmap efficiently, for intended view size and display resolution
                        // https://developer.android.com/training/displaying-bitmaps/load-bitmap.html
                        val drawable: Drawable = BitmapDrawable(resources, picturePath)
                        template.setImageDrawable(drawable)
                    }
                    catch (e: Exception)
                    {
                        Toast.makeText(context, e.localizedMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    companion object
    {
        const val TAG = "CanvasFragment"
    }
}