/*
 * Web engine for the Autodash car-screen browser, ported from AA Browser.
 *
 * Copyright (C) 2025 AABrowser Contributors (https://github.com/kododake/AABrowser)
 * Changes for Autodash: Copyright (C) 2026 Autodash contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Based on these AA Browser files (kododake/AABrowser, GPL-3.0-or-later):
 * web/ConfiguredWebView.kt, web/UserAgentManager.kt, web/WebScripts.kt and
 * web/BrowserWebViewClient.kt. Changes: trimmed to what a WebView drawn on the
 * Android Auto surface needs (no activity dialogs, SSL prompts or downloads),
 * user agent limited to the Chrome profile, settings come from Autodash.
 */

package com.alzadash.car.aab

import android.graphics.Color
import android.os.Build
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature

/** How AA Browser sets up its WebView, so sites such as YouTube load as they do in Chrome. */
object AABrowserWeb {
    private const val CHROME_VERSION = "154.0.0.0"
    private const val ANDROID_PLATFORM_VERSION = "10.0.0"
    private const val WINDOWS_PLATFORM_VERSION = "10.0.0"
    private const val DESKTOP_BITNESS = 64

    const val MOBILE_CHROME_UA =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_VERSION Mobile Safari/537.36"
    const val WINDOWS_CHROME_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$CHROME_VERSION Safari/537.36"

    /** Settings from AA Browser's configureWebView: JavaScript, storage, autoplay, popups, zoom, cookies, DRM. */
    fun configure(webView: WebView, desktop: Boolean) {
        with(webView) {
            setBackgroundColor(Color.parseColor("#0E1116"))
            isHorizontalScrollBarEnabled = false
            isVerticalScrollBarEnabled = true

            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(this, DRM_L3_ENFORCER_JS, setOf("*"))
            }

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                javaScriptCanOpenWindowsAutomatically = true
                setSupportMultipleWindows(true)
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                offscreenPreRaster = true
            }

            CookieManager.getInstance().also {
                it.setAcceptCookie(true)
                it.setAcceptThirdPartyCookies(this, true)
            }
        }
        applyIdentity(webView, desktop)
    }

    /** AA Browser's UserAgentManager.applyBrowserIdentity for the Chrome profile. */
    fun applyIdentity(webView: WebView, desktop: Boolean) {
        webView.settings.userAgentString = if (desktop) WINDOWS_CHROME_UA else MOBILE_CHROME_UA
        webView.settings.useWideViewPort = true
        webView.settings.loadWithOverviewMode = desktop
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            WebSettingsCompat.setUserAgentMetadata(webView.settings, chromeMetadata(desktop))
        }
    }

    private fun chromeMetadata(desktop: Boolean): UserAgentMetadata = UserAgentMetadata.Builder()
        .setBrandVersionList(
            listOf(
                UserAgentMetadata.BrandVersion.Builder().setBrand("Google Chrome")
                    .setMajorVersion(CHROME_VERSION.substringBefore('.')).setFullVersion(CHROME_VERSION).build(),
                UserAgentMetadata.BrandVersion.Builder().setBrand("Chromium")
                    .setMajorVersion(CHROME_VERSION.substringBefore('.')).setFullVersion(CHROME_VERSION).build(),
            ),
        )
        .setFullVersion(CHROME_VERSION)
        .setPlatform(if (desktop) "Windows" else "Android")
        .setPlatformVersion(if (desktop) WINDOWS_PLATFORM_VERSION else ANDROID_PLATFORM_VERSION)
        .setArchitecture(if (desktop) "x86" else "")
        .setModel("")
        .setMobile(!desktop)
        .setBitness(if (desktop) DESKTOP_BITNESS else UserAgentMetadata.BITNESS_DEFAULT)
        .setWow64(false)
        .build()

    /**
     * AA Browser's onPermissionRequest: protected (DRM) video is allowed, so paid video sites
     * play; everything else, including the microphone and camera, is refused on the car screen.
     */
    fun onPermissionRequest(view: WebView, request: PermissionRequest) {
        val grant = request.resources.filter { it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID }.toTypedArray()
        if (grant.isEmpty()) request.deny() else view.post { request.grant(grant) }
    }

    /** AA Browser's link rule: web pages open here, links to apps (intent:, market:, tel: ...) don't. */
    fun isWebScheme(scheme: String?): Boolean =
        scheme == null || scheme.lowercase() in setOf("http", "https", "about", "data", "javascript")

    /**
     * From AA Browser's WebScripts: phone WebViews only have software (L3) Widevine, so a request
     * for hardware-secure playback is retried as software, letting DRM video start. YouTube is left alone.
     */
    val DRM_L3_ENFORCER_JS = """
(function() {
    try {
        const host = window.location.hostname;
        if (host && (
            host === 'youtube.com' || host.endsWith('.youtube.com') ||
            host === 'googlevideo.com' || host.endsWith('.googlevideo.com') ||
            host === 'youtube-nocookie.com' || host.endsWith('.youtube-nocookie.com')
        )) {
            return;
        }
    } catch (_) {}
    if (typeof navigator === 'undefined' || !navigator.requestMediaKeySystemAccess) return;
    const patchKey = typeof Symbol !== 'undefined' && Symbol.for ? Symbol.for('__aab_drm_l3_enforced__') : '__aab_drm_l3_enforced__';
    if (navigator[patchKey]) return;
    const originalRequest = navigator.requestMediaKeySystemAccess;
    const soft = function(caps) {
        return Array.from(caps).map(function(cap) {
            const c = Object.assign({}, cap);
            if (c.robustness && typeof c.robustness === 'string' && c.robustness.startsWith('HW_SECURE')) c.robustness = 'SW_SECURE_DECODE';
            return c;
        });
    };
    const patchedRequest = function requestMediaKeySystemAccess(keySystem, configs) {
        if (keySystem === 'com.widevine.alpha' && configs) {
            try {
                const newConfigs = Array.from(configs).map(function(config) {
                    const n = Object.assign({}, config);
                    if (config.videoCapabilities) n.videoCapabilities = soft(config.videoCapabilities);
                    if (config.audioCapabilities) n.audioCapabilities = soft(config.audioCapabilities);
                    return n;
                });
                return originalRequest.call(navigator, keySystem, newConfigs).catch(function() {
                    return originalRequest.call(navigator, keySystem, configs);
                });
            } catch (_) {
                return originalRequest.call(navigator, keySystem, configs);
            }
        }
        return originalRequest.call(navigator, keySystem, configs);
    };
    try {
        Object.defineProperty(patchedRequest, 'name', { value: 'requestMediaKeySystemAccess' });
        patchedRequest.toString = function() { return 'function requestMediaKeySystemAccess() { [native code] }'; };
    } catch (_) {}
    try {
        Object.defineProperty(navigator, patchKey, { value: true, enumerable: false, configurable: false });
        Object.defineProperty(navigator, 'requestMediaKeySystemAccess', { value: patchedRequest, writable: true, configurable: true });
    } catch (_) {
        navigator.requestMediaKeySystemAccess = patchedRequest;
    }
})();
""".trimIndent()
}
