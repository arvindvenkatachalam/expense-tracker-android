package com.expensetracker.presentation.classify

import android.util.Log
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.Transaction
import com.expensetracker.presentation.theme.getCategoryColor
import com.expensetracker.util.CurrencyUtils
import com.expensetracker.util.DateUtils
import kotlinx.coroutines.launch

private const val TAG = "ClassifyScreen"

data class DragTargetInfo(
    val transaction: Transaction,
    val offset: Offset
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassifyScreen(
    viewModel: ClassifyViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    var dragInfo by remember { mutableStateOf<DragTargetInfo?>(null) }
    var hoveredCategoryId by remember { mutableStateOf<Long?>(null) }
    val categoryBounds = remember { mutableStateMapOf<Long, androidx.compose.ui.geometry.Rect>() }
    
    // Dialog states
    var transactionToEdit by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Classify Transactions") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.uncategorizedTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "🎉 All Caught Up!",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "No uncategorized transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Uncategorized Transactions Section
                    Text(
                        text = "Uncategorized Transactions (${uiState.uncategorizedTransactions.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                    
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.27f),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.uncategorizedTransactions, key = { it.id }) { transaction ->
                            DraggableTransactionItem(
                                transaction = transaction,
                                isDragging = dragInfo?.transaction?.id == transaction.id,
                                onDragStart = { offset ->
                                    dragInfo = DragTargetInfo(transaction, offset)
                                    Log.d(TAG, "Started dragging: ${transaction.merchant}")
                                },
                                onDrag = { offset ->
                                    dragInfo = dragInfo?.copy(offset = offset)
                                    
                                    // Check which category is being hovered
                                    hoveredCategoryId = categoryBounds.entries.firstOrNull { (_, bounds) ->
                                        bounds.contains(offset)
                                    }?.key
                                },
                                onDragEnd = { finalOffset ->
                                    Log.d(TAG, "Drop at position: $finalOffset")
                                    Log.d(TAG, "Available category bounds: ${categoryBounds.size}")
                                    
                                    // Check final position directly for more reliable drop detection
                                    val droppedOnCategory = categoryBounds.entries.firstOrNull { (catId, bounds) ->
                                        val contains = bounds.contains(finalOffset)
                                        Log.d(TAG, "Category $catId bounds: $bounds, contains: $contains")
                                        contains
                                    }?.key
                                    
                                    when (droppedOnCategory) {
                                        -1L -> {
                                            // Edit action
                                            Log.d(TAG, "✏️ Edit transaction: ${transaction.merchant}")
                                            transactionToEdit = transaction
                                        }
                                        -2L -> {
                                            // Delete action
                                            Log.d(TAG, "🗑️ Delete transaction: ${transaction.merchant}")
                                            transactionToDelete = transaction
                                        }
                                        null -> {
                                            Log.w(TAG, "✗ DROP MISSED - No category at position $finalOffset")
                                        }
                                        else -> {
                                            // Category drop
                                            val category = uiState.categories.find { it.id == droppedOnCategory }
                                            if (category != null) {
                                                Log.d(TAG, "✓ Categorizing ${transaction.merchant} to ${category.name}")
                                                viewModel.categorizeTransaction(transaction, category)
                                            } else {
                                                Log.w(TAG, "✗ Category not found for ID: $droppedOnCategory")
                                            }
                                        }
                                    }
                                    
                                    dragInfo = null
                                    hoveredCategoryId = null
                                }
                            )
                        }
                    }
                    
                    Divider(modifier = Modifier.padding(vertical = 8.dp))

                    
                    // Categories Grid Section
                    Text(
                        text = "Drag to categorize, edit, or delete",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.73f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Edit and Delete action cards
                        item {
                            ActionCard(
                                icon = "✏️",
                                label = "Edit",
                                color = MaterialTheme.colorScheme.primary,
                                isHovered = hoveredCategoryId == -1L,
                                isDragging = dragInfo != null,
                                onBoundsChanged = { bounds ->
                                    categoryBounds[-1L] = bounds
                                }
                            )
                        }
                        item {
                            ActionCard(
                                icon = "🗑️",
                                label = "Delete",
                                color = MaterialTheme.colorScheme.error,
                                isHovered = hoveredCategoryId == -2L,
                                isDragging = dragInfo != null,
                                onBoundsChanged = { bounds ->
                                    categoryBounds[-2L] = bounds
                                }
                            )
                        }
                        
                        // Category cards
                        items(uiState.categories, key = { it.id }) { category ->
                            CategoryDropTarget(
                                category = category,
                                isHovered = hoveredCategoryId == category.id,
                                isDragging = dragInfo != null,
                                onBoundsChanged = { bounds ->
                                    categoryBounds[category.id] = bounds
                                    Log.d(TAG, "Updated bounds for ${category.name}: $bounds")
                                }
                            )
                        }
                    }
                }
                
                // Floating dragged item
                dragInfo?.let { info ->
                    FloatingTransactionCard(
                        transaction = info.transaction,
                        offset = info.offset
                    )
                }
            }
        }
    }
    
    // Edit Dialog (without delete button)
    transactionToEdit?.let { transaction ->
        val category = uiState.categories.find { it.id == transaction.categoryId }
        TransactionEditDialogWithoutDelete(
            transaction = transaction,
            category = category,
            onDismiss = { transactionToEdit = null },
            onSave = { newAmount ->
                viewModel.updateTransactionAmount(transaction, newAmount)
                transactionToEdit = null
            }
        )
    }
    
    // Delete Confirmation Dialog
    transactionToDelete?.let { transaction ->
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("Delete Transaction?") },
            text = { Text("Are you sure you want to delete this transaction? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(transaction)
                        transactionToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DraggableTransactionItem(
    transaction: Transaction,
    isDragging: Boolean,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (Offset) -> Unit
) {
    var itemBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var currentPosition by remember { mutableStateOf(Offset.Zero) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isDragging) 0.3f else 1f)
            .onGloballyPositioned { coordinates ->
                itemBounds = coordinates.boundsInRoot()
            }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        itemBounds?.let { bounds ->
                            val position = Offset(
                                bounds.left + startOffset.x,
                                bounds.top + startOffset.y
                            )
                            currentPosition = position
                            onDragStart(position)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        itemBounds?.let { bounds ->
                            // Use absolute position from change
                            val absolutePosition = Offset(
                                bounds.left + change.position.x,
                                bounds.top + change.position.y
                            )
                            currentPosition = absolutePosition
                            onDrag(absolutePosition)
                        }
                    },
                    onDragEnd = {
                        onDragEnd(currentPosition)
                    },
                    onDragCancel = {
                        onDragEnd(currentPosition)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Long press to drag",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = DateUtils.formatDateTime(transaction.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = CurrencyUtils.formatAmount(transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun FloatingTransactionCard(
    transaction: Transaction,
    offset: Offset
) {
    Card(
        modifier = Modifier
            .width(300.dp)
            .zIndex(1000f)
            .graphicsLayer {
                // Position the card centered under the finger
                translationX = offset.x - 150.dp.toPx()
                translationY = offset.y - 40.dp.toPx()
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transaction.merchant,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = DateUtils.formatDateTime(transaction.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            
            Text(
                text = CurrencyUtils.formatAmount(transaction.amount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun CategoryDropTarget(
    category: Category,
    isHovered: Boolean,
    isDragging: Boolean,
    onBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.05f else 1f,
        label = "scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 8.dp else 2.dp,
        label = "elevation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onGloballyPositioned { coordinates ->
                // Capture bounds AFTER all modifiers
                onBoundsChanged(coordinates.boundsInRoot())
            }
            .graphicsLayer {
                // Use scale transform instead of padding to avoid changing bounds
                scaleX = scale
                scaleY = scale
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) {
                getCategoryColor(category.color).copy(alpha = 0.3f)
            } else if (isDragging) {
                getCategoryColor(category.color).copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        shape = RoundedCornerShape(if (isHovered) 16.dp else 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(getCategoryColor(category.color).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.icon,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isHovered) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    }
}

@Composable
fun ActionCard(
    icon: String,
    label: String,
    color: Color,
    isHovered: Boolean,
    isDragging: Boolean,
    onBoundsChanged: (androidx.compose.ui.geometry.Rect) -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.05f else 1f,
        label = "scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 8.dp else 2.dp,
        label = "elevation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInRoot())
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors = CardDefaults.cardColors(
            containerColor = if (isHovered) {
                color.copy(alpha = 0.3f)
            } else if (isDragging) {
                color.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        shape = RoundedCornerShape(if (isHovered) 16.dp else 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isHovered) FontWeight.Bold else FontWeight.Medium,
                color = color
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditDialogWithoutDelete(
    transaction: Transaction,
    category: Category?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf(transaction.amount.toString()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Transaction") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Transaction details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = transaction.merchant,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (category != null) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = DateUtils.formatDateTime(transaction.timestamp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Divider()
                
                // Amount input
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    prefix = { Text("₹") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newAmount = amountText.toDoubleOrNull()
                    if (newAmount != null && newAmount > 0) {
                        onSave(newAmount)
                    }
                },
                enabled = amountText.toDoubleOrNull() != null && amountText.toDoubleOrNull()!! > 0
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
