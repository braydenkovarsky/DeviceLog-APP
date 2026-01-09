package com.example.infocore

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.min
import kotlin.math.roundToInt

class SpeedTestActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var progressBar: ProgressBar

    private val testDurationMs = 10_000L // 10 seconds per test
    private val bufferSize = 64 * 1024 // 64 KB buffer
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_speed_test)

        tvStatus = findViewById(R.id.tvStatus)
        progressBar = findViewById(R.id.progressBar)

        scope.launch {
            runSpeedTest()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private suspend fun runSpeedTest() {
        try {
            tvStatus.text = "Running download test..."
            progressBar.progress = 0
            val downloadSpeed = withContext(Dispatchers.IO) { downloadTest() }

            tvStatus.text = "Running upload test..."
            progressBar.progress = 50
            val uploadSpeed = withContext(Dispatchers.IO) { uploadTest() }

            progressBar.progress = 100
            showResults(downloadSpeed, uploadSpeed)
        } catch (e: Exception) {
            e.printStackTrace()
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Speed test failed: ${e.message}")
                .setPositiveButton("OK") { _, _ -> finish() }
                .show()
        }
    }

    /** Download test: continuous streaming from a public speed test file */
    private fun downloadTest(): Double {
        val url = "https://speed.hetzner.de/100MB.bin"
        val start = System.currentTimeMillis()
        val end = start + testDurationMs
        var totalBytes: Long = 0
        val buffer = ByteArray(bufferSize)

        while (System.currentTimeMillis() < end) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val input: InputStream = conn.inputStream

                while (System.currentTimeMillis() < end) {
                    val bytesRead = input.read(buffer)
                    if (bytesRead == -1) break
                    totalBytes += bytesRead

                    val elapsed = System.currentTimeMillis() - start
                    val mbps = totalBytes * 8 / 1_000_000.0 / ((elapsed / 1000.0).coerceAtLeast(0.1))
                    runOnUiThread {
                        tvStatus.text = "Download: ${mbps.roundToInt()} Mbps"
                        progressBar.progress = min((elapsed * 50 / testDurationMs).toInt(), 50)
                    }
                }
                input.close()
                conn.disconnect()
            } catch (_: Exception) {
                // ignore and retry
            }
        }

        val seconds = testDurationMs.toDouble() / 1000
        return totalBytes * 8 / 1_000_000.0 / seconds
    }

    /** Upload test: continuous POST to httpbin.org */
    private fun uploadTest(): Double {
        val url = "https://httpbin.org/post"
        val start = System.currentTimeMillis()
        val end = start + testDurationMs
        var totalBytes: Long = 0
        val data = ByteArray(50 * 1024) // 50 KB

        while (System.currentTimeMillis() < end) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                conn.outputStream.use { os ->
                    os.write(data)
                    totalBytes += data.size
                }
                conn.inputStream.use {}
                conn.disconnect()

                val elapsed = System.currentTimeMillis() - start
                val mbps = totalBytes * 8 / 1_000_000.0 / ((elapsed / 1000.0).coerceAtLeast(0.1))
                runOnUiThread {
                    tvStatus.text = "Upload: ${mbps.roundToInt()} Mbps"
                    progressBar.progress = min((elapsed * 50 / testDurationMs).toInt() + 50, 100)
                }
            } catch (_: Exception) {
                // ignore network errors
            }
        }

        val seconds = testDurationMs.toDouble() / 1000
        return totalBytes * 8 / 1_000_000.0 / seconds
    }

    private fun showResults(download: Double, upload: Double) {
        AlertDialog.Builder(this)
            .setTitle("Speed Test Results")
            .setMessage("Download: ${download.roundToInt()} Mbps\nUpload: ${upload.roundToInt()} Mbps")
            .setPositiveButton("OK") { _, _ -> finish() }
            .show()
    }
}
