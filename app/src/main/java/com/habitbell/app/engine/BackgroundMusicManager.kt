package com.habitbell.app.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.regex.Pattern

enum class BackgroundSoundType {
    DEFAULT_AUM,
    CUSTOM_FILE,
    YOUTUBE_LINK,
    NONE
}

class BackgroundMusicManager(private val context: Context) {

    private val TAG = "BackgroundMusicManager"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var mediaPlayer: MediaPlayer? = null
    private var youtubeWebView: WebView? = null

    var isEnabled: Boolean = true
    var soundType: BackgroundSoundType = BackgroundSoundType.DEFAULT_AUM
    var customAudioUri: String? = null
    var youtubeUrl: String = "https://www.youtube.com/watch?v=x6UITRjhijI"
    var volume: Float = 0.35f
        set(value) {
            field = value.coerceIn(0f, 1f)
            try {
                mediaPlayer?.setVolume(field, field)
            } catch (_: Exception) {}
            setYouTubeVolume(field)
        }

    fun start() {
        if (!isEnabled || soundType == BackgroundSoundType.NONE) {
            stop()
            return
        }

        when (soundType) {
            BackgroundSoundType.YOUTUBE_LINK -> {
                stopMediaPlayer()
                playYouTubeAudio()
            }
            BackgroundSoundType.CUSTOM_FILE -> {
                stopYouTube()
                playMediaPlayer()
            }
            BackgroundSoundType.DEFAULT_AUM -> {
                stopYouTube()
                playMediaPlayer()
            }
            BackgroundSoundType.NONE -> {
                stop()
            }
        }
    }

    fun pause() {
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}

        sendYouTubeCommand("pauseVideo")
    }

    fun resume() {
        if (!isEnabled || soundType == BackgroundSoundType.NONE) return
        when (soundType) {
            BackgroundSoundType.YOUTUBE_LINK -> {
                sendYouTubeCommand("playVideo")
            }
            else -> {
                try {
                    if (mediaPlayer != null) {
                        mediaPlayer?.setVolume(volume, volume)
                        mediaPlayer?.start()
                    } else {
                        playMediaPlayer()
                    }
                } catch (_: Exception) {
                    playMediaPlayer()
                }
            }
        }
    }

    fun stop() {
        stopMediaPlayer()
        stopYouTube()
    }

    private fun stopMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (_: Exception) {}
        mediaPlayer = null
    }

    private fun stopYouTube() {
        mainHandler.post {
            try {
                sendYouTubeCommand("stopVideo")
                youtubeWebView?.loadUrl("about:blank")
                youtubeWebView?.destroy()
            } catch (_: Exception) {}
            youtubeWebView = null
        }
    }

    private fun playMediaPlayer() {
        stopMediaPlayer()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        if (soundType == BackgroundSoundType.CUSTOM_FILE && !customAudioUri.isNullOrBlank()) {
            try {
                val uri = Uri.parse(customAudioUri)
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(context, uri)
                    isLooping = true
                    prepare()
                    setVolume(this@BackgroundMusicManager.volume, this@BackgroundMusicManager.volume)
                    start()
                }
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed playing custom file, falling back to Aum", e)
            }
        }

        // Default Aum Chanting Drone
        val resId = context.resources.getIdentifier("aum_chant_drone", "raw", context.packageName)
        if (resId != 0) {
            mediaPlayer = MediaPlayer.create(context, resId, audioAttributes, 0)?.apply {
                isLooping = true
                setVolume(this@BackgroundMusicManager.volume, this@BackgroundMusicManager.volume)
                start()
            }
        }
    }

    private fun playYouTubeAudio() {
        val videoId = extractVideoId(youtubeUrl) ?: "x6UITRjhijI"

        mainHandler.post {
            try {
                if (youtubeWebView == null) {
                    youtubeWebView = WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.domStorageEnabled = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                injectAdBlocker(view)
                                setYouTubeVolume(volume)
                            }
                        }
                    }
                }

                val embedHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            body { margin: 0; background: #000000; overflow: hidden; }
                            iframe { width: 100vw; height: 100vh; border: none; }
                            .video-ads, .ytp-ad-module, .ytp-ad-player-overlay, .ytp-ad-overlay-container, #player-ads { display: none !important; }
                        </style>
                    </head>
                    <body>
                        <iframe id="ytPlayer"
                            src="https://www.youtube-nocookie.com/embed/$videoId?enablejsapi=1&autoplay=1&controls=0&modestbranding=1&rel=0&iv_load_policy=3&loop=1&playlist=$videoId&playsinline=1"
                            allow="autoplay; encrypted-media">
                        </iframe>
                    </body>
                    </html>
                """.trimIndent()

                youtubeWebView?.loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
                Log.d(TAG, "Started ad-free YouTube background player for video: $videoId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting YouTube audio player", e)
            }
        }
    }

    private fun injectAdBlocker(view: WebView?) {
        val js = """
            (function() {
                var css = '.video-ads, .ytp-ad-module, .ytp-ad-player-overlay, .ytp-ad-overlay-container, #player-ads { display: none !important; }';
                var style = document.createElement('style');
                style.textContent = css;
                document.head.appendChild(style);

                setInterval(function() {
                    var skipBtn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-overlay-close-button');
                    if (skipBtn) {
                        skipBtn.click();
                    }
                    var video = document.querySelector('video');
                    if (document.querySelector('.ad-showing') && video) {
                        video.currentTime = video.duration || 9999;
                    }
                }, 250);
            })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    private fun sendYouTubeCommand(command: String, args: String = "") {
        mainHandler.post {
            val json = if (args.isEmpty()) {
                "{\"event\":\"command\",\"func\":\"$command\",\"args\":\"\"}"
            } else {
                "{\"event\":\"command\",\"func\":\"$command\",\"args\":$args}"
            }
            val js = "document.querySelector('iframe')?.contentWindow?.postMessage('$json', '*');"
            youtubeWebView?.evaluateJavascript(js, null)
        }
    }

    private fun setYouTubeVolume(vol: Float) {
        val intVol = (vol * 100).toInt().coerceIn(0, 100)
        sendYouTubeCommand("setVolume", "[$intVol]")
    }

    fun extractVideoId(url: String): String? {
        val cleanUrl = url.trim()
        val patterns = listOf(
            "(?:v=|\\/v\\/|youtu\\.be\\/|\\/embed\\/|\\/shorts\\/)([a-zA-Z0-9_-]{11})",
            "^([a-zA-Z0-9_-]{11})$"
        )
        for (pattern in patterns) {
            val matcher = Pattern.compile(pattern).matcher(cleanUrl)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    fun release() {
        stop()
    }
}
