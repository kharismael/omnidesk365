package com.example.omnidesk365

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.NavigationDelegate
import org.mozilla.geckoview.GeckoSession.ProgressDelegate
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoView

class MainActivity : AppCompatActivity() {

    private lateinit var geckoRuntime: GeckoRuntime
    private lateinit var geckoSession: GeckoSession
    private var lastBackPressTime: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Hardware Back Button Sensor (Double tap to exit)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackPressTime < 2000) {
                    finish() 
                } else {
                    geckoSession.goBack() 
                    lastBackPressTime = currentTime
                    Toast.makeText(this@MainActivity, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
        })

        // 2. Start Web Engine
        validateNetworkAndStart()
    }

    // --- NETWORK VALIDATION ---
    private fun validateNetworkAndStart() {
        if (isInternetAvailable()) {
            initializeGeckoView()
        } else {
            showOfflineDialog()
        }
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && 
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun showOfflineDialog() {
        AlertDialog.Builder(this)
            .setTitle("No Internet Connection")
            .setMessage("OmniDesk 365 requires an active internet connection. As an offline alternative, you can open the local M365 Copilot app.")
            .setCancelable(false)
            .setPositiveButton("Retry") { _, _ -> validateNetworkAndStart() }
            .setNeutralButton("Open Copilot 365") { _, _ -> launchLocalApp("com.microsoft.office.officehubrow") }
            .setNegativeButton("Exit") { _, _ -> finish() }
            .show()
    }

    private fun launchLocalApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            startActivity(launchIntent)
            finish() 
        } else {
            AlertDialog.Builder(this)
                .setTitle("App Not Found")
                .setMessage("The system could not detect the target application on this device.")
                .setPositiveButton("Back") { _, _ -> showOfflineDialog() }
                .show()
        }
    }

    // --- PROTOCOL INTERCEPTOR (SMART ROUTER) ---
    private fun interceptCustomProtocol(urlTarget: String): Boolean {
        
        // 1. Detect Legacy Office Applications (Word, Excel, PowerPoint)
        if (urlTarget.startsWith("ms-word:") || 
            urlTarget.startsWith("ms-excel:") || 
            urlTarget.startsWith("ms-powerpoint:")) {
            runOnUiThread {
                try {
                    val mobileIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlTarget))
                    // Explicitly route to Microsoft 365 (Office) app
                    mobileIntent.setPackage("com.microsoft.office.officehubrow")
                    startActivity(mobileIntent)
                } catch (e: Exception) {
                    // Fail silently if the Office app is not installed
                }
            }
            return true // Instruct GeckoView to halt navigation for this URL
        }
        
        // 2. DETECT MICROSOFT TEAMS LINKS
        if (urlTarget.contains("teams.microsoft.com") || urlTarget.startsWith("msteams:")) {
            runOnUiThread {
                try {
                    // Attempt to open the native Microsoft Teams mobile app
                    val teamsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlTarget))
                    teamsIntent.setPackage("com.microsoft.teams") 
                    startActivity(teamsIntent)
                    Toast.makeText(this@MainActivity, "Redirecting to Microsoft Teams app...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    // If Teams is not installed, redirect the user to the Google Play Store
                    Toast.makeText(this@MainActivity, "Microsoft Teams not found. Redirecting to Play Store...", Toast.LENGTH_LONG).show()
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.microsoft.teams")))
                    } catch (playStoreError: Exception) {
                        // Failsafe if the Play Store app itself is missing
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.microsoft.teams")))
                    }
                }
            }
            return true // Instruct GeckoView to halt navigation for this URL (Prevents black screen)
        }
        return false // Allow GeckoView to load all other standard web URLs normally
    }

    // --- CORE ENGINE ---
    // --- CORE ENGINE ---
    private fun initializeGeckoView() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        val frameLayout = FrameLayout(this)
        
        // --- NEW: PULL TO REFRESH WRAPPER ---
        val swipeRefreshLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this)
        val geckoView = GeckoView(this)
        
        swipeRefreshLayout.addView(geckoView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        frameLayout.addView(swipeRefreshLayout, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val progressBar = ProgressBar(this)
        val progressParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER }
        frameLayout.addView(progressBar, progressParams)

        val loadingText = TextView(this).apply {
            val secret = "T21uaURlc2sgMzY1CkNyZWF0ZWQgYnkgTHVqaWU="
            text = String(Base64.decode(secret, Base64.DEFAULT))
            gravity = Gravity.CENTER 
            textSize = 14f
            setTextColor(Color.parseColor("#777777")) 
            setTypeface(null, Typeface.BOLD)
        }
        val textParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 80 
        }
        frameLayout.addView(loadingText, textParams)
        
        setContentView(frameLayout)

        geckoRuntime = GeckoRuntime.create(this)
        
        val sessionSettings = GeckoSessionSettings.Builder()
            .userAgentOverride("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36 Edg/122.0.0.0")
            .build()

        geckoSession = GeckoSession(sessionSettings)
        geckoSession.open(geckoRuntime)

        // --- SCROLL DELEGATE (PREVENT ACCIDENTAL REFRESH) ---
        // --- ZONE RADAR ---
        // Tracks whether the user is inside an editor or on the home dashboard
        var isInEditorZone = false 

        // --- SCROLL DELEGATE (ZONE AWARE) ---
        geckoSession.scrollDelegate = object : GeckoSession.ScrollDelegate {
            override fun onScrollChanged(session: GeckoSession, scrollX: Int, scrollY: Int) {
                runOnUiThread {
                    // Disable pull-to-refresh completely if inside Word/Excel/PPT editor
                    // Otherwise, only enable it when scrolled to the absolute top (scrollY == 0)
                    swipeRefreshLayout.isEnabled = if (isInEditorZone) false else (scrollY == 0)
                }
            }
        }

        // --- PROGRESS DELEGATE (SMART ROUTER & FALLBACK) ---
        geckoSession.progressDelegate = object : ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                runOnUiThread {
                    progressBar.visibility = View.VISIBLE
                    loadingText.visibility = View.VISIBLE
                    
                    // SMART ROUTER: Identify M365 editor endpoints based on telemetry
                    val lowerUrl = url.lowercase()
                    
                    // Lock the zone if the URL contains Word (doc.aspx) or Excel/PPT (docid) patterns
                    isInEditorZone = lowerUrl.contains("doc.aspx") || 
                                     lowerUrl.contains("docid")
                                     
                    // Immediately disable pull-to-refresh to prevent accidental reloads in editor
                    if (isInEditorZone) {
                        swipeRefreshLayout.isEnabled = false
                    }
                }
            }
            
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                runOnUiThread {
                    progressBar.visibility = View.GONE
                    loadingText.visibility = View.GONE
                    swipeRefreshLayout.isRefreshing = false 
                }
            }
        }

        // --- ANTI WHITE SCREEN & NATIVE DOWNLOAD HANDLER ---
        geckoSession.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(session: GeckoSession) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Restoring session memory...", Toast.LENGTH_SHORT).show()
                    session.reload()
                }
            }
            
            override fun onKill(session: GeckoSession) {
                runOnUiThread {
                    session.reload()
                }
            }

            // GECKOVIEW DIRECT STREAM HIJACKER (Bypass Cookie Wall)
            override fun onExternalResponse(session: GeckoSession, response: org.mozilla.geckoview.WebResponse) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Intercepting document... Extracting real filename!", Toast.LENGTH_SHORT).show()
                }

                Thread {
                    try {
                        val inputStream = response.body ?: return@Thread
                        var contentDisposition: String? = null
                        var contentType: String? = null
                        
                        for ((key, value) in response.headers) {
                            if (key.equals("content-disposition", ignoreCase = true)) contentDisposition = value
                            if (key.equals("content-type", ignoreCase = true)) contentType = value
                        }
                        
                        var guessedName = android.webkit.URLUtil.guessFileName(response.uri, contentDisposition, contentType)
                        val lowerUrl = response.uri.lowercase()
                        
                        if (guessedName.contains(".aspx") || guessedName.contains(".ashx") || guessedName.endsWith(".bin") || guessedName == "downloadcopy") {
                            if (lowerUrl.contains("xlfilehandler")) {
                                guessedName = "OmniDesk_Spreadsheet_${System.currentTimeMillis()}.xlsx"
                            } else if (lowerUrl.contains("podhandler")) {
                                guessedName = "OmniDesk_Presentation_${System.currentTimeMillis()}.pptx"
                            } else if (lowerUrl.endsWith(".pdf")) {
                                guessedName = "OmniDesk_Document_${System.currentTimeMillis()}.pdf"
                            } else {
                                guessedName = "OmniDesk_Document_${System.currentTimeMillis()}.docx" 
                            }
                        }

                        val resolver = contentResolver
                        val contentValues = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, guessedName)
                            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                        }

                        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                        if (uri != null) {
                            resolver.openOutputStream(uri)?.use { outputStream ->
                                inputStream.use { input -> input.copyTo(outputStream) }
                            }
                            runOnUiThread {
                                Toast.makeText(this@MainActivity, "Download Complete: $guessedName", Toast.LENGTH_LONG).show()
                            }
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            Toast.makeText(this@MainActivity, "Hijack failed: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }.start()
            }
        }

        geckoSession.navigationDelegate = object : NavigationDelegate {
            override fun onNewSession(triggerSession: GeckoSession, uri: String): GeckoResult<GeckoSession>? {
                if (interceptCustomProtocol(uri)) return null 

                if (uri.isEmpty() || uri == "about:blank") {
                    val asyncResult = GeckoResult<GeckoSession>()
                    runOnUiThread {
                        val ghostSession = GeckoSession()
                        ghostSession.open(geckoRuntime)
                        ghostSession.navigationDelegate = this
                        asyncResult.complete(ghostSession)
                    }
                    return asyncResult
                }
                triggerSession.loadUri(uri)
                return null
            }

            override fun onLoadRequest(session: GeckoSession, request: NavigationDelegate.LoadRequest): GeckoResult<AllowOrDeny>? {
                if (interceptCustomProtocol(request.uri)) {
                    runOnUiThread { session.goBack() } 
                    return GeckoResult.fromValue(AllowOrDeny.DENY)
                }
                return GeckoResult.fromValue(AllowOrDeny.ALLOW)
            }
        }

        geckoView.setSession(geckoSession)
        
        val systemUrl = intent.dataString
        if (systemUrl != null) {
            geckoSession.loadUri(systemUrl)
        } else {
            geckoSession.loadUri("https://m365.cloud.microsoft/apps") 
        }
    }

    override fun onDestroy() {
        if (::geckoSession.isInitialized) geckoSession.close()
        if (::geckoRuntime.isInitialized) geckoRuntime.shutdown()
        super.onDestroy()
    }
    
    // --- NATIVE DOWNLOAD MANAGER BRIDGE ---
    // Safely handles file downloads by delegating them to the Android OS Download Manager
    private fun downloadFileFromWeb(url: String, fileName: String) {
        try {
            val request = android.app.DownloadManager.Request(Uri.parse(url))
            
            // Allow downloading over both Mobile Network and Wi-Fi
            request.setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
            request.setTitle(fileName)
            request.setDescription("Downloading document from OmniDesk 365...")
            
            // Show the download progress in the Android notification drawer
            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            
            // Save the file to the public Downloads folder of the user's phone
            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileName)
            
            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            downloadManager.enqueue(request)
            
            Toast.makeText(this, "Download started. Check your notifications.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to initiate download. URL might be secured.", Toast.LENGTH_LONG).show()
        }
    }

    // --- LIFECYCLE SYNCHRONIZATION (PREVENT WHITE SCREEN) ---
    
    // Automatically called by Android when the app returns to the foreground
    override fun onResume() {
        super.onResume()
    }

    // Automatically called by Android when the app is minimized or covered by another app
    override fun onPause() {
        super.onPause()
    }
}