package com.habitbell.app.engine

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.util.regex.Pattern

/**
 * Supported sound sources for background ambient soundscapes during meditation.
 */
enum class BackgroundSoundType {
    /** Continuous acoustic Aum / Om resonant drone synthesized or looped from assets. */
    DEFAULT_AUM,

    /** User-selected local audio file via Android Storage Access Framework URI. */
    CUSTOM_FILE,

    /** Online ambient stream streamed via YouTube IFrame player. */
    YOUTUBE_LINK,

    /** Complete background silence (only bell chimes will play). */
    NONE
}

/**
 * Coordinates ambient background soundscapes during active timer sessions.
 *
 * Supports multi-source audio delivery:
 * 1. Looping local audio via Android [MediaPlayer].
 * 2. Background audio streaming from YouTube URLs utilizing a sandboxed, ad-free
 *    headless [WebView] communicating via the YouTube IFrame Player API.
 *
 * @param context Android context for media playback and resource resolution.
 */
class BackgroundMusicManager(private val context: Context) {

    private val TAG = "BackgroundMusicManager"

    /** Main thread handler for dispatching UI-bound WebView calls. */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Native media player for local raw resources and SAF storage audio files. */
    private var mediaPlayer: MediaPlayer? = null

    /** Sandboxed headless WebView for hosting YouTube IFrame embed. */
    private var youtubeWebView: WebView? = null

    /** Currently active video ID loaded in WebView. */
    private var activeVideoId: String? = null

    /** State flag to prevent redundant restart loops. */
    private var isPlaying: Boolean = false

    /** Master toggle enabling or disabling background music playback. */
    var isEnabled: Boolean = true

    /** Currently selected sound source strategy. */
    var soundType: BackgroundSoundType = BackgroundSoundType.DEFAULT_AUM

    /** String URI of user-selected custom audio file. */
    var customAudioUri: String? = null

    /** YouTube video URL or ID for streaming ambient music. */
    var youtubeUrl: String = "https://youtu.be/x6UITRjhijI"

    /**
     * Normalized audio volume gain (0.0f to 1.0f).
     * Synchronously propagates volume changes to both [MediaPlayer] and the YouTube IFrame player.
     */
    var volume: Float = 0.35f
        set(value) {
            field = value.coerceIn(0f, 1f)
            try {
                mediaPlayer?.setVolume(field, field)
            } catch (_: Exception) {}
            setYouTubeVolume(field)
        }

