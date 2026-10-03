package com.voxgest.dryrun

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity

class AvatarHelloPreviewActivity : ComponentActivity() {

    private lateinit var videoView: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 36, 24, 24)
            setBackgroundColor(Color.rgb(246, 250, 252))
        }

        val title = TextView(this).apply {
            text = "FSL Avatar Test — HELLO"
            textSize = 22f
            setTextColor(Color.rgb(18, 43, 58))
            gravity = Gravity.CENTER
            setPadding(8, 8, 8, 16)
        }

        val status = TextView(this).apply {
            text = "Experimental validated Blender 3.2 avatar preview"
            textSize = 14f
            setTextColor(Color.DKGRAY)
            gravity = Gravity.CENTER
            setPadding(8, 0, 8, 16)
        }

        videoView = VideoView(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val replay = Button(this).apply {
            text = "Replay HELLO"
            setOnClickListener {
                videoView.seekTo(0)
                videoView.start()
            }
        }

        root.addView(title)
        root.addView(status)
        root.addView(videoView)
        root.addView(replay)

        setContentView(root)

        val uri = Uri.parse(
            "android.resource://$packageName/${R.raw.fsl_hello_avatar_preview}"
        )

        videoView.setVideoURI(uri)

        videoView.setOnPreparedListener { player ->
            player.isLooping = false
            status.text = "Avatar preview ready"
            videoView.start()
        }

        videoView.setOnErrorListener { _, _, _ ->
            status.text = "Avatar unavailable. VoxGest is still ready to use."
            replay.isEnabled = false
            true
        }
    }

    override fun onPause() {
        if (::videoView.isInitialized && videoView.isPlaying) {
            videoView.pause()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::videoView.isInitialized) {
            videoView.stopPlayback()
        }
        super.onDestroy()
    }
}
