package com.eventmap.theme.ui_kit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventmap.theme.NisTheme
import com.eventmap.theme.interFontFamily

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun TextPreview() {
    NisTheme {
        Column() {
            Text_20_Bold(text = "Text_20_Bold")
            Text_20_Reg(text = "Text_20_Reg")
            Text_16_Bold(text = "Text_16_Bold")
            Text_16_Reg(text = "Text_16_Reg")
            Text_14_Bold(text = "Text_14_Bold")
            Text_14_Reg(text = "Text_14_Reg")
            Text_16_Reg(text = "Text_16_Reg")
            Text_14_Reg(text = "Text_14_Reg")
            Text_12_Reg(text = "Text_12_Reg")
        }
    }
}

@Composable
fun Text_24_700(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    )
}

@Composable
fun Text_32_Bold(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
    )
}

@Composable
fun Text_24_Bold(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    )
}

@Composable
fun Text_24_Reg(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
    )
}

@Composable
fun Text_24_Bold_NameApp(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
    )
}

@Composable
fun Text_24_400(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
    )
}
@Composable
fun Text_20_Bold(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
    )
}

@Composable
fun Text_20_Reg(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = 20.sp,
    )
}

@Composable
fun Text_16_Bold(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
    )
}

@Composable
fun Text_18_Bold(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
    )
}

@Composable
fun Text_16_Reg(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    )
}

@Composable
fun Text_14_Bold(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    )
}



@Composable
fun Text_12_Bold(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
    )
}

@Composable
fun TextWithIcon(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    icon: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            color = color,
            textAlign = textAlign,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            onTextLayout = onTextLayout,
            fontFamily = interFontFamily(),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
        )

        icon?.let {
            Spacer(modifier = Modifier.width(4.dp)) //
            it()
        }
    }
}


@Composable
fun Text_14_Reg(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    style: TextStyle = LocalTextStyle.current
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        lineHeight = 20.sp,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        style =style,
    )
}

@Composable
fun Text_12_Reg(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    )
}

@Composable
fun Text_13_Reg(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    )
}

@Composable
fun Text_10_Reg(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
    )
}

@Composable
fun Text_11_Reg(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    onTextLayout: (TextLayoutResult) -> Unit = {},
){
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = onTextLayout,
        fontFamily = interFontFamily(),
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    )
}