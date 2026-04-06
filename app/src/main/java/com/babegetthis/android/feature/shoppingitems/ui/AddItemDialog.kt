package com.babegetthis.android.feature.shoppingitems.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.babegetthis.android.R
import com.babegetthis.android.core.model.Category

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddItemDialog(
    categories: List<Category>,
    onDismiss: () -> Unit,
    onAdd: (name: String, quantity: String, categoryId: String?, shop: String?) -> Unit,
    onCreateCategory: (name: String, onCreated: (Category) -> Unit) -> Unit = { _, _ -> },
) {
    var itemName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var shop by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var categorySearchText by remember { mutableStateOf("") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = stringResource(R.string.shopping_items_add_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text(stringResource(R.string.shopping_items_name_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = quantity,
                    onValueChange = { quantity = it },
                    label = { Text(stringResource(R.string.shopping_items_quantity_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Shop field — simple text input, nullable
                OutlinedTextField(
                    value = shop,
                    onValueChange = { shop = it },
                    label = { Text("Shop (optional)") },
                    placeholder = { Text("e.g. Whole Foods, Costco") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Searchable category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = if (isCreatingNewCategory) "Other" else categorySearchText,
                        onValueChange = { newValue ->
                            categorySearchText = newValue
                            selectedCategory = null
                            isCreatingNewCategory = false
                            categoryDropdownExpanded = true
                        },
                        label = { Text(stringResource(R.string.shopping_items_category)) },
                        placeholder = { Text("Search or select category") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded)
                        },
                        singleLine = true,
                        readOnly = isCreatingNewCategory,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryEditable),
                        shape = RoundedCornerShape(12.dp),
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false },
                        modifier = Modifier.heightIn(max = 250.dp),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "None",
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
                                        "No categories match \"$categorySearchText\"",
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
                                    "Other",
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
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = newCategoryName,
                            onValueChange = { newCategoryName = it },
                            label = { Text("New category name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (itemName.isNotBlank()) {
                        val shopValue = shop.trim().ifBlank { null }
                        if (isCreatingNewCategory && newCategoryName.isNotBlank()) {
                            onCreateCategory(newCategoryName.trim()) { createdCategory ->
                                onAdd(itemName.trim(), quantity.trim(), createdCategory.id, shopValue)
                            }
                        } else {
                            onAdd(itemName.trim(), quantity.trim(), selectedCategory?.id, shopValue)
                        }
                    }
                },
                enabled = itemName.isNotBlank(),
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
