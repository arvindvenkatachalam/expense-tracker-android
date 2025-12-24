package com.expensetracker.presentation.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.expensetracker.data.local.entity.Rule
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    onBackClick: () -> Unit,
    viewModel: RulesViewModel = hiltViewModel()
) {
    val rules by viewModel.rules.collectAsState()
    val categories by viewModel.categories.collectAsState()
    
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Categorization Rules") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Group rules by category
                val rulesByCategory = rules.groupBy { it.categoryId }
                
                rulesByCategory.forEach { (categoryId, categoryRules) ->
                    val category = categories.find { it.id == categoryId }
                    
                    item {
                        Text(
                            text = category?.name ?: "Unknown Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                    
                    items(categoryRules) { rule ->
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
    
    // Rule Editor Dialog
    if (showEditorDialog) {
        RuleEditorDialog(
            rule = editingRule,
            categories = categories,
            onSave = { newRule ->
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
                        // Adding new rule - check for matching transactions
                        val count = viewModel.countMatchingTransactions(newRule.pattern, newRule.matchType)
                        if (count > 0) {
                            affectedTransactionCount = count
                            pendingNewRule = newRule
                            showRecategorizeDialogForNewRule = true
                            showEditorDialog = false
                            editingRule = null
                        } else {
                            viewModel.addRule(newRule)
                            snackbarHostState.showSnackbar("✓ Rule added")
                            showEditorDialog = false
                            editingRule = null
                        }
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
        val newRule = pendingNewRule!!
        val newCategory = categories.find { it.id == newRule.categoryId }
        val othersCategory = categories.find { it.name == "Others" }
        
        if (newCategory != null && othersCategory != null) {
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
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {},
                            label = { Text(rule.matchType.name.replace("_", " ")) }
                        )
                        if (rule.priority > 0) {
                            AssistChip(
                                onClick = {},
                                label = { Text("Priority: ${rule.priority}") }
                            )
                        }
                    }
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
