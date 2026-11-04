package com.eventmap.views.media

import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.eventmap.core.network.ktor.createMediaHttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap

/** CDN images use a client without EventMap auth or URL logging. Limited to 4 MiB. */
@OptIn(ExperimentalResourceApi::class)
@Composable
fun RemoteMediaImage(url: String?, modifier: Modifier = Modifier, description: String? = null) {
    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank()) return@LaunchedEffect
        val client = createMediaHttpClient()
        try {
            val response = client.get(url)
            if (response.status.isSuccess()) {
                val bytes = response.bodyAsChannel().readRemaining(4 * 1024 * 1024L + 1).readByteArray()
                if (bytes.size <= 4 * 1024 * 1024) bitmap = withContext(Dispatchers.Default) { bytes.decodeToImageBitmap() }
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* Keep placeholder; never log signed URLs. */ }
        finally { client.close() }
    }
    bitmap?.let { Image(it, description, modifier, contentScale = ContentScale.Crop) }
}
