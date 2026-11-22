
package com.zafarry.opencore

import android.app.Activity
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var console: TextView
    private lateinit var serverChoice: Spinner
    private lateinit var startButton: Button
    private lateinit var stopButton: Button

    private var serverProcess: Process? = null
    private var importedServerDir: File? = null

    private val pickFolder = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshRuntimeStatus()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 28)
            setBackgroundColor(0xFF101116.toInt())
        }

        fun label(text: String, size: Float = 14f) =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(0xFFE7E1F5.toInt())
            }

        page.addView(label("OPEN CORE", 26f))
        page.addView(label("Windows Game Server Manager"))

        status = label("Runtime: checking...")
        page.addView(status)

        serverChoice = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf(
                    "open.mp Windows",
                    "SA-MP Windows"
                )
            )
        }

        page.addView(serverChoice)

        val importButton = Button(this).apply {
            text = "IMPORT SERVER FOLDER"
            setOnClickListener {
                openFolderPicker()
            }
        }

        page.addView(importButton)

        startButton = Button(this).apply {
            text = "START SERVER"
            setOnClickListener {
                startServer()
            }
        }

        page.addView(startButton)

        stopButton = Button(this).apply {
            text = "STOP SERVER"
            isEnabled = false
            setOnClickListener {
                stopServer()
            }
        }

        page.addView(stopButton)

        page.addView(label("CONSOLE", 17f))

        console = label(
            "Open Core ready.\nImport a server folder to begin."
        )

        console.setTextIsSelectable(true)
        console.setPadding(14, 14, 14, 14)
        console.setBackgroundColor(0xFF1B1D27.toInt())
        console.minHeight = 220

        page.addView(console)

        page.addView(
            label(
                "Wine and Box64 runtime must be integrated " +
                "before Windows server execution."
            )
        )

        scroll.addView(page)
        setContentView(scroll)
    }

    private fun openFolderPicker() {
        val intent = android.content.Intent(
            android.content.Intent.ACTION_OPEN_DOCUMENT_TREE
        )

        intent.addFlags(
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
        )

        startActivityForResult(intent, pickFolder)
    }

    @Deprecated("Use Activity Result API")
    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: android.content.Intent?
    ) {
        super.onActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (requestCode != pickFolder ||
            resultCode != Activity.RESULT_OK
        ) return

        val uri = data?.data ?: return

        try {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            val folderName =
                if (serverChoice.selectedItemPosition == 0)
                    "openmp"
                else
                    "samp"

            val target = File(
                filesDir,
                "servers/$folderName"
            )

            target.deleteRecursively()
            target.mkdirs()

            val source =
                DocumentFile.fromTreeUri(this, uri)
                    ?: throw Exception("Folder tidak bisa dibaca")

            copyDocumentTree(source, target)

            importedServerDir = target

            appendLog("Server folder imported!")
            appendLog(target.absolutePath)

            Toast.makeText(
                this,
                "Server imported",
                Toast.LENGTH_SHORT
            ).show()

        } catch (e: Exception) {
            appendLog("Import error: ${e.message}")
        }
    }

    private fun copyDocumentTree(
        source: DocumentFile,
        destination: File
    ) {
        destination.mkdirs()

        for (child in source.listFiles()) {

            val out = File(
                destination,
                child.name ?: "unnamed"
            )

            if (child.isDirectory) {

                copyDocumentTree(child, out)

            } else {

                contentResolver.openInputStream(
                    child.uri
                )?.use { input ->

                    FileOutputStream(out).use { output ->
                        input.copyTo(output)
                    }
                }
            }
        }
    }

    private fun startServer() {

        val dir = importedServerDir

        if (dir == null || !dir.exists()) {
            appendLog("Import server folder first.")
            return
        }

        val exeName =
            if (serverChoice.selectedItemPosition == 0)
                "omp-server.exe"
            else
                "samp-server.exe"

        val exe = File(dir, exeName)

        if (!exe.exists()) {
            appendLog("$exeName not found.")
            return
        }

        val runtimeDir = File(filesDir, "runtime")

        val box64 = File(runtimeDir, "box64")
        val wine = File(runtimeDir, "wine/bin/wine")

        if (!box64.exists() || !wine.exists()) {
            appendLog("Wine + Box64 not installed.")
            appendLog("Windows server cannot start yet.")
            refreshRuntimeStatus()
            return
        }

        try {
            serverProcess = ProcessBuilder(
                box64.absolutePath,
                wine.absolutePath,
                exe.absolutePath
            )
                .directory(dir)
                .redirectErrorStream(true)
                .start()

            startButton.isEnabled = false
            stopButton.isEnabled = true

            appendLog("Server process started.")

        } catch (e: Exception) {
            appendLog("Start failed: ${e.message}")
        }
    }

    private fun stopServer() {

        serverProcess?.destroy()
        serverProcess = null

        startButton.isEnabled = true
        stopButton.isEnabled = false

        appendLog("Stop requested.")
    }

    private fun refreshRuntimeStatus() {

        val runtimeDir = File(filesDir, "runtime")

        val ready =
            File(runtimeDir, "box64").exists() &&
            File(runtimeDir, "wine/bin/wine").exists()

        status.text =
            if (ready)
                "Runtime: files detected"
            else
                "Runtime: NOT INSTALLED"
    }

    private fun appendLog(message: String) {
        console.append("\n$message")
    }

    override fun onDestroy() {
        serverProcess?.destroy()
        super.onDestroy()
    }
}
