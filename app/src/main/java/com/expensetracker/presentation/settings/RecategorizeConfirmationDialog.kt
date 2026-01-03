package com.expensetracker.presentation.settings

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import com.expensetracker.data.local.entity.Category

@Composable
fun RecategorizeConfirmationDialog(
    pattern: String,
    oldCategory: Category,
    newCategory: Category,
    affectedCount: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Update Category?")
        },
        text = {
            Text(
                text = buildString {
                    append("This will recategorize ")
                    append(affectedCount)
                    append(" existing transaction")
                    if (affectedCount != 1) append("s")
                    append(" that match this pattern.\n\n")
                    append("Pattern: \"$pattern\"\n")
                    append("Old Category: ${oldCategory.name}\n")
                    append("New Category: ${newCategory.name}")
                }
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Update All")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
