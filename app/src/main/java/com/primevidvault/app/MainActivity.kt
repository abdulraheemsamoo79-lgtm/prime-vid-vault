package com.primevidvault.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.net.URL

/**
 * PRIME VID VAULT v4.0 — Aurora UI + TikTok/Instagram tabs + Share-to-app + In-app updater
 *
 * Dark premium theme: deep black + midnight navy, violet → cyan gradient.
 * Bold gradient hero, unified input pill, card-grid download options.
 * Share target: the app appears in TikTok's Share sheet (ACTION_SEND);
 * tapping it opens the app, asks "Do you want to download this video?",
 * and downloads the No-Watermark HD version on confirmation.
 * In-app updater: checks version.json on GitHub (max once a day, plus a
 * manual button); on a newer version it downloads the APK itself and opens
 * the system installer — users never re-download manually.
 * History persists until the user deletes it manually — nothing auto-removed.
 * No permission is needed: on Android 10+ the system DownloadManager saves to
 * the public Downloads folder on the app's behalf, and MediaScanner makes the
 * file visible in Gallery. So downloads run directly — no permission popups,
 * no Settings detours.
 */

/** Download platform. v4.0: TikTok via tikwm, Instagram via Cobalt. */
enum class Platform { TIKTOK, INSTAGRAM }

class MainActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private var currentLink: String = ""
    private lateinit var etLink: EditText
    private lateinit var btnPaste: Button
    private lateinit var btnFetch: Button
    private lateinit var skeletonCard: View
    private lateinit var skeletonInner: View
    private lateinit var tabTikTok: Button
    private lateinit var tabInstagram: Button
    private lateinit var tvSubtitle: TextView
    private lateinit var statsRow: View
    private var currentPlatform = Platform.TIKTOK
    private var currentMedia: CobaltMedia? = null
    private lateinit var tvStatus: TextView
    private lateinit var emptyPreview: View
    private lateinit var resultCard: View
    private lateinit var ivCover: ImageView
    private lateinit var ivAvatar: ImageView
    private lateinit var tvDuration: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var tvStatViews: TextView
    private lateinit var tvStatLikes: TextView
    private lateinit var tvStatComments: TextView
    private lateinit var tvStatShares: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var tvEmptyHistory: TextView
    private lateinit var btnClearHistory: Button
    private lateinit var statusCard: View
    private lateinit var tvDownloadFile: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var tvDownloadStatus: TextView
    private lateinit var btnOpenDownloads: Button
    private lateinit var btnRetryDownload: Button

    private var currentVideo: VideoInfo? = null
    private var pendingDownload: PendingDownload? = null
    private var pendingDownloadId: Long = -1L
    private var updateDownloadId: Long = -1L
    private lateinit var historyAdapter: HistoryAdapter
    private val progressHandler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private data class PendingDownload(
        val url: String,
        val fileName: String,
        val label: String,
        val type: String,
        val itemId: String,
        val itemTitle: String,
        val itemCover: String,
        val platform: String
    )

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (id == pendingDownloadId) onDownloadComplete(id)
            else if (id == updateDownloadId) onUpdateDownloaded()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollView = findViewById(R.id.scrollView)
        etLink = findViewById(R.id.etLink)
        btnPaste = findViewById(R.id.btnPaste)
        btnFetch = findViewById(R.id.btnFetch)
        skeletonCard = findViewById(R.id.skeletonCard)
        skeletonInner = findViewById(R.id.skeletonInner)
        tvStatus = findViewById(R.id.tvStatus)
        emptyPreview = findViewById(R.id.emptyPreview)
        resultCard = findViewById(R.id.resultCard)
        ivCover = findViewById(R.id.ivCover)
        ivAvatar = findViewById(R.id.ivAvatar)
        tvDuration = findViewById(R.id.tvDuration)
        tvTitle = findViewById(R.id.tvTitle)
        tvAuthor = findViewById(R.id.tvAuthor)
        tvStatViews = findViewById(R.id.tvStatViews)
        tvStatLikes = findViewById(R.id.tvStatLikes)
        tvStatComments = findViewById(R.id.tvStatComments)
        tvStatShares = findViewById(R.id.tvStatShares)
        rvHistory = findViewById(R.id.rvHistory)
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        statusCard = findViewById(R.id.statusCard)
        tvDownloadFile = findViewById(R.id.tvDownloadFile)
        downloadProgress = findViewById(R.id.downloadProgress)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)
        btnOpenDownloads = findViewById(R.id.btnOpenDownloads)
        btnRetryDownload = findViewById(R.id.btnRetryDownload)

        historyAdapter = HistoryAdapter(
            HistoryStore.load(this),
            onRedownload = {
                Toast.makeText(
                    this,
                    "Paste the link again, then download",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onDelete = { item ->
                HistoryStore.remove(this, item.id)
                refreshHistory()
            }
        )
        rvHistory.layoutManager = LinearLayoutManager(this)
        rvHistory.adapter = historyAdapter
        refreshHistory()

        btnPaste.setOnClickListener { pasteFromClipboard() }
        btnFetch.setOnClickListener { fetchVideo() }
        btnClearHistory.setOnClickListener {
            HistoryStore.clear(this)
            refreshHistory()
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show()
        }
        btnOpenDownloads.setOnClickListener { openDownloads() }
        btnRetryDownload.setOnClickListener { retryDownload() }
        findViewById<Button>(R.id.btnCopyCaption).setOnClickListener { copyCaption() }
        findViewById<Button>(R.id.btnOpenTikTok).setOnClickListener { openInSourceApp() }
        findViewById<Button>(R.id.btnShareLink).setOnClickListener { shareLink() }

        // v4.0 platform tabs. Download-option buttons are bound per result
        // (bindTikTokButtons / showInstagramResult) so each platform shows
        // only the formats it actually supports.
        tabTikTok = findViewById(R.id.tabTikTok)
        tabInstagram = findViewById(R.id.tabInstagram)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        statsRow = findViewById(R.id.statsRow)
        tabTikTok.setOnClickListener { selectTab(Platform.TIKTOK) }
        tabInstagram.setOnClickListener { selectTab(Platform.INSTAGRAM) }

        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(downloadReceiver, filter)
        }

        autofillFromClipboard()
        handleShareIntent(intent)

        // v3 Aurora: gradient headline — "anything." in violet → cyan.
        val tvHeroAccent: TextView = findViewById(R.id.tvHeroAccent)
        tvHeroAccent.post {
            val w = tvHeroAccent.paint.measureText(tvHeroAccent.text.toString())
            if (w > 0) {
                tvHeroAccent.paint.shader = LinearGradient(
                    0f, 0f, w, tvHeroAccent.textSize,
                    intArrayOf(0xFF8B5CF6.toInt(), 0xFF22D3EE.toInt()),
                    null, Shader.TileMode.CLAMP
                )
                tvHeroAccent.invalidate()
            }
        }

        // v3.1: in-app updater — auto-check once a day, manual button anytime.
        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
            checkForUpdates(manual = true)
        }
        if (UpdateChecker.shouldAutoCheck(this)) {
            UpdateChecker.markChecked(this)
            checkForUpdates(manual = false)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShareIntent(intent)
    }

    // ---------- Share-to-app (TikTok Share sheet -> PRIME VID VAULT) ----------

    private var autoDownloadOnFetch = false

    private fun handleShareIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        val link = extractSharedUrl(sharedText)
        if (link == null) {
            Toast.makeText(
                this,
                "No link found in the shared content",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        // v4.0: detect the platform and open the right tab automatically.
        selectTab(detectPlatform(link))
        etLink.setText(link)
        showShareDownloadDialog(link)
    }

    private fun detectPlatform(url: String): Platform {
        return if (url.contains("instagram.com", ignoreCase = true)) Platform.INSTAGRAM
        else Platform.TIKTOK
    }

    private fun extractSharedUrl(text: String): String? {
        val urls = Regex("https?://[^\\s]+")
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ')', '!', '?') }
            .toList()
        return urls.firstOrNull { it.contains("tiktok.com", ignoreCase = true) }
            ?: urls.firstOrNull { it.contains("instagram.com", ignoreCase = true) }
            ?: urls.firstOrNull()
    }

    private fun showShareDownloadDialog(link: String) {
        val view = layoutInflater.inflate(R.layout.dialog_share, null)
        view.findViewById<TextView>(R.id.tvShareTitle).text =
            if (currentPlatform == Platform.INSTAGRAM) "Download from Instagram?"
            else "Download this video?"
        view.findViewById<TextView>(R.id.tvShareLink).text = link
        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        view.findViewById<Button>(R.id.btnShareDownload).setOnClickListener {
            dialog.dismiss()
            autoDownloadOnFetch = true
            fetchVideo()
        }
        view.findViewById<Button>(R.id.btnShareCancel).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressPolling()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

    // ---------- Clipboard / fetch ----------

    private fun autofillFromClipboard() {
        val text = clipboardText() ?: return
        if (isTikTokLink(text)) etLink.setText(text)
    }

    private fun pasteFromClipboard() {
        val text = clipboardText()
        if (text.isNullOrEmpty()) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        etLink.setText(text)
        if (isTikTokLink(text)) fetchVideo()
    }

    private fun clipboardText(): String? {
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return clip.getItemAt(0).text?.toString()
    }

    private fun isTikTokLink(text: String): Boolean {
        return text.trim().lowercase().contains("tiktok.com")
    }

    private fun isInstagramLink(text: String): Boolean {
        return text.trim().lowercase().contains("instagram.com")
    }

    /** v4.0: switch download platform. Updates tab highlight, hint texts,
     *  and clears any previous result. */
    private fun selectTab(platform: Platform) {
        currentPlatform = platform
        val tik = platform == Platform.TIKTOK
        tabTikTok.setBackgroundResource(
            if (tik) R.drawable.btn_primary else android.R.color.transparent
        )
        tabInstagram.setBackgroundResource(
            if (tik) android.R.color.transparent else R.drawable.btn_primary
        )
        tabTikTok.setTextColor(if (tik) Color.WHITE else getColor(R.color.muted))
        tabInstagram.setTextColor(if (tik) getColor(R.color.muted) else Color.WHITE)
        tvSubtitle.text = if (tik)
            "Paste a TikTok link — or share straight from TikTok."
        else
            "Paste an Instagram link — or share straight from Instagram."
        etLink.hint = if (tik) "Paste TikTok link…" else "Paste Instagram link…"
        etLink.text.clear()
        resultCard.visibility = View.GONE
        skeletonInner.clearAnimation()
        skeletonCard.visibility = View.GONE
        hideStatus()
    }

    private fun fetchVideo() {
        val link = etLink.text.toString().trim()
        val platform = currentPlatform
        if (link.isEmpty()) {
            showStatus("Paste a link first.")
            return
        }
        if (platform == Platform.TIKTOK && !isTikTokLink(link)) {
            showStatus("That doesn't look like a TikTok link. Switch to the Instagram tab for Instagram links.")
            return
        }
        if (platform == Platform.INSTAGRAM && !isInstagramLink(link)) {
            showStatus("That doesn't look like an Instagram link. Switch to the TikTok tab for TikTok links.")
            return
        }
        hideStatus()
        skeletonCard.visibility = View.VISIBLE
        skeletonInner.startAnimation(AnimationUtils.loadAnimation(this, R.anim.pulse))
        btnFetch.isEnabled = false
        resultCard.visibility = View.GONE
        emptyPreview.visibility = View.GONE
        currentLink = link
        Thread {
            if (platform == Platform.TIKTOK) {
                val result = TikWmClient.fetch(link)
                runOnUiThread {
                    skeletonInner.clearAnimation()
                    skeletonCard.visibility = View.GONE
                    btnFetch.isEnabled = true
                    result.onSuccess { info ->
                        currentVideo = info
                        currentMedia = null
                        showResult(info)
                        if (autoDownloadOnFetch) {
                            autoDownloadOnFetch = false
                            // User confirmed via the share dialog: download the best
                            // (No-Watermark HD) version right away. The preview card
                            // is still shown, so other formats remain one tap away.
                            startDownloadFlow(info.hdPlayUrl, "mp4", "No-Watermark HD", "Video")
                        }
                    }.onFailure { e ->
                        autoDownloadOnFetch = false
                        emptyPreview.visibility = View.VISIBLE
                        showStatus(e.message ?: "Something went wrong. Try again.")
                    }
                }
            } else {
                val result = CobaltClient.fetch(link)
                runOnUiThread {
                    skeletonInner.clearAnimation()
                    skeletonCard.visibility = View.GONE
                    btnFetch.isEnabled = true
                    result.onSuccess { media ->
                        currentMedia = media
                        currentVideo = null
                        showInstagramResult(media)
                        if (autoDownloadOnFetch) {
                            autoDownloadOnFetch = false
                            downloadBestInstagram(media)
                        }
                    }.onFailure { e ->
                        autoDownloadOnFetch = false
                        emptyPreview.visibility = View.VISIBLE
                        showStatus(e.message ?: "Something went wrong. Try again.")
                    }
                }
            }
        }.start()
    }

    private fun showResult(info: VideoInfo) {
        emptyPreview.visibility = View.GONE
        resultCard.visibility = View.VISIBLE
        // Restore the TikTok layout (the Instagram branch hides/relables parts).
        statsRow.visibility = View.VISIBLE
        findViewById<View>(R.id.hdBadge).visibility = View.VISIBLE
        findViewById<Button>(R.id.btnOpenTikTok).text = "Open in TikTok"
        bindTikTokButtons(info)
        // Smooth entrance: fade + slide up, then scroll the card into view.
        resultCard.alpha = 0f
        resultCard.translationY = 32f
        resultCard.animate().alpha(1f).translationY(0f).setDuration(350).start()
        resultCard.post { scrollView.smoothScrollTo(0, resultCard.top) }
        tvTitle.text = if (info.title.isEmpty()) "TikTok Video" else info.title
        tvAuthor.text = if (info.authorName.isEmpty()) {
            "@tiktok"
        } else if (info.authorUsername.isNotEmpty()) {
            info.authorName + "  @" + info.authorUsername
        } else {
            info.authorName
        }
        if (info.duration > 0) {
            tvDuration.text = info.duration.toString() + "s"
            tvDuration.visibility = View.VISIBLE
        } else {
            tvDuration.visibility = View.GONE
        }
        if (info.authorAvatar.isNotEmpty()) {
            ivAvatar.visibility = View.VISIBLE
            ivAvatar.alpha = 0f
            Thread {
                try {
                    val bmp = BitmapFactory.decodeStream(URL(info.authorAvatar).openStream())
                    runOnUiThread {
                        if (bmp != null) {
                            ivAvatar.setImageBitmap(bmp)
                            ivAvatar.animate().alpha(1f).setDuration(300).start()
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread { ivAvatar.visibility = View.GONE }
                }
            }.start()
        } else {
            ivAvatar.visibility = View.GONE
        }
        tvStatViews.text = formatCount(info.playCount) + "\nViews"
        tvStatLikes.text = formatCount(info.likeCount) + "\nLikes"
        tvStatComments.text = formatCount(info.commentCount) + "\nComments"
        tvStatShares.text = formatCount(info.shareCount) + "\nShares"
        ivCover.setImageResource(android.R.drawable.ic_menu_gallery)
        val playOverlay: View = findViewById(R.id.playOverlay)
        playOverlay.visibility = View.VISIBLE
        if (info.cover.isNotEmpty()) {
            Thread {
                try {
                    val bmp = BitmapFactory.decodeStream(URL(info.cover).openStream())
                    runOnUiThread {
                        if (bmp != null) {
                            ivCover.alpha = 0f
                            ivCover.setImageBitmap(bmp)
                            ivCover.animate().alpha(1f).setDuration(300).start()
                            playOverlay.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    // Keep placeholder
                }
            }.start()
        }
    }

    /** Binds the five TikTok download buttons to this result. */
    private fun bindTikTokButtons(info: VideoInfo) {
        val bHd: Button = findViewById(R.id.btnNoWmHd)
        val bNoWm: Button = findViewById(R.id.btnNoWm)
        val bWm: Button = findViewById(R.id.btnWm)
        val bMp3: Button = findViewById(R.id.btnMp3)
        val bCover: Button = findViewById(R.id.btnCover)
        bHd.visibility = View.VISIBLE
        bHd.text = "⬇ HD Video"
        bHd.setOnClickListener {
            startDownloadFlow(info.hdPlayUrl, "mp4", "No-Watermark HD", "Video")
        }
        bNoWm.visibility = View.VISIBLE
        bNoWm.text = "🎬 No-Watermark"
        bNoWm.setOnClickListener {
            startDownloadFlow(info.playUrl, "mp4", "No-Watermark", "Video")
        }
        bWm.visibility = View.VISIBLE
        bWm.text = "© Watermark"
        bWm.setOnClickListener {
            startDownloadFlow(info.wmPlayUrl, "mp4", "With Watermark", "Video")
        }
        bMp3.visibility = View.VISIBLE
        bMp3.text = "🎵 MP3 Audio"
        bMp3.setOnClickListener {
            startDownloadFlow(info.musicUrl, "mp3", "MP3 Audio", "Audio")
        }
        bCover.visibility = View.VISIBLE
        bCover.text = "🖼 Cover Image"
        bCover.setOnClickListener {
            startDownloadFlow(info.cover, "jpg", "Cover Image", "Cover")
        }
    }

    /** Shows an Instagram result in the shared result card. Only the formats
     *  Cobalt actually returned are shown — no fake buttons. */
    private fun showInstagramResult(media: CobaltMedia) {
        emptyPreview.visibility = View.GONE
        resultCard.visibility = View.VISIBLE
        resultCard.alpha = 0f
        resultCard.translationY = 32f
        resultCard.animate().alpha(1f).translationY(0f).setDuration(350).start()
        resultCard.post { scrollView.smoothScrollTo(0, resultCard.top) }

        tvTitle.text = if (media.title.isEmpty()) "Instagram Post" else media.title
        tvAuthor.text = if (media.author.isEmpty()) "@instagram" else media.author
        tvDuration.visibility = View.GONE
        ivAvatar.visibility = View.GONE
        statsRow.visibility = View.GONE
        findViewById<Button>(R.id.btnOpenTikTok).text = "Open in Instagram"
        findViewById<View>(R.id.hdBadge).visibility = View.GONE

        val thumb = media.thumbnail.ifEmpty { media.photoUrls.firstOrNull().orEmpty() }
        ivCover.setImageResource(android.R.drawable.ic_menu_gallery)
        val playOverlay: View = findViewById(R.id.playOverlay)
        playOverlay.visibility = if (media.videoUrl != null) View.VISIBLE else View.GONE
        if (thumb.isNotEmpty()) {
            Thread {
                try {
                    val bmp = BitmapFactory.decodeStream(URL(thumb).openStream())
                    runOnUiThread {
                        if (bmp != null) {
                            ivCover.alpha = 0f
                            ivCover.setImageBitmap(bmp)
                            ivCover.animate().alpha(1f).setDuration(300).start()
                            if (media.videoUrl != null) playOverlay.visibility = View.GONE
                        }
                    }
                } catch (e: Exception) {
                    // Keep placeholder
                }
            }.start()
        }

        val bHd: Button = findViewById(R.id.btnNoWmHd)
        val bNoWm: Button = findViewById(R.id.btnNoWm)
        val bWm: Button = findViewById(R.id.btnWm)
        val bMp3: Button = findViewById(R.id.btnMp3)
        val bCover: Button = findViewById(R.id.btnCover)
        // Instagram has no watermark variants.
        bNoWm.visibility = View.GONE
        bWm.visibility = View.GONE
        if (media.videoUrl != null) {
            bHd.visibility = View.VISIBLE
            bHd.text = "⬇ Reel Video"
            bHd.setOnClickListener {
                startInstagramDownload(media.videoUrl, "mp4", "Reel Video", "Video", media)
            }
        } else {
            bHd.visibility = View.GONE
        }
        if (media.audioUrl != null) {
            bMp3.visibility = View.VISIBLE
            bMp3.text = "🎵 MP3 Audio"
            bMp3.setOnClickListener {
                startInstagramDownload(media.audioUrl, "mp3", "MP3 Audio", "Audio", media)
            }
        } else {
            bMp3.visibility = View.GONE
        }
        if (media.photoUrls.isNotEmpty()) {
            bCover.visibility = View.VISIBLE
            bCover.text = if (media.photoUrls.size > 1)
                "🖼 Photos (${media.photoUrls.size})" else "🖼 Photo"
            bCover.setOnClickListener {
                media.photoUrls.forEachIndexed { i, url ->
                    startInstagramDownload(url, "jpg", "Photo ${i + 1}", "Photo", media)
                }
            }
        } else {
            bCover.visibility = View.GONE
        }
    }

    /** Share-to-app auto-download: best available Instagram format, video first. */
    private fun downloadBestInstagram(media: CobaltMedia) {
        when {
            media.videoUrl != null ->
                startInstagramDownload(media.videoUrl, "mp4", "Reel Video", "Video", media)
            media.photoUrls.isNotEmpty() ->
                startInstagramDownload(media.photoUrls[0], "jpg", "Photo", "Photo", media)
            media.audioUrl != null ->
                startInstagramDownload(media.audioUrl, "mp3", "MP3 Audio", "Audio", media)
            else ->
                Toast.makeText(this, "Nothing downloadable found", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Quick actions ----------

    private fun copyCaption() {
        val caption = when {
            currentPlatform == Platform.INSTAGRAM && currentMedia != null ->
                currentMedia!!.title.ifEmpty { "Instagram Post" }
            currentVideo != null -> currentVideo!!.title.ifEmpty { "TikTok Video" }
            else -> null
        }
        if (caption == null) {
            Toast.makeText(this, "Fetch something first", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(
            ClipData.newPlainText("caption", caption)
        )
        Toast.makeText(this, "Caption copied", Toast.LENGTH_SHORT).show()
    }

    private fun openInSourceApp() {
        if (currentLink.isEmpty()) {
            Toast.makeText(this, "Fetch something first", Toast.LENGTH_SHORT).show()
            return
        }
        val appName = if (currentPlatform == Platform.INSTAGRAM) "Instagram" else "TikTok"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentLink)))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open $appName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareLink() {
        if (currentLink.isEmpty()) {
            Toast.makeText(this, "Fetch a video first", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, currentLink + " (via PRIME VID VAULT)")
        }
        startActivity(Intent.createChooser(intent, "Share link"))
    }

    private fun formatCount(n: Long): String {
        return when {
            n >= 1_000_000 -> trimOneDecimal(n / 1_000_000.0) + "M"
            n >= 1_000 -> trimOneDecimal(n / 1_000.0) + "K"
            else -> n.toString()
        }
    }

    private fun trimOneDecimal(v: Double): String {
        val s = String.format("%.1f", v)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    // ---------- Download flow (no permission needed) ----------

    private fun startDownloadFlow(fileUrl: String?, ext: String, label: String, type: String) {
        val video = currentVideo
        if (fileUrl.isNullOrEmpty() || video == null) {
            Toast.makeText(this, "Fetch a video first", Toast.LENGTH_SHORT).show()
            return
        }
        startDownloadFlowInternal(
            fileUrl, ext, label, type,
            id = video.id,
            title = video.title.ifEmpty { "TikTok Video" },
            cover = video.cover,
            platform = "tiktok"
        )
    }

    /** Instagram download entry point — works without a TikTok VideoInfo. */
    private fun startInstagramDownload(
        fileUrl: String?, ext: String, label: String, type: String, media: CobaltMedia
    ) {
        if (fileUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Not available for this post", Toast.LENGTH_SHORT).show()
            return
        }
        startDownloadFlowInternal(
            fileUrl, ext, label, type,
            id = media.id,
            title = media.title.ifEmpty { "Instagram Post" },
            cover = media.thumbnail.ifEmpty { media.photoUrls.firstOrNull().orEmpty() },
            platform = "instagram"
        )
    }

    private fun startDownloadFlowInternal(
        fileUrl: String, ext: String, label: String, type: String,
        id: String, title: String, cover: String, platform: String
    ) {
        val safeId = id.ifEmpty { System.currentTimeMillis().toString() }
        val fileName = "PVV_" + safeId + "_" + System.currentTimeMillis() + "." + ext
        pendingDownload = PendingDownload(
            fileUrl, fileName, label, type,
            itemId = safeId, itemTitle = title, itemCover = cover, platform = platform
        )
        startDownload()
    }

    private fun startDownload() {
        val pending = pendingDownload ?: return
        try {
            val request = DownloadManager.Request(Uri.parse(pending.url))
                .setTitle("PRIME VID VAULT - " + pending.label)
                .setDescription("Downloading...")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "PrimeVidVault/" + pending.fileName
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            pendingDownloadId = dm.enqueue(request)
            showDownloadStarted(pending)
            HistoryStore.add(
                this,
                HistoryItem(
                    id = pending.itemId + "_" + System.currentTimeMillis(),
                    title = pending.itemTitle,
                    cover = pending.itemCover,
                    type = pending.type,
                    date = System.currentTimeMillis(),
                    platform = pending.platform
                )
            )
            refreshHistory()
        } catch (e: Exception) {
            statusCard.visibility = View.VISIBLE
            tvDownloadFile.text = pending.fileName
            tvDownloadStatus.text = "Download didn't start: " + e.message
            btnRetryDownload.visibility = View.VISIBLE
            btnOpenDownloads.visibility = View.GONE
            Toast.makeText(
                this,
                "Download didn't start: " + e.message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showDownloadStarted(pending: PendingDownload) {
        statusCard.visibility = View.VISIBLE
        // Smooth entrance for the status card.
        statusCard.alpha = 0f
        statusCard.translationY = 24f
        statusCard.animate().alpha(1f).translationY(0f).setDuration(300).start()
        tvDownloadFile.text = pending.fileName
        downloadProgress.isIndeterminate = true
        downloadProgress.progress = 0
        tvDownloadStatus.text = "Downloading..."
        btnOpenDownloads.visibility = View.GONE
        btnRetryDownload.visibility = View.GONE
        startProgressPolling()
    }

    private fun startProgressPolling() {
        stopProgressPolling()
        progressRunnable = object : Runnable {
            override fun run() {
                updateDownloadProgress()
                progressHandler.postDelayed(this, 800)
            }
        }
        progressHandler.post(progressRunnable!!)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { progressHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun updateDownloadProgress() {
        if (pendingDownloadId < 0) return
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(pendingDownloadId))
        cursor.use { c ->
            if (c.moveToFirst()) {
                val totalIdx = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val doneIdx = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val total = if (totalIdx >= 0) c.getLong(totalIdx) else -1L
                val done = if (doneIdx >= 0) c.getLong(doneIdx) else 0L
                if (total > 0) {
                    downloadProgress.isIndeterminate = false
                    val pct = ((done * 100) / total).toInt()
                    // Animated progress (smooth, not jumpy).
                    downloadProgress.setProgress(pct, true)
                    tvDownloadStatus.text = "Downloading... " + pct + "%"
                }
            }
        }
    }

    private fun onDownloadComplete(id: Long) {
        stopProgressPolling()
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(id))
        cursor.use { c ->
            if (c.moveToFirst()) {
                val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusIdx >= 0) c.getInt(statusIdx) else -1
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    downloadProgress.isIndeterminate = false
                    downloadProgress.setProgress(100, true)
                    tvDownloadStatus.text = "Saved — check your Downloads folder"
                    btnOpenDownloads.visibility = View.VISIBLE
                    btnRetryDownload.visibility = View.GONE
                    val uriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val localUri = if (uriIdx >= 0) c.getString(uriIdx) else null
                    if (localUri != null) {
                        val path = Uri.parse(localUri).path
                        if (path != null) {
                            MediaScannerConnection.scanFile(this, arrayOf(path), null, null)
                        }
                    }
                    Toast.makeText(
                        this,
                        "Saved to your Downloads folder",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = if (reasonIdx >= 0) c.getInt(reasonIdx) else -1
                    tvDownloadStatus.text =
                        "Download failed (reason: " + reason + "). Try again."
                    btnRetryDownload.visibility = View.VISIBLE
                    btnOpenDownloads.visibility = View.GONE
                    Toast.makeText(this, "Download failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openDownloads() {
        try {
            startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Open the Downloads folder: Files app → Downloads",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun retryDownload() {
        startDownload()
    }

    // ---------- In-app updater (v4.0): WiFi-aware + legendary dialogs ----------

    private var pendingUpdateInfo: UpdateChecker.UpdateInfo? = null
    private var pendingUpdateApk: File? = null

    /** True only on WiFi — silent auto-downloads never touch mobile data. */
    private fun isWifiConnected(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
    }

    private fun checkForUpdates(manual: Boolean) {
        UpdateChecker.check(this, BuildConfig.VERSION_CODE) { info ->
            if (info != null) {
                pendingUpdateInfo = info
                if (isWifiConnected()) {
                    // WiFi: download silently in the background; the legendary
                    // "Update Ready" dialog appears when it finishes.
                    downloadUpdate(info, silent = true)
                } else {
                    // Mobile data: ask first, with changelog and a Later option.
                    showUpdateDialog(info, downloaded = false)
                }
            } else if (manual) {
                Toast.makeText(this, "You're on the latest version", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Legendary update dialog. downloaded=false -> "Update Available" (asks to
     * download, shows what's new). downloaded=true -> "Update Ready" (asks to
     * install). English only, always with a separate Later option.
     */
    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo, downloaded: Boolean) {
        val view = layoutInflater.inflate(R.layout.dialog_update, null)
        view.findViewById<TextView>(R.id.tvUpdateTitle).text =
            if (downloaded) "Update Ready" else "Update Available"
        view.findViewById<TextView>(R.id.tvUpdateVersion).text = "v" + info.versionName
        view.findViewById<TextView>(R.id.tvUpdateChangelog).text =
            if (info.changelog.isNotEmpty()) info.changelog
            else "Bug fixes and performance improvements."
        val btnPrimary = view.findViewById<Button>(R.id.btnUpdatePrimary)
        btnPrimary.text = if (downloaded) "Install Now" else "Download Update"

        val dialog = AlertDialog.Builder(this)
            .setView(view)
            .setCancelable(true)
            .create()
        btnPrimary.setOnClickListener {
            dialog.dismiss()
            if (downloaded) openUpdateInstaller() else downloadUpdate(info, silent = false)
        }
        view.findViewById<Button>(R.id.btnUpdateLater).setOnClickListener {
            dialog.dismiss()
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
    }

    private fun downloadUpdate(info: UpdateChecker.UpdateInfo, silent: Boolean) {
        try {
            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("PRIME VID VAULT v" + info.versionName)
                .setDescription("Downloading update...")
                .setNotificationVisibility(
                    if (silent) DownloadManager.Request.VISIBILITY_VISIBLE
                    else DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "PrimeVidVault/update.apk"
                )
                .setAllowedOverMetered(!silent) // silent mode: WiFi only, never mobile data
                .setAllowedOverRoaming(false)
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            updateDownloadId = dm.enqueue(request)
            if (!silent) {
                Toast.makeText(this, "Downloading update...", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Update download didn't start: " + e.message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun onUpdateDownloaded() {
        val id = updateDownloadId
        updateDownloadId = -1L
        if (id < 0) return
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(id))
        cursor.use { c ->
            if (c.moveToFirst()) {
                val status = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS))
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    val uriStr = c.getString(c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                    val path = uriStr?.let { Uri.parse(it).path }
                    if (path == null) {
                        Toast.makeText(this, "Update file not found", Toast.LENGTH_LONG).show()
                        return
                    }
                    pendingUpdateApk = File(path)
                    val info = pendingUpdateInfo
                    if (info != null) showUpdateDialog(info, downloaded = true)
                    else openUpdateInstaller()
                } else {
                    Toast.makeText(this, "Update download failed", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openUpdateInstaller() {
        val apk = pendingUpdateApk
        if (apk == null || !apk.exists()) {
            Toast.makeText(this, "Update file not found", Toast.LENGTH_LONG).show()
            return
        }
        val apkUri = FileProvider.getUriForFile(
            this,
            "com.primevidvault.app.fileprovider",
            apk
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open installer", Toast.LENGTH_LONG).show()
        }
    }

    // ---------- Helpers ----------

    private fun refreshHistory() {
        val items = HistoryStore.load(this)
        historyAdapter.update(items)
        tvEmptyHistory.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun showStatus(msg: String) {
        tvStatus.text = msg
        tvStatus.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        tvStatus.visibility = View.GONE
    }
}
