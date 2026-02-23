package com.example.entryplayer

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.entryplayer.databinding.ActivityMainBinding
import com.example.entryplayer.ent.EntExtractor
import com.example.entryplayer.ent.ProjectRewriter
import com.example.entryplayer.server.LocalProjectServer
import com.example.entryplayer.web.JsBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Main activity hosting a WebView-based Entry player.
 *
 * This activity allows the user to select an `.ent` file from storage. It extracts the file,
 * rewrites asset paths in the project JSON, starts a local HTTP server, and loads the project
 * into EntryJS running inside a WebView. Run/Stop buttons control execution of the loaded project.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var localServer: LocalProjectServer? = null
    private var currentProjectRoot: File? = null
    private var currentProjectJsonUrl: String? = null

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleEntFile(uri)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupButtons()
        startServer()
        loadPlayerPage()
    }

    /**
     * Configure UI buttons for file open, run and stop actions.
     */
    private fun setupButtons() {
        binding.btnOpen.setOnClickListener {
            // Launch system file picker; MIME is loosely specified as any type.
            openDocumentLauncher.launch(arrayOf("*/*"))
        }

        binding.btnRun.setOnClickListener {
            // Forward run command to JS bridge in the WebView.
            binding.webView.evaluateJavascript(
                "window.EntryPlayerBridge && window.EntryPlayerBridge.run && window.EntryPlayerBridge.run();",
                null
            )
        }

        binding.btnStop.setOnClickListener {
            // Forward stop command to JS bridge in the WebView.
            binding.webView.evaluateJavascript(
                "window.EntryPlayerBridge && window.EntryPlayerBridge.stop && window.EntryPlayerBridge.stop();",
                null
            )
        }
    }

    /**
     * Initialize the WebView with required settings and attach a JS interface.
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        binding.webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                updateStatus(
                    "JS: ${consoleMessage.message()} @${consoleMessage.sourceId()}:${consoleMessage.lineNumber()}"
                )
                return true
            }
        }

        binding.webView.addJavascriptInterface(
            JsBridge(onLog = { msg -> runOnUiThread { updateStatus(msg) } }),
            "AndroidBridge"
        )
    }

    /**
     * Start the embedded HTTP server for serving the player page and extracted project assets.
     */
    private fun startServer() {
        val server = LocalProjectServer(this, 18080)
        server.start()
        localServer = server
        updateStatus("로컬 서버 시작: http://127.0.0.1:18080")
    }

    /**
     * Load the HTML player page from the assets via the local server.
     */
    private fun loadPlayerPage() {
        binding.webView.loadUrl("http://127.0.0.1:18080/player/index.html")
    }

    /**
     * Handle selection of a `.ent` file. Extracts the file, rewrites the project, and notifies the WebView.
     */
    private fun handleEntFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                updateStatus("파일 처리 시작...")

                // Process extraction on a background dispatcher
                val root = withContext(Dispatchers.IO) {
                    val workRoot = File(cacheDir, "current_project").apply {
                        deleteRecursively()
                        mkdirs()
                    }

                    val extracted = EntExtractor.extractFromUri(this@MainActivity, uri, workRoot)
                    val projectJson = EntExtractor.findProjectJson(extracted)
                        ?: error("project.json을 찾지 못했습니다.")
                    ProjectRewriter.rewriteProjectJsonForLocalServer(
                        projectJson = projectJson,
                        projectRoot = extracted,
                        localBaseUrl = "http://127.0.0.1:18080/project/"
                    )
                    extracted
                }

                currentProjectRoot = root
                localServer?.setProjectRoot(root)
                currentProjectJsonUrl = "http://127.0.0.1:18080/project/project.json"

                val js = """
                    window.EntryPlayerBridge && window.EntryPlayerBridge.loadProjectByUrl &&
                    window.EntryPlayerBridge.loadProjectByUrl(${jsonString(currentProjectJsonUrl!!)});
                """.trimIndent()
                binding.webView.evaluateJavascript(js, null)

                updateStatus("프로젝트 로드 준비 완료")
            } catch (e: Exception) {
                updateStatus("오류: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Update status text on the UI.
     */
    private fun updateStatus(message: String) {
        binding.txtStatus.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        localServer?.stop()
    }

    /**
     * Escape string into a JSON literal representation for injection into JS.
     */
    private fun jsonString(s: String): String {
        return "\"" + s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n") + "\""
    }
}