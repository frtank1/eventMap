package com.eventmap.business.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.animateLottieCompositionAsState
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter

/**
 * Премиум-анимация пина через Lottie (Compottie). Этап 2 — демонстрация:
 * встроенная Lottie-анимация (пульсирующий круг), которую позже заменит
 * купленный из каталога файл (Кайрат/Тобол и т.п.).
 */
@Composable
fun LottiePin(size: Dp = 56.dp) {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(PREMIUM_LOTTIE)
    }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = Compottie.IterateForever,
    )
    Image(
        painter = rememberLottiePainter(composition = composition, progress = { progress }),
        contentDescription = null,
        modifier = Modifier.size(size),
    )
}

private const val PREMIUM_LOTTIE = """
{"v":"5.7.4","fr":30,"ip":0,"op":60,"w":120,"h":120,"nm":"premium","ddd":0,"assets":[],
"layers":[{"ddd":0,"ind":1,"ty":4,"nm":"c","sr":1,
"ks":{"o":{"a":0,"k":100},"r":{"a":0,"k":0},"p":{"a":0,"k":[60,60,0]},"a":{"a":0,"k":[0,0,0]},
"s":{"a":1,"k":[
{"t":0,"s":[55,55,100],"i":{"x":[0.5,0.5,0.5],"y":[1,1,1]},"o":{"x":[0.5,0.5,0.5],"y":[0,0,0]}},
{"t":30,"s":[105,105,100],"i":{"x":[0.5,0.5,0.5],"y":[1,1,1]},"o":{"x":[0.5,0.5,0.5],"y":[0,0,0]}},
{"t":60,"s":[55,55,100]}]}},
"shapes":[{"ty":"gr","it":[
{"ty":"el","d":1,"s":{"a":0,"k":[70,70]},"p":{"a":0,"k":[0,0]}},
{"ty":"fl","c":{"a":0,"k":[0.93,0.29,0.36,1]},"o":{"a":0,"k":100}},
{"ty":"tr","p":{"a":0,"k":[0,0]},"a":{"a":0,"k":[0,0]},"s":{"a":0,"k":[100,100]},"r":{"a":0,"k":0},"o":{"a":0,"k":100}}]}],
"ip":0,"op":60,"st":0,"bm":0}]}
"""
