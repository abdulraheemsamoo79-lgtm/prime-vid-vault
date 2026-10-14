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
    private lateinit var tvStats: TextView
    private lateinit var rvHistory: RecyclerView
    private lateinit var btnClearHistory: Button

    private var currentVideo: VideoInfo? = null
    private var pendingDownload: PendingDownload? = null
    private var pendingDownloadId: Long = -1L
    private lateinit var historyAdapter: HistoryAdapter

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
        tvStats = findViewById(R.id.tvStats)
        rvHistory = findViewById(R.id.rvHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)

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
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
    }

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
        tvStats.text = "Views " + formatCount(info.playCount) +
            "  |  Likes " + formatCount(info.likeCount) +
            "  |  Comments " + formatCount(info.commentCount) +
            "  |  Shares " + formatCount(info.shareCount)
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

    private fun startDownloadFlow(fileUrl: String?, ext: String, label: String, type: String) {
        val video = currentVideo
        if (fileUrl.isNullOrEmpty() || video == null) {
            Toast.makeText(this, "Pehle video fetch karo", Toast.LENGTH_SHORT).show()
            return
        }
        val safeId = video.id.ifEmpty { System.currentTimeMillis().toString() }
        val fileName = "PVV_" + safeId + "_" + System.currentTimeMillis() + "." + ext
        pendingDownload = PendingDownload(fileUrl, fileName, label, type)
        if (HistoryStore.isPermissionGranted(this)) {
            startDownload()
        } else {
            showPermissionDialog()
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Photos aur videos ki permission")
            .setMessage(
                "PRIME VID VAULT ko gallery me videos save karne ke liye " +
                    "photos aur videos ka access chahiye. Allow karne par video " +
                    "seedha Gallery me save hogi."
            )
            .setPositiveButton("Allow") { _, _ -> requestSystemPermission() }
            .setNegativeButton("Deny") { _, _ ->
                Toast.makeText(
                    this,
                    "Permission deny hui - download nahi hoga",
                    Toast.LENGTH_SHORT
                ).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun requestSystemPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            HistoryStore.setPermissionGranted(this, true)
            startDownload()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
                HistoryStore.setPermissionGranted(this, true)
                startDownload()
            } else {
                Toast.makeText(
                    this,
                    "Permission deny hui - download nahi hoga",
                    Toast.LENGTH_SHORT
                ).show()
            }
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
            Toast.makeText(
                this,
                "Download shuru - " + pending.label,
                Toast.LENGTH_SHORT
            ).show()
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
            Toast.makeText(
                this,
                "Download shuru nahi hui: " + e.message,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun onDownloadComplete(id: Long) {
        val dm = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val cursor = dm.query(DownloadManager.Query().setFilterById(id))
        cursor.use { c ->
            if (c.moveToFirst()) {
                val statusIdx = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
                val status = if (statusIdx >= 0) c.getInt(statusIdx) else -1
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
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
                    Toast.makeText(this, "Download fail ho gaya", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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
