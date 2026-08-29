package com.fotoyu.compressor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.scale
import androidx.documentfile.provider.DocumentFile
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.*
import java.io.*
import java.util.ArrayDeque
import kotlin.math.max
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var sourceUri: Uri? = null
    private var outputUri: Uri? = null
    private var photos: List<PhotoItem> = emptyList()
    private var cancelRequested = false

    private lateinit var sourceText: TextView
    private lateinit var outputText: TextView
    private lateinit var scanButton: Button
    private lateinit var startButton: Button
    private lateinit var cancelButton: Button
    private lateinit var scanResult: TextView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private lateinit var logView: TextView
    private lateinit var maxFilesEdit: EditText
    private lateinit var maxWidthEdit: EditText

    private val sourceLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onSourceFolderSelected(it) }
    }

    private val outputLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { onOutputFolderSelected(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sourceText = findViewById(R.id.sourceText)
        outputText = findViewById(R.id.outputText)
        scanButton = findViewById(R.id.scanButton)
        startButton = findViewById(R.id.startButton)
        cancelButton = findViewById(R.id.cancelButton)
        scanResult = findViewById(R.id.scanResult)
        progress = findViewById(R.id.progress)
        status = findViewById(R.id.status)
        logView = findViewById(R.id.log)
        maxFilesEdit = findViewById(R.id.maxFiles)
        maxWidthEdit = findViewById(R.id.maxWidth)

        findViewById<Button>(R.id.sourceButton).setOnClickListener { sourceLauncher.launch(null) }
        findViewById<Button>(R.id.outputButton).setOnClickListener { outputLauncher.launch(null) }
        scanButton.setOnClickListener { scan() }
        startButton.setOnClickListener { startProcessing() }
        cancelButton.setOnClickListener {
            cancelRequested = true
            status.text = getString(R.string.status_cancelling)
        }
    }

    private fun onSourceFolderSelected(uri: Uri) {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { contentResolver.takePersistableUriPermission(uri, takeFlags) } catch (_: Exception) {}
        sourceUri = uri
        sourceText.text = uri.toString()
        photos = emptyList()
        scanResult.text = getString(R.string.scan_not_started)
        scanButton.isEnabled = true
        startButton.isEnabled = false
        appendLog(getString(R.string.log_source_selected))
    }

    private fun onOutputFolderSelected(uri: Uri) {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { contentResolver.takePersistableUriPermission(uri, takeFlags) } catch (_: Exception) {}
        outputUri = uri
        outputText.text = uri.toString()
        updateStart()
        appendLog(getString(R.string.log_output_selected))
    }

    private fun isPhoto(name: String): Boolean = setOf("jpg","jpeg","png","webp","bmp","tif","tiff","gif").contains(name.substringAfterLast('.', "").lowercase())

    private fun scan() {
        val root = sourceUri ?: return
        scanButton.isEnabled = false
        startButton.isEnabled = false
        status.text = getString(R.string.status_scanning)
        scope.launch {
            try {
                val found = recursivePhotos(root)
                withContext(Dispatchers.Main) {
                    photos = found
                    val maxFiles = maxFilesEdit.text.toString().toIntOrNull()?.coerceAtLeast(1) ?: 2000
                    val folders = if (found.isEmpty()) 0 else (found.size + maxFiles - 1) / maxFiles
                    scanResult.text = getString(R.string.scan_result_format, found.size, folders)
                    status.text = if (found.isEmpty()) getString(R.string.status_no_photos) else getString(R.string.status_scan_finished)
                    appendLog(getString(R.string.log_photos_found, found.size))
                    scanButton.isEnabled = true
                    updateStart()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status.text = getString(R.string.status_scan_failed, e.message)
                    appendLog(getString(R.string.log_error_format, e.message))
                    scanButton.isEnabled = true
                }
            }
        }
    }

    private fun recursivePhotos(root: Uri): List<PhotoItem> {
        val result = mutableListOf<PhotoItem>()
        val queue = ArrayDeque<Pair<DocumentFile,String>>()
        DocumentFile.fromTreeUri(this, root)?.let { queue.add(it to "") } ?: return result
        while (queue.isNotEmpty()) {
            val (folder, rel) = queue.removeFirst()
            folder.listFiles().sortedBy { it.name?.lowercase() ?: "" }.forEach { f ->
                val name = f.name ?: return@forEach
                val nextRel = if (rel.isEmpty()) name else "$rel/$name"
                if (f.isDirectory) queue.add(f to nextRel)
                else if (f.isFile && isPhoto(name)) result.add(PhotoItem(f.uri, name, nextRel))
            }
        }
        return result
    }

    private fun updateStart() { startButton.isEnabled = sourceUri != null && outputUri != null && photos.isNotEmpty() }

    private fun startProcessing() {
        val outRoot = outputUri ?: return
        val maxFiles = maxFilesEdit.text.toString().toIntOrNull()
        val maxWidth = maxWidthEdit.text.toString().toIntOrNull()
        if (maxFiles == null || maxFiles < 1 || maxWidth == null || maxWidth < 480) {
            status.text = getString(R.string.status_invalid_settings)
            return
        }
        cancelRequested = false
        sourceUri?.let {
            if (it == outRoot) {
                status.text = getString(R.string.status_same_folder)
                return
            }
        }
        setUiRunning(true)
        progress.progress = 0
        val items = photos
        scope.launch {
            var success = 0
            var failed = 0
            try {
                val folderCount = (items.size + maxFiles - 1) / maxFiles
                val parent = DocumentFile.fromTreeUri(this@MainActivity, outRoot) ?: throw IOException(getString(R.string.error_output_folder))
                val folders = (1..folderCount).map { n ->
                    val name = "Folder_${n.toString().padStart(3,'0')}"
                    parent.findFile(name) ?: parent.createDirectory(name) ?: throw IOException(getString(R.string.error_create_folder, name))
                }
                for ((index,item) in items.withIndex()) {
                    if (cancelRequested) break
                    val folder = folders[index / maxFiles]
                    try {
                        val base = item.name.substringBeforeLast('.', item.name)
                        val dest = folder.createFile("image/jpeg", "$base.jpg") ?: throw IOException(getString(R.string.error_create_file))
                        compressToUri(item.uri, dest.uri, maxWidth)
                        success++
                        withContext(Dispatchers.Main) {
                            progress.progress = ((index + 1) * 100 / items.size)
                            status.text = getString(R.string.status_compressing, item.relativePath)
                        }
                    } catch (e: Exception) {
                        failed++
                        withContext(Dispatchers.Main) {
                            appendLog(getString(R.string.log_failed_format, item.relativePath, e.message))
                        }
                    }
                }
                val cancelled = cancelRequested
                withContext(Dispatchers.Main) {
                    status.text = if (cancelled) getString(R.string.status_cancelled, success, failed) else getString(R.string.status_finished, success, folderCount)
                    appendLog(getString(R.string.log_summary_format, if (cancelled) "CANCEL" else "SELESAI", success, failed))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    status.text = getString(R.string.status_process_failed, e.message)
                    appendLog(getString(R.string.log_error_format, e.message))
                }
            } finally {
                withContext(Dispatchers.Main) {
                    setUiRunning(false)
                    updateStart()
                }
            }
        }
    }

    private fun compressToUri(src: Uri, dest: Uri, maxWidth: Int) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        (contentResolver.openInputStream(src) ?: throw IOException(getString(R.string.error_read_photo))).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        val sample = calculateSample(bounds.outWidth, bounds.outHeight, maxWidth)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bitmap = contentResolver.openInputStream(src)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: throw IOException(getString(R.string.error_decode_failed))
        var scaled = bitmap
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide > maxWidth) {
            val ratio = maxWidth.toFloat()/maxSide.toFloat()
            scaled = bitmap.scale((bitmap.width*ratio).roundToInt(), (bitmap.height*ratio).roundToInt(), true)
            if (scaled !== bitmap) bitmap.recycle()
        }
        // Orientation correction for the common EXIF rotations.
        val rotated = try { applyExifOrientation(src, scaled) } catch (_: Exception) { scaled }
        if (rotated !== scaled) scaled.recycle()
        var quality = 82
        var bytes: ByteArray
        do {
            val bos = ByteArrayOutputStream()
            if (!rotated.compress(Bitmap.CompressFormat.JPEG, quality, bos)) throw IOException(getString(R.string.error_encode_failed))
            bytes = bos.toByteArray()
            quality -= 7
        } while (bytes.size > 950 * 1024 && quality >= 45)
        if (bytes.size > 950 * 1024) {
            val ratio = 0.82f
            val smaller = rotated.scale(max(480,(rotated.width*ratio).roundToInt()), max(480,(rotated.height*ratio).roundToInt()), true)
            rotated.recycle()
            val bos = ByteArrayOutputStream(); smaller.compress(Bitmap.CompressFormat.JPEG, 60, bos); bytes = bos.toByteArray(); smaller.recycle()
        } else rotated.recycle()
        if (bytes.size > 1024*1024) throw IOException(getString(R.string.error_compress_limit))
        contentResolver.openOutputStream(dest)?.use { it.write(bytes) } ?: throw IOException(getString(R.string.error_write_failed))
    }

    private fun calculateSample(w: Int, h: Int, target: Int): Int { var sample=1; while (max(w/sample,h/sample) > target*2) sample*=2; return sample }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val exif = contentResolver.openInputStream(uri)?.use { ExifInterface(it) } ?: return bitmap
        return when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap,90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap,180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap,270f)
            else -> bitmap
        }
    }
    private fun rotate(src: Bitmap, degrees: Float): Bitmap { val m=android.graphics.Matrix(); m.postRotate(degrees); return Bitmap.createBitmap(src,0,0,src.width,src.height,m,true) }

    private fun setUiRunning(running: Boolean) { sourceText.post { scanButton.isEnabled=!running && sourceUri!=null; startButton.isEnabled=!running && outputUri!=null && photos.isNotEmpty(); cancelButton.isEnabled=running } }
    private fun appendLog(s: String) { logView.post { logView.append("\n[${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale("id")).format(java.util.Date())}] $s") } }

    override fun onDestroy() { cancelRequested=true; scope.cancel(); super.onDestroy() }
    data class PhotoItem(val uri: Uri, val name: String, val relativePath: String)
}
