package com.expensetracker.presentation.classify

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.local.entity.Category
import com.expensetracker.data.local.entity.Transaction
import com.expensetracker.presentation.theme.getCategoryColor
import com.expensetracker.util.CurrencyUtils
import com.expensetracker.util.DateUtils

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Classify Transactions") }
            )
        }
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
                            .weight(0.4f),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.uncategorizedTransactions) { transaction ->
                            DraggableTransactionItem(
                                transaction = transaction,
                                isDragging = dragInfo?.transaction?.id == transaction.id,
                                onDragStart = { offset ->
                                    dragInfo = DragTargetInfo(transaction, offset)
                                },
                                onDrag = { offset ->
                                    dragInfo = dragInfo?.copy(offset = offset)
                                    
                                    // Check which category is being hovered
                                    hoveredCategoryId = categoryBounds.entries.firstOrNull { (_, bounds) ->
                                        bounds.contains(offset)
                                    }?.key
                                },
                                onDragEnd = {
                                    hoveredCategoryId?.let { categoryId ->
                                        val category = uiState.categories.find { it.id == categoryId }
                                        if (category != null) {
                                            viewModel.categorizeTransaction(transaction, category)
                                        }
                                    }
                                    dragInfo = null
                                    hoveredCategoryId = null
                                }
                            )
                        }
                    }
                    
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    // Categories Grid Section
                    Text(
                        text = "Drag transaction to a category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.6f),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.categories) { category ->
                            CategoryDropTarget(
                                category = category,
                                isHovered = hoveredCategoryId == category.id,
                                isDragging = dragInfo != null,
                                onBoundsChanged = { bounds ->
                                    categoryBounds[category.id] = bounds
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
}

@Composable
fun DraggableTransactionItem(
    transaction: Transaction,
    isDragging: Boolean,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit
) {
    var itemBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    
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
                            onDragStart(bounds.center)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        itemBounds?.let { bounds ->
                            val newOffset = Offset(
                                bounds.center.x + change.position.x - bounds.width / 2,
                                bounds.center.y + change.position.y - bounds.height / 2
                            )
                            onDrag(newOffset)
                        }
                    },
                    onDragEnd = {
                        onDragEnd()
                    },
                    onDragCancel = {
                        onDragEnd()
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
            .offset(x = offset.x.dp, y = offset.y.dp)
            .zIndex(1000f)
            .graphicsLayer {
                translationX = -150.dp.toPx() // Center horizontally
                translationY = -40.dp.toPx()  // Center vertically
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
    val scale by animateDpAsState(
        targetValue = if (isHovered) 4.dp else 0.dp,
        label = "scale"
    )
    
    val elevation by animateDpAsState(
        targetValue = if (isHovered) 8.dp else 2.dp,
        label = "elevation"
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged(coordinates.boundsInRoot())
            }
            .padding(scale),
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(if (isHovered) 64.dp else 56.dp)
                    .clip(CircleShape)
                    .background(getCategoryColor(category.color).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = category.icon,
                    style = if (isHovered) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isHovered) FontWeight.ExtraBold else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
