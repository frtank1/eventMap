package com.eventmap.theme.ui_kit

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.eventmap.theme.black
import com.eventmap.theme.gray
import com.eventmap.theme.light_gray
import com.eventmap.theme.primery_blue
import com.eventmap.theme.system_error
import com.eventmap.theme.white
import kotlin.math.absoluteValue
import kotlin.text.forEach

@Composable
fun EditTextWithTitle(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,

    title: String? = null,
    showRequiredStar: Boolean = false,

    placeholder: String? = null,
    enabled: Boolean = true,
    maxLines: Int = 100,
    minLines: Int = 1,
    isError: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = false,

    containerColor: Color = white,
    unfocusedBorderColor: Color = light_gray,

    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,

    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {

    Column(
        modifier = modifier
    ) {

        if (title != null) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showRequiredStar) {


                    Text_14_Reg(
                        text = "* ",
                        color = system_error
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text_14_Reg(
                    text = title,
                    color = black
                )


            }

            Spacer(Modifier.height(4.dp))
        }

        EditText(
            value = value,
            onValueChange = onValueChange,

            enabled = enabled,
            isError = isError,
            readOnly = readOnly,
            singleLine = singleLine,

            maxLines = maxLines,
            minLines = minLines,

            modifier = Modifier.fillMaxWidth(),

            placeholder = placeholder,

            containerColor = containerColor,
            unfocusedBorderColor = unfocusedBorderColor,

            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,

            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditText(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    maxLines: Int = 100,
    minLines: Int = 1,
    isError: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    containerColor: Color = white,
    unfocusedBorderColor: Color = light_gray,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    //keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text_14_Reg(label ?: "") },
        placeholder = { Text_14_Reg(placeholder ?: "", color = gray) },
        modifier = modifier,
        isError = isError,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        maxLines = maxLines,
        minLines = minLines,
        readOnly = readOnly,
        singleLine = singleLine,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            errorContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            focusedIndicatorColor = primery_blue,
            unfocusedIndicatorColor = unfocusedBorderColor,
            errorIndicatorColor = system_error,
            focusedLabelColor = primery_blue,
            unfocusedLabelColor = gray,
            errorLabelColor = system_error,
            errorTrailingIconColor = system_error,
            focusedTrailingIconColor = primery_blue,
            unfocusedTrailingIconColor = light_gray,
            disabledPlaceholderColor = gray,
            errorPlaceholderColor = system_error,
            focusedPlaceholderColor = gray
        ),
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditText12Reg(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    maxLines: Int = 100,
    minLines: Int = 1,
    isError: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    containerColor: Color = white,
    unfocusedBorderColor: Color = light_gray,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    //keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text_12_Reg(label ?: "") },
        placeholder = { Text_12_Reg(placeholder ?: "", color = gray) },
        modifier = modifier,
        isError = isError,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        maxLines = maxLines,
        minLines = minLines,
        readOnly = readOnly,
        singleLine = singleLine,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            errorContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            disabledContainerColor = containerColor,
            focusedIndicatorColor = primery_blue,
            unfocusedIndicatorColor = unfocusedBorderColor,
            errorIndicatorColor = system_error,
            focusedLabelColor = primery_blue,
            unfocusedLabelColor = gray,
            errorLabelColor = system_error,
            errorTrailingIconColor = system_error,
            focusedTrailingIconColor = primery_blue,
            unfocusedTrailingIconColor = light_gray,
            disabledPlaceholderColor = gray,
            errorPlaceholderColor = system_error,
            focusedPlaceholderColor = gray
        ),
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AOTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    maxLines: Int = 100,
    isError: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    containerColor: Color = white,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    //keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text_14_Reg(label ?: "") },
        placeholder = { Text_14_Reg(placeholder ?: "", color = gray) },
        modifier = modifier,
        isError = isError,
        keyboardOptions = keyboardOptions,
        enabled = enabled,
        maxLines = maxLines,
        readOnly = readOnly,
        singleLine = singleLine,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedLabelColor = primery_blue,
            unfocusedLabelColor = gray,
            errorLabelColor = system_error,
            errorTrailingIconColor = system_error,
            focusedTrailingIconColor = primery_blue,
            unfocusedTrailingIconColor = light_gray
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTextWithCursor(
    valueText: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
    maxLines: Int = 100,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    minLines: Int = 1,
    isError: Boolean = false,
    readOnly: Boolean = false,
    singleLine: Boolean = false,
    containerColor: Color = Color.White,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val textFieldValueState = remember {
        mutableStateOf(
            TextFieldValue(
                text = valueText,
                selection = TextRange(valueText.length)
            )
        )
    }

    if (textFieldValueState.value.text != valueText) {
        textFieldValueState.value = textFieldValueState.value.copy(
            text = valueText,
            selection = TextRange(valueText.length)
        )
    }
    OutlinedTextField(
        value = textFieldValueState.value,
        onValueChange = { newValue ->
            textFieldValueState.value = newValue
            onValueChange.invoke(newValue.text)

        },
        label = { label?.let { Text_14_Reg(it) } },
        placeholder = { placeholder?.let { Text_14_Reg(it, color = Color.Gray) } },
        modifier = modifier
            .fillMaxWidth(),
        isError = isError,
        keyboardOptions = keyboardOptions,

        keyboardActions = keyboardActions,
        enabled = enabled,
        maxLines = maxLines,
        minLines = minLines,
        readOnly = readOnly,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            focusedLabelColor = Color.Blue,
            unfocusedLabelColor = Color.Gray,
            errorTrailingIconColor = Color.Red,
            focusedTrailingIconColor = Color.Blue,
            unfocusedTrailingIconColor = Color.LightGray
        ),
    )
}
//
//@OptIn(ExperimentalMaterial3Api::class)
//@Composable
//fun EditTextPassword(
//    value: String,
//    onValueChange: (String) -> Unit,
//    modifier: Modifier = Modifier,
//    label: String,
//    enabled: Boolean = true,
//    isError: Boolean = false,
//    containerColor: Color = white,
//) {
//    val passwordVisible = rememberSaveable { mutableStateOf(false) }
//    OutlinedTextField(
//        value = value,
//        onValueChange = onValueChange,
//        label = { Text_14_Reg(label) },
//        modifier = modifier,
//        isError = isError,
//        enabled = enabled,
//        shape = RoundedCornerShape(8.dp),
//        visualTransformation = if (passwordVisible.value) VisualTransformation.None else PasswordVisualTransformation(),
//        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
//        trailingIcon = {
//            val image = if (passwordVisible.value)
//                com.eventmap.theme.R.drawable.visibility
//            else com.eventmap.theme.R.drawable.visibility_off
//
//            // Please provide localized description for accessibility services
//            val description = if (passwordVisible.value) "Hide password" else "Show password"
//
//            IconButton(onClick = { passwordVisible.value = !passwordVisible.value }) {
//                Icon(painter = painterResource(id = image), description)
//            }
//        },
//        colors = OutlinedTextFieldDefaults.colors(
//            focusedContainerColor = containerColor,
//            unfocusedContainerColor = containerColor,
//            disabledContainerColor = containerColor,
//            focusedBorderColor = primery_blue,
//            unfocusedBorderColor = light_gray,
//            errorBorderColor = system_error,
//            focusedTrailingIconColor = primery_blue,
//            unfocusedTrailingIconColor = light_gray,
//            errorTrailingIconColor = system_error,
//            focusedLabelColor = primery_blue,
//            unfocusedLabelColor = gray,
//            errorLabelColor = system_error,
//        )
//    )
//}

class MaskVisualTransformation(private val mask: String) : VisualTransformation {

    private val specialSymbolsIndices = mask.indices.filter { mask[it] != '#' }

    override fun filter(text: AnnotatedString): TransformedText {
        var out = ""
        var maskIndex = 0
        text.forEach { char ->
            while (specialSymbolsIndices.contains(maskIndex)) {
                out += mask[maskIndex]
                maskIndex++
            }
            out += char
            maskIndex++
        }
        return TransformedText(AnnotatedString(out), offsetTranslator())
    }

    private fun offsetTranslator() = object : OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val offsetValue = offset.absoluteValue
            if (offsetValue == 0) return 0
            var numberOfHashtags = 0
            val masked = mask.takeWhile {
                if (it == '#') numberOfHashtags++
                numberOfHashtags < offsetValue
            }
            return masked.length + 1
        }

        override fun transformedToOriginal(offset: Int): Int {
            return mask.take(offset.absoluteValue).count { it == '#' }
        }
    }
}
//
//@Composable
//fun MinimalTextField(
//    value: String,
//    onValueChange: (String) -> Unit,
//    placeholderText: String = "",
//    keyboardOptions: KeyboardOptions =KeyboardOptions.Default
//) {
//    TextField(
//        value = value,
//        onValueChange = onValueChange,
//        placeholder = {
//            Text_14_Reg(
//                text = placeholderText,
//                color = light_gray_blue
//            )
//        },
//        trailingIcon = {
//            if (value.isNotEmpty()) {
//                IconButton(onClick = { onValueChange("") }) {
//                    Icon(
//                        painter = painterResource(id = R.drawable.close),
//                        contentDescription = "clear",
//                        tint = vivid_blue
//                    )
//                }
//            }
//        },
//        colors = TextFieldDefaults.colors(
//            focusedTextColor = black,
//            unfocusedTextColor = black,
//            cursorColor = black,
//            focusedContainerColor = Color.Transparent,
//            unfocusedContainerColor = Color.Transparent,
//            disabledContainerColor = Color.Transparent,
//            errorContainerColor = Color.Transparent,
//            focusedIndicatorColor = Color.Transparent,
//            unfocusedIndicatorColor = Color.Transparent,
//            disabledIndicatorColor = Color.Transparent,
//            errorIndicatorColor = Color.Transparent,
//            focusedPlaceholderColor = light_gray_blue,
//            unfocusedPlaceholderColor = light_gray_blue
//        ),
//        singleLine = true,
//        modifier = Modifier.fillMaxWidth(),
//        keyboardOptions = keyboardOptions
//    )
//}



