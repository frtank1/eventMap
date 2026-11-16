package com.eventmap.map.city

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import eventmap.common.feature.map.ui.generated.resources.Res
import org.jetbrains.compose.resources.ExperimentalResourceApi

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalResourceApi::class)
@Composable
actual fun GameCityView(payload:String, modifier:Modifier, onMessage:(String)->Unit) {
    val context=LocalContext.current
    val callback by rememberUpdatedState(onMessage)
    var ready by remember { mutableStateOf(false) }
    val web=remember {
        WebView(context).apply {
            layoutParams=ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled=true; settings.domStorageEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically=false
            settings.setSupportMultipleWindows(false)
            val loader=WebViewAssetLoader.Builder().addPathHandler("/assets/",WebViewAssetLoader.AssetsPathHandler(context)).build()
            val entry=Res.getUri("files/game-city/index.html").replace("file:///android_asset/","https://appassets.androidplatform.net/assets/")
            webViewClient=object:WebViewClient() {
                override fun shouldInterceptRequest(view:WebView,request:WebResourceRequest):WebResourceResponse? = loader.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view:WebView,request:WebResourceRequest):Boolean = request.url.toString()!=entry
            }
            if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(this,"EventMapNative",setOf("https://appassets.androidplatform.net")) { _,message,origin,isMainFrame,_ ->
                    if(isMainFrame && origin.host=="appassets.androidplatform.net") {
                        val raw=message.data ?: return@addWebMessageListener
                        val parsed=parseCityMessage(raw) ?: return@addWebMessageListener
                        if(parsed.type=="ready") ready=true
                        callback(raw)
                    }
                }
                loadUrl(entry)
            } else post { callback("{\"v\":1,\"type\":\"error\",\"state\":\"unsupported-webview\"}") }
        }
    }
    LaunchedEffect(web,ready,payload) { if(ready) web.evaluateJavascript("window.EventMapMobile?.receive($payload);",null) }
    DisposableEffect(web) { onDispose { ready=false;web.stopLoading();web.loadUrl("about:blank");web.removeAllViews();web.destroy() } }
    AndroidView(factory={web},modifier=modifier)
}
