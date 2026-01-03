package com.expensetracker.presentation.analysis

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.presentation.dashboard.CategoryExpense
import com.expensetracker.presentation.dashboard.MonthDateSelector
import com.expensetracker.presentation.theme.getCategoryColor
import com.expensetracker.util.CurrencyUtils
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryAnalysisScreen(
    viewModel: CategoryAnalysisViewModel = hiltViewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Category Analysis") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
                
                // Pie Chart
                if (uiState.categoryExpenses.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            PieChartView(
                                categoryExpenses = uiState.categoryExpenses,
                                totalExpenses = uiState.totalExpenses,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(350.dp)
                                    .padding(16.dp)
                            )
                        }
                    }
                    
                    // Category Breakdown Header
                    item {
                        Text(
                            text = "Category Breakdown",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    // Category List
                    items(uiState.categoryExpenses) { categoryExpense ->
                        CategoryBreakdownItem(categoryExpense = categoryExpense)
                    }
                } else {
                    item {
                        EmptyAnalysisCard()
                    }
                }
            }
        }
    }
}

@Composable
fun PieChartView(
    categoryExpenses: List<CategoryExpense>,
    totalExpenses: Double,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                // Donut style
                isDrawHoleEnabled = true
                holeRadius = 65f
                transparentCircleRadius = 70f
                setHoleColor(Color.TRANSPARENT)
                
                // Center text (total)
                setDrawCenterText(true)
                setCenterTextSize(16f)
                setCenterTextColor(Color.BLACK)
                
                // Styling
                description.isEnabled = false
                legend.isEnabled = false
                setDrawEntryLabels(false)
                
                // Touch
                isRotationEnabled = true
                isHighlightPerTapEnabled = true
                
                // Animation
                animateY(1000)
            }
        },
        update = { chart ->
            val entries = categoryExpenses.map { 
                PieEntry(it.percentage, it.category.name)
            }
            
            val colors = categoryExpenses.map {
                getCategoryColor(it.category.color).copy(alpha = 1f).hashCode()
            }
            
            val dataSet = PieDataSet(entries, "").apply {
                this.colors = colors
                valueTextSize = 12f
                valueTextColor = Color.WHITE
                sliceSpace = 2f
                valueFormatter = PercentFormatter(chart)
            }
            
            chart.data = PieData(dataSet)
            chart.centerText = "Total\n${CurrencyUtils.formatAmount(totalExpenses)}"
            chart.invalidate()
        },
        modifier = modifier
    )
}

@Composable
fun CategoryBreakdownItem(categoryExpense: CategoryExpense) {
    Card(
        modifier = Modifier.fillMaxWidth()
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
fun EmptyAnalysisCard() {
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
                text = "No data available",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No transactions found for the selected period",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
