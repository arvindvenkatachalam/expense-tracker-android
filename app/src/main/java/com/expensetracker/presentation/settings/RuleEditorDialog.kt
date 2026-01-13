package com.expensetracker.presentation.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.MatchType
import com.expensetracker.data.local.entity.Rule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditorDialog(
    rule: Rule? = null,  // null for creating new rule
    categories: List<Category>,
    onSave: (Rule) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedCategoryId by remember(rule, categories) { 
        mutableLongStateOf(rule?.categoryId ?: categories.firstOrNull()?.id ?: 0L) 
    }
    var pattern by remember { mutableStateOf(rule?.pattern ?: "") }
    var selectedMatchType by remember { mutableStateOf(rule?.matchType ?: MatchType.CONTAINS) }
    var isActive by remember { mutableStateOf(rule?.isActive ?: true) }
    
    var showCategoryDropdown by remember { mutableStateOf(false) }
    var showMatchTypeDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (rule == null) "Create Rule" else "Edit Rule")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Category Selector
                ExposedDropdownMenuBox(
                    expanded = showCategoryDropdown,
                    onExpandedChange = { showCategoryDropdown = it }
                ) {
                    OutlinedTextField(
                        value = categories.find { it.id == selectedCategoryId }?.name ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showCategoryDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showCategoryDropdown,
                        onDismissRequest = { showCategoryDropdown = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    selectedCategoryId = category.id
                                    showCategoryDropdown = false
                                }
                            )
                        }
                    }
                }
                
                // Pattern Input
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    label = { Text("Pattern") },
                    placeholder = { Text("e.g., ZOMATO, SWIGGY") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                // Match Type Selector
                ExposedDropdownMenuBox(
                    expanded = showMatchTypeDropdown,
                    onExpandedChange = { showMatchTypeDropdown = it }
                ) {
                    OutlinedTextField(
                        value = selectedMatchType.name.replace("_", " "),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Match Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showMatchTypeDropdown) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = showMatchTypeDropdown,
                        onDismissRequest = { showMatchTypeDropdown = false }
                    ) {
                        MatchType.values().forEach { matchType ->
                            DropdownMenuItem(
                                text = { Text(matchType.name.replace("_", " ")) },
                                onClick = {
                                    selectedMatchType = matchType
                                    showMatchTypeDropdown = false
                                }
                            )
                        }
                    }
                }
                
                // Active Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Active", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = isActive,
                        onCheckedChange = { isActive = it }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (pattern.isNotBlank()) {
                        val newRule = Rule(
                            id = rule?.id ?: 0,
                            categoryId = selectedCategoryId,
                            pattern = pattern.trim(),
                            matchType = selectedMatchType,
                            priority = 0,
                            isActive = isActive
                        )
                        onSave(newRule)
                    }
                },
                enabled = pattern.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
