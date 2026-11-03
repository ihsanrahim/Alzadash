package com.autodash.car

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.autodash.core.Browser
import java.io.ByteArrayInputStream

/**
 * The web browser on the car screen. Ideas taken from how AutoDeck's car browser behaves:
 * - scrolling is done inside the page (the nearest scrollable box under the middle of the screen),
 *   which is smoother than faking finger drags;
 * - typing goes straight into the page's selected field from our own car keyboard;
 * - desktop mode uses a desktop Firefox identity, which sites like YouTube serve a full page to;
 * - full-screen video fills the screen.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
class CarBrowser(private val context: Context, private val container: FrameLayout, startUrl: String) {
    private val web = WebView(context)
    private var fullscreenView: View? = null
    private val main = Handler(Looper.getMainLooper())

    /** Address and title whenever a page starts or finishes. */
    var onPage: (url: String, title: String?) -> Unit = { _, _ -> }
    var onLoading: (Boolean) -> Unit = {}
    /** A text box on the page was selected (true) or left (false). */
    var onField: (Boolean) -> Unit = {}

    init {
        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            textZoom = Browser.textZoom(context)
        }
        web.setBackgroundColor(DashboardBg)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        applyDesktop()
        web.addJavascriptInterface(Bridge(), "AutodashCar")
        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (Browser.adBlock(context) && Browser.isAd(request.url.host)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return null
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.url.toString().startsWith(Browser.HOME)) {
                    load(Browser.HOME)
                    return true
                }
                // Links to apps (intent:, market:, tel: ...) can't open on the car screen.
                val scheme = request.url.scheme ?: return false
                if (scheme == "intent") {
                    Regex("S\\.browser_fallback_url=([^;]+)").find(request.url.toString())?.groupValues?.get(1)
                        ?.let { load(android.net.Uri.decode(it)) }
                    return true
                }
                return scheme != "http" && scheme != "https"
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                onLoading(true)
                onPage(url ?: "", null)
                onField(false)
            }

            override fun onPageFinished(view: WebView, url: String?) {
                onLoading(false)
                onPage(url ?: "", view.title)
                view.evaluateJavascript(HELPERS, null)
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                fullscreenView?.let { container.removeView(it) }
                fullscreenView = view
                container.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
                web.evaluateJavascript(FILL_VIDEO, null)
            }

            override fun onHideCustomView() {
                fullscreenView?.let { container.removeView(it) }
                fullscreenView = null
                web.evaluateJavascript(UNFILL_VIDEO, null)
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                onPage(view.url ?: "", title)
            }

            // Lets streaming sites ask for their copy-protection (DRM) key.
            override fun onPermissionRequest(request: PermissionRequest) {
                val ok = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }
                if (ok.isEmpty()) request.deny() else request.grant(ok.toTypedArray())
            }
        }
        container.addView(web, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        load(startUrl)
    }

    private inner class Bridge {
        @JavascriptInterface
        fun field(on: Boolean) {
            main.post { onField(on) }
        }
    }

    fun load(url: String) {
        if (url.startsWith(Browser.HOME)) {
            web.loadDataWithBaseURL(Browser.HOME, Browser.homeHtml(context), "text/html", "utf-8", null)
        } else {
            web.loadUrl(url)
        }
    }

    fun canGoBack() = fullscreenView != null || web.canGoBack()

    fun goBack() {
        if (fullscreenView != null) {
            web.evaluateJavascript("document.fullscreenElement&&document.exitFullscreen();", null)
            web.webChromeClient?.onHideCustomView()
            return
        }
        if (web.canGoBack()) web.goBack()
    }

    fun goForward() {
        if (web.canGoForward()) web.goForward()
    }

    fun reload() = web.reload()
    fun stop() = web.stopLoading()

    /** Title and address of the page showing now, or null on the start page. */
    val page: Pair<String, String>?
        get() {
            val url = web.url ?: return null
            if (url.startsWith(Browser.HOME)) return null
            return (web.title?.takeIf { it.isNotBlank() } ?: url) to url
        }

    // ---- Scrolling, gathered into one page scroll every few frames ----

    private var sx = 0f
    private var sy = 0f
    private val flush = Runnable {
        val dx = sx
        val dy = sy
        sx = 0f
        sy = 0f
        web.evaluateJavascript("window.__adScroll?window.__adScroll($dx,$dy):window.scrollBy($dx/devicePixelRatio,$dy/devicePixelRatio);", null)
    }

    /** Car-screen drag, in screen pixels. */
    fun scroll(dx: Float, dy: Float) {
        if (fullscreenView != null) return
        sx += dx
        sy += dy
        main.removeCallbacks(flush)
        main.postDelayed(flush, 24)
    }

    /** Pinch on the car screen. */
    fun zoomBy(factor: Float) {
        web.zoomBy(factor.coerceIn(0.5f, 2f))
    }

    // ---- Typing from the car keyboard into the page ----

    fun type(text: String) {
        web.evaluateJavascript("window.__adType&&window.__adType(${org.json.JSONObject.quote(text)});", null)
    }

    fun backspace() {
        web.evaluateJavascript("window.__adType&&window.__adType(null);", null)
    }

    /** Enter in the page's field: sends the form, like a search box's Go. */
    fun enter() {
        web.evaluateJavascript("window.__adEnter&&window.__adEnter();", null)
        onField(false)
    }

    private fun applyDesktop() {
        val desktop = Browser.desktop(context)
        web.settings.userAgentString = if (desktop) Browser.DESKTOP_UA else null
        web.settings.loadWithOverviewMode = desktop
    }

    /** Applies the desktop-site and text size settings and reloads. */
    fun applySettings() {
        applyDesktop()
        web.settings.textZoom = Browser.textZoom(context)
        web.reload()
    }

    /** Forgets cookies, history and cached pages. */
    fun clearData() {
        web.clearHistory()
        web.clearCache(true)
        CookieManager.getInstance().removeAllCookies(null)
        WebStorage.getInstance().deleteAllData()
    }

    fun onPause() = web.onPause()
    fun onResume() = web.onResume()

    /** Stops everything, including any video sound. */
    fun destroy() {
        main.removeCallbacks(flush)
        web.loadUrl("about:blank")
        container.removeAllViews()
        web.destroy()
    }

    companion object {
        private val DashboardBg = android.graphics.Color.parseColor("#0E1116")

        private const val FILL_VIDEO =
            "(function(){if(document.getElementById('ad-fs'))return;var s=document.createElement('style');s.id='ad-fs';" +
                "s.textContent='video{object-fit:contain!important}';document.documentElement.appendChild(s);})();"
        private const val UNFILL_VIDEO = "(function(){var s=document.getElementById('ad-fs');if(s)s.remove();})();"

        /** Added to every page: in-page scrolling, typing into the selected field, and field focus reports. */
        private val HELPERS = """
(function(){
  if(window.__adReady) return; window.__adReady=true;
  var re=/(auto|scroll|overlay)/;
  window.__adScroll=function(dx,dy){
    var r=window.devicePixelRatio||1; dx/=r; dy/=r;
    var el=document.elementFromPoint(innerWidth/2,innerHeight/2);
    while(el&&el!==document.body&&el!==document.documentElement){
      var cs=getComputedStyle(el);
      var canY=dy!==0&&re.test(cs.overflowY)&&el.scrollHeight>el.clientHeight+1;
      var canX=dx!==0&&re.test(cs.overflowX)&&el.scrollWidth>el.clientWidth+1;
      if(canY||canX){var t=el.scrollTop,l=el.scrollLeft;el.scrollBy(dx,dy);if(el.scrollTop!==t||el.scrollLeft!==l)return;}
      el=el.parentElement;
    }
    window.scrollBy(dx,dy);
  };
  function isField(e){
    if(!e) return false;
    if(e.isContentEditable) return true;
    if(e.tagName==='TEXTAREA') return true;
    if(e.tagName!=='INPUT') return false;
    var t=(e.type||'text').toLowerCase();
    return ['text','search','email','url','tel','password','number',''].indexOf(t)>=0;
  }
  document.addEventListener('focusin',function(ev){ if(isField(ev.target)){window.__adField=ev.target;AutodashCar.field(true);} },true);
  document.addEventListener('focusout',function(ev){ if(isField(ev.target)) setTimeout(function(){ if(!isField(document.activeElement)) AutodashCar.field(false); },150); },true);
  window.__adType=function(t){
    var e=isField(document.activeElement)?document.activeElement:window.__adField; if(!e) return;
    e.focus();
    var ok=false;
    try{ ok=document.execCommand(t===null?'delete':'insertText',false,t===null?undefined:t); }catch(x){}
    if(!ok&&e.value!==undefined){
      var s=e.selectionStart==null?e.value.length:e.selectionStart, n=e.selectionEnd==null?s:e.selectionEnd;
      if(t===null){ if(s===n&&s>0) s--; e.value=e.value.slice(0,s)+e.value.slice(n); n=s; }
      else { e.value=e.value.slice(0,s)+t+e.value.slice(n); n=s+t.length; }
      try{ e.selectionStart=e.selectionEnd=n; }catch(x){}
      e.dispatchEvent(new Event('input',{bubbles:true}));
    }
  };
  window.__adEnter=function(){
    var e=isField(document.activeElement)?document.activeElement:window.__adField; if(!e) return;
    ['keydown','keypress','keyup'].forEach(function(k){
      e.dispatchEvent(new KeyboardEvent(k,{key:'Enter',code:'Enter',keyCode:13,which:13,bubbles:true}));
    });
    if(e.form){ if(e.form.requestSubmit) e.form.requestSubmit(); else e.form.submit(); }
    e.blur();
  };
})();"""
    }
}
