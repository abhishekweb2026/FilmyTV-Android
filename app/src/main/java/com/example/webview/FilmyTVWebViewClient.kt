package com.example.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.util.Log
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import java.net.URISyntaxException

class FilmyTVWebViewClient(
  private val context: Context,
  private val onPageStartedCallback: (url: String?) -> Unit,
  private val onPageFinishedCallback: (url: String?) -> Unit,
  private val onPageCommitVisibleCallback: (url: String?) -> Unit = {},
  private val onErrorCallback: (failingUrl: String?) -> Unit
) : WebViewClient() {

  companion object {
    private const val TAG = "FilmyTVWebViewClient"
    const val PRIMARY_DOMAIN = "filmytv.webapps.in.net"
    const val FALLBACK_DOMAIN = "webapps.in.net"
  }

  override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
    val uri = request?.url ?: return false
    val url = uri.toString()

    return handleUri(view, uri, url)
  }

  @Deprecated("Deprecated in Java", ReplaceWith("handleUri(view, Uri.parse(url), url)"))
  override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
    if (url == null) return false
    val uri = Uri.parse(url)
    return handleUri(view, uri, url)
  }

  private fun handleUri(view: WebView?, uri: Uri, url: String): Boolean {
    val scheme = uri.scheme?.lowercase() ?: return false

    // 1. Handle Android Intent Scheme (e.g., intent://...)
    if (scheme == "intent") {
      try {
        val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        if (intent != null) {
          val packageManager = context.packageManager
          val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.resolveActivity(
              intent,
              android.content.pm.PackageManager.ResolveInfoFlags.of(0L)
            )
          } else {
            @Suppress("DEPRECATION")
            packageManager.resolveActivity(intent, 0)
          }

          if (info != null) {
            context.startActivity(intent)
            return true
          }

          // Check for fallback browser URL in intent
          val fallbackUrl = intent.getStringExtra("browser_fallback_url")
          if (!fallbackUrl.isNullOrEmpty()) {
            view?.loadUrl(fallbackUrl)
            return true
          }

          // Fallback to app store if package specified
          val packageName = intent.`package`
          if (!packageName.isNullOrEmpty()) {
            val marketIntent = Intent(
              Intent.ACTION_VIEW,
              Uri.parse("market://details?id=$packageName")
            ).apply {
              addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
              context.startActivity(marketIntent)
            } catch (e: ActivityNotFoundException) {
              val playStoreIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
              ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              context.startActivity(playStoreIntent)
            }
            return true
          }
        }
      } catch (e: URISyntaxException) {
        Log.e(TAG, "Invalid intent URI: $url", e)
      } catch (e: ActivityNotFoundException) {
        Log.e(TAG, "No activity found for intent: $url", e)
      }
      return true
    }

    // 2. Handle Custom Protocols (UPI, Payment apps, Phone, Email, WhatsApp, etc.)
    val nonHttpSchemes = setOf(
      "upi", "paytmmp", "phonepe", "tez", "gpay", "bhim",
      "tel", "mailto", "sms", "whatsapp", "market"
    )

    if (nonHttpSchemes.contains(scheme)) {
      try {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
          addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return true
      } catch (e: ActivityNotFoundException) {
        val message = when (scheme) {
          "upi", "paytmmp", "phonepe", "tez", "gpay" -> "No UPI or Payment application installed."
          "whatsapp" -> "WhatsApp is not installed."
          "tel" -> "No phone dialer application available."
          "mailto" -> "No email application found."
          else -> "No application available to open this link."
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        return true
      }
    }

    // 3. HTTP / HTTPS Handling
    if (scheme == "http" || scheme == "https") {
      val host = uri.host?.lowercase() ?: ""

      // Check if this is an internal domain
      val isInternal = host == PRIMARY_DOMAIN ||
          host.endsWith(".$PRIMARY_DOMAIN") ||
          host.endsWith(".$FALLBACK_DOMAIN")

      if (isInternal) {
        // Keep internal website navigation inside the WebView
        return false
      }

      // Check if this is a known payment or authentication redirect that should load inside WebView
      // (e.g., payment gateways embedding checkout, OAuth callback screens)
      val isAuthOrPaymentRedirect = isAuthOrPaymentHost(host)
      if (isAuthOrPaymentRedirect) {
        // Let it load in the WebView so cookies and session are preserved
        return false
      }

      // External website -> Open in Chrome Custom Tab for polished UX & security
      openInCustomTab(context, uri)
      return true
    }

    return false
  }

  private fun isAuthOrPaymentHost(host: String): Boolean {
    val authAndPaymentKeywords = listOf(
      "razorpay", "cashfree", "paytm", "phonepe", "billdesk", "payu", "ccavenue",
      "stripe", "paypal", "instamojo", "accounts.google.com", "login.live.com",
      "facebook.com/v", "appleid.apple.com"
    )
    return authAndPaymentKeywords.any { host.contains(it) }
  }

  private fun openInCustomTab(context: Context, uri: Uri) {
    try {
      val customTabsIntent = CustomTabsIntent.Builder()
        .setShowTitle(true)
        .build()
      customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      customTabsIntent.launchUrl(context, uri)
    } catch (e: Exception) {
      // Fallback to standard external browser intent
      val browserIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      try {
        context.startActivity(browserIntent)
      } catch (ex: ActivityNotFoundException) {
        Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
      }
    }
  }

  override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
    super.onPageStarted(view, url, favicon)
    onPageStartedCallback(url)
  }

  override fun onPageCommitVisible(view: WebView?, url: String?) {
    super.onPageCommitVisible(view, url)
    onPageCommitVisibleCallback(url)
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    // Persist cookies across launches
    CookieManager.getInstance().flush()
    onPageFinishedCallback(url)
  }

  override fun onReceivedError(
    view: WebView?,
    request: WebResourceRequest?,
    error: WebResourceError?
  ) {
    super.onReceivedError(view, request, error)
    if (request?.isForMainFrame == true) {
      val failingUrl = request.url.toString()
      Log.w(TAG, "Main frame error: ${error?.description} on $failingUrl")
      onErrorCallback(failingUrl)
    }
  }

  @Deprecated("Deprecated in Java", ReplaceWith("super.onReceivedError(view, errorCode, description, failingUrl)"))
  override fun onReceivedError(
    view: WebView?,
    errorCode: Int,
    description: String?,
    failingUrl: String?
  ) {
    super.onReceivedError(view, errorCode, description, failingUrl)
    Log.w(TAG, "Legacy main frame error: $description on $failingUrl")
    onErrorCallback(failingUrl)
  }

  override fun onReceivedHttpError(
    view: WebView?,
    request: WebResourceRequest?,
    errorResponse: WebResourceResponse?
  ) {
    super.onReceivedHttpError(view, request, errorResponse)
    if (request?.isForMainFrame == true) {
      val statusCode = errorResponse?.statusCode ?: 200
      if (statusCode >= 500) {
        // Server unavailable / gateway timeout
        onErrorCallback(request.url.toString())
      }
    }
  }

  override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
    // Strictly adhere to security requirement: Do not disable SSL certificate validation
    Log.e(TAG, "SSL certificate error: ${error?.primaryError}. Cancelling connection.")
    handler?.cancel()
    onErrorCallback(view?.url)
  }
}
