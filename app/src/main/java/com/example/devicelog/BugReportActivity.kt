package com.example.devicelog

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.util.Date

class BugReportActivity : AppCompatActivity() {

    private var selectedImageUri: Uri? = null
    private lateinit var ivPreview: ImageView
    private lateinit var layoutPreview: LinearLayout

    private val categories = arrayOf(
        "UI Interface Glitch",
        "Battery Polling Inaccuracy",
        "Memory Leak/High RAM Usage",
        "Network Ping Timeout",
        "Save File Generation Error",
        "Unexpected Crash/Force Close",
        "Other Technical Issue"
    )

    private val getImage = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            selectedImageUri = result.data?.data
            ivPreview.setImageURI(selectedImageUri)
            layoutPreview.visibility = View.VISIBLE
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) openGallery()
        else Toast.makeText(this, "Permission Required", Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bug_report)

        val spinner = findViewById<Spinner>(R.id.spinnerCategory)
        val etSteps = findViewById<EditText>(R.id.etSteps)
        val etExpected = findViewById<EditText>(R.id.etExpected)
        val etAdditional = findViewById<EditText>(R.id.etAdditional)
        val btnAttach = findViewById<Button>(R.id.btnAttachPhoto)
        val btnClearPhoto = findViewById<Button>(R.id.btnRemovePhoto)
        val btnSend = findViewById<Button>(R.id.btnSendReport)

        ivPreview = findViewById(R.id.ivPreview)
        layoutPreview = findViewById(R.id.layoutPreview)

        val adapter = ArrayAdapter(this, R.layout.spinner_item_bug, categories)
        adapter.setDropDownViewResource(R.layout.spinner_item_bug)
        spinner.adapter = adapter

        btnAttach.setOnClickListener { handlePermission() }

        btnClearPhoto.setOnClickListener {
            selectedImageUri = null
            ivPreview.setImageURI(null)
            layoutPreview.visibility = View.GONE
        }

        btnSend.setOnClickListener {
            if (etSteps.text.isEmpty() || etExpected.text.isEmpty()) {
                Toast.makeText(this, "Fill required fields.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dispatchDirectlyToEmail(
                spinner.selectedItem.toString(),
                etSteps.text.toString(),
                etExpected.text.toString(),
                etAdditional.text.toString()
            )
        }
    }

    private fun handlePermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) openGallery()
        else requestPermissionLauncher.launch(permission)
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
        getImage.launch(intent)
    }

    private fun dispatchDirectlyToEmail(cat: String, steps: String, exp: String, notes: String) {
        val emailRecipient = "fosomstudios@gmail.com"
        val emailSubject = "BUG REPORT: $cat"
        val emailBody = """
            ==================================================
            INFOCORE SECURE DIAGNOSTIC REPORT
            ==================================================
            DATE: ${Date()}
            CATEGORY: $cat
            
            [02 - STEPS]
            $steps
            
            [03 - EXPECTED]
            $exp
            
            [04 - NOTES]
            ${if (notes.isEmpty()) "N/A" else notes}
            
            [TELEMETRY]
            Device: ${Build.MANUFACTURER} ${Build.MODEL}
            Android: ${Build.VERSION.RELEASE}
            ==================================================
        """.trimIndent()

        // DIRECT EMAIL INTENT
        val intent = Intent(Intent.ACTION_SEND).apply {
            // "message/rfc822" forces Android to only look for email apps
            type = "message/rfc822"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(emailRecipient))
            putExtra(Intent.EXTRA_SUBJECT, emailSubject)
            putExtra(Intent.EXTRA_TEXT, emailBody)

            selectedImageUri?.let {
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // If there's an image, we change type to image/* so the attachment works
                type = "image/*"
            }
        }

        try {
            // This takes them directly to the app
            startActivity(intent)

            // Show the redirect popup after the intent is fired
            showPostDispatchDialog()
        } catch (e: Exception) {
            Toast.makeText(this, "Error: No email app found.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPostDispatchDialog() {
        AlertDialog.Builder(this)
            .setTitle("REPORT DISPATCHED")
            .setMessage("System returning to Primary Terminal.")
            .setCancelable(false)
            .setPositiveButton("ACKNOWLEDGE") { _, _ ->
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
                startActivity(intent)
                finish()
            }
            .show()
    }
}