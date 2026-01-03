package com.expensetracker.domain.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.expensetracker.data.model.PdfTransaction
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

/**
 * Parser for HDFC Bank statement PDFs
 * Uses balance-change detection to classify withdrawals vs deposits
 */
class HdfcStatementParser @Inject constructor() : PdfParser {
    
    companion object {
        private const val TAG = "HdfcStatementParser"
        
        private val DATE_FORMATS = listOf(
            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).apply {
                set2DigitYearStart(Date(100, 0, 1)) // Year 2000
            },
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
        )
    }
    
    override suspend fun parsePdf(context: Context, uri: Uri, password: String?): List<PdfTransaction> = withContext(Dispatchers.IO) {
        try {
            PDFBoxResourceLoader.init(context)
            
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw PdfParsingException("Cannot open PDF file")
            
            val document = PDDocument.load(inputStream)
            
            // Check if PDF is encrypted
            if (document.isEncrypted) {
                Log.d(TAG, "PDF is encrypted")
                
                if (password == null) {
                    document.close()
                    inputStream.close()
                    throw PdfPasswordRequiredException()
                }
                
                // Try to decrypt with provided password using PDFBox API
                try {
                    val decryptionMaterial = com.tom_roush.pdfbox.pdmodel.encryption.StandardDecryptionMaterial(password)
                    document.openProtection(decryptionMaterial)
                    
                    // Check if decryption was successful by trying to access the document
                    if (document.isEncrypted) {
                        // Still encrypted after attempting to decrypt = wrong password
                        document.close()
                        inputStream.close()
                        throw PdfInvalidPasswordException()
                    }
                    
                    Log.d(TAG, "PDF successfully decrypted")
                } catch (e: PdfInvalidPasswordException) {
                    throw e
                } catch (e: Exception) {
                    document.close()
                    inputStream.close()
                    Log.e(TAG, "Decryption failed", e)
                    throw PdfInvalidPasswordException("Failed to decrypt PDF: ${e.message}")
                }
            }
            
            val stripper = PDFTextStripper()
            val text = stripper.getText(document)
            
            document.close()
            inputStream.close()
            
            Log.d(TAG, "Extracted text length: ${text.length}")
            
            val transactions = parseTransactions(text)
            
            Log.d(TAG, "Parsed ${transactions.size} transactions")
            Log.d(TAG, "Total debits: ${transactions.mapNotNull { it.debit }.sum()}")
            Log.d(TAG, "Total credits: ${transactions.mapNotNull { it.credit }.sum()}")
            
            transactions
            
        } catch (e: PdfPasswordRequiredException) {
            throw e
        } catch (e: PdfInvalidPasswordException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing PDF", e)
            throw PdfParsingException("Failed to parse PDF: ${e.message}", e)
        }
    }
    
    private fun parseTransactions(text: String): List<PdfTransaction> {
        val transactions = mutableListOf<PdfTransaction>()
        val lines = text.lines()
        
        var startIndex = -1
        var endIndex = lines.size
        var depositColumnPos = -1
        var balanceColumnPos = -1
        
        for (i in lines.indices) {
            val line = lines[i]
            
            if (line.contains("Date") && line.contains("Narration") && 
                line.contains("Withdrawal Amt.") && line.contains("Deposit Amt.")) {
                startIndex = i + 1
                
                // Detect column positions
                depositColumnPos = line.indexOf("Deposit Amt.")
                balanceColumnPos = line.indexOf("Closing Balance")
                
                Log.d(TAG, "========== HEADER DETECTION ==========")
                Log.d(TAG, "Header line: '$line'")
                Log.d(TAG, "Header line length: ${line.length}")
                Log.d(TAG, "Deposit column 'Deposit Amt.' at position: $depositColumnPos")
                Log.d(TAG, "Balance column 'Closing Balance' at position: $balanceColumnPos")
                Log.d(TAG, "======================================")
            }
            
            if (line.contains("STATEMENT SUMMARY", ignoreCase = true)) {
                endIndex = i
                Log.d(TAG, "Found summary at line $i")
                break
            }
        }
        
        if (startIndex == -1) {
            Log.w(TAG, "Could not find transaction table")
            return transactions
        }
        
        var i = startIndex
        var previousBalance: Double? = null  // Track previous transaction's balance
        
        // CRITICAL: Extract opening balance FIRST before processing any transactions
        Log.d(TAG, "========== SEARCHING FOR OPENING BALANCE ==========")
        
        // Try multiple search strategies
        // Strategy 1: Search in statement summary section (after endIndex)
        Log.d(TAG, "Strategy 1: Searching in statement summary (lines $endIndex to ${lines.size})")
        for (j in endIndex until lines.size) {
            val line = lines[j]
            
            // Try multiple patterns
            if (line.contains("Opening Balance", ignoreCase = true) || 
                line.contains("Opening Bal", ignoreCase = true) ||
                line.contains("Op. Balance", ignoreCase = true)) {
                
                Log.d(TAG, "Found opening balance text at line $j: '$line'")
                
                // Try to extract amount from this line
                val balanceMatch = Regex("""([\d,]+\.\d{2})""").find(line)
                if (balanceMatch != null) {
                    previousBalance = balanceMatch.value.replace(",", "").toDouble()
                    Log.d(TAG, "✓ FOUND OPENING BALANCE: ₹$previousBalance")
                    break
                } else {
                    // Amount might be on next line
                    if (j + 1 < lines.size) {
                        val nextLine = lines[j + 1]
                        val nextMatch = Regex("""([\d,]+\.\d{2})""").find(nextLine)
                        if (nextMatch != null) {
                            previousBalance = nextMatch.value.replace(",", "").toDouble()
                            Log.d(TAG, "✓ FOUND OPENING BALANCE on next line: ₹$previousBalance")
                            break
                        }
                    }
                }
            }
        }
        
        // Strategy 2: If not found, search entire document
        if (previousBalance == null) {
            Log.w(TAG, "Strategy 2: Opening balance not found in summary, searching entire document")
            for (j in lines.indices) {
                val line = lines[j]
                if (line.contains("Opening Balance", ignoreCase = true) || 
                    line.contains("Opening Bal", ignoreCase = true)) {
                    
                    val balanceMatch = Regex("""([\d,]+\.\d{2})""").find(line)
                    if (balanceMatch != null) {
                        previousBalance = balanceMatch.value.replace(",", "").toDouble()
                        Log.d(TAG, "✓ FOUND OPENING BALANCE in document: ₹$previousBalance")
                        break
                    }
                }
            }
        }
        
        if (previousBalance == null) {
            Log.e(TAG, "❌ CRITICAL: Could not find opening balance anywhere in document!")
            Log.e(TAG, "   First transaction will be skipped")
        } else {
            Log.d(TAG, "✓ Starting with opening balance: ₹$previousBalance")
        }
        Log.d(TAG, "===================================================")
        
        while (i < endIndex) {
            val line = lines[i]
            
            if (line.matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                try {
                    val transaction = parseTransaction(lines, i, endIndex, depositColumnPos, balanceColumnPos, previousBalance)
                    if (transaction != null) {
                        // Update previous balance for next iteration
                        previousBalance = transaction.balance
                        
                        // Only import withdrawal transactions (debits), skip deposits
                        if (transaction.debit != null && transaction.debit > 0) {
                            transactions.add(transaction)
                            Log.d(TAG, "✓ WITHDRAWAL: ${transaction.date} - ${transaction.description.take(50)} - ₹${transaction.debit}")
                        } else if (transaction.credit != null && transaction.credit > 0) {
                            Log.d(TAG, "✗ SKIPPED DEPOSIT: ${transaction.date} - ${transaction.description.take(50)} - ₹${transaction.credit}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse line $i", e)
                }
            }
            
            i++
        }
        
        return transactions
    }
    
    private fun parseTransaction(
        allLines: List<String>,
        currentIndex: Int,
        endIndex: Int,
        depositColumnPos: Int,
        balanceColumnPos: Int,
        previousBalance: Double?
    ): PdfTransaction? {
        try {
            val firstLine = allLines[currentIndex]
            
            Log.d(TAG, "\n=== Line $currentIndex ===")
            Log.d(TAG, "First line: $firstLine")
            
            // Extract date
            val dateMatch = Regex("""^(\d{2}[/-]\d{2}[/-]\d{2,4})""").find(firstLine)
                ?: return null
            val dateStr = dateMatch.groupValues[1]
            
            // Collect ALL lines for this transaction
            val transactionLines = mutableListOf<String>()
            var j = currentIndex
            
            while (j < endIndex && j < currentIndex + 10) {
                val line = allLines[j]
                
                // Stop at next transaction
                if (j > currentIndex && line.matches(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}.*"""))) {
                    break
                }
                if (line.contains("STATEMENT SUMMARY", ignoreCase = true)) break
                if (line.contains("Page No")) break
                
                transactionLines.add(line)
                j++
            }
            
            Log.d(TAG, "Collected ${transactionLines.size} lines")
            
            // Combine all lines
            val fullText = transactionLines.joinToString(" ").replace(Regex("""\s+"""), " ")
            Log.d(TAG, "Full text: $fullText")
            
            // Find reference number (15-16 digits starting with 0000)
            val refPattern = Regex("""\b(0000\d{11,12})\b""")
            val refMatch = refPattern.find(fullText)
            
            val description: String
            val afterRef: String
            
            if (refMatch == null) {
                Log.w(TAG, "No reference number found - using fallback parsing")
                
                // Fallback: Description is everything before the first amount
                val firstAmountMatch = Regex("""([\d,]+\.\d{2})""").find(fullText)
                if (firstAmountMatch != null) {
                    description = fullText.substring(0, firstAmountMatch.range.first)
                        .replace(Regex("""\d{2}[/-]\d{2}[/-]\d{2,4}"""), "")  // Remove dates
                        .replace(Regex("""\s+"""), " ")
                        .trim()
                    afterRef = fullText.substring(firstAmountMatch.range.first)
                } else {
                    Log.w(TAG, "No amounts found in transaction")
                    return null
                }
            } else {
                val refNumber = refMatch.value
                Log.d(TAG, "Reference: $refNumber at position ${refMatch.range.first}")
                
                // Description is everything BEFORE the reference number
                description = fullText.substring(0, refMatch.range.first)
                    .replace(Regex("""\d{2}[/-]\d{2}[/-]\d{2,4}"""), "")  // Remove dates
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                
                // Amounts section is AFTER the reference number
                afterRef = fullText.substring(refMatch.range.last + 1).trim()
            }
            
            Log.d(TAG, "Description: $description")
            Log.d(TAG, "After ref: '$afterRef'")
            
            // Remove the value date (first date after ref)
            val withoutValueDate = afterRef.replaceFirst(Regex("""^\d{2}[/-]\d{2}[/-]\d{2,4}"""), "").trim()
            
            Log.d(TAG, "After removing value date: '$withoutValueDate'")
            
            // Extract all amounts
            val amountPattern = Regex("""([\d,]+\.\d{2})""")
            val allAmounts = amountPattern.findAll(withoutValueDate)
                .map { it.value.replace(",", "").toDouble() }
                .toList()
            
            Log.d(TAG, "Found amounts: $allAmounts")
            
            if (allAmounts.isEmpty()) {
                Log.w(TAG, "No amounts found")
                return null
            }
            
            // CRITICAL: Last amount is ALWAYS the closing balance
            val balance = allAmounts.last()
            Log.d(TAG, "Balance (last): $balance")
            
            // Process transaction amounts (all except last which is balance)
            val txAmounts = allAmounts.dropLast(1)
            
            var withdrawal: Double? = null
            var deposit: Double? = null
            
            // BALANCE-CHANGE APPROACH: Compare current balance with previous balance
            if (txAmounts.size == 1) {
                val amount = txAmounts[0]
                
                if (previousBalance != null) {
                    // Compare balances to determine transaction type
                    if (balance < previousBalance) {
                        // Balance decreased = Withdrawal
                        withdrawal = amount
                        Log.d(TAG, "→ WITHDRAWAL: ₹$amount (balance decreased: ₹$previousBalance → ₹$balance)")
                    } else if (balance > previousBalance) {
                        // Balance increased = Deposit
                        deposit = amount
                        Log.d(TAG, "→ DEPOSIT: ₹$amount (balance increased: ₹$previousBalance → ₹$balance)")
                    } else {
                        // Balance unchanged - shouldn't happen, treat as withdrawal
                        withdrawal = amount
                        Log.d(TAG, "→ WITHDRAWAL: ₹$amount (balance unchanged)")
                    }
                } else {
                    // First transaction - no opening balance found
                    // We can't reliably determine if it's withdrawal or deposit
                    // But we can use this transaction's balance as the starting point for next transactions
                    Log.w(TAG, "⚠ SKIPPED FIRST TRANSACTION: ₹$amount (no opening balance to compare)")
                    Log.w(TAG, "  Using this transaction's balance (₹$balance) as reference for next transaction")
                    // Don't add to transactions list, but continue processing
                    // The balance will be used as previousBalance for the next transaction
                    withdrawal = null
                    deposit = null
                }
            } else if (txAmounts.isEmpty()) {
                Log.d(TAG, "No transaction amount (balance-only line)")
            } else {
                Log.w(TAG, "WARNING: Found ${txAmounts.size} transaction amounts, expected 0 or 1")
                withdrawal = txAmounts[0]
            }
            
            val timestamp = parseDate(dateStr)
            
            Log.d(TAG, "FINAL: Date=$dateStr, Withdrawal=$withdrawal, Deposit=$deposit, Balance=$balance")
            
            return PdfTransaction(
                date = dateStr,
                timestamp = timestamp,
                description = description,
                debit = withdrawal,
                credit = deposit,
                balance = balance
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing transaction", e)
            return null
        }
    }
    
    private fun parseDate(dateStr: String): Long {
        for (format in DATE_FORMATS) {
            try {
                val date = format.parse(dateStr)
                if (date != null) {
                    var timestamp = date.time
                    
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = timestamp
                    val year = calendar.get(Calendar.YEAR)
                    
                    if (year < 1000) {
                        calendar.set(Calendar.YEAR, year + 2000)
                        timestamp = calendar.timeInMillis
                    }
                    
                    return timestamp
                }
            } catch (e: Exception) {
                // Try next format
            }
        }
        
        Log.w(TAG, "Could not parse date: $dateStr")
        return System.currentTimeMillis()
    }
}