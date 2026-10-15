package com.primevidvault.app

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.graphics.LinearGradient
import android.graphics.Shader
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
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
 * PRIME VID VAULT v3.1 — Aurora UI + Share-to-app + In-app updater
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
class MainActivity : AppCompatActivity() {

    private lateinit var scrollView: ScrollView
    private var currentLink: String = ""
    private lateinit var etLink: EditText
    private lateinit var btnPaste: Button
    private lateinit var btnFetch: Button
    private lateinit var progressBar: ProgressBar
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
        val type: String
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
        progressBar = findViewById(R.id.progressBar)
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
        findViewById<Button>(R.id.btnOpenTikTok).setOnClickListener { openInTikTok() }
        findViewById<Button>(R.id.btnShareLink).setOnClickListener { shareLink() }

        findViewById<Button>(R.id.btnNoWmHd).setOnClickListener {
            startDownloadFlow(currentVideo?.hdPlayUrl, "mp4", "No-Watermark HD", "Video")
        }
        findViewById<Button>(R.id.btnNoWm).setOnClickListener {
            startDownloadFlow(currentVideo?.playUrl, "mp4", "No-Watermark", "Video")
        }
        findViewById<Button>(R.id.btnWm).setOnClickListener {
            startDownloadFlow(currentVideo?.wmPlayUrl, "mp4", "With Watermark", "Video")
        }
        findViewById<Button>(R.id.btnMp3).setOnClickListener {
            startDownloadFlow(currentVideo?.musicUrl, "mp3", "MP3 Audio", "Audio")
        }
        findViewById<Button>(R.id.btnCover).setOnClickListener {
            startDownloadFlow(currentVideo?.cover, "jpg", "Cover Image", "Cover")
        }

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
        val link = extractTikTokUrl(sharedText)
        if (link == null) {
            Toast.makeText(
                this,
                "No TikTok link found in the shared content",
                Toast.LENGTH_SHORT
            ).show()
            return
        }
        etLink.setText(link)
        showShareDownloadDialog(link)
    }

    private fun extractTikTokUrl(text: String): String? {
        val urls = Regex("https?://[^\\s]+")
            .findAll(text)
            .map { it.value.trimEnd('.', ',', ')', '!', '?') }
            .toList()
        return urls.firstOrNull { it.contains("tiktok.com", ignoreCase = true) }
            ?: urls.firstOrNull()
    }

    private fun showShareDownloadDialog(link: String) {
        AlertDialog.Builder(this)
            .setTitle("Download this video?")
            .setMessage("Do you want to download this video?")
            .setPositiveButton("Download") { _, _ ->
                autoDownloadOnFetch = true
                fetchVideo()
            }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun fetchVideo() {
        val link = etLink.text.toString().trim()
        if (link.isEmpty()) {
            showStatus("Paste a TikTok link first.")
            return
        }
        if (!isTikTokLink(link)) {
            showStatus("That doesn't look like a TikTok link. Paste a valid TikTok link.")
            return
        }
        hideStatus()
        progressBar.visibility = View.VISIBLE
        btnFetch.isEnabled = false
        resultCard.visibility = View.GONE
        emptyPreview.visibility = View.GONE
        currentLink = link
        Thread {
            val result = TikWmClient.fetch(link)
            runOnUiThread {
                progressBar.visibility = View.GONE
                btnFetch.isEnabled = true
                result.onSuccess { info ->
                    currentVideo = info
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
        }.start()
    }

    private fun showResult(info: VideoInfo) {
        emptyPreview.visibility = View.GONE
        resultCard.visibility = View.VISIBLE
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

    // ---------- Quick actions ----------

    private fun copyCaption() {
        val video = currentVideo
        if (video == null) {
            Toast.makeText(this, "Fetch a video first", Toast.LENGTH_SHORT).show()
            return
        }
        val cm = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(
            ClipData.newPlainText("caption", video.title.ifEmpty { "TikTok Video" })
        )
        Toast.makeText(this, "Caption copied", Toast.LENGTH_SHORT).show()
    }

    private fun openInTikTok() {
        if (currentLink.isEmpty()) {
            Toast.makeText(this, "Fetch a video first", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentLink)))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open TikTok", Toast.LENGTH_SHORT).show()
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
        val safeId = video.id.ifEmpty { System.currentTimeMillis().toString() }
        val fileName = "PVV_" + safeId + "_" + System.currentTimeMillis() + "." + ext
        pendingDownload = PendingDownload(fileUrl, fileName, label, type)
        startDownload()
    }

    private fun startDownload() {
        val pending = pendingDownload ?: return
        val video = currentVideo ?: return
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
                    id = video.id + "_" + System.currentTimeMillis(),
                    title = video.title.ifEmpty { "TikTok Video" },
                    cover = video.cover,
                    type = pending.type,
                    date = System.currentTimeMillis()
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

    // ---------- In-app updater (v3.1+) ----------

    private fun checkForUpdates(manual: Boolean) {
        UpdateChecker.check(this, BuildConfig.VERSION_CODE) { info ->
            if (info != null) {
                showUpdateDialog(info)
            } else if (manual) {
                Toast.makeText(this, "You're on the latest version", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        val msg = (if (info.changelog.isNotEmpty()) info.changelog + "\n\n" else "") +
            "Download and install v" + info.versionName + " now?"
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage(msg)
            .setPositiveButton("Update") { _, _ -> downloadUpdate(info) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadUpdate(info: UpdateChecker.UpdateInfo) {
        try {
            val request = DownloadManager.Request(Uri.parse(info.apkUrl))
                .setTitle("PRIME VID VAULT v" + info.versionName)
                .setDescription("Downloading update...")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "PrimeVidVault/update.apk"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            updateDownloadId = dm.enqueue(request)
            Toast.makeText(this, "Downloading update...", Toast.LENGTH_SHORT).show()
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
                    val apkUri = FileProvider.getUriForFile(
                        this,
                        "com.primevidvault.app.fileprovider",
                        File(path)
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
                } else {
                    Toast.makeText(this, "Update download failed", Toast.LENGTH_LONG).show()
                }
            }
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
