package com.eventmap.theme.ui_kit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.eventmap.theme.NisTheme
import com.eventmap.theme.pressed_button
import com.eventmap.theme.primery_blue
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun ButtonPreview() {
    NisTheme {
        Column() {
            AOButton(onClick = {}, text = "asdfasdf")
            AOTextButton(onClick = {}, text = "asdfasdf")
            AOOutlineButton(onClick = {}, text = "asdfasdf")
            AOCheckOutlineButton(onClick = {}, text = "asdfasdf", selected = true)
        }
    }
}

@Composable
fun AOButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    text: String = ""
){
    Button(
        onClick =  onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.extraLarge,
    ){
        Text(text = text)
    }
}

@Composable
fun AOButtonSquare(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    text: String = ""
){
    Button(
        onClick =  onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,
    ){
        Text(text = text)
    }
}
@Composable
fun AOButtonSquareWithIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    text: String = "",
    size: Dp,
    leadIcon: DrawableResource,
    tintIcon: Color? = null
){
    Button(
        onClick =  onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.medium,

    ){
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(resource = leadIcon),
                tint = tintIcon?: primery_blue,
                contentDescription = "",
                modifier = Modifier
                    .size(size)
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Text(text = text)
        }

    }
}


@Composable
fun AOOutlineButtonSecond(
    onClick: () -> Unit,
    fontSize: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier,
    colorText: Color = Color.Unspecified,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    text: String = "",
    size: Dp,
    leadIcon: DrawableResource? = null,
    tintIcon: Color? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        shape = MaterialTheme.shapes.medium,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    ) {



        Row(verticalAlignment = Alignment.CenterVertically) {
            if (text.isNotEmpty()) {
                Text_12_Reg(text = text, color = colorText,   modifier = Modifier.padding(end = 8.dp))
            }

            if (leadIcon != null) {
                Icon(
                    painter = painterResource(leadIcon),
                    tint = tintIcon ?: primery_blue,
                    contentDescription = null,
                    modifier = Modifier
                        .size(size)
                    )
            }
        }
    }
}


@Composable
fun AOOutlineButton(
    onClick: () -> Unit,
    fontSize: TextUnit = TextUnit.Unspecified,
    modifier: Modifier = Modifier,
    colorText: Color = Color.Unspecified,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    text: String = "",
    leadIcon: DrawableResource? = null,
    tintIcon: Color? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        shape = shape,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    ) {


        if (text.isNotEmpty()) {
            Text_12_Reg(text = text, color = colorText)
        }
        if (leadIcon != null) {
            Icon(
                painter = painterResource(leadIcon),
                tint = tintIcon ?: primery_blue,
                contentDescription = null,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
fun AOOutlineButtonWithOneIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    size: Dp,
    leadIcon: DrawableResource,
    tintIcon: Color? = null
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        shape = MaterialTheme.shapes.medium,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    ) {
        Icon(
            painter = painterResource(leadIcon),
            tint = tintIcon ?: primery_blue,
            contentDescription = null,
            modifier = Modifier.size(size)
        )
    }
}



@Composable
fun AOCheckOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = ButtonDefaults.outlinedButtonBorder,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    text: String = "",
    selected: Boolean = false
){
    val colorsS = if(selected){
        ButtonDefaults.outlinedButtonColors(
            containerColor = pressed_button
        )
    }else{
        colors
    }
    OutlinedButton(
        onClick =  onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colorsS,
        elevation = elevation,
        shape = MaterialTheme.shapes.medium,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
    ){
        Text(text = text)
    }
}

@Composable
fun AOTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.textButtonColors(),
    elevation: ButtonElevation? = null,
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    text: String = "",
    textColor: Color = Color.Unspecified
){
    TextButton(
        onClick =  onClick,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        elevation = elevation,
        border = border,
        contentPadding = contentPadding,
        interactionSource = interactionSource,
        shape = ButtonDefaults.textShape,
    ){
        Text(text = text, color = textColor)
    }
}