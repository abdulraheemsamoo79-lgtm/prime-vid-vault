package com.primevidvault.app

import android.Manifest
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.net.URL

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val PREFS = "prime_vid_vault"
        private const val KEY_PERMISSION_PROMPTED = "permission_prompted"
        private const val KEY_PERMISSION_REQUESTED_BEFORE = "permission_requested_before"
    }

    private lateinit var etLink: EditText
    private lateinit var btnPaste: Button
    private lateinit var btnFetch: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var resultCard: View
    private lateinit var ivCover: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvAuthor: TextView
    private lateinit var tvStatViews: TextView
    private lateinit var tvStatLikes: TextView
    private lateinit var tvStatComments: TextView
    private lateinit var tvStatShares: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var btnClearHistory: Button
    private lateinit var tvPermissionStatus: TextView
    private lateinit var btnPermissionFix: Button
    private lateinit var statusCard: View
    private lateinit var tvDownloadFile: TextView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var tvDownloadStatus: TextView
    private lateinit var btnOpenDownloads: Button
    private lateinit var btnRetryDownload: Button

    private var currentVideo: VideoInfo? = null
    private var pendingDownload: PendingDownload? = null
    private var pendingDownloadId: Long = -1L
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etLink = findViewById(R.id.etLink)
        btnPaste = findViewById(R.id.btnPaste)
        btnFetch = findViewById(R.id.btnFetch)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        resultCard = findViewById(R.id.resultCard)
        ivCover = findViewById(R.id.ivCover)
        tvTitle = findViewById(R.id.tvTitle)
        tvAuthor = findViewById(R.id.tvAuthor)
        tvStatViews = findViewById(R.id.tvStatViews)
        tvStatLikes = findViewById(R.id.tvStatLikes)
        tvStatComments = findViewById(R.id.tvStatComments)
        tvStatShares = findViewById(R.id.tvStatShares)
        rvHistory = findViewById(R.id.rvHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)
        tvPermissionStatus = findViewById(R.id.tvPermissionStatus)
        btnPermissionFix = findViewById(R.id.btnPermissionFix)
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
                    "Link dobara paste karo, phir download karo",
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

        btnPaste.setOnClickListener { pasteFromClipboard() }
        btnFetch.setOnClickListener { fetchVideo() }
        btnClearHistory.setOnClickListener {
            HistoryStore.clear(this)
            refreshHistory()
            Toast.makeText(this, "History clear ho gayi", Toast.LENGTH_SHORT).show()
        }
        btnPermissionFix.setOnClickListener { handlePermissionButton() }
        btnOpenDownloads.setOnClickListener { openDownloads() }
        btnRetryDownload.setOnClickListener { retryDownload() }

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

        updatePermissionUi()
        maybePromptPermissionOnFirstLaunch()
        autofillFromClipboard()
    }

    override fun onResume() {
        super.onResume()
        // Refresh after returning from system Settings.
        updatePermissionUi()
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

    // ---------- Permissions ----------

    private fun prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun mediaPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.WRITE_EXTERNAL_STORAGE

    private fun isPermissionGranted(): Boolean {
        return ContextCompat.checkSelfPermission(this, mediaPermission()) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun isPermanentlyDenied(): Boolean {
        val requestedBefore = prefs().getBoolean(KEY_PERMISSION_REQUESTED_BEFORE, false)
        return requestedBefore &&
            !isPermissionGranted() &&
            !ActivityCompat.shouldShowRequestPermissionRationale(this, mediaPermission())
    }

    private fun maybePromptPermissionOnFirstLaunch() {
        val p = prefs()
        if (p.getBoolean(KEY_PERMISSION_PROMPTED, false)) return
        p.edit().putBoolean(KEY_PERMISSION_PROMPTED, true).apply()
        showPermissionExplainer()
    }

    private fun showPermissionExplainer() {
        AlertDialog.Builder(this)
            .setTitle("Permission chahiye")
            .setMessage(
                "Videos ko Gallery me save karne ke liye PRIME VID VAULT ko " +
                    "photos/videos ka access chahiye. Allow karne par downloads " +
                    "seedha Gallery me save hongi aur download notifications bhi milengi."
            )
            .setPositiveButton("Allow") { _, _ -> requestSystemPermissions() }
            .setNegativeButton("Baad me") { _, _ -> updatePermissionUi() }
            .setCancelable(false)
            .show()
    }

    private fun handlePermissionButton() {
        if (isPermissionGranted()) {
            Toast.makeText(this, "Permissions pehle se allowed hain", Toast.LENGTH_SHORT).show()
            return
        }
        if (isPermanentlyDenied()) {
            showGoToSettingsDialog()
        } else {
            showPermissionExplainer()
        }
    }

    private fun requestSystemPermissions() {
        prefs().edit().putBoolean(KEY_PERMISSION_REQUESTED_BEFORE, true).apply()
        val perms = mutableListOf(mediaPermission())
        if (Build.VERSION.SDK_INT >= 33) {
            // Needed so download-complete notifications are visible on Android 13+.
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        ActivityCompat.requestPermissions(this, perms.toTypedArray(), PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val mediaIdx = permissions.indexOf(mediaPermission())
            val mediaGranted = mediaIdx >= 0 &&
                grantResults.getOrNull(mediaIdx) == PackageManager.PERMISSION_GRANTED
            updatePermissionUi()
            if (mediaGranted) {
                Toast.makeText(this, "Permission allowed", Toast.LENGTH_SHORT).show()
                startDownload()
            } else {
                if (isPermanentlyDenied()) {
                    showGoToSettingsDialog()
                } else {
                    Toast.makeText(
                        this,
                        "Permission deny hui - download nahi hoga",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Settings se allow karo")
            .setMessage(
                "Permission permanently deny ho gayi hai, isliye popup dobara " +
                    "nahi aayega. App Settings → Permissions me jaake " +
                    "Photos/Videos ko Allow karo."
            )
            .setPositiveButton("Settings Kholo") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", packageName, null)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updatePermissionUi() {
        if (isPermissionGranted()) {
            tvPermissionStatus.text = "Permissions: Granted"
            tvPermissionStatus.setTextColor(
                ContextCompat.getColor(this, android.R.color.holo_green_light)
            )
            btnPermissionFix.visibility = View.GONE
        } else {
            tvPermissionStatus.text = "Permissions: Not granted"
            tvPermissionStatus.setTextColor(ContextCompat.getColor(this, R.color.pink))
            btnPermissionFix.visibility = View.VISIBLE
            btnPermissionFix.text =
                if (isPermanentlyDenied()) "Settings Kholo" else "Allow Karo"
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
            Toast.makeText(this, "Clipboard khali hai", Toast.LENGTH_SHORT).show()
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
            showStatus("Pehle TikTok link paste karo.")
            return
        }
        if (!isTikTokLink(link)) {
            showStatus("Ye TikTok link nahi lag raha. Sahi link paste karo.")
            return
        }
        hideStatus()
        progressBar.visibility = View.VISIBLE
        btnFetch.isEnabled = false
        resultCard.visibility = View.GONE
        Thread {
            val result = TikWmClient.fetch(link)
            runOnUiThread {
                progressBar.visibility = View.GONE
                btnFetch.isEnabled = true
                result.onSuccess { info ->
                    currentVideo = info
                    showResult(info)
                }.onFailure { e ->
                    showStatus(e.message ?: "Kuch galat ho gaya. Dobara try karo.")
                }
            }
        }.start()
    }

    private fun showResult(info: VideoInfo) {
        resultCard.visibility = View.VISIBLE
        tvTitle.text = if (info.title.isEmpty()) "TikTok Video" else info.title
        tvAuthor.text = if (info.authorName.isEmpty()) "@tiktok" else "@" + info.authorName
        tvStatViews.text = formatCount(info.playCount) + "\nViews"
        tvStatLikes.text = formatCount(info.likeCount) + "\nLikes"
        tvStatComments.text = formatCount(info.commentCount) + "\nComments"
        tvStatShares.text = formatCount(info.shareCount) + "\nShares"
        ivCover.setImageResource(android.R.drawable.ic_menu_gallery)
        if (info.cover.isNotEmpty()) {
            Thread {
                try {
                    val bmp = BitmapFactory.decodeStream(URL(info.cover).openStream())
                    runOnUiThread { if (bmp != null) ivCover.setImageBitmap(bmp) }
                } catch (e: Exception) {
                    // Keep placeholder
                }
            }.start()
        }
    }

    private fun formatCount(n: Long): String {
        return when {
            n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
            else -> n.toString()
        }
    }

    // ---------- Download flow ----------

    private fun startDownloadFlow(fileUrl: String?, ext: String, label: String, type: String) {
        val video = currentVideo
        if (fileUrl.isNullOrEmpty() || video == null) {
            Toast.makeText(this, "Pehle video fetch karo", Toast.LENGTH_SHORT).show()
            return
        }
        val safeId = video.id.ifEmpty { System.currentTimeMillis().toString() }
        val fileName = "PVV_" + safeId + "_" + System.currentTimeMillis() + "." + ext
        pendingDownload = PendingDownload(fileUrl, fileName, label, type)
        if (isPermissionGranted()) {
            startDownload()
        } else if (isPermanentlyDenied()) {
            showGoToSettingsDialog()
        } else {
            showPermissionExplainer()
        }
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
            tvDownloadStatus.text = "Download shuru nahi hui: " + e.message
            btnRetryDownload.visibility = View.VISIBLE
            btnOpenDownloads.visibility = View.GONE
            Toast.makeText(
                this,
                "Download shuru nahi hui: " + e.message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showDownloadStarted(pending: PendingDownload) {
        statusCard.visibility = View.VISIBLE
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
                    downloadProgress.progress = pct
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
                    downloadProgress.progress = 100
                    tvDownloadStatus.text = "Saved to Gallery"
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
                        "Gallery me save ho gaya",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    val reasonIdx = c.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = if (reasonIdx >= 0) c.getInt(reasonIdx) else -1
                    tvDownloadStatus.text =
                        "Download fail ho gaya (reason: " + reason + "). Dobara try karo."
                    btnRetryDownload.visibility = View.VISIBLE
                    btnOpenDownloads.visibility = View.GONE
                    Toast.makeText(this, "Download fail ho gaya", Toast.LENGTH_LONG).show()
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
                "Downloads folder kholo: Files app → Downloads",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun retryDownload() {
        if (isPermissionGranted()) {
            startDownload()
        } else if (isPermanentlyDenied()) {
            showGoToSettingsDialog()
        } else {
            showPermissionExplainer()
        }
    }

    // ---------- Helpers ----------

    private fun refreshHistory() {
        historyAdapter.update(HistoryStore.load(this))
    }

    private fun showStatus(msg: String) {
        tvStatus.text = msg
        tvStatus.visibility = View.VISIBLE
    }

    private fun hideStatus() {
        tvStatus.visibility = View.GONE
    }
}
