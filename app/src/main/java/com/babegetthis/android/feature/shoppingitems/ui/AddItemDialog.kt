package com.babegetthis.android.feature.shoppingitems.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.babegetthis.android.R
import com.babegetthis.android.core.model.Category
import com.babegetthis.android.feature.shoppingitems.model.ShoppingItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAdd: (name: String, quantity: String, categoryId: String?, shop: String?, note: String?) -> Unit,
    onCreateCategory: (name: String, onCreated: (Category) -> Unit) -> Unit = { _, _ -> },
    // Edit mode: when non-null, the dialog pre-fills with this item's data
    editingItem: ShoppingItem? = null,
    onEdit: (itemId: String, name: String, quantity: String, categoryId: String?, shop: String?, note: String?) -> Unit = { _, _, _, _, _, _ -> },
) {
    val isEditMode = editingItem != null

    // Pre-fill fields when editing, empty when adding
    var itemName by remember { mutableStateOf(editingItem?.name ?: "") }
    var quantity by remember { mutableStateOf(editingItem?.quantity ?: "") }
    var note by remember { mutableStateOf(editingItem?.note ?: "") }
    var shop by remember { mutableStateOf(editingItem?.shop ?: "") }
    var selectedCategory by remember {
        mutableStateOf(editingItem?.categoryId?.let { catId ->
            categories.find { it.id == catId }
        })
    }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var categorySearchText by remember {
        mutableStateOf(editingItem?.categoryId?.let { catId ->
            categories.find { it.id == catId }?.name
        } ?: "")
    }
    var isCreatingNewCategory by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    val filteredCategories by remember(categorySearchText, categories) {
        derivedStateOf {
            if (categorySearchText.isBlank()) {
                categories
            } else {
                categories.filter {
                    it.name.contains(categorySearchText, ignoreCase = true)
                }
            }
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
        focusedContainerColor = MaterialTheme.colorScheme.surface,
        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .imePadding(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            ) {
                Text(
                    text = if (isEditMode) stringResource(R.string.shopping_items_edit_title)
                           else stringResource(R.string.shopping_items_add_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                // Item name
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { if (it.length <= 60) itemName = it },
                    label = { Text(stringResource(R.string.shopping_items_name_hint)) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.ShoppingCart,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    supportingText = if (itemName.length >= 60) {
                        { Text("${itemName.length}/60") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quantity (required)
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.shopping_items_quantity_hint)) },
                    leadingIcon = {
                        Icon(
                            Icons.AutoMirrored.Outlined.List,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 80) note = it },
                    label = { Text(stringResource(R.string.shopping_items_note)) },
                    placeholder = { Text(stringResource(R.string.shopping_items_note_placeholder)) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    supportingText = if (note.length >= 80) {
                        { Text("${note.length}/80") }
                    } else null,
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Shop
                OutlinedTextField(
                    value = shop,
                    onValueChange = { if (it.length <= 40) shop = it },
                    label = { Text(stringResource(R.string.shopping_items_shop)) },
                    placeholder = { Text(stringResource(R.string.shopping_items_shop_placeholder)) },
                    leadingIcon = {
                        Icon(
                            Icons.Outlined.Place,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    supportingText = if (shop.length >= 40) {
                        { Text("${shop.length}/40") }
                    } else null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = if (isCreatingNewCategory) stringResource(R.string.category_other) else categorySearchText,
                        onValueChange = { newValue ->
                            categorySearchText = newValue
                            selectedCategory = null
                            isCreatingNewCategory = false
                            categoryDropdownExpanded = true
                        },
                        label = { Text(stringResource(R.string.shopping_items_category)) },
                        placeholder = { Text(stringResource(R.string.shopping_items_category_placeholder)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded)
                        },
                        singleLine = true,
                        readOnly = isCreatingNewCategory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                        shape = RoundedCornerShape(12.dp),
                        colors = fieldColors,
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier.heightIn(max = 250.dp),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.category_none),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            onClick = {
                                selectedCategory = null
                                categorySearchText = ""
                                isCreatingNewCategory = false
                                categoryDropdownExpanded = false
                            }
                        )

                        filteredCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategory = category
                                    categorySearchText = category.name
                                    isCreatingNewCategory = false
                                    categoryDropdownExpanded = false
                                }
                            )
                        }

                        if (filteredCategories.isEmpty() && categorySearchText.isNotBlank()) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(R.string.shopping_items_category_no_match, categorySearchText),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                },
                                onClick = {},
                                enabled = false,
                            )
                        }

                        HorizontalDivider()

                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.category_other),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
                            onClick = {
                                isCreatingNewCategory = true
                                selectedCategory = null
                                categorySearchText = ""
                                categoryDropdownExpanded = false
                            }
                        )
                    }
                }

                // New category name field
                AnimatedVisibility(visible = isCreatingNewCategory) {
                    Column {
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text(stringResource(R.string.shopping_items_new_category)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = fieldColors,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Button(
                        onClick = {
                            if (itemName.isNotBlank() && quantity.isNotBlank()) {
                                val shopValue = shop.trim().ifBlank { null }
                                val noteValue = note.trim().ifBlank { null }

                                if (isEditMode) {
                                    // Edit mode: determine category (new or existing)
                                    if (isCreatingNewCategory && newCategoryName.isNotBlank()) {
                                        onCreateCategory(newCategoryName.trim()) { createdCategory ->
                                            onEdit(
                                                editingItem!!.id,
                                                itemName.trim(),
                                                quantity.trim(),
                                                createdCategory.id,
                                                shopValue,
                                                noteValue,
                                            )
                                        }
                                    } else {
                                        onEdit(
                                            editingItem!!.id,
                                            itemName.trim(),
                                            quantity.trim(),
                                            selectedCategory?.id,
                                            shopValue,
                                            noteValue,
                                        )
                                    }
                                } else {
                                    // Add mode
                                    if (isCreatingNewCategory && newCategoryName.isNotBlank()) {
                                        onCreateCategory(newCategoryName.trim()) { createdCategory ->
                                            onAdd(
                                                itemName.trim(),
                                                quantity.trim(),
                                                createdCategory.id,
                                                shopValue,
                                                noteValue,
                                            )
                                        }
                                    } else {
                                        onAdd(
                                            itemName.trim(),
                                            quantity.trim(),
                                            selectedCategory?.id,
                                            shopValue,
                                            noteValue,
                                        )
                                    }
                                }
                            }
                        },
                        enabled = itemName.isNotBlank() && quantity.isNotBlank(),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            if (isEditMode) Icons.Outlined.Edit else Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isEditMode) stringResource(R.string.save)
                                   else stringResource(R.string.add),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}