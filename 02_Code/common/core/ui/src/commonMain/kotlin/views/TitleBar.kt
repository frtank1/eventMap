package com.eventmap.views

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.eventmap.theme.black
import com.eventmap.theme.light_gray
import com.eventmap.theme.milk
import com.eventmap.theme.ui_kit.Text_20_Bold
import com.eventmap.core.ui.resources.Res
import com.eventmap.core.ui.resources.arrow
import org.jetbrains.compose.resources.painterResource


@Composable
fun TitleBar(
    modifier: Modifier = Modifier.padding(top = 16.dp),
    title: String,
    onClickBack: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 16.dp)
    ) {
        if (onClickBack != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(milk)
                    .clickable { onClickBack.invoke() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(Res.drawable.arrow),
                    contentDescription = "back",
                    tint = black,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Text_20_Bold(
            text = title,

            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
        )
    }
}