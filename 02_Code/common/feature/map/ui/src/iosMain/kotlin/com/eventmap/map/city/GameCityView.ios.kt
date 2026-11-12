@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, org.jetbrains.compose.resources.ExperimentalResourceApi::class)
package com.eventmap.map.city

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import eventmap.common.feature.map.ui.generated.resources.Res
import kotlinx.cinterop.readValue
import platform.CoreGraphics.CGRectZero
import platform.darwin.NSObject
import platform.Foundation.NSURL
import platform.WebKit.*

private class CityScriptHandler(val expectedUrl:String,val receive:(String)->Unit):NSObject(),WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController:WKUserContentController,didReceiveScriptMessage:WKScriptMessage) {
        if(!didReceiveScriptMessage.frameInfo.mainFrame || didReceiveScriptMessage.frameInfo.request.URL?.absoluteString!=expectedUrl) return
        val raw=didReceiveScriptMessage.body as? String ?: return
        if(parseCityMessage(raw)!=null) receive(raw)
    }
}

private class CityNavigation(val entry:String):NSObject(),WKNavigationDelegateProtocol {
    override fun webView(webView:WKWebView,decidePolicyForNavigationAction:WKNavigationAction,decisionHandler:(WKNavigationActionPolicy)->Unit) {
        val url=decidePolicyForNavigationAction.request.URL?.absoluteString
        decisionHandler(if(url==entry) WKNavigationActionPolicy.WKNavigationActionPolicyAllow else WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
    }
}

@Composable
actual fun GameCityView(payload:String,modifier:Modifier,onMessage:(String)->Unit) {
    val callback by rememberUpdatedState(onMessage)
    var ready by remember { mutableStateOf(false) }
    val url=remember { Res.getUri("files/game-city/index.html") }
    val handler=remember { CityScriptHandler(url) { raw -> if(parseCityMessage(raw)?.type=="ready")ready=true;callback(raw) } }
    val navigation=remember { CityNavigation(url) }
    val web=remember {
        val config=WKWebViewConfiguration()
        config.websiteDataStore=WKWebsiteDataStore.defaultDataStore()
        config.userContentController.addScriptMessageHandler(handler,"EventMapNative")
        WKWebView(CGRectZero.readValue(),config).apply {
            navigationDelegate=navigation
            scrollView.scrollEnabled=false
            val entry=NSURL.URLWithString(url)!!
            loadFileURL(entry,allowingReadAccessToURL=entry.URLByDeletingLastPathComponent!!)
        }
    }
    LaunchedEffect(web,ready,payload) { if(ready)web.evaluateJavaScript("window.EventMapMobile?.receive($payload);",completionHandler=null) }
    DisposableEffect(web) { onDispose { web.stopLoading();web.configuration.userContentController.removeScriptMessageHandlerForName("EventMapNative");web.navigationDelegate=null } }
    UIKitView(factory={web},modifier=modifier)
}
