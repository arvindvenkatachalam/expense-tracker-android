package com.expensetracker.presentation.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.local.entity.Transaction
import com.expensetracker.presentation.theme.getCategoryColor
import com.expensetracker.util.CurrencyUtils
import com.expensetracker.util.DateUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel(),
    onCategoryClick: (Long) -> Unit,
    onAnalysisClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Transaction edit dialog state
    var selectedTransaction by remember { mutableStateOf<Transaction?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    
    // Force refresh when screen is displayed (helps after PDF import)
    LaunchedEffect(Unit) {
        viewModel.forceRefresh()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expense Tracker") }
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
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Month and Date Selector
                item {
                    MonthDateSelector(
                        selectedYear = uiState.selectedYear,
                        selectedMonth = uiState.selectedMonth,
                        selectedDate = uiState.selectedDate,
                        onPreviousMonth = { viewModel.goToPreviousMonth() },
                        onNextMonth = { viewModel.goToNextMonth() },
                        onDateSelected = { viewModel.selectDate(it) }
                    )
                }
                
                // Total Expenses Card
                item {
                    TotalExpensesCard(totalExpenses = uiState.totalExpenses)
                }
                
                // Category Breakdown
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expenses by Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onAnalysisClick) {
                            Text("View Details →")
                        }
                    }
                }
                
                if (uiState.categoryExpenses.isEmpty()) {
                    item {
                        EmptyStateCard()
                    }
                } else {
                    items(uiState.categoryExpenses) { categoryExpense ->
                        CategoryExpenseItem(
                            categoryExpense = categoryExpense,
                            onClick = { onCategoryClick(categoryExpense.category.id) }
                        )
                    }
                }
                
                // Recent Transactions
                if (uiState.recentTransactions.isNotEmpty()) {
                    item {
                        Text(
                            text = "Recent Transactions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    items(uiState.recentTransactions) { transaction ->
                        val category = uiState.categoryExpenses
                            .find { it.category.id == transaction.categoryId }
                            ?.category
                        TransactionItem(
                            transaction = transaction,
                            category = category,
                            onClick = {
                                selectedTransaction = transaction
                                showEditDialog = true
                            }
                        )
                    }
                }
            }
        }
    }
    
    // Transaction Edit Dialog
    if (showEditDialog && selectedTransaction != null) {
        TransactionEditDialog(
            transaction = selectedTransaction!!,
            category = uiState.categoryExpenses
                .find { it.category.id == selectedTransaction!!.categoryId }
                ?.category,
            onDismiss = {
                showEditDialog = false
                selectedTransaction = null
            },
            onSave = { newAmount ->
                viewModel.updateTransactionAmount(selectedTransaction!!, newAmount)
                showEditDialog = false
                selectedTransaction = null
            },
            onDelete = {
                viewModel.deleteTransaction(selectedTransaction!!)
                showEditDialog = false
                selectedTransaction = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePeriodSelector(
    selectedPeriod: TimePeriod,
    onPeriodSelected: (TimePeriod) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TimePeriod.values().filter { it != TimePeriod.CUSTOM }.forEach { period ->
            FilterChip(
                selected = selectedPeriod == period,
                onClick = { onPeriodSelected(period) },
                label = {
                    Text(
                        when (period) {
                            TimePeriod.TODAY -> "Today"
                            TimePeriod.THIS_WEEK -> "This Week"
                            TimePeriod.THIS_MONTH -> "This Month"
                            else -> ""
                        }
                    )
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TotalExpensesCard(totalExpenses: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Total Expenses",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = CurrencyUtils.formatAmount(totalExpenses),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
fun CategoryExpenseItem(
    categoryExpense: CategoryExpense,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(getCategoryColor(categoryExpense.category.color).copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = categoryExpense.category.icon,
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Category Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = categoryExpense.category.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${categoryExpense.transactionCount} transactions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Amount and Percentage
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = CurrencyUtils.formatAmount(categoryExpense.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${String.format("%.1f", categoryExpense.percentage)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun TransactionItem(
    transaction: com.expensetracker.data.local.entity.Transaction,
    category: com.expensetracker.data.local.entity.Category? = null,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon
            if (category != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(getCategoryColor(category.color).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = category.icon,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }
            
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
fun EmptyStateCard() {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No transactions yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Transactions will appear here automatically when you receive bank SMS",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditDialog(
    transaction: com.expensetracker.data.local.entity.Transaction,
    category: com.expensetracker.data.local.entity.Category?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
    onDelete: () -> Unit
) {
    var amountText by remember { mutableStateOf(transaction.amount.toString()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { showDeleteConfirmation = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
    
    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Transaction?") },
            text = { Text("Are you sure you want to delete this transaction? This action cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthDateSelector(
    selectedYear: Int,
    selectedMonth: Int,
    selectedDate: Int?,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onDateSelected: (Int?) -> Unit
) {
    val currentCalendar = java.util.Calendar.getInstance()
    val currentYear = currentCalendar.get(java.util.Calendar.YEAR)
    val currentMonth = currentCalendar.get(java.util.Calendar.MONTH)
    val currentDay = currentCalendar.get(java.util.Calendar.DAY_OF_MONTH)
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Month selector with arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousMonth) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowBack,
                    contentDescription = "Previous Month"
                )
            }
            
            Text(
                text = com.expensetracker.util.DateUtils.formatMonthYear(selectedYear, selectedMonth),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            IconButton(onClick = onNextMonth) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Default.ArrowForward,
                    contentDescription = "Next Month"
                )
            }
        }
        
        // Date tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // "All" chip to show entire month
            FilterChip(
                selected = selectedDate == null,
                onClick = { onDateSelected(null) },
                label = { Text("All") }
            )
            
            // Date chips for each day in the month
            val daysInMonth = com.expensetracker.util.DateUtils.getDaysInMonth(selectedYear, selectedMonth)
            daysInMonth.forEach { day ->
                val isToday = selectedYear == currentYear && 
                             selectedMonth == currentMonth && 
                             day == currentDay
                
                FilterChip(
                    selected = selectedDate == day,
                    onClick = { onDateSelected(day) },
                    label = { 
                        Text(
                            text = day.toString(),
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = if (isToday && selectedDate != day) {
                        FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                        )
                    } else {
                        FilterChipDefaults.filterChipColors()
                    }
                )
            }
        }
    }
}