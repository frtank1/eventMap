package com.eventmap.views

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.eventmap.theme.black
import com.eventmap.theme.black_gray
import com.eventmap.theme.gray
import com.eventmap.theme.gray_dark
import com.eventmap.theme.light_gray
import com.eventmap.theme.primery_blue
import com.eventmap.theme.system_error
import com.eventmap.theme.ui_kit.Text_14_Reg
import com.eventmap.theme.white
import com.eventmap.core.ui.resources.Res
import com.eventmap.core.ui.resources.arrow_drop_down_24px
import com.eventmap.core.ui.resources.arrow_drop_up_24px
import org.jetbrains.compose.resources.painterResource
import kotlin.compareTo


class DropDownNestedComponent {
    data class DropDownNestedItem(
        val id: String,
        val text: String,
        val selectable: Boolean = true,
        var isExpand: Boolean = false,
        var isLoading: Boolean = false,
        val canChild: Boolean = false,
        val parentId:String? = null,
        val level: Int = 1,
        var visible: Boolean = true
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropDownNested(
    modifier: Modifier = Modifier,
    defaultText: String,
    list: MutableState<List<DropDownNestedComponent.DropDownNestedItem>>,
    showDropDownNested: MutableState<Boolean>,
    enabled: Boolean = false,
    isError:Boolean = false,
    onSelected: (DropDownNestedComponent.DropDownNestedItem) -> Unit,
    onDismissRequest: () -> Unit,
    onClickOpen: () -> Unit,
    onLoad: ((item: DropDownNestedComponent.DropDownNestedItem, indexParent: Int) -> Unit)? = null,
) {
    val clickOpen = remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(defaultText) }
    var rowSize by remember { mutableStateOf(Size.Zero) }
    ExposedDropdownMenuBox(
        modifier = modifier
            .onGloballyPositioned { layoutCoordinates ->
                rowSize = layoutCoordinates.size.toSize()
            },
        expanded = showDropDownNested.value,
        onExpandedChange = {
            showDropDownNested.value = !showDropDownNested.value
        }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                text = it
            },
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (clickOpen.value) {
                        clickOpen.value = false
                    } else {
                        onClickOpen.invoke()
                    }
                }
                .menuAnchor(),
            readOnly = true,
            isError = isError,
            textStyle = TextStyle.Default.copy(fontSize = 14.sp),
            shape = RoundedCornerShape(8.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = white,
                unfocusedContainerColor = white,
                focusedIndicatorColor = primery_blue,
                unfocusedIndicatorColor = light_gray,
                errorIndicatorColor = system_error,
                focusedLabelColor = primery_blue,
                unfocusedLabelColor = gray,
                errorLabelColor = system_error,
                errorTrailingIconColor = system_error,
                focusedTrailingIconColor = primery_blue,
                unfocusedTrailingIconColor = light_gray
            ),
            trailingIcon = {
                Image(
                    painter = painterResource(
                        resource =
                            if (showDropDownNested.value)
                                Res.drawable.arrow_drop_up_24px
                            else
                                Res.drawable.arrow_drop_down_24px
                    ),
                    contentDescription = null,
                )
            }
        )
        DropdownMenu(
            expanded = showDropDownNested.value,
            properties = PopupProperties(
                focusable = true,
                dismissOnClickOutside = true,
                dismissOnBackPress = true
            ),
            onDismissRequest = {
                showDropDownNested.value = false
                onDismissRequest.invoke()
            },
            modifier = Modifier
                .exposedDropdownSize()
                .width(with(LocalDensity.current) { rowSize.width.toDp() })
                .background(white),
        ) {
            list.value.forEach { itemList ->
                if (itemList.visible) {
                    DropDownNested2(
                        onSelected = { item ->
                            text = item.text
                            list.value = list.value.map {
                                if (it.level > 1) {
                                    it.copy(visible = false, isExpand = false)
                                } else {
                                    it.copy(isExpand = false)
                                }
                            }
                            onSelected.invoke(item)
                        },
                        //showDropDownNested = showDropDownNested,
                        itemList = itemList,
                        list = list.value,
                        onExpand = { item ->
                            list.value = list.value.map {
                                if (it.id == item.id) {
                                    it.copy(isExpand = item.isExpand)
                                } else {
                                    it
                                }
                                if (it.parentId == item.id) {
                                    it.copy(visible = item.isExpand)
                                } else {
                                    it
                                }
                            }
                            if (item.isExpand == false) {
                                hideItems(list, item.id)
                            }
                        },
                        /*onDismissRequest = {
                            clickOpen.value = true
                            onDismissRequest.invoke()
                            list.value = list.value.map {
                                if (it.level > 1) {
                                    it.copy(visible = false, isExpand = false)
                                } else {
                                    it.copy(isExpand = false)
                                }
                            }
                        },*/
                        onLoad = { item ->
                            val parentIndex = list.value.indexOf(item)
                            list.value = list.value.map {
                                if (it.id == item.id) {
                                    it.copy(isLoading = true)
                                } else {
                                    it
                                }
                            }
                            onLoad?.invoke(item, parentIndex)
                        }
                    )
                }

            }
        }

    }
}

private fun hideItems(
    list: MutableState<List<DropDownNestedComponent.DropDownNestedItem>>,
    id: String,
) {
    list.value.forEach {
        if (it.parentId == id) {
            hideItems(list, it.id)
            it.visible = false
            it.isExpand = false
        }
    }
}

@Composable
private fun DropDownNested2(
    onSelected: (item: DropDownNestedComponent.DropDownNestedItem) -> Unit,
    itemList: DropDownNestedComponent.DropDownNestedItem,
    list: List<DropDownNestedComponent.DropDownNestedItem>,
    onExpand: (DropDownNestedComponent.DropDownNestedItem) -> Unit,
    onLoad: (DropDownNestedComponent.DropDownNestedItem) -> Unit,
) {
    Row(
        modifier = Modifier
            .padding(start = 16.dp * itemList.level)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = itemList.text,
            modifier = Modifier
                .clickable {
                    if (itemList.selectable) {
                        onSelected.invoke(itemList)
                    }
                }
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 20.dp)
                .weight(1f)
        )
        if (itemList.canChild) {
            if (itemList.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(32.dp),
                    color = primery_blue
                )
            } else {
                val icon =
                    if (itemList.isExpand) Res.drawable.arrow_drop_up_24px else Res.drawable.arrow_drop_down_24px
                val tintColor = if (itemList.isExpand) primery_blue else gray_dark
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .clickable {
                            itemList.isExpand = !itemList.isExpand
                            if (list.find { it.parentId == itemList.id } == null) {
                                onLoad.invoke(itemList)
                            } else {
                                onExpand.invoke(itemList)
                            }
                        }
                        .padding(8.dp)
                )
            }
        }
    }
}