    /**
     * Returns or instantiates the WebView. Attaching it to the Activity view hierarchy
     * ensures Chromium does not throttle or suspend background audio execution.
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(ctx: Context): WebView {
        if (youtubeWebView == null) {
            try {
                WebView.setWebContentsDebuggingEnabled(true)
            } catch (_: Exception) {}
            youtubeWebView = WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                        Log.d("YouTubeWebView", "[JS Console] ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        Log.d(TAG, "YouTube player page finished loading: $url")
                    }
                }
            }
        }
        return youtubeWebView!!
    }

    /**
     * Starts background soundscape according to the active [soundType].
     */
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
            BackgroundSoundType.CUSTOM_FILE, BackgroundSoundType.DEFAULT_AUM -> {
                stopYouTube()
                playMediaPlayer()
            }
            BackgroundSoundType.NONE -> {
                stop()
            }
        }
        isPlaying = true
    }

    /**
     * Pauses active media playback (both native MediaPlayer and YouTube IFrame).
     */
    fun pause() {
        isPlaying = false
        try {
            if (mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
            }
        } catch (_: Exception) {}

        sendYouTubeCommand("pauseVideo")
    }

    /**
     * Resumes background playback if enabled.
     */
    fun resume() {
        if (!isEnabled || soundType == BackgroundSoundType.NONE) return
        isPlaying = true
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

    /**
     * Stops both MediaPlayer and YouTube streams.
     */
    fun stop() {
        isPlaying = false
        stopMediaPlayer()
        stopYouTube()
    }

    /**
     * Safely halts and releases the native [MediaPlayer] instance.
     */
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

    /**
     * Pauses and stops YouTube playback without destroying the persistent WebView.
     */
    private fun stopYouTube() {
        sendYouTubeCommand("stopVideo")
        activeVideoId = null
    }

    /**
     * Instantiates and begins looping playback of a local audio track or bundled Aum chant drone.
     */
    private fun playMediaPlayer() {
        stopMediaPlayer()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        // 1. Check if user has an imported custom Aum file (internal filesDir)
        val internalAumFile = java.io.File(context.filesDir, "custom_aum.mp3")
        if ((soundType == BackgroundSoundType.DEFAULT_AUM || soundType == BackgroundSoundType.CUSTOM_FILE) &&
            internalAumFile.exists() && internalAumFile.length() > 0) {
            try {
                mediaPlayer = MediaPlayer().apply {
                    setAudioAttributes(audioAttributes)
                    setDataSource(internalAumFile.absolutePath)
                    isLooping = true
                    prepare()
                    setVolume(this@BackgroundMusicManager.volume, this@BackgroundMusicManager.volume)
                    start()
                }
                Log.d(TAG, "Playing internal custom Aum background track (default)")
                return
            } catch (e: Exception) {
                Log.w(TAG, "Failed playing internal custom_aum.mp3, trying URI/fallback", e)
            }
        }

        // 2. Handle user custom audio file via URI
        if (!customAudioUri.isNullOrBlank()) {
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
                Log.w(TAG, "Failed playing custom file, falling back to bundled Aum", e)
            }
        }

        // 3. Fallback to bundled pristine Aum audio from compile-time raw assets
        try {
            mediaPlayer = MediaPlayer.create(context, com.habitbell.app.R.raw.aum, audioAttributes, 0)?.apply {
                isLooping = true
                setVolume(this@BackgroundMusicManager.volume, this@BackgroundMusicManager.volume)
                start()
            }
            Log.d(TAG, "Playing bundled raw Aum ambient track")
        } catch (e: Exception) {
            Log.e(TAG, "Failed creating MediaPlayer with bundled R.raw.aum", e)
        }
    }

    /**
     * Initializes or commands the sandboxed WebView to play YouTube audio ad-free in an infinite loop.
     */
    private fun playYouTubeAudio() {
        val videoId = extractVideoId(youtubeUrl) ?: "x6UITRjhijI"

        mainHandler.post {
            try {
                val webView = getOrCreateWebView(context)

                // If already playing this exact video, simply resume it
                if (activeVideoId == videoId && isPlaying) {
                    sendYouTubeCommand("playVideo")
                    return@post
                }

                activeVideoId = videoId
                val targetVol = (volume * 100).toInt().coerceIn(10, 100)

                // Robust HTML using official YouTube IFrame Player API with auto ad-skipping and infinite loop
                val embedHtml = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <style>
                            * { margin: 0; padding: 0; box-sizing: border-box; }
                            html, body { width: 100%; height: 100%; background: #000000; overflow: hidden; }
                            #player { width: 100%; height: 100%; position: absolute; top: 0; left: 0; }
                            .video-ads, .ytp-ad-module, .ytp-ad-player-overlay { display: none !important; }
                        </style>
                    </head>
                    <body>
                        <div id="player"></div>
                        <script>
                            console.log("Initializing YouTube IFrame API for video: $videoId");
                            var tag = document.createElement('script');
                            tag.src = "https://www.youtube.com/iframe_api";
                            var firstScriptTag = document.getElementsByTagName('script')[0];
                            firstScriptTag.parentNode.insertBefore(tag, firstScriptTag);

                            var player;
                            function onYouTubeIframeAPIReady() {
                                console.log("onYouTubeIframeAPIReady fired for: $videoId");
                                player = new YT.Player('player', {
                                    width: '100%',
                                    height: '100%',
                                    videoId: '$videoId',
                                    playerVars: {
                                        'autoplay': 1,
                                        'controls': 0,
                                        'playsinline': 1,
                                        'loop': 1,
                                        'playlist': '$videoId',
                                        'enablejsapi': 1,
                                        'origin': 'https://www.youtube-nocookie.com',
                                        'rel': 0,
                                        'fs': 0,
                                        'modestbranding': 1,
                                        'disablekb': 1,
                                        'iv_load_policy': 3
                                    },
                                    events: {
                                        'onReady': function(event) {
                                            console.log("YouTube Player onReady! Unmuting and playing at volume: $targetVol");
                                            try {
                                                event.target.unMute();
                                                event.target.setVolume($targetVol);
                                                event.target.playVideo();
                                            } catch (err) {
                                                console.error("onReady play error:", err);
                                            }
                                        },
                                        'onStateChange': function(event) {
                                            console.log("YouTube Player state changed to: " + event.data);
                                            // Loop when video reaches end (0 = YT.PlayerState.ENDED)
                                            if (event.data === 0) {
                                                console.log("Video ended, looping back to beginning");
                                                event.target.seekTo(0);
                                                event.target.playVideo();
                                            } else if (event.data === 2) {
                                                console.log("Video paused");
                                            } else if (event.data === 1) {
                                                console.log("Video is actively playing!");
                                            }
                                        },
                                        'onError': function(event) {
                                            console.error("YouTube Player error code: " + event.data);
                                        }
                                    }
                                });
                            }

                            // Auto-click ad skip buttons continuously
                            setInterval(function() {
                                try {
                                    var btn = document.querySelector('.ytp-ad-skip-button, .ytp-ad-skip-button-modern, .ytp-ad-overlay-close-button');
                                    if (btn) {
                                        btn.click();
                                        console.log("Ad skip clicked");
                                    }
                                    var vid = document.querySelector('video');
                                    if (document.querySelector('.ad-showing') && vid) {
                                        vid.currentTime = vid.duration || 9999;
                                    }
                                } catch(e) {}
                            }, 500);
                        </script>
                    </body>
                    </html>
                """.trimIndent()

                webView.loadDataWithBaseURL("https://www.youtube-nocookie.com", embedHtml, "text/html", "UTF-8", null)
                Log.d(TAG, "Started ad-free YouTube player for video: $videoId")
            } catch (e: Exception) {
                Log.e(TAG, "Failed starting YouTube audio player", e)
            }
        }
    }

    /**
     * Dispatches a direct JavaScript command to the YouTube player instance.
     */
    private fun sendYouTubeCommand(command: String, args: String = "") {
        mainHandler.post {
            val script = when (command) {
                "playVideo" -> "if (typeof player !== 'undefined' && player.playVideo) player.playVideo();"
                "pauseVideo" -> "if (typeof player !== 'undefined' && player.pauseVideo) player.pauseVideo();"
                "stopVideo" -> "if (typeof player !== 'undefined' && player.stopVideo) player.stopVideo();"
                "setVolume" -> "if (typeof player !== 'undefined' && player.setVolume) player.setVolume($args);"
                else -> ""
            }
            if (script.isNotEmpty()) {
                youtubeWebView?.evaluateJavascript(script, null)
            }
        }
    }

    /**
     * Updates YouTube player volume.
     *
     * @param vol Normalized floating-point volume between `0.0f` and `1.0f`.
     */
    private fun setYouTubeVolume(vol: Float) {
        val intVol = (vol * 100).toInt().coerceIn(0, 100)
        sendYouTubeCommand("setVolume", "$intVol")
    }

    /**
     * Extracts an 11-character YouTube video ID from various URL structures (watch, shorts, embed, youtu.be).
     *
     * @param url Raw input URL string.
     * @return 11-character alphanumeric video ID, or null if no valid ID pattern was found.
     */
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

    /**
     * Releases active media players and destroys WebView instances.
     */
    fun release() {
        stop()
        mainHandler.post {
            try {
                youtubeWebView?.loadUrl("about:blank")
                youtubeWebView?.destroy()
            } catch (_: Exception) {}
            youtubeWebView = null
        }
    }
}
