package com.expensetracker.presentation.settings

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.local.entity.Rule
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    viewModel: RulesViewModel = hiltViewModel()
) {
    val TAG = "RulesScreen"
    
    val rules by viewModel.rules.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val recategorizeMessage by viewModel.recategorizeMessage.collectAsState()
    
    var showEditorDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<Rule?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf<Rule?>(null) }
    var showRecategorizeDialog by remember { mutableStateOf(false) }
    var pendingRuleUpdate by remember { mutableStateOf<Pair<Rule, Rule>?>(null) }
    var showRecategorizeDialogForNewRule by remember { mutableStateOf(false) }
    var pendingNewRule by remember { mutableStateOf<Rule?>(null) }
    var affectedTransactionCount by remember { mutableIntStateOf(0) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    
    // Tab state for category filtering
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    
    // Show recategorization feedback
    LaunchedEffect(recategorizeMessage) {
        recategorizeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearRecategorizeMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorization Rules") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingRule = null
                    showEditorDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Rule")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (rules.isEmpty()) {
            // Empty state
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
                        "No rules yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Create rules to automatically categorize transactions",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Category Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // "All" tab
                    Tab(
                        selected = selectedTabIndex == 0,
                        onClick = { selectedTabIndex = 0 },
                        text = { Text("All") }
                    )
                    
                    // Category tabs
                    categories.forEachIndexed { index, category ->
                        Tab(
                            selected = selectedTabIndex == index + 1,
                            onClick = { selectedTabIndex = index + 1 },
                            text = { Text(category.name) }
                        )
                    }
                }
                
                // Filter rules based on selected tab
                val filteredRules = if (selectedTabIndex == 0) {
                    rules // Show all rules
                } else {
                    val selectedCategory = categories.getOrNull(selectedTabIndex - 1)
                    rules.filter { it.categoryId == selectedCategory?.id }
                }
                
                // Rules list
                if (filteredRules.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (selectedTabIndex == 0) "No rules yet" else "No rules for this category",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredRules) { rule ->
                            RuleCard(
                                rule = rule,
                                onEdit = {
                                    editingRule = rule
                                    showEditorDialog = true
                                },
                                onDelete = {
                                    showDeleteConfirmation = rule
                                },
                                onToggleActive = {
                                    viewModel.updateRule(rule.copy(isActive = !rule.isActive))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // Rule Editor Dialog
    if (showEditorDialog) {
        RuleEditorDialog(
            rule = editingRule,
            categories = categories,
            onSave = { newRule ->
                // DEBUG: Verify onSave is called
                android.widget.Toast.makeText(
                    context,
                    "onSave called! editingRule=${if (editingRule != null) "NOT NULL" else "NULL"}",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                
                scope.launch {
                    if (editingRule != null && editingRule!!.categoryId != newRule.categoryId) {
                        // Category changed - check if we need to recategorize
                        val count = viewModel.countMatchingTransactions(newRule.pattern, newRule.matchType)
                        if (count > 0) {
                            affectedTransactionCount = count
                            pendingRuleUpdate = Pair(newRule, editingRule!!)
                            showRecategorizeDialog = true
                        } else {
                            viewModel.updateRule(newRule, editingRule)
                        }
                    } else if (editingRule != null) {
                        viewModel.updateRule(newRule, editingRule)
                        showEditorDialog = false
                        editingRule = null
                    } else {
                        Log.d(TAG, "=== BRANCH: Adding NEW rule ===")
                        Log.d(TAG, "Rule pattern: '${newRule.pattern}', category: ${newRule.categoryId}, matchType: ${newRule.matchType}")
                        
                        // Adding new rule - check for matching transactions
                        val count = viewModel.countMatchingTransactions(newRule.pattern, newRule.matchType)
                        
                        Log.d(TAG, "Count returned from ViewModel: $count")
                        
                        if (count > 0) {
                            Log.d(TAG, "Count > 0, setting up recategorization dialog")
                            affectedTransactionCount = count
                            pendingNewRule = newRule
                            showRecategorizeDialogForNewRule = true
                            showEditorDialog = false
                            editingRule = null
                            
                            Log.d(TAG, "State after setup:")
                            Log.d(TAG, "  affectedTransactionCount = $affectedTransactionCount")
                            Log.d(TAG, "  pendingNewRule = ${pendingNewRule?.pattern}")
                            Log.d(TAG, "  showRecategorizeDialogForNewRule = $showRecategorizeDialogForNewRule")
                            Log.d(TAG, "  showEditorDialog = $showEditorDialog")
                        } else {
                            Log.d(TAG, "Count = 0, adding rule directly but requesting recategorization check anyway")
                            viewModel.addRule(newRule, recategorize = true)
                            snackbarHostState.showSnackbar("✓ Rule added")
                            showEditorDialog = false
                            editingRule = null
                        }
                        Log.d(TAG, "=== END new rule branch ===")
                    }
                }
            },
            onDismiss = {
                showEditorDialog = false
                editingRule = null
            },
            onTestPattern = { merchant, pattern, matchType ->
                viewModel.testPattern(merchant, pattern, matchType)
            }
        )
    }
    
    // Recategorize Confirmation Dialog
    if (showRecategorizeDialog && pendingRuleUpdate != null) {
        val (newRule, oldRule) = pendingRuleUpdate!!
        val oldCategory = categories.find { it.id == oldRule.categoryId }
        val newCategory = categories.find { it.id == newRule.categoryId }
        
        if (oldCategory != null && newCategory != null) {
            RecategorizeConfirmationDialog(
                pattern = newRule.pattern,
                oldCategory = oldCategory,
                newCategory = newCategory,
                affectedCount = affectedTransactionCount,
                onConfirm = {
                    scope.launch {
                        viewModel.updateRule(newRule, oldRule)
                        showRecategorizeDialog = false
                        pendingRuleUpdate = null
                        snackbarHostState.showSnackbar(
                            "✓ Rule updated. $affectedTransactionCount transaction${if (affectedTransactionCount != 1) "s" else ""} recategorized."
                        )
                    }
                },
                onDismiss = {
                    showRecategorizeDialog = false
                    pendingRuleUpdate = null
                }
            )
        }
    }
    
    // Recategorize Dialog for New Rules
    if (showRecategorizeDialogForNewRule && pendingNewRule != null) {
        Log.d(TAG, "=== RENDERING RecategorizeDialog for NEW rule ===")
        val newRule = pendingNewRule!!
        val newCategory = categories.find { it.id == newRule.categoryId }
        
        // Try to find "Others" category, but use a fallback if it doesn't exist
        val othersCategory = categories.find { it.name.equals("Others", ignoreCase = true) }
            ?: categories.find { it.name.equals("Other", ignoreCase = true) }
            ?: categories.firstOrNull { it.id == 7L } // Default category ID
            ?: categories.firstOrNull() // Absolute fallback
        
        Log.d(TAG, "Dialog state:")
        Log.d(TAG, "  newRule.pattern = '${newRule.pattern}'")
        Log.d(TAG, "  newCategory = ${newCategory?.name ?: "NULL"}")
        Log.d(TAG, "  othersCategory = ${othersCategory?.name ?: "NULL"}")
        Log.d(TAG, "  affectedCount = $affectedTransactionCount")
        
        if (newCategory != null && othersCategory != null) {
            Log.d(TAG, "Rendering RecategorizeConfirmationDialog")
            RecategorizeConfirmationDialog(
                pattern = newRule.pattern,
                oldCategory = othersCategory,
                newCategory = newCategory,
                affectedCount = affectedTransactionCount,
                onConfirm = {
                    scope.launch {
                        viewModel.addRule(newRule, recategorize = true)
                        showRecategorizeDialogForNewRule = false
                        pendingNewRule = null
                        snackbarHostState.showSnackbar(
                            "✓ Rule added. $affectedTransactionCount transaction${if (affectedTransactionCount != 1) "s" else ""} recategorized."
                        )
                    }
                },
                onDismiss = {
                    scope.launch {
                        viewModel.addRule(newRule, recategorize = false)
                        showRecategorizeDialogForNewRule = false
                        pendingNewRule = null
                        snackbarHostState.showSnackbar("✓ Rule added (past transactions not affected)")
                    }
                }
            )
        } else {
            Log.e(TAG, "ERROR: Cannot render dialog - newCategory or othersCategory is null!")
            // Fallback: just add the rule without dialog
            scope.launch {
                viewModel.addRule(newRule, recategorize = false)
                showRecategorizeDialogForNewRule = false
                pendingNewRule = null
                snackbarHostState.showSnackbar("✓ Rule added")
            }
        }
    }
    
    // Delete Confirmation Dialog
    showDeleteConfirmation?.let { rule ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text("Delete Rule?") },
            text = { Text("Are you sure you want to delete the rule for \"${rule.pattern}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteRule(rule)
                        showDeleteConfirmation = null
                        scope.launch {
                            snackbarHostState.showSnackbar("Rule deleted")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun RuleCard(
    rule: Rule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.pattern,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    AssistChip(
                        onClick = {},
                        label = { Text(rule.matchType.name.replace("_", " ")) }
                    )
                }
                
                Switch(
                    checked = rule.isActive,
                    onCheckedChange = { onToggleActive() }
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit")
                }
                
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}
